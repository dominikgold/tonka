package com.dominikgold.tonka.importing

import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.DraftId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeSnapshot
import kotlinx.serialization.Serializable

// What every entry point (manual, text, web, scan) produces; the editor reviews it before it becomes revision 1
@Serializable
data class RecipeDraft(
    val id: DraftId,
    val snapshot: RecipeSnapshot,
    val originSummary: String,
    val imageCandidates: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),
    val dietHint: DietClass? = null,
    val scanImagePaths: List<String> = emptyList(),
    // AI guesses keyed by line id, used to prefill new ingredients
    val categoryGuesses: Map<String, String> = emptyMap(),
    val dietGuesses: Map<String, DietClass> = emptyMap(),
    // Set when the draft edits an existing recipe instead of creating one
    val editingRecipeId: RecipeId? = null,
)
