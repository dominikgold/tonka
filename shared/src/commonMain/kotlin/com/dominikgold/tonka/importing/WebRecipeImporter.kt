package com.dominikgold.tonka.importing

import com.dominikgold.tonka.domain.Tuning
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.DraftId
import com.dominikgold.tonka.model.RecipeSection
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.RecipeSource
import com.dominikgold.tonka.model.RecipeStep
import com.dominikgold.tonka.model.SectionId
import com.dominikgold.tonka.model.SourceType
import com.dominikgold.tonka.model.StepId
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

class WebImportException(message: String) : Exception(message)

data class WebPage(
    val url: String,
    val html: String,
)

data class WebImportResult(
    // Null when the page has no schema.org Recipe; readableText can still go to Extract with AI
    val draft: RecipeDraft?,
    val readableText: String,
    val pageTitle: String,
)

class WebRecipeImporter(private val httpClient: HttpClient) {

    suspend fun fetch(url: String): WebPage {
        val normalizedUrl = url.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val response = try {
            withTimeout(Tuning.WEB_FETCH_TIMEOUT_MILLIS) {
                httpClient.get(normalizedUrl) {
                    header(HttpHeaders.UserAgent, DESKTOP_USER_AGENT)
                    header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw WebImportException("The page took too long to load.")
        } catch (e: Exception) {
            throw WebImportException("The page could not be loaded. Check the address and your connection.")
        }
        if (!response.status.isSuccess()) throw WebImportException("The site answered with ${response.status.value}.")
        return WebPage(url = response.call.request.url.toString(), html = response.bodyAsText())
    }

    fun parse(page: WebPage): WebImportResult {
        val document = Ksoup.parse(page.html, page.url)
        val readableText = document.body().text().take(Tuning.AI_TEXT_MAX_CHARACTERS)
        val host = runCatching { Url(page.url).host.removePrefix("www.") }.getOrDefault(page.url)
        val origin = "Imported from $host"
        val recipe = findJsonLdRecipe(document)?.let { mapJsonLd(it, page.url, document, origin) }
            ?: findMicrodataRecipe(document)?.let { mapMicrodata(it, page.url, document, origin) }
        return WebImportResult(draft = recipe, readableText = readableText, pageTitle = document.title())
    }

    private fun findJsonLdRecipe(document: Document): JsonObject? {
        for (script in document.select("script[type=application/ld+json]")) {
            val element = parseLenient(script.data()) ?: continue
            findRecipeObject(element)?.let { return it }
        }
        return null
    }

    private fun parseLenient(text: String): JsonElement? {
        val trimmed = text.trim().removePrefix("<!--").removeSuffix("-->").trim()
        return runCatching { lenientJson.parseToJsonElement(trimmed) }.getOrNull()
            ?: runCatching { lenientJson.parseToJsonElement(trimmed.replace(Regex("[\\u0000-\\u001F]"), " ")) }.getOrNull()
    }

    private fun findRecipeObject(element: JsonElement): JsonObject? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { findRecipeObject(it) }
        is JsonObject -> when {
            element.isOfType("Recipe") -> element
            else -> element["@graph"]?.let(::findRecipeObject)
                ?: element["mainEntity"]?.let(::findRecipeObject)
        }
        else -> null
    }

    private fun JsonObject.isOfType(type: String): Boolean = when (val value = this["@type"]) {
        is JsonPrimitive -> value.contentOrNull?.substringAfterLast('/') == type
        is JsonArray -> value.any { (it as? JsonPrimitive)?.contentOrNull?.substringAfterLast('/') == type }
        else -> false
    }

    private fun mapJsonLd(recipe: JsonObject, pageUrl: String, document: Document, origin: String): RecipeDraft {
        val warnings = mutableListOf<String>()
        val sections = mutableListOf<RecipeSection>()
        val steps = mutableListOf<RecipeStep>()

        fun addInstructions(element: JsonElement?, sectionId: SectionId?) {
            when (element) {
                null, JsonNull -> Unit
                is JsonPrimitive -> element.contentOrNull?.let { text ->
                    cleanText(text, keepNewlines = true).split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        steps += RecipeStep(id = StepId.new(), sectionId = sectionId, text = it)
                    }
                }
                is JsonArray -> element.forEach { addInstructions(it, sectionId) }
                is JsonObject -> when {
                    element.isOfType("HowToSection") -> {
                        val name = element.string("name")?.let(::cleanText).orEmpty()
                        val section = if (name.isNotEmpty()) SectionId.new().also { sections += RecipeSection(it, name) } else sectionId
                        addInstructions(element["itemListElement"], section)
                    }
                    element.isOfType("ItemList") -> addInstructions(element["itemListElement"], sectionId)
                    else -> (element.string("text") ?: element.string("name"))?.let(::cleanText)?.takeIf { it.isNotEmpty() }?.let {
                        steps += RecipeStep(id = StepId.new(), sectionId = sectionId, text = it)
                    }
                }
            }
        }
        addInstructions(recipe["recipeInstructions"], null)

        val lines = recipe.strings("recipeIngredient").ifEmpty { recipe.strings("ingredients") }
            .map { IngredientLineParser.parse(cleanText(it)).toLine() }
        if (lines.isEmpty()) warnings += "The page listed no ingredients."
        if (steps.isEmpty()) warnings += "The page listed no instructions."

        val prepMinutes = recipe.string("prepTime")?.let(IsoDuration::toMinutes)
        val cookMinutes = recipe.string("cookTime")?.let(IsoDuration::toMinutes)
            ?: recipe.string("totalTime")?.let(IsoDuration::toMinutes)?.let { total -> total - (prepMinutes ?: 0) }?.takeIf { it > 0 }
        val tagSuggestions = (recipe.strings("keywords") + recipe.strings("recipeCategory") + recipe.strings("recipeCuisine"))
            .flatMap { it.split(',') }
            .map { cleanText(it) }
            .filter { it.isNotEmpty() && it.length <= 30 }
            .distinctBy { it.lowercase() }
        val dietHint = recipe.strings("suitableForDiet").let { diets ->
            when {
                diets.any { it.contains("VeganDiet") } -> DietClass.VEGAN
                diets.any { it.contains("VegetarianDiet") } -> DietClass.VEGETARIAN
                else -> null
            }
        }
        val sourceUrl = recipe.string("url")?.takeIf { it.startsWith("http") }
            ?: document.selectFirst("link[rel=canonical]")?.attr("abs:href")?.takeIf { it.isNotEmpty() }
            ?: pageUrl

        return RecipeDraft(
            id = DraftId.new(),
            snapshot = RecipeSnapshot.blank().copy(
                title = recipe.string("name")?.let(::cleanText).orEmpty().ifEmpty { document.title() },
                description = recipe.string("description")?.let(::cleanText).orEmpty(),
                source = RecipeSource(type = SourceType.WEB, url = sourceUrl, author = authorOf(recipe["author"])),
                yieldText = yieldOf(recipe["recipeYield"]),
                prepMinutes = prepMinutes,
                cookMinutes = cookMinutes,
                sections = sections,
                lines = lines,
                steps = steps,
            ),
            originSummary = origin,
            imageCandidates = imagesOf(recipe["image"], pageUrl),
            warnings = warnings,
            tagSuggestions = tagSuggestions,
            dietHint = dietHint,
        )
    }

