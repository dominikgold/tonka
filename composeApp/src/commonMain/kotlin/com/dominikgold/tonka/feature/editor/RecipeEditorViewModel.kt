package com.dominikgold.tonka.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.data.DraftRepository
import com.dominikgold.tonka.data.ImageStore
import com.dominikgold.tonka.data.IngredientRepository
import com.dominikgold.tonka.data.RecipeRepository
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.domain.IngredientMatch
import com.dominikgold.tonka.domain.IngredientMatcher
import com.dominikgold.tonka.domain.Tuning
import com.dominikgold.tonka.importing.ImportService
import com.dominikgold.tonka.importing.IngredientLineParser
import com.dominikgold.tonka.importing.RecipeDraft
import com.dominikgold.tonka.importing.categoryForGuess
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.DraftId
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.IngredientInput
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.IngredientOrigin
import com.dominikgold.tonka.model.LineAlternative
import com.dominikgold.tonka.model.LineId
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeSection
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.RecipeStep
import com.dominikgold.tonka.model.RecipeTip
import com.dominikgold.tonka.model.RecipeVariant
import com.dominikgold.tonka.model.SectionId
import com.dominikgold.tonka.model.Seasonality
import com.dominikgold.tonka.model.SourceType
import com.dominikgold.tonka.model.StepId
import com.dominikgold.tonka.model.TipId
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.RecipeDetailRoute
import com.dominikgold.tonka.util.UiEventsFlow
import com.dominikgold.tonka.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Month

enum class EditorMode { Editing, Matching }

sealed interface MatchStatus {
    data class Linked(val ingredient: Ingredient) : MatchStatus
    data class Confirm(val candidate: Ingredient, val others: List<Ingredient>) : MatchStatus
    data class New(val form: NewIngredientForm) : MatchStatus
}

data class NewIngredientForm(
    val name: String,
    val parentId: IngredientId?,
    val dietClass: DietClass,
    val freshMonths: MonthSet = MonthSet.None,
    val storageMonths: MonthSet = MonthSet.None,
    // Untouched forms are created as unknown ingredients and stay in the review queue
    val isFilledIn: Boolean = false,
)

data class MatchRow(
    val lineId: LineId,
    val lineText: String,
    val status: MatchStatus,
)

