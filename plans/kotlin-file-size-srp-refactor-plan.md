# Kotlin File Size and SRP Refactor Plan

## Summary

This plan addresses Kotlin files that currently exceed 20 KB and reviews them against single-file responsibility expectations.

Current findings:

| File | Current size | Current lines | SRP risk |
|---|---:|---:|---|
| `app/src/main/java/com/example/pocastcloni/ui/main/MainActivity.kt` | 22.9 KB | 509 | High |
| `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsSections.kt` | 37.8 KB | 873 | Medium |
| `app/src/main/java/com/example/pocastcloni/data/repository/UserPreferencesRepositoryImpl.kt` | 23.9 KB | 463 | Low |

Priority:

1. Reduce `MainActivity.kt` below 20 KB and make it a thin app entry point again.
2. Split the largest settings UI sections if the first sprint is stable.
3. Leave `UserPreferencesRepositoryImpl.kt` mostly unchanged unless the refactor can be done without widening scope.

No behavioral redesign is intended. This is a structure-only refactor.

## Goals

- Keep `MainActivity.kt` focused on app bootstrap and top-level composition.
- Move reusable app-shell UI and navigation helpers into dedicated files under `ui/main`.
- Preserve current behavior:
  - gradient app background,
  - bottom-bar clean mode and auto-hide,
  - mini-player cover/detail toggle,
  - main-screen horizontal swipe navigation,
  - existing bottom navigation behavior.
- Keep each new file below 20 KB.
- Avoid broad rewrites of settings or persistence while the app has many active UI changes.

## Non-Goals

- No visual redesign.
- No navigation route redesign.
- No new settings.
- No DataStore schema changes.
- No package/module architecture migration.
- No push or remote git actions.

## Sprint 1 - MainActivity App Shell Extraction

### Scope

Extract app-shell background and top-level navigation helpers from `MainActivity.kt`.

### Proposed files

- `app/src/main/java/com/example/pocastcloni/ui/main/AppGradientBackground.kt`
- `app/src/main/java/com/example/pocastcloni/ui/main/MainNavigation.kt`

### Changes

- Move `AppGradientBackground` out of `MainActivity.kt`.
- Move `BottomNavItem` and `bottomNavItems` only if needed by extracted navigation helpers.
- Move:
  - `String?.isMainBottomNavRoute`
  - `NavHostController.navigateMainScreen`
  - `adjacentMainScreen`
  - `Modifier.mainScreenSwipeNavigation`
- Keep signatures stable and package-private where possible.
- Ensure `MainActivity.kt` only calls these helpers.

### Acceptance checks

- `MainActivity.kt` drops below 20 KB.
- No route behavior changes.
- Main screens still swipe in order:
  - `Settings -> Home -> Downloads -> Search`
  - reverse order on swipe right.
- `Favorites`, `History`, `PodcastDetail`, and fullplayer do not react to main-screen swipe.

### Tests

- Run `./gradlew.bat :app:compileDebugKotlin`.
- Run `./gradlew.bat :app:assembleDebug`.
- Install debug APK on emulator.
- Manual emulator smoke test:
  - launch app,
  - swipe left/right between main screens,
  - open podcast detail from mini-player cover,
  - tap mini-player cover again on same detail screen and confirm return to Home,
  - verify settings sliders still work without accidental screen switching.

### Risk

Pointer input helpers are sensitive to imports and Compose API versions. Keep the move mechanical and compile after each extraction.

## Sprint 2 - Bottom Bar Extraction

### Scope

Extract bottom navigation and clean-mode behavior from `MainActivity.kt`.

### Proposed file

- `app/src/main/java/com/example/pocastcloni/ui/main/AppBottomNavigation.kt`

### Changes

- Move:
  - bottom-bar dimensions,
  - `CleanModeBottomBarHost`,
  - `BottomBarRevealHandle`,
  - `Modifier.bottomBarSwipeGesture`,
  - `AppBottomNavigation`.
- Keep the current callback shape:
  - `onPlayerExpanded`,
  - `onNavigationItemClicked`.
- Keep Home special behavior:
  - from `Favorites`, `History`, `PodcastDetail`, Home button pops back.

### Acceptance checks

- `MainActivity.kt` remains below 20 KB, ideally below 14 KB.
- New `AppBottomNavigation.kt` remains below 20 KB.
- Bottom-bar clean mode still reveals on upward swipe and hides on downward swipe.
- Auto-hide delay still resets after navigation item clicks.

### Tests

- Run `./gradlew.bat :app:compileDebugKotlin`.
- Run `./gradlew.bat :app:assembleDebug`.
- Manual emulator smoke test:
  - clean mode enabled,
  - swipe up to reveal bottom bar,
  - tap Search/Downloads/Home,
  - confirm bar waits for configured delay instead of disappearing immediately,
  - swipe down to hide.

