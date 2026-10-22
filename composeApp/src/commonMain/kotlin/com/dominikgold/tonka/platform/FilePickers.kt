package com.dominikgold.tonka.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch

@Composable
fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch { onPicked(file.readBytes()) }
    }
    return { launcher.launch() }
}

@Composable
fun rememberFilePicker(
    extensions: Set<String>,
    onPicked: (name: String, bytes: ByteArray) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberFilePickerLauncher(type = fileKitType(forExtensions = extensions)) { file ->
        if (file != null) scope.launch { onPicked(file.name, file.readBytes()) }
    }
    return { launcher.launch() }
}
