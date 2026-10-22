package com.dominikgold.tonka.designsystem

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

// A column whose items can be reordered by dragging the handle each item places via [dragHandle]
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, dragHandle: @Composable () -> Unit) -> Unit,
) {
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val itemKey = key(item)
            key(itemKey) {
                val isDragging = draggingKey == itemKey
                Box(
                    modifier = Modifier
                        .onSizeChanged { heights[itemKey] = it.height }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            shadowElevation = if (isDragging) 8f else 0f
                        },
                ) {
                    itemContent(item) {
                        Icon(
                            Icons.Rounded.DragIndicator,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.pointerInput(itemKey) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingKey = itemKey
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val list = currentItems
                                        val index = list.indexOfFirst { key(it) == itemKey }
                                        if (index < 0) return@detectDragGestures
                                        val spacing = 8.dp.toPx()
                                        if (dragOffset > 0 && index < list.lastIndex) {
                                            val neighbor = (heights[key(list[index + 1])] ?: 0) + spacing
                                            if (dragOffset > neighbor / 2) {
                                                currentOnMove(index, index + 1)
                                                dragOffset -= neighbor
                                            }
                                        } else if (dragOffset < 0 && index > 0) {
                                            val neighbor = (heights[key(list[index - 1])] ?: 0) + spacing
                                            if (-dragOffset > neighbor / 2) {
                                                currentOnMove(index, index - 1)
                                                dragOffset += neighbor
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
