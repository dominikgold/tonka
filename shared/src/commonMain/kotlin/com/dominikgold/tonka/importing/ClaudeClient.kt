package com.dominikgold.tonka.importing

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

sealed class AiException(message: String) : Exception(message) {
    class MissingKey : AiException("Add your Anthropic API key in Settings first.")
    class InvalidKey : AiException("The API rejected the request. Check your API key in Settings.")
    class RateLimited : AiException("The API is busy or rate limited right now. Try again in a moment.")
    class Offline : AiException("You seem to be offline. Check your connection and retry.")
    class TimedOut : AiException("The request took too long. Try again, maybe with fewer pages.")
    class Refused : AiException("The model declined to process these pages.")
    class NoResult : AiException("The model didn't return a recipe. Retry, or try clearer photos.")
    class Server(status: Int, detail: String) : AiException("The API returned an error ($status). $detail".trim())
}

class ClaudeClient(private val httpClient: HttpClient) {

    suspend fun testKey(apiKey: String) {
        val response = send { httpClient.get("$BASE_URL/v1/models?limit=1") { headers(apiKey, model = null) } }
        response.throwIfFailed()
    }

    suspend fun extractRecipe(
        apiKey: String,
        model: String,
        jpegImages: List<ByteArray>,
        text: String?,
    ): AiRecipe {
        val body = requestBody(model, jpegImages, text)
        var attempt = 0
        while (true) {
            val response = send {
                httpClient.post("$BASE_URL/v1/messages") {
                    headers(apiKey, model)
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
            val status = response.status.value
            if ((status == 429 || status == 529) && attempt < MAX_RETRIES) {
                attempt++
                delay(RETRY_BACKOFF_MILLIS * attempt)
                continue
            }
            response.throwIfFailed()
            return parseResponse(response.bodyAsText())
        }
    }

    private suspend fun send(request: suspend () -> HttpResponse): HttpResponse = try {
        request()
    } catch (e: HttpRequestTimeoutException) {
        throw AiException.TimedOut()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw AiException.Offline()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.headers(apiKey: String, model: String?) {
        header("x-api-key", apiKey)
        header("anthropic-version", ANTHROPIC_VERSION)
        if (model != null && model in MODELS_WITH_SERVER_FALLBACK) {
            header("anthropic-beta", SERVER_FALLBACK_BETA)
        }
    }

    private suspend fun HttpResponse.throwIfFailed() {
        if (status.isSuccess()) return
        val detail = runCatching {
            json.parseToJsonElement(bodyAsText()).jsonObject["error"]?.jsonObject?.get("message")?.let { (it as? JsonPrimitive)?.contentOrNull }
        }.getOrNull().orEmpty()
        throw when (status.value) {
            401, 403 -> AiException.InvalidKey()
            429, 529 -> AiException.RateLimited()
            else -> AiException.Server(status.value, detail)
        }
    }

    private fun requestBody(model: String, jpegImages: List<ByteArray>, text: String?): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", MAX_TOKENS)
        if (model in MODELS_WITH_SERVER_FALLBACK) put("fallbacks", "default")
        put("system", SYSTEM_PROMPT)
        putJsonArray("tools") { add(submitRecipeTool) }
        // Forced tool_choice is rejected by current models, so the system prompt asks for the call instead
        putJsonObject("tool_choice") { put("type", "auto") }
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        jpegImages.forEach { image ->
                            add(
                                buildJsonObject {
                                    put("type", "image")
                                    putJsonObject("source") {
                                        put("type", "base64")
                                        put("media_type", "image/jpeg")
                                        put("data", Base64.encode(image))
                                    }
                                },
                            )
                        }
                        text?.let { pageText ->
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", "<source_text>\n$pageText\n</source_text>")
                                },
                            )
                        }
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put(
                                    "text",
                                    if (jpegImages.isNotEmpty()) {
                                        "These are ${jpegImages.size} photo(s) of cookbook pages, in reading order. Transcribe the recipe and call submit_recipe."
                                    } else {
                                        "Structure the recipe in the source text above and call submit_recipe."
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    private fun parseResponse(body: String): AiRecipe {
        val response = json.parseToJsonElement(body).jsonObject
        val stopReason = (response["stop_reason"] as? JsonPrimitive)?.contentOrNull
        if (stopReason == "refusal") throw AiException.Refused()
        val toolInput = (response["content"] as? JsonArray).orEmpty()
            .map { it.jsonObject }
            .firstOrNull { block ->
                (block["type"] as? JsonPrimitive)?.contentOrNull == "tool_use" &&
                    (block["name"] as? JsonPrimitive)?.contentOrNull == TOOL_NAME
            }
            ?.get("input")
            ?: throw AiException.NoResult()
        return runCatching { json.decodeFromJsonElement(AiRecipe.serializer(), toolInput) }
            .getOrElse { throw AiException.NoResult() }
    }

    companion object {
        private const val BASE_URL = "https://api.anthropic.com"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val SERVER_FALLBACK_BETA = "server-side-fallback-2026-07-01"
        private const val TOOL_NAME = "submit_recipe"
        private const val MAX_TOKENS = 16000
        private const val MAX_RETRIES = 2
        private const val RETRY_BACKOFF_MILLIS = 3_000L

        // Models that accept "fallbacks": "default", which reroutes a safety refusal to another model server-side
        private val MODELS_WITH_SERVER_FALLBACK = setOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-opus-5", "claude-fable-5-1")

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        private val SYSTEM_PROMPT = """
            You turn recipes into structured data for a personal recipe collection.
            Always answer by calling the submit_recipe tool exactly once; do not reply with plain text.
            Rules:
            - Transcribe faithfully in the language of the source. Do not translate.
            - Never invent amounts, ingredients or steps. Leave a field empty when the source doesn't say.
            - Keep each ingredient's original line in raw_text and split out amount, unit, name and preparation.
            - Summarize technical advice from headnotes, sidebars and notes into at most 6 short tips.
            - If the source describes variations (for example a vegan version or a regional style), list them in variants and
              put the variant names each ingredient and step belongs to in its variants field. Leave that field empty for
              items shared by all variants.
            - category_guess is a general ingredient category such as "Root vegetables", "Dairy" or "Spices".
            - diet_guess is VEGAN, VEGETARIAN or OMNI for the ingredient itself.
            - Report unreadable, cut-off or uncertain parts as warnings instead of guessing.
        """.trimIndent()

        private val submitRecipeTool: JsonObject = buildJsonObject {
            put("name", TOOL_NAME)
            put("description", "Submit the transcribed recipe.")
            putJsonObject("input_schema") {
                put("type", "object")
                putJsonObject("properties") {
                    stringProperty("title")
                    stringProperty("description")
                    putJsonObject("source") {
                        put("type", "object")
                        putJsonObject("properties") {
                            stringProperty("book_title")
                            stringProperty("author")
                            stringProperty("page")
                        }
                    }
                    stringProperty("yield")
                    integerProperty("prep_minutes")
                    integerProperty("cook_minutes")
                    stringArrayProperty("sections", "Group names such as Batter or Toppings, in order.")
                    putJsonObject("variants") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                stringProperty("name")
                                stringProperty("description")
                                putJsonObject("is_default") { put("type", "boolean") }
                            }
                            putJsonArray("required") { add("name") }
                        }
                    }
                    putJsonObject("ingredients") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                stringProperty("section")
                                stringProperty("raw_text")
                                stringProperty("amount")
                                stringProperty("unit")
                                stringProperty("name")
                                stringProperty("preparation")
                                putJsonObject("optional") { put("type", "boolean") }
                                putJsonObject("alternatives") {
                                    put("type", "array")
                                    putJsonObject("items") {
                                        put("type", "object")
                                        putJsonObject("properties") {
                                            stringProperty("name")
                                            stringProperty("note")
                                        }
                                    }
                                }
                                stringProperty("category_guess")
                                putJsonObject("diet_guess") {
                                    put("type", "string")
                                    putJsonArray("enum") {
                                        add("VEGAN")
                                        add("VEGETARIAN")
                                        add("OMNI")
                                    }
                                }
                                stringArrayProperty("variants", "Variant names this line belongs to; empty for all.")
                            }
                            putJsonArray("required") {
                                add("raw_text")
                                add("name")
                            }
                        }
                    }
                    putJsonObject("steps") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                stringProperty("section")
                                stringProperty("text")
                                stringArrayProperty("variants", "Variant names this step belongs to; empty for all.")
                            }
                            putJsonArray("required") { add("text") }
                        }
                    }
                    stringArrayProperty("tips", "At most 6 short technical tips.")
                    stringArrayProperty("tag_suggestions", "A few short tags such as cuisine or meal type.")
                    stringArrayProperty("warnings", "Unreadable or uncertain parts.")
                }
                putJsonArray("required") {
                    add("title")
                    add("ingredients")
                    add("steps")
                }
            }
        }

        private fun kotlinx.serialization.json.JsonObjectBuilder.stringProperty(name: String) {
            putJsonObject(name) { put("type", "string") }
        }

        private fun kotlinx.serialization.json.JsonObjectBuilder.integerProperty(name: String) {
            putJsonObject(name) { put("type", "integer") }
        }

        private fun kotlinx.serialization.json.JsonObjectBuilder.stringArrayProperty(name: String, description: String) {
            putJsonObject(name) {
                put("type", "array")
                put("description", description)
                putJsonObject("items") { put("type", "string") }
            }
        }
    }
}

