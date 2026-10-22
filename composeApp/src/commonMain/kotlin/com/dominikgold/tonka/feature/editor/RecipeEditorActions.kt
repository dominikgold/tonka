package com.dominikgold.tonka.feature.editor

import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.LineId
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.SectionId
import com.dominikgold.tonka.model.SourceType
import com.dominikgold.tonka.model.StepId
import com.dominikgold.tonka.model.TipId
import com.dominikgold.tonka.model.VariantId
import kotlinx.datetime.Month

interface RecipeEditorActions {
    fun onTitleChanged(title: String)
    fun onDescriptionChanged(description: String)
    fun onSourceTypeSelected(type: SourceType)
    fun onSourceUrlChanged(url: String)
    fun onSourceBookChanged(book: String)
    fun onSourceAuthorChanged(author: String)
    fun onSourcePageChanged(page: String)
    fun onYieldChanged(yieldText: String)
    fun onPrepMinutesChanged(minutes: String)
    fun onCookMinutesChanged(minutes: String)
    fun onHeaderImagePicked(bytes: ByteArray)
    fun onScanPhotoChosenAsHeader(path: String)
    fun onHeaderImageRemoved()

    fun onAddVariantClicked()
    fun onVariantRenamed(variantId: VariantId, name: String)
    fun onDefaultVariantSelected(variantId: VariantId)
    fun onVariantRemoved(variantId: VariantId)
    fun onVariantMoved(from: Int, to: Int)
    fun onAddSectionClicked(name: String)
    fun onSectionRenamed(sectionId: SectionId, name: String)
    fun onSectionRemoved(sectionId: SectionId)

    fun onAddLineClicked(sectionId: SectionId?)
    fun onAddLinesFromText(text: String, sectionId: SectionId?)
    fun onLineAmountChanged(lineId: LineId, amount: String)
    fun onLineUnitChanged(lineId: LineId, unit: String)
    fun onLineNameChanged(lineId: LineId, name: String)
    fun onLineIngredientPicked(lineId: LineId, ingredientId: IngredientId)
    fun onLineIngredientCreated(lineId: LineId, name: String)
    fun onLineUnlinked(lineId: LineId)
    fun onLinePreparationChanged(lineId: LineId, preparation: String)
    fun onLineOptionalToggled(lineId: LineId)
    fun onLineKeyToggled(lineId: LineId)
    fun onLineVariantToggled(lineId: LineId, variantId: VariantId)
    fun onLineSectionSelected(lineId: LineId, sectionId: SectionId?)
    fun onLineAlternativeAdded(lineId: LineId, ingredientId: IngredientId)
    fun onLineAlternativeRemoved(lineId: LineId, ingredientId: IngredientId)
    fun onLineRemoved(lineId: LineId)
    fun onLineMoved(from: Int, to: Int)

    fun onAddStepClicked()
    fun onStepTextChanged(stepId: StepId, text: String)
    fun onStepSectionSelected(stepId: StepId, sectionId: SectionId?)
    fun onStepTimerChanged(stepId: StepId, minutes: String)
    fun onStepVariantToggled(stepId: StepId, variantId: VariantId)
    fun onStepImagePicked(stepId: StepId, bytes: ByteArray)
    fun onStepImageRemoved(stepId: StepId)
    fun onStepRemoved(stepId: StepId)
    fun onStepMoved(from: Int, to: Int)
    fun onAddTipClicked()
    fun onTipTextChanged(tipId: TipId, text: String)
    fun onTipVariantToggled(tipId: TipId, variantId: VariantId)
    fun onTipRemoved(tipId: TipId)
    fun onTipMoved(from: Int, to: Int)

    fun onTagAdded(tag: String)
    fun onTagRemoved(tag: String)
    fun onSeasonMonthToggled(month: Month)
    fun onSeasonPresetClicked(months: MonthSet)
    fun onSeasonCleared()
    fun onDietOverrideSelected(dietClass: DietClass?)

    fun onBackClicked()
    fun onDiscardConfirmed()
    fun onKeepDraftClicked()
    fun onDiscardDialogDismissed()
    fun onWhatChangedChanged(text: String)
    fun onSaveClicked()
    fun onBackToEditingClicked()
    fun onMatchIngredientPicked(lineId: LineId, ingredientId: IngredientId)
    fun onMatchCreateNewClicked(lineId: LineId)
    fun onNewIngredientFormChanged(lineId: LineId, form: NewIngredientForm)
    fun onFinishSaveClicked()
}

