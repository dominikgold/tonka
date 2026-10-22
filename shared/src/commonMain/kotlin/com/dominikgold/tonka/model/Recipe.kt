package com.dominikgold.tonka.model

import kotlinx.serialization.Serializable

@Serializable
enum class SourceType { MANUAL, COOKBOOK, WEB, TEXT, SCAN }

@Serializable
data class RecipeSource(
    val type: SourceType = SourceType.MANUAL,
    val url: String? = null,
    val book: String? = null,
    val author: String? = null,
    val page: String? = null,
)

@Serializable
data class RecipeVariant(
    val id: VariantId,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
)

@Serializable
data class RecipeSection(
    val id: SectionId,
    val name: String,
)

@Serializable
data class LineAlternative(
    val ingredientId: IngredientId,
    val note: String = "",
)

@Serializable
data class IngredientLine(
    val id: LineId,
    val sectionId: SectionId? = null,
    val rawText: String,
    val amountText: String = "",
    val unit: String = "",
    val ingredientId: IngredientId? = null,
    val displayName: String,
    val preparation: String = "",
    val isOptional: Boolean = false,
    val isKey: Boolean = false,
    val alternatives: List<LineAlternative> = emptyList(),
    // Empty means the line applies to every variant
    val variantIds: Set<VariantId> = emptySet(),
)

@Serializable
data class RecipeStep(
    val id: StepId,
    val sectionId: SectionId? = null,
    val text: String,
    val imagePath: String? = null,
    val timerSeconds: Int? = null,
    val variantIds: Set<VariantId> = emptySet(),
)

@Serializable
data class RecipeTip(
    val id: TipId,
    val text: String,
    val variantIds: Set<VariantId> = emptySet(),
)

// The full editable state of a recipe; revisions store it as JSON
@Serializable
data class RecipeSnapshot(
    val id: RecipeId,
    val title: String,
    val description: String = "",
    val source: RecipeSource = RecipeSource(),
    val headerImagePath: String? = null,
    val yieldText: String = "",
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val seasonOverride: MonthSet? = null,
    val dietOverride: DietClass? = null,
    val variants: List<RecipeVariant>,
    val sections: List<RecipeSection> = emptyList(),
    val lines: List<IngredientLine> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val tips: List<RecipeTip> = emptyList(),
    val tags: List<String> = emptyList(),
) {

    val defaultVariant: RecipeVariant get() = variants.firstOrNull { it.isDefault } ?: variants.first()

    fun variant(id: VariantId?): RecipeVariant = variants.firstOrNull { it.id == id } ?: defaultVariant

    fun effectiveLines(variantId: VariantId): List<IngredientLine> = lines.filter { it.appliesTo(variantId) }

    fun effectiveSteps(variantId: VariantId): List<RecipeStep> = steps.filter { it.variantIds.appliesTo(variantId) }

    fun effectiveTips(variantId: VariantId): List<RecipeTip> = tips.filter { it.variantIds.appliesTo(variantId) }

    companion object {
        fun blank(title: String = "") = RecipeSnapshot(
            id = RecipeId.new(),
            title = title,
            variants = listOf(RecipeVariant(id = VariantId.new(), name = "Default", isDefault = true)),
        )
    }
}

fun IngredientLine.appliesTo(variantId: VariantId) = variantIds.appliesTo(variantId)

private fun Set<VariantId>.appliesTo(variantId: VariantId) = isEmpty() || variantId in this

data class RecipeRecord(
    val snapshot: RecipeSnapshot,
    val currentRevision: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RecipeNote(
    val id: NoteId,
    val recipeId: RecipeId,
    val variantId: VariantId?,
    val createdAt: Long,
    val text: String,
)

data class RecipeRevision(
    val id: RevisionId,
    val recipeId: RecipeId,
    val number: Int,
    val createdAt: Long,
    val summary: String,
    val snapshot: RecipeSnapshot,
)

data class Tag(
    val id: TagId,
    val name: String,
)
