package com.dominikgold.tonka.feature.seasons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.data.AppClock
import com.dominikgold.tonka.data.IngredientRepository
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.domain.TextNormalizer
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.Seasonality
import com.dominikgold.tonka.model.next
import com.dominikgold.tonka.model.previous
import com.dominikgold.tonka.navigation.IngredientDetailRoute
import com.dominikgold.tonka.navigation.IngredientsRoute
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.SettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Month

enum class SeasonsView { Month, Year }

enum class SeasonMarker { NewThisMonth, LastChance }

data class SeasonTile(
    val ingredient: Ingredient,
    val marker: SeasonMarker?,
)

data class SeasonGroup(
    val name: String,
    val tiles: List<SeasonTile>,
)

data class YearGroup(
    val name: String,
    val ingredients: List<Ingredient>,
)

data class SeasonsState(
    val view: SeasonsView = SeasonsView.Month,
    val month: Month = Month.JANUARY,
    val currentMonth: Month = Month.JANUARY,
    val filter: String = "",
    val freshGroups: List<SeasonGroup> = emptyList(),
    val storageGroups: List<SeasonGroup> = emptyList(),
    val yearGroups: List<YearGroup> = emptyList(),
    val reviewCount: Int = 0,
)

interface SeasonsActions {
    fun onViewSelected(view: SeasonsView)
    fun onMonthSelected(month: Month)
    fun onFilterChanged(filter: String)
    fun onIngredientClicked(ingredientId: IngredientId)
    fun onManageIngredientsClicked()
    fun onSettingsClicked()
}

class SeasonsViewModel(
    ingredientRepository: IngredientRepository,
    clock: AppClock,
    private val navigator: Navigator,
) : ViewModel(), SeasonsActions {

    private val currentMonth = clock.today().month
    private val view = MutableStateFlow(SeasonsView.Month)
    private val month = MutableStateFlow(currentMonth)
    private val filter = MutableStateFlow("")

    val state: StateFlow<SeasonsState> = combine(ingredientRepository.graph, view, month, filter) { graph, view, month, filter ->
        val seasonal = seasonalIngredients(graph, filter)
        val fresh = seasonal.filter { month in it.freshMonths }
        val storage = seasonal.filter { month in it.storageMonths && month !in it.freshMonths }
        SeasonsState(
            view = view,
            month = month,
            currentMonth = currentMonth,
            filter = filter,
            freshGroups = fresh.groupedTiles(graph) { ingredient ->
                when {
                    month.previous() !in ingredient.freshMonths -> SeasonMarker.NewThisMonth
                    month.next() !in ingredient.freshMonths -> SeasonMarker.LastChance
                    else -> null
                }
            },
            storageGroups = storage.groupedTiles(graph) { null },
            yearGroups = seasonal.groupBy { groupName(graph, it) }
                .entries
                .sortedBy { it.key }
                .map { (name, ingredients) -> YearGroup(name, ingredients.sortedBy { it.name.lowercase() }) },
            reviewCount = graph.reviewQueue.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeasonsState(month = currentMonth, currentMonth = currentMonth))

    override fun onViewSelected(view: SeasonsView) {
        this.view.value = view
    }

    override fun onMonthSelected(month: Month) {
        this.month.value = month
    }

    override fun onFilterChanged(filter: String) {
        this.filter.value = filter
    }

    override fun onIngredientClicked(ingredientId: IngredientId) = navigator.navigateTo(IngredientDetailRoute(ingredientId.value))

    override fun onManageIngredientsClicked() = navigator.navigateTo(IngredientsRoute())

    override fun onSettingsClicked() = navigator.navigateTo(SettingsRoute())

    private fun seasonalIngredients(graph: IngredientGraph, filter: String): List<Ingredient> {
        val query = TextNormalizer.normalize(filter)
        return graph.ingredients.filter { ingredient ->
            ingredient.kind == IngredientKind.INGREDIENT && ingredient.seasonality == Seasonality.SEASONAL &&
                (query.isEmpty() || ingredient.matches(graph, query))
        }
    }

    private fun List<Ingredient>.groupedTiles(graph: IngredientGraph, marker: (Ingredient) -> SeasonMarker?): List<SeasonGroup> =
        groupBy { groupName(graph, it) }
            .entries
            .sortedBy { it.key }
            .map { (name, ingredients) -> SeasonGroup(name, ingredients.sortedBy { it.name.lowercase() }.map { SeasonTile(it, marker(it)) }) }
}

fun groupName(graph: IngredientGraph, ingredient: Ingredient): String = graph.topLevelCategory(ingredient.id)?.name ?: "Other"

fun Ingredient.matches(graph: IngredientGraph, normalizedQuery: String): Boolean =
    TextNormalizer.normalize(name).contains(normalizedQuery) ||
        graph.aliases[id].orEmpty().any { TextNormalizer.normalize(it).contains(normalizedQuery) }
