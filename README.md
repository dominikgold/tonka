# Tonka

A personal, local-first recipe book that knows what's in season. Compose Multiplatform for Android, iOS and Desktop
(macOS, Windows, Linux).

- **Recipes**: variants that share lines (Osaka vs. Hiroshima okonomiyaki, classic vs. vegan cake), sections, tips,
  tags, a cooking-notes log and a full revision history with diff and restore.
- **Seasons**: an ingredient calendar for temperate Central/Northern Europe (fresh and storage months), a category graph
  of any depth, substitutions and a review queue for new ingredients.
- **Meal Plan**: this week and next, weekday themes, seasonal suggestions that rotate what you haven't cooked lately,
  and a shopping list of ingredient types from today until Sunday next week.
- **Import**: write manually, paste or open text, import from a website (schema.org JSON-LD or microdata), or scan
  1–4 cookbook pages with Claude using your own Anthropic API key.
- **Backup**: export everything, images included, to one `.recipebook` file and import it on another device.

Everything is stored on the device. The only network features are the website import and the AI calls.

## Project layout

| Module | Contents |
| --- | --- |
| `shared` | No UI. `model` (domain types), `data` (SQLDelight schema, repositories, image store), `domain` (seasonality, diet derivation, suggestions, search, shopping list, ingredient matching), `importing` (text, web and AI pipelines), `backup` (export and import), `platform` (driver, secret store, image processing). |
| `composeApp` | All UI: `designsystem`, `feature.recipes`, `feature.editor`, `feature.importflow`, `feature.seasons`, `feature.mealplan`, `feature.settings`, `navigation`. Also the desktop entry point and the iOS view controller. |
| `androidApp` | The Android application module (AGP 9 keeps the app module separate from the KMP library). |
| `iosApp` | The Xcode wrapper project. |

Tunable constants for scoring, suggestions and search live in `shared/.../domain/Tuning.kt`. The ingredient seed is
`composeApp/src/commonMain/composeResources/files/seed/ingredients.json`; bump its `version` to re-apply it (rows you
edited are never overwritten).

## Running

```bash
./gradlew :composeApp:run
```

```bash
./gradlew :androidApp:installDebug
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme; the build phase calls
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.

Desktop installers (dmg, msi, deb):

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

## Schema changes

The schema lives in `shared/src/commonMain/sqldelight`. After v1 every change goes into a numbered migration in
`shared/src/commonMain/sqldelight/migrations/<version>.sqm`, and a new or renamed column must also be added to
`shared/.../backup/BackupTables.kt` so backups stay lossless.

## Notes

- Seasonal months are approximate values for Central Europe, compiled with the German BZfE Saisonkalender in mind.
  Verify and adjust them in the Ingredients screen.
- Fonts: Playfair Display and Inter, both under the SIL Open Font License 1.1 (Latin subsets).
