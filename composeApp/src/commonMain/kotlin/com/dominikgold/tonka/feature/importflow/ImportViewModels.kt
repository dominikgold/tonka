package com.dominikgold.tonka.feature.importflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.data.SettingKey
import com.dominikgold.tonka.data.SettingsRepository
import com.dominikgold.tonka.domain.Tuning
import com.dominikgold.tonka.importing.AiException
import com.dominikgold.tonka.importing.ImportService
import com.dominikgold.tonka.importing.RecipeDraft
import com.dominikgold.tonka.importing.WebImportException
import com.dominikgold.tonka.model.newId
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.RecipeEditorRoute
import com.dominikgold.tonka.navigation.SettingsRoute
import com.dominikgold.tonka.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun Navigator.openDraft(draft: RecipeDraft) = replaceCurrent(RecipeEditorRoute(draftId = draft.id.value))

data class TextImportState(
    val text: String = "",
    val fileName: String? = null,
    val isWorking: Boolean = false,
    val error: String? = null,
    val hasApiKey: Boolean = false,
)

interface TextImportActions {
    fun onBackClicked()
    fun onTextChanged(text: String)
    fun onFileOpened(name: String, bytes: ByteArray)
    fun onParseClicked()
    fun onStructureWithAiClicked()
}

class TextImportViewModel(
    private val importService: ImportService,
    private val settingsRepository: SettingsRepository,
    private val navigator: Navigator,
) : ViewModel(), TextImportActions {

    val state = MutableStateFlow(TextImportState())

    init {
        viewModelScope.launch { state.update { it.copy(hasApiKey = settingsRepository.apiKey() != null) } }
    }

    override fun onBackClicked() = navigator.goBack()

    override fun onTextChanged(text: String) = state.update { it.copy(text = text, error = null) }

    override fun onFileOpened(name: String, bytes: ByteArray) = state.update {
        it.copy(text = bytes.decodeToString(), fileName = name, error = null)
    }

    override fun onParseClicked() = run { importService.importText(state.value.text) }

    override fun onStructureWithAiClicked() = run { importService.structureTextWithAi(state.value.text) }

    private fun run(block: suspend () -> RecipeDraft) {
        if (state.value.isWorking || state.value.text.isBlank()) return
        state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                navigator.openDraft(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e.userMessage()) }
            } finally {
                state.update { it.copy(isWorking = false) }
            }
        }
    }
}

data class WebImportState(
    val url: String = "",
    val isWorking: Boolean = false,
    val error: String? = null,
    // The page loaded but has no schema.org recipe, so only Extract with AI can help
    val isRecipeMissing: Boolean = false,
    val hasApiKey: Boolean = false,
)

interface WebImportActions {
    fun onBackClicked()
    fun onUrlChanged(url: String)
    fun onImportClicked()
    fun onExtractWithAiClicked()
}

class WebImportViewModel(
    initialUrl: String?,
    private val importService: ImportService,
    private val settingsRepository: SettingsRepository,
    private val navigator: Navigator,
) : ViewModel(), WebImportActions {

    val state = MutableStateFlow(WebImportState(url = initialUrl.orEmpty()))
    private var readableText: Pair<String, String>? = null

    init {
        viewModelScope.launch { state.update { it.copy(hasApiKey = settingsRepository.apiKey() != null) } }
        if (!initialUrl.isNullOrBlank()) onImportClicked()
    }

    override fun onBackClicked() = navigator.goBack()

    override fun onUrlChanged(url: String) = state.update { it.copy(url = url, error = null, isRecipeMissing = false) }

    override fun onImportClicked() {
        val url = state.value.url.trim()
        if (url.isEmpty() || state.value.isWorking) return
        state.update { it.copy(isWorking = true, error = null, isRecipeMissing = false) }
        viewModelScope.launch {
            try {
                val result = importService.fetchWebPage(url)
                readableText = url to result.readableText
                val draft = result.draft
                if (draft != null) {
                    navigator.openDraft(draft)
                } else {
                    state.update { it.copy(isRecipeMissing = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WebImportException) {
                state.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                state.update { it.copy(error = e.userMessage()) }
            } finally {
                state.update { it.copy(isWorking = false) }
            }
        }
    }

    override fun onExtractWithAiClicked() {
        val url = state.value.url.trim()
        if (url.isEmpty() || state.value.isWorking) return
        state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                val text = readableText?.takeIf { it.first == url }?.second
                    ?: importService.fetchWebPage(url).also { readableText = url to it.readableText }.readableText
                navigator.openDraft(importService.extractWebWithAi(url, text))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e.userMessage()) }
            } finally {
                state.update { it.copy(isWorking = false) }
            }
        }
    }
}

