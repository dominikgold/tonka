package com.dominikgold.tonka.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.data.AppClock
import com.dominikgold.tonka.data.DraftRepository
import com.dominikgold.tonka.domain.DietFilter
import com.dominikgold.tonka.domain.IndexedRecipe
import com.dominikgold.tonka.domain.RecipeIndexProvider
import com.dominikgold.tonka.domain.RecipeQuery
import com.dominikgold.tonka.domain.RecipeSeason
import com.dominikgold.tonka.domain.SearchHit
import com.dominikgold.tonka.importing.ImportService
import com.dominikgold.tonka.importing.RecipeDraft
import com.dominikgold.tonka.model.DraftId
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.RecipeEditorRoute
import com.dominikgold.tonka.navigation.ScanImportRoute
import com.dominikgold.tonka.navigation.SettingsRoute
import com.dominikgold.tonka.navigation.TextImportRoute
import com.dominikgold.tonka.navigation.WebImportRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Month

enum class RecipeSort(val label: String) {
    AToZ("A–Z"),
    RecentlyAdded("Recently added"),
    RecentlyPlanned("Recently planned"),
}

enum class AddRecipeOption { Manual, Text, Website, Scan }

data class CarouselItem(
    val recipe: IndexedRecipe,
    val season: RecipeSeason,
)

data class RecipesHomeState(
    val isLoading: Boolean = true,
    val month: Month = Month.JANUARY,
    val carousel: List<CarouselItem> = emptyList(),
    val allRecipes: List<IndexedRecipe> = emptyList(),
    val query: RecipeQuery = RecipeQuery(),
    val results: List<SearchHit> = emptyList(),
    val tagUsage: List<Pair<String, Int>> = emptyList(),
    val sort: RecipeSort = RecipeSort.AToZ,
    val drafts: List<RecipeDraft> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && carousel.isEmpty() && allRecipes.isEmpty()
}

interface RecipesHomeActions {
    fun onSearchTextChanged(text: String)
    fun onDietSelected(diet: DietFilter)
    fun onTagsChanged(tags: Set<String>)
    fun onClearFiltersClicked()
    fun onSortSelected(sort: RecipeSort)
    fun onAddOptionClicked(option: AddRecipeOption)
    fun onImportBackupClicked()
    fun onSettingsClicked()
    fun onResumeDraftClicked(draftId: DraftId)
    fun onDiscardDraftClicked(draftId: DraftId)
}

class RecipesHomeViewModel(
    indexProvider: RecipeIndexProvider,
    private val draftRepository: DraftRepository,
    private val importService: ImportService,
    private val clock: AppClock,
    private val navigator: Navigator,
) : ViewModel(), RecipesHomeActions {

    val search = RecipeSearchController(viewModelScope, indexProvider.index)
    private val sort = MutableStateFlow(RecipeSort.AToZ)

    val state: StateFlow<RecipesHomeState> = combine(
        indexProvider.index,
        search.query,
        search.results,
        sort,
        draftRepository.drafts,
    ) { index, query, results, sort, drafts ->
        val month = clock.today().month
        val carousel = index.recipes
            .map { CarouselItem(it, it.season(month)) }
            .filter { it.season.isInSeason }
            .sortedWith(
                compareByDescending<CarouselItem> { it.season.score ?: 0.0 }
                    .thenBy { it.recipe.lastPlanned?.toEpochDays() ?: Long.MIN_VALUE },
            )
        val carouselIds = carousel.map { it.recipe.id }.toSet()
        val others = index.recipes.filter { it.id !in carouselIds }
        RecipesHomeState(
            isLoading = !index.isLoaded,
            month = month,
            carousel = carousel,
            allRecipes = when (sort) {
                RecipeSort.AToZ -> others.sortedBy { it.title.lowercase() }
                RecipeSort.RecentlyAdded -> others.sortedByDescending { it.record.createdAt }
                RecipeSort.RecentlyPlanned -> others.sortedByDescending { it.lastPlanned?.toEpochDays() ?: Long.MIN_VALUE }
            },
            query = query,
            results = results,
            tagUsage = index.tagUsage,
            sort = sort,
            drafts = drafts.filter { it.editingRecipeId == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesHomeState())

    override fun onSearchTextChanged(text: String) = search.onTextChanged(text)

    override fun onDietSelected(diet: DietFilter) = search.onDietSelected(diet)

    override fun onTagsChanged(tags: Set<String>) = search.onTagsChanged(tags)

    override fun onClearFiltersClicked() = search.onClearClicked()

    override fun onSortSelected(sort: RecipeSort) {
        this.sort.value = sort
    }

    override fun onAddOptionClicked(option: AddRecipeOption) {
        when (option) {
            AddRecipeOption.Manual -> viewModelScope.launch {
                val draft = importService.startManualDraft(cookbook = false)
                navigator.navigateTo(RecipeEditorRoute(draftId = draft.id.value))
            }
            AddRecipeOption.Text -> navigator.navigateTo(TextImportRoute)
            AddRecipeOption.Website -> navigator.navigateTo(WebImportRoute())
            AddRecipeOption.Scan -> navigator.navigateTo(ScanImportRoute)
        }
    }

    override fun onImportBackupClicked() = navigator.navigateTo(SettingsRoute(startImport = true))

    override fun onSettingsClicked() = navigator.navigateTo(SettingsRoute())

    override fun onResumeDraftClicked(draftId: DraftId) = navigator.navigateTo(RecipeEditorRoute(draftId = draftId.value))

    override fun onDiscardDraftClicked(draftId: DraftId) {
        viewModelScope.launch { draftRepository.discard(draftId) }
    }
}
