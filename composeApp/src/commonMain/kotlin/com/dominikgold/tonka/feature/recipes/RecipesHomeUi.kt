package com.dominikgold.tonka.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.window.core.layout.WindowSizeClass
import com.dominikgold.tonka.designsystem.CardShape
import com.dominikgold.tonka.designsystem.DietBadgeChip
import com.dominikgold.tonka.designsystem.EmptyState
import com.dominikgold.tonka.designsystem.RecipeImage
import com.dominikgold.tonka.designsystem.SectionHeader
import com.dominikgold.tonka.designsystem.SmallBadge
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.domain.DietFilter
import com.dominikgold.tonka.domain.IndexedRecipe
import com.dominikgold.tonka.model.DraftId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.model.displayName
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.RecipeDetailRoute
import com.dominikgold.tonka.util.collectLifecycleAware
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RecipesHomeUi() {
    val viewModel = koinViewModel<RecipesHomeViewModel>()
    val state by viewModel.state.collectLifecycleAware()
    val navigator = koinInject<Navigator>()
    val isExpanded = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    if (!isExpanded) {
        RecipesHomeContent(
            state = state,
            actions = viewModel,
            onRecipeClicked = { recipeId, variantId ->
                navigator.navigateTo(RecipeDetailRoute(recipeId.value, variantId?.value))
            },
        )
        return
    }

    val paneNavigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    ListDetailPaneScaffold(
        directive = paneNavigator.scaffoldDirective,
        value = paneNavigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                RecipesHomeContent(
                    state = state,
                    actions = viewModel,
                    onRecipeClicked = { recipeId, variantId ->
                        scope.launch {
                            paneNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, listOfNotNull(recipeId.value, variantId?.value).joinToString("|"))
                        }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val key = paneNavigator.currentDestination?.contentKey
                if (key == null) {
                    EmptyState(title = "Pick a recipe", message = "Its ingredients and steps show up here.")
                } else {
                    val (recipeId, variantId) = key.split('|').let { it[0] to it.getOrNull(1) }
                    RecipeDetailUi(
                        recipeId = recipeId,
                        initialVariantId = variantId,
                        isPane = true,
                    )
                }
            }
        },
    )
}

@Composable
private fun RecipesHomeContent(
    state: RecipesHomeState,
    actions: RecipesHomeActions,
    onRecipeClicked: (RecipeId, VariantId?) -> Unit,
) {
    var isShowingAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TonkaTopBar(title = "Recipes", onSettingsClicked = actions::onSettingsClicked) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isShowingAddSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add recipe") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "search") {
                RecipeSearchPanel(
                    query = state.query,
                    tagUsage = state.tagUsage,
                    onTextChanged = actions::onSearchTextChanged,
                    onDietSelected = actions::onDietSelected,
                    onTagsChanged = actions::onTagsChanged,
                    onClearClicked = actions::onClearFiltersClicked,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.drafts.isNotEmpty()) {
                item(key = "drafts") {
                    DraftsSection(state, actions)
                }
            }

            when {
                state.isEmpty && state.drafts.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = "Your recipe book is empty",
                        message = "Add a recipe by writing it down, pasting text, importing it from a website or " +
                            "scanning cookbook pages. Or bring in a backup from another device.",
                    ) {
                        Button(onClick = { isShowingAddSheet = true }) { Text("Add recipe") }
                        OutlinedButton(onClick = actions::onImportBackupClicked) { Text("Import backup") }
                    }
                }
                state.query.isActive -> {
                    item(key = "results-header") {
                        SectionHeader(title = "${state.results.size} results")
                    }
                    items(state.results, key = { "result-${it.recipe.id.value}" }) { hit ->
                        SearchHitRow(
                            hit = hit,
                            month = state.month,
                            onClick = { onRecipeClicked(hit.recipe.id, hit.matchingVariantId) },
                        )
                    }
                    if (state.results.isEmpty()) {
                        item(key = "no-results") {
                            EmptyState(title = "Nothing found", message = "Try a different word or clear the filters.")
                        }
                    }
                }
                else -> {
                    if (state.carousel.isNotEmpty()) {
                        item(key = "carousel") {
                            SectionHeader(title = "In season now · ${state.month.displayName()}")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.carousel, key = { it.recipe.id.value }) { item ->
                                    SeasonalRecipeCard(
                                        item = item,
                                        onClick = { onRecipeClicked(item.recipe.id, item.season.bestVariantId.takeIf { it != item.recipe.snapshot.defaultVariant.id }) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    item(key = "all-header") {
                        SectionHeader(
                            title = "All recipes",
                            trailing = { SortMenu(state.sort, actions::onSortSelected) },
                        )
                    }
                    items(state.allRecipes, key = { "all-${it.id.value}" }) { recipe ->
                        RecipeRow(recipe = recipe, onClick = { onRecipeClicked(recipe.id, null) })
                    }
                }
            }
        }
    }

    if (isShowingAddSheet) {
        AddRecipeSheet(
            onOptionClicked = { option ->
                isShowingAddSheet = false
                actions.onAddOptionClicked(option)
            },
            onDismiss = { isShowingAddSheet = false },
        )
    }
}

