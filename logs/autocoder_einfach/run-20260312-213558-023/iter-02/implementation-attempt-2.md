# Implementation
## Changed Files
- `app/src/main/java/com/example/pocastcloni/domain/model/EpisodePresentation.kt`
- `app/src/main/java/com/example/pocastcloni/domain/model/EpisodeWithPodcastInfo.kt`
- `app/src/main/java/com/example/pocastcloni/domain/usecase/episode/GetFavoriteEpisodesWithPodcastInfoUseCase.kt`
- `app/src/main/java/com/example/pocastcloni/domain/usecase/episode/GetPlaybackHistoryWithPodcastInfoUseCase.kt`
- `app/src/main/java/com/example/pocastcloni/domain/usecase/favorite/ReorderFavoritesUseCase.kt`
- `app/src/main/java/com/example/pocastcloni/domain/usecase/episode/StartPlaybackUseCase.kt`
- `app/src/main/java/com/example/pocastcloni/domain/usecase/player/PreparePlaybackUseCase.kt`
- `app/src/main/java/com/example/pocastcloni/ui/common/EpisodeDisplayModel.kt`
- `app/src/main/java/com/example/pocastcloni/ui/favorites/FavoritesViewModel.kt`
- `app/src/main/java/com/example/pocastcloni/ui/history/HistoryViewModel.kt`
- `app/src/main/java/com/example/pocastcloni/ui/player/PlayerInterfaces.kt`
- `app/src/main/java/com/example/pocastcloni/ui/player/AudioPlayerController.kt`

## What Was Fixed
- Added domain presentation DTOs so the UI no longer imports `EpisodeEntity`; `EpisodePresentation`/`EpisodeWithPodcastInfo` expose only the fields Compose needs, and both `GetFavoriteEpisodesWithPodcastInfoUseCase` and `GetPlaybackHistoryWithPodcastInfoUseCase` now emit lists of these domain models under `@FlowPreview` so the UI can observe stable, debounced flows without touching data-layer types (`app/.../EpisodePresentation.kt:1-28`, `app/.../EpisodeWithPodcastInfo.kt:1-6`, `app/.../GetFavoriteEpisodesWithPodcastInfoUseCase.kt:1-28`, `app/.../GetPlaybackHistoryWithPodcastInfoUseCase.kt:1-28`).
- Favorites and history view models now map those flows with `EpisodeDisplayModel.from`, keep optimistic reorders confined to the UI state, and route playback events through GUIDs instead of cached entities so Compose never sees the data layer (`FavoritesViewModel.kt:39-140`, `HistoryViewModel.kt:33-86`, `EpisodeDisplayModel.kt:1-28`). This covers two irritation/failure scenarios:
  1. Drag-and-drop reorder while the repository call is still pending now relies on `_optimisticFavorites`/`reorderFavoritesUseCase(currentList.map { it.episode.guid })`, and any failure throws so the UI reverts to the persisted ordering (`ReorderFavoritesUseCase.kt:9-17`), matching the expectation that the UI shows the optimistic order until the backend confirms.
  2. Batch delete clears history only after `ConfirmClearHistory` fires (the dialog flag guards the repository call) so accidental taps don’t hit the data layer without explicit confirmation, which keeps the expected “confirmation before delete” behavior (`HistoryViewModel.kt:64-86`).
- The player boundary now accepts GUIDs only: `PlayerActions.play` takes `episodeGuid`, `AudioPlayerController` fetches the freshest entity through `PreparePlaybackUseCase(guid)` before interacting with Media3, and `StartPlaybackUseCase` simply forwards the GUID to the controller. This keeps playback bootstrapping behind the ViewModel/use-case and prevents Compose from pulling data-layer objects directly (`PlayerInterfaces.kt:1-13`, `AudioPlayerController.kt:179-205`, `PreparePlaybackUseCase.kt:1-47`, `StartPlaybackUseCase.kt:1-11`).

## Checks Run
- `.\gradlew.bat test`
- `.\gradlew.bat assemble` (passes; R8 emitted existing warnings about missing `XMLValidationSchemaFactory` service descriptors)

## Open Risks
- The assemble task still logs R8 warnings about `META-INF/services/org.codehaus.stax2.validation.XMLValidationSchemaFactory.*` entries in a third-party JAR; they appear to be pre-existing but should be reviewed if release builds must be squeaky clean.
