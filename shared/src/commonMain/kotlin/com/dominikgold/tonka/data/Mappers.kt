package com.dominikgold.tonka.data

import com.dominikgold.tonka.model.DietClass
import com.dominikgold.tonka.model.Ingredient
import com.dominikgold.tonka.model.IngredientEdge
import com.dominikgold.tonka.model.IngredientId
import com.dominikgold.tonka.model.IngredientKind
import com.dominikgold.tonka.model.IngredientOrigin
import com.dominikgold.tonka.model.IngredientSubstitution
import com.dominikgold.tonka.model.MealPlanEntry
import com.dominikgold.tonka.model.MealPlanEntryId
import com.dominikgold.tonka.model.MonthSet
import com.dominikgold.tonka.model.NoteId
import com.dominikgold.tonka.model.RecipeId
import com.dominikgold.tonka.model.RecipeNote
import com.dominikgold.tonka.model.Seasonality
import com.dominikgold.tonka.model.SubstitutionId
import com.dominikgold.tonka.model.VariantId
import kotlinx.datetime.LocalDate
import com.dominikgold.tonka.data.db.Ingredient as DbIngredient
import com.dominikgold.tonka.data.db.Ingredient_edge as DbIngredientEdge
import com.dominikgold.tonka.data.db.Ingredient_substitution as DbIngredientSubstitution
import com.dominikgold.tonka.data.db.Meal_plan_entry as DbMealPlanEntry
import com.dominikgold.tonka.data.db.Recipe_note as DbRecipeNote

inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

fun DbIngredient.toEntity() = Ingredient(
    id = IngredientId(id),
    name = name,
    kind = kind.toEnumOr(IngredientKind.INGREDIENT),
    dietClass = diet_class.toEnumOr(DietClass.UNKNOWN),
    seasonality = seasonality.toEnumOr(Seasonality.UNKNOWN),
    freshMonths = MonthSet(fresh_months.toInt()),
    storageMonths = MonthSet(storage_months.toInt()),
    isPantryStaple = is_pantry_staple,
    needsReview = needs_review,
    origin = origin.toEnumOr(IngredientOrigin.USER),
    notes = notes,
    seedKey = seed_key,
    isUserEdited = user_edited,
    createdAt = created_at,
    updatedAt = updated_at,
)

fun DbIngredientEdge.toEntity() = IngredientEdge(
    childId = IngredientId(child_id),
    parentId = IngredientId(parent_id),
)

fun DbIngredientSubstitution.toEntity() = IngredientSubstitution(
    id = SubstitutionId(id),
    fromId = IngredientId(from_id),
    toId = IngredientId(to_id),
    note = note,
    isBidirectional = bidirectional,
)

fun DbMealPlanEntry.toEntity() = MealPlanEntry(
    id = MealPlanEntryId(id),
    date = LocalDate.parse(date),
    sortOrder = sort_order.toInt(),
    recipeId = recipe_id?.let(::RecipeId),
    variantId = variant_id?.let(::VariantId),
    titleSnapshot = title_snapshot,
    note = note,
)

fun DbRecipeNote.toEntity() = RecipeNote(
    id = NoteId(id),
    recipeId = RecipeId(recipe_id),
    variantId = variant_id?.let(::VariantId),
    createdAt = created_at,
    text = text,
)
