package com.dominikgold.tonka.domain

import com.dominikgold.tonka.data.IngredientRepository
import com.dominikgold.tonka.data.MealPlanRepository
import com.dominikgold.tonka.data.RecipeRepository
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.MealPlanEntry
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeRecord
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.VariantId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

enum class DietFilter(val required: DietClass?) {
    All(null),
    Vegetarian(DietClass.VEGETARIAN),
    Vegan(DietClass.VEGAN),
}

data class RecipeQuery(
    val text: String = "",
    val diet: DietFilter = DietFilter.All,
    val tags: Set<String> = emptySet(),
) {
    val isActive: Boolean get() = text.isNotBlank() || diet != DietFilter.All || tags.isNotEmpty()
}

class IndexedRecipe(
    val record: RecipeRecord,
    val diet: RecipeDiet,
    val seasons: Map<Month, RecipeSeason>,
    // Normalized word -> best weight among title (3), ingredient (2) and category (1) words
    val words: Map<String, Int>,
    val normalizedTags: Set<String>,
    // Sorted ascending
    val planDates: List<LocalDate>,
) {
    val id: RecipeId get() = record.snapshot.id
    val snapshot: RecipeSnapshot get() = record.snapshot
    val title: String get() = record.snapshot.title
    val lastPlanned: LocalDate? get() = planDates.lastOrNull()

    fun season(month: Month): RecipeSeason = seasons.getValue(month)

    fun lastPlannedBefore(date: LocalDate): LocalDate? = planDates.lastOrNull { it < date }
}

data class SearchHit(
    val recipe: IndexedRecipe,
    val score: Int,
    // Set when a diet filter matched a variant other than the default
    val matchingVariantId: VariantId?,
)

class RecipeIndex(
    val recipes: List<IndexedRecipe>,
    val graph: IngredientGraph,
    val entries: List<MealPlanEntry>,
    val isLoaded: Boolean,
) {

    val byId: Map<RecipeId, IndexedRecipe> = recipes.associateBy { it.id }

    // Tag name -> number of recipes using it, sorted by name
    val tagUsage: List<Pair<String, Int>> = recipes.flatMap { it.snapshot.tags }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedBy { it.first.lowercase() }

    operator fun get(id: RecipeId?): IndexedRecipe? = id?.let { byId[it] }

    fun snapshot(id: RecipeId): RecipeSnapshot? = byId[id]?.snapshot

    // Recipes that can be linked into [recipeId] without creating a loop
    fun linkableInto(recipeId: RecipeId): List<IndexedRecipe> = recipes.filter { !dependsOn(it.id, recipeId, ::snapshot) }

    fun search(query: RecipeQuery): List<SearchHit> {
        val tokens = TextNormalizer.words(query.text)
        val requiredTags = query.tags.map { TextNormalizer.normalize(it) }.toSet()
        return recipes.mapNotNull { recipe ->
            if (!recipe.normalizedTags.containsAll(requiredTags)) return@mapNotNull null
            val matchingVariant = query.diet.required?.let { required ->
                recipe.diet.qualifyingVariant(required) ?: return@mapNotNull null
            }
            var score = 0
            for (token in tokens) {
                val best = recipe.words.entries.filter { it.key.startsWith(token) }.maxOfOrNull { it.value }
                    ?: return@mapNotNull null
                score += best
            }
            SearchHit(
                recipe = recipe,
                score = score,
                matchingVariantId = matchingVariant?.takeIf { it != recipe.snapshot.defaultVariant.id },
            )
        }.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.recipe.title.lowercase() })
    }

    companion object {
        val Empty = RecipeIndex(emptyList(), IngredientGraph.Empty, emptyList(), isLoaded = false)

        fun build(
            records: List<RecipeRecord>,
            graph: IngredientGraph,
            entries: List<MealPlanEntry>,
        ): RecipeIndex {
            val snapshots = records.associate { it.snapshot.id to it.snapshot }
            val analysis = RecipeAnalysis(graph, snapshots::get)
            val planDatesByRecipe = entries.filter { it.recipeId != null }
                .groupBy({ it.recipeId!! }, { it.date })
                .mapValues { (_, dates) -> dates.sorted() }
            val recipes = records.map { record ->
                val snapshot = record.snapshot
                IndexedRecipe(
                    record = record,
                    diet = analysis.diet(snapshot),
                    seasons = Month.entries.associateWith { analysis.season(snapshot, it) },
                    words = searchWords(snapshot, graph, snapshots::get),
                    normalizedTags = snapshot.tags.map { TextNormalizer.normalize(it) }.toSet(),
                    planDates = planDatesByRecipe[snapshot.id].orEmpty(),
                )
            }.sortedBy { it.title.lowercase() }
            return RecipeIndex(recipes, graph, entries, isLoaded = true)
        }

        // Linked recipes contribute their title and their own ingredients, so "carrot" also finds a dish built on a carrot base
        private fun searchWords(
            snapshot: RecipeSnapshot,
            graph: IngredientGraph,
            lookup: (RecipeId) -> RecipeSnapshot?,
        ): Map<String, Int> {
            val words = mutableMapOf<String, Int>()
            fun add(text: String, weight: Int) {
                TextNormalizer.words(text).forEach { word -> words[word] = maxOf(words[word] ?: 0, weight) }
            }
            add(snapshot.title, Tuning.SEARCH_TITLE_WEIGHT)
            snapshot.lines.mapNotNull { it.linkedRecipeId }.forEach { linkedId ->
                lookup(linkedId)?.let { add(it.title, Tuning.SEARCH_INGREDIENT_WEIGHT) }
            }
            snapshot.variants.flatMap { snapshot.expandedLines(it.id, lookup) }.distinctBy { it.id }.forEach { line ->
                val ingredient = graph[line.ingredientId]
                if (ingredient == null) {
                    add(line.rawText, Tuning.SEARCH_INGREDIENT_WEIGHT)
                    return@forEach
                }
                add(ingredient.name, Tuning.SEARCH_INGREDIENT_WEIGHT)
                graph.aliases[ingredient.id].orEmpty().forEach { add(it, Tuning.SEARCH_INGREDIENT_WEIGHT) }
                graph.ancestors(ingredient.id).forEach { add(it.name, Tuning.SEARCH_CATEGORY_WEIGHT) }
            }
            return words
        }
    }
}

class RecipeIndexProvider(
    recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    mealPlanRepository: MealPlanRepository,
    appScope: CoroutineScope,
) {

    val index: StateFlow<RecipeIndex> = combine(
        recipeRepository.records.filterNotNull(),
        ingredientRepository.graph,
        mealPlanRepository.entries,
    ) { records, graph, entries ->
        RecipeIndex.build(records, graph, entries)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(appScope, SharingStarted.Eagerly, RecipeIndex.Empty)
}
