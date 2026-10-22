package com.dominikgold.tonka.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.DietBadgeChip
import com.dominikgold.tonka.designsystem.EmptyState
import com.dominikgold.tonka.designsystem.HeaderImage
import com.dominikgold.tonka.designsystem.InSeasonDot
import com.dominikgold.tonka.designsystem.SeasonStrip
import com.dominikgold.tonka.designsystem.SectionCard
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.designsystem.minutesLabel
import com.dominikgold.tonka.domain.IndexedRecipe
import com.dominikgold.tonka.domain.displayName
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.LineId
import com.dominikgold.tonka.model.NoteId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.SourceType
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.util.collectEvents
import com.dominikgold.tonka.util.collectLifecycleAware
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RecipeDetailUi(
    recipeId: String,
    initialVariantId: String?,
    isPane: Boolean = false,
) {
    val viewModel = koinViewModel<RecipeDetailViewModel>(key = "detail-$recipeId-$isPane") {
        parametersOf(recipeId, initialVariantId, isPane)
    }
    val state by viewModel.state.collectLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }
    viewModel.messages.collectEvents { snackbarHostState.showSnackbar(it) }

    RecipeDetailContent(
        state = state,
        actions = viewModel,
        isPane = isPane,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun RecipeDetailContent(
    state: RecipeDetailState,
    actions: RecipeDetailActions,
    isPane: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    val recipe = state.recipe
    Scaffold(
        topBar = {
            TonkaTopBar(
                title = "",
                onBackClicked = if (isPane) null else actions::onBackClicked,
                actions = { if (recipe != null) DetailOverflowMenu(actions) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (recipe == null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (!state.isLoading) EmptyState(title = "Recipe not found", message = "It may have been deleted.")
            }
            return@Scaffold
        }
        val snapshot = recipe.snapshot
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (snapshot.headerImagePath != null) HeaderImage(snapshot.headerImagePath, snapshot.title)
                    Text(snapshot.title, style = MaterialTheme.typography.headlineLarge)
                    SourceLine(snapshot)
                    if (snapshot.description.isNotBlank()) {
                        Text(snapshot.description, style = MaterialTheme.typography.bodyLarge)
                    }
                    MetaLine(snapshot)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DietBadgeChip(recipe.diet.badge)
                    }
                    SeasonStrip(
                        states = state.seasonStates,
                        contentDescription = state.seasonDescription,
                        highlightedMonth = state.month,
                    )
                    if (snapshot.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            snapshot.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                        }
                    }
                    if (snapshot.variants.size > 1) {
                        VariantSelector(snapshot, state.selectedVariantId, actions::onVariantSelected)
                    }
                }
            }

            item(key = "ingredients") {
                SectionCard(title = "Ingredients") {
                    state.lineGroups.forEach { group ->
                        group.name?.let {
                            Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                        group.lines.forEach { lineUi -> IngredientLineRow(lineUi, actions, isNested = false) }
                    }
                    if (state.lineGroups.isEmpty()) Text("No ingredients", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state.tips.isNotEmpty()) {
                item(key = "tips") {
                    SectionCard(title = "Tips") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.tips.forEach { tip -> Text("• ${tip.text}", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            item(key = "steps") {
                SectionCard(title = "Steps") {
                    var number = 1
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.stepGroups.forEach { group ->
                            group.name?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                            group.steps.forEach { step ->
                                val stepNumber = number++
                                Row {
                                    Text(
                                        "$stepNumber",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(32.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(step.text, style = MaterialTheme.typography.bodyLarge)
                                        step.timerSeconds?.let { seconds ->
                                            minutesLabel(seconds / 60)?.let {
                                                Text("Timer: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        step.imagePath?.let {
                                            Spacer(Modifier.height(8.dp))
                                            HeaderImage(it, "Step $stepNumber")
                                        }
                                    }
                                }
                            }
                        }
                        if (state.stepGroups.isEmpty()) Text("No steps", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item(key = "notes") {
                NotesCard(state, actions)
            }
        }
    }

    state.alternatives?.let { AlternativesSheet(it, actions) }

    if (state.isShowingDeleteDialog) {
        AlertDialog(
            onDismissRequest = actions::onDeleteDialogDismissed,
            title = { Text("Delete recipe?") },
            text = { Text("This removes the recipe with its revisions and notes. Meal plan entries keep its name.") },
            confirmButton = { TextButton(onClick = actions::onDeleteConfirmed) { Text("Delete") } },
            dismissButton = { TextButton(onClick = actions::onDeleteDialogDismissed) { Text("Cancel") } },
        )
    }

    state.mealPlanDays?.let { days -> MealPlanDayDialog(days, state.today, actions) }
}

@Composable
private fun SourceLine(snapshot: RecipeSnapshot) {
    val uriHandler = LocalUriHandler.current
    val source = snapshot.source
    val url = source.url
    when {
        url != null -> Text(
            text = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/'),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
        )
        source.book != null -> Text(
            text = listOfNotNull(source.book, source.author).joinToString(" · ") + (source.page?.let { ", p. $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        source.author != null -> Text("By ${source.author}", style = MaterialTheme.typography.bodyMedium)
        source.type == SourceType.SCAN -> Text("Scanned from a cookbook", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetaLine(snapshot: RecipeSnapshot) {
    val parts = listOfNotNull(
        snapshot.yieldText.takeIf { it.isNotBlank() },
        minutesLabel(snapshot.prepMinutes)?.let { "Prep $it" },
        minutesLabel(snapshot.cookMinutes)?.let { "Cook $it" },
    )
    if (parts.isEmpty()) return
    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun VariantSelector(
    snapshot: RecipeSnapshot,
    selected: VariantId?,
    onSelected: (VariantId) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        snapshot.variants.forEachIndexed { index, variant ->
            SegmentedButton(
                selected = variant.id == selected,
                onClick = { onSelected(variant.id) },
                shape = SegmentedButtonDefaults.itemShape(index, snapshot.variants.size),
                label = { Text(variant.name, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun IngredientLineRow(
    lineUi: LineUi,
    actions: RecipeDetailActions,
    isNested: Boolean,
) {
    val linkedRecipe = lineUi.linkedRecipe
    if (linkedRecipe != null) {
        LinkedRecipeLineRow(lineUi, linkedRecipe, actions)
        return
    }
    val line = lineUi.line
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Alternatives belong to the recipe that owns the line, so nested lines from a linked recipe don't open them
            .then(if (isNested) Modifier else Modifier.clickable { actions.onLineClicked(line.id) })
            .padding(vertical = if (isNested) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOf(line.amountText, line.unit).filter { it.isNotBlank() }.joinToString(" "),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.displayName.ifBlank { line.rawText }, style = MaterialTheme.typography.bodyLarge)
                if (line.isKey) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Star, contentDescription = "Key ingredient", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                if (lineUi.isInSeason) {
                    Spacer(Modifier.width(6.dp))
                    InSeasonDot()
                }
            }
            val details = listOfNotNull(
                line.preparation.takeIf { it.isNotBlank() },
                "optional".takeIf { line.isOptional },
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Tapping the line opens the linked recipe; the chevron unfolds its ingredients in place
@Composable
private fun LinkedRecipeLineRow(
    lineUi: LineUi,
    linkedRecipe: IndexedRecipe,
    actions: RecipeDetailActions,
) {
    val line = lineUi.line
    var isExpanded by rememberSaveable(line.id.value) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.onLinkedRecipeClicked(linkedRecipe.id) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                listOf(line.amountText, line.unit).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(80.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        linkedRecipe.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                }
                val details = listOfNotNull(
                    line.preparation.takeIf { it.isNotBlank() },
                    "optional".takeIf { line.isOptional },
                )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (lineUi.nestedLines.isNotEmpty()) {
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Hide ingredients of ${linkedRecipe.title}" else "Show ingredients of ${linkedRecipe.title}",
                    )
                }
            }
        }
        if (isExpanded) {
            val guideColor = MaterialTheme.colorScheme.outlineVariant
            Column(
                Modifier
                    .padding(start = 8.dp, bottom = 4.dp)
                    .drawBehind {
                        drawLine(guideColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx())
                    }
                    .padding(start = 12.dp),
            ) {
                lineUi.nestedLines.forEach { nested -> IngredientLineRow(nested, actions, isNested = true) }
            }
        }
    }
}

@Composable
private fun NotesCard(
    state: RecipeDetailState,
    actions: RecipeDetailActions,
) {
    SectionCard(title = "Cooking notes") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.noteDraft,
                onValueChange = actions::onNoteTextChanged,
                placeholder = { Text("Needed more salt…") },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions::onAddNoteClicked, enabled = state.noteDraft.isNotBlank()) { Text("Add") }
        }
        state.notes.forEach { note ->
            val date = Instant.fromEpochMilliseconds(note.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
            ListItem(
                headlineContent = { Text(note.text) },
                supportingContent = {
                    val variantName = note.variantId?.let { id -> state.recipe?.snapshot?.variants?.firstOrNull { it.id == id }?.name }
                    Text(listOfNotNull(date.toString(), variantName?.takeIf { (state.recipe?.snapshot?.variants?.size ?: 0) > 1 }).joinToString(" · "))
                },
                trailingContent = {
                    IconButton(onClick = { actions.onDeleteNoteClicked(note.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete note")
                    }
                },
            )
        }
    }
}

@Composable
private fun DetailOverflowMenu(actions: RecipeDetailActions) {
    var isExpanded by remember { mutableStateOf(false) }
    IconButton(onClick = actions::onEditClicked) {
        Icon(Icons.Rounded.Edit, contentDescription = "Edit recipe")
    }
    Box {
        IconButton(onClick = { isExpanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            listOf(
                "Add to meal plan" to actions::onAddToMealPlanClicked,
                "Duplicate" to actions::onDuplicateClicked,
                "Revision history" to actions::onRevisionHistoryClicked,
                "Delete" to actions::onDeleteClicked,
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        isExpanded = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun AlternativesSheet(
    sheet: AlternativesSheetState,
    actions: RecipeDetailActions,
) {
    ModalBottomSheet(onDismissRequest = actions::onAlternativesDismissed) {
        Text(
            "Instead of ${sheet.title}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (sheet.items.isEmpty()) {
            Text(
                "No alternatives known yet. Add substitutions on the ingredient's page.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(sheet.items, key = { it.ingredient.id.value }) { item ->
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.ingredient.name)
                            if (item.isInSeason) {
                                Spacer(Modifier.width(6.dp))
                                InSeasonDot()
                            }
                        }
                    },
                    supportingContent = {
                        Text(listOf(item.source.label, item.note).filter { it.isNotBlank() }.joinToString(" · "))
                    },
                    modifier = Modifier.clickable { actions.onAlternativeClicked(item.ingredient.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MealPlanDayDialog(
    days: List<LocalDate>,
    today: LocalDate?,
    actions: RecipeDetailActions,
) {
    AlertDialog(
        onDismissRequest = actions::onMealPlanDialogDismissed,
        title = { Text("Add to meal plan") },
        text = {
            LazyColumn {
                items(days) { date ->
                    val isPast = today != null && date < today
                    ListItem(
                        headlineContent = { Text("${date.dayOfWeek.displayName()}, ${date.day}.${date.month.number}.") },
                        supportingContent = { Text(if (days.indexOf(date) < 7) "This week" else "Next week") },
                        modifier = Modifier.clickable(enabled = !isPast) { actions.onMealPlanDayPicked(date) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = actions::onMealPlanDialogDismissed) { Text("Cancel") } },
    )
}

@Suppress("EmptyFunctionBlock")
private object FakeRecipeDetailActions : RecipeDetailActions {
    override fun onBackClicked() {}
    override fun onVariantSelected(variantId: VariantId) {}
    override fun onLineClicked(lineId: LineId) {}
    override fun onLinkedRecipeClicked(recipeId: RecipeId) {}
    override fun onAlternativesDismissed() {}
    override fun onAlternativeClicked(ingredientId: IngredientId) {}
    override fun onEditClicked() {}
    override fun onAddToMealPlanClicked() {}
    override fun onMealPlanDayPicked(date: LocalDate) {}
    override fun onMealPlanDialogDismissed() {}
    override fun onDuplicateClicked() {}
    override fun onRevisionHistoryClicked() {}
    override fun onDeleteClicked() {}
    override fun onDeleteConfirmed() {}
    override fun onDeleteDialogDismissed() {}
    override fun onNoteTextChanged(text: String) {}
    override fun onAddNoteClicked() {}
    override fun onDeleteNoteClicked(noteId: NoteId) {}
}

@Preview
@Composable
private fun RecipeDetailMissingPreview() {
    TonkaTheme {
        RecipeDetailContent(
            state = RecipeDetailState(isLoading = false),
            actions = FakeRecipeDetailActions,
            isPane = false,
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
