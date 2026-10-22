package com.dominikgold.tonka.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dominikgold.tonka.feature.editor.RecipeEditorUi
import com.dominikgold.tonka.feature.importflow.ScanImportUi
import com.dominikgold.tonka.feature.importflow.TextImportUi
import com.dominikgold.tonka.feature.importflow.WebImportUi
import com.dominikgold.tonka.feature.mealplan.MealPlanUi
import com.dominikgold.tonka.feature.mealplan.WeekdayThemesUi
import com.dominikgold.tonka.feature.recipes.RecipeDetailUi
import com.dominikgold.tonka.feature.recipes.RecipesHomeUi
import com.dominikgold.tonka.feature.recipes.RevisionDetailUi
import com.dominikgold.tonka.feature.recipes.RevisionHistoryUi
import com.dominikgold.tonka.feature.seasons.IngredientDetailUi
import com.dominikgold.tonka.feature.seasons.IngredientEditorUi
import com.dominikgold.tonka.feature.seasons.IngredientsUi
import com.dominikgold.tonka.feature.seasons.SeasonsUi
import com.dominikgold.tonka.feature.settings.SettingsUi
import com.dominikgold.tonka.platform.isDesktop
import org.koin.compose.koinInject

private val TopLevelDestination.icon: ImageVector
    get() = when (this) {
        TopLevelDestination.Recipes -> Icons.AutoMirrored.Rounded.MenuBook
        TopLevelDestination.Seasons -> Icons.Rounded.Eco
        TopLevelDestination.MealPlan -> Icons.Rounded.CalendarMonth
    }

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navigator = koinInject<Navigator>()

    LaunchedEffect(navigator, navController) {
        navigator.commands.collect { command -> navController.apply(command) }
    }

    val sharedText by IncomingShares.sharedText.collectAsState()
    LaunchedEffect(sharedText) {
        val text = sharedText ?: return@LaunchedEffect
        IncomingShares.extractUrl(text)?.let { navController.navigate(WebImportRoute(it)) }
        IncomingShares.consume()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { topLevel ->
        destination?.hierarchy?.any { it.hasRoute(topLevel.route::class) } == true
    }
    val adaptiveLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { topLevel ->
                item(
                    selected = currentTopLevel == topLevel,
                    onClick = {
                        navController.navigate(topLevel.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(topLevel.icon, contentDescription = null) },
                    label = { Text(topLevel.label) },
                )
            }
        },
        // Detail screens take the whole window; desktop windows always get the rail
        layoutType = when {
            currentTopLevel == null -> NavigationSuiteType.None
            isDesktop -> NavigationSuiteType.NavigationRail
            else -> adaptiveLayout
        },
    ) {
        NavHost(navController = navController, startDestination = RecipesRoute) {
            composable<RecipesRoute> { RecipesHomeUi() }
            composable<SeasonsRoute> { SeasonsUi() }
            composable<MealPlanRoute> { MealPlanUi() }
            composable<SettingsRoute> { entry -> SettingsUi(startImport = entry.toRoute<SettingsRoute>().startImport) }
            composable<RecipeDetailRoute> { entry ->
                val route = entry.toRoute<RecipeDetailRoute>()
                RecipeDetailUi(recipeId = route.recipeId, initialVariantId = route.variantId)
            }
            composable<RecipeEditorRoute> { entry ->
                val route = entry.toRoute<RecipeEditorRoute>()
                RecipeEditorUi(draftId = route.draftId, recipeId = route.recipeId)
            }
            composable<RevisionHistoryRoute> { entry -> RevisionHistoryUi(entry.toRoute<RevisionHistoryRoute>().recipeId) }
            composable<RevisionDetailRoute> { entry ->
                val route = entry.toRoute<RevisionDetailRoute>()
                RevisionDetailUi(recipeId = route.recipeId, number = route.number)
            }
            composable<TextImportRoute> { TextImportUi() }
            composable<WebImportRoute> { entry -> WebImportUi(entry.toRoute<WebImportRoute>().url) }
            composable<ScanImportRoute> { ScanImportUi() }
            composable<IngredientsRoute> { entry -> IngredientsUi(entry.toRoute<IngredientsRoute>().reviewOnly) }
            composable<IngredientDetailRoute> { entry -> IngredientDetailUi(entry.toRoute<IngredientDetailRoute>().ingredientId) }
            composable<IngredientEditorRoute> { entry -> IngredientEditorUi(entry.toRoute<IngredientEditorRoute>().ingredientId) }
            composable<WeekdayThemesRoute> { WeekdayThemesUi() }
        }
    }
}

private fun NavHostController.apply(command: NavigationCommand) {
    when (command) {
        is NavigationCommand.NavigateTo -> navigate(command.route) {
            if (command.replaceCurrent) {
                currentDestination?.id?.let { popUpTo(it) { inclusive = true } }
            }
        }
        NavigationCommand.GoBack -> popBackStack()
    }
}
