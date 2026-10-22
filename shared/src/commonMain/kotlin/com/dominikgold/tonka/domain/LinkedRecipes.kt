package com.dominikgold.tonka.domain

import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.VariantId

// A line linking another recipe stands for that recipe's default variant lines. Each recipe is followed once per
// chain, so a link loop ends instead of recursing forever.
fun RecipeSnapshot.expandedLines(
    variantId: VariantId,
    lookup: (RecipeId) -> RecipeSnapshot?,
    visited: Set<RecipeId> = setOf(id),
): List<IngredientLine> = effectiveLines(variantId).flatMap { line ->
    val linkedId = line.linkedRecipeId ?: return@flatMap listOf(line)
    if (linkedId in visited) return@flatMap emptyList()
    val linked = lookup(linkedId) ?: return@flatMap listOf(line.copy(linkedRecipeId = null))
    linked.expandedLines(linked.defaultVariant.id, lookup, visited + linkedId)
        .map { if (line.isOptional) it.copy(isOptional = true) else it }
}

// True when [recipeId] links [target], directly or through other linked recipes, in any variant
fun dependsOn(
    recipeId: RecipeId,
    target: RecipeId,
    lookup: (RecipeId) -> RecipeSnapshot?,
    visited: Set<RecipeId> = emptySet(),
): Boolean {
    if (recipeId == target) return true
    if (recipeId in visited) return false
    val snapshot = lookup(recipeId) ?: return false
    return snapshot.lines.mapNotNull { it.linkedRecipeId }.distinct().any { dependsOn(it, target, lookup, visited + recipeId) }
}
