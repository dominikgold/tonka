package com.dominikgold.tonka.domain

import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.VariantId
import kotlinx.datetime.Month

data class RecipeDiet(
    val byVariant: Map<VariantId, DietClass>,
    // The most permissive class across variants, used by the diet filter
    val filterClass: DietClass,
    val defaultVariantClass: DietClass,
) {

    val badge: DietBadge? get() = when {
        filterClass == DietClass.UNKNOWN -> DietBadge.Unverified
        filterClass == DietClass.VEGAN && defaultVariantClass == DietClass.VEGAN -> DietBadge.Vegan
        filterClass == DietClass.VEGAN -> DietBadge.VeganOption
        filterClass == DietClass.VEGETARIAN && defaultVariantClass == DietClass.VEGETARIAN -> DietBadge.Vegetarian
        filterClass == DietClass.VEGETARIAN -> DietBadge.VegetarianOption
        else -> null
    }

    fun qualifyingVariant(required: DietClass): VariantId? = byVariant.entries
        .filter { it.value != DietClass.UNKNOWN && it.value.ordinal <= required.ordinal }
        .minByOrNull { it.value.ordinal }
        ?.key
}

enum class DietBadge { Vegan, VeganOption, Vegetarian, VegetarianOption, Unverified }

data class VariantSeason(
    val score: Double?,
    val isInSeason: Boolean,
    val freshIngredients: List<Ingredient>,
)

data class RecipeSeason(
    val month: Month,
    val score: Double?,
    val isInSeason: Boolean,
    val bestVariantId: VariantId,
    val freshIngredients: List<Ingredient>,
)

class RecipeAnalysis(
    private val graph: IngredientGraph,
    // Resolves recipes linked from ingredient lines
    private val recipes: (RecipeId) -> RecipeSnapshot? = { null },
) {

    fun diet(snapshot: RecipeSnapshot, visited: Set<RecipeId> = setOf(snapshot.id)): RecipeDiet {
        val byVariant = snapshot.variants.associate { variant ->
            variant.id to (snapshot.dietOverride ?: variantDiet(snapshot, variant.id, visited))
        }
        val known = byVariant.values.filter { it != DietClass.UNKNOWN }
        val filterClass = known.minByOrNull { it.ordinal } ?: DietClass.UNKNOWN
        return RecipeDiet(
            byVariant = byVariant,
            filterClass = filterClass,
            defaultVariantClass = byVariant[snapshot.defaultVariant.id] ?: DietClass.UNKNOWN,
        )
    }

    // The least permissive class among non-optional lines; any unresolved or unknown line makes it UNKNOWN.
    // A linked recipe counts with its own default variant class, so a diet set by hand on a base recipe carries over.
    fun variantDiet(
        snapshot: RecipeSnapshot,
        variantId: VariantId,
        visited: Set<RecipeId> = setOf(snapshot.id),
    ): DietClass {
        val lines = snapshot.effectiveLines(variantId).filter { !it.isOptional }
        if (lines.isEmpty()) return DietClass.UNKNOWN
        var result = DietClass.VEGAN
        for (line in lines) {
            val lineClass = when (val linkedId = line.linkedRecipeId) {
                null -> graph.dietClass(line.ingredientId ?: return DietClass.UNKNOWN)
                in visited -> continue
                else -> recipes(linkedId)?.let { diet(it, visited + linkedId).defaultVariantClass } ?: DietClass.UNKNOWN
            }
            if (lineClass == DietClass.UNKNOWN) return DietClass.UNKNOWN
            if (lineClass.ordinal > result.ordinal) result = lineClass
        }
        return result
    }

    fun variantSeason(snapshot: RecipeSnapshot, variantId: VariantId, month: Month): VariantSeason {
        val relevant = snapshot.expandedLines(variantId, recipes).filter { line ->
            !line.isOptional && line.ingredientId?.let(graph::isSeasonal) == true
        }
        val statuses = relevant.map { line ->
            val status = graph.monthStatus(line.ingredientId!!, month)
            val weight = if (line.isKey) Tuning.KEY_INGREDIENT_WEIGHT else Tuning.REGULAR_INGREDIENT_WEIGHT
            Triple(line, status, weight)
        }
        val derived = statuses.takeIf { it.isNotEmpty() }?.let { list ->
            list.sumOf { it.second * it.third } / list.sumOf { it.third }
        }
        val seasonTag = snapshot.seasonOverride?.takeIf { !it.isEmpty }
        val score = when {
            seasonTag == null -> derived
            month in seasonTag -> 1.0
            else -> derived?.times(Tuning.SEASON_TAG_EXCLUDED_FACTOR)
        }
        val freshLines = statuses.filter { it.second >= Tuning.FRESH_STATUS }
        val hasFreshLineOrTagMatch = freshLines.isNotEmpty() || (seasonTag != null && month in seasonTag)
        return VariantSeason(
            score = score,
            isInSeason = score != null && score >= Tuning.IN_SEASON_THRESHOLD && hasFreshLineOrTagMatch,
            freshIngredients = freshLines
                .sortedByDescending { it.first.isKey }
                .mapNotNull { graph[it.first.ingredientId] }
                .distinctBy { it.id },
        )
    }

    fun season(snapshot: RecipeSnapshot, month: Month): RecipeSeason {
        val perVariant = snapshot.variants.map { it.id to variantSeason(snapshot, it.id, month) }
        val best = perVariant.maxByOrNull { it.second.score ?: -1.0 }
            ?.takeIf { it.second.score != null }
            ?: (snapshot.defaultVariant.id to perVariant.first { it.first == snapshot.defaultVariant.id }.second)
        return RecipeSeason(
            month = month,
            score = best.second.score,
            isInSeason = best.second.isInSeason,
            bestVariantId = best.first,
            freshIngredients = best.second.freshIngredients,
        )
    }
}
