package com.dominikgold.tonka.data

import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.Seasonality
import kotlinx.datetime.Month
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SeedData(
    val version: Int,
    val categories: List<SeedNode>,
    val ingredients: List<SeedNode>,
) {

    private val categoriesByKey by lazy { categories.associateBy { it.key } }

    // Explicit value, else SEASONAL when fresh months exist, else the nearest category default
    fun seasonalityOf(node: SeedNode): Seasonality {
        node.season?.let { return it }
        if (node.fresh != null) return Seasonality.SEASONAL
        var parentKeys = node.parents
        val visited = mutableSetOf<String>()
        while (parentKeys.isNotEmpty()) {
            val parents = parentKeys.mapNotNull { categoriesByKey[it] }.filter { visited.add(it.key) }
            parents.firstNotNullOfOrNull { it.season }?.let { return it }
            parentKeys = parents.flatMap { it.parents }
        }
        return Seasonality.NOT_APPLICABLE
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): SeedData = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class SeedNode(
    val key: String,
    val name: String,
    val parents: List<String> = emptyList(),
    val diet: DietClass = DietClass.UNKNOWN,
    val season: Seasonality? = null,
    // Month ranges such as "6-10" or "11-5,7"; ranges may wrap around the year end
    val fresh: String? = null,
    val storage: String? = null,
    val aliases: List<String> = emptyList(),
    val staple: Boolean = false,
) {

    fun freshMonths(): MonthSet = parseMonths(fresh)

    fun storageMonths(): MonthSet = parseMonths(storage)
}

private fun parseMonths(text: String?): MonthSet {
    if (text.isNullOrBlank()) return MonthSet.None
    return text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.fold(MonthSet.None) { set, part ->
        val bounds = part.split('-').mapNotNull { it.trim().toIntOrNull()?.takeIf { month -> month in 1..12 } }
        when (bounds.size) {
            1 -> set + MonthSet.of(Month.entries[bounds[0] - 1])
            2 -> set + MonthSet.range(Month.entries[bounds[0] - 1], Month.entries[bounds[1] - 1])
            else -> set
        }
    }
}