    private fun findMicrodataRecipe(document: Document): Element? =
        document.select("[itemtype*=schema.org/Recipe]").firstOrNull()

    private fun mapMicrodata(recipe: Element, pageUrl: String, document: Document, origin: String): RecipeDraft {
        val properties = recipe.select("[itemprop]").filter { element ->
            element.parents().firstOrNull { it.hasAttr("itemscope") } == recipe || element == recipe
        }

        fun values(name: String): List<Element> = properties.filter { element ->
            element.attr("itemprop").split(' ').any { it.equals(name, ignoreCase = true) }
        }

        fun value(element: Element): String = when {
            element.hasAttr("content") -> element.attr("content")
            element.hasAttr("datetime") -> element.attr("datetime")
            element.tagName() == "meta" -> element.attr("content")
            element.tagName() == "img" -> element.attr("abs:src")
            element.tagName() == "a" || element.tagName() == "link" -> element.attr("abs:href").ifEmpty { element.text() }
            else -> element.text()
        }.trim()

        fun first(name: String): String? = values(name).firstOrNull()?.let(::value)?.takeIf { it.isNotEmpty() }

        val lines = (values("recipeIngredient") + values("ingredients")).map { IngredientLineParser.parse(cleanText(it.text())).toLine() }
        val steps = values("recipeInstructions").flatMap { element ->
            val nested = element.select("[itemprop=text], li")
            if (nested.isNotEmpty()) nested.map { it.text() } else value(element).split('\n')
        }.map { cleanText(it) }.filter { it.isNotEmpty() }.map { RecipeStep(id = StepId.new(), text = it) }
        val prepMinutes = first("prepTime")?.let(IsoDuration::toMinutes)
        val cookMinutes = first("cookTime")?.let(IsoDuration::toMinutes)
        val warnings = buildList {
            if (lines.isEmpty()) add("The page listed no ingredients.")
            if (steps.isEmpty()) add("The page listed no instructions.")
        }
        val images = values("image").map(::value).filter { it.startsWith("http") }

        return RecipeDraft(
            id = DraftId.new(),
            snapshot = RecipeSnapshot.blank().copy(
                title = first("name")?.let(::cleanText).orEmpty().ifEmpty { document.title() },
                description = first("description")?.let(::cleanText).orEmpty(),
                source = RecipeSource(
                    type = SourceType.WEB,
                    url = document.selectFirst("link[rel=canonical]")?.attr("abs:href")?.takeIf { it.isNotEmpty() } ?: pageUrl,
                    author = first("author")?.let(::cleanText),
                ),
                yieldText = first("recipeYield")?.let(::cleanText).orEmpty(),
                prepMinutes = prepMinutes,
                cookMinutes = cookMinutes,
                lines = lines,
                steps = steps,
            ),
            originSummary = origin,
            imageCandidates = images.take(Tuning.MAX_WEB_IMAGE_CANDIDATES),
            warnings = warnings,
            tagSuggestions = (values("keywords") + values("recipeCategory") + values("recipeCuisine"))
                .flatMap { value(it).split(',') }
                .map { cleanText(it) }
                .filter { it.isNotEmpty() && it.length <= 30 }
                .distinctBy { it.lowercase() },
        )
    }

