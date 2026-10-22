package com.dominikgold.tonka.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

// One-time UI events (errors, confirmations) that are delivered once even across recomposition
class UiEventsFlow<T> {

    private val channel = Channel<T>(Channel.BUFFERED)

    val events: Flow<T> = channel.receiveAsFlow()

    suspend fun emit(event: T) = channel.send(event)

    fun tryEmit(event: T) = channel.trySend(event)
}

@Composable
fun <T> StateFlow<T>.collectLifecycleAware(): State<T> = collectAsStateWithLifecycle()

@Composable
fun <T> UiEventsFlow<T>.collectEvents(onEvent: suspend (T) -> Unit) {
    LaunchedEffect(this) {
        events.collect { onEvent(it) }
    }
}

fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
