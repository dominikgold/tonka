package com.dominikgold.tonka.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.IngredientPickerDialog
import com.dominikgold.tonka.designsystem.IngredientSuggestions
import com.dominikgold.tonka.designsystem.ReorderableColumn
import com.dominikgold.tonka.designsystem.SectionCard
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.model.IngredientLine
import com.dominikgold.tonka.model.RecipeSection
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.RecipeVariant
import com.dominikgold.tonka.model.SectionId
import com.dominikgold.tonka.model.VariantId

@Composable
fun IngredientsCard(
    snapshot: RecipeSnapshot,
    graph: IngredientGraph,
    actions: RecipeEditorActions,
) {
    var isShowingPasteDialog by remember { mutableStateOf(false) }
    var isShowingSectionDialog by remember { mutableStateOf(false) }

    SectionCard(title = "Ingredients") {
        SectionsRow(snapshot.sections, actions, onAddClicked = { isShowingSectionDialog = true })
        ReorderableColumn(
            items = snapshot.lines,
            key = { it.id.value },
            onMove = actions::onLineMoved,
        ) { line, dragHandle ->
            LineEditor(
                line = line,
                graph = graph,
                variants = snapshot.variants,
                sections = snapshot.sections,
                actions = actions,
                dragHandle = dragHandle,
            )
        }
        Row {
            TextButton(onClick = { actions.onAddLineClicked(snapshot.lines.lastOrNull()?.sectionId) }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add ingredient")
            }
            TextButton(onClick = { isShowingPasteDialog = true }) { Text("Paste lines") }
        }
    }

    if (isShowingPasteDialog) {
        TextInputDialog(
            title = "Paste ingredient lines",
            label = "One ingredient per line",
            singleLine = false,
            onConfirm = { text ->
                isShowingPasteDialog = false
                actions.onAddLinesFromText(text, snapshot.lines.lastOrNull()?.sectionId)
            },
            onDismiss = { isShowingPasteDialog = false },
        )
    }
    if (isShowingSectionDialog) {
        TextInputDialog(
            title = "New group",
            label = "For example Batter or Toppings",
            onConfirm = { name ->
                isShowingSectionDialog = false
                actions.onAddSectionClicked(name)
            },
            onDismiss = { isShowingSectionDialog = false },
        )
    }
}

