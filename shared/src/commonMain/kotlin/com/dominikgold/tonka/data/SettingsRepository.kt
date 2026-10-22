package com.dominikgold.tonka.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dominikgold.tonka.data.db.TonkaDatabase
import com.dominikgold.tonka.platform.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

object SettingKey {
    const val AiModel = "ai_model"
    const val KeepScanPhotos = "keep_scan_photos"
    const val ScanNoticeAcknowledged = "scan_notice_acknowledged"
    const val SeedVersion = "seed_version"
    const val RecipeSort = "recipe_sort"
}

object SecretKey {
    const val AnthropicApiKey = "anthropic_api_key"
}

const val DEFAULT_AI_MODEL = "claude-sonnet-5-5"

class SettingsRepository(
    private val database: TonkaDatabase,
    private val secretStore: SecretStore,
) {

    private val queries get() = database.mealPlanQueries

    private val apiKeyState = MutableStateFlow<String?>(null)
    private var isApiKeyLoaded = false

    fun observe(key: String): Flow<String?> = queries.selectSetting(key).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun observeBoolean(key: String, default: Boolean): Flow<Boolean> =
        observe(key).map { it?.toBooleanStrictOrNull() ?: default }

    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        queries.selectSetting(key).executeAsOneOrNull()
    }

    suspend fun set(key: String, value: String?) = withContext(Dispatchers.IO) {
        if (value == null) queries.deleteSetting(key) else queries.upsertSetting(key, value)
        Unit
    }

    fun observeAiModel(): Flow<String> = observe(SettingKey.AiModel).map { it?.takeIf { model -> model.isNotBlank() } ?: DEFAULT_AI_MODEL }

    suspend fun aiModel(): String = get(SettingKey.AiModel)?.takeIf { it.isNotBlank() } ?: DEFAULT_AI_MODEL

    suspend fun apiKeyFlow(): StateFlow<String?> {
        loadApiKey()
        return apiKeyState
    }

    suspend fun apiKey(): String? {
        loadApiKey()
        return apiKeyState.value
    }

    suspend fun setApiKey(value: String?) {
        val cleaned = value?.trim()?.takeIf { it.isNotEmpty() }
        secretStore.set(SecretKey.AnthropicApiKey, cleaned)
        apiKeyState.value = cleaned
        isApiKeyLoaded = true
    }

    private suspend fun loadApiKey() {
        if (isApiKeyLoaded) return
        apiKeyState.value = secretStore.get(SecretKey.AnthropicApiKey)
        isApiKeyLoaded = true
    }
}
