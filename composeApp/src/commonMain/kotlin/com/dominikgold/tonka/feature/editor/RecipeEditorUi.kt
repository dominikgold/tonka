package com.dominikgold.tonka.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.HeaderImage
import com.dominikgold.tonka.designsystem.RecipeImage
import com.dominikgold.tonka.designsystem.ReorderableColumn
import com.dominikgold.tonka.designsystem.SectionCard
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.designsystem.WarningBanner
import com.dominikgold.tonka.domain.IngredientGraph
import com.dominikgold.tonka.importing.RecipeDraft
import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.DraftId
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.RecipeSnapshot
import com.dominikgold.tonka.model.SourceType
import com.dominikgold.tonka.model.shortName
import com.dominikgold.tonka.platform.rememberImagePicker
import com.dominikgold.tonka.util.collectEvents
import com.dominikgold.tonka.util.collectLifecycleAware
import kotlinx.datetime.Month
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RecipeEditorUi(
    draftId: String?,
    recipeId: String?,
) {
    val viewModel = koinViewModel<RecipeEditorViewModel>(key = "editor-$draftId-$recipeId") { parametersOf(draftId, recipeId) }
    val state by viewModel.state.collectLifecycleAware()
    val graph by viewModel.graph.collectLifecycleAware()
    val allTags by viewModel.allTags.collectLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }
    viewModel.messages.collectEvents { snackbarHostState.showSnackbar(it) }

    RecipeEditorContent(
        state = state,
        graph = graph,
        allTags = allTags,
        actions = viewModel,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun RecipeEditorContent(
    state: EditorState,
    graph: IngredientGraph,
    allTags: List<String>,
    actions: RecipeEditorActions,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TonkaTopBar(
                title = when {
                    state.mode == EditorMode.Matching -> "Match ingredients"
                    state.isEditingExisting -> "Edit recipe"
                    else -> "New recipe"
                },
                onBackClicked = if (state.mode == EditorMode.Matching) actions::onBackToEditingClicked else actions::onBackClicked,
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp))
                    } else {
                        TextButton(
                            onClick = if (state.mode == EditorMode.Matching) actions::onFinishSaveClicked else actions::onSaveClicked,
                            enabled = state.draft != null,
                        ) { Text("Save") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val draft = state.draft
        if (draft == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        when (state.mode) {
            EditorMode.Editing -> EditorForm(
                state = state,
                draft = draft,
                graph = graph,
                allTags = allTags,
                actions = actions,
                modifier = Modifier.padding(padding),
            )
            EditorMode.Matching -> IngredientMatchingContent(
                state = state,
                graph = graph,
                actions = actions,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.isShowingDiscardDialog) {
        val isNewRecipe = !state.isEditingExisting
        AlertDialog(
            onDismissRequest = actions::onDiscardDialogDismissed,
            title = { Text(if (isNewRecipe) "Leave this draft?" else "Discard changes?") },
            text = {
                Text(
                    if (isNewRecipe) {
                        "You can keep the draft and finish it later from the Recipes screen, or discard it."
                    } else {
                        "Your unsaved changes to this recipe will be lost."
                    },
                )
            },
            confirmButton = { TextButton(onClick = actions::onDiscardConfirmed) { Text("Discard") } },
            dismissButton = {
                Row {
                    if (isNewRecipe) TextButton(onClick = actions::onKeepDraftClicked) { Text("Keep draft") }
                    TextButton(onClick = actions::onDiscardDialogDismissed) { Text("Keep editing") }
                }
            },
        )
    }
}

@Composable
private fun EditorForm(
    state: EditorState,
    draft: RecipeDraft,
    graph: IngredientGraph,
    allTags: List<String>,
    actions: RecipeEditorActions,
    modifier: Modifier = Modifier,
) {
    val snapshot = draft.snapshot
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WarningBanner(draft.warnings)
        BasicsCard(draft, actions)
        VariantsCard(snapshot, actions)
        IngredientsCard(snapshot, graph, actions)
        StepsCard(snapshot, actions)
        TipsCard(snapshot, actions)
        TagsCard(snapshot, allTags, draft.tagSuggestions, actions)
        SeasonAndDietCard(snapshot, draft.dietHint, actions)
        if (state.isEditingExisting) {
            OutlinedTextField(
                value = state.whatChanged,
                onValueChange = actions::onWhatChangedChanged,
                label = { Text("What changed? (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = actions::onSaveClicked, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text("Save recipe")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BasicsCard(
    draft: RecipeDraft,
    actions: RecipeEditorActions,
) {
    val snapshot = draft.snapshot
    val pickHeaderImage = rememberImagePicker(actions::onHeaderImagePicked)
    SectionCard(title = "Basics") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = snapshot.title,
                onValueChange = actions::onTitleChanged,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = snapshot.description,
                onValueChange = actions::onDescriptionChanged,
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (snapshot.headerImagePath != null) {
                Box {
                    HeaderImage(snapshot.headerImagePath, snapshot.title)
                    IconButton(onClick = actions::onHeaderImageRemoved, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove header image")
                    }
                }
            }
            OutlinedButton(onClick = pickHeaderImage) {
                Icon(Icons.Rounded.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (snapshot.headerImagePath == null) "Add header image" else "Replace header image")
            }
            if (draft.scanImagePaths.isNotEmpty()) {
                Text("Use a scanned page as header", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.scanImagePaths.forEach { path ->
                        RecipeImage(
                            relativePath = path,
                            contentDescription = "Scanned page",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { actions.onScanPhotoChosenAsHeader(path) },
                        )
                    }
                }
            }
            SourceFields(snapshot, actions)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = snapshot.yieldText,
                    onValueChange = actions::onYieldChanged,
                    label = { Text("Yield") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                MinutesField("Prep (min)", snapshot.prepMinutes, actions::onPrepMinutesChanged, Modifier.weight(1f))
                MinutesField("Cook (min)", snapshot.cookMinutes, actions::onCookMinutesChanged, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SourceFields(
    snapshot: RecipeSnapshot,
    actions: RecipeEditorActions,
) {
    val source = snapshot.source
    val options = buildList {
        add(SourceType.MANUAL to "Manual")
        add(SourceType.COOKBOOK to "Cookbook")
        if (source.type == SourceType.WEB) add(SourceType.WEB to "Website")
        if (source.type == SourceType.TEXT) add(SourceType.TEXT to "Text")
        if (source.type == SourceType.SCAN) add(SourceType.SCAN to "Scan")
    }
    Text("Source", style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = source.type == type,
                onClick = { actions.onSourceTypeSelected(type) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
    if (source.type == SourceType.COOKBOOK || source.type == SourceType.SCAN) {
        OutlinedTextField(
            value = source.book.orEmpty(),
            onValueChange = actions::onSourceBookChanged,
            label = { Text("Book title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = source.author.orEmpty(),
                onValueChange = actions::onSourceAuthorChanged,
                label = { Text("Author") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = source.page.orEmpty(),
                onValueChange = actions::onSourcePageChanged,
                label = { Text("Page") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (source.type == SourceType.WEB) {
        OutlinedTextField(
            value = source.url.orEmpty(),
            onValueChange = actions::onSourceUrlChanged,
            label = { Text("Web address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = source.author.orEmpty(),
            onValueChange = actions::onSourceAuthorChanged,
            label = { Text("Author") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MinutesField(
    label: String,
    value: Int?,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = onValueChanged,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun VariantsCard(
    snapshot: RecipeSnapshot,
    actions: RecipeEditorActions,
) {
    SectionCard(title = "Variants") {
        if (snapshot.variants.size <= 1) {
            Text(
                "Add a variant when the recipe comes in versions, such as a vegan one, that share most ingredients and steps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ReorderableColumn(
                items = snapshot.variants,
                key = { it.id.value },
                onMove = actions::onVariantMoved,
            ) { variant, dragHandle ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    dragHandle()
                    RadioButton(selected = variant.isDefault, onClick = { actions.onDefaultVariantSelected(variant.id) })
                    OutlinedTextField(
                        value = variant.name,
                        onValueChange = { actions.onVariantRenamed(variant.id, it) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { actions.onVariantRemoved(variant.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove variant ${variant.name}")
                    }
                }
            }
            Text("The selected variant is the default.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = actions::onAddVariantClicked) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add variant")
        }
    }
}

@Composable
private fun TagsCard(
    snapshot: RecipeSnapshot,
    allTags: List<String>,
    suggestions: List<String>,
    actions: RecipeEditorActions,
) {
    var text by remember { mutableStateOf("") }
    SectionCard(title = "Tags") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            snapshot.tags.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { actions.onTagRemoved(tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove tag $tag", modifier = Modifier.size(16.dp)) },
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Add tag") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(
                    onClick = {
                        actions.onTagAdded(text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                ) { Icon(Icons.Rounded.Add, contentDescription = "Add tag") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val matching = allTags.filter { tag ->
            text.isNotBlank() && tag.contains(text.trim(), ignoreCase = true) && snapshot.tags.none { it.equals(tag, ignoreCase = true) }
        }.take(MAX_TAG_SUGGESTIONS)
        val offered = suggestions.filter { suggestion -> snapshot.tags.none { it.equals(suggestion, ignoreCase = true) } }
        if (matching.isNotEmpty() || offered.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            if (offered.isNotEmpty() && matching.isEmpty()) {
                Text("Suggested", style = MaterialTheme.typography.labelMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (matching.ifEmpty { offered }).forEach { tag ->
                    AssistChip(
                        onClick = {
                            actions.onTagAdded(tag)
                            text = ""
                        },
                        label = { Text(tag) },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonAndDietCard(
    snapshot: RecipeSnapshot,
    dietHint: DietClass?,
    actions: RecipeEditorActions,
) {
    val seasonTag = snapshot.seasonOverride ?: MonthSet.None
    SectionCard(title = "Season and diet") {
        Text(
            "Tag the months this dish belongs to. Without a tag the season is worked out from its ingredients.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Month.entries.forEach { month ->
                FilterChip(
                    selected = month in seasonTag,
                    onClick = { actions.onSeasonMonthToggled(month) },
                    label = { Text(month.shortName()) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Spring" to MonthSet.Spring, "Summer" to MonthSet.Summer, "Autumn" to MonthSet.Autumn, "Winter" to MonthSet.Winter)
                .forEach { (label, months) ->
                    AssistChip(onClick = { actions.onSeasonPresetClicked(months) }, label = { Text(label) })
                }
            if (!seasonTag.isEmpty) TextButton(onClick = actions::onSeasonCleared) { Text("Clear") }
        }
        Spacer(Modifier.height(12.dp))
        DietOverrideMenu(snapshot.dietOverride, dietHint, actions::onDietOverrideSelected)
    }
}

@Composable
private fun DietOverrideMenu(
    selected: DietClass?,
    hint: DietClass?,
    onSelected: (DietClass?) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Column {
        Text("Diet", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { isExpanded = true }, modifier = Modifier.widthIn(min = 200.dp)) {
                Text(selected?.label ?: "Worked out from ingredients")
            }
            DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                DropdownMenuItem(text = { Text("Worked out from ingredients") }, onClick = {
                    isExpanded = false
                    onSelected(null)
                })
                listOf(DietClass.VEGAN, DietClass.VEGETARIAN, DietClass.OMNI).forEach { diet ->
                    DropdownMenuItem(text = { Text(diet.label) }, onClick = {
                        isExpanded = false
                        onSelected(diet)
                    })
                }
            }
        }
        hint?.let {
            Text("The source says: ${it.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

val DietClass.label: String
    get() = when (this) {
        DietClass.VEGAN -> "Vegan"
        DietClass.VEGETARIAN -> "Vegetarian"
        DietClass.OMNI -> "Contains meat or fish"
        DietClass.UNKNOWN -> "Unknown"
    }

private const val MAX_TAG_SUGGESTIONS = 8

@Preview
@Composable
private fun RecipeEditorPreview() {
    TonkaTheme {
        RecipeEditorContent(
            state = EditorState(
                isLoading = false,
                draft = RecipeDraft(
                    id = DraftId("preview"),
                    snapshot = RecipeSnapshot.blank("Okonomiyaki"),
                    originSummary = "Written manually",
                    warnings = listOf("No steps were recognized."),
                ),
            ),
            graph = IngredientGraph.Empty,
            allTags = emptyList(),
            actions = FakeRecipeEditorActions,
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
