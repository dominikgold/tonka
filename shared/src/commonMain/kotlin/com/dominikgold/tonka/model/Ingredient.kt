package com.dominikgold.tonka.model

import kotlinx.serialization.Serializable

@Serializable
enum class IngredientKind { INGREDIENT, CATEGORY }

// Declared from most to least permissive, so a higher ordinal restricts more
@Serializable
enum class DietClass { VEGAN, VEGETARIAN, OMNI, UNKNOWN }

@Serializable
enum class Seasonality { SEASONAL, YEAR_ROUND, NOT_APPLICABLE, UNKNOWN }

@Serializable
enum class IngredientOrigin { SEED, USER, IMPORT }

data class Ingredient(
    val id: IngredientId,
    val name: String,
    val kind: IngredientKind,
    val dietClass: DietClass,
    val seasonality: Seasonality,
    val freshMonths: MonthSet,
    val storageMonths: MonthSet,
    val isPantryStaple: Boolean,
    val needsReview: Boolean,
    val origin: IngredientOrigin,
    val notes: String,
    val seedKey: String?,
    val isUserEdited: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class IngredientEdge(
    val childId: IngredientId,
    val parentId: IngredientId,
)

data class IngredientSubstitution(
    val id: SubstitutionId,
    val fromId: IngredientId,
    val toId: IngredientId,
    val note: String,
    val isBidirectional: Boolean,
)

// What the ingredient editor writes back; the repository fills timestamps and origin
data class IngredientInput(
    val name: String,
    val kind: IngredientKind,
    val dietClass: DietClass,
    val seasonality: Seasonality,
    val freshMonths: MonthSet,
    val storageMonths: MonthSet,
    val isPantryStaple: Boolean,
    val notes: String,
    val aliases: List<String>,
    val parentIds: List<IngredientId>,
    val needsReview: Boolean = false,
)
