package com.dominikgold.tonka.domain

import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.MealPlanEntry
import com.dominikgold.tonka.model.TagMatch
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.model.WeekdayTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.min

fun LocalDate.mondayOfWeek(): LocalDate = minus(DatePeriod(days = dayOfWeek.isoDayNumber - 1))

fun LocalDate.daysOfWeek(): List<LocalDate> = mondayOfWeek().let { monday -> (0 until 7).map { monday.plus(DatePeriod(days = it)) } }

fun shoppingWindow(today: LocalDate): ClosedRange<LocalDate> =
    today..today.mondayOfWeek().plus(DatePeriod(days = 13))

data class Suggestion(
    val recipe: IndexedRecipe,
    val score: Double,
    val variantId: VariantId,
    val reason: String,
)

data class Suggestions(
    val theme: WeekdayTheme?,
    val primary: List<Suggestion>,
    // "More seasonal ideas" when fewer than five recipes match the day's theme
    val more: List<Suggestion>,
)

object SuggestionEngine {

    fun suggest(
        index: RecipeIndex,
        date: LocalDate,
        theme: WeekdayTheme?,
        tagNamesById: Map<String, String>,
        shuffleSeed: Int,
    ): Suggestions {
        val scored = index.recipes.map { score(it, date, index.entries, shuffleSeed) }
            .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.recipe.title.lowercase() })
        val themeTags = theme?.tagIds?.mapNotNull { tagNamesById[it.value] }?.map { TextNormalizer.normalize(it) }?.toSet().orEmpty()
        if (theme == null || themeTags.isEmpty()) {
            return Suggestions(theme = theme, primary = scored.take(Tuning.SUGGESTION_COUNT), more = emptyList())
        }
        val (themed, unthemed) = scored.partition { suggestion ->
            val tags = suggestion.recipe.normalizedTags
            when (theme.tagMatch) {
                TagMatch.ANY -> themeTags.any { it in tags }
                TagMatch.ALL -> tags.containsAll(themeTags)
            }
        }
        return Suggestions(
            theme = theme,
            primary = themed.take(Tuning.SUGGESTION_COUNT),
            more = if (themed.size < Tuning.THEME_FALLBACK_THRESHOLD) unthemed.take(Tuning.SUGGESTION_COUNT) else emptyList(),
        )
    }

    fun score(recipe: IndexedRecipe, date: LocalDate, entries: List<MealPlanEntry>, shuffleSeed: Int): Suggestion {
        val season = recipe.season(date.month)
        val seasonScore = season.score ?: Tuning.SUGGESTION_SCORE_WITHOUT_SEASON
        val lastPlanned = recipe.lastPlannedBefore(date)
        val recency = lastPlanned?.let {
            min(1.0, it.daysUntil(date).toDouble() / Tuning.RECENCY_RECOVERY_DAYS)
        } ?: 1.0
        val isPlannedNearby = entries.any { entry ->
            entry.recipeId == recipe.id && abs(entry.date.daysUntil(date)) <= Tuning.RECENTLY_PLANNED_WINDOW_DAYS
        }
        val base = Tuning.SUGGESTION_SEASON_WEIGHT * seasonScore + Tuning.SUGGESTION_RECENCY_WEIGHT * recency
        val penalized = if (isPlannedNearby) base * Tuning.RECENTLY_PLANNED_PENALTY else base
        val jitter = jitter(date, recipe.id.value, shuffleSeed) * Tuning.SUGGESTION_MAX_JITTER
        return Suggestion(
            recipe = recipe,
            score = penalized + jitter,
            variantId = season.bestVariantId,
            reason = reason(season, lastPlanned, date),
        )
    }

    private fun reason(season: RecipeSeason, lastPlanned: LocalDate?, date: LocalDate): String {
        val parts = mutableListOf<String>()
        if (season.isInSeason && season.freshIngredients.isNotEmpty()) {
            parts += "In season: " + season.freshIngredients.take(3).joinToString(", ") { it.name.lowercase() }
        }
        parts += when (lastPlanned) {
            null -> "Never planned"
            else -> "last planned ${ageLabel(lastPlanned.daysUntil(date))}"
        }
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }

    private fun ageLabel(days: Int): String = when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 14 -> "$days days ago"
        else -> "${days / 7} weeks ago"
    }

    // Deterministic per (date, recipe, seed) so a day keeps its list until Shuffle reseeds it
    private fun jitter(date: LocalDate, recipeId: String, seed: Int): Double {
        var hash = -0x340d631b7bdddcdbL
        for (char in "$date|$recipeId|$seed") {
            hash = hash xor char.code.toLong()
            hash *= 0x100000001b3L
        }
        return ((hash ushr 11).toDouble() / (1L shl 53).toDouble())
    }
}

