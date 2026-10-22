package com.dominikgold.tonka.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.backup.BackupException
import com.dominikgold.tonka.backup.BackupPreview
import com.dominikgold.tonka.backup.BackupService
import com.dominikgold.tonka.data.DEFAULT_AI_MODEL
import com.dominikgold.tonka.data.IngredientRepository
import com.dominikgold.tonka.data.SettingKey
import com.dominikgold.tonka.data.SettingsRepository
import com.dominikgold.tonka.importing.ImportService
import com.dominikgold.tonka.navigation.IngredientsRoute
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.WeekdayThemesRoute
import com.dominikgold.tonka.util.UiEventsFlow
import com.dominikgold.tonka.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class KeyTestResult { Testing, Valid, Invalid }

data class PendingImport(
    val bytes: ByteArray,
    val preview: BackupPreview,
) {
    override fun equals(other: Any?): Boolean = other is PendingImport && other.preview == preview

    override fun hashCode(): Int = preview.hashCode()
}

data class SettingsState(
    val apiKeyInput: String = "",
    val hasStoredKey: Boolean = false,
    val model: String = DEFAULT_AI_MODEL,
    val keyTestResult: KeyTestResult? = null,
    val keyTestMessage: String? = null,
    val keepScanPhotos: Boolean = true,
    val reviewCount: Int = 0,
    val exportProgress: Float? = null,
    val exportedBytes: ByteArray? = null,
    val pendingImport: PendingImport? = null,
    val isImporting: Boolean = false,
    val isShowingLicences: Boolean = false,
)

interface SettingsActions {
    fun onBackClicked()
    fun onApiKeyChanged(key: String)
    fun onSaveApiKeyClicked()
    fun onRemoveApiKeyClicked()
    fun onTestKeyClicked()
    fun onModelChanged(model: String)
    fun onKeepScanPhotosToggled(keep: Boolean)
    fun onWeekdayThemesClicked()
    fun onReviewQueueClicked()
    fun onExportClicked()
    fun onExportSaved()
    fun onBackupFileOpened(bytes: ByteArray)
    fun onImportConfirmed()
    fun onImportDismissed()
    fun onLicencesClicked()
    fun onLicencesDismissed()
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val importService: ImportService,
    private val backupService: BackupService,
    ingredientRepository: IngredientRepository,
    private val navigator: Navigator,
) : ViewModel(), SettingsActions {

    val state = MutableStateFlow(SettingsState())
    val messages = UiEventsFlow<String>()

    val defaultBackupFileName: String get() = backupService.defaultFileName()

    init {
        viewModelScope.launch {
            val key = settingsRepository.apiKey()
            state.update {
                it.copy(
                    hasStoredKey = key != null,
                    model = settingsRepository.aiModel(),
                    keepScanPhotos = importService.keepScanPhotos(),
                )
            }
        }
        viewModelScope.launch {
            ingredientRepository.graph.collect { graph -> state.update { it.copy(reviewCount = graph.reviewQueue.size) } }
        }
    }

    override fun onBackClicked() = navigator.goBack()

    override fun onApiKeyChanged(key: String) = state.update { it.copy(apiKeyInput = key, keyTestResult = null, keyTestMessage = null) }

    override fun onSaveApiKeyClicked() {
        val key = state.value.apiKeyInput.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.setApiKey(key)
            state.update { it.copy(apiKeyInput = "", hasStoredKey = true) }
            messages.emit("API key saved on this device")
        }
    }

    override fun onRemoveApiKeyClicked() {
        viewModelScope.launch {
            settingsRepository.setApiKey(null)
            state.update { it.copy(hasStoredKey = false, keyTestResult = null, keyTestMessage = null) }
        }
    }

    override fun onTestKeyClicked() {
        if (state.value.keyTestResult == KeyTestResult.Testing) return
        state.update { it.copy(keyTestResult = KeyTestResult.Testing, keyTestMessage = null) }
        viewModelScope.launch {
            val key = state.value.apiKeyInput.trim().ifEmpty { settingsRepository.apiKey().orEmpty() }
            runCatching { importService.testApiKey(key) }
                .onSuccess { state.update { it.copy(keyTestResult = KeyTestResult.Valid, keyTestMessage = "The key works.") } }
                .onFailure { error -> state.update { it.copy(keyTestResult = KeyTestResult.Invalid, keyTestMessage = error.userMessage()) } }
        }
    }

    override fun onModelChanged(model: String) {
        state.update { it.copy(model = model) }
        viewModelScope.launch { settingsRepository.set(SettingKey.AiModel, model.trim().ifEmpty { null }) }
    }

    override fun onKeepScanPhotosToggled(keep: Boolean) {
        state.update { it.copy(keepScanPhotos = keep) }
        viewModelScope.launch { settingsRepository.set(SettingKey.KeepScanPhotos, keep.toString()) }
    }

    override fun onWeekdayThemesClicked() = navigator.navigateTo(WeekdayThemesRoute)

    override fun onReviewQueueClicked() = navigator.navigateTo(IngredientsRoute(reviewOnly = true))

    // The UI opens the save dialog once the bytes are ready, then calls onExportSaved
    override fun onExportClicked() {
        if (state.value.exportProgress != null) return
        state.update { it.copy(exportProgress = 0f) }
        viewModelScope.launch {
            runCatching { backupService.export { progress -> state.update { it.copy(exportProgress = progress) } } }
                .onSuccess { bytes -> state.update { it.copy(exportedBytes = bytes) } }
                .onFailure { error ->
                    state.update { it.copy(exportProgress = null) }
                    messages.emit(error.userMessage())
                }
        }
    }

    override fun onExportSaved() = state.update { it.copy(exportProgress = null, exportedBytes = null) }

    override fun onBackupFileOpened(bytes: ByteArray) {
        viewModelScope.launch {
            runCatching { backupService.inspect(bytes) }
                .onSuccess { preview -> state.update { it.copy(pendingImport = PendingImport(bytes, preview)) } }
                .onFailure { error -> messages.emit((error as? BackupException)?.message ?: error.userMessage()) }
        }
    }

    override fun onImportConfirmed() {
        val pending = state.value.pendingImport ?: return
        state.update { it.copy(pendingImport = null, isImporting = true) }
        viewModelScope.launch {
            runCatching { backupService.restore(pending.bytes) }
                .onSuccess {
                    val preview = pending.preview
                    messages.emit(
                        "Imported ${preview.recipeCount} recipes, ${preview.ingredientCount} ingredients and " +
                            "${preview.mealPlanEntryCount} meal plan entries.",
                    )
                }
                .onFailure { error -> messages.emit(error.userMessage()) }
            state.update { it.copy(isImporting = false) }
        }
    }

    override fun onImportDismissed() = state.update { it.copy(pendingImport = null) }

    override fun onLicencesClicked() = state.update { it.copy(isShowingLicences = true) }

    override fun onLicencesDismissed() = state.update { it.copy(isShowingLicences = false) }
}
