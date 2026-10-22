package com.dominikgold.tonka.importing

import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.LineId
import com.dominikgold.tonka.model.SectionId

data class ParsedIngredientLine(
    val rawText: String,
    val amountText: String,
    val unit: String,
    val name: String,
    val preparation: String,
    val isOptional: Boolean,
) {
    val hasAmountOrUnit: Boolean get() = amountText.isNotEmpty() || unit.isNotEmpty()

    fun toLine(sectionId: SectionId? = null) = IngredientLine(
        id = LineId.new(),
        sectionId = sectionId,
        rawText = rawText,
        amountText = amountText,
        unit = unit,
        displayName = name.ifBlank { rawText },
        preparation = preparation,
        isOptional = isOptional,
    )
}

// Amounts are kept as text; nothing is converted
object IngredientLineParser {

    private const val UNICODE_FRACTIONS = "½¼¾⅓⅔⅛⅜⅝⅞⅕"
    private const val SINGLE_AMOUNT =
        "(?:\\d+\\s+\\d+/\\d+|\\d+\\s*[$UNICODE_FRACTIONS]|\\d+/\\d+|[$UNICODE_FRACTIONS]|\\d+(?:[.,]\\d+)?)"
    private val amountPattern = Regex("^($SINGLE_AMOUNT(?:\\s*(?:-|–|—|to|bis)\\s*$SINGLE_AMOUNT)?)\\s*")

    private val units = listOf(
        "tablespoons", "tablespoon", "teaspoons", "teaspoon", "kilograms", "kilogram", "milliliters", "millilitres",
        "milliliter", "millilitre", "handfuls", "handful", "bunches", "bunch", "pinches", "pinch", "cloves", "clove",
        "ounces", "ounce", "pounds", "pound", "liters", "litres", "liter", "litre", "grams", "gram", "slices", "slice",
        "sprigs", "sprig", "sticks", "stick", "pieces", "piece", "cups", "cup", "cans", "can", "tins", "tin", "dash",
        "tbsp", "tsp", "kg", "ml", "dl", "cl", "oz", "lbs", "lb", "g", "l",
        "Päckchen", "Prisen", "Prise", "Zehen", "Zehe", "Dosen", "Dose", "Bund", "Becher", "Stück", "Msp", "Pck", "EL", "TL",
    ).sortedByDescending { it.length }

    private val unitPattern = Regex(
        "^(" + units.joinToString("|") { Regex.escape(it) } + ")(?:\\.|(?=\\s|$|,))\\s*(?:of\\s+)?",
        RegexOption.IGNORE_CASE,
    )
    private val optionalPattern = Regex("\\(?\\s*\\boptional\\b\\s*\\)?:?", RegexOption.IGNORE_CASE)
    private val bulletPattern = Regex("^\\s*(?:[-*•·▢□]|\\d+[.)])\\s+")

    fun parse(raw: String): ParsedIngredientLine {
        val rawText = raw.replace(bulletPattern, "").trim()
        var rest = rawText
        val isOptional = optionalPattern.containsMatchIn(rest)
        if (isOptional) rest = rest.replace(optionalPattern, " ").replace(Regex("\\s+"), " ").trim()

        val amount = amountPattern.find(rest)?.let { match ->
            rest = rest.substring(match.range.last + 1)
            match.groupValues[1].trim()
        }.orEmpty()
        val unit = unitPattern.find(rest)?.let { match ->
            rest = rest.substring(match.range.last + 1)
            match.groupValues[1]
        }.orEmpty()

        val name = rest.substringBefore(',').trim().trimEnd('.', ':').trim()
        val preparation = rest.substringAfter(',', missingDelimiterValue = "").trim()
        return ParsedIngredientLine(
            rawText = rawText,
            amountText = amount,
            unit = unit,
            name = name,
            preparation = preparation,
            isOptional = isOptional,
        )
    }

    fun looksLikeIngredient(raw: String): Boolean = parse(raw).hasAmountOrUnit
}
