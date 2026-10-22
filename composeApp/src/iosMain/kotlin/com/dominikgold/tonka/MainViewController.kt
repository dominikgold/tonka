package com.dominikgold.tonka

import androidx.compose.ui.window.ComposeUIViewController
import com.dominikgold.tonka.platform.PlatformContext
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    startTonka(PlatformContext())
    return ComposeUIViewController { App() }
}