data class ShoppingItem(
    val name: String,
    val recipeTitles: List<String>,
    val isOptional: Boolean,
)

data class ShoppingGroup(
    val name: String,
    val items: List<ShoppingItem>,
    val isPantry: Boolean,
)

data class ShoppingList(
    val groups: List<ShoppingGroup>,
) {
    val itemCount: Int get() = groups.sumOf { it.items.size }

    fun toPlainText(): String = groups.joinToString("\n\n") { group ->
        group.name + "\n" + group.items.joinToString("\n") { item ->
            val optional = if (item.isOptional) " (optional)" else ""
            "- ${item.name}$optional"
        }
    }
}

object ShoppingListBuilder {

    private const val OTHER_GROUP = "Other"
    private const val PANTRY_GROUP = "Pantry staples"

    fun build(index: RecipeIndex, entries: List<MealPlanEntry>, today: LocalDate): ShoppingList {
        val window = shoppingWindow(today)
        val graph = index.graph

        class Accumulator(val name: String, val group: String, val isPantry: Boolean) {
            val recipes = linkedSetOf<String>()
            var allOptional = true
        }

        val items = linkedMapOf<String, Accumulator>()
        entries.filter { it.date in window && it.recipeId != null }.sortedBy { it.date }.forEach { entry ->
            val recipe = index[entry.recipeId] ?: return@forEach
            val snapshot = recipe.snapshot
            val variantId = snapshot.variant(entry.variantId).id
            snapshot.effectiveLines(variantId).forEach { line ->
                val ingredient = graph[line.ingredientId]
                val key = ingredient?.id?.value ?: "name:" + TextNormalizer.normalize(line.displayName.ifBlank { line.rawText })
                val accumulator = items.getOrPut(key) {
                    when {
                        ingredient == null -> Accumulator(line.displayName.ifBlank { line.rawText }, OTHER_GROUP, isPantry = false)
                        ingredient.isPantryStaple -> Accumulator(ingredient.displayName(), PANTRY_GROUP, isPantry = true)
                        else -> Accumulator(
                            name = ingredient.displayName(),
                            group = graph.topLevelCategory(ingredient.id)?.name ?: OTHER_GROUP,
                            isPantry = false,
                        )
                    }
                }
                accumulator.recipes += snapshot.title
                if (!line.isOptional) accumulator.allOptional = false
            }
        }

        val groups = items.values.groupBy { it.group }.map { (name, accumulators) ->
            ShoppingGroup(
                name = name,
                items = accumulators.map { ShoppingItem(it.name, it.recipes.toList(), it.allOptional) }.sortedBy { it.name.lowercase() },
                isPantry = accumulators.first().isPantry,
            )
        }
        return ShoppingList(
            groups.filter { !it.isPantry && it.name != OTHER_GROUP }.sortedBy { it.name.lowercase() } +
                groups.filter { it.name == OTHER_GROUP } +
                groups.filter { it.isPantry },
        )
    }

    private fun com.dominikgold.tonka.model.Ingredient.displayName() =
        if (kind == IngredientKind.CATEGORY) "$name (any)" else name
}

fun DayOfWeek.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
