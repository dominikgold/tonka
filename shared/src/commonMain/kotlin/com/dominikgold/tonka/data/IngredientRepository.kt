package com.dominikgold.tonka.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dominikgold.tonka.data.db.TonkaDatabase
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.domain.TextNormalizer
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.IngredientInput
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.IngredientOrigin
import com.dominikgold.tonka.model.IngredientSubstitution
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.Seasonality
import com.dominikgold.tonka.model.SubstitutionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import com.dominikgold.tonka.data.db.Ingredient as DbIngredient
import com.dominikgold.tonka.data.db.Ingredient_alias as DbIngredientAlias
import com.dominikgold.tonka.data.db.Ingredient_edge as DbIngredientEdge
import com.dominikgold.tonka.data.db.Ingredient_substitution as DbIngredientSubstitution

class IngredientCycleException(childName: String, parentName: String) :
    IllegalArgumentException("\"$parentName\" is already below \"$childName\", so it can't be its parent too")

class IngredientInUseException(name: String) :
    IllegalStateException("\"$name\" is still used by recipes, substitutions or other ingredients")

class DuplicateIngredientException(name: String) :
    IllegalArgumentException("An ingredient called \"$name\" already exists")

class IngredientRepository(
    private val database: TonkaDatabase,
    private val clock: AppClock,
    appScope: CoroutineScope,
) {

    private val queries get() = database.ingredientQueries

    val graph: StateFlow<IngredientGraph> = combine(
        queries.selectAll().asFlow().mapToList(Dispatchers.IO),
        queries.selectAllAliases().asFlow().mapToList(Dispatchers.IO),
        queries.selectAllEdges().asFlow().mapToList(Dispatchers.IO),
        queries.selectAllSubstitutions().asFlow().mapToList(Dispatchers.IO),
    ) { ingredients, aliases, edges, substitutions ->
        IngredientGraph(
            ingredients = ingredients.map { it.toEntity() },
            aliases = aliases.groupBy({ IngredientId(it.ingredient_id) }, { it.alias }),
            edges = edges.map { it.toEntity() },
            substitutions = substitutions.map { it.toEntity() },
        )
    }.stateIn(appScope, SharingStarted.Eagerly, IngredientGraph.Empty)

    suspend fun create(input: IngredientInput, origin: IngredientOrigin): IngredientId = withContext(Dispatchers.IO) {
        val id = IngredientId.new()
        database.transaction {
            insertIngredient(id, input, origin, seedKey = null)
            writeAliases(id, input.aliases)
            writeParents(id, input.parentIds)
        }
        id
    }

    // Creates an unknown ingredient from an unmatched recipe line; it stays in the review queue
    suspend fun createUnreviewed(
        name: String,
        parentId: IngredientId?,
        dietClass: DietClass = DietClass.UNKNOWN,
        origin: IngredientOrigin = IngredientOrigin.IMPORT,
    ): IngredientId = withContext(Dispatchers.IO) {
        queries.selectByName(name.trim()).executeAsOneOrNull()?.let { return@withContext IngredientId(it.id) }
        create(
            IngredientInput(
                name = name.trim(),
                kind = IngredientKind.INGREDIENT,
                dietClass = dietClass,
                seasonality = Seasonality.UNKNOWN,
                freshMonths = MonthSet.None,
                storageMonths = MonthSet.None,
                isPantryStaple = false,
                notes = "",
                aliases = emptyList(),
                parentIds = listOfNotNull(parentId),
                needsReview = true,
            ),
            origin = origin,
        )
    }

    suspend fun update(id: IngredientId, input: IngredientInput) = withContext(Dispatchers.IO) {
        database.transaction {
            val existing = queries.selectByName(input.name.trim()).executeAsOneOrNull()
            if (existing != null && existing.id != id.value) throw DuplicateIngredientException(input.name)
            queries.update(
                name = input.name.trim(),
                kind = input.kind.name,
                dietClass = input.dietClass.name,
                seasonality = input.seasonality.name,
                freshMonths = input.freshMonths.mask.toLong(),
                storageMonths = input.storageMonths.mask.toLong(),
                isPantryStaple = input.isPantryStaple,
                needsReview = input.needsReview,
                notes = input.notes,
                userEdited = true,
                updatedAt = clock.nowMillis(),
                id = id.value,
            )
            queries.deleteAliasesFor(id.value)
            writeAliases(id, input.aliases)
            queries.deleteParentEdgesOf(id.value)
            writeParents(id, input.parentIds)
        }
    }

    suspend fun markReviewed(id: IngredientId) = withContext(Dispatchers.IO) {
        queries.setNeedsReview(false, clock.nowMillis(), id.value)
        Unit
    }

    suspend fun addParent(childId: IngredientId, parentId: IngredientId) = withContext(Dispatchers.IO) {
        database.transaction { addEdgeChecked(childId, parentId) }
    }

    suspend fun removeParent(childId: IngredientId, parentId: IngredientId) = withContext(Dispatchers.IO) {
        queries.deleteEdge(childId.value, parentId.value)
        Unit
    }

    suspend fun referenceCount(id: IngredientId): Long = withContext(Dispatchers.IO) {
        queries.countReferences(id.value).executeAsOne()
    }

    suspend fun delete(id: IngredientId) = withContext(Dispatchers.IO) {
        database.transaction {
            val ingredient = queries.selectById(id.value).executeAsOneOrNull() ?: return@transaction
            if (queries.countReferences(id.value).executeAsOne() > 0) throw IngredientInUseException(ingredient.name)
            queries.deleteById(id.value)
        }
    }

    // Repoints every reference from [fromId] to [intoId] and keeps the merged name as an alias
    suspend fun merge(fromId: IngredientId, intoId: IngredientId) = withContext(Dispatchers.IO) {
        if (fromId == intoId) return@withContext
        database.transaction {
            val from = queries.selectById(fromId.value).executeAsOneOrNull() ?: return@transaction
            val recipeQueries = database.recipeQueries
            recipeQueries.repointLines(toId = intoId.value, fromId = fromId.value)
            recipeQueries.repointAlternatives(toId = intoId.value, fromId = fromId.value)
            recipeQueries.deleteAlternativesOf(fromId.value)
            queries.repointSubstitutionsFrom(toId = intoId.value, fromId = fromId.value)
            queries.repointSubstitutionsTo(toId = intoId.value, fromId = fromId.value)
            queries.deleteSelfSubstitutions()
            // Moving the merged node's parents could close a cycle, so they are re-added through the check
            val parentIds = queries.selectAllEdges().executeAsList()
                .filter { it.child_id == fromId.value }
                .map { IngredientId(it.parent_id) }
            queries.repointEdgeParents(toId = intoId.value, fromId = fromId.value)
            queries.deleteSelfEdges()
            queries.moveAliases(toId = intoId.value, fromId = fromId.value)
            queries.deleteById(fromId.value)
            parentIds.filter { it != intoId }.forEach { parentId ->
                runCatching { addEdgeChecked(intoId, parentId) }
            }
            insertAlias(intoId, from.name)
        }
    }

    suspend fun saveSubstitution(substitution: IngredientSubstitution) = withContext(Dispatchers.IO) {
        queries.insertSubstitution(
            DbIngredientSubstitution(
                id = substitution.id.value,
                from_id = substitution.fromId.value,
                to_id = substitution.toId.value,
                note = substitution.note,
                bidirectional = substitution.isBidirectional,
            ),
        )
        Unit
    }

    suspend fun deleteSubstitution(id: SubstitutionId) = withContext(Dispatchers.IO) {
        queries.deleteSubstitution(id.value)
        Unit
    }

    suspend fun applySeed(seed: SeedData) = withContext(Dispatchers.IO) {
        val settingsQueries = database.mealPlanQueries
        val appliedVersion = settingsQueries.selectSetting(SettingKey.SeedVersion).executeAsOneOrNull()?.toIntOrNull() ?: 0
        if (appliedVersion >= seed.version) return@withContext
        val now = clock.nowMillis()
        database.transaction {
            val idsByKey = mutableMapOf<String, IngredientId>()
            val nodes = seed.categories.map { it to IngredientKind.CATEGORY } + seed.ingredients.map { it to IngredientKind.INGREDIENT }
            for ((node, kind) in nodes) {
                val seedKey = "seed:${node.key}"
                val existing = queries.selectBySeedKey(seedKey).executeAsOneOrNull()
                    ?: queries.selectByName(node.name).executeAsOneOrNull()
                val seasonality = seed.seasonalityOf(node)
                val id = existing?.let { IngredientId(it.id) } ?: IngredientId.new()
                idsByKey[node.key] = id
                when {
                    existing == null -> queries.insert(
                        DbIngredient(
                            id = id.value,
                            name = node.name,
                            kind = kind.name,
                            diet_class = node.diet.name,
                            seasonality = seasonality.name,
                            fresh_months = node.freshMonths().mask.toLong(),
                            storage_months = node.storageMonths().mask.toLong(),
                            is_pantry_staple = node.staple,
                            needs_review = false,
                            origin = IngredientOrigin.SEED.name,
                            notes = "",
                            seed_key = seedKey,
                            user_edited = false,
                            created_at = now,
                            updated_at = now,
                        ),
                    )
                    !existing.user_edited -> queries.update(
                        name = node.name,
                        kind = kind.name,
                        dietClass = node.diet.name,
                        seasonality = seasonality.name,
                        freshMonths = node.freshMonths().mask.toLong(),
                        storageMonths = node.storageMonths().mask.toLong(),
                        isPantryStaple = node.staple,
                        needsReview = false,
                        notes = existing.notes,
                        userEdited = false,
                        updatedAt = now,
                        id = id.value,
                    )
                }
                if (existing == null || !existing.user_edited) {
                    node.aliases.forEach { alias -> insertAlias(id, alias) }
                }
            }
            for ((node, _) in nodes) {
                val childId = idsByKey.getValue(node.key)
                node.parents.mapNotNull { idsByKey[it] }.forEach { parentId ->
                    runCatching { addEdgeChecked(childId, parentId) }
                }
            }
            settingsQueries.upsertSetting(SettingKey.SeedVersion, seed.version.toString())
        }
    }

    private fun insertIngredient(
        id: IngredientId,
        input: IngredientInput,
        origin: IngredientOrigin,
        seedKey: String?,
    ) {
        if (queries.selectByName(input.name.trim()).executeAsOneOrNull() != null) {
            throw DuplicateIngredientException(input.name)
        }
        val now = clock.nowMillis()
        queries.insert(
            DbIngredient(
                id = id.value,
                name = input.name.trim(),
                kind = input.kind.name,
                diet_class = input.dietClass.name,
                seasonality = input.seasonality.name,
                fresh_months = input.freshMonths.mask.toLong(),
                storage_months = input.storageMonths.mask.toLong(),
                is_pantry_staple = input.isPantryStaple,
                needs_review = input.needsReview,
                origin = origin.name,
                notes = input.notes,
                seed_key = seedKey,
                user_edited = origin == IngredientOrigin.USER,
                created_at = now,
                updated_at = now,
            ),
        )
    }

    private fun writeAliases(id: IngredientId, aliases: List<String>) {
        aliases.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { insertAlias(id, it) }
    }

    private fun insertAlias(id: IngredientId, alias: String) {
        val normalized = TextNormalizer.normalize(alias)
        if (normalized.isEmpty()) return
        queries.insertAlias(DbIngredientAlias(ingredient_id = id.value, alias = alias.trim(), normalized = normalized))
    }

    private fun writeParents(id: IngredientId, parentIds: List<IngredientId>) {
        parentIds.distinct().forEach { addEdgeChecked(id, it) }
    }

    private fun addEdgeChecked(childId: IngredientId, parentId: IngredientId) {
        val hits = queries.countCycleHits(parentId = parentId.value, childId = childId.value).executeAsOne()
        if (hits > 0 || childId == parentId) {
            val childName = queries.selectById(childId.value).executeAsOneOrNull()?.name ?: childId.value
            val parentName = queries.selectById(parentId.value).executeAsOneOrNull()?.name ?: parentId.value
            throw IngredientCycleException(childName, parentName)
        }
        queries.insertEdge(DbIngredientEdge(child_id = childId.value, parent_id = parentId.value))
    }
}
