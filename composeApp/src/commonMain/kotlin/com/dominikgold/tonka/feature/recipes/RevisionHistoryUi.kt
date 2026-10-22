package com.dominikgold.tonka.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dominikgold.tonka.designsystem.EmptyState
import com.dominikgold.tonka.designsystem.SectionCard
import com.dominikgold.tonka.designsystem.TonkaTheme
import com.dominikgold.tonka.designsystem.TonkaTopBar
import com.dominikgold.tonka.domain.ItemChanges
import com.dominikgold.tonka.model.RecipeRevision
import com.dominikgold.tonka.util.collectEvents
import com.dominikgold.tonka.util.collectLifecycleAware
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RevisionHistoryUi(recipeId: String) {
    val viewModel = koinViewModel<RevisionHistoryViewModel>(key = "revisions-$recipeId") { parametersOf(recipeId) }
    val revisions by viewModel.revisions.collectLifecycleAware()
    RevisionHistoryContent(revisions = revisions, actions = viewModel)
}

@Composable
private fun RevisionHistoryContent(
    revisions: List<RecipeRevision>?,
    actions: RevisionHistoryActions,
) {
    Scaffold(topBar = { TonkaTopBar(title = "Revision history", onBackClicked = actions::onBackClicked) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (revisions?.isEmpty() == true) {
                item { EmptyState(title = "No revisions", message = "Revisions appear every time you save the recipe.") }
            }
            items(revisions.orEmpty(), key = { it.id.value }) { revision ->
                ListItem(
                    headlineContent = { Text("Revision ${revision.number}") },
                    supportingContent = {
                        Text(listOf(revision.createdAt.formattedDateTime(), revision.summary).filter { it.isNotBlank() }.joinToString(" · "))
                    },
                    modifier = Modifier.clickable { actions.onRevisionClicked(revision.number) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun RevisionDetailUi(recipeId: String, number: Int) {
    val viewModel = koinViewModel<RevisionDetailViewModel>(key = "revision-$recipeId-$number") { parametersOf(recipeId, number) }
    val state by viewModel.state.collectLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }
    viewModel.messages.collectEvents { snackbarHostState.showSnackbar(it) }
    RevisionDetailContent(state = state, number = number, actions = viewModel, snackbarHostState = snackbarHostState)
}

@Composable
private fun RevisionDetailContent(
    state: RevisionDetailState,
    number: Int,
    actions: RevisionDetailActions,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = { TonkaTopBar(title = "Revision $number", onBackClicked = actions::onBackClicked) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val revision = state.revision ?: return@Scaffold
        val snapshot = revision.snapshot
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(snapshot.title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        listOf(revision.createdAt.formattedDateTime(), revision.summary).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.isCurrent) {
                        Button(onClick = actions::onRestoreClicked, enabled = !state.isRestoreInProgress) {
                            Text("Restore this version")
                        }
                    } else {
                        Text("This is the current version.", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            state.diff?.let { diff ->
                item {
                    SectionCard(title = "Changes from previous") {
                        if (diff.isEmpty) Text("No changes", style = MaterialTheme.typography.bodyMedium)
                        if (diff.changedFields.isNotEmpty()) {
                            Text("Changed: ${diff.changedFields.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                        }
                        ChangeList("Ingredients", diff.lines) { it.rawText.ifBlank { it.displayName } }
                        ChangeList("Steps", diff.steps) { it.text }
                        ChangeList("Tips", diff.tips) { it.text }
                    }
                }
            }
            item {
                SectionCard(title = "Ingredients") {
                    snapshot.lines.forEach { Text("• ${it.rawText.ifBlank { it.displayName }}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                SectionCard(title = "Steps") {
                    snapshot.steps.forEachIndexed { index, step ->
                        Text("${index + 1}. ${step.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
            if (snapshot.tips.isNotEmpty()) {
                item {
                    SectionCard(title = "Tips") {
                        snapshot.tips.forEach { Text("• ${it.text}", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChangeList(
    title: String,
    changes: ItemChanges<T>,
    label: (T) -> String,
) {
    if (changes.isEmpty) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        changes.added.forEach { Text("+ ${label(it)}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
        changes.removed.forEach { Text("− ${label(it)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        changes.edited.forEach { (before, after) ->
            Text("~ ${label(before)} → ${label(after)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun Long.formattedDateTime(): String {
    val dateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.date} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

@Suppress("EmptyFunctionBlock")
private object FakeRevisionHistoryActions : RevisionHistoryActions {
    override fun onBackClicked() {}
    override fun onRevisionClicked(number: Int) {}
}

@Preview
@Composable
private fun RevisionHistoryPreview() {
    TonkaTheme {
        RevisionHistoryContent(revisions = emptyList(), actions = FakeRevisionHistoryActions)
    }
}
