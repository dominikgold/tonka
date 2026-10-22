package com.dominikgold.tonka

import androidx.compose.runtime.Composable
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.navigation.AppNavigation

@Composable
fun App() {
    TonkaTheme {
        AppNavigation()
    }
}
