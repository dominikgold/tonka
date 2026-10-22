package com.dominikgold.tonka.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

fun newId(): String = Uuid.random().toString()

@Serializable
@JvmInline
value class IngredientId(val value: String) {
    companion object {
        fun new() = IngredientId(newId())
    }
}

@Serializable
@JvmInline
value class SubstitutionId(val value: String) {
    companion object {
        fun new() = SubstitutionId(newId())
    }
}

@Serializable
@JvmInline
value class RecipeId(val value: String) {
    companion object {
        fun new() = RecipeId(newId())
    }
}

@Serializable
@JvmInline
value class VariantId(val value: String) {
    companion object {
        fun new() = VariantId(newId())
    }
}

@Serializable
@JvmInline
value class SectionId(val value: String) {
    companion object {
        fun new() = SectionId(newId())
    }
}

@Serializable
@JvmInline
value class LineId(val value: String) {
    companion object {
        fun new() = LineId(newId())
    }
}

@Serializable
@JvmInline
value class StepId(val value: String) {
    companion object {
        fun new() = StepId(newId())
    }
}

@Serializable
@JvmInline
value class TipId(val value: String) {
    companion object {
        fun new() = TipId(newId())
    }
}

@Serializable
@JvmInline
value class TagId(val value: String) {
    companion object {
        fun new() = TagId(newId())
    }
}

@Serializable
@JvmInline
value class NoteId(val value: String) {
    companion object {
        fun new() = NoteId(newId())
    }
}

@Serializable
@JvmInline
value class RevisionId(val value: String) {
    companion object {
        fun new() = RevisionId(newId())
    }
}

@Serializable
@JvmInline
value class MealPlanEntryId(val value: String) {
    companion object {
        fun new() = MealPlanEntryId(newId())
    }
}

@Serializable
@JvmInline
value class DraftId(val value: String) {
    companion object {
        fun new() = DraftId(newId())
    }
}
