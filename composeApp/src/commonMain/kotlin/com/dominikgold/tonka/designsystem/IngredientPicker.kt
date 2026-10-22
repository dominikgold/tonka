package com.dominikgold.tonka.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.domain.IngredientSearch
import com.dominikgold.tonka.domain.IngredientSearchResult
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.IngredientKind

// Results for the editor's search-as-you-type: exact, then prefix, then contains; the last row creates a new ingredient
@Composable
fun IngredientSuggestions(
    graph: IngredientGraph,
    query: String,
    onPicked: (Ingredient) -> Unit,
    modifier: Modifier = Modifier,
    onCreateClicked: ((String) -> Unit)? = null,
    limit: Int = 6,
    filter: (Ingredient) -> Boolean = { true },
) {
    val results = remember(graph, query) { IngredientSearch.search(graph, query, limit * 2) }.filter { filter(it.ingredient) }.take(limit)
    Column(modifier = modifier.fillMaxWidth()) {
        results.forEach { result -> SuggestionRow(result, onClick = { onPicked(result.ingredient) }) }
        if (onCreateClicked != null && query.isNotBlank()) {
            ListItem(
                headlineContent = { Text("Create “${query.trim()}”") },
                leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                modifier = Modifier.clickable { onCreateClicked(query.trim()) },
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    result: IngredientSearchResult,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(result.ingredient.name + if (result.ingredient.kind == IngredientKind.CATEGORY) " (category)" else "")
        },
        supportingContent = {
            val detail = listOfNotNull(
                result.categoryPath.takeIf { it.isNotEmpty() },
                result.matchedAlias?.let { "matches “$it”" },
            ).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun IngredientPickerDialog(
    title: String,
    graph: IngredientGraph,
    onPicked: (Ingredient) -> Unit,
    onDismiss: () -> Unit,
    onCreateClicked: ((String) -> Unit)? = null,
    filter: (Ingredient) -> Boolean = { true },
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Search ingredients") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val results = remember(graph, query) {
                    if (query.isBlank()) {
                        graph.ingredients.filter(filter).sortedBy { it.name.lowercase() }.take(BROWSE_LIMIT)
                            .map { IngredientSearchResult(it, null, graph.categoryPathLabel(it.id)) }
                    } else {
                        IngredientSearch.search(graph, query, SEARCH_LIMIT).filter { filter(it.ingredient) }
                    }
                }
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(results, key = { it.ingredient.id.value }) { result ->
                        SuggestionRow(result, onClick = { onPicked(result.ingredient) })
                    }
                    if (onCreateClicked != null && query.isNotBlank()) {
                        item {
                            ListItem(
                                headlineContent = { Text("Create “${query.trim()}”") },
                                leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                modifier = Modifier.clickable { onCreateClicked(query.trim()) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val BROWSE_LIMIT = 60
private const val SEARCH_LIMIT = 30
