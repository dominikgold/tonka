package com.dominikgold.tonka.feature.mealplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.data.MealPlanRepository
import com.dominikgold.tonka.data.RecipeRepository
import com.dominikgold.tonka.designsystem.SectionCard
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.domain.displayName
import com.dominikgold.tonka.model.Tag
import com.dominikgold.tonka.model.TagId
import com.dominikgold.tonka.model.TagMatch
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.util.collectLifecycleAware
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import org.koin.compose.viewmodel.koinViewModel

data class ThemeDraft(
    val weekday: DayOfWeek,
    val name: String = "",
    val tagMatch: TagMatch = TagMatch.ANY,
    val tagIds: Set<TagId> = emptySet(),
)

interface WeekdayThemesActions {
    fun onBackClicked()
    fun onNameChanged(weekday: DayOfWeek, name: String)
    fun onTagToggled(weekday: DayOfWeek, tagId: TagId)
    fun onTagMatchSelected(weekday: DayOfWeek, tagMatch: TagMatch)
    fun onSaveClicked()
}

class WeekdayThemesViewModel(
    private val mealPlanRepository: MealPlanRepository,
    recipeRepository: RecipeRepository,
    private val navigator: Navigator,
) : ViewModel(), WeekdayThemesActions {

    val drafts = MutableStateFlow(DayOfWeek.entries.map { ThemeDraft(it) })
    val tags: StateFlow<List<Tag>> = recipeRepository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val themes = mealPlanRepository.themes.first()
            drafts.value = DayOfWeek.entries.map { day ->
                themes[day]?.let { ThemeDraft(day, it.name, it.tagMatch, it.tagIds) } ?: ThemeDraft(day)
            }
        }
    }

    private fun update(weekday: DayOfWeek, transform: (ThemeDraft) -> ThemeDraft) = drafts.update { list ->
        list.map { if (it.weekday == weekday) transform(it) else it }
    }

    override fun onBackClicked() = navigator.goBack()

    override fun onNameChanged(weekday: DayOfWeek, name: String) = update(weekday) { it.copy(name = name) }

    override fun onTagToggled(weekday: DayOfWeek, tagId: TagId) = update(weekday) { draft ->
        draft.copy(tagIds = if (tagId in draft.tagIds) draft.tagIds - tagId else draft.tagIds + tagId)
    }

    override fun onTagMatchSelected(weekday: DayOfWeek, tagMatch: TagMatch) = update(weekday) { it.copy(tagMatch = tagMatch) }

    override fun onSaveClicked() {
        viewModelScope.launch {
            drafts.value.forEach { mealPlanRepository.saveTheme(it.weekday, it.name, it.tagMatch, it.tagIds) }
            navigator.goBack()
        }
    }
}

@Composable
fun WeekdayThemesUi() {
    val viewModel = koinViewModel<WeekdayThemesViewModel>()
    val drafts by viewModel.drafts.collectLifecycleAware()
    val tags by viewModel.tags.collectLifecycleAware()
    WeekdayThemesContent(drafts = drafts, tags = tags, actions = viewModel)
}

@Composable
private fun WeekdayThemesContent(
    drafts: List<ThemeDraft>,
    tags: List<Tag>,
    actions: WeekdayThemesActions,
) {
    Scaffold(
        topBar = {
            TonkaTopBar(
                title = "Weekday themes",
                onBackClicked = actions::onBackClicked,
                actions = { TextButton(onClick = actions::onSaveClicked) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Give a weekday a theme like Pasta Monday. Suggestions for that day then only show recipes with its tags.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(drafts, key = { it.weekday.name }) { draft ->
                SectionCard(title = draft.weekday.displayName()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { actions.onNameChanged(draft.weekday, it) },
                            label = { Text("Theme name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (tags.isEmpty()) {
                            Text("Tag some recipes to use them in themes.", style = MaterialTheme.typography.bodySmall)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            tags.forEach { tag ->
                                FilterChip(
                                    selected = tag.id in draft.tagIds,
                                    onClick = { actions.onTagToggled(draft.weekday, tag.id) },
                                    label = { Text(tag.name) },
                                )
                            }
                        }
                        if (draft.tagIds.size > 1) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                TagMatch.entries.forEachIndexed { index, match ->
                                    SegmentedButton(
                                        selected = draft.tagMatch == match,
                                        onClick = { actions.onTagMatchSelected(draft.weekday, match) },
                                        shape = SegmentedButtonDefaults.itemShape(index, TagMatch.entries.size),
                                        label = { Text(if (match == TagMatch.ANY) "Any of the tags" else "All of the tags") },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("EmptyFunctionBlock")
private object FakeWeekdayThemesActions : WeekdayThemesActions {
    override fun onBackClicked() {}
    override fun onNameChanged(weekday: DayOfWeek, name: String) {}
    override fun onTagToggled(weekday: DayOfWeek, tagId: TagId) {}
    override fun onTagMatchSelected(weekday: DayOfWeek, tagMatch: TagMatch) {}
    override fun onSaveClicked() {}
}

@Preview
@Composable
private fun WeekdayThemesPreview() {
    TonkaTheme {
        WeekdayThemesContent(
            drafts = DayOfWeek.entries.map { ThemeDraft(it, name = if (it == DayOfWeek.MONDAY) "Pasta Monday" else "") },
            tags = listOf(Tag(TagId("1"), "pasta"), Tag(TagId("2"), "quick")),
            actions = FakeWeekdayThemesActions,
        )
    }
}
