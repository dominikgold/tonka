package com.dominikgold.tonka.backup

enum class ColumnType { Text, Integer }

class BackupColumn(val name: String, val type: ColumnType)

class BackupTable(
    val name: String,
    val columns: List<BackupColumn>,
    val orderBy: List<String>,
)

private fun text(name: String) = BackupColumn(name, ColumnType.Text)
private fun integer(name: String) = BackupColumn(name, ColumnType.Integer)

// Every exported table in foreign-key order: parents first, so inserts follow this list and deletes run in reverse.
// Keep in sync with the .sq schema; a new column must be added here in the same change.
val BackupSchema: List<BackupTable> = listOf(
    BackupTable(
        "ingredient",
        listOf(
            text("id"), text("name"), text("kind"), text("diet_class"), text("seasonality"), integer("fresh_months"),
            integer("storage_months"), integer("is_pantry_staple"), integer("needs_review"), text("origin"), text("notes"),
            text("seed_key"), integer("user_edited"), integer("created_at"), integer("updated_at"),
        ),
        orderBy = listOf("id"),
    ),
    BackupTable("ingredient_alias", listOf(text("ingredient_id"), text("alias"), text("normalized")), listOf("ingredient_id", "alias")),
    BackupTable("ingredient_edge", listOf(text("child_id"), text("parent_id")), listOf("child_id", "parent_id")),
    BackupTable(
        "ingredient_substitution",
        listOf(text("id"), text("from_id"), text("to_id"), text("note"), integer("bidirectional")),
        listOf("id"),
    ),
    BackupTable(
        "recipe",
        listOf(
            text("id"), text("title"), text("description"), text("source_type"), text("source_url"), text("source_book"),
            text("source_author"), text("source_page"), text("header_image_path"), text("yield_text"), integer("prep_minutes"),
            integer("cook_minutes"), integer("season_override_months"), text("diet_override"), integer("current_revision"),
            integer("created_at"), integer("updated_at"),
        ),
        listOf("id"),
    ),
    BackupTable(
        "recipe_variant",
        listOf(text("id"), text("recipe_id"), text("name"), text("description"), integer("sort_order"), integer("is_default")),
        listOf("id"),
    ),
    BackupTable("recipe_section", listOf(text("id"), text("recipe_id"), text("name"), integer("sort_order")), listOf("id")),
    BackupTable(
        "recipe_ingredient",
        listOf(
            text("id"), text("recipe_id"), text("section_id"), integer("sort_order"), text("raw_text"), text("amount_text"),
            text("unit"), text("ingredient_id"), text("display_name"), text("preparation"), integer("is_optional"), integer("is_key"),
        ),
        listOf("id"),
    ),
    BackupTable(
        "recipe_ingredient_alternative",
        listOf(text("line_id"), text("ingredient_id"), text("note"), integer("sort_order")),
        listOf("line_id", "ingredient_id"),
    ),
    BackupTable(
        "recipe_step",
        listOf(text("id"), text("recipe_id"), text("section_id"), integer("sort_order"), text("text"), text("image_path"), integer("timer_seconds")),
        listOf("id"),
    ),
    BackupTable("recipe_tip", listOf(text("id"), text("recipe_id"), integer("sort_order"), text("text")), listOf("id")),
    BackupTable("variant_scope", listOf(text("owner_type"), text("owner_id"), text("variant_id")), listOf("owner_type", "owner_id", "variant_id")),
    BackupTable("tag", listOf(text("id"), text("name")), listOf("id")),
    BackupTable("recipe_tag", listOf(text("recipe_id"), text("tag_id")), listOf("recipe_id", "tag_id")),
    BackupTable(
        "recipe_note",
        listOf(text("id"), text("recipe_id"), text("variant_id"), integer("created_at"), text("text")),
        listOf("id"),
    ),
    BackupTable(
        "recipe_revision",
        listOf(text("id"), text("recipe_id"), integer("number"), integer("created_at"), text("summary"), text("snapshot_json")),
        listOf("id"),
    ),
    BackupTable("recipe_scan_image", listOf(text("recipe_id"), text("path"), integer("sort_order")), listOf("recipe_id", "path")),
    BackupTable(
        "meal_plan_entry",
        listOf(text("id"), text("date"), integer("sort_order"), text("recipe_id"), text("variant_id"), text("title_snapshot"), text("note")),
        listOf("id"),
    ),
    BackupTable("weekday_theme", listOf(integer("weekday"), text("name"), text("tag_match")), listOf("weekday")),
    BackupTable("weekday_theme_tag", listOf(integer("weekday"), text("tag_id")), listOf("weekday", "tag_id")),
    BackupTable("app_setting", listOf(text("key"), text("value")), listOf("key")),
)
