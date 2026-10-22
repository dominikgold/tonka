package com.dominikgold.tonka.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.data.AppClock
import com.dominikgold.tonka.data.MealPlanRepository
import com.dominikgold.tonka.data.RecipeRepository
import com.dominikgold.tonka.designsystem.MonthState
import com.dominikgold.tonka.domain.IndexedRecipe
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.domain.RecipeIndex
import com.dominikgold.tonka.domain.RecipeIndexProvider
import com.dominikgold.tonka.domain.daysOfWeek
import com.dominikgold.tonka.domain.displayName
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.LineId
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.NoteId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeNote
import com.dominikgold.tonka.model.RecipeStep
import com.dominikgold.tonka.model.RecipeTip
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.navigation.IngredientDetailRoute
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.RecipeDetailRoute
import com.dominikgold.tonka.navigation.RecipeEditorRoute
import com.dominikgold.tonka.navigation.RevisionHistoryRoute
import com.dominikgold.tonka.util.UiEventsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number
import kotlinx.datetime.plus

data class LineUi(
    val line: IngredientLine,
    val ingredient: Ingredient?,
    val isInSeason: Boolean,
    val linkedRecipe: IndexedRecipe? = null,
    // The linked recipe's own ingredients, shown in the inline expandable list
    val nestedLines: List<LineUi> = emptyList(),
)

data class LineGroup(
    val name: String?,
    val lines: List<LineUi>,
)

data class StepGroup(
    val name: String?,
    val steps: List<RecipeStep>,
)

enum class AlternativeSource(val label: String) {
    Recipe("Suggested by the recipe"),
    Example("Fits this category"),
    Swap("Swap"),
    Similar("Similar"),
}

data class AlternativeItem(
    val ingredient: Ingredient,
    val source: AlternativeSource,
    val note: String,
    val isInSeason: Boolean,
)

data class AlternativesSheetState(
    val title: String,
    val items: List<AlternativeItem>,
)

data class RecipeDetailState(
    val isLoading: Boolean = true,
    val recipe: IndexedRecipe? = null,
    val selectedVariantId: VariantId? = null,
    val month: Month = Month.JANUARY,
    val lineGroups: List<LineGroup> = emptyList(),
    val stepGroups: List<StepGroup> = emptyList(),
    val tips: List<RecipeTip> = emptyList(),
    val seasonStates: Map<Month, MonthState> = emptyMap(),
    val seasonDescription: String = "",
    val notes: List<RecipeNote> = emptyList(),
    val noteDraft: String = "",
    val alternatives: AlternativesSheetState? = null,
    val isShowingDeleteDialog: Boolean = false,
    val mealPlanDays: List<LocalDate>? = null,
    val today: LocalDate? = null,
)

interface RecipeDetailActions {
    fun onBackClicked()
    fun onVariantSelected(variantId: VariantId)
    fun onLineClicked(lineId: LineId)
    fun onLinkedRecipeClicked(recipeId: RecipeId)
    fun onAlternativesDismissed()
    fun onAlternativeClicked(ingredientId: IngredientId)
    fun onEditClicked()
    fun onAddToMealPlanClicked()
    fun onMealPlanDayPicked(date: LocalDate)
    fun onMealPlanDialogDismissed()
    fun onDuplicateClicked()
    fun onRevisionHistoryClicked()
    fun onDeleteClicked()
    fun onDeleteConfirmed()
    fun onDeleteDialogDismissed()
    fun onNoteTextChanged(text: String)
    fun onAddNoteClicked()
    fun onDeleteNoteClicked(noteId: NoteId)
}

