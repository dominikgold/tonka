package com.dominikgold.tonka

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dominikgold.tonka.platform.PlatformContext

fun main() {
    startTonka(PlatformContext())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Tonka",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            App()
        }
    }
}
