package com.dominikgold.tonka.importing

import com.dominikgold.tonka.model.DraftId
import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.RecipeSection
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.RecipeSource
import com.dominikgold.tonka.model.RecipeStep
import com.dominikgold.tonka.model.RecipeTip
import com.dominikgold.tonka.model.SectionId
import com.dominikgold.tonka.model.SourceType
import com.dominikgold.tonka.model.StepId
import com.dominikgold.tonka.model.TipId

object TextRecipeImporter {

    private enum class Block { Description, Ingredients, Steps, Tips }

    private val ingredientHeadings = setOf("ingredients", "zutaten", "you will need", "you'll need")
    private val stepHeadings = setOf(
        "method", "instructions", "directions", "steps", "preparation", "zubereitung", "anleitung", "how to make it",
    )
    private val tipHeadings = setOf("tips", "notes", "tipps", "hinweise", "tip", "note", "tipp")

    private val numberedLine = Regex("^\\s*(?:step\\s*)?(\\d+)[.):]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val bullet = Regex("^\\s*[-*•·]\\s+")
    private val markdownDecoration = Regex("^#+\\s*|\\*\\*|__")

    fun parse(text: String, originSummary: String = "Imported from text"): RecipeDraft {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        val titleIndex = lines.indexOfFirst { it.isNotBlank() }
        if (titleIndex < 0) {
            return RecipeDraft(
                id = DraftId.new(),
                snapshot = RecipeSnapshot.blank().copy(source = RecipeSource(type = SourceType.TEXT)),
                originSummary = originSummary,
                warnings = listOf("The text was empty."),
            )
        }
        val title = cleanHeading(lines[titleIndex])
        val body = lines.drop(titleIndex + 1)
        val hasHeadings = body.any { headingBlock(it) != null }

        val warnings = mutableListOf<String>()
        val description = mutableListOf<String>()
        val ingredientLines = mutableListOf<IngredientLine>()
        val stepTexts = mutableListOf<Pair<String, SectionId?>>()
        val tips = mutableListOf<String>()
        val sections = mutableListOf<RecipeSection>()

        fun sectionFor(name: String): SectionId =
            sections.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: SectionId.new().also { sections += RecipeSection(it, name) }

        if (hasHeadings) {
            var block = Block.Description
            var currentSection: SectionId? = null
            val stepBuffer = mutableListOf<String>()
            var stepsAreNumbered = false

            fun flushParagraph() {
                if (stepBuffer.isNotEmpty()) {
                    stepTexts += stepBuffer.joinToString(" ") to currentSection
                    stepBuffer.clear()
                }
            }

            for (line in body) {
                val heading = headingBlock(line)
                if (heading != null) {
                    flushParagraph()
                    block = heading
                    currentSection = null
                    stepsAreNumbered = false
                    continue
                }
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    if (block == Block.Steps) flushParagraph()
                    continue
                }
                when (block) {
                    Block.Description -> description += trimmed
                    Block.Ingredients -> {
                        if (isSubHeading(trimmed)) {
                            currentSection = sectionFor(cleanHeading(trimmed))
                        } else {
                            ingredientLines += IngredientLineParser.parse(trimmed).toLine(currentSection)
                        }
                    }
                    Block.Steps -> {
                        val numbered = numberedLine.find(trimmed)
                        when {
                            numbered != null -> {
                                flushParagraph()
                                stepsAreNumbered = true
                                numbered.groupValues[2].trim().takeIf { it.isNotEmpty() }?.let { stepBuffer += it }
                            }
                            isSubHeading(trimmed) && !stepsAreNumbered -> {
                                flushParagraph()
                                currentSection = sectionFor(cleanHeading(trimmed))
                            }
                            bullet.containsMatchIn(trimmed) -> {
                                flushParagraph()
                                stepBuffer += trimmed.replace(bullet, "")
                            }
                            else -> stepBuffer += trimmed
                        }
                    }
                    Block.Tips -> tips += trimmed.replace(bullet, "")
                }
            }
            flushParagraph()
        } else {
            warnings += "No headings like Ingredients or Method were found. Lines with an amount or unit became ingredients and the rest became steps, so please check them."
            for (line in body) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (IngredientLineParser.looksLikeIngredient(trimmed)) {
                    ingredientLines += IngredientLineParser.parse(trimmed).toLine()
                } else {
                    val numbered = numberedLine.find(trimmed)
                    stepTexts += (numbered?.groupValues?.get(2)?.trim() ?: trimmed.replace(bullet, "")) to null
                }
            }
        }

        if (ingredientLines.isEmpty()) warnings += "No ingredients were recognized."
        if (stepTexts.isEmpty()) warnings += "No steps were recognized."

        val blank = RecipeSnapshot.blank()
        return RecipeDraft(
            id = DraftId.new(),
            snapshot = blank.copy(
                title = title,
                description = description.joinToString("\n"),
                source = RecipeSource(type = SourceType.TEXT),
                sections = sections,
                lines = ingredientLines,
                steps = stepTexts.map { (text, sectionId) -> RecipeStep(id = StepId.new(), sectionId = sectionId, text = text) },
                tips = tips.map { RecipeTip(id = TipId.new(), text = it) },
            ),
            originSummary = originSummary,
            warnings = warnings,
        )
    }

    private fun headingBlock(line: String): Block? {
        val cleaned = cleanHeading(line).lowercase()
        if (cleaned.isEmpty() || cleaned.length > 40) return null
        return when (cleaned) {
            in ingredientHeadings -> Block.Ingredients
            in stepHeadings -> Block.Steps
            in tipHeadings -> Block.Tips
            else -> null
        }
    }

    private fun isSubHeading(line: String): Boolean =
        line.endsWith(":") && line.length <= 50 && !IngredientLineParser.looksLikeIngredient(line)

    private fun cleanHeading(line: String): String = line.trim().replace(markdownDecoration, "").trim().trimEnd(':').trim()
}
