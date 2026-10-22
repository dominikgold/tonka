package com.dominikgold.tonka.importing

import com.dominikgold.tonka.data.DraftRepository
import com.dominikgold.tonka.data.ImageStore
import com.dominikgold.tonka.data.IngredientRepository
import com.dominikgold.tonka.data.RecipeRepository
import com.dominikgold.tonka.data.SettingKey
import com.dominikgold.tonka.data.SettingsRepository
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.domain.IngredientMatch
import com.dominikgold.tonka.domain.IngredientMatcher
import com.dominikgold.tonka.domain.TextNormalizer
import com.dominikgold.tonka.domain.Tuning
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.DraftId
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.LineAlternative
import com.dominikgold.tonka.model.LineId
import com.dominikgold.tonka.model.RecipeSection
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.RecipeSource
import com.dominikgold.tonka.model.RecipeStep
import com.dominikgold.tonka.model.RecipeTip
import com.dominikgold.tonka.model.RecipeVariant
import com.dominikgold.tonka.model.SectionId
import com.dominikgold.tonka.model.SourceType
import com.dominikgold.tonka.model.StepId
import com.dominikgold.tonka.model.TipId
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.platform.ImageProcessor
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull

class ImportService(
    private val httpClient: HttpClient,
    private val webImporter: WebRecipeImporter,
    private val claudeClient: ClaudeClient,
    private val imageProcessor: ImageProcessor,
    private val imageStore: ImageStore,
    private val draftRepository: DraftRepository,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun startManualDraft(cookbook: Boolean): RecipeDraft {
        val blank = RecipeSnapshot.blank()
        val draft = RecipeDraft(
            id = DraftId.new(),
            snapshot = blank.copy(source = RecipeSource(type = if (cookbook) SourceType.COOKBOOK else SourceType.MANUAL)),
            originSummary = if (cookbook) "Entered from a cookbook" else "Written manually",
        )
        draftRepository.save(draft)
        return draft
    }

    suspend fun startEditDraft(recipeId: com.dominikgold.tonka.model.RecipeId): RecipeDraft? {
        val record = recipeRepository.record(recipeId) ?: return null
        return RecipeDraft(
            id = DraftId.new(),
            snapshot = record.snapshot,
            originSummary = "",
            editingRecipeId = recipeId,
        )
    }

    suspend fun importText(text: String): RecipeDraft {
        val draft = TextRecipeImporter.parse(text).prelinked()
        draftRepository.save(draft)
        return draft
    }

    suspend fun structureTextWithAi(text: String): RecipeDraft {
        val recipe = claudeClient.extractRecipe(
            apiKey = requireApiKey(),
            model = settingsRepository.aiModel(),
            jpegImages = emptyList(),
            text = text.take(Tuning.AI_TEXT_MAX_CHARACTERS),
        )
        val draft = recipe.toDraft(SourceType.TEXT, originSummary = "Structured with AI from text")
        draftRepository.save(draft)
        return draft
    }

    suspend fun fetchWebPage(url: String): WebImportResult {
        val page = webImporter.fetch(url)
        val result = webImporter.parse(page)
        val draft = result.draft ?: return result
        val withImage = draft.withDownloadedHeader().prelinked()
        draftRepository.save(withImage)
        return result.copy(draft = withImage)
    }

    suspend fun extractWebWithAi(url: String, readableText: String): RecipeDraft {
        val recipe = claudeClient.extractRecipe(
            apiKey = requireApiKey(),
            model = settingsRepository.aiModel(),
            jpegImages = emptyList(),
            text = readableText.take(Tuning.AI_TEXT_MAX_CHARACTERS),
        )
        val host = runCatching { io.ktor.http.Url(url).host.removePrefix("www.") }.getOrDefault(url)
        val draft = recipe.toDraft(SourceType.WEB, originSummary = "Extracted with AI from $host")
            .let { it.copy(snapshot = it.snapshot.copy(source = it.snapshot.source.copy(url = url))) }
        draftRepository.save(draft)
        return draft
    }

    // The photos stay with the caller so a failed scan can be retried without retaking them
    suspend fun scanPages(photos: List<ByteArray>): RecipeDraft {
        val apiKey = requireApiKey()
        val uploads = photos.mapNotNull { photo ->
            imageProcessor.toJpeg(
                bytes = photo,
                maxLongEdge = Tuning.SCAN_UPLOAD_MAX_LONG_EDGE,
                quality = Tuning.SCAN_UPLOAD_JPEG_QUALITY,
            )?.jpegBytes
        }
        require(uploads.isNotEmpty()) { "None of the photos could be read." }
        val recipe = claudeClient.extractRecipe(apiKey, settingsRepository.aiModel(), uploads, text = null)
        val storedPhotos = photos.mapNotNull { imageStore.store(it) }
        val pageLabel = if (photos.size == 1) "1 page" else "${photos.size} pages"
        val draft = recipe.toDraft(SourceType.SCAN, originSummary = "Scanned, $pageLabel").copy(scanImagePaths = storedPhotos)
        draftRepository.save(draft)
        return draft
    }

    suspend fun testApiKey(apiKey: String) = claudeClient.testKey(apiKey.trim())

    suspend fun keepScanPhotos(): Boolean = settingsRepository.get(SettingKey.KeepScanPhotos)?.toBooleanStrictOrNull() ?: true

    private suspend fun requireApiKey(): String = settingsRepository.apiKey() ?: throw AiException.MissingKey()

    private suspend fun RecipeDraft.withDownloadedHeader(): RecipeDraft {
        for (url in imageCandidates) {
            val bytes = withTimeoutOrNull(Tuning.WEB_FETCH_TIMEOUT_MILLIS) {
                runCatching {
                    val response = httpClient.get(url) { header(HttpHeaders.Accept, "image/*") }
                    if (response.status.isSuccess()) response.readRawBytes() else null
                }.getOrNull()
            } ?: continue
            val path = imageStore.store(bytes) ?: continue
            return copy(snapshot = snapshot.copy(headerImagePath = path))
        }
        return this
    }

    // Links lines that match an existing ingredient exactly; everything else waits for the matching screen
    private fun RecipeDraft.prelinked(): RecipeDraft {
        val graph = ingredientRepository.graph.value
        val matcher = IngredientMatcher(graph)
        return copy(
            snapshot = snapshot.copy(
                lines = snapshot.lines.map { line ->
                    if (line.ingredientId != null) return@map line
                    val match = matcher.match(line.displayName.ifBlank { line.rawText })
                    if (match is IngredientMatch.Exact) line.copy(ingredientId = match.ingredient.id) else line
                },
            ),
        )
    }

    private fun AiRecipe.toDraft(sourceType: SourceType, originSummary: String): RecipeDraft {
        val graph = ingredientRepository.graph.value
        val matcher = IngredientMatcher(graph)
        val variantsByName = variants.filter { it.name.isNotBlank() }.distinctBy { it.name.lowercase() }
            .associate { it.name.lowercase() to RecipeVariant(id = VariantId.new(), name = it.name, description = it.description.orEmpty(), isDefault = it.isDefault) }
        val variantList = variantsByName.values.toList().ifEmpty {
            listOf(RecipeVariant(id = VariantId.new(), name = "Default", isDefault = true))
        }
        val sectionsByName = (sections + ingredients.mapNotNull { it.section } + steps.mapNotNull { it.section })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .associate { it.lowercase() to RecipeSection(SectionId.new(), it) }

        fun scope(names: List<String>): Set<VariantId> = names.mapNotNull { variantsByName[it.lowercase()]?.id }.toSet()
        fun section(name: String?): SectionId? = name?.trim()?.lowercase()?.let { sectionsByName[it]?.id }

        val categoryGuesses = mutableMapOf<String, String>()
        val dietGuesses = mutableMapOf<String, DietClass>()
        val lines = ingredients.filter { it.rawText.isNotBlank() || it.name.isNotBlank() }.map { ingredient ->
            val lineId = LineId.new()
            val displayName = ingredient.name.ifBlank { IngredientLineParser.parse(ingredient.rawText).name }
            val linked = (matcher.match(displayName) as? IngredientMatch.Exact)?.ingredient
            val alternatives = ingredient.alternatives.mapNotNull { alternative ->
                (matcher.match(alternative.name) as? IngredientMatch.Exact)?.ingredient?.let {
                    LineAlternative(ingredientId = it.id, note = alternative.note.orEmpty())
                }
            }
            val unmatchedAlternatives = ingredient.alternatives.filter { alternative ->
                matcher.match(alternative.name) !is IngredientMatch.Exact
            }
            ingredient.categoryGuess?.takeIf { it.isNotBlank() }?.let { categoryGuesses[lineId.value] = it }
            ingredient.dietGuess?.let { guess -> DietClass.entries.firstOrNull { it.name == guess } }?.let { dietGuesses[lineId.value] = it }
            IngredientLine(
                id = lineId,
                sectionId = section(ingredient.section),
                rawText = ingredient.rawText.ifBlank { listOfNotNull(ingredient.amount, ingredient.unit, ingredient.name).joinToString(" ") },
                amountText = ingredient.amount.orEmpty(),
                unit = ingredient.unit.orEmpty(),
                ingredientId = linked?.id,
                displayName = displayName,
                preparation = (listOfNotNull(ingredient.preparation?.takeIf { it.isNotBlank() }) +
                    unmatchedAlternatives.map { "or ${it.name}" }).joinToString(", "),
                isOptional = ingredient.optional,
                alternatives = alternatives,
                variantIds = scope(ingredient.variants),
            )
        }
        val dietHint = dietGuesses.values.takeIf { it.isNotEmpty() }?.maxBy { it.ordinal }
        return RecipeDraft(
            id = DraftId.new(),
            snapshot = RecipeSnapshot(
                id = com.dominikgold.tonka.model.RecipeId.new(),
                title = title.trim(),
                description = description.orEmpty().trim(),
                source = RecipeSource(
                    type = if (sourceType == SourceType.SCAN && source?.bookTitle != null) SourceType.COOKBOOK else sourceType,
                    book = source?.bookTitle?.takeIf { it.isNotBlank() },
                    author = source?.author?.takeIf { it.isNotBlank() },
                    page = source?.page?.takeIf { it.isNotBlank() },
                ),
                yieldText = yield.orEmpty(),
                prepMinutes = prepMinutes,
                cookMinutes = cookMinutes,
                variants = variantList,
                sections = sectionsByName.values.toList(),
                lines = lines,
                steps = steps.filter { it.text.isNotBlank() }.map {
                    RecipeStep(id = StepId.new(), sectionId = section(it.section), text = it.text.trim(), variantIds = scope(it.variants))
                },
                tips = tips.filter { it.isNotBlank() }.take(MAX_TIPS).map { RecipeTip(id = TipId.new(), text = it.trim()) },
            ),
            originSummary = originSummary,
            warnings = warnings,
            tagSuggestions = tagSuggestions.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            dietHint = dietHint,
            categoryGuesses = categoryGuesses,
            dietGuesses = dietGuesses,
        )
    }

    private companion object {
        const val MAX_TIPS = 6
    }
}

// Picks the category a guess like "Root vegetables" refers to, if the graph knows it
fun IngredientGraph.categoryForGuess(guess: String?): com.dominikgold.tonka.model.Ingredient? {
    val normalized = guess?.let { TextNormalizer.normalize(it) }?.takeIf { it.isNotEmpty() } ?: return null
    val categories = ingredients.filter { it.kind == IngredientKind.CATEGORY }
    return categories.firstOrNull { TextNormalizer.normalize(it.name) == normalized }
        ?: categories.firstOrNull { category -> aliases[category.id].orEmpty().any { TextNormalizer.normalize(it) == normalized } }
        ?: categories.firstOrNull { IngredientMatcher.singularize(TextNormalizer.normalize(it.name)) == IngredientMatcher.singularize(normalized) }
}