class RecipeDetailViewModel(
    private val recipeId: RecipeId,
    initialVariantId: VariantId?,
    private val isPane: Boolean,
    indexProvider: RecipeIndexProvider,
    private val recipeRepository: RecipeRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val clock: AppClock,
    private val navigator: Navigator,
) : ViewModel(), RecipeDetailActions {

    val messages = UiEventsFlow<String>()

    private val index = indexProvider.index
    private val selectedVariantId = MutableStateFlow(initialVariantId)
    private val local = MutableStateFlow(LocalState())

    private data class LocalState(
        val noteDraft: String = "",
        val alternatives: AlternativesSheetState? = null,
        val isShowingDeleteDialog: Boolean = false,
        val mealPlanDays: List<LocalDate>? = null,
    )

    val state: StateFlow<RecipeDetailState> = combine(
        indexProvider.index,
        selectedVariantId,
        recipeRepository.observeNotes(recipeId),
        local,
    ) { index, variantId, notes, local ->
        val recipe = index[recipeId]
        val today = clock.today()
        val month = today.month
        if (recipe == null) {
            return@combine RecipeDetailState(isLoading = !index.isLoaded, today = today)
        }
        val snapshot = recipe.snapshot
        val variant = snapshot.variant(variantId)
        val lines = snapshot.effectiveLines(variant.id).map { line -> lineUi(line, index, month, visited = setOf(recipeId)) }
        val sectionNames = snapshot.sections.associate { it.id to it.name }
        val inSeasonMonths = MonthSet.of(Month.entries.filter { recipe.season(it).isInSeason })
        RecipeDetailState(
            isLoading = false,
            recipe = recipe,
            selectedVariantId = variant.id,
            month = month,
            lineGroups = lines.groupBy { it.line.sectionId }.map { (sectionId, group) -> LineGroup(sectionNames[sectionId], group) },
            stepGroups = snapshot.effectiveSteps(variant.id).groupBy { it.sectionId }
                .map { (sectionId, steps) -> StepGroup(sectionNames[sectionId], steps) },
            tips = snapshot.effectiveTips(variant.id),
            seasonStates = Month.entries.associateWith { month ->
                val season = recipe.season(month)
                when {
                    season.isInSeason -> MonthState.Fresh
                    (season.score ?: 0.0) >= PARTIAL_SEASON_SCORE -> MonthState.Storage
                    else -> MonthState.Off
                }
            },
            seasonDescription = if (inSeasonMonths.isEmpty) "Not tied to a season" else "In season: ${inSeasonMonths.label()}",
            notes = notes,
            noteDraft = local.noteDraft,
            alternatives = local.alternatives,
            isShowingDeleteDialog = local.isShowingDeleteDialog,
            mealPlanDays = local.mealPlanDays,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailState())

    override fun onBackClicked() = navigator.goBack()

    override fun onVariantSelected(variantId: VariantId) {
        selectedVariantId.value = variantId
    }

    override fun onLineClicked(lineId: LineId) {
        val current = state.value
        val line = current.recipe?.snapshot?.lines?.firstOrNull { it.id == lineId } ?: return
        local.update {
            it.copy(
                alternatives = AlternativesSheetState(
                    title = line.displayName.ifBlank { line.rawText },
                    items = alternativesFor(line, index.value.graph, current.month),
                ),
            )
        }
    }

    override fun onLinkedRecipeClicked(recipeId: RecipeId) = navigator.navigateTo(RecipeDetailRoute(recipeId.value))

    override fun onAlternativesDismissed() = local.update { it.copy(alternatives = null) }

    override fun onAlternativeClicked(ingredientId: IngredientId) {
        local.update { it.copy(alternatives = null) }
        navigator.navigateTo(IngredientDetailRoute(ingredientId.value))
    }

    override fun onEditClicked() = navigator.navigateTo(RecipeEditorRoute(recipeId = recipeId.value))

    override fun onAddToMealPlanClicked() {
        val today = clock.today()
        val thisWeek = today.daysOfWeek()
        val nextWeek = thisWeek.last().plus(DatePeriod(days = 1)).daysOfWeek()
        local.update { it.copy(mealPlanDays = thisWeek + nextWeek) }
    }

    override fun onMealPlanDayPicked(date: LocalDate) {
        val recipe = state.value.recipe ?: return
        local.update { it.copy(mealPlanDays = null) }
        viewModelScope.launch {
            mealPlanRepository.addRecipe(date, recipe.id, state.value.selectedVariantId, recipe.title)
            messages.emit("Added to ${date.dayOfWeek.displayName()}, ${date.day}.${date.month.number}.")
        }
    }

    override fun onMealPlanDialogDismissed() = local.update { it.copy(mealPlanDays = null) }

    override fun onDuplicateClicked() {
        viewModelScope.launch {
            val copyId = recipeRepository.duplicate(recipeId) ?: return@launch
            navigator.navigateTo(RecipeDetailRoute(copyId.value))
        }
    }

    override fun onRevisionHistoryClicked() = navigator.navigateTo(RevisionHistoryRoute(recipeId.value))

    override fun onDeleteClicked() = local.update { it.copy(isShowingDeleteDialog = true) }

    override fun onDeleteConfirmed() {
        local.update { it.copy(isShowingDeleteDialog = false) }
        viewModelScope.launch {
            recipeRepository.delete(recipeId)
            if (!isPane) navigator.goBack()
        }
    }

    override fun onDeleteDialogDismissed() = local.update { it.copy(isShowingDeleteDialog = false) }

    override fun onNoteTextChanged(text: String) = local.update { it.copy(noteDraft = text) }

    override fun onAddNoteClicked() {
        val text = local.value.noteDraft
        if (text.isBlank()) return
        local.update { it.copy(noteDraft = "") }
        viewModelScope.launch { recipeRepository.addNote(recipeId, state.value.selectedVariantId, text) }
    }

    override fun onDeleteNoteClicked(noteId: NoteId) {
        viewModelScope.launch { recipeRepository.deleteNote(noteId) }
    }

    private companion object {
        const val PARTIAL_SEASON_SCORE = 0.3
    }
}

// A linked recipe brings its default variant's lines along; each recipe appears once per chain so a loop can't recurse
private fun lineUi(
    line: IngredientLine,
    index: RecipeIndex,
    month: Month,
    visited: Set<RecipeId>,
): LineUi {
    val graph = index.graph
    val linked = index[line.linkedRecipeId]?.takeIf { it.id !in visited }
    return LineUi(
        line = line,
        ingredient = graph[line.ingredientId],
        isInSeason = line.ingredientId?.let { graph.isSeasonal(it) && graph.isFresh(it, month) } == true,
        linkedRecipe = linked,
        nestedLines = linked?.let { recipe ->
            recipe.snapshot.effectiveLines(recipe.snapshot.defaultVariant.id).map { lineUi(it, index, month, visited + recipe.id) }
        }.orEmpty(),
    )
}

// The line's own alternatives, then a category's examples, then global swaps, then siblings as a weaker hint
fun alternativesFor(line: IngredientLine, graph: IngredientGraph, month: Month): List<AlternativeItem> {
    val items = mutableListOf<AlternativeItem>()
    fun inSeason(ingredient: Ingredient) = graph.isSeasonal(ingredient.id) && graph.isFresh(ingredient.id, month)

    line.alternatives.forEach { alternative ->
        graph[alternative.ingredientId]?.let { items += AlternativeItem(it, AlternativeSource.Recipe, alternative.note, inSeason(it)) }
    }
    val ingredient = graph[line.ingredientId]
    if (ingredient != null) {
        if (ingredient.kind == IngredientKind.CATEGORY) {
            graph.examples(ingredient.id)
                .sortedByDescending { inSeason(it) }
                .forEach { items += AlternativeItem(it, AlternativeSource.Example, "", inSeason(it)) }
        }
        graph.substitutionsFor(ingredient.id).forEach { (target, substitution) ->
            items += AlternativeItem(target, AlternativeSource.Swap, substitution.note, inSeason(target))
        }
        graph.siblings(ingredient.id).forEach { items += AlternativeItem(it, AlternativeSource.Similar, "", inSeason(it)) }
    }
    return items.distinctBy { it.ingredient.id }.filter { it.ingredient.id != line.ingredientId }
}
