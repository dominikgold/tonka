package com.dominikgold.tonka.di

import com.dominikgold.tonka.feature.editor.RecipeEditorViewModel
import com.dominikgold.tonka.feature.importflow.ScanImportViewModel
import com.dominikgold.tonka.feature.importflow.TextImportViewModel
import com.dominikgold.tonka.feature.importflow.WebImportViewModel
import com.dominikgold.tonka.feature.mealplan.MealPlanViewModel
import com.dominikgold.tonka.feature.mealplan.WeekdayThemesViewModel
import com.dominikgold.tonka.feature.recipes.RecipeDetailViewModel
import com.dominikgold.tonka.feature.recipes.RecipesHomeViewModel
import com.dominikgold.tonka.feature.recipes.RevisionDetailViewModel
import com.dominikgold.tonka.feature.recipes.RevisionHistoryViewModel
import com.dominikgold.tonka.feature.seasons.IngredientDetailViewModel
import com.dominikgold.tonka.feature.seasons.IngredientEditorViewModel
import com.dominikgold.tonka.feature.seasons.IngredientsViewModel
import com.dominikgold.tonka.feature.seasons.SeasonsViewModel
import com.dominikgold.tonka.feature.settings.SettingsViewModel
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.navigation.Navigator
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Route arguments arrive as plain values through parametersOf, in the order the Ui passes them
val uiModule = module {
    single { Navigator() }

    viewModel { RecipesHomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { params ->
        RecipeDetailViewModel(
            recipeId = RecipeId(params.values[0] as String),
            initialVariantId = (params.values[1] as String?)?.let(::VariantId),
            isPane = params.values[2] as Boolean,
            indexProvider = get(),
            recipeRepository = get(),
            mealPlanRepository = get(),
            clock = get(),
            navigator = get(),
        )
    }
    viewModel { params -> RevisionHistoryViewModel(RecipeId(params.values[0] as String), get(), get()) }
    viewModel { params -> RevisionDetailViewModel(RecipeId(params.values[0] as String), params.values[1] as Int, get(), get()) }
    viewModel { params ->
        RecipeEditorViewModel(
            draftId = params.values[0] as String?,
            recipeId = params.values[1] as String?,
            draftRepository = get(),
            recipeRepository = get(),
            ingredientRepository = get(),
            importService = get(),
            imageStore = get(),
            indexProvider = get(),
            navigator = get(),
        )
    }
    viewModel { TextImportViewModel(get(), get(), get()) }
    viewModel { params -> WebImportViewModel(params.values[0] as String?, get(), get(), get()) }
    viewModel { ScanImportViewModel(get(), get(), get()) }
    viewModel { SeasonsViewModel(get(), get(), get()) }
    viewModel { params -> IngredientsViewModel(params.values[0] as Boolean, get(), get()) }
    viewModel { params -> IngredientDetailViewModel(IngredientId(params.values[0] as String), get(), get(), get(), get()) }
    viewModel { params -> IngredientEditorViewModel((params.values[0] as String?)?.let(::IngredientId), get(), get()) }
    viewModel { MealPlanViewModel(get(), get(), get(), get(), get()) }
    viewModel { WeekdayThemesViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
}