data class ScanPage(
    val id: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is ScanPage && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

data class ScanImportState(
    val pages: List<ScanPage> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null,
    val isKeyProblem: Boolean = false,
    val hasApiKey: Boolean = true,
    val isShowingNotice: Boolean = false,
) {
    val canAddPage: Boolean get() = pages.size < Tuning.MAX_SCAN_PAGES
}

interface ScanImportActions {
    fun onBackClicked()
    fun onPhotoAdded(bytes: ByteArray)
    fun onPhotoReplaced(pageId: String, bytes: ByteArray)
    fun onPhotoRemoved(pageId: String)
    fun onPhotoMoved(from: Int, to: Int)
    fun onScanClicked()
    fun onNoticeAcknowledged()
    fun onOpenSettingsClicked()
}

class ScanImportViewModel(
    private val importService: ImportService,
    private val settingsRepository: SettingsRepository,
    private val navigator: Navigator,
) : ViewModel(), ScanImportActions {

    val state = MutableStateFlow(ScanImportState())

    init {
        viewModelScope.launch {
            val hasKey = settingsRepository.apiKey() != null
            val acknowledged = settingsRepository.get(SettingKey.ScanNoticeAcknowledged)?.toBooleanStrictOrNull() ?: false
            state.update { it.copy(hasApiKey = hasKey, isShowingNotice = hasKey && !acknowledged) }
        }
    }

    override fun onBackClicked() = navigator.goBack()

    override fun onPhotoAdded(bytes: ByteArray) = state.update { current ->
        if (!current.canAddPage) current else current.copy(pages = current.pages + ScanPage(newId(), bytes), error = null)
    }

    override fun onPhotoReplaced(pageId: String, bytes: ByteArray) = state.update { current ->
        current.copy(pages = current.pages.map { if (it.id == pageId) ScanPage(pageId, bytes) else it })
    }

    override fun onPhotoRemoved(pageId: String) = state.update { current -> current.copy(pages = current.pages.filter { it.id != pageId }) }

    override fun onPhotoMoved(from: Int, to: Int) = state.update { current ->
        val pages = current.pages
        if (from !in pages.indices || to !in pages.indices) current
        else current.copy(pages = pages.toMutableList().apply { add(to, removeAt(from)) })
    }

    override fun onScanClicked() {
        val current = state.value
        if (current.isScanning || current.pages.isEmpty()) return
        state.update { it.copy(isScanning = true, error = null, isKeyProblem = false) }
        viewModelScope.launch {
            try {
                // The photos stay in this screen's state, so a failed scan can simply be retried
                navigator.replaceCurrent(RecipeEditorRoute(draftId = importService.scanPages(current.pages.map { it.bytes }).id.value))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                state.update {
                    it.copy(error = e.message, isKeyProblem = e is AiException.InvalidKey || e is AiException.MissingKey)
                }
            } catch (e: Exception) {
                state.update { it.copy(error = e.userMessage()) }
            } finally {
                state.update { it.copy(isScanning = false) }
            }
        }
    }

    override fun onNoticeAcknowledged() {
        state.update { it.copy(isShowingNotice = false) }
        viewModelScope.launch { settingsRepository.set(SettingKey.ScanNoticeAcknowledged, true.toString()) }
    }

    override fun onOpenSettingsClicked() = navigator.navigateTo(SettingsRoute())
}
