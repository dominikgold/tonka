package com.dominikgold.tonka.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface NavigationCommand {
    data class NavigateTo(val route: Any, val replaceCurrent: Boolean) : NavigationCommand
    data object GoBack : NavigationCommand
}

// ViewModels navigate through this; AppNavigation applies the commands to the NavController
class Navigator {

    private val channel = Channel<NavigationCommand>(Channel.BUFFERED)

    val commands: Flow<NavigationCommand> = channel.receiveAsFlow()

    fun navigateTo(route: Any) {
        channel.trySend(NavigationCommand.NavigateTo(route, replaceCurrent = false))
    }

    fun replaceCurrent(route: Any) {
        channel.trySend(NavigationCommand.NavigateTo(route, replaceCurrent = true))
    }

    fun goBack() {
        channel.trySend(NavigationCommand.GoBack)
    }
}

// Text shared to the app from other apps (Android share target)
object IncomingShares {

    private val pending = MutableStateFlow<String?>(null)

    val sharedText: StateFlow<String?> = pending

    fun share(text: String) {
        pending.value = text
    }

    fun consume() {
        pending.value = null
    }

    fun extractUrl(text: String): String? = Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')')
}
