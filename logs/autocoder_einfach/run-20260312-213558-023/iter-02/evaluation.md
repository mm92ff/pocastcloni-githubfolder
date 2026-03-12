# Evaluation
## Summary
- **Architecture gap:** `PodcastRepository` is already leaking data-layer artifacts into the domain boundary (`com.example.pocastcloni.domain.repository.PodcastRepository`: imports such as `EpisodeEntity` at `PodcastRepository.kt:3` and flow-return methods like `getEpisodesFlow` at `PodcastRepository.kt:18` expose Room entities instead of pure domain models, which makes any UI/Domain refactor contingent on surface-level schema changes.
- **UI coupling:** Several UI pieces bind directly to data-layer types or repositories, violating the UI→Domain→Data flow. For example `FavoritesScreen` imports `EpisodeEntity` at `FavoritesScreen.kt:37`, and `FavoritesContract` exposes actions/bindings with `EpisodeEntity` at `FavoritesContract.kt:4` and `FavoritesContract.kt:34`. `AudioPlayerController` (a UI-layer controller) directly calls `PodcastRepository` methods to resolve favorites and episode details (`ui/player/AudioPlayerController.kt:30`, `:174`, `:190`, `:224`‑`:225`), bypassing ViewModels/use cases. These leaks make it hard to reason about boundaries, prevent lightweight UI testing, and risk accidental data-layer mutations in UI code.
- **Best-practice direction:** UI components should consume only UI models assembled by ViewModels. A small Kotlin example for the Favorites screen could look like this:
  ```kotlin
  @Immutable
  data class FavoriteEpisodeUiModel(
      val guid: String,
      val title: String,
      val podcastTitle: String?,
      val description: String,
      val isFavorite: Boolean
  )

  fun FavoriteEpisodeUiModel.Companion.fromDomain(
      episode: EpisodeEntity,
      podcast: Podcast?
  ) = FavoriteEpisodeUiModel(
      guid = episode.guid,
      title = episode.title,
      podcastTitle = podcast?.title,
      description = episode.description,
      isFavorite = episode.isFavorite
  )
  ```
  The ViewModel should map the repository flow to this UI model before exposing it, so composables no longer import `EpisodeEntity`.
- **Irritation / Failure Scenarios:**
  1. *Schema change spillover:* Modifying the Room `EpisodeEntity` (e.g., renaming a column) now forces updates in every UI composable that references it—unexpected recompilation and runtime crashes. Expected behavior: only the data layer and dedicated mappers should touch `EpisodeEntity`; UI consumes a stable `FavoriteEpisodeUiModel`.
  2. *Data-layer race in playback controller:* `AudioPlayerController` calling `podcastRepository.getEpisode` off the UI thread mingles playback logic with data persistence, making the Player harder to mock and causing duplicated coroutine/thread handling. Expected behavior: a dedicated use case exposes `PlaybackEpisode` domain data; the controller observes that via ViewModel/Flow and never touches the repository directly.

## Plan
1. **Catalog boundary violations:** List all UI packages (favorites, history, player, etc.) that import `data.local` or interact with repositories directly; earmark each for refactor. Document dependencies on domain mappers so the Implementation phase can target only these files.
2. **Define UI model layer:** Introduce immutable UI DTOs (e.g., `FavoriteEpisodeUiModel`, `PlaybackEpisodeUiModel`) and move `EpisodeEntity`/`PodcastEntity` mapping into use cases or helper mappers in the data/domain layer, so ViewModels expose UI-friendly flows.
3. **Re-route events through ViewModel:** Ensure composables fire only `ViewModel` actions (e.g., `FavoritesAction`), and that controllers (e.g., `AudioPlayerController`) mediate via domain use cases (possibly a new `PlaybackStateUseCase`) instead of pulling from repositories.
4. **Audit required/optional checks:** Plan to run `./gradlew test` + `./gradlew assemble` (required) and consider elevating `./gradlew detekt` or `./gradlew ktlintCheck` to spot layering violations before they reach UI code.

## Acceptance Checklist
- [ ] UI layer (composables, controllers, contracts) no longer import `com.example.pocastcloni.data.local.*`; all flows consume UI/domain models produced by ViewModels.
- [ ] Domain layer (use cases, repositories) exposes only domain-safe types; `PodcastRepository` interface no longer returns `EpisodeEntity` or other Room types.
- [ ] Events and interactions are routed UI → ViewModel → UseCase/Repository; no composable or controller bypasses the ViewModel to mutate data.
- [ ] Required checks pass: `./gradlew test`, `./gradlew assemble`.
- [ ] Optional/static-analysis candidates considered for future (suggested: `./gradlew detekt`, `./gradlew ktlintCheck`, `./gradlew spotlessCheck` to enforce clean layering).

## Risks
- Fixing these boundaries depends on shared mappers and domain models; any change to `EpisodeEntity`/`PodcastEntity` semantics may ripple through the UI mapping layer.
- Without running optional static checks (detekt/ktlint), we may miss subtle dependency leaks; consider adding them to future iterations.
