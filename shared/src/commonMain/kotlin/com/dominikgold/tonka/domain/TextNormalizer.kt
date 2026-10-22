package com.dominikgold.tonka.domain

object TextNormalizer {

    private val diacritics = mapOf(
        'à' to "a", 'á' to "a", 'â' to "a", 'ã' to "a", 'ä' to "a", 'å' to "a", 'ā' to "a",
        'ç' to "c", 'č' to "c",
        'è' to "e", 'é' to "e", 'ê' to "e", 'ë' to "e", 'ē' to "e",
        'ì' to "i", 'í' to "i", 'î' to "i", 'ï' to "i",
        'ñ' to "n",
        'ò' to "o", 'ó' to "o", 'ô' to "o", 'õ' to "o", 'ö' to "o", 'ø' to "o",
        'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u",
        'ý' to "y", 'ÿ' to "y",
        'š' to "s", 'ž' to "z",
        'ß' to "ss", 'æ' to "ae", 'œ' to "oe",
    )

    private val whitespace = Regex("\\s+")
    private val wordSeparator = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(text: String): String {
        val lowercase = text.lowercase()
        val builder = StringBuilder(lowercase.length)
        for (char in lowercase) {
            builder.append(diacritics[char] ?: char)
        }
        return builder.toString().replace(whitespace, " ").trim()
    }

    fun words(text: String): List<String> = normalize(text).split(wordSeparator).filter { it.isNotBlank() }
}

fun levenshtein(first: String, second: String, limit: Int = Int.MAX_VALUE): Int {
    if (first == second) return 0
    if (kotlin.math.abs(first.length - second.length) > limit) return limit + 1
    var previous = IntArray(second.length + 1) { it }
    var current = IntArray(second.length + 1)
    for (i in 1..first.length) {
        current[0] = i
        var rowMinimum = current[0]
        for (j in 1..second.length) {
            val cost = if (first[i - 1] == second[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            rowMinimum = minOf(rowMinimum, current[j])
        }
        if (rowMinimum > limit) return limit + 1
        val swap = previous
        previous = current
        current = swap
    }
    return previous[second.length]
}
