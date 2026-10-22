package com.dominikgold.tonka.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.DietBadgeChip
import com.dominikgold.tonka.designsystem.InSeasonBadge
import com.dominikgold.tonka.designsystem.RecipeImage
import com.dominikgold.tonka.domain.DietFilter
import com.dominikgold.tonka.domain.RecipeIndex
import com.dominikgold.tonka.domain.RecipeQuery
import com.dominikgold.tonka.domain.SearchHit
import com.dominikgold.tonka.domain.Tuning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Month

// Shared by the home screen and the meal plan picker so both search and filter identically
class RecipeSearchController(
    scope: CoroutineScope,
    index: StateFlow<RecipeIndex>,
) {

    val query = MutableStateFlow(RecipeQuery())

    val results: StateFlow<List<SearchHit>> = combine(
        query.debounce { if (it.text.isEmpty()) 0L else Tuning.SEARCH_DEBOUNCE_MILLIS },
        index,
    ) { query, index -> index.search(query) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun onTextChanged(text: String) = query.update { it.copy(text = text) }

    fun onDietSelected(diet: DietFilter) = query.update { it.copy(diet = diet) }

    fun onTagsChanged(tags: Set<String>) = query.update { it.copy(tags = tags) }

    fun onClearClicked() {
        query.value = RecipeQuery()
    }
}

@Composable
fun RecipeSearchPanel(
    query: RecipeQuery,
    tagUsage: List<Pair<String, Int>>,
    onTextChanged: (String) -> Unit,
    onDietSelected: (DietFilter) -> Unit,
    onTagsChanged: (Set<String>) -> Unit,
    onClearClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isShowingTagSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query.text,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search recipes or ingredients") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.text.isNotEmpty()) {
                    IconButton(onClick = { onTextChanged("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search text")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(DietFilter.entries) { diet ->
                FilterChip(
                    selected = query.diet == diet,
                    onClick = { onDietSelected(diet) },
                    label = { Text(diet.label) },
                )
            }
            item {
                FilterChip(
                    selected = query.tags.isNotEmpty(),
                    onClick = { isShowingTagSheet = true },
                    label = { Text(if (query.tags.isEmpty()) "Tags" else "Tags · ${query.tags.size}") },
                    leadingIcon = { Icon(Icons.Rounded.Sell, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            items(query.tags.sorted()) { tag ->
                InputChip(
                    selected = true,
                    onClick = { onTagsChanged(query.tags - tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove tag $tag", modifier = Modifier.size(16.dp)) },
                )
            }
            if (query.isActive) {
                item {
                    TextButton(onClick = onClearClicked) { Text("Clear") }
                }
            }
        }
    }

    if (isShowingTagSheet) {
        TagFilterSheet(
            tagUsage = tagUsage,
            selected = query.tags,
            onTagsChanged = onTagsChanged,
            onDismiss = { isShowingTagSheet = false },
        )
    }
}

@Composable
private fun TagFilterSheet(
    tagUsage: List<Pair<String, Int>>,
    selected: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Filter by tags",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (tagUsage.isEmpty()) {
            Text(
                "No tags yet. Add tags to recipes in the editor.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(tagUsage, key = { it.first }) { (tag, count) ->
                val isSelected = tag in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTagsChanged(if (isSelected) selected - tag else selected + tag) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text(tag, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SearchHitRow(
    hit: SearchHit,
    month: Month,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val recipe = hit.recipe
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(
            relativePath = recipe.snapshot.headerImagePath,
            modifier = Modifier
                .size(width = 72.dp, height = 56.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DietBadgeChip(recipe.diet.badge)
                if (recipe.season(month).isInSeason) InSeasonBadge()
                hit.matchingVariantId?.let { variantId ->
                    Text(
                        recipe.snapshot.variant(variantId).name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

val DietFilter.label: String
    get() = when (this) {
        DietFilter.All -> "All"
        DietFilter.Vegetarian -> "Vegetarian"
        DietFilter.Vegan -> "Vegan"
    }
