package com.dominikgold.tonka.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.backup.BackupService
import com.dominikgold.tonka.data.DEFAULT_AI_MODEL
import com.dominikgold.tonka.designsystem.SectionCard
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.platform.rememberFilePicker
import com.dominikgold.tonka.util.collectEvents
import com.dominikgold.tonka.util.collectLifecycleAware
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsUi(startImport: Boolean) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }
    viewModel.messages.collectEvents { snackbarHostState.showSnackbar(it) }
    val scope = rememberCoroutineScope()

    val saver = rememberFileSaverLauncher(dialogSettings = FileKitDialogSettings.createDefault()) { file ->
        val bytes = state.exportedBytes
        if (file != null && bytes != null) {
            scope.launch {
                runCatching { file.write(bytes) }
                    .onSuccess { snackbarHostState.showSnackbar("Backup saved") }
                    .onFailure { snackbarHostState.showSnackbar("The backup could not be written.") }
                viewModel.onExportSaved()
            }
        } else {
            viewModel.onExportSaved()
        }
    }
    LaunchedEffect(state.exportedBytes) {
        if (state.exportedBytes != null) {
            saver.launch(suggestedName = viewModel.defaultBackupFileName.removeSuffix(".${BackupService.FILE_EXTENSION}"), extension = BackupService.FILE_EXTENSION)
        }
    }
    val openBackup = rememberFilePicker(extensions = setOf(BackupService.FILE_EXTENSION, "zip")) { _, bytes -> viewModel.onBackupFileOpened(bytes) }
    LaunchedEffect(startImport) {
        if (startImport) openBackup()
    }

    SettingsContent(
        state = state,
        actions = viewModel,
        onImportClicked = openBackup,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsState,
    actions: SettingsActions,
    onImportClicked: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = { TonkaTopBar(title = "Settings", onBackClicked = actions::onBackClicked) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AiCard(state, actions)
            SectionCard(title = "Meal planning") {
                ListItem(
                    headlineContent = { Text("Weekday themes") },
                    supportingContent = { Text("Pasta Monday, Fish Friday, …") },
                    modifier = Modifier.clickable(onClick = actions::onWeekdayThemesClicked),
                )
            }
            SectionCard(title = "Ingredients") {
                ListItem(
                    headlineContent = { Text("Review queue") },
                    supportingContent = { Text("New ingredients that still need a category, diet or season") },
                    trailingContent = { if (state.reviewCount > 0) Badge { Text("${state.reviewCount}") } },
                    modifier = Modifier.clickable(onClick = actions::onReviewQueueClicked),
                )
            }
            BackupCard(state, actions, onImportClicked)
            SectionCard(title = "About") {
                Text("Tonka ${BackupService.APP_VERSION}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "A personal recipe book that knows what's in season. Everything stays on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = actions::onLicencesClicked) { Text("Licences") }
            }
        }
    }

    state.pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = actions::onImportDismissed,
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "This backup from ${pending.preview.manifest.exportedAt.take(10)} contains ${pending.preview.recipeCount} recipes, " +
                        "${pending.preview.ingredientCount} ingredients, ${pending.preview.mealPlanEntryCount} meal plan entries and " +
                        "${pending.preview.imageCount} images.\n\nImporting replaces everything currently in the app. A safety copy of " +
                        "the current data is kept on this device first.",
                )
            },
            confirmButton = { TextButton(onClick = actions::onImportConfirmed) { Text("Replace") } },
            dismissButton = { TextButton(onClick = actions::onImportDismissed) { Text("Cancel") } },
        )
    }
    if (state.isShowingLicences) LicencesDialog(actions::onLicencesDismissed)
}