@Composable
private fun SectionsRow(
    sections: List<RecipeSection>,
    actions: RecipeEditorActions,
    onAddClicked: () -> Unit,
) {
    var renaming by remember { mutableStateOf<RecipeSection?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sections.forEach { section ->
            InputChip(
                selected = false,
                onClick = { renaming = section },
                label = { Text(section.name) },
                trailingIcon = {
                    IconButton(onClick = { actions.onSectionRemoved(section.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove group ${section.name}", modifier = Modifier.size(16.dp))
                    }
                },
            )
        }
        AssistChip(
            onClick = onAddClicked,
            label = { Text("Add group") },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
    renaming?.let { section ->
        TextInputDialog(
            title = "Rename group",
            label = "Name",
            initialValue = section.name,
            onConfirm = { name ->
                renaming = null
                if (name.isNotBlank()) actions.onSectionRenamed(section.id, name.trim())
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun LineEditor(
    line: IngredientLine,
    graph: IngredientGraph,
    variants: List<RecipeVariant>,
    sections: List<RecipeSection>,
    actions: RecipeEditorActions,
    dragHandle: @Composable () -> Unit,
) {
    var isNameFocused by remember { mutableStateOf(false) }
    var isShowingAlternativePicker by remember { mutableStateOf(false) }
    val ingredient = graph[line.ingredientId]

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dragHandle()
                OutlinedTextField(
                    value = line.amountText,
                    onValueChange = { actions.onLineAmountChanged(line.id, it) },
                    label = { Text("Amount") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = line.unit,
                    onValueChange = { actions.onLineUnitChanged(line.id, it) },
                    label = { Text("Unit") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.onLineRemoved(line.id) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove ingredient line")
                }
            }
            OutlinedTextField(
                value = line.displayName,
                onValueChange = { actions.onLineNameChanged(line.id, it) },
                label = { Text("Ingredient") },
                singleLine = true,
                supportingText = {
                    when {
                        ingredient != null -> Text(
                            listOf("Linked to ${ingredient.name}", graph.categoryPathLabel(ingredient.id))
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                        )
                        line.rawText.isNotBlank() && line.rawText != line.displayName -> Text("Original: ${line.rawText}")
                    }
                },
                trailingIcon = {
                    if (ingredient != null) {
                        IconButton(onClick = { actions.onLineUnlinked(line.id) }) {
                            Icon(Icons.Rounded.LinkOff, contentDescription = "Unlink ${ingredient.name}")
                        }
                    } else if (line.displayName.isNotBlank()) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isNameFocused = it.isFocused },
            )
            if (isNameFocused && ingredient == null && line.displayName.isNotBlank()) {
                IngredientSuggestions(
                    graph = graph,
                    query = line.displayName,
                    onPicked = { actions.onLineIngredientPicked(line.id, it.id) },
                    onCreateClicked = { actions.onLineIngredientCreated(line.id, it) },
                )
            }
            OutlinedTextField(
                value = line.preparation,
                onValueChange = { actions.onLinePreparationChanged(line.id, it) },
                label = { Text("Preparation") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = line.isOptional, onClick = { actions.onLineOptionalToggled(line.id) }, label = { Text("Optional") })
                FilterChip(selected = line.isKey, onClick = { actions.onLineKeyToggled(line.id) }, label = { Text("Key ingredient") })
                if (sections.isNotEmpty()) {
                    SectionDropdown(sections, line.sectionId) { actions.onLineSectionSelected(line.id, it) }
                }
                AssistChip(onClick = { isShowingAlternativePicker = true }, label = { Text("Add alternative") })
            }
            if (line.alternatives.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("or", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
                    line.alternatives.forEach { alternative ->
                        val name = graph[alternative.ingredientId]?.name ?: "Unknown"
                        InputChip(
                            selected = false,
                            onClick = { actions.onLineAlternativeRemoved(line.id, alternative.ingredientId) },
                            label = { Text(name) },
                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove alternative $name", modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            if (variants.size > 1) {
                AppliesToChips(variants, line.variantIds) { actions.onLineVariantToggled(line.id, it) }
            }
        }
    }

    if (isShowingAlternativePicker) {
        IngredientPickerDialog(
            title = "Alternative for ${line.displayName.ifBlank { "this line" }}",
            graph = graph,
            onPicked = {
                isShowingAlternativePicker = false
                actions.onLineAlternativeAdded(line.id, it.id)
            },
            onDismiss = { isShowingAlternativePicker = false },
        )
    }
}

@Composable
fun AppliesToChips(
    variants: List<RecipeVariant>,
    selected: Set<VariantId>,
    onToggled: (VariantId) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Applies to",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        variants.forEach { variant ->
            FilterChip(
                selected = selected.isEmpty() || variant.id in selected,
                onClick = { onToggled(variant.id) },
                label = { Text(variant.name) },
            )
        }
    }
}

@Composable
fun SectionDropdown(
    sections: List<RecipeSection>,
    selected: SectionId?,
    onSelected: (SectionId?) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { isExpanded = true },
            label = { Text(sections.firstOrNull { it.id == selected }?.name ?: "No group") },
        )
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            DropdownMenuItem(text = { Text("No group") }, onClick = {
                isExpanded = false
                onSelected(null)
            })
            sections.forEach { section ->
                DropdownMenuItem(text = { Text(section.name) }, onClick = {
                    isExpanded = false
                    onSelected(section.id)
                })
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
    singleLine: Boolean = true,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 5,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
