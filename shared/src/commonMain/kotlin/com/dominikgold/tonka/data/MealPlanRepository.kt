package com.dominikgold.tonka.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dominikgold.tonka.data.db.TonkaDatabase
import com.dominikgold.tonka.model.MealPlanEntry
import com.dominikgold.tonka.model.MealPlanEntryId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.TagId
import com.dominikgold.tonka.model.TagMatch
import com.dominikgold.tonka.model.VariantId
import com.dominikgold.tonka.model.WeekdayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import com.dominikgold.tonka.data.db.Meal_plan_entry as DbMealPlanEntry
import com.dominikgold.tonka.data.db.Weekday_theme as DbWeekdayTheme
import com.dominikgold.tonka.data.db.Weekday_theme_tag as DbWeekdayThemeTag

class MealPlanRepository(
    private val database: TonkaDatabase,
    appScope: CoroutineScope,
) {

    private val queries get() = database.mealPlanQueries

    val entries: StateFlow<List<MealPlanEntry>> = queries.selectAllEntries().asFlow().mapToList(Dispatchers.IO)
        .map { entries -> entries.map { it.toEntity() } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    val themes: StateFlow<Map<DayOfWeek, WeekdayTheme>> = combine(
        queries.selectAllThemes().asFlow().mapToList(Dispatchers.IO),
        queries.selectAllThemeTags().asFlow().mapToList(Dispatchers.IO),
    ) { themes, themeTags ->
        val tagsByDay = themeTags.groupBy({ it.weekday }, { TagId(it.tag_id) })
        themes.associate { theme ->
            val weekday = DayOfWeek(theme.weekday.toInt())
            weekday to WeekdayTheme(
                weekday = weekday,
                name = theme.name,
                tagMatch = theme.tag_match.toEnumOr(TagMatch.ANY),
                tagIds = tagsByDay[theme.weekday].orEmpty().toSet(),
            )
        }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    suspend fun addRecipe(date: LocalDate, recipeId: RecipeId, variantId: VariantId?, title: String) =
        insert(date, recipeId, variantId, title, note = "")

    suspend fun addNote(date: LocalDate, text: String) = insert(date, recipeId = null, variantId = null, title = text.trim(), note = "")

    suspend fun move(entryId: MealPlanEntryId, date: LocalDate) = withContext(Dispatchers.IO) {
        val sortOrder = nextSortOrder(date)
        queries.updateEntryPlacement(date.toString(), sortOrder, entryId.value)
        Unit
    }

    suspend fun changeVariant(entryId: MealPlanEntryId, variantId: VariantId?) = withContext(Dispatchers.IO) {
        queries.updateEntryVariant(variantId?.value, entryId.value)
        Unit
    }

    suspend fun remove(entryId: MealPlanEntryId) = withContext(Dispatchers.IO) {
        queries.deleteEntry(entryId.value)
        Unit
    }

    suspend fun saveTheme(weekday: DayOfWeek, name: String, tagMatch: TagMatch, tagIds: Set<TagId>) = withContext(Dispatchers.IO) {
        val day = weekday.isoDayNumber.toLong()
        database.transaction {
            if (name.isBlank() && tagIds.isEmpty()) {
                queries.deleteTheme(day)
                return@transaction
            }
            queries.upsertTheme(DbWeekdayTheme(weekday = day, name = name.trim(), tag_match = tagMatch.name))
            queries.deleteThemeTags(day)
            tagIds.forEach { queries.insertThemeTag(DbWeekdayThemeTag(weekday = day, tag_id = it.value)) }
        }
    }

    private suspend fun insert(
        date: LocalDate,
        recipeId: RecipeId?,
        variantId: VariantId?,
        title: String,
        note: String,
    ) = withContext(Dispatchers.IO) {
        queries.insertEntry(
            DbMealPlanEntry(
                id = MealPlanEntryId.new().value,
                date = date.toString(),
                sort_order = nextSortOrder(date),
                recipe_id = recipeId?.value,
                variant_id = variantId?.value,
                title_snapshot = title,
                note = note,
            ),
        )
        Unit
    }

    private fun nextSortOrder(date: LocalDate): Long =
        (queries.maxSortOrderOn(date.toString()).executeAsOneOrNull()?.MAX ?: -1L) + 1
}