@Composable
private fun AiCard(
    state: SettingsState,
    actions: SettingsActions,
) {
    SectionCard(title = "AI scanning") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Scanning cookbook pages and the AI import options use the Anthropic API with your own key. The key is stored " +
                    "only on this device and is never exported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = actions::onApiKeyChanged,
                label = { Text(if (state.hasStoredKey) "Replace API key" else "Anthropic API key") },
                placeholder = { Text(if (state.hasStoredKey) "A key is saved" else "sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = actions::onSaveApiKeyClicked, enabled = state.apiKeyInput.isNotBlank()) { Text("Save key") }
                OutlinedButton(
                    onClick = actions::onTestKeyClicked,
                    enabled = (state.hasStoredKey || state.apiKeyInput.isNotBlank()) && state.keyTestResult != KeyTestResult.Testing,
                ) { Text("Test key") }
                if (state.hasStoredKey) TextButton(onClick = actions::onRemoveApiKeyClicked) { Text("Remove") }
            }
            if (state.keyTestResult == KeyTestResult.Testing) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.keyTestMessage?.let {
                Text(
                    it,
                    color = if (state.keyTestResult == KeyTestResult.Valid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                value = state.model,
                onValueChange = actions::onModelChanged,
                label = { Text("Model") },
                supportingText = { Text("Default: $DEFAULT_AI_MODEL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep scanned photos", style = MaterialTheme.typography.bodyLarge)
                    Text("Store the page photos with the recipe for reference", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.keepScanPhotos, onCheckedChange = actions::onKeepScanPhotosToggled)
            }
        }
    }
}

@Composable
private fun BackupCard(
    state: SettingsState,
    actions: SettingsActions,
    onImportClicked: () -> Unit,
) {
    SectionCard(title = "Backup") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Export saves all recipes, ingredients, meal plans, settings and images into one .recipebook file. Importing a " +
                    "backup replaces the current data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.exportProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            if (state.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions::onExportClicked, enabled = state.exportProgress == null && !state.isImporting) { Text("Export") }
                OutlinedButton(onClick = onImportClicked, enabled = state.exportProgress == null && !state.isImporting) { Text("Import") }
            }
        }
    }
}

@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Licences") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LicenceEntry(
                    "Playfair Display",
                    "Copyright 2017 The Playfair Display Project Authors. Licensed under the SIL Open Font License 1.1.",
                )
                LicenceEntry("Inter", "Copyright 2016 The Inter Project Authors. Licensed under the SIL Open Font License 1.1.")
                LicenceEntry(
                    "Libraries",
                    "Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime, Compose Multiplatform, Material 3, " +
                        "AndroidX Lifecycle and Navigation, SQLDelight, Ktor, Okio, Coil, Koin, FileKit and Ksoup are licensed " +
                        "under the Apache License 2.0. java-keyring is licensed under the BSD 3-Clause License.",
                )
                LicenceEntry(
                    "Seasonal data",
                    "Months are approximate and based on Central European seasonal calendars. Edit any ingredient to adjust them.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun LicenceEntry(title: String, text: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp).width(1.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Suppress("EmptyFunctionBlock")
private object FakeSettingsActions : SettingsActions {
    override fun onBackClicked() {}
    override fun onApiKeyChanged(key: String) {}
    override fun onSaveApiKeyClicked() {}
    override fun onRemoveApiKeyClicked() {}
    override fun onTestKeyClicked() {}
    override fun onModelChanged(model: String) {}
    override fun onKeepScanPhotosToggled(keep: Boolean) {}
    override fun onWeekdayThemesClicked() {}
    override fun onReviewQueueClicked() {}
    override fun onExportClicked() {}
    override fun onExportSaved() {}
    override fun onBackupFileOpened(bytes: ByteArray) {}
    override fun onImportConfirmed() {}
    override fun onImportDismissed() {}
    override fun onLicencesClicked() {}
    override fun onLicencesDismissed() {}
}

@Preview
@Composable
private fun SettingsPreview() {
    TonkaTheme {
        SettingsContent(
            state = SettingsState(hasStoredKey = true, reviewCount = 4),
            actions = FakeSettingsActions,
            onImportClicked = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
