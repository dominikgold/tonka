package com.dominikgold.tonka.domain

import com.dominikgold.tonka.importing.IngredientLineParser
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.IngredientKind

sealed interface IngredientMatch {
    data class Exact(val ingredient: Ingredient) : IngredientMatch

    // Proposed for confirmation; containmentParent is offered as the parent when a new ingredient is created instead
    data class Fuzzy(val candidates: List<Ingredient>, val containmentParent: Ingredient?) : IngredientMatch

    data class None(val suggestedParent: Ingredient?) : IngredientMatch
}

class IngredientMatcher(private val graph: IngredientGraph) {

    private val byName: Map<String, Ingredient> = graph.ingredients.associateBy { TextNormalizer.normalize(it.name) }
    private val byAlias: Map<String, Ingredient> = graph.aliases.flatMap { (id, aliases) ->
        val ingredient = graph.byId[id] ?: return@flatMap emptyList()
        aliases.map { TextNormalizer.normalize(it) to ingredient }
    }.toMap()
    private val bySingular: Map<String, Ingredient> = buildMap {
        (byAlias + byName).forEach { (key, ingredient) -> put(singularize(key), ingredient) }
    }

    fun match(rawName: String): IngredientMatch {
        val name = normalizeForMatching(rawName)
        if (name.isEmpty()) return IngredientMatch.None(suggestedParent = null)

        // A concrete ingredient beats a category of the same name, so "eggs" finds Egg rather than the Eggs category
        byName[name]?.takeIf { it.kind == IngredientKind.INGREDIENT }?.let { return IngredientMatch.Exact(it) }
        byAlias[name]?.let { return IngredientMatch.Exact(it) }
        byName[name]?.let { return IngredientMatch.Exact(it) }
        bySingular[singularize(name)]?.let { return IngredientMatch.Exact(it) }

        val singular = singularize(name)
        val fuzzy = if (singular.length >= Tuning.FUZZY_MIN_NAME_LENGTH) {
            bySingular.entries
                .map { (key, ingredient) -> ingredient to levenshtein(singular, key, Tuning.FUZZY_MAX_EDIT_DISTANCE) }
                .filter { it.second <= Tuning.FUZZY_MAX_EDIT_DISTANCE }
                .sortedBy { it.second }
                .map { it.first }
        } else {
            emptyList()
        }
        val tokens = singular.split(' ').toSet()
        val containment = bySingular.entries
            .filter { (key, _) -> key.split(' ').let { words -> words.size < tokens.size + 1 && tokens.containsAll(words) } }
            .sortedByDescending { it.key.length }
            .map { it.value }
        val candidates = (fuzzy + containment).distinctBy { it.id }.take(5)
        val parent = containment.firstOrNull()
        return if (candidates.isEmpty()) IngredientMatch.None(parent) else IngredientMatch.Fuzzy(candidates, parent)
    }

    companion object {
        private val parenthetical = Regex("\\([^)]*\\)|\\[[^]]*]")

        // Strips amounts, units, parentheticals and preparation notes so "2 large carrots (peeled), diced" becomes "large carrot"
        fun normalizeForMatching(raw: String): String {
            val parsed = IngredientLineParser.parse(raw)
            val name = parsed.name.replace(parenthetical, " ").substringBefore(',')
            return singularizePhrase(TextNormalizer.normalize(name))
        }

        fun singularize(phrase: String): String = singularizePhrase(phrase)

        private fun singularizePhrase(phrase: String): String {
            val words = phrase.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) return ""
            return (words.dropLast(1) + singularizeWord(words.last())).joinToString(" ")
        }

        private fun singularizeWord(word: String): String = when {
            word.length <= 3 -> word
            word.endsWith("ies") -> word.dropLast(3) + "y"
            word.endsWith("oes") -> word.dropLast(2)
            word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") || word.endsWith("sses") -> word.dropLast(2)
            word.endsWith("ss") || word.endsWith("us") -> word
            word.endsWith("s") -> word.dropLast(1)
            else -> word
        }
    }
}

data class IngredientSearchResult(
    val ingredient: Ingredient,
    val matchedAlias: String?,
    val categoryPath: String,
)

object IngredientSearch {

    fun search(graph: IngredientGraph, query: String, limit: Int = 20): List<IngredientSearchResult> {
        val normalized = TextNormalizer.normalize(query)
        if (normalized.isEmpty()) return emptyList()
        data class Candidate(val ingredient: Ingredient, val alias: String?, val rank: Int)
        val candidates = graph.ingredients.mapNotNull { ingredient ->
            val names = listOf(ingredient.name to null) + graph.aliases[ingredient.id].orEmpty().map { it to it }
            names.mapNotNull { (text, alias) ->
                val value = TextNormalizer.normalize(text)
                val rank = when {
                    value == normalized -> 0
                    value.startsWith(normalized) -> 1
                    value.split(' ').any { it.startsWith(normalized) } -> 2
                    value.contains(normalized) -> 3
                    else -> return@mapNotNull null
                }
                Candidate(ingredient, alias, rank)
            }.minByOrNull { it.rank }
        }
        return candidates
            .sortedWith(
                compareBy<Candidate> { it.rank }
                    .thenBy { if (it.ingredient.kind == IngredientKind.INGREDIENT) 0 else 1 }
                    .thenBy { it.ingredient.name.length }
                    .thenBy { it.ingredient.name.lowercase() },
            )
            .take(limit)
            .map { IngredientSearchResult(it.ingredient, it.alias, graph.categoryPathLabel(it.ingredient.id)) }
    }
}
