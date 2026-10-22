package com.dominikgold.tonka.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dominikgold.tonka.data.db.TonkaDatabase
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.LineAlternative
import com.dominikgold.tonka.model.LineId
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.NoteId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeNote
import com.dominikgold.tonka.model.RecipeRecord
import com.dominikgold.tonka.model.RecipeRevision
import com.dominikgold.tonka.model.RecipeSection
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.RecipeSource
import com.dominikgold.tonka.model.RecipeStep
import com.dominikgold.tonka.model.RecipeTip
import com.dominikgold.tonka.model.RecipeVariant
import com.dominikgold.tonka.model.RevisionId
import com.dominikgold.tonka.model.SectionId
import com.dominikgold.tonka.model.SourceType
import com.dominikgold.tonka.model.StepId
import com.dominikgold.tonka.model.Tag
import com.dominikgold.tonka.model.TagId
import com.dominikgold.tonka.model.TipId
import com.dominikgold.tonka.model.VariantId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.dominikgold.tonka.data.db.Recipe as DbRecipe
import com.dominikgold.tonka.data.db.Recipe_ingredient as DbLine
import com.dominikgold.tonka.data.db.Recipe_ingredient_alternative as DbAlternative
import com.dominikgold.tonka.data.db.Recipe_note as DbNote
import com.dominikgold.tonka.data.db.Recipe_revision as DbRevision
import com.dominikgold.tonka.data.db.Recipe_scan_image as DbScanImage
import com.dominikgold.tonka.data.db.Recipe_section as DbSection
import com.dominikgold.tonka.data.db.Recipe_step as DbStep
import com.dominikgold.tonka.data.db.Recipe_tag as DbRecipeTag
import com.dominikgold.tonka.data.db.Recipe_tip as DbTip
import com.dominikgold.tonka.data.db.Recipe_variant as DbVariant
import com.dominikgold.tonka.data.db.Tag as DbTag
import com.dominikgold.tonka.data.db.Variant_scope as DbScope

object ScopeOwnerType {
    const val Line = "LINE"
    const val Step = "STEP"
    const val Tip = "TIP"
}

val SnapshotJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class RecipeRepository(
    private val database: TonkaDatabase,
    private val clock: AppClock,
    private val imageStore: ImageStore,
    appScope: CoroutineScope,
) {

    private val queries get() = database.recipeQueries

    val records: StateFlow<List<RecipeRecord>?> = combine(
        listOf(
            queries.selectAllRecipes().asFlow().mapToList(Dispatchers.IO),
            queries.selectAllVariants().asFlow().mapToList(Dispatchers.IO),
            queries.selectAllSections().asFlow().mapToList(Dispatchers.IO),
            queries.selectAllLines().asFlow().mapToList(Dispatchers.IO),
            queries.selectAllAlternatives().asFlow().mapToList(Dispatchers.IO),
            queries.selectAllSteps().asFlow().mapToList(Dispatchers.IO),
            queries.selectAllTips().asFlow().mapToList(Dispatchers.IO),
            queries.selectAllScopes().asFlow().mapToList(Dispatchers.IO),
            queries.selectAllTags().asFlow().mapToList(Dispatchers.IO),
            queries.selectAllRecipeTags().asFlow().mapToList(Dispatchers.IO),
        ),
    ) { tables ->
        @Suppress("UNCHECKED_CAST")
        assembleRecords(
            RecipeTables(
                recipes = tables[0] as List<DbRecipe>,
                variants = tables[1] as List<DbVariant>,
                sections = tables[2] as List<DbSection>,
                lines = tables[3] as List<DbLine>,
                alternatives = tables[4] as List<DbAlternative>,
                steps = tables[5] as List<DbStep>,
                tips = tables[6] as List<DbTip>,
                scopes = tables[7] as List<DbScope>,
                tags = tables[8] as List<DbTag>,
                recipeTags = tables[9] as List<DbRecipeTag>,
            ),
        )
    }.stateIn(appScope, SharingStarted.Eagerly, null)

    val tags: Flow<List<Tag>> = queries.selectAllTags().asFlow().mapToList(Dispatchers.IO).map { tags ->
        tags.map { Tag(TagId(it.id), it.name) }.sortedBy { it.name.lowercase() }
    }

    fun observeNotes(recipeId: RecipeId): Flow<List<RecipeNote>> =
        queries.selectNotesOfRecipe(recipeId.value).asFlow().mapToList(Dispatchers.IO).map { notes -> notes.map { it.toEntity() } }

    fun observeRevisions(recipeId: RecipeId): Flow<List<RecipeRevision>> =
        queries.selectRevisionsOfRecipe(recipeId.value).asFlow().mapToList(Dispatchers.IO).map { revisions ->
            revisions.mapNotNull { it.toEntity() }
        }

    fun observeScanImages(recipeId: RecipeId): Flow<List<String>> =
        queries.selectScanImagesOfRecipe(recipeId.value).asFlow().mapToList(Dispatchers.IO).map { images -> images.map { it.path } }

    suspend fun revision(recipeId: RecipeId, number: Int): RecipeRevision? = withContext(Dispatchers.IO) {
        queries.selectRevision(recipeId.value, number.toLong()).executeAsOneOrNull()?.toEntity()
    }

    suspend fun record(recipeId: RecipeId): RecipeRecord? = withContext(Dispatchers.IO) {
        val recipe = queries.selectRecipeById(recipeId.value).executeAsOneOrNull() ?: return@withContext null
        assembleRecords(
            RecipeTables(
                recipes = listOf(recipe),
                variants = queries.selectAllVariants().executeAsList(),
                sections = queries.selectAllSections().executeAsList(),
                lines = queries.selectAllLines().executeAsList(),
                alternatives = queries.selectAllAlternatives().executeAsList(),
                steps = queries.selectAllSteps().executeAsList(),
                tips = queries.selectAllTips().executeAsList(),
                scopes = queries.selectAllScopes().executeAsList(),
                tags = queries.selectAllTags().executeAsList(),
                recipeTags = queries.selectAllRecipeTags().executeAsList(),
            ),
        ).firstOrNull()
    }

    // Writes the recipe and a new revision; returns the revision number, unchanged when nothing differs
    suspend fun save(
        snapshot: RecipeSnapshot,
        summary: String,
        forceRevision: Boolean = false,
        scanImagePaths: List<String> = emptyList(),
    ): Int = withContext(Dispatchers.IO) {
        val cleaned = snapshot.normalized()
        database.transactionWithResult {
            val existing = queries.selectRecipeById(cleaned.id.value).executeAsOneOrNull()
            val currentRevision = existing?.current_revision?.toInt() ?: 0
            if (existing != null && !forceRevision) {
                val latest = queries.selectRevision(cleaned.id.value, currentRevision.toLong()).executeAsOneOrNull()?.toEntity()
                if (latest?.snapshot == cleaned) return@transactionWithResult currentRevision
            }
            val now = clock.nowMillis()
            val revision = currentRevision + 1
            val knownIngredients = database.ingredientQueries.selectAll().executeAsList().map { it.id }.toSet()
            val knownRecipes = queries.selectAllRecipes().executeAsList().map { it.id }.toSet()
            val sanitized = cleaned.withExistingReferences(knownIngredients, knownRecipes)
            writeRecipe(sanitized, revision, createdAt = existing?.created_at ?: now, updatedAt = now)
            queries.insertRevision(
                DbRevision(
                    id = RevisionId.new().value,
                    recipe_id = sanitized.id.value,
                    number = revision.toLong(),
                    created_at = now,
                    summary = summary.trim(),
                    snapshot_json = SnapshotJson.encodeToString(RecipeSnapshot.serializer(), sanitized),
                ),
            )
            scanImagePaths.forEachIndexed { index, path ->
                queries.insertScanImage(DbScanImage(recipe_id = sanitized.id.value, path = path, sort_order = index.toLong()))
            }
            database.mealPlanQueries.updateTitleSnapshots(sanitized.title, sanitized.id.value)
            queries.deleteUnusedTags()
            revision
        }
    }

    suspend fun restore(recipeId: RecipeId, number: Int): Int? {
        val revision = revision(recipeId, number) ?: return null
        return save(revision.snapshot.copy(id = recipeId), summary = "Restored revision $number", forceRevision = true)
    }

    suspend fun duplicate(recipeId: RecipeId): RecipeId? {
        val record = record(recipeId) ?: return null
        val copy = record.snapshot.withFreshIds().let { it.copy(title = "${it.title} (copy)") }
        save(copy, summary = "Duplicated from ${record.snapshot.title}")
        return copy.id
    }

    suspend fun delete(recipeId: RecipeId) {
        val imagePaths = withContext(Dispatchers.IO) {
            val revisions = queries.selectRevisionsOfRecipe(recipeId.value).executeAsList().mapNotNull { it.toEntity() }
            val scanImages = queries.selectScanImagesOfRecipe(recipeId.value).executeAsList().map { it.path }
            val paths = revisions.flatMap { it.snapshot.imagePaths() } + scanImages
            queries.deleteRecipe(recipeId.value)
            queries.deleteUnusedTags()
            paths.toSet()
        }
        imageStore.delete(imagePaths)
    }

    suspend fun addNote(recipeId: RecipeId, variantId: VariantId?, text: String) = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext
        queries.insertNote(
            DbNote(
                id = NoteId.new().value,
                recipe_id = recipeId.value,
                variant_id = variantId?.value,
                created_at = clock.nowMillis(),
                text = text.trim(),
            ),
        )
        Unit
    }

    suspend fun deleteNote(noteId: NoteId) = withContext(Dispatchers.IO) {
        queries.deleteNote(noteId.value)
        Unit
    }

    suspend fun setLineIngredient(lineId: LineId, ingredientId: IngredientId?) = withContext(Dispatchers.IO) {
        queries.setLineIngredient(ingredientId?.value, lineId.value)
        Unit
    }

    private fun writeRecipe(snapshot: RecipeSnapshot, revision: Int, createdAt: Long, updatedAt: Long) {
        val recipeId = snapshot.id.value
        val row = DbRecipe(
            id = recipeId,
            title = snapshot.title,
            description = snapshot.description,
            source_type = snapshot.source.type.name,
            source_url = snapshot.source.url,
            source_book = snapshot.source.book,
            source_author = snapshot.source.author,
            source_page = snapshot.source.page,
            header_image_path = snapshot.headerImagePath,
            yield_text = snapshot.yieldText,
            prep_minutes = snapshot.prepMinutes?.toLong(),
            cook_minutes = snapshot.cookMinutes?.toLong(),
            season_override_months = snapshot.seasonOverride?.mask?.toLong(),
            diet_override = snapshot.dietOverride?.name,
            current_revision = revision.toLong(),
            created_at = createdAt,
            updated_at = updatedAt,
        )
        if (queries.selectRecipeById(recipeId).executeAsOneOrNull() == null) {
            queries.insertRecipe(row)
        } else {
            queries.updateRecipe(
                title = row.title,
                description = row.description,
                sourceType = row.source_type,
                sourceUrl = row.source_url,
                sourceBook = row.source_book,
                sourceAuthor = row.source_author,
                sourcePage = row.source_page,
                headerImagePath = row.header_image_path,
                yieldText = row.yield_text,
                prepMinutes = row.prep_minutes,
                cookMinutes = row.cook_minutes,
                seasonOverrideMonths = row.season_override_months,
                dietOverride = row.diet_override,
                currentRevision = row.current_revision,
                updatedAt = row.updated_at,
                id = recipeId,
            )
        }

        // Variants are updated in place so notes and meal plan entries keep pointing at them
        val existingVariantIds = queries.selectVariantIdsOfRecipe(recipeId).executeAsList().toSet()
        queries.deleteScopesOfRecipe(recipeId)
        snapshot.variants.forEachIndexed { index, variant ->
            if (variant.id.value in existingVariantIds) {
                queries.updateVariant(variant.name, variant.description, index.toLong(), variant.isDefault, variant.id.value)
            } else {
                queries.insertVariant(
                    DbVariant(
                        id = variant.id.value,
                        recipe_id = recipeId,
                        name = variant.name,
                        description = variant.description,
                        sort_order = index.toLong(),
                        is_default = variant.isDefault,
                    ),
                )
            }
        }
        (existingVariantIds - snapshot.variants.map { it.id.value }.toSet()).forEach { queries.deleteVariant(it) }

        queries.deleteLinesOfRecipe(recipeId)
        queries.deleteStepsOfRecipe(recipeId)
        queries.deleteTipsOfRecipe(recipeId)
        queries.deleteSectionsOfRecipe(recipeId)
        queries.deleteTagsOfRecipe(recipeId)

        snapshot.sections.forEachIndexed { index, section ->
            queries.insertSection(DbSection(id = section.id.value, recipe_id = recipeId, name = section.name, sort_order = index.toLong()))
        }
        snapshot.lines.forEachIndexed { index, line ->
            queries.insertLine(
                DbLine(
                    id = line.id.value,
                    recipe_id = recipeId,
                    section_id = line.sectionId?.value,
                    sort_order = index.toLong(),
                    raw_text = line.rawText,
                    amount_text = line.amountText,
                    unit = line.unit,
                    ingredient_id = line.ingredientId?.value,
                    display_name = line.displayName,
                    preparation = line.preparation,
                    is_optional = line.isOptional,
                    is_key = line.isKey,
                    linked_recipe_id = line.linkedRecipeId?.value,
                ),
            )
            line.alternatives.forEachIndexed { alternativeIndex, alternative ->
                queries.insertAlternative(
                    DbAlternative(
                        line_id = line.id.value,
                        ingredient_id = alternative.ingredientId.value,
                        note = alternative.note,
                        sort_order = alternativeIndex.toLong(),
                    ),
                )
            }
            insertScopes(ScopeOwnerType.Line, line.id.value, line.variantIds)
        }
        snapshot.steps.forEachIndexed { index, step ->
            queries.insertStep(
                DbStep(
                    id = step.id.value,
                    recipe_id = recipeId,
                    section_id = step.sectionId?.value,
                    sort_order = index.toLong(),
                    text = step.text,
                    image_path = step.imagePath,
                    timer_seconds = step.timerSeconds?.toLong(),
                ),
            )
            insertScopes(ScopeOwnerType.Step, step.id.value, step.variantIds)
        }
        snapshot.tips.forEachIndexed { index, tip ->
            queries.insertTip(DbTip(id = tip.id.value, recipe_id = recipeId, sort_order = index.toLong(), text = tip.text))
            insertScopes(ScopeOwnerType.Tip, tip.id.value, tip.variantIds)
        }
        snapshot.tags.forEach { name ->
            val tagId = queries.selectTagByName(name).executeAsOneOrNull()?.id
                ?: TagId.new().value.also { queries.insertTag(DbTag(id = it, name = name)) }
            queries.insertRecipeTag(DbRecipeTag(recipe_id = recipeId, tag_id = tagId))
        }
    }

    private fun insertScopes(ownerType: String, ownerId: String, variantIds: Set<VariantId>) {
        variantIds.forEach { queries.insertScope(DbScope(owner_type = ownerType, owner_id = ownerId, variant_id = it.value)) }
    }

    suspend fun findOrCreateTag(name: String): TagId = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        queries.selectTagByName(trimmed).executeAsOneOrNull()?.let { return@withContext TagId(it.id) }
        TagId.new().also { queries.insertTag(DbTag(id = it.value, name = trimmed)) }
    }
}

