package com.dominikgold.tonka.navigation

import kotlinx.serialization.Serializable

@Serializable
data object RecipesRoute

@Serializable
data object SeasonsRoute

@Serializable
data object MealPlanRoute

@Serializable
data class SettingsRoute(val startImport: Boolean = false)

@Serializable
data class RecipeDetailRoute(val recipeId: String, val variantId: String? = null)

// Either a draft (new or imported recipe) or an existing recipe to edit
@Serializable
data class RecipeEditorRoute(val draftId: String? = null, val recipeId: String? = null)

@Serializable
data class RevisionHistoryRoute(val recipeId: String)

@Serializable
data class RevisionDetailRoute(val recipeId: String, val number: Int)

@Serializable
data object TextImportRoute

@Serializable
data class WebImportRoute(val url: String? = null)

@Serializable
data object ScanImportRoute

@Serializable
data class IngredientsRoute(val reviewOnly: Boolean = false)

@Serializable
data class IngredientDetailRoute(val ingredientId: String)

@Serializable
data class IngredientEditorRoute(val ingredientId: String? = null)

@Serializable
data object WeekdayThemesRoute

enum class TopLevelDestination(val route: Any, val label: String) {
    Recipes(RecipesRoute, "Recipes"),
    Seasons(SeasonsRoute, "Seasons"),
    MealPlan(MealPlanRoute, "Meal Plan"),
}
