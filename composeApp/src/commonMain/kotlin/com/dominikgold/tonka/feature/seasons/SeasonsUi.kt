package com.dominikgold.tonka.feature.seasons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.EmptyState
import com.dominikgold.tonka.designsystem.SectionHeader
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.designsystem.hatched
import com.dominikgold.tonka.designsystem.seasonDescription
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.displayName
import com.dominikgold.tonka.model.initial
import com.dominikgold.tonka.model.shortName
import com.dominikgold.tonka.util.collectLifecycleAware
import kotlinx.datetime.Month
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SeasonsUi() {
    val viewModel = koinViewModel<SeasonsViewModel>()
    val state by viewModel.state.collectLifecycleAware()
    SeasonsContent(state = state, actions = viewModel)
}

@Composable
private fun SeasonsContent(
    state: SeasonsState,
    actions: SeasonsActions,
) {
    Scaffold(
        topBar = {
            TonkaTopBar(
                title = "Seasons",
                onSettingsClicked = actions::onSettingsClicked,
                actions = { SeasonsOverflowMenu(state.reviewCount, actions::onManageIngredientsClicked) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SeasonsView.entries.forEachIndexed { index, view ->
                    SegmentedButton(
                        selected = state.view == view,
                        onClick = { actions.onViewSelected(view) },
                        shape = SegmentedButtonDefaults.itemShape(index, SeasonsView.entries.size),
                        label = { Text(if (view == SeasonsView.Month) "Month" else "Year") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.filter,
                onValueChange = actions::onFilterChanged,
                placeholder = { Text("Filter ingredients") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filter.isNotEmpty()) {
                        IconButton(onClick = { actions.onFilterChanged("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear filter") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            when (state.view) {
                SeasonsView.Month -> MonthView(state, actions)
                SeasonsView.Year -> YearView(state, actions)
            }
        }
    }
}

@Composable
private fun SeasonsOverflowMenu(
    reviewCount: Int,
    onManageIngredientsClicked: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }) {
            BadgedBox(badge = { if (reviewCount > 0) Badge { Text("$reviewCount") } }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
            }
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            DropdownMenuItem(
                text = { Text(if (reviewCount > 0) "Ingredients · $reviewCount to review" else "Ingredients") },
                onClick = {
                    isExpanded = false
                    onManageIngredientsClicked()
                },
            )
        }
    }
}

@Composable
private fun MonthView(
    state: SeasonsState,
    actions: SeasonsActions,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            LazyRow(
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Month.entries) { month ->
                    FilterChip(
                        selected = month == state.month,
                        onClick = { actions.onMonthSelected(month) },
                        label = { Text(month.shortName() + if (month == state.currentMonth) " •" else "") },
                    )
                }
            }
        }
        item { SectionHeader(title = "Fresh now · ${state.month.displayName()}") }
        if (state.freshGroups.isEmpty()) {
            item { Text("Nothing fresh matches.", modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        items(state.freshGroups, key = { "fresh-${it.name}" }) { group -> TileGroup(group, isStorage = false, actions = actions) }
        if (state.storageGroups.isNotEmpty()) {
            item { SectionHeader(title = "From storage") }
            items(state.storageGroups, key = { "storage-${it.name}" }) { group -> TileGroup(group, isStorage = true, actions = actions) }
        }
    }
}

@Composable
private fun TileGroup(
    group: SeasonGroup,
    isStorage: Boolean,
    actions: SeasonsActions,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(group.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.tiles.forEach { tile -> SeasonTileCard(tile, isStorage, onClick = { actions.onIngredientClicked(tile.ingredient.id) }) }
        }
    }
}

@Composable
private fun SeasonTileCard(
    tile: SeasonTile,
    isStorage: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isStorage) colors.surfaceContainerHigh else colors.tertiaryContainer,
        contentColor = if (isStorage) colors.onSurface else colors.onTertiaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(tile.ingredient.name, style = MaterialTheme.typography.titleSmall)
            tile.marker?.let { marker ->
                Text(
                    when (marker) {
                        SeasonMarker.NewThisMonth -> "New this month"
                        SeasonMarker.LastChance -> "Last chance"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (marker == SeasonMarker.LastChance) colors.primary else colors.tertiary,
                )
            }
        }
    }
}

@Composable
private fun YearView(
    state: SeasonsState,
    actions: SeasonsActions,
) {
    val colors = MaterialTheme.colorScheme
    val hatchColor = colors.tertiary.copy(alpha = 0.45f)
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Spacer(Modifier.width(NAME_COLUMN_WIDTH))
                Month.entries.forEach { month ->
                    Box(Modifier.width(CELL_WIDTH), contentAlignment = Alignment.Center) {
                        Text(month.initial(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (state.yearGroups.isEmpty()) EmptyState(title = "Nothing matches", message = "Try a different filter.")
            state.yearGroups.forEach { group ->
                Text(group.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                group.ingredients.forEach { ingredient ->
                    Row(
                        modifier = Modifier
                            .clickable { actions.onIngredientClicked(ingredient.id) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${ingredient.name}. ${seasonDescription(ingredient.freshMonths, ingredient.storageMonths)}"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            ingredient.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(NAME_COLUMN_WIDTH).padding(end = 8.dp),
                        )
                        Month.entries.forEach { month ->
                            val isFresh = month in ingredient.freshMonths
                            val isStorage = !isFresh && month in ingredient.storageMonths
                            Box(
                                Modifier
                                    .padding(1.dp)
                                    .size(width = CELL_WIDTH - 2.dp, height = 22.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            isFresh -> colors.tertiary
                                            isStorage -> colors.tertiaryContainer
                                            else -> colors.surfaceContainerHigh
                                        },
                                    )
                                    .then(if (isStorage) Modifier.hatched(hatchColor) else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val NAME_COLUMN_WIDTH = 150.dp
private val CELL_WIDTH = 28.dp

@Suppress("EmptyFunctionBlock")
private object FakeSeasonsActions : SeasonsActions {
    override fun onViewSelected(view: SeasonsView) {}
    override fun onMonthSelected(month: Month) {}
    override fun onFilterChanged(filter: String) {}
    override fun onIngredientClicked(ingredientId: IngredientId) {}
    override fun onManageIngredientsClicked() {}
    override fun onSettingsClicked() {}
}

@Preview
@Composable
private fun SeasonsPreview() {
    TonkaTheme {
        SeasonsContent(state = SeasonsState(month = Month.MAY, currentMonth = Month.MAY), actions = FakeSeasonsActions)
    }
}
