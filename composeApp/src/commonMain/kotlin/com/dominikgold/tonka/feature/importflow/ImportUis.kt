package com.dominikgold.tonka.feature.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.domain.Tuning
import com.dominikgold.tonka.platform.rememberCameraLauncher
import com.dominikgold.tonka.platform.rememberFilePicker
import com.dominikgold.tonka.platform.rememberImagePicker
import com.dominikgold.tonka.util.collectLifecycleAware
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TextImportUi() {
    val viewModel = koinViewModel<TextImportViewModel>()
    val state by viewModel.state.collectLifecycleAware()
    TextImportContent(state = state, actions = viewModel)
}

@Composable
private fun TextImportContent(
    state: TextImportState,
    actions: TextImportActions,
) {
    val openFile = rememberFilePicker(extensions = setOf("txt", "md", "markdown")) { name, bytes -> actions.onFileOpened(name, bytes) }
    Scaffold(topBar = { TonkaTopBar(title = "Paste or open text", onBackClicked = actions::onBackClicked) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Paste a recipe or open a .txt or .md file. The first line becomes the title; headings like Ingredients, " +
                    "Method or Tips (also in German) split the rest.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = openFile) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(state.fileName ?: "Open file")
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = actions::onTextChanged,
                label = { Text("Recipe text") },
                minLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (state.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions::onParseClicked, enabled = state.text.isNotBlank() && !state.isWorking) { Text("Continue") }
                if (state.hasApiKey) {
                    OutlinedButton(onClick = actions::onStructureWithAiClicked, enabled = state.text.isNotBlank() && !state.isWorking) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Structure with AI")
                    }
                }
            }
        }
    }
}

@Composable
fun WebImportUi(url: String?) {
    val viewModel = koinViewModel<WebImportViewModel>(key = "web-$url") { parametersOf(url) }
    val state by viewModel.state.collectLifecycleAware()
    WebImportContent(state = state, actions = viewModel)
}

@Composable
private fun WebImportContent(
    state: WebImportState,
    actions: WebImportActions,
) {
    Scaffold(topBar = { TonkaTopBar(title = "From website", onBackClicked = actions::onBackClicked) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = actions::onUrlChanged,
                label = { Text("Recipe page address") },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { actions.onImportClicked() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (state.isRecipeMissing) {
                Text(
                    if (state.hasApiKey) {
                        "This page has no structured recipe data. Extract with AI reads the page text instead."
                    } else {
                        "This page has no structured recipe data. With an Anthropic API key in Settings, AI can read the page text instead."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions::onImportClicked, enabled = state.url.isNotBlank() && !state.isWorking) { Text("Import") }
                if (state.hasApiKey) {
                    OutlinedButton(onClick = actions::onExtractWithAiClicked, enabled = state.url.isNotBlank() && !state.isWorking) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Extract with AI")
                    }
                }
            }
        }
    }
}

@Composable
fun ScanImportUi() {
    val viewModel = koinViewModel<ScanImportViewModel>()
    val state by viewModel.state.collectLifecycleAware()
    ScanImportContent(state = state, actions = viewModel)
}

@Composable
private fun ScanImportContent(
    state: ScanImportState,
    actions: ScanImportActions,
) {
    var retakeTarget by remember { mutableStateOf<String?>(null) }
    val camera = rememberCameraLauncher { bytes ->
        val target = retakeTarget
        if (target != null) actions.onPhotoReplaced(target, bytes) else actions.onPhotoAdded(bytes)
        retakeTarget = null
    }
    val pickImage = rememberImagePicker { bytes ->
        val target = retakeTarget
        if (target != null) actions.onPhotoReplaced(target, bytes) else actions.onPhotoAdded(bytes)
        retakeTarget = null
    }

    Scaffold(topBar = { TonkaTopBar(title = "Scan cookbook pages", onBackClicked = actions::onBackClicked) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.hasApiKey) {
                Text(
                    "Scanning uses the Anthropic API with your own key. Add it in Settings first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = actions::onOpenSettingsClicked) { Text("Open Settings") }
                return@Column
            }
            Text(
                "Add 1–${Tuning.MAX_SCAN_PAGES} photos of the recipe's pages in reading order.",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.pages.forEachIndexed { index, page ->
                ScanPageCard(
                    page = page,
                    index = index,
                    pageCount = state.pages.size,
                    actions = actions,
                    onRetakeClicked = {
                        retakeTarget = page.id
                        (camera ?: pickImage)()
                    },
                )
            }
            if (state.canAddPage) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    camera?.let { launch ->
                        OutlinedButton(onClick = {
                            retakeTarget = null
                            launch()
                        }) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Take photo")
                        }
                    }
                    OutlinedButton(onClick = {
                        retakeTarget = null
                        pickImage()
                    }) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose image")
                    }
                }
            }
            state.error?.let { error ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        Row {
                            TextButton(onClick = actions::onScanClicked) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Retry")
                            }
                            if (state.isKeyProblem) TextButton(onClick = actions::onOpenSettingsClicked) { Text("Check API key") }
                        }
                    }
                }
            }
            if (state.isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Reading the pages… this can take a minute.")
                }
            }
            Button(
                onClick = actions::onScanClicked,
                enabled = state.pages.isNotEmpty() && !state.isScanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan ${state.pages.size} page${if (state.pages.size == 1) "" else "s"}")
            }
        }
    }

    if (state.isShowingNotice) {
        AlertDialog(
            onDismissRequest = actions::onNoticeAcknowledged,
            title = { Text("Before you scan") },
            text = {
                Text(
                    "Your photos are sent to Anthropic to be read by Claude. Each scan uses your own paid API quota.",
                )
            },
            confirmButton = { TextButton(onClick = actions::onNoticeAcknowledged) { Text("Got it") } },
        )
    }
}