### Risk

The bottom-bar code depends on `NavHostController`, current destination state, and `UserSettings`. Extracting too aggressively could make the API noisy. Prefer one cohesive file over many tiny files.

## Sprint 3 - MainActivity Final Thin-Entry Review

### Scope

Clean up `MainActivity.kt` after the extraction.

### Changes

- Remove unused imports.
- Keep only:
  - `setContent`,
  - `MainViewModel` and `uiState` collection,
  - `rememberNavController`,
  - `onNavigateToPodcastDetail`,
  - `PocastCloniTheme`,
  - `Scaffold/NavHost/PlayerContainer` composition.
- Consider extracting the mini-player detail toggle to a named helper if it still makes `onCreate` dense.

### Acceptance checks

- `MainActivity.kt` is easy to scan.
- `onCreate` no longer contains raw gesture implementation.
- No helper in `MainActivity.kt` unrelated to bootstrap remains unless it is tiny and local.

### Tests

- Run `./gradlew.bat :app:compileDebugKotlin`.
- Run `./gradlew.bat :app:assembleDebug`.
- Optional: `./gradlew.bat :app:testDebugUnitTest` if quick enough.

### Risk

Over-extraction can make navigation harder to follow. Stop once the Activity is thin and the extracted files are coherent.

## Sprint 4 - SettingsSections Split Assessment

### Scope

Analyze `SettingsSections.kt` after the MainActivity work. Split only if the app remains stable.

### Proposed files

- `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsDesignSections.kt`
- `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsSyncSections.kt`
- Optional later:
  - `SettingsPlaybackSections.kt`
  - `SettingsDataSections.kt`

### Changes

- First move only the highest-change sections:
  - `SectionAppearance`
  - `SectionIndicator`
  - `SectionDownloads`
  - `SectionDownloadLocation`
  - `SectionCleanup`
- Keep shared small components in `SettingsSections.kt` or `SettingsComponents.kt` depending on existing usage.
- Avoid changing display text, ordering, state, or callbacks.

### Acceptance checks

- `SettingsSections.kt` becomes materially smaller.
- Each settings section file stays below 20 KB.
- The four settings tabs still show the same controls in the same order.
- Backup/import/export behavior remains unchanged.

### Tests

- Run `./gradlew.bat :app:compileDebugKotlin`.
- Run `./gradlew.bat :app:assembleDebug`.
- Manual emulator smoke test:
  - open Settings,
  - switch Design/Playback/Sync/Data tabs,
  - toggle transparent cards,
  - toggle transparent episode rows,
  - change gradient strength,
  - verify Save to Downloads and cleanup controls still render.

### Risk

Settings has many parameters and callback chains. A mechanical move is safe; changing APIs at the same time is not.

## Sprint 5 - DataStore Boilerplate Review

### Scope

Review `UserPreferencesRepositoryImpl.kt` only after UI extraction is stable.

### Recommendation

Do not split immediately unless there is a strong reason. Although it is over 20 KB, it has one cohesive responsibility: persist and restore `UserSettings` through DataStore.

### Possible future cleanup

- Extract preference key declarations into a private `UserPreferenceKeys.kt`.
- Extract `Preferences.toUserSettings(defaultSettings)` mapper.
- Extract `MutablePreferences.writeUserSettings(settings)` restore helper.

### Acceptance checks

- Behavior is identical for all settings.
- Backup restore still includes:
  - horizontal/vertical offset,
  - gradient background settings,
  - transparent card settings,
  - transparent episode row settings,
  - bottom-bar clean mode settings,
  - mini-player time overlay setting.

### Tests

- Run `./gradlew.bat :app:testDebugUnitTest`.
- Specifically verify existing tests:
  - `PodcastBackupHelperParsingTest`
  - `UpdateUserSettingsUseCaseTest`
  - `SettingsViewModelTest`
- Manual backup restore smoke test if emulator backup file is available.

### Risk

DataStore refactors are easy to break silently. This should stay lower priority than the Activity/UI split.

## Final Validation Checklist

- No Kotlin file introduced by the refactor exceeds 20 KB.
- `MainActivity.kt` is below 20 KB.
- `./gradlew.bat :app:compileDebugKotlin` passes.
- `./gradlew.bat :app:assembleDebug` passes.
- Emulator install succeeds.
- Manual smoke test covers:
  - main tab swipes,
  - bottom-bar clean mode,
  - mini-player detail toggle,
  - fullplayer open/collapse,
  - settings tabs and sliders.

## Suggested Commit Strategy

- Commit 1: Extract main swipe/navigation helpers.
- Commit 2: Extract bottom-bar app shell.
- Commit 3: Optional settings section split.
- Commit 4: Optional DataStore mapper/key cleanup.

Keep commits local only.
