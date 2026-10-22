package com.dominikgold.tonka.feature.seasons

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.InSeasonDot
import com.dominikgold.tonka.designsystem.IngredientPickerDialog
import com.dominikgold.tonka.designsystem.SeasonStrip
import com.dominikgold.tonka.designsystem.SectionCard
import com.dominikgold.tonka.designsystem.SmallBadge
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.designsystem.monthStates
import com.dominikgold.tonka.designsystem.seasonDescription
import com.dominikgold.tonka.feature.editor.MonthChips
import com.dominikgold.tonka.feature.editor.label
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.Seasonality
import com.dominikgold.tonka.model.SubstitutionId
import com.dominikgold.tonka.util.collectEvents
import com.dominikgold.tonka.util.collectLifecycleAware
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun IngredientsUi(reviewOnly: Boolean) {
    val viewModel = koinViewModel<IngredientsViewModel>(key = "ingredients-$reviewOnly") { parametersOf(reviewOnly) }
    val state by viewModel.state.collectLifecycleAware()
    IngredientsContent(state = state, actions = viewModel)
}

@Composable
private fun IngredientsContent(
    state: IngredientsState,
    actions: IngredientsActions,
) {
    Scaffold(
        topBar = { TonkaTopBar(title = "Ingredients", onBackClicked = actions::onBackClicked) },
        floatingActionButton = {
            FloatingActionButton(onClick = actions::onAddIngredientClicked) {
                Icon(Icons.Rounded.Add, contentDescription = "Add ingredient")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    value = state.filter,
                    onValueChange = actions::onFilterChanged,
                    placeholder = { Text("Filter by name or alias") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    FilterChip(
                        selected = state.isReviewOnly,
                        onClick = actions::onReviewOnlyToggled,
                        label = { Text("To review") },
                        trailingIcon = { if (state.reviewCount > 0) Badge { Text("${state.reviewCount}") } },
                    )
                }
            }
            items(state.items, key = { it.ingredient.id.value }) { item ->
                ListItem(
                    headlineContent = {
                        Text(item.ingredient.name + if (item.ingredient.kind == IngredientKind.CATEGORY) " (category)" else "")
                    },
                    supportingContent = { if (item.categoryPath.isNotEmpty()) Text(item.categoryPath) },
                    trailingContent = {
                        if (item.ingredient.needsReview) {
                            SmallBadge(
                                label = "Review",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    },
                    modifier = Modifier.clickable { actions.onIngredientClicked(item.ingredient.id) },
                )
            }
        }
    }
}

@Composable
fun IngredientDetailUi(ingredientId: String) {
    val viewModel = koinViewModel<IngredientDetailViewModel>(key = "ingredient-$ingredientId") { parametersOf(ingredientId) }
    val state by viewModel.state.collectLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }
    viewModel.messages.collectEvents { snackbarHostState.showSnackbar(it) }
    IngredientDetailContent(state = state, actions = viewModel, snackbarHostState = snackbarHostState)
}

@Composable
private fun IngredientDetailContent(
    state: IngredientDetailState,
    actions: IngredientDetailActions,
    snackbarHostState: SnackbarHostState,
) {
    var isPickingSubstitution by remember { mutableStateOf(false) }
    var substitutionTarget by remember { mutableStateOf<Ingredient?>(null) }
    var isPickingMergeTarget by remember { mutableStateOf(false) }
    var mergeTarget by remember { mutableStateOf<Ingredient?>(null) }
    var isShowingDeleteDialog by remember { mutableStateOf(false) }
    val ingredient = state.ingredient

    Scaffold(
        topBar = {
            TonkaTopBar(
                title = ingredient?.name.orEmpty(),
                onBackClicked = actions::onBackClicked,
                actions = {
                    if (ingredient != null) {
                        IconButton(onClick = actions::onEditClicked) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ingredient") }
                        DetailMenu(
                            canDelete = state.referenceCount == 0L,
                            onMergeClicked = { isPickingMergeTarget = true },
                            onDeleteClicked = { isShowingDeleteDialog = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (ingredient == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ingredient.name, style = MaterialTheme.typography.headlineMedium)
                    if (ingredient.needsReview) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Needs review", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            TextButton(onClick = actions::onMarkReviewedClicked) { Text("Mark as reviewed") }
                        }
                    }
                    state.categoryPaths.forEach { path ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            path.forEachIndexed { index, category ->
                                Text(
                                    category.name + if (index < path.lastIndex) " ›" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { actions.onIngredientClicked(category.id) },
                                )
                            }
                        }
                    }
                    if (state.aliases.isNotEmpty()) {
                        Text("Also known as: ${state.aliases.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Diet: ${state.dietClass.label}", style = MaterialTheme.typography.bodyMedium)
                    if (ingredient.isPantryStaple) Text("Pantry staple", style = MaterialTheme.typography.bodyMedium)
                    if (ingredient.seasonality == Seasonality.SEASONAL || ingredient.kind == IngredientKind.CATEGORY) {
                        SeasonStrip(
                            states = monthStates(state.freshMonths, state.storageMonths),
                            contentDescription = seasonDescription(state.freshMonths, state.storageMonths),
                            highlightedMonth = state.month,
                        )
                    } else {
                        Text(ingredient.seasonality.label, style = MaterialTheme.typography.bodySmall)
                    }
                    if (ingredient.notes.isNotBlank()) Text(ingredient.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SectionCard(title = "Substitutions") {
                    state.substitutions.forEach { item ->
                        ListItem(
                            headlineContent = { Text(item.target.name) },
                            supportingContent = {
                                val parts = listOfNotNull(
                                    item.substitution.note.takeIf { it.isNotBlank() },
                                    "both ways".takeIf { item.substitution.isBidirectional },
                                    "from a parent category".takeIf { item.isInherited },
                                )
                                if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                            },
                            trailingContent = {
                                if (!item.isInherited) {
                                    IconButton(onClick = { actions.onSubstitutionRemoved(item.substitution.id) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove substitution ${item.target.name}")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { actions.onIngredientClicked(item.target.id) },
                        )
                    }
                    TextButton(onClick = { isPickingSubstitution = true }) { Text("Add substitution") }
                }
            }
            if (state.children.isNotEmpty() || state.examples.isNotEmpty()) {
                item {
                    SectionCard(title = "Examples") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            (state.children + state.examples).forEach { example ->
                                AssistChip(onClick = { actions.onIngredientClicked(example.id) }, label = { Text(example.name) })
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Recipes") {
                    if (state.recipes.isEmpty()) Text("No recipes use it yet.", style = MaterialTheme.typography.bodyMedium)
                    state.recipes.forEach { (recipe, isInSeason) ->
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(recipe.title)
                                    if (isInSeason) {
                                        Spacer(Modifier.size(6.dp))
                                        InSeasonDot()
                                    }
                                }
                            },
                            modifier = Modifier.clickable { actions.onRecipeClicked(recipe.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (isPickingSubstitution && ingredient != null) {
        IngredientPickerDialog(
            title = "Swap ${ingredient.name} for…",
            graph = state.graph,
            filter = { it.id != ingredient.id },
            onPicked = {
                isPickingSubstitution = false
                substitutionTarget = it
            },
            onDismiss = { isPickingSubstitution = false },
        )
    }
    substitutionTarget?.let { target ->
        SubstitutionNoteDialog(
            target = target,
            onConfirm = { note, isBidirectional ->
                substitutionTarget = null
                actions.onSubstitutionAdded(target.id, note, isBidirectional)
            },
            onDismiss = { substitutionTarget = null },
        )
    }
    if (isPickingMergeTarget && ingredient != null) {
        IngredientPickerDialog(
            title = "Merge ${ingredient.name} into…",
            graph = state.graph,
            filter = { it.id != ingredient.id },
            onPicked = {
                isPickingMergeTarget = false
                mergeTarget = it
            },
            onDismiss = { isPickingMergeTarget = false },
        )
    }
    mergeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { mergeTarget = null },
            title = { Text("Merge into ${target.name}?") },
            text = {
                Text(
                    "Every recipe line, alternative, substitution and category link that uses ${ingredient?.name} will point to " +
                        "${target.name}. ${ingredient?.name} is kept as an alias.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mergeTarget = null
                    actions.onMergeConfirmed(target.id)
                }) { Text("Merge") }
            },
            dismissButton = { TextButton(onClick = { mergeTarget = null }) { Text("Cancel") } },
        )
    }
    if (isShowingDeleteDialog) {
        AlertDialog(
            onDismissRequest = { isShowingDeleteDialog = false },
            title = { Text("Delete ${ingredient?.name}?") },
            text = { Text("It isn't used anywhere, so nothing else changes.") },
            confirmButton = {
                TextButton(onClick = {
                    isShowingDeleteDialog = false
                    actions.onDeleteConfirmed()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { isShowingDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailMenu(
    canDelete: Boolean,
    onMergeClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            DropdownMenuItem(text = { Text("Merge into…") }, onClick = {
                isExpanded = false
                onMergeClicked()
            })
            DropdownMenuItem(
                text = { Text(if (canDelete) "Delete" else "Delete (still in use)") },
                enabled = canDelete,
                onClick = {
                    isExpanded = false
                    onDeleteClicked()
                },
            )
        }
    }
}

@Composable
private fun SubstitutionNoteDialog(
    target: Ingredient,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    var isBidirectional by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap for ${target.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ratio or context (optional)") },
                    placeholder = { Text("1 egg = 1 tbsp ground flax + 3 tbsp water") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isBidirectional, onCheckedChange = { isBidirectional = it })
                    Text("Works both ways")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(note, isBidirectional) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun IngredientEditorUi(ingredientId: String?) {
    val viewModel = koinViewModel<IngredientEditorViewModel>(key = "ingredient-editor-$ingredientId") { parametersOf(ingredientId) }
    val state by viewModel.state.collectLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }
    viewModel.messages.collectEvents { snackbarHostState.showSnackbar(it) }
    IngredientEditorContent(state = state, actions = viewModel, snackbarHostState = snackbarHostState)
}

@Composable
private fun IngredientEditorContent(
    state: IngredientEditorState,
    actions: IngredientEditorActions,
    snackbarHostState: SnackbarHostState,
) {
    val form = state.form
    var isPickingParent by remember { mutableStateOf(false) }
    fun update(transform: (IngredientForm) -> IngredientForm) = actions.onFormChanged(transform(form))

    Scaffold(
        topBar = {
            TonkaTopBar(
                title = if (state.isNew) "New ingredient" else "Edit ingredient",
                onBackClicked = actions::onBackClicked,
                actions = { TextButton(onClick = actions::onSaveClicked, enabled = !state.isSaving) { Text("Save") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { name -> update { it.copy(name = name) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledSegments(
                label = "Kind",
                options = listOf(IngredientKind.INGREDIENT to "Ingredient", IngredientKind.CATEGORY to "Category"),
                selected = form.kind,
                onSelected = { kind -> update { it.copy(kind = kind) } },
            )
            OutlinedTextField(
                value = form.aliases,
                onValueChange = { aliases -> update { it.copy(aliases = aliases) } },
                label = { Text("Aliases, comma separated") },
                supportingText = { Text("Synonyms and other languages, such as eggplant, Aubergine") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Categories", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                form.parentIds.forEach { parentId ->
                    val name = state.graph[parentId]?.name ?: "Unknown"
                    InputChip(
                        selected = false,
                        onClick = { update { it.copy(parentIds = it.parentIds - parentId) } },
                        label = { Text(name) },
                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove category $name", modifier = Modifier.size(16.dp)) },
                    )
                }
                AssistChip(onClick = { isPickingParent = true }, label = { Text("Add category") })
            }
            LabeledSegments(
                label = "Diet",
                options = listOf(
                    DietClass.VEGAN to "Vegan",
                    DietClass.VEGETARIAN to "Vegetarian",
                    DietClass.OMNI to "Meat/fish",
                    DietClass.UNKNOWN to "Unknown",
                ),
                selected = form.dietClass,
                onSelected = { diet -> update { it.copy(dietClass = diet) } },
            )
            LabeledSegments(
                label = "Seasonality",
                options = listOf(
                    Seasonality.SEASONAL to "Seasonal",
                    Seasonality.YEAR_ROUND to "Year-round",
                    Seasonality.NOT_APPLICABLE to "n/a",
                    Seasonality.UNKNOWN to "Unknown",
                ),
                selected = form.seasonality,
                onSelected = { seasonality -> update { it.copy(seasonality = seasonality) } },
            )
            if (form.seasonality == Seasonality.SEASONAL) {
                MonthChips("Fresh months", form.freshMonths) { month -> update { it.copy(freshMonths = it.freshMonths.toggle(month)) } }
                MonthChips("Storage months", form.storageMonths) { month -> update { it.copy(storageMonths = it.storageMonths.toggle(month)) } }
            }
            SwitchRow("Pantry staple", form.isPantryStaple) { checked -> update { it.copy(isPantryStaple = checked) } }
            SwitchRow("Needs review", form.needsReview) { checked -> update { it.copy(needsReview = checked) } }
            OutlinedTextField(
                value = form.notes,
                onValueChange = { notes -> update { it.copy(notes = notes) } },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = actions::onSaveClicked, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (isPickingParent) {
        IngredientPickerDialog(
            title = "Add category",
            graph = state.graph,
            filter = { it.kind == IngredientKind.CATEGORY && it.id !in form.parentIds },
            onPicked = { picked ->
                isPickingParent = false
                update { it.copy(parentIds = it.parentIds + picked.id) }
            },
            onDismiss = { isPickingParent = false },
        )
    }
}

@Composable
private fun <T> LabeledSegments(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, text) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(text, maxLines = 1) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val Seasonality.label: String
    get() = when (this) {
        Seasonality.SEASONAL -> "Seasonal"
        Seasonality.YEAR_ROUND -> "Available all year"
        Seasonality.NOT_APPLICABLE -> "Not seasonal"
        Seasonality.UNKNOWN -> "Seasonality unknown"
    }

@Suppress("EmptyFunctionBlock")
private object FakeIngredientsActions : IngredientsActions {
    override fun onBackClicked() {}
    override fun onFilterChanged(filter: String) {}
    override fun onReviewOnlyToggled() {}
    override fun onIngredientClicked(ingredientId: IngredientId) {}
    override fun onAddIngredientClicked() {}
}

@Preview
@Composable
private fun IngredientsPreview() {
    TonkaTheme { IngredientsContent(state = IngredientsState(reviewCount = 3), actions = FakeIngredientsActions) }
}

@Suppress("EmptyFunctionBlock")
private object FakeIngredientEditorActions : IngredientEditorActions {
    override fun onBackClicked() {}
    override fun onFormChanged(form: IngredientForm) {}
    override fun onSaveClicked() {}
}

@Preview
@Composable
private fun IngredientEditorPreview() {
    TonkaTheme {
        IngredientEditorContent(
            state = IngredientEditorState(form = IngredientForm(name = "Carrot", seasonality = Seasonality.SEASONAL)),
            actions = FakeIngredientEditorActions,
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Suppress("EmptyFunctionBlock")
private object FakeIngredientDetailActions : IngredientDetailActions {
    override fun onBackClicked() {}
    override fun onEditClicked() {}
    override fun onMarkReviewedClicked() {}
    override fun onIngredientClicked(ingredientId: IngredientId) {}
    override fun onRecipeClicked(recipeId: RecipeId) {}
    override fun onSubstitutionAdded(targetId: IngredientId, note: String, isBidirectional: Boolean) {}
    override fun onSubstitutionRemoved(substitutionId: SubstitutionId) {}
    override fun onMergeConfirmed(intoId: IngredientId) {}
    override fun onDeleteConfirmed() {}
}

@Preview
@Composable
private fun IngredientDetailEmptyPreview() {
    TonkaTheme {
        IngredientDetailContent(
            state = IngredientDetailState(),
            actions = FakeIngredientDetailActions,
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
