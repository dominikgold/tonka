package com.dominikgold.tonka.feature.seasons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.data.AppClock
import com.dominikgold.tonka.data.IngredientRepository
import com.dominikgold.tonka.domain.IndexedRecipe
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.domain.RecipeIndexProvider
import com.dominikgold.tonka.domain.TextNormalizer
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.IngredientInput
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.IngredientOrigin
import com.dominikgold.tonka.model.IngredientSubstitution
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.Seasonality
import com.dominikgold.tonka.model.SubstitutionId
import com.dominikgold.tonka.navigation.IngredientDetailRoute
import com.dominikgold.tonka.navigation.IngredientEditorRoute
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.RecipeDetailRoute
import com.dominikgold.tonka.util.UiEventsFlow
import com.dominikgold.tonka.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Month

data class IngredientListItem(
    val ingredient: Ingredient,
    val categoryPath: String,
)

data class IngredientsState(
    val filter: String = "",
    val isReviewOnly: Boolean = false,
    val items: List<IngredientListItem> = emptyList(),
    val reviewCount: Int = 0,
)

interface IngredientsActions {
    fun onBackClicked()
    fun onFilterChanged(filter: String)
    fun onReviewOnlyToggled()
    fun onIngredientClicked(ingredientId: IngredientId)
    fun onAddIngredientClicked()
}