@Serializable
data class AiRecipe(
    val title: String = "",
    val description: String? = null,
    val source: AiSource? = null,
    val yield: String? = null,
    @SerialName("prep_minutes") val prepMinutes: Int? = null,
    @SerialName("cook_minutes") val cookMinutes: Int? = null,
    val sections: List<String> = emptyList(),
    val variants: List<AiVariant> = emptyList(),
    val ingredients: List<AiIngredient> = emptyList(),
    val steps: List<AiStep> = emptyList(),
    val tips: List<String> = emptyList(),
    @SerialName("tag_suggestions") val tagSuggestions: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class AiSource(
    @SerialName("book_title") val bookTitle: String? = null,
    val author: String? = null,
    val page: String? = null,
)

@Serializable
data class AiVariant(
    val name: String,
    val description: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
)

@Serializable
data class AiIngredient(
    val section: String? = null,
    @SerialName("raw_text") val rawText: String = "",
    val amount: String? = null,
    val unit: String? = null,
    val name: String = "",
    val preparation: String? = null,
    val optional: Boolean = false,
    val alternatives: List<AiAlternative> = emptyList(),
    @SerialName("category_guess") val categoryGuess: String? = null,
    @SerialName("diet_guess") val dietGuess: String? = null,
    val variants: List<String> = emptyList(),
)

@Serializable
data class AiAlternative(
    val name: String,
    val note: String? = null,
)

@Serializable
data class AiStep(
    val section: String? = null,
    val text: String,
    val variants: List<String> = emptyList(),
)

private fun JsonArray?.orEmpty(): JsonArray = this ?: buildJsonArray { }
