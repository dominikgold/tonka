package com.dominikgold.tonka.feature.mealplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.dominikgold.tonka.designsystem.SectionHeader
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.domain.DietFilter
import com.dominikgold.tonka.domain.Suggestion
import com.dominikgold.tonka.domain.displayName
import com.dominikgold.tonka.feature.recipes.RecipeSearchPanel
import com.dominikgold.tonka.feature.recipes.SearchHitRow
import com.dominikgold.tonka.model.MealPlanEntry
import com.dominikgold.tonka.model.MealPlanEntryId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.util.collectLifecycleAware
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MealPlanUi() {
    val viewModel = koinViewModel<MealPlanViewModel>()
    val state by viewModel.state.collectLifecycleAware()
    val isExpanded = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    MealPlanContent(state = state, actions = viewModel, isExpanded = isExpanded)
}

@Composable
private fun MealPlanContent(
    state: MealPlanState,
    actions: MealPlanActions,
    isExpanded: Boolean,
) {
    Scaffold(
        topBar = {
            TonkaTopBar(
                title = "Meal Plan",
                onSettingsClicked = actions::onSettingsClicked,
                actions = {
                    IconButton(onClick = actions::onThemesClicked) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = "Weekday themes")
                    }
                },
            )
        },
    ) { padding ->
        if (isExpanded) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    WeekSelector(state.week, actions::onWeekSelected)
                    Row(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.days.forEach { day ->
                            DayCard(day, state.upcomingDays, actions, Modifier.weight(1f))
                        }
                    }
                }
                VerticalDivider()
                Column(
                    Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ShoppingListContent(state.shoppingList, isCollapsible = false)
                }
            }
        } else {
            var isShoppingListExpanded by remember { mutableStateOf(false) }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { WeekSelector(state.week, actions::onWeekSelected) }
                items(state.days, key = { it.date.toString() }) { day ->
                    DayCard(day, state.upcomingDays, actions, Modifier.padding(horizontal = 16.dp))
                }
                item {
                    ShoppingListContent(
                        shoppingList = state.shoppingList,
                        isCollapsible = true,
                        isExpanded = isShoppingListExpanded,
                        onToggle = { isShoppingListExpanded = !isShoppingListExpanded },
                    )
                }
            }
        }
    }

    state.addMeal?.let { addMeal -> AddMealSheet(state, addMeal, actions) }
}

@Composable
private fun WeekSelector(
    week: WeekChoice,
    onWeekSelected: (WeekChoice) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        WeekChoice.entries.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == week,
                onClick = { onWeekSelected(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, WeekChoice.entries.size),
                label = { Text(choice.label) },
            )
        }
    }
}