data class EditorState(
    val isLoading: Boolean = true,
    val draft: RecipeDraft? = null,
    val mode: EditorMode = EditorMode.Editing,
    val matchRows: List<MatchRow> = emptyList(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isShowingDiscardDialog: Boolean = false,
    val whatChanged: String = "",
) {
    val snapshot: RecipeSnapshot? get() = draft?.snapshot
    val isEditingExisting: Boolean get() = draft?.editingRecipeId != null
}

class RecipeEditorViewModel(
    private val draftId: String?,
    private val recipeId: String?,
    private val draftRepository: DraftRepository,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val importService: ImportService,
    private val imageStore: ImageStore,
    private val navigator: Navigator,
) : ViewModel(), RecipeEditorActions {

    val state = MutableStateFlow(EditorState())
    val messages = UiEventsFlow<String>()
    val graph: StateFlow<IngredientGraph> = ingredientRepository.graph
    val allTags: StateFlow<List<String>> = recipeRepository.tags.map { tags -> tags.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val draft = when {
                draftId != null -> draftRepository.get(DraftId(draftId))
                recipeId != null -> importService.startEditDraft(RecipeId(recipeId))
                else -> importService.startManualDraft(cookbook = false)
            }
            if (draft == null) {
                messages.emit("This draft no longer exists.")
                navigator.goBack()
                return@launch
            }
            state.value = EditorState(isLoading = false, draft = draft)
        }
        // Drafts of new recipes survive the app being killed; edits of saved recipes are not persisted until saved
        viewModelScope.launch {
            state.map { it.draft }
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(AUTOSAVE_DEBOUNCE_MILLIS)
                .collect { draft -> if (draft.editingRecipeId == null && state.value.isDirty) draftRepository.save(draft) }
        }
    }

    private fun updateSnapshot(transform: (RecipeSnapshot) -> RecipeSnapshot) {
        state.update { current ->
            val draft = current.draft ?: return@update current
            current.copy(draft = draft.copy(snapshot = transform(draft.snapshot)), isDirty = true)
        }
    }

    private fun updateLine(lineId: LineId, transform: (IngredientLine) -> IngredientLine) = updateSnapshot { snapshot ->
        snapshot.copy(lines = snapshot.lines.map { if (it.id == lineId) transform(it) else it })
    }

    private fun updateStep(stepId: StepId, transform: (RecipeStep) -> RecipeStep) = updateSnapshot { snapshot ->
        snapshot.copy(steps = snapshot.steps.map { if (it.id == stepId) transform(it) else it })
    }

    private fun updateTip(tipId: TipId, transform: (RecipeTip) -> RecipeTip) = updateSnapshot { snapshot ->
        snapshot.copy(tips = snapshot.tips.map { if (it.id == tipId) transform(it) else it })
    }

    // Basics

    override fun onTitleChanged(title: String) = updateSnapshot { it.copy(title = title) }

    override fun onDescriptionChanged(description: String) = updateSnapshot { it.copy(description = description) }

    override fun onSourceTypeSelected(type: SourceType) = updateSnapshot { it.copy(source = it.source.copy(type = type)) }

    override fun onSourceUrlChanged(url: String) = updateSnapshot { it.copy(source = it.source.copy(url = url.ifBlank { null })) }

    override fun onSourceBookChanged(book: String) = updateSnapshot { it.copy(source = it.source.copy(book = book.ifBlank { null })) }

    override fun onSourceAuthorChanged(author: String) = updateSnapshot { it.copy(source = it.source.copy(author = author.ifBlank { null })) }

    override fun onSourcePageChanged(page: String) = updateSnapshot { it.copy(source = it.source.copy(page = page.ifBlank { null })) }

    override fun onYieldChanged(yieldText: String) = updateSnapshot { it.copy(yieldText = yieldText) }

    override fun onPrepMinutesChanged(minutes: String) = updateSnapshot { it.copy(prepMinutes = minutes.filter(Char::isDigit).toIntOrNull()) }

    override fun onCookMinutesChanged(minutes: String) = updateSnapshot { it.copy(cookMinutes = minutes.filter(Char::isDigit).toIntOrNull()) }

    override fun onHeaderImagePicked(bytes: ByteArray) {
        viewModelScope.launch {
            val path = imageStore.store(bytes, cropAspectRatio = Tuning.HEADER_IMAGE_ASPECT_RATIO)
            if (path == null) messages.emit("That image could not be read.") else updateSnapshot { it.copy(headerImagePath = path) }
        }
    }

    override fun onScanPhotoChosenAsHeader(path: String) {
        viewModelScope.launch {
            val bytes = imageStore.read(path) ?: return@launch
            onHeaderImagePicked(bytes)
        }
    }

    override fun onHeaderImageRemoved() = updateSnapshot { it.copy(headerImagePath = null) }

    // Variants and sections

    override fun onAddVariantClicked() = updateSnapshot { snapshot ->
        snapshot.copy(variants = snapshot.variants + RecipeVariant(VariantId.new(), name = "Variant ${snapshot.variants.size + 1}"))
    }

    override fun onVariantRenamed(variantId: VariantId, name: String) = updateSnapshot { snapshot ->
        snapshot.copy(variants = snapshot.variants.map { if (it.id == variantId) it.copy(name = name) else it })
    }

    override fun onDefaultVariantSelected(variantId: VariantId) = updateSnapshot { snapshot ->
        snapshot.copy(variants = snapshot.variants.map { it.copy(isDefault = it.id == variantId) })
    }

    override fun onVariantRemoved(variantId: VariantId) = updateSnapshot { snapshot ->
        if (snapshot.variants.size <= 1) return@updateSnapshot snapshot
        val remaining = snapshot.variants.filter { it.id != variantId }
        val variants = if (remaining.none { it.isDefault }) remaining.mapIndexed { index, it -> it.copy(isDefault = index == 0) } else remaining
        fun Set<VariantId>.without() = (this - variantId)
        snapshot.copy(
            variants = variants,
            // Items only scoped to the removed variant are dropped along with it
            lines = snapshot.lines.filter { it.variantIds.isEmpty() || it.variantIds.without().isNotEmpty() }.map { it.copy(variantIds = it.variantIds.without()) },
            steps = snapshot.steps.filter { it.variantIds.isEmpty() || it.variantIds.without().isNotEmpty() }.map { it.copy(variantIds = it.variantIds.without()) },
            tips = snapshot.tips.filter { it.variantIds.isEmpty() || it.variantIds.without().isNotEmpty() }.map { it.copy(variantIds = it.variantIds.without()) },
        )
    }

    override fun onVariantMoved(from: Int, to: Int) = updateSnapshot { it.copy(variants = it.variants.moved(from, to)) }

    override fun onAddSectionClicked(name: String) = updateSnapshot { snapshot ->
        if (name.isBlank()) return@updateSnapshot snapshot
        snapshot.copy(sections = snapshot.sections + RecipeSection(SectionId.new(), name.trim()))
    }

    override fun onSectionRenamed(sectionId: SectionId, name: String) = updateSnapshot { snapshot ->
        snapshot.copy(sections = snapshot.sections.map { if (it.id == sectionId) it.copy(name = name) else it })
    }

    override fun onSectionRemoved(sectionId: SectionId) = updateSnapshot { snapshot ->
        snapshot.copy(
            sections = snapshot.sections.filter { it.id != sectionId },
            lines = snapshot.lines.map { if (it.sectionId == sectionId) it.copy(sectionId = null) else it },
            steps = snapshot.steps.map { if (it.sectionId == sectionId) it.copy(sectionId = null) else it },
        )
    }

    // Ingredient lines

    override fun onAddLineClicked(sectionId: SectionId?) = updateSnapshot { snapshot ->
        snapshot.copy(lines = snapshot.lines + IngredientLine(id = LineId.new(), sectionId = sectionId, rawText = "", displayName = ""))
    }

    override fun onAddLinesFromText(text: String, sectionId: SectionId?) = updateSnapshot { snapshot ->
        val parsed = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { IngredientLineParser.parse(it).toLine(sectionId) }
        val matcher = IngredientMatcher(graph.value)
        val linked = parsed.map { line ->
            val match = matcher.match(line.displayName)
            if (match is IngredientMatch.Exact) line.copy(ingredientId = match.ingredient.id) else line
        }
        snapshot.copy(lines = snapshot.lines + linked)
    }

    override fun onLineAmountChanged(lineId: LineId, amount: String) = updateLine(lineId) { it.copy(amountText = amount) }

    override fun onLineUnitChanged(lineId: LineId, unit: String) = updateLine(lineId) { it.copy(unit = unit) }

    override fun onLineNameChanged(lineId: LineId, name: String) = updateLine(lineId) { it.copy(displayName = name, ingredientId = null) }

    override fun onLineIngredientPicked(lineId: LineId, ingredientId: IngredientId) {
        val ingredient = graph.value[ingredientId] ?: return
        updateLine(lineId) { line ->
            line.copy(ingredientId = ingredientId, displayName = line.displayName.ifBlank { ingredient.name })
        }
    }

    override fun onLineIngredientCreated(lineId: LineId, name: String) {
        viewModelScope.launch {
            runCatching { ingredientRepository.createUnreviewed(name, parentId = null, origin = IngredientOrigin.USER) }
                .onSuccess { id -> updateLine(lineId) { it.copy(ingredientId = id, displayName = it.displayName.ifBlank { name }) } }
                .onFailure { messages.emit(it.userMessage()) }
        }
    }

    override fun onLineUnlinked(lineId: LineId) = updateLine(lineId) { it.copy(ingredientId = null) }

    override fun onLinePreparationChanged(lineId: LineId, preparation: String) = updateLine(lineId) { it.copy(preparation = preparation) }

    override fun onLineOptionalToggled(lineId: LineId) = updateLine(lineId) { it.copy(isOptional = !it.isOptional) }

    override fun onLineKeyToggled(lineId: LineId) = updateLine(lineId) { it.copy(isKey = !it.isKey) }

    override fun onLineVariantToggled(lineId: LineId, variantId: VariantId) {
        val variants = state.value.snapshot?.variants?.map { it.id } ?: return
        updateLine(lineId) { it.copy(variantIds = toggledScope(it.variantIds, variantId, variants)) }
    }

    override fun onLineSectionSelected(lineId: LineId, sectionId: SectionId?) = updateLine(lineId) { it.copy(sectionId = sectionId) }

    override fun onLineAlternativeAdded(lineId: LineId, ingredientId: IngredientId) = updateLine(lineId) { line ->
        if (line.alternatives.any { it.ingredientId == ingredientId } || line.ingredientId == ingredientId) line
        else line.copy(alternatives = line.alternatives + LineAlternative(ingredientId))
    }

    override fun onLineAlternativeRemoved(lineId: LineId, ingredientId: IngredientId) = updateLine(lineId) { line ->
        line.copy(alternatives = line.alternatives.filter { it.ingredientId != ingredientId })
    }

    override fun onLineRemoved(lineId: LineId) = updateSnapshot { snapshot -> snapshot.copy(lines = snapshot.lines.filter { it.id != lineId }) }

    override fun onLineMoved(from: Int, to: Int) = updateSnapshot { it.copy(lines = it.lines.moved(from, to)) }

    // Steps and tips

    override fun onAddStepClicked() = updateSnapshot { snapshot ->
        snapshot.copy(steps = snapshot.steps + RecipeStep(id = StepId.new(), sectionId = snapshot.steps.lastOrNull()?.sectionId, text = ""))
    }

    override fun onStepTextChanged(stepId: StepId, text: String) = updateStep(stepId) { it.copy(text = text) }

    override fun onStepSectionSelected(stepId: StepId, sectionId: SectionId?) = updateStep(stepId) { it.copy(sectionId = sectionId) }

    override fun onStepTimerChanged(stepId: StepId, minutes: String) = updateStep(stepId) { step ->
        step.copy(timerSeconds = minutes.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }?.times(60))
    }

    override fun onStepVariantToggled(stepId: StepId, variantId: VariantId) {
        val variants = state.value.snapshot?.variants?.map { it.id } ?: return
        updateStep(stepId) { it.copy(variantIds = toggledScope(it.variantIds, variantId, variants)) }
    }

    override fun onStepImagePicked(stepId: StepId, bytes: ByteArray) {
        viewModelScope.launch {
            val path = imageStore.store(bytes)
            if (path == null) messages.emit("That image could not be read.") else updateStep(stepId) { it.copy(imagePath = path) }
        }
    }

    override fun onStepImageRemoved(stepId: StepId) = updateStep(stepId) { it.copy(imagePath = null) }

    override fun onStepRemoved(stepId: StepId) = updateSnapshot { snapshot -> snapshot.copy(steps = snapshot.steps.filter { it.id != stepId }) }

    override fun onStepMoved(from: Int, to: Int) = updateSnapshot { it.copy(steps = it.steps.moved(from, to)) }

    override fun onAddTipClicked() = updateSnapshot { it.copy(tips = it.tips + RecipeTip(id = TipId.new(), text = "")) }

    override fun onTipTextChanged(tipId: TipId, text: String) = updateTip(tipId) { it.copy(text = text) }

    override fun onTipVariantToggled(tipId: TipId, variantId: VariantId) {
        val variants = state.value.snapshot?.variants?.map { it.id } ?: return
        updateTip(tipId) { it.copy(variantIds = toggledScope(it.variantIds, variantId, variants)) }
    }

    override fun onTipRemoved(tipId: TipId) = updateSnapshot { snapshot -> snapshot.copy(tips = snapshot.tips.filter { it.id != tipId }) }

    override fun onTipMoved(from: Int, to: Int) = updateSnapshot { it.copy(tips = it.tips.moved(from, to)) }

    // Tags, season and diet

    override fun onTagAdded(tag: String) = updateSnapshot { snapshot ->
        val trimmed = tag.trim()
        if (trimmed.isEmpty() || snapshot.tags.any { it.equals(trimmed, ignoreCase = true) }) snapshot
        else snapshot.copy(tags = snapshot.tags + trimmed)
    }

    override fun onTagRemoved(tag: String) = updateSnapshot { snapshot -> snapshot.copy(tags = snapshot.tags.filter { it != tag }) }

    override fun onSeasonMonthToggled(month: Month) = updateSnapshot { snapshot ->
        val toggled = (snapshot.seasonOverride ?: MonthSet.None).toggle(month)
        snapshot.copy(seasonOverride = toggled.takeIf { !it.isEmpty })
    }

    override fun onSeasonPresetClicked(months: MonthSet) = updateSnapshot { snapshot ->
        snapshot.copy(seasonOverride = (snapshot.seasonOverride ?: MonthSet.None) + months)
    }

    override fun onSeasonCleared() = updateSnapshot { it.copy(seasonOverride = null) }

    override fun onDietOverrideSelected(dietClass: DietClass?) = updateSnapshot { it.copy(dietOverride = dietClass) }

    // Leaving and saving

    override fun onBackClicked() {
        val current = state.value
        val draft = current.draft
        val isUntouchedNewDraft = draft != null && draft.editingRecipeId == null && !current.isDirty &&
            draft.snapshot.title.isBlank() && draft.snapshot.lines.isEmpty() && draft.snapshot.steps.isEmpty()
        when {
            isUntouchedNewDraft -> onDiscardConfirmed()
            current.isDirty || (draft != null && draft.editingRecipeId == null) -> state.update { it.copy(isShowingDiscardDialog = true) }
            else -> navigator.goBack()
        }
    }

    override fun onKeepDraftClicked() {
        val draft = state.value.draft
        state.update { it.copy(isShowingDiscardDialog = false) }
        viewModelScope.launch {
            if (draft != null && draft.editingRecipeId == null) draftRepository.save(draft)
            navigator.goBack()
        }
    }

    override fun onDiscardConfirmed() {
        val draft = state.value.draft
        state.update { it.copy(isShowingDiscardDialog = false) }
        viewModelScope.launch {
            if (draft != null && draft.editingRecipeId == null) {
                draftRepository.discard(draft.id)
                imageStore.delete(draft.scanImagePaths)
            }
            navigator.goBack()
        }
    }

    override fun onDiscardDialogDismissed() = state.update { it.copy(isShowingDiscardDialog = false) }

    override fun onWhatChangedChanged(text: String) = state.update { it.copy(whatChanged = text) }

    override fun onSaveClicked() {
        val snapshot = state.value.snapshot ?: return
        if (snapshot.title.isBlank()) {
            messages.tryEmit("Give the recipe a title first.")
            return
        }
        val unresolved = snapshot.lines.filter { it.ingredientId == null && (it.displayName.isNotBlank() || it.rawText.isNotBlank()) }
        if (unresolved.isEmpty()) {
            save()
            return
        }
        val graph = graph.value
        val matcher = IngredientMatcher(graph)
        val draft = state.value.draft
        val rows = unresolved.map { line ->
            val name = line.displayName.ifBlank { IngredientLineParser.parse(line.rawText).name }
            val status = when (val match = matcher.match(name)) {
                is IngredientMatch.Exact -> MatchStatus.Linked(match.ingredient)
                is IngredientMatch.Fuzzy -> MatchStatus.Confirm(match.candidates.first(), match.candidates.drop(1))
                is IngredientMatch.None -> MatchStatus.New(
                    NewIngredientForm(
                        name = IngredientMatcher.normalizeForMatching(name).replaceFirstChar { it.uppercase() }.ifBlank { name },
                        parentId = graph.categoryForGuess(draft?.categoryGuesses?.get(line.id.value))?.id ?: match.suggestedParent?.id,
                        dietClass = draft?.dietGuesses?.get(line.id.value) ?: DietClass.UNKNOWN,
                    ),
                )
            }
            MatchRow(lineId = line.id, lineText = line.rawText.ifBlank { name }, status = status)
        }
        state.update { it.copy(mode = EditorMode.Matching, matchRows = rows) }
    }

    override fun onBackToEditingClicked() = state.update { it.copy(mode = EditorMode.Editing) }

    override fun onMatchIngredientPicked(lineId: LineId, ingredientId: IngredientId) {
        val ingredient = graph.value[ingredientId] ?: return
        updateMatchRow(lineId) { it.copy(status = MatchStatus.Linked(ingredient)) }
    }

    override fun onMatchCreateNewClicked(lineId: LineId) = updateMatchRow(lineId) { row ->
        val name = state.value.snapshot?.lines?.firstOrNull { it.id == lineId }?.displayName.orEmpty()
        row.copy(status = MatchStatus.New(NewIngredientForm(name = name.ifBlank { row.lineText }, parentId = null, dietClass = DietClass.UNKNOWN)))
    }

    override fun onNewIngredientFormChanged(lineId: LineId, form: NewIngredientForm) = updateMatchRow(lineId) {
        it.copy(status = MatchStatus.New(form.copy(isFilledIn = true)))
    }

    override fun onFinishSaveClicked() = save()

    private fun updateMatchRow(lineId: LineId, transform: (MatchRow) -> MatchRow) = state.update { current ->
        current.copy(matchRows = current.matchRows.map { if (it.lineId == lineId) transform(it) else it })
    }

    private fun save() {
        val current = state.value
        val draft = current.draft ?: return
        if (current.isSaving) return
        state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                val resolvedIds = resolveMatches(current.matchRows.takeIf { current.mode == EditorMode.Matching }.orEmpty())
                val snapshot = draft.snapshot.copy(
                    lines = draft.snapshot.lines.map { line ->
                        val withIngredient = resolvedIds[line.id]?.let { line.copy(ingredientId = it) } ?: line
                        if (withIngredient.rawText.isBlank()) withIngredient.copy(rawText = withIngredient.composedText()) else withIngredient
                    },
                )
                val summary = current.whatChanged.trim().ifEmpty {
                    if (draft.editingRecipeId == null) draft.originSummary else "Edited"
                }
                val keepPhotos = importService.keepScanPhotos()
                recipeRepository.save(
                    snapshot = snapshot,
                    summary = summary,
                    scanImagePaths = if (keepPhotos) draft.scanImagePaths else emptyList(),
                )
                if (draft.editingRecipeId == null) {
                    draftRepository.discard(draft.id)
                    if (!keepPhotos) imageStore.delete(draft.scanImagePaths)
                }
                snapshot.id
            }.onSuccess { savedId ->
                state.update { it.copy(isSaving = false, isDirty = false) }
                if (draft.editingRecipeId == null) {
                    navigator.replaceCurrent(RecipeDetailRoute(savedId.value))
                } else {
                    navigator.goBack()
                }
            }.onFailure { error ->
                state.update { it.copy(isSaving = false) }
                messages.emit(error.userMessage())
            }
        }
    }

    private suspend fun resolveMatches(rows: List<MatchRow>): Map<LineId, IngredientId> = rows.associate { row ->
        row.lineId to when (val status = row.status) {
            is MatchStatus.Linked -> status.ingredient.id
            is MatchStatus.Confirm -> status.candidate.id
            is MatchStatus.New -> createIngredient(status.form)
        }
    }

    private suspend fun createIngredient(form: NewIngredientForm): IngredientId {
        graph.value.ingredients.firstOrNull { it.name.equals(form.name.trim(), ignoreCase = true) }?.let { return it.id }
        if (!form.isFilledIn) {
            return ingredientRepository.createUnreviewed(form.name, form.parentId, form.dietClass)
        }
        return ingredientRepository.create(
            IngredientInput(
                name = form.name,
                kind = IngredientKind.INGREDIENT,
                dietClass = form.dietClass,
                seasonality = if (form.freshMonths.isEmpty) Seasonality.YEAR_ROUND else Seasonality.SEASONAL,
                freshMonths = form.freshMonths,
                storageMonths = form.storageMonths,
                isPantryStaple = false,
                notes = "",
                aliases = emptyList(),
                parentIds = listOfNotNull(form.parentId),
                needsReview = form.dietClass == DietClass.UNKNOWN,
            ),
            origin = IngredientOrigin.USER,
        )
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MILLIS = 500L
    }
}

// An empty scope means "all variants"; deselecting from it scopes to the rest, and the last variant can't be removed
fun toggledScope(current: Set<VariantId>, toggled: VariantId, all: List<VariantId>): Set<VariantId> {
    val effective = current.ifEmpty { all.toSet() }
    val updated = if (toggled in effective) effective - toggled else effective + toggled
    return when {
        updated.isEmpty() -> current
        updated.size == all.size -> emptySet()
        else -> updated
    }
}

fun IngredientLine.composedText(): String {
    val main = listOf(amountText, unit, displayName).filter { it.isNotBlank() }.joinToString(" ")
    return if (preparation.isBlank()) main else "$main, $preparation"
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
