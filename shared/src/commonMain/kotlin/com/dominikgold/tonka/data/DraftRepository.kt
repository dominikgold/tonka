package com.dominikgold.tonka.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dominikgold.tonka.data.db.TonkaDatabase
import com.dominikgold.tonka.importing.RecipeDraft
import com.dominikgold.tonka.model.DraftId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.dominikgold.tonka.data.db.Recipe_draft as DbDraft

// In-progress drafts are stored as JSON so an import survives the app being killed
class DraftRepository(
    private val database: TonkaDatabase,
    private val clock: AppClock,
) {

    private val queries get() = database.recipeQueries

    val drafts: Flow<List<RecipeDraft>> = queries.selectAllDrafts().asFlow().mapToList(Dispatchers.IO).map { drafts ->
        drafts.mapNotNull { it.decode() }
    }

    suspend fun get(id: DraftId): RecipeDraft? = withContext(Dispatchers.IO) {
        queries.selectDraft(id.value).executeAsOneOrNull()?.decode()
    }

    suspend fun save(draft: RecipeDraft) = withContext(Dispatchers.IO) {
        queries.upsertDraft(
            DbDraft(
                id = draft.id.value,
                updated_at = clock.nowMillis(),
                draft_json = SnapshotJson.encodeToString(RecipeDraft.serializer(), draft),
            ),
        )
        Unit
    }

    suspend fun discard(id: DraftId) = withContext(Dispatchers.IO) {
        queries.deleteDraft(id.value)
        Unit
    }

    private fun DbDraft.decode(): RecipeDraft? =
        runCatching { SnapshotJson.decodeFromString(RecipeDraft.serializer(), draft_json) }.getOrNull()
}
