# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project follows a beta release flow.

## [Unreleased]

### Added
- Current README screenshots in the versioned `picture/` folder, including the
  Home screen with visible bottom navigation.
- Date-based grouping for playback history and favorites.
- Favorites sort switch between manual order and added-date order.
- Episode publish dates in history and favorites rows.
- Mini-player timeline time overlay setting.
- Four-tab Settings layout: Design, Playback, Sync and Data.
- Bottom bar clean mode with swipe reveal, auto-hide and configurable delay.
- Swipe navigation between Settings, Home, Downloads and Search.
- Gradient background option with adjustable strength.
- Transparent search/settings cards and transparent history/favorites rows.
- One-handed layout improvements for history.
- Additional backup/restore coverage for settings roundtrips.
- Room schema version 11.

### Changed
- Project license changed from MIT to Mozilla Public License 2.0 (`MPL-2.0`).
- Refactored oversized UI and settings files into smaller focused modules.
- Improved dark-theme contrast across home, detail, history, favorites,
  downloads, settings and full-player screens.
- Polished Settings slider card value layout.
- Updated README to describe the current app feature set and local file policy.

### Fixed
- Android navigation bar color now follows the app theme.
- Legacy backup fields for horizontal and vertical offsets are restored.
- Mini-player time overlay no longer renders with an unwanted chip background.
- Full-player background and controls respect transparent/gradient styling.
- Notification dot clipping and shape rendering were corrected.
- Bottom bar now stays visible for the configured delay after menu interaction.
- Transient download failures are retried more robustly.

## [v3.51-beta] - 2026-03-26

### Added
- First published beta release asset: `pocastcloni_v3.51-beta.apk`.
- Repository documentation setup with `README.md` and `LICENSE`.
- Screenshot section in `README.md` with optimized app images.

### Changed
- Release asset naming aligned to `v3.51-beta`.

