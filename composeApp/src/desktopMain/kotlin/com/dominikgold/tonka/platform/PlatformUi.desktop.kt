package com.dominikgold.tonka.platform

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.dialogs.FileKitType

actual val isDesktop: Boolean = true

@Composable
actual fun rememberCameraLauncher(onPhotoTaken: (ByteArray) -> Unit): (() -> Unit)? = null

actual fun fileKitType(forExtensions: Set<String>): FileKitType = FileKitType.File(extensions = forExtensions)