private class RecipeTables(
    val recipes: List<DbRecipe>,
    val variants: List<DbVariant>,
    val sections: List<DbSection>,
    val lines: List<DbLine>,
    val alternatives: List<DbAlternative>,
    val steps: List<DbStep>,
    val tips: List<DbTip>,
    val scopes: List<DbScope>,
    val tags: List<DbTag>,
    val recipeTags: List<DbRecipeTag>,
)

private fun assembleRecords(tables: RecipeTables): List<RecipeRecord> {
    val variantsByRecipe = tables.variants.groupBy { it.recipe_id }
    val sectionsByRecipe = tables.sections.groupBy { it.recipe_id }
    val linesByRecipe = tables.lines.groupBy { it.recipe_id }
    val alternativesByLine = tables.alternatives.groupBy { it.line_id }
    val stepsByRecipe = tables.steps.groupBy { it.recipe_id }
    val tipsByRecipe = tables.tips.groupBy { it.recipe_id }
    val scopesByOwner = tables.scopes.groupBy({ it.owner_type to it.owner_id }, { VariantId(it.variant_id) })
    val tagNames = tables.tags.associate { it.id to it.name }
    val tagsByRecipe = tables.recipeTags.groupBy({ it.recipe_id }, { tagNames[it.tag_id] })

    fun scopes(ownerType: String, ownerId: String) = scopesByOwner[ownerType to ownerId].orEmpty().toSet()

    return tables.recipes.mapNotNull { recipe ->
        val variants = variantsByRecipe[recipe.id].orEmpty().map {
            RecipeVariant(id = VariantId(it.id), name = it.name, description = it.description, isDefault = it.is_default)
        }
        if (variants.isEmpty()) return@mapNotNull null
        RecipeRecord(
            snapshot = RecipeSnapshot(
                id = RecipeId(recipe.id),
                title = recipe.title,
                description = recipe.description,
                source = RecipeSource(
                    type = recipe.source_type.toEnumOr(SourceType.MANUAL),
                    url = recipe.source_url,
                    book = recipe.source_book,
                    author = recipe.source_author,
                    page = recipe.source_page,
                ),
                headerImagePath = recipe.header_image_path,
                yieldText = recipe.yield_text,
                prepMinutes = recipe.prep_minutes?.toInt(),
                cookMinutes = recipe.cook_minutes?.toInt(),
                seasonOverride = recipe.season_override_months?.let { MonthSet(it.toInt()) },
                dietOverride = recipe.diet_override.toEnumOrNull<DietClass>(),
                variants = variants,
                sections = sectionsByRecipe[recipe.id].orEmpty().map { RecipeSection(SectionId(it.id), it.name) },
                lines = linesByRecipe[recipe.id].orEmpty().map { line ->
                    IngredientLine(
                        id = LineId(line.id),
                        sectionId = line.section_id?.let(::SectionId),
                        rawText = line.raw_text,
                        amountText = line.amount_text,
                        unit = line.unit,
                        ingredientId = line.ingredient_id?.let(::IngredientId),
                        displayName = line.display_name,
                        preparation = line.preparation,
                        isOptional = line.is_optional,
                        isKey = line.is_key,
                        alternatives = alternativesByLine[line.id].orEmpty().map {
                            LineAlternative(ingredientId = IngredientId(it.ingredient_id), note = it.note)
                        },
                        variantIds = scopes(ScopeOwnerType.Line, line.id),
                        linkedRecipeId = line.linked_recipe_id?.let(::RecipeId),
                    )
                },
                steps = stepsByRecipe[recipe.id].orEmpty().map { step ->
                    RecipeStep(
                        id = StepId(step.id),
                        sectionId = step.section_id?.let(::SectionId),
                        text = step.text,
                        imagePath = step.image_path,
                        timerSeconds = step.timer_seconds?.toInt(),
                        variantIds = scopes(ScopeOwnerType.Step, step.id),
                    )
                },
                tips = tipsByRecipe[recipe.id].orEmpty().map { tip ->
                    RecipeTip(id = TipId(tip.id), text = tip.text, variantIds = scopes(ScopeOwnerType.Tip, tip.id))
                },
                tags = tagsByRecipe[recipe.id].orEmpty().filterNotNull().sortedBy { it.lowercase() },
            ),
            currentRevision = recipe.current_revision.toInt(),
            createdAt = recipe.created_at,
            updatedAt = recipe.updated_at,
        )
    }
}

