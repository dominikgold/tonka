package com.dominikgold.tonka.domain

import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.RecipeStep
import com.dominikgold.tonka.model.RecipeTip

data class ItemChanges<T>(
    val added: List<T>,
    val removed: List<T>,
    val edited: List<Pair<T, T>>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && edited.isEmpty()
}

data class SnapshotDiff(
    val changedFields: List<String>,
    val lines: ItemChanges<IngredientLine>,
    val steps: ItemChanges<RecipeStep>,
    val tips: ItemChanges<RecipeTip>,
) {
    val isEmpty: Boolean get() = changedFields.isEmpty() && lines.isEmpty && steps.isEmpty && tips.isEmpty

    companion object {
        fun between(previous: RecipeSnapshot?, current: RecipeSnapshot): SnapshotDiff {
            if (previous == null) {
                return SnapshotDiff(
                    changedFields = emptyList(),
                    lines = ItemChanges(current.lines, emptyList(), emptyList()),
                    steps = ItemChanges(current.steps, emptyList(), emptyList()),
                    tips = ItemChanges(current.tips, emptyList(), emptyList()),
                )
            }
            val fields = listOfNotNull(
                "Title".takeIf { previous.title != current.title },
                "Description".takeIf { previous.description != current.description },
                "Source".takeIf { previous.source != current.source },
                "Header image".takeIf { previous.headerImagePath != current.headerImagePath },
                "Yield".takeIf { previous.yieldText != current.yieldText },
                "Times".takeIf { previous.prepMinutes != current.prepMinutes || previous.cookMinutes != current.cookMinutes },
                "Season tag".takeIf { previous.seasonOverride != current.seasonOverride },
                "Diet override".takeIf { previous.dietOverride != current.dietOverride },
                "Variants".takeIf { previous.variants != current.variants },
                "Sections".takeIf { previous.sections != current.sections },
                "Tags".takeIf { previous.tags != current.tags },
            )
            return SnapshotDiff(
                changedFields = fields,
                lines = changes(previous.lines, current.lines) { it.id.value },
                steps = changes(previous.steps, current.steps) { it.id.value },
                tips = changes(previous.tips, current.tips) { it.id.value },
            )
        }

        private fun <T> changes(previous: List<T>, current: List<T>, key: (T) -> String): ItemChanges<T> {
            val previousByKey = previous.associateBy(key)
            val currentByKey = current.associateBy(key)
            return ItemChanges(
                added = current.filter { key(it) !in previousByKey },
                removed = previous.filter { key(it) !in currentByKey },
                edited = current.mapNotNull { item -> previousByKey[key(item)]?.takeIf { it != item }?.let { it to item } },
            )
        }
    }
}