@Suppress("EmptyFunctionBlock", "TooManyFunctions")
object FakeRecipeEditorActions : RecipeEditorActions {
    override fun onTitleChanged(title: String) {}
    override fun onDescriptionChanged(description: String) {}
    override fun onSourceTypeSelected(type: SourceType) {}
    override fun onSourceUrlChanged(url: String) {}
    override fun onSourceBookChanged(book: String) {}
    override fun onSourceAuthorChanged(author: String) {}
    override fun onSourcePageChanged(page: String) {}
    override fun onYieldChanged(yieldText: String) {}
    override fun onPrepMinutesChanged(minutes: String) {}
    override fun onCookMinutesChanged(minutes: String) {}
    override fun onHeaderImagePicked(bytes: ByteArray) {}
    override fun onScanPhotoChosenAsHeader(path: String) {}
    override fun onHeaderImageRemoved() {}
    override fun onAddVariantClicked() {}
    override fun onVariantRenamed(variantId: VariantId, name: String) {}
    override fun onDefaultVariantSelected(variantId: VariantId) {}
    override fun onVariantRemoved(variantId: VariantId) {}
    override fun onVariantMoved(from: Int, to: Int) {}
    override fun onAddSectionClicked(name: String) {}
    override fun onSectionRenamed(sectionId: SectionId, name: String) {}
    override fun onSectionRemoved(sectionId: SectionId) {}
    override fun onAddLineClicked(sectionId: SectionId?) {}
    override fun onAddLinesFromText(text: String, sectionId: SectionId?) {}
    override fun onLineAmountChanged(lineId: LineId, amount: String) {}
    override fun onLineUnitChanged(lineId: LineId, unit: String) {}
    override fun onLineNameChanged(lineId: LineId, name: String) {}
    override fun onLineIngredientPicked(lineId: LineId, ingredientId: IngredientId) {}
    override fun onLineIngredientCreated(lineId: LineId, name: String) {}
    override fun onLineUnlinked(lineId: LineId) {}
    override fun onLinePreparationChanged(lineId: LineId, preparation: String) {}
    override fun onLineOptionalToggled(lineId: LineId) {}
    override fun onLineKeyToggled(lineId: LineId) {}
    override fun onLineVariantToggled(lineId: LineId, variantId: VariantId) {}
    override fun onLineSectionSelected(lineId: LineId, sectionId: SectionId?) {}
    override fun onLineAlternativeAdded(lineId: LineId, ingredientId: IngredientId) {}
    override fun onLineAlternativeRemoved(lineId: LineId, ingredientId: IngredientId) {}
    override fun onLineRemoved(lineId: LineId) {}
    override fun onLineMoved(from: Int, to: Int) {}
    override fun onAddStepClicked() {}
    override fun onStepTextChanged(stepId: StepId, text: String) {}
    override fun onStepSectionSelected(stepId: StepId, sectionId: SectionId?) {}
    override fun onStepTimerChanged(stepId: StepId, minutes: String) {}
    override fun onStepVariantToggled(stepId: StepId, variantId: VariantId) {}
    override fun onStepImagePicked(stepId: StepId, bytes: ByteArray) {}
    override fun onStepImageRemoved(stepId: StepId) {}
    override fun onStepRemoved(stepId: StepId) {}
    override fun onStepMoved(from: Int, to: Int) {}
    override fun onAddTipClicked() {}
    override fun onTipTextChanged(tipId: TipId, text: String) {}
    override fun onTipVariantToggled(tipId: TipId, variantId: VariantId) {}
    override fun onTipRemoved(tipId: TipId) {}
    override fun onTipMoved(from: Int, to: Int) {}
    override fun onTagAdded(tag: String) {}
    override fun onTagRemoved(tag: String) {}
    override fun onSeasonMonthToggled(month: Month) {}
    override fun onSeasonPresetClicked(months: MonthSet) {}
    override fun onSeasonCleared() {}
    override fun onDietOverrideSelected(dietClass: DietClass?) {}
    override fun onBackClicked() {}
    override fun onDiscardConfirmed() {}
    override fun onKeepDraftClicked() {}
    override fun onDiscardDialogDismissed() {}
    override fun onWhatChangedChanged(text: String) {}
    override fun onSaveClicked() {}
    override fun onBackToEditingClicked() {}
    override fun onMatchIngredientPicked(lineId: LineId, ingredientId: IngredientId) {}
    override fun onMatchCreateNewClicked(lineId: LineId) {}
    override fun onNewIngredientFormChanged(lineId: LineId, form: NewIngredientForm) {}
    override fun onFinishSaveClicked() {}
}
