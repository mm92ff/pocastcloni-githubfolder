# Implementation
## Changed Files
- `app/src/main/java/com/example/pocastcloni/ui/common/EpisodeDisplayModel.kt`
- `app/src/main/java/com/example/pocastcloni/ui/common/ListableEpisodeItem.kt`
- `app/src/main/java/com/example/pocastcloni/ui/favorites/FavoritesContract.kt`
- `app/src/main/java/com/example/pocastcloni/ui/favorites/FavoritesScreen.kt`
- `app/src/main/java/com/example/pocastcloni/ui/favorites/FavoritesViewModel.kt`
- `app/src/main/java/com/example/pocastcloni/ui/history/HistoryContract.kt`
- `app/src/main/java/com/example/pocastcloni/ui/history/HistoryScreen.kt`
- `app/src/main/java/com/example/pocastcloni/ui/history/HistoryViewModel.kt`

## What Was Fixed
- Added `EpisodeDisplayModel` + shared picker to map Room `EpisodeEntity` → UI-only data, kept `ListableEpisodeItem`, favorites/history contracts, and screens free of `data.local` imports so Compose consumes stable UI models.
- Favorites/history view models now cache the latest `EpisodeEntity`s internally, expose intent-driven actions (`guid` instead of entities), and mediate all repository/use-case calls (playback, toggle, reorder) so UI actions stay within the UI→ViewModel→UseCase flow; this also resolves the “schema change spillover” irritation because the UI no longer touches `EpisodeEntity`.
- Actions and dialogs now rely on the UI model, and reorder/swipe logic pulls entities from the ViewModel cache before calling `reorderFavoritesUseCase` or `audioPlayerController.play`, preventing the “data-layer race in the playback controller” failure scenario by keeping repository access behind the ViewModel boundary.

## Checks Run
- `./gradlew test` *(passes; existing warnings about `Icons.Filled.ArrowBack` deprecation remain)*
- `./gradlew assemble` *(passes; existing R8 warnings about missing XMLValidationSchemaFactory service classes persist)*

## Open Risks
- The deprecated `Icons.Default.ArrowBack` usage and R8 service-class warnings predate this change but still appear in the logs—track replace/cleanup and review the linked dependencies if they surface in release audits.
- Layering discipline would benefit from optional static analysis (`./gradlew detekt`, `./gradlew ktlintCheck`, `./gradlew spotlessCheck`) to automatically guard against future UI→data leaks; consider adding them to the next iteration’s checklist.
