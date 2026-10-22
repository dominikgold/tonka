package com.dominikgold.tonka.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.IngredientPickerDialog
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.LineId
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.shortName
import kotlinx.datetime.Month

@Composable
fun IngredientMatchingContent(
    state: EditorState,
    graph: IngredientGraph,
    actions: RecipeEditorActions,
    modifier: Modifier = Modifier,
) {
    var pickingFor by remember { mutableStateOf<LineId?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "These lines aren't linked to an ingredient yet. Check the suggestions, pick another one or add a new ingredient. " +
                    "New ingredients you don't fill in go to the review queue.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(state.matchRows, key = { it.lineId.value }) { row ->
            MatchRowCard(
                row = row,
                graph = graph,
                actions = actions,
                onPickClicked = { pickingFor = row.lineId },
            )
        }
        if (state.isEditingExisting) {
            item {
                OutlinedTextField(
                    value = state.whatChanged,
                    onValueChange = actions::onWhatChangedChanged,
                    label = { Text("What changed? (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Button(onClick = actions::onFinishSaveClicked, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text("Save recipe")
            }
        }
    }

    pickingFor?.let { lineId ->
        IngredientPickerDialog(
            title = "Link to ingredient",
            graph = graph,
            onPicked = {
                pickingFor = null
                actions.onMatchIngredientPicked(lineId, it.id)
            },
            onDismiss = { pickingFor = null },
        )
    }
}

@Composable
private fun MatchRowCard(
    row: MatchRow,
    graph: IngredientGraph,
    actions: RecipeEditorActions,
    onPickClicked: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.lineText, style = MaterialTheme.typography.titleMedium)
            when (val status = row.status) {
                is MatchStatus.Linked -> {
                    Text("Linked to ${status.ingredient.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    Row {
                        TextButton(onClick = onPickClicked) { Text("Change") }
                        TextButton(onClick = { actions.onMatchCreateNewClicked(row.lineId) }) { Text("New ingredient") }
                    }
                }
                is MatchStatus.Confirm -> {
                    Text("Did you mean ${status.candidate.name}?", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = true,
                            onClick = { actions.onMatchIngredientPicked(row.lineId, status.candidate.id) },
                            label = { Text(status.candidate.name) },
                        )
                        status.others.forEach { other ->
                            AssistChip(onClick = { actions.onMatchIngredientPicked(row.lineId, other.id) }, label = { Text(other.name) })
                        }
                    }
                    Row {
                        TextButton(onClick = onPickClicked) { Text("Pick another") }
                        TextButton(onClick = { actions.onMatchCreateNewClicked(row.lineId) }) { Text("New ingredient") }
                    }
                }
                is MatchStatus.New -> NewIngredientFormContent(
                    form = status.form,
                    graph = graph,
                    onFormChanged = { actions.onNewIngredientFormChanged(row.lineId, it) },
                    onPickExistingClicked = onPickClicked,
                )
            }
        }
    }
}

@Composable
private fun NewIngredientFormContent(
    form: NewIngredientForm,
    graph: IngredientGraph,
    onFormChanged: (NewIngredientForm) -> Unit,
    onPickExistingClicked: () -> Unit,
) {
    var isPickingCategory by remember { mutableStateOf(false) }
    Text(
        if (form.isFilledIn) "New ingredient" else "New ingredient · fill in now or leave it for the review queue",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
    OutlinedTextField(
        value = form.name,
        onValueChange = { onFormChanged(form.copy(name = it)) },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row {
        AssistChip(
            onClick = { isPickingCategory = true },
            label = { Text("Category: " + (graph[form.parentId]?.name ?: "none")) },
        )
        TextButton(onClick = onPickExistingClicked) { Text("Link existing instead") }
    }
    val diets = listOf(DietClass.VEGAN, DietClass.VEGETARIAN, DietClass.OMNI, DietClass.UNKNOWN)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        diets.forEachIndexed { index, diet ->
            SegmentedButton(
                selected = form.dietClass == diet,
                onClick = { onFormChanged(form.copy(dietClass = diet)) },
                shape = SegmentedButtonDefaults.itemShape(index, diets.size),
                label = { Text(if (diet == DietClass.OMNI) "Meat/fish" else diet.label, maxLines = 1) },
            )
        }
    }
    MonthChips("Fresh months", form.freshMonths) { onFormChanged(form.copy(freshMonths = form.freshMonths.toggle(it))) }
    MonthChips("Storage months", form.storageMonths) { onFormChanged(form.copy(storageMonths = form.storageMonths.toggle(it))) }

    if (isPickingCategory) {
        IngredientPickerDialog(
            title = "Category",
            graph = graph,
            filter = { it.kind == IngredientKind.CATEGORY },
            onPicked = {
                isPickingCategory = false
                onFormChanged(form.copy(parentId = it.id))
            },
            onDismiss = { isPickingCategory = false },
        )
    }
}

@Composable
fun MonthChips(
    label: String,
    months: MonthSet,
    onToggled: (Month) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Month.entries.forEach { month ->
                FilterChip(
                    selected = month in months,
                    onClick = { onToggled(month) },
                    label = { Text(month.shortName()) },
                )
            }
        }
    }
}