    private fun authorOf(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> element.string("name")
        is JsonArray -> element.mapNotNull { authorOf(it) }.joinToString(", ").takeIf { it.isNotEmpty() }
        else -> null
    }?.let(::cleanText)

    private fun yieldOf(element: JsonElement?): String = when (element) {
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.maxByOrNull { it.length }.orEmpty()
        else -> ""
    }.let(::cleanText)

    // Sorted largest first when the page states sizes; the importer downloads the first that decodes
    private fun imagesOf(element: JsonElement?, pageUrl: String): List<String> {
        data class Candidate(val url: String, val area: Int)
        fun collect(element: JsonElement?): List<Candidate> = when (element) {
            is JsonPrimitive -> listOfNotNull(element.contentOrNull?.let { Candidate(it, 0) })
            is JsonArray -> element.flatMap { collect(it) }
            is JsonObject -> listOfNotNull(
                (element.string("url") ?: element.string("contentUrl"))?.let { url ->
                    val width = (element["width"] as? JsonPrimitive)?.intOrNull ?: 0
                    val height = (element["height"] as? JsonPrimitive)?.intOrNull ?: 0
                    Candidate(url, width * height)
                },
            )
            else -> emptyList()
        }
        return collect(element)
            .map { it.copy(url = resolveUrl(pageUrl, it.url)) }
            .filter { it.url.startsWith("http") }
            .sortedByDescending { it.area }
            .map { it.url }
            .distinct()
            .take(Tuning.MAX_WEB_IMAGE_CANDIDATES)
    }

    private fun resolveUrl(base: String, url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> runCatching { Url(base).let { "${it.protocol.name}://${it.host}$url" } }.getOrDefault(url)
        else -> url
    }

    private fun JsonObject.string(key: String): String? = when (val value = this[key]) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> value.string("@value") ?: value.string("name")
        is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    private fun JsonObject.strings(key: String): List<String> = when (val value = this[key]) {
        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
        is JsonArray -> value.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull
                is JsonObject -> item.string("text") ?: item.string("name") ?: item.string("@id")
                else -> null
            }
        }
        is JsonObject -> listOfNotNull(value.string("text") ?: value.string("name") ?: value.string("@id"))
        else -> emptyList()
    }

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

        private val lenientJson = Json {
            isLenient = true
            ignoreUnknownKeys = true
            allowTrailingComma = true
        }

        // Decodes entities, strips tags and collapses whitespace
        fun cleanText(text: String, keepNewlines: Boolean = false): String {
            if (keepNewlines) {
                return text.split(Regex("\\n|<br\\s*/?>", RegexOption.IGNORE_CASE))
                    .joinToString("\n") { cleanText(it) }
                    .trim()
            }
            val decoded = if (text.contains('<') || text.contains('&')) Ksoup.parseBodyFragment(text).text() else text
            return decoded.replace(Regex("\\s+"), " ").trim()
        }
    }
}

object IsoDuration {

    private val isoPattern = Regex(
        "^P(?:(\\d+(?:\\.\\d+)?)D)?(?:T(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$",
        RegexOption.IGNORE_CASE,
    )
    private val loosePattern = Regex("(\\d+)\\s*(h|hr|hour|std|min|m)", RegexOption.IGNORE_CASE)

    fun toMinutes(text: String): Int? {
        val trimmed = text.trim()
        isoPattern.find(trimmed)?.let { match ->
            val (days, hours, minutes, seconds) = match.destructured
            val total = (days.toDoubleOrNull() ?: 0.0) * 1440 + (hours.toDoubleOrNull() ?: 0.0) * 60 +
                (minutes.toDoubleOrNull() ?: 0.0) + (seconds.toDoubleOrNull() ?: 0.0) / 60
            return total.toInt().takeIf { it > 0 }
        }
        val loose = loosePattern.findAll(trimmed).sumOf { match ->
            val value = match.groupValues[1].toInt()
            if (match.groupValues[2].lowercase().startsWith("h") || match.groupValues[2].lowercase() == "std") value * 60 else value
        }
        return loose.takeIf { it > 0 } ?: trimmed.toIntOrNull()
    }
}