class IngredientsViewModel(
    reviewOnly: Boolean,
    ingredientRepository: IngredientRepository,
    private val navigator: Navigator,
) : ViewModel(), IngredientsActions {

    private val filter = MutableStateFlow("")
    private val isReviewOnly = MutableStateFlow(reviewOnly)

    val state: StateFlow<IngredientsState> = combine(ingredientRepository.graph, filter, isReviewOnly) { graph, filter, reviewOnly ->
        val query = TextNormalizer.normalize(filter)
        IngredientsState(
            filter = filter,
            isReviewOnly = reviewOnly,
            items = graph.ingredients
                .filter { !reviewOnly || it.needsReview }
                .filter { query.isEmpty() || it.matches(graph, query) }
                .sortedBy { it.name.lowercase() }
                .map { IngredientListItem(it, graph.categoryPathLabel(it.id)) },
            reviewCount = graph.reviewQueue.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngredientsState(isReviewOnly = reviewOnly))

    override fun onBackClicked() = navigator.goBack()

    override fun onFilterChanged(filter: String) {
        this.filter.value = filter
    }

    override fun onReviewOnlyToggled() = isReviewOnly.update { !it }

    override fun onIngredientClicked(ingredientId: IngredientId) = navigator.navigateTo(IngredientDetailRoute(ingredientId.value))

    override fun onAddIngredientClicked() = navigator.navigateTo(IngredientEditorRoute())
}

data class SubstitutionUi(
    val target: Ingredient,
    val substitution: IngredientSubstitution,
    // Inherited swaps are defined on an ancestor category and can only be removed there
    val isInherited: Boolean,
)

data class IngredientDetailState(
    val ingredient: Ingredient? = null,
    val aliases: List<String> = emptyList(),
    val categoryPaths: List<List<Ingredient>> = emptyList(),
    val dietClass: DietClass = DietClass.UNKNOWN,
    val freshMonths: MonthSet = MonthSet.None,
    val storageMonths: MonthSet = MonthSet.None,
    val substitutions: List<SubstitutionUi> = emptyList(),
    val examples: List<Ingredient> = emptyList(),
    val children: List<Ingredient> = emptyList(),
    val recipes: List<Pair<IndexedRecipe, Boolean>> = emptyList(),
    val month: Month = Month.JANUARY,
    val graph: IngredientGraph = IngredientGraph.Empty,
    val referenceCount: Long = 0,
)

interface IngredientDetailActions {
    fun onBackClicked()
    fun onEditClicked()
    fun onMarkReviewedClicked()
    fun onIngredientClicked(ingredientId: IngredientId)
    fun onRecipeClicked(recipeId: RecipeId)
    fun onSubstitutionAdded(targetId: IngredientId, note: String, isBidirectional: Boolean)
    fun onSubstitutionRemoved(substitutionId: SubstitutionId)
    fun onMergeConfirmed(intoId: IngredientId)
    fun onDeleteConfirmed()
}

class IngredientDetailViewModel(
    private val ingredientId: IngredientId,
    private val ingredientRepository: IngredientRepository,
    indexProvider: RecipeIndexProvider,
    clock: AppClock,
    private val navigator: Navigator,
) : ViewModel(), IngredientDetailActions {

    val messages = UiEventsFlow<String>()
    private val referenceCount = MutableStateFlow(0L)

    val state: StateFlow<IngredientDetailState> = combine(indexProvider.index, referenceCount) { index, references ->
        val graph = index.graph
        val ingredient = graph[ingredientId] ?: return@combine IngredientDetailState(graph = graph)
        val month = clock.today().month
        val relevantIds = setOf(ingredientId) + graph.descendantIds(ingredientId)
        val ownSubstitutions = graph.substitutions.filter { it.fromId == ingredientId || it.toId == ingredientId }.map { it.id }.toSet()
        IngredientDetailState(
            ingredient = ingredient,
            aliases = graph.aliases[ingredientId].orEmpty(),
            categoryPaths = graph.categoryPaths(ingredientId),
            dietClass = graph.dietClass(ingredientId),
            freshMonths = graph.effectiveFreshMonths(ingredientId),
            storageMonths = graph.effectiveStorageMonths(ingredientId),
            substitutions = graph.substitutionsFor(ingredientId).map { (target, substitution) ->
                SubstitutionUi(target, substitution, isInherited = substitution.id !in ownSubstitutions)
            },
            examples = if (ingredient.kind == IngredientKind.CATEGORY) graph.examples(ingredientId) else emptyList(),
            children = graph.children(ingredientId).filter { it.kind == IngredientKind.CATEGORY },
            recipes = index.recipes
                .filter { recipe -> recipe.snapshot.lines.any { it.ingredientId in relevantIds } }
                .map { it to it.season(month).isInSeason }
                .sortedWith(compareByDescending<Pair<IndexedRecipe, Boolean>> { it.second }.thenBy { it.first.title.lowercase() }),
            month = month,
            graph = graph,
            referenceCount = references,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngredientDetailState())

    init {
        refreshReferenceCount()
    }

    private fun refreshReferenceCount() {
        viewModelScope.launch { referenceCount.value = ingredientRepository.referenceCount(ingredientId) }
    }

    override fun onBackClicked() = navigator.goBack()

    override fun onEditClicked() = navigator.navigateTo(IngredientEditorRoute(ingredientId.value))

    override fun onMarkReviewedClicked() {
        viewModelScope.launch { ingredientRepository.markReviewed(ingredientId) }
    }

    override fun onIngredientClicked(ingredientId: IngredientId) = navigator.navigateTo(IngredientDetailRoute(ingredientId.value))

    override fun onRecipeClicked(recipeId: RecipeId) = navigator.navigateTo(RecipeDetailRoute(recipeId.value))

    override fun onSubstitutionAdded(targetId: IngredientId, note: String, isBidirectional: Boolean) {
        viewModelScope.launch {
            ingredientRepository.saveSubstitution(
                IngredientSubstitution(
                    id = SubstitutionId.new(),
                    fromId = ingredientId,
                    toId = targetId,
                    note = note.trim(),
                    isBidirectional = isBidirectional,
                ),
            )
            refreshReferenceCount()
        }
    }

    override fun onSubstitutionRemoved(substitutionId: SubstitutionId) {
        viewModelScope.launch {
            ingredientRepository.deleteSubstitution(substitutionId)
            refreshReferenceCount()
        }
    }

    override fun onMergeConfirmed(intoId: IngredientId) {
        viewModelScope.launch {
            runCatching { ingredientRepository.merge(fromId = ingredientId, intoId = intoId) }
                .onSuccess { navigator.replaceCurrent(IngredientDetailRoute(intoId.value)) }
                .onFailure { messages.emit(it.userMessage()) }
        }
    }

    override fun onDeleteConfirmed() {
        viewModelScope.launch {
            runCatching { ingredientRepository.delete(ingredientId) }
                .onSuccess { navigator.goBack() }
                .onFailure { messages.emit(it.userMessage()) }
        }
    }
}

data class IngredientForm(
    val name: String = "",
    val kind: IngredientKind = IngredientKind.INGREDIENT,
    val aliases: String = "",
    val parentIds: List<IngredientId> = emptyList(),
    val dietClass: DietClass = DietClass.UNKNOWN,
    val seasonality: Seasonality = Seasonality.UNKNOWN,
    val freshMonths: MonthSet = MonthSet.None,
    val storageMonths: MonthSet = MonthSet.None,
    val isPantryStaple: Boolean = false,
    val notes: String = "",
    val needsReview: Boolean = false,
)

data class IngredientEditorState(
    val isNew: Boolean = true,
    val form: IngredientForm = IngredientForm(),
    val graph: IngredientGraph = IngredientGraph.Empty,
    val isSaving: Boolean = false,
)

interface IngredientEditorActions {
    fun onBackClicked()
    fun onFormChanged(form: IngredientForm)
    fun onSaveClicked()
}

class IngredientEditorViewModel(
    private val ingredientId: IngredientId?,
    private val ingredientRepository: IngredientRepository,
    private val navigator: Navigator,
) : ViewModel(), IngredientEditorActions {

    val messages = UiEventsFlow<String>()
    val state = MutableStateFlow(IngredientEditorState(isNew = ingredientId == null))

    init {
        viewModelScope.launch {
            ingredientRepository.graph.collect { graph -> state.update { it.copy(graph = graph) } }
        }
        val graph = ingredientRepository.graph.value
        graph[ingredientId]?.let { ingredient ->
            state.update {
                it.copy(
                    form = IngredientForm(
                        name = ingredient.name,
                        kind = ingredient.kind,
                        aliases = graph.aliases[ingredient.id].orEmpty().joinToString(", "),
                        parentIds = graph.parents(ingredient.id).map { parent -> parent.id },
                        dietClass = ingredient.dietClass,
                        seasonality = ingredient.seasonality,
                        freshMonths = ingredient.freshMonths,
                        storageMonths = ingredient.storageMonths,
                        isPantryStaple = ingredient.isPantryStaple,
                        notes = ingredient.notes,
                        needsReview = ingredient.needsReview,
                    ),
                )
            }
        }
    }

    override fun onBackClicked() = navigator.goBack()

    override fun onFormChanged(form: IngredientForm) = state.update { it.copy(form = form) }

    override fun onSaveClicked() {
        val form = state.value.form
        if (form.name.isBlank()) {
            messages.tryEmit("Give the ingredient a name.")
            return
        }
        if (state.value.isSaving) return
        state.update { it.copy(isSaving = true) }
        val input = IngredientInput(
            name = form.name.trim(),
            kind = form.kind,
            dietClass = form.dietClass,
            seasonality = if (!form.freshMonths.isEmpty && form.seasonality == Seasonality.UNKNOWN) Seasonality.SEASONAL else form.seasonality,
            freshMonths = form.freshMonths,
            storageMonths = form.storageMonths,
            isPantryStaple = form.isPantryStaple,
            notes = form.notes,
            aliases = form.aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            parentIds = form.parentIds,
            needsReview = form.needsReview,
        )
        viewModelScope.launch {
            runCatching {
                if (ingredientId == null) ingredientRepository.create(input, IngredientOrigin.USER) else {
                    ingredientRepository.update(ingredientId, input)
                    ingredientId
                }
            }.onSuccess { savedId ->
                if (ingredientId == null) navigator.replaceCurrent(IngredientDetailRoute(savedId.value)) else navigator.goBack()
            }.onFailure { messages.emit(it.userMessage()) }
            state.update { it.copy(isSaving = false) }
        }
    }
}
