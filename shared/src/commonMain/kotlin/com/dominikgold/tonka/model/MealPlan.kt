package com.dominikgold.tonka.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

data class MealPlanEntry(
    val id: MealPlanEntryId,
    val date: LocalDate,
    val sortOrder: Int,
    val recipeId: RecipeId?,
    val variantId: VariantId?,
    val titleSnapshot: String,
    val note: String,
)

@Serializable
enum class TagMatch { ANY, ALL }

data class WeekdayTheme(
    val weekday: DayOfWeek,
    val name: String,
    val tagMatch: TagMatch,
    val tagIds: Set<TagId>,
)