@Composable
private fun ScanPageCard(
    page: ScanPage,
    index: Int,
    pageCount: Int,
    actions: ScanImportActions,
    onRetakeClicked: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = page.bytes,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Text("Page ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.onPhotoMoved(index, index - 1) }, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Move page earlier")
            }
            IconButton(onClick = { actions.onPhotoMoved(index, index + 1) }, enabled = index < pageCount - 1) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Move page later")
            }
            IconButton(onClick = onRetakeClicked) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = "Retake page ${index + 1}")
            }
            IconButton(onClick = { actions.onPhotoRemoved(page.id) }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove page ${index + 1}")
            }
        }
    }
}

@Suppress("EmptyFunctionBlock")
private object FakeWebImportActions : WebImportActions {
    override fun onBackClicked() {}
    override fun onUrlChanged(url: String) {}
    override fun onImportClicked() {}
    override fun onExtractWithAiClicked() {}
}

@Preview
@Composable
private fun WebImportPreview() {
    TonkaTheme {
        WebImportContent(
            state = WebImportState(url = "https://www.example.com/recipe", isRecipeMissing = true, hasApiKey = true),
            actions = FakeWebImportActions,
        )
    }
}

@Suppress("EmptyFunctionBlock")
private object FakeScanImportActions : ScanImportActions {
    override fun onBackClicked() {}
    override fun onPhotoAdded(bytes: ByteArray) {}
    override fun onPhotoReplaced(pageId: String, bytes: ByteArray) {}
    override fun onPhotoRemoved(pageId: String) {}
    override fun onPhotoMoved(from: Int, to: Int) {}
    override fun onScanClicked() {}
    override fun onNoticeAcknowledged() {}
    override fun onOpenSettingsClicked() {}
}

@Preview
@Composable
private fun ScanImportPreview() {
    TonkaTheme {
        ScanImportContent(state = ScanImportState(error = "The API is busy or rate limited right now."), actions = FakeScanImportActions)
    }
}

@Suppress("EmptyFunctionBlock")
private object FakeTextImportActions : TextImportActions {
    override fun onBackClicked() {}
    override fun onTextChanged(text: String) {}
    override fun onFileOpened(name: String, bytes: ByteArray) {}
    override fun onParseClicked() {}
    override fun onStructureWithAiClicked() {}
}

@Preview
@Composable
private fun TextImportPreview() {
    TonkaTheme {
        TextImportContent(state = TextImportState(text = "Pancakes\n\nIngredients\n200 g flour"), actions = FakeTextImportActions)
    }
}
