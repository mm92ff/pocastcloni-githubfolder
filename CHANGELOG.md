# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project follows a beta release flow.

## [Unreleased]

### Added
- Complete German UI localization and an app-language selector with system-default,
  English and German choices.
- Configurable Smart Stream feed-read limits from full-feed mode (`0`) through
  progressive presets up to `100`; manual full refresh bypasses the read limit
  without downloading episode audio.
- Transparent mini-player option.
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
- Configurable Home bottom spacing above the bottom-bar reveal area, defaulting
  to no extra gap while preserving visible mini-player clearance.

### Changed
- The language selector keeps its globe and always displays `Language` so users can
  find it after switching to an unfamiliar interface language.
- English remains the complete default and fallback UI catalog while code, diagnostic
  logs and developer documentation remain consistently English.
- Android API 35 build support with Android 15 edge-to-edge behavior and
  background-work constraints.
- Energy-efficient feed defaults for fresh installations.
- Portable backup and restore now preserve podcast ordering, favorites, played
  state, playback progress, episode media metadata and the Smart Stream limit.
- Room schema version 16 with explicit migration coverage.
- Downloads now use resumable, storage-aware transfers and recover interrupted
  or pending MediaStore publication.
- Playback now uses a bounded media cache with lifecycle-aware progress and
  streaming statistics.
- Project license changed from MIT to Mozilla Public License 2.0 (`MPL-2.0`).
- Refactored oversized UI and settings files into smaller focused modules.
- Improved dark-theme contrast across home, detail, history, favorites,
  downloads, settings and full-player screens.
- Polished Settings slider card value layout.
- Updated README to describe the current app feature set and local file policy.

### Fixed
- Explicit app-language selections are applied on the first UI render after Android 13+
  restores legacy AppCompat locale state during startup.
- Home pull-to-refresh now honors the configured feed update mode and Smart
  Stream read limit.
- Episode identity is scoped to its podcast feed, preventing collisions when
  different feeds reuse the same GUID.
- Feed refresh requests are coordinated and persisted atomically, with safe
  handling for unordered feeds; limited Smart Stream still follows feed order.
- Player command and progress lifecycle handling avoids unavailable-episode
  crashes and preserves terminal playback progress.
- Startup recovery and initialization are ordered to avoid competing data flows.
- Android navigation bar color now follows the app theme.
- Legacy backup fields for horizontal and vertical offsets are restored.
- Mini-player time overlay no longer renders with an unwanted chip background.
- Full-player background and controls respect transparent/gradient styling.
- Notification dot clipping and shape rendering were corrected.
- Bottom bar now stays visible for the configured delay after menu interaction.
- Transient download failures are retried more robustly.

### Security
- Media session access is restricted to approved controllers.
- Cleartext and local/private podcast resources require explicit approval.
- RSS parsing, redirects, response bodies, backups and downloads use bounded
  processing and storage limits.
- Backup imports validate settings and referenced data before mutation and use
  rollback-safe recovery.

## [v3.51-beta] - 2026-03-26

### Added
- First published beta release asset: `pocastcloni_v3.51-beta.apk`.
- Repository documentation setup with `README.md` and `LICENSE`.
- Screenshot section in `README.md` with optimized app images.

### Changed
- Release asset naming aligned to `v3.51-beta`.

