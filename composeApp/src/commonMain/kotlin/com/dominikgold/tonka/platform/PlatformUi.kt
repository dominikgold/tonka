package com.dominikgold.tonka.platform

import androidx.compose.runtime.Composable

expect val isDesktop: Boolean

// Null when the platform has no camera capture (desktop); photos then come from the file picker
@Composable
expect fun rememberCameraLauncher(onPhotoTaken: (ByteArray) -> Unit): (() -> Unit)?
