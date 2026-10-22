package com.dominikgold.tonka.platform

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.dialogs.FileKitType
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch

actual val isDesktop: Boolean = false

@Composable
actual fun rememberCameraLauncher(onPhotoTaken: (ByteArray) -> Unit): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val launcher = rememberCameraPickerLauncher { file ->
        if (file != null) scope.launch { onPhotoTaken(file.readBytes()) }
    }
    return { launcher.launch() }
}

actual fun fileKitType(forExtensions: Set<String>): FileKitType = FileKitType.File(extensions = forExtensions)
