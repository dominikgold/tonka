package com.dominikgold.tonka.feature.mealplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.domain.ShoppingGroup
import com.dominikgold.tonka.domain.ShoppingList

// Ingredient types only: no amounts and no check-off, by design
@Composable
fun ShoppingListContent(
    shoppingList: ShoppingList,
    isCollapsible: Boolean,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true,
    onToggle: () -> Unit = {},
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isCollapsible) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Shopping list · ${shoppingList.itemCount} items", style = MaterialTheme.typography.titleLarge)
                Text("Today until Sunday next week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(shoppingList.toPlainText())) }, enabled = shoppingList.itemCount > 0) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy as text")
            }
            if (isCollapsible) {
                Icon(
                    if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse shopping list" else "Expand shopping list",
                )
            }
        }
        if (!isCollapsible || isExpanded) {
            if (shoppingList.itemCount == 0) {
                Text(
                    "Plan some meals and their ingredients show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            shoppingList.groups.forEach { group -> ShoppingGroupContent(group) }
        }
    }
}

@Composable
private fun ShoppingGroupContent(group: ShoppingGroup) {
    var isExpanded by remember(group.name) { mutableStateOf(!group.isPantry) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${group.name} (${group.items.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
    }
    if (!isExpanded) return
    group.items.forEach { item ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
            Text(item.name + if (item.isOptional) " (optional)" else "", style = MaterialTheme.typography.bodyLarge)
            Text(
                item.recipeTitles.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
