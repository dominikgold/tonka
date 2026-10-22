package com.dominikgold.tonka.platform

import androidx.compose.runtime.Composable

actual val isDesktop: Boolean = true

@Composable
actual fun rememberCameraLauncher(onPhotoTaken: (ByteArray) -> Unit): (() -> Unit)? = null
