package com.dominikgold.tonka.feature.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.data.AppClock
import com.dominikgold.tonka.data.MealPlanRepository
import com.dominikgold.tonka.data.RecipeRepository
import com.dominikgold.tonka.domain.DietFilter
import com.dominikgold.tonka.domain.IndexedRecipe
import com.dominikgold.tonka.domain.RecipeIndexProvider
import com.dominikgold.tonka.domain.RecipeQuery
import com.dominikgold.tonka.domain.SearchHit
import com.dominikgold.tonka.domain.ShoppingList
import com.dominikgold.tonka.domain.ShoppingListBuilder
import com.dominikgold.tonka.domain.SuggestionEngine
import com.dominikgold.tonka.domain.Suggestions
import com.dominikgold.tonka.domain.daysOfWeek
import com.dominikgold.tonka.feature.recipes.RecipeSearchController
import com.dominikgold.tonka.model.MealPlanEntry
import com.dominikgold.tonka.model.MealPlanEntryId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.model.WeekdayTheme
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.RecipeDetailRoute
import com.dominikgold.tonka.navigation.SettingsRoute
import com.dominikgold.tonka.navigation.WeekdayThemesRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class WeekChoice(val label: String) {
    ThisWeek("This week"),
    NextWeek("Next week"),
}

enum class AddMealTab { Suggestions, Search }

data class EntryUi(
    val entry: MealPlanEntry,
    val recipe: IndexedRecipe?,
) {
    val title: String get() = recipe?.title ?: entry.titleSnapshot
    val variantName: String?
        get() = recipe?.snapshot?.takeIf { it.variants.size > 1 }?.variant(entry.variantId)?.name
}

data class DayUi(
    val date: LocalDate,
    val isPast: Boolean,
    val isToday: Boolean,
    val theme: WeekdayTheme?,
    val entries: List<EntryUi>,
)

data class AddMealState(
    val date: LocalDate,
    val tab: AddMealTab = AddMealTab.Suggestions,
    val shuffleSeed: Int = 0,
)

data class MealPlanState(
    val week: WeekChoice = WeekChoice.ThisWeek,
    val days: List<DayUi> = emptyList(),
    val upcomingDays: List<LocalDate> = emptyList(),
    val shoppingList: ShoppingList = ShoppingList(emptyList()),
    val addMeal: AddMealState? = null,
    val suggestions: Suggestions? = null,
    val query: RecipeQuery = RecipeQuery(),
    val searchResults: List<SearchHit> = emptyList(),
    val tagUsage: List<Pair<String, Int>> = emptyList(),
)

interface MealPlanActions {
    fun onWeekSelected(week: WeekChoice)
    fun onAddClicked(date: LocalDate)
    fun onAddSheetDismissed()
    fun onAddTabSelected(tab: AddMealTab)
    fun onShuffleClicked()
    fun onRecipePicked(recipeId: RecipeId, variantId: VariantId?)
    fun onFreeTextAdded(text: String)
    fun onSearchTextChanged(text: String)
    fun onDietSelected(diet: DietFilter)
    fun onTagsChanged(tags: Set<String>)
    fun onClearFiltersClicked()
    fun onEntryOpenClicked(entry: MealPlanEntry)
    fun onEntryVariantChanged(entryId: MealPlanEntryId, variantId: VariantId)
    fun onEntryMoved(entryId: MealPlanEntryId, date: LocalDate)
    fun onEntryRemoved(entryId: MealPlanEntryId)
    fun onThemesClicked()
    fun onSettingsClicked()
}