private fun DbRevision.toEntity(): RecipeRevision? = runCatching {
    RecipeRevision(
        id = RevisionId(id),
        recipeId = RecipeId(recipe_id),
        number = number.toInt(),
        createdAt = created_at,
        summary = summary,
        snapshot = SnapshotJson.decodeFromString(RecipeSnapshot.serializer(), snapshot_json),
    )
}.getOrNull()

// Keeps exactly one default variant, drops scopes to removed variants and sorts tags, so equal recipes compare equal
fun RecipeSnapshot.normalized(): RecipeSnapshot {
    val variants = variants.ifEmpty { listOf(RecipeVariant(VariantId.new(), "Default", isDefault = true)) }
    val defaultId = (variants.firstOrNull { it.isDefault } ?: variants.first()).id
    val variantIds = variants.map { it.id }.toSet()
    val sectionIds = sections.map { it.id }.toSet()

    fun Set<VariantId>.cleaned() = (this intersect variantIds).let { if (it.size == variantIds.size) emptySet() else it }

    return copy(
        title = title.trim(),
        description = description.trim(),
        variants = variants.map { it.copy(name = it.name.trim(), isDefault = it.id == defaultId) },
        lines = lines.filter { it.rawText.isNotBlank() || it.displayName.isNotBlank() }.map {
            it.copy(
                variantIds = it.variantIds.cleaned(),
                sectionId = it.sectionId?.takeIf { id -> id in sectionIds },
                alternatives = it.alternatives.distinctBy { alternative -> alternative.ingredientId },
            )
        },
        steps = steps.filter { it.text.isNotBlank() }.map {
            it.copy(text = it.text.trim(), variantIds = it.variantIds.cleaned(), sectionId = it.sectionId?.takeIf { id -> id in sectionIds })
        },
        tips = tips.filter { it.text.isNotBlank() }.map { it.copy(text = it.text.trim(), variantIds = it.variantIds.cleaned()) },
        tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
    )
}