@Composable
private fun DayCard(
    day: DayUi,
    movableDays: List<LocalDate>,
    actions: MealPlanActions,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (day.isPast) 0.6f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = if (day.isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.date.dayOfWeek.displayName(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text("${day.date.day}.${day.date.month.number}.", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { actions.onAddClicked(day.date) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add meal on ${day.date.dayOfWeek.displayName()}")
                }
            }
            day.theme?.takeIf { it.name.isNotBlank() }?.let { theme ->
                AssistChip(onClick = actions::onThemesClicked, label = { Text(theme.name, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
            day.entries.forEach { entry -> EntryRow(entry, movableDays, actions) }
        }
    }
}

@Composable
private fun EntryRow(
    entry: EntryUi,
    movableDays: List<LocalDate>,
    actions: MealPlanActions,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isPickingVariant by remember { mutableStateOf(false) }
    var isPickingDay by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.recipe != null) { actions.onEntryOpenClicked(entry.entry) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                entry.title,
                style = if (entry.entry.recipeId == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.variantName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Box {
            IconButton(onClick = { isMenuExpanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${entry.title}") }
            DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                if (entry.recipe != null) {
                    DropdownMenuItem(text = { Text("Open recipe") }, onClick = {
                        isMenuExpanded = false
                        actions.onEntryOpenClicked(entry.entry)
                    })
                    if (entry.recipe.snapshot.variants.size > 1) {
                        DropdownMenuItem(text = { Text("Change variant") }, onClick = {
                            isMenuExpanded = false
                            isPickingVariant = true
                        })
                    }
                }
                DropdownMenuItem(text = { Text("Move to another day") }, onClick = {
                    isMenuExpanded = false
                    isPickingDay = true
                })
                DropdownMenuItem(text = { Text("Remove") }, onClick = {
                    isMenuExpanded = false
                    actions.onEntryRemoved(entry.entry.id)
                })
            }
        }
    }

    if (isPickingVariant && entry.recipe != null) {
        ChoiceDialog(
            title = "Variant",
            options = entry.recipe.snapshot.variants.map { it.id to it.name },
            onPicked = { variantId ->
                isPickingVariant = false
                actions.onEntryVariantChanged(entry.entry.id, variantId)
            },
            onDismiss = { isPickingVariant = false },
        )
    }
    if (isPickingDay) {
        ChoiceDialog(
            title = "Move to",
            options = movableDays.map { it to "${it.dayOfWeek.displayName()}, ${it.day}.${it.month.number}." },
            onPicked = { date ->
                isPickingDay = false
                actions.onEntryMoved(entry.entry.id, date)
            },
            onDismiss = { isPickingDay = false },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    onPicked: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { (value, label) ->
                    ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable { onPicked(value) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddMealSheet(
    state: MealPlanState,
    addMeal: AddMealState,
    actions: MealPlanActions,
) {
    var freeText by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = actions::onAddSheetDismissed) {
        Text(
            "Add to ${addMeal.date.dayOfWeek.displayName()}, ${addMeal.date.day}.${addMeal.date.month.number}.",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        PrimaryTabRow(selectedTabIndex = addMeal.tab.ordinal) {
            Tab(selected = addMeal.tab == AddMealTab.Suggestions, onClick = { actions.onAddTabSelected(AddMealTab.Suggestions) }, text = { Text("Suggestions") })
            Tab(selected = addMeal.tab == AddMealTab.Search, onClick = { actions.onAddTabSelected(AddMealTab.Search) }, text = { Text("Search") })
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            when (addMeal.tab) {
                AddMealTab.Suggestions -> {
                    val suggestions = state.suggestions
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                suggestions?.theme?.name?.takeIf { it.isNotBlank() }?.let { "Theme: $it" } ?: "Seasonal ideas",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = actions::onShuffleClicked) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Shuffle")
                            }
                        }
                    }
                    if (suggestions != null && suggestions.primary.isEmpty() && suggestions.more.isEmpty()) {
                        item { Text("Add some recipes first.", modifier = Modifier.padding(16.dp)) }
                    }
                    items(suggestions?.primary.orEmpty(), key = { "primary-${it.recipe.id.value}" }) { suggestion ->
                        SuggestionRow(suggestion, actions)
                    }
                    if (!suggestions?.more.isNullOrEmpty()) {
                        item { SectionHeader(title = "More seasonal ideas") }
                        items(suggestions?.more.orEmpty(), key = { "more-${it.recipe.id.value}" }) { suggestion ->
                            SuggestionRow(suggestion, actions)
                        }
                    }
                }
                AddMealTab.Search -> {
                    item {
                        Spacer(Modifier.height(8.dp))
                        RecipeSearchPanel(
                            query = state.query,
                            tagUsage = state.tagUsage,
                            onTextChanged = actions::onSearchTextChanged,
                            onDietSelected = actions::onDietSelected,
                            onTagsChanged = actions::onTagsChanged,
                            onClearClicked = actions::onClearFiltersClicked,
                        )
                    }
                    items(state.searchResults, key = { "hit-${it.recipe.id.value}" }) { hit ->
                        SearchHitRow(
                            hit = hit,
                            month = addMeal.date.month,
                            onClick = { actions.onRecipePicked(hit.recipe.id, hit.matchingVariantId) },
                        )
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { freeText = it },
                        placeholder = { Text("Or a note, like Leftovers or Eating out") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { actions.onFreeTextAdded(freeText) }, enabled = freeText.isNotBlank()) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    actions: MealPlanActions,
) {
    ListItem(
        headlineContent = { Text(suggestion.recipe.title) },
        supportingContent = { Text(suggestion.reason) },
        modifier = Modifier.clickable { actions.onRecipePicked(suggestion.recipe.id, suggestion.variantId) },
    )
}

@Suppress("EmptyFunctionBlock")
private object FakeMealPlanActions : MealPlanActions {
    override fun onWeekSelected(week: WeekChoice) {}
    override fun onAddClicked(date: LocalDate) {}
    override fun onAddSheetDismissed() {}
    override fun onAddTabSelected(tab: AddMealTab) {}
    override fun onShuffleClicked() {}
    override fun onRecipePicked(recipeId: RecipeId, variantId: VariantId?) {}
    override fun onFreeTextAdded(text: String) {}
    override fun onSearchTextChanged(text: String) {}
    override fun onDietSelected(diet: DietFilter) {}
    override fun onTagsChanged(tags: Set<String>) {}
    override fun onClearFiltersClicked() {}
    override fun onEntryOpenClicked(entry: MealPlanEntry) {}
    override fun onEntryVariantChanged(entryId: MealPlanEntryId, variantId: VariantId) {}
    override fun onEntryMoved(entryId: MealPlanEntryId, date: LocalDate) {}
    override fun onEntryRemoved(entryId: MealPlanEntryId) {}
    override fun onThemesClicked() {}
    override fun onSettingsClicked() {}
}

@Preview
@Composable
private fun MealPlanPreview() {
    TonkaTheme {
        MealPlanContent(
            state = MealPlanState(
                days = listOf(DayUi(date = LocalDate(2026, 10, 19), isPast = false, isToday = true, theme = null, entries = emptyList())),
            ),
            actions = FakeMealPlanActions,
            isExpanded = false,
        )
    }
}
