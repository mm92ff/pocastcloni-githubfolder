# Implementation
## Changed Files
- `app/src/main/java/com/example/pocastcloni/domain/usecase/podcast/RemovePodcastSubscriptionUseCase.kt#L1` – new use case that encapsulates the unsubscribe path so UI layers no longer call `PodcastRepository.removePodcastByUrl` directly.
- `app/src/main/java/com/example/pocastcloni/domain/usecase/podcast/UpdatePodcastAutoDownloadUseCase.kt#L1` – new use case that owns `autoDownloadEnabled` flips by loading the podcast and calling `updatePodcastSettings` inside an IO dispatcher.
- `app/src/main/java/com/example/pocastcloni/ui/home/add/AddPodcastViewModel.kt#L35` – injects the new unsubscribe use case and wires `onTogglePodcast` through it instead of touching the repository.
- `app/src/main/java/com/example/pocastcloni/ui/history/HistoryViewModel.kt#L24` – swaps the direct `clearHistory` repository call for `ClearHistoryUseCase`, keeping the ViewModel as the sole UI→domain gateway.
- `app/src/main/java/com/example/pocastcloni/ui/home/detail/PodcastDetailViewModel.kt#L220` – routes the auto-download toggle through `UpdatePodcastAutoDownloadUseCase`, preserving the ViewModel’s event handling while isolating domain logic.
- `app/src/main/java/com/example/pocastcloni/domain/model/EpisodePresentation.kt#L1` – exposes `downloadStatus` as part of the domain presentation so downstream UI mappers no longer read `EpisodeEntity`.
- `app/src/main/java/com/example/pocastcloni/ui/home/detail/EpisodeUiModel.kt#L1` – reworked the mapper to consume `EpisodePresentation`/`EpisodeWithPodcastInfo` and provide composables with `DownloadStatusUiModel` without ever importing `EpisodeEntity`.
- `app/src/main/java/com/example/pocastcloni/domain/usecase/episode/GetDownloadedEpisodesWithPodcastInfoUseCase.kt#L1` – now emits domain models (`EpisodeWithPodcastInfo`), so the UI layer builds its `EpisodeUiModel` without domain code referencing UI types.
- `app/src/main/java/com/example/pocastcloni/ui/home/downloads/DownloadsViewModel.kt#L19` – imports the new mapper and transforms the domain flow into UI models, keeping the downloaded-episode flow free of repository calls.

## What Was Fixed
- Added explicit domain use cases for toggling podcast subscriptions and auto-download settings so ViewModels invoke orchestration logic instead of hitting `PodcastRepository` directly, satisfying the UI→ViewModel→UseCase flow.
- History, Add Podcast, and Podcast Detail ViewModels now route every user event through those use cases (and the existing `ClearHistoryUseCase`), insulating the UI from data-layer concerns and making failure scenarios (e.g., clear-history latency or update auto-download validation) observable through a single ViewModel boundary.
- `EpisodePresentation` now carries `DownloadStatus`, `EpisodeUiModel` was rewritten to rely solely on domain models, and the downloaded-episode flow feeds `EpisodeWithPodcastInfo` into UI mapping, restoring strict layer separation while preserving the cached UI diff logic.

## Checks Run
- `./gradlew.bat test` (pass, existing `@FlowPreview` warnings from `HistoryViewModel`/favorites use cases remain)
- `./gradlew.bat assemble` (pass, existing R8 warnings about missing XMLValidationSchemaFactory service entries from third-party libs)

## Open Risks
- `HistoryViewModel`/related use cases still rely on `@FlowPreview` flows, so compiler warnings remain until those continuations can migrate to stable APIs (`GetPlaybackHistoryWithPodcastInfoUseCase`, `FavoritesViewModel`).
- R8 reports the same missing service-class warnings for `XMLValidationSchemaFactory` resources during `assemble`; they stem from upstream dependencies and currently do not block the build but should be monitored if dependency versions change.