// An old revision can point at ingredients or recipes deleted since; those links are dropped, the line text stays
private fun RecipeSnapshot.withExistingReferences(knownIngredients: Set<String>, knownRecipes: Set<String>): RecipeSnapshot = copy(
    lines = lines.map { line ->
        line.copy(
            ingredientId = line.ingredientId?.takeIf { it.value in knownIngredients },
            alternatives = line.alternatives.filter { it.ingredientId.value in knownIngredients },
            linkedRecipeId = line.linkedRecipeId?.takeIf { it.value in knownRecipes && it != id },
        )
    },
)

fun RecipeSnapshot.imagePaths(): List<String> = listOfNotNull(headerImagePath) + steps.mapNotNull { it.imagePath }

fun RecipeSnapshot.withFreshIds(): RecipeSnapshot {
    val variantIds = variants.associate { it.id to VariantId.new() }
    val sectionIds = sections.associate { it.id to SectionId.new() }
    fun Set<VariantId>.remapped() = mapNotNull { variantIds[it] }.toSet()
    return copy(
        id = RecipeId.new(),
        variants = variants.map { it.copy(id = variantIds.getValue(it.id)) },
        sections = sections.map { it.copy(id = sectionIds.getValue(it.id)) },
        lines = lines.map { it.copy(id = LineId.new(), sectionId = it.sectionId?.let(sectionIds::get), variantIds = it.variantIds.remapped()) },
        steps = steps.map { it.copy(id = StepId.new(), sectionId = it.sectionId?.let(sectionIds::get), variantIds = it.variantIds.remapped()) },
        tips = tips.map { it.copy(id = TipId.new(), variantIds = it.variantIds.remapped()) },
    )
}
