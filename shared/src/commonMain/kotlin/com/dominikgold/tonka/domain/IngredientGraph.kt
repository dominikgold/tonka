package com.dominikgold.tonka.domain

import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.IngredientEdge
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.IngredientSubstitution
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.Seasonality
import kotlinx.datetime.Month

// In-memory view of the ingredient DAG, rebuilt whenever the ingredient tables change
class IngredientGraph(
    val ingredients: List<Ingredient>,
    aliases: Map<IngredientId, List<String>>,
    edges: List<IngredientEdge>,
    val substitutions: List<IngredientSubstitution>,
) {

    val byId: Map<IngredientId, Ingredient> = ingredients.associateBy { it.id }
    val aliases: Map<IngredientId, List<String>> = aliases

    private val parentsById: Map<IngredientId, List<IngredientId>> =
        edges.groupBy({ it.childId }, { it.parentId })
    private val childrenById: Map<IngredientId, List<IngredientId>> =
        edges.groupBy({ it.parentId }, { it.childId })

    // Precomputed once per graph; the graph is read from several coroutines at a time
    private val ancestorsById: Map<IngredientId, List<IngredientId>> by lazy {
        ingredients.associate { it.id to collectTransitive(it.id, parentsById) }
    }
    private val descendantsById: Map<IngredientId, List<IngredientId>> by lazy {
        ingredients.associate { it.id to collectTransitive(it.id, childrenById) }
    }
    private val seasonalById: Map<IngredientId, Boolean> by lazy {
        ingredients.associate { it.id to computeIsSeasonal(it) }
    }
    private val dietById: Map<IngredientId, DietClass> by lazy {
        ingredients.associate { it.id to computeDietClass(it) }
    }

    val reviewQueue: List<Ingredient> get() = ingredients.filter { it.needsReview }.sortedBy { it.name.lowercase() }

    operator fun get(id: IngredientId?): Ingredient? = id?.let { byId[it] }

    fun parents(id: IngredientId): List<Ingredient> = parentsById[id].orEmpty().mapNotNull { byId[it] }

    fun children(id: IngredientId): List<Ingredient> = childrenById[id].orEmpty().mapNotNull { byId[it] }

    fun ancestorIds(id: IngredientId): List<IngredientId> = ancestorsById[id].orEmpty()

    fun descendantIds(id: IngredientId): List<IngredientId> = descendantsById[id].orEmpty()

    fun ancestors(id: IngredientId): List<Ingredient> = ancestorIds(id).mapNotNull { byId[it] }

    fun descendants(id: IngredientId): List<Ingredient> = descendantIds(id).mapNotNull { byId[it] }

    fun examples(categoryId: IngredientId): List<Ingredient> =
        descendants(categoryId).filter { it.kind == IngredientKind.INGREDIENT }.sortedBy { it.name.lowercase() }

    fun roots(): List<Ingredient> = ingredients.filter { parentsById[it.id].isNullOrEmpty() && it.kind == IngredientKind.CATEGORY }
        .sortedBy { it.name.lowercase() }

    fun topLevelCategory(id: IngredientId): Ingredient? {
        val ingredient = byId[id] ?: return null
        val roots = (ancestorIds(id) + id).mapNotNull { byId[it] }
            .filter { parentsById[it.id].isNullOrEmpty() && it.kind == IngredientKind.CATEGORY }
        return roots.minByOrNull { it.name.lowercase() } ?: ingredient.takeIf { it.kind == IngredientKind.CATEGORY }
    }

    // Every path from a root category down to the node's direct parent, e.g. [Vegetables, Root vegetables]
    fun categoryPaths(id: IngredientId): List<List<Ingredient>> {
        val parents = parents(id)
        if (parents.isEmpty()) return emptyList()
        return parents.flatMap { parent ->
            val upper = categoryPaths(parent.id)
            if (upper.isEmpty()) listOf(listOf(parent)) else upper.map { it + parent }
        }.sortedBy { path -> path.joinToString { it.name } }
    }

    fun categoryPathLabel(id: IngredientId): String =
        categoryPaths(id).firstOrNull()?.joinToString(" › ") { it.name }.orEmpty()

    fun siblings(id: IngredientId): List<Ingredient> = parentsById[id].orEmpty()
        .flatMap { childrenById[it].orEmpty() }
        .distinct()
        .filter { it != id }
        .mapNotNull { byId[it] }
        .filter { it.kind == IngredientKind.INGREDIENT }
        .sortedBy { it.name.lowercase() }

    // Swaps defined on the ingredient itself or any ancestor category, as (target, substitution) pairs
    fun substitutionsFor(id: IngredientId): List<Pair<Ingredient, IngredientSubstitution>> {
        val sources = setOf(id) + ancestorIds(id)
        return substitutions.mapNotNull { substitution ->
            when {
                substitution.fromId in sources -> byId[substitution.toId]?.let { it to substitution }
                substitution.isBidirectional && substitution.toId in sources ->
                    byId[substitution.fromId]?.let { it to substitution }
                else -> null
            }
        }.filter { it.first.id != id }.distinctBy { it.first.id }
    }

    fun isSeasonal(id: IngredientId): Boolean = seasonalById[id] ?: false

    // 1.0 fresh, 0.5 from storage, 0 otherwise; a category takes its best descendant's status
    fun monthStatus(id: IngredientId, month: Month): Double {
        val ingredient = byId[id] ?: return 0.0
        return when (ingredient.kind) {
            IngredientKind.INGREDIENT -> ingredientMonthStatus(ingredient, month)
            IngredientKind.CATEGORY -> (listOf(ingredient) + descendants(id))
                .maxOfOrNull { ingredientMonthStatus(it, month) } ?: 0.0
        }
    }

    fun isFresh(id: IngredientId, month: Month): Boolean = monthStatus(id, month) >= Tuning.FRESH_STATUS

    fun effectiveFreshMonths(id: IngredientId): MonthSet = effectiveMonths(id) { it.freshMonths }

    fun effectiveStorageMonths(id: IngredientId): MonthSet = effectiveMonths(id) { it.storageMonths }

    fun dietClass(id: IngredientId): DietClass = dietById[id] ?: DietClass.UNKNOWN

    private fun computeIsSeasonal(ingredient: Ingredient): Boolean = when (ingredient.kind) {
        IngredientKind.INGREDIENT -> ingredient.seasonality == Seasonality.SEASONAL
        IngredientKind.CATEGORY -> ingredient.seasonality == Seasonality.SEASONAL ||
            descendants(ingredient.id).any { it.kind == IngredientKind.INGREDIENT && it.seasonality == Seasonality.SEASONAL }
    }

    // A category with an UNKNOWN class falls back to the most restrictive class among its descendants
    private fun computeDietClass(ingredient: Ingredient): DietClass {
        if (ingredient.kind == IngredientKind.INGREDIENT || ingredient.dietClass != DietClass.UNKNOWN) {
            return ingredient.dietClass
        }
        val classes = descendants(ingredient.id).filter { it.kind == IngredientKind.INGREDIENT }.map { it.dietClass }
        return if (classes.isEmpty() || DietClass.UNKNOWN in classes) DietClass.UNKNOWN else classes.maxBy { it.ordinal }
    }

    private fun effectiveMonths(id: IngredientId, months: (Ingredient) -> MonthSet): MonthSet {
        val ingredient = byId[id] ?: return MonthSet.None
        if (ingredient.kind == IngredientKind.INGREDIENT) return months(ingredient)
        return (listOf(ingredient) + descendants(id)).fold(MonthSet.None) { set, it -> set + months(it) }
    }

    private fun ingredientMonthStatus(ingredient: Ingredient, month: Month): Double = when {
        ingredient.seasonality != Seasonality.SEASONAL -> 0.0
        month in ingredient.freshMonths -> Tuning.FRESH_STATUS
        month in ingredient.storageMonths -> Tuning.STORAGE_STATUS
        else -> 0.0
    }

    private fun collectTransitive(id: IngredientId, links: Map<IngredientId, List<IngredientId>>): List<IngredientId> {
        val result = LinkedHashSet<IngredientId>()
        val queue = ArrayDeque(links[id].orEmpty())
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (next == id || !result.add(next)) continue
            queue.addAll(links[next].orEmpty())
        }
        return result.toList()
    }

    companion object {
        val Empty = IngredientGraph(emptyList(), emptyMap(), emptyList(), emptyList())
    }
}
