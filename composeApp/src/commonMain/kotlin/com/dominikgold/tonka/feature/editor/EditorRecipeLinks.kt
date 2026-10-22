package com.dominikgold.tonka.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.DietBadgeChip
import com.dominikgold.tonka.domain.IndexedRecipe
import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeSection
import com.dominikgold.tonka.model.RecipeVariant

data class RecipeLinkState(
    val query: String,
    val candidates: List<IndexedRecipe>,
)

@Composable
fun LinkRecipeSheet(
    state: RecipeLinkState,
    onQueryChanged: (String) -> Unit,
    onRecipePicked: (RecipeId) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            "Link recipe",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            "Use another recipe, like a base dough or sauce, as an ingredient.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            placeholder = { Text("Search recipes") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search text") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 480.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (state.candidates.isEmpty()) {
                item {
                    Text(
                        if (state.query.isBlank()) "There are no other recipes to link yet." else "No recipe matches.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(state.candidates, key = { it.id.value }) { recipe ->
                ListItem(
                    headlineContent = { Text(recipe.title) },
                    supportingContent = {
                        val tags = recipe.snapshot.tags.take(3).joinToString(" · ")
                        if (tags.isNotEmpty()) Text(tags)
                    },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                    trailingContent = { DietBadgeChip(recipe.diet.badge) },
                    modifier = Modifier.clickable { onRecipePicked(recipe.id) },
                )
            }
        }
    }
}

// A linked line keeps amount, preparation, group and variant scope; its name always follows the linked recipe
@Composable
fun LinkedRecipeLineEditor(
    line: IngredientLine,
    variants: List<RecipeVariant>,
    sections: List<RecipeSection>,
    actions: RecipeEditorActions,
    dragHandle: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                dragHandle()
                Icon(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(start = 4.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Linked recipe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { actions.onLineRemoved(line.id) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove linked recipe ${line.displayName}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = line.amountText,
                    onValueChange = { actions.onLineAmountChanged(line.id, it) },
                    label = { Text("Amount") },
                    placeholder = { Text("1") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = line.unit,
                    onValueChange = { actions.onLineUnitChanged(line.id, it) },
                    label = { Text("Unit") },
                    placeholder = { Text("batch") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = line.preparation,
                onValueChange = { actions.onLinePreparationChanged(line.id, it) },
                label = { Text("Note") },
                placeholder = { Text("blind-baked") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = line.isOptional, onClick = { actions.onLineOptionalToggled(line.id) }, label = { Text("Optional") })
                if (sections.isNotEmpty()) {
                    SectionDropdown(sections, line.sectionId) { actions.onLineSectionSelected(line.id, it) }
                }
            }
            if (variants.size > 1) {
                AppliesToChips(variants, line.variantIds) { actions.onLineVariantToggled(line.id, it) }
            }
        }
    }
}