@Composable
private fun DraftsSection(
    state: RecipesHomeState,
    actions: RecipesHomeActions,
) {
    Column {
        SectionHeader(title = "Unfinished drafts")
        state.drafts.forEach { draft ->
            ListItem(
                headlineContent = { Text(draft.snapshot.title.ifBlank { "Untitled recipe" }) },
                supportingContent = { Text(draft.originSummary) },
                leadingContent = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { actions.onDiscardDraftClicked(draft.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Discard draft")
                    }
                },
                modifier = Modifier.clickable { actions.onResumeDraftClicked(draft.id) },
            )
        }
    }
}

@Composable
private fun SeasonalRecipeCard(
    item: CarouselItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(260.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        RecipeImage(
            relativePath = item.recipe.snapshot.headerImagePath,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CardShape),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.recipe.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.season.freshIngredients.take(3).forEach { ingredient ->
                    SmallBadge(
                        label = ingredient.name,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                DietBadgeChip(item.recipe.diet.badge)
            }
        }
    }
}

@Composable
private fun RecipeRow(
    recipe: IndexedRecipe,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(
            relativePath = recipe.snapshot.headerImagePath,
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(4f / 3f)
                .clip(CardShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            DietBadgeChip(recipe.diet.badge)
        }
    }
}

@Composable
private fun SortMenu(
    sort: RecipeSort,
    onSortSelected: (RecipeSort) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { isExpanded = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(sort.label)
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            RecipeSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        isExpanded = false
                        onSortSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun AddRecipeSheet(
    onOptionClicked: (AddRecipeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Add recipe",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        AddOption(Icons.Rounded.EditNote, "Write manually", "Start from a blank recipe") { onOptionClicked(AddRecipeOption.Manual) }
        AddOption(Icons.AutoMirrored.Rounded.Notes, "Paste or open text", "From a .txt or .md file or the clipboard") {
            onOptionClicked(AddRecipeOption.Text)
        }
        AddOption(Icons.Rounded.Language, "From website", "Import a recipe page by its address") { onOptionClicked(AddRecipeOption.Website) }
        AddOption(Icons.Rounded.CameraAlt, "Scan cookbook pages", "Photograph 1–4 pages and let AI transcribe them") {
            onOptionClicked(AddRecipeOption.Scan)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AddOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Suppress("EmptyFunctionBlock")
private object FakeRecipesHomeActions : RecipesHomeActions {
    override fun onSearchTextChanged(text: String) {}
    override fun onDietSelected(diet: DietFilter) {}
    override fun onTagsChanged(tags: Set<String>) {}
    override fun onClearFiltersClicked() {}
    override fun onSortSelected(sort: RecipeSort) {}
    override fun onAddOptionClicked(option: AddRecipeOption) {}
    override fun onImportBackupClicked() {}
    override fun onSettingsClicked() {}
    override fun onResumeDraftClicked(draftId: DraftId) {}
    override fun onDiscardDraftClicked(draftId: DraftId) {}
}

@Preview
@Composable
private fun RecipesHomeEmptyPreview() {
    TonkaTheme {
        RecipesHomeContent(
            state = RecipesHomeState(isLoading = false),
            actions = FakeRecipesHomeActions,
            onRecipeClicked = { _, _ -> },
        )
    }
}
