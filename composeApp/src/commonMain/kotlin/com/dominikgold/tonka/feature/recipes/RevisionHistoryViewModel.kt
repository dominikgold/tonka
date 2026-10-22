package com.dominikgold.tonka.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikgold.tonka.data.RecipeRepository
import com.dominikgold.tonka.domain.SnapshotDiff
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeRevision
import com.dominikgold.tonka.navigation.Navigator
import com.dominikgold.tonka.navigation.RevisionDetailRoute
import com.dominikgold.tonka.util.UiEventsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface RevisionHistoryActions {
    fun onBackClicked()
    fun onRevisionClicked(number: Int)
}

class RevisionHistoryViewModel(
    private val recipeId: RecipeId,
    recipeRepository: RecipeRepository,
    private val navigator: Navigator,
) : ViewModel(), RevisionHistoryActions {

    val revisions: StateFlow<List<RecipeRevision>?> = recipeRepository.observeRevisions(recipeId)
        .map<List<RecipeRevision>, List<RecipeRevision>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override fun onBackClicked() = navigator.goBack()

    override fun onRevisionClicked(number: Int) = navigator.navigateTo(RevisionDetailRoute(recipeId.value, number))
}

data class RevisionDetailState(
    val revision: RecipeRevision? = null,
    val diff: SnapshotDiff? = null,
    val isCurrent: Boolean = false,
    val isRestoreInProgress: Boolean = false,
)

interface RevisionDetailActions {
    fun onBackClicked()
    fun onRestoreClicked()
}

class RevisionDetailViewModel(
    private val recipeId: RecipeId,
    private val number: Int,
    private val recipeRepository: RecipeRepository,
    private val navigator: Navigator,
) : ViewModel(), RevisionDetailActions {

    val messages = UiEventsFlow<String>()
    val state = MutableStateFlow(RevisionDetailState())

    init {
        viewModelScope.launch {
            val revision = recipeRepository.revision(recipeId, number)
            val previous = if (number > 1) recipeRepository.revision(recipeId, number - 1) else null
            val current = recipeRepository.record(recipeId)?.currentRevision
            state.value = RevisionDetailState(
                revision = revision,
                diff = revision?.let { SnapshotDiff.between(previous?.snapshot, it.snapshot) },
                isCurrent = current == number,
            )
        }
    }

    override fun onBackClicked() = navigator.goBack()

    override fun onRestoreClicked() {
        if (state.value.isRestoreInProgress) return
        state.value = state.value.copy(isRestoreInProgress = true)
        viewModelScope.launch {
            val restored = recipeRepository.restore(recipeId, number)
            state.value = state.value.copy(isRestoreInProgress = false)
            if (restored == null) {
                messages.emit("This revision could not be restored.")
            } else {
                messages.emit("Restored as revision $restored")
                navigator.goBack()
            }
        }
    }
}
