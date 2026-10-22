package com.dominikgold.tonka.platform

import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher
import io.github.vinceglb.filekit.dialogs.init
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

// Android pickers filter by MIME type, and FileKit drops extensions without one (like .recipebook), which leaves
// those files greyed out. Allow any file then - the callers validate what they read.
actual fun fileKitType(forExtensions: Set<String>): FileKitType {
    val mimeTypeMap = MimeTypeMap.getSingleton()
    if (forExtensions.any { mimeTypeMap.getMimeTypeFromExtension(it) == null }) return FileKitType.File()
    return FileKitType.File(extensions = forExtensions)
}

// FileKit needs the activity to register its pickers before they are used
fun initPlatformUi(activity: ComponentActivity) {
    FileKit.init(activity)
}
