package com.dominikgold.tonka.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.initial
import kotlinx.datetime.Month

enum class MonthState { Fresh, Storage, Off }

// Fresh months are solid, storage months hatched, so the strip stays readable without color
@Composable
fun SeasonStrip(
    states: Map<Month, MonthState>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highlightedMonth: Month? = null,
    cellHeight: Int = 28,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Month.entries.forEach { month ->
            val state = states[month] ?: MonthState.Off
            val background = when (state) {
                MonthState.Fresh -> colors.tertiary
                MonthState.Storage -> colors.tertiaryContainer
                MonthState.Off -> colors.surfaceContainerHigh
            }
            val textColor = when (state) {
                MonthState.Fresh -> colors.onTertiary
                MonthState.Storage -> colors.onTertiaryContainer
                MonthState.Off -> colors.onSurfaceVariant
            }
            val hatchColor = colors.tertiary.copy(alpha = 0.45f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(cellHeight.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(background)
                    .then(if (state == MonthState.Storage) Modifier.hatched(hatchColor) else Modifier)
                    .then(
                        if (month == highlightedMonth) {
                            Modifier.drawBehind {
                                drawRect(color = colors.primary, alpha = 0.18f)
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = month.initial(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (month == highlightedMonth) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 1.dp),
                )
            }
        }
    }
}

fun Modifier.hatched(color: Color, spacing: Float = 9f): Modifier = drawBehind {
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = color,
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = 2.5f,
        )
        x += spacing
    }
}

fun monthStates(fresh: MonthSet, storage: MonthSet): Map<Month, MonthState> = Month.entries.associateWith { month ->
    when {
        month in fresh -> MonthState.Fresh
        month in storage -> MonthState.Storage
        else -> MonthState.Off
    }
}

fun seasonDescription(fresh: MonthSet, storage: MonthSet): String {
    if (fresh.isEmpty && storage.isEmpty) return "No seasonal months"
    return listOfNotNull(
        fresh.takeIf { !it.isEmpty }?.let { "Fresh: ${it.label()}" },
        storage.takeIf { !it.isEmpty }?.let { "From storage: ${it.label()}" },
    ).joinToString("; ")
}