class MealPlanViewModel(
    indexProvider: RecipeIndexProvider,
    private val mealPlanRepository: MealPlanRepository,
    recipeRepository: RecipeRepository,
    private val clock: AppClock,
    private val navigator: Navigator,
) : ViewModel(), MealPlanActions {

    private val index = indexProvider.index
    private val search = RecipeSearchController(viewModelScope, index)
    private val week = MutableStateFlow(WeekChoice.ThisWeek)
    private val addMeal = MutableStateFlow<AddMealState?>(null)

    private val planState = combine(index, mealPlanRepository.themes, week) { index, themes, week ->
        val today = clock.today()
        val thisWeek = today.daysOfWeek()
        val days = if (week == WeekChoice.ThisWeek) thisWeek else thisWeek.last().plus(DatePeriod(days = 1)).daysOfWeek()
        val entriesByDate = index.entries.groupBy { it.date }
        Triple(
            days.map { date ->
                DayUi(
                    date = date,
                    isPast = date < today,
                    isToday = date == today,
                    theme = themes[date.dayOfWeek],
                    entries = entriesByDate[date].orEmpty().sortedBy { it.sortOrder }.map { EntryUi(it, index[it.recipeId]) },
                )
            },
            ShoppingListBuilder.build(index, index.entries, today),
            thisWeek + thisWeek.last().plus(DatePeriod(days = 1)).daysOfWeek(),
        )
    }

    private val suggestions = combine(index, mealPlanRepository.themes, recipeRepository.tags, addMeal) { index, themes, tags, addMeal ->
        addMeal?.let {
            SuggestionEngine.suggest(
                index = index,
                date = it.date,
                theme = themes[it.date.dayOfWeek],
                tagNamesById = tags.associate { tag -> tag.id.value to tag.name },
                shuffleSeed = it.shuffleSeed,
            )
        }
    }

    val state: StateFlow<MealPlanState> = combine(
        planState,
        addMeal,
        suggestions,
        search.query,
        search.results,
    ) { (days, shoppingList, upcomingDays), addMeal, suggestions, query, results ->
        MealPlanState(
            week = week.value,
            days = days,
            upcomingDays = upcomingDays,
            shoppingList = shoppingList,
            addMeal = addMeal,
            suggestions = suggestions,
            query = query,
            searchResults = results,
            tagUsage = index.value.tagUsage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPlanState())

    override fun onWeekSelected(week: WeekChoice) {
        this.week.value = week
    }

    override fun onAddClicked(date: LocalDate) {
        addMeal.value = AddMealState(date = date)
    }

    override fun onAddSheetDismissed() {
        addMeal.value = null
        search.onClearClicked()
    }

    override fun onAddTabSelected(tab: AddMealTab) = addMeal.update { it?.copy(tab = tab) }

    override fun onShuffleClicked() = addMeal.update { it?.copy(shuffleSeed = it.shuffleSeed + 1) }

    // A recipe with several variants starts on the variant that scores best for that day's month
    override fun onRecipePicked(recipeId: RecipeId, variantId: VariantId?) {
        val date = addMeal.value?.date ?: return
        val recipe = index.value[recipeId] ?: return
        val chosenVariant = variantId ?: recipe.season(date.month).bestVariantId
        onAddSheetDismissed()
        viewModelScope.launch { mealPlanRepository.addRecipe(date, recipeId, chosenVariant, recipe.title) }
    }

    override fun onFreeTextAdded(text: String) {
        val date = addMeal.value?.date ?: return
        if (text.isBlank()) return
        onAddSheetDismissed()
        viewModelScope.launch { mealPlanRepository.addNote(date, text) }
    }

    override fun onSearchTextChanged(text: String) = search.onTextChanged(text)

    override fun onDietSelected(diet: DietFilter) = search.onDietSelected(diet)

    override fun onTagsChanged(tags: Set<String>) = search.onTagsChanged(tags)

    override fun onClearFiltersClicked() = search.onClearClicked()

    override fun onEntryOpenClicked(entry: MealPlanEntry) {
        val recipeId = entry.recipeId ?: return
        navigator.navigateTo(RecipeDetailRoute(recipeId.value, entry.variantId?.value))
    }

    override fun onEntryVariantChanged(entryId: MealPlanEntryId, variantId: VariantId) {
        viewModelScope.launch { mealPlanRepository.changeVariant(entryId, variantId) }
    }

    override fun onEntryMoved(entryId: MealPlanEntryId, date: LocalDate) {
        viewModelScope.launch { mealPlanRepository.move(entryId, date) }
    }

    override fun onEntryRemoved(entryId: MealPlanEntryId) {
        viewModelScope.launch { mealPlanRepository.remove(entryId) }
    }

    override fun onThemesClicked() = navigator.navigateTo(WeekdayThemesRoute)

    override fun onSettingsClicked() = navigator.navigateTo(SettingsRoute())
}
