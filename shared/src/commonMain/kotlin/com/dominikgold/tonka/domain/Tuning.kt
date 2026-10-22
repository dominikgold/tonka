package com.dominikgold.tonka.domain

object Tuning {

    // Seasonality scoring
    const val FRESH_STATUS = 1.0
    const val STORAGE_STATUS = 0.5
    const val KEY_INGREDIENT_WEIGHT = 3.0
    const val REGULAR_INGREDIENT_WEIGHT = 1.0
    const val SEASON_TAG_EXCLUDED_FACTOR = 0.5
    const val IN_SEASON_THRESHOLD = 0.6

    // Meal plan suggestions
    const val SUGGESTION_SEASON_WEIGHT = 0.6
    const val SUGGESTION_RECENCY_WEIGHT = 0.4
    const val SUGGESTION_SCORE_WITHOUT_SEASON = 0.3
    const val RECENCY_RECOVERY_DAYS = 42
    const val RECENTLY_PLANNED_WINDOW_DAYS = 7
    const val RECENTLY_PLANNED_PENALTY = 0.2
    const val SUGGESTION_MAX_JITTER = 0.05
    const val SUGGESTION_COUNT = 10
    const val THEME_FALLBACK_THRESHOLD = 5

    // Search
    const val SEARCH_TITLE_WEIGHT = 3
    const val SEARCH_INGREDIENT_WEIGHT = 2
    const val SEARCH_CATEGORY_WEIGHT = 1
    const val SEARCH_DEBOUNCE_MILLIS = 150L

    // Ingredient matching
    const val FUZZY_MAX_EDIT_DISTANCE = 2
    const val FUZZY_MIN_NAME_LENGTH = 4

    // Images
    const val STORED_IMAGE_MAX_LONG_EDGE = 2000
    const val STORED_IMAGE_JPEG_QUALITY = 85
    const val SCAN_UPLOAD_MAX_LONG_EDGE = 2576
    const val SCAN_UPLOAD_JPEG_QUALITY = 85
    const val HEADER_IMAGE_ASPECT_RATIO = 16f / 9f
    const val MAX_SCAN_PAGES = 4

    // Importing
    const val WEB_FETCH_TIMEOUT_MILLIS = 15_000L
    const val AI_TEXT_MAX_CHARACTERS = 50_000
    const val MAX_WEB_IMAGE_CANDIDATES = 3

    // Backup
    const val SAFETY_BACKUPS_TO_KEEP = 3
}
