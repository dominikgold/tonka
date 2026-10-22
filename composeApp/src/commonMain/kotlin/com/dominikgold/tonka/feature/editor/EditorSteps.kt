package com.dominikgold.tonka.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.HeaderImage
import com.dominikgold.tonka.designsystem.ReorderableColumn
import com.dominikgold.tonka.designsystem.SectionCard
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.RecipeStep
import com.dominikgold.tonka.platform.rememberImagePicker

@Composable
fun StepsCard(
    snapshot: RecipeSnapshot,
    actions: RecipeEditorActions,
) {
    SectionCard(title = "Steps") {
        ReorderableColumn(
            items = snapshot.steps,
            key = { it.id.value },
            onMove = actions::onStepMoved,
        ) { step, dragHandle ->
            StepEditor(
                step = step,
                number = snapshot.steps.indexOf(step) + 1,
                snapshot = snapshot,
                actions = actions,
                dragHandle = dragHandle,
            )
        }
        TextButton(onClick = actions::onAddStepClicked) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add step")
        }
    }
}

@Composable
private fun StepEditor(
    step: RecipeStep,
    number: Int,
    snapshot: RecipeSnapshot,
    actions: RecipeEditorActions,
    dragHandle: @Composable () -> Unit,
) {
    val pickImage = rememberImagePicker { actions.onStepImagePicked(step.id, it) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    dragHandle()
                    Text("$number", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = step.text,
                    onValueChange = { actions.onStepTextChanged(step.id, it) },
                    minLines = 2,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                IconButton(onClick = { actions.onStepRemoved(step.id) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove step $number")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (snapshot.sections.isNotEmpty()) {
                    SectionDropdown(snapshot.sections, step.sectionId) { actions.onStepSectionSelected(step.id, it) }
                }
                OutlinedTextField(
                    value = step.timerSeconds?.let { (it / 60).toString() }.orEmpty(),
                    onValueChange = { actions.onStepTimerChanged(step.id, it) },
                    label = { Text("Timer (min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                AssistChip(
                    onClick = pickImage,
                    label = { Text(if (step.imagePath == null) "Add photo" else "Replace photo") },
                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                )
            }
            step.imagePath?.let { path ->
                Box {
                    HeaderImage(path, "Step $number")
                    IconButton(onClick = { actions.onStepImageRemoved(step.id) }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove step photo")
                    }
                }
            }
            if (snapshot.variants.size > 1) {
                AppliesToChips(snapshot.variants, step.variantIds) { actions.onStepVariantToggled(step.id, it) }
            }
        }
    }
}

@Composable
fun TipsCard(
    snapshot: RecipeSnapshot,
    actions: RecipeEditorActions,
) {
    SectionCard(title = "Tips") {
        ReorderableColumn(
            items = snapshot.tips,
            key = { it.id.value },
            onMove = actions::onTipMoved,
        ) { tip, dragHandle ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    dragHandle()
                    OutlinedTextField(
                        value = tip.text,
                        onValueChange = { actions.onTipTextChanged(tip.id, it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    IconButton(onClick = { actions.onTipRemoved(tip.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove tip")
                    }
                }
                if (snapshot.variants.size > 1) {
                    AppliesToChips(snapshot.variants, tip.variantIds) { actions.onTipVariantToggled(tip.id, it) }
                }
            }
        }
        TextButton(onClick = actions::onAddTipClicked) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add tip")
        }
    }
}
