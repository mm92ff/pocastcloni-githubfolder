# PocastCloni

PocastCloni is a modern Android podcast player built with Kotlin, Jetpack
Compose, Media3/ExoPlayer, Room, DataStore, WorkManager, and RSS feed
synchronisation.

## Features

- **Podcast Library & Covers** - Add podcasts by RSS URL or iTunes search,
  reorder subscriptions, inspect podcast details, and mark all new episodes as
  seen. Previously loaded cover art is retained as bounded persistent
  thumbnails, checked for updates at most weekly, and can be refreshed manually.
- **Playback** - Mini player and full player with play/pause, transactional
  timeline seeking, skip controls, progress tracking, background playback,
  notification support, and an optional mini-player time overlay.
- **Downloads & Sync** - Stream episodes, resume interrupted downloads, recover
  incomplete MediaStore publication, optionally save files to Android's
  Downloads folder, and run background feed checks. Smart Stream read limits
  range from full-feed mode (`0`) through progressive presets up to `100`, while
  manual full refresh always reads the complete feed without downloading audio.
- **Auto Download & Cleanup** - Per-podcast auto-download, global download
  limits, retry handling for transient failures, and automatic cleanup of played
  downloaded episodes.
- **History & Favorites** - Playback history and favorites with date grouping,
  publication dates, manual favorite ordering, and added-date sorting.
- **Customisation** - Light, Dark, and System themes; custom accent colour;
  optional gradient background; transparent mini player, cards, bottom bar, and
  episode rows; one-handed layout; swipe navigation; and bottom bar Clean Mode
  with auto-hide, configurable delay, and a 24-120 dp reveal touch area.
- **Languages** - Complete English default/fallback UI plus German, selectable
  independently from the device language or inherited from the system.
- **Settings** - Tabbed Settings screen for Design, Playback, Sync, and Data,
  including manual cover refresh and installed app version information.
- **Backup & Restore** - Portable JSON backups preserve subscription order and
  auto-download choices, favorites and manual ordering, playback history and
  progress, episode media metadata, and backup-supported user settings.
- **Statistics** - Listening and download statistics with reset actions.

## Screenshots

| Home | Home with Bottom Bar | Settings |
|:---:|:---:|:---:|
| ![Home](picture/home.png) | ![Home with Bottom Bar](picture/home2.png) | ![Settings](picture/settings.png) |

| Downloads | Search | History |
|:---:|:---:|:---:|
| ![Downloads](picture/downloads.png) | ![Search](picture/search.png) | ![History](picture/history.png) |

| Favorites | Podcast Detail | Player |
|:---:|:---:|:---:|
| ![Favorites](picture/favorites.png) | ![Podcast Detail](picture/podcast_detail.png) | ![Player](picture/player.png) |

## Download

Latest published release:

- [pocastcloni-3.69-beta-p30-signed.apk](https://github.com/mm92ff/pocastcloni-githubfolder/releases/download/v3.69-beta/pocastcloni-3.69-beta-p30-signed.apk)

The release uses version name `3.69-beta` and version code `36900`. The Data
settings tab shows the exact version installed on a device.

## Requirements

| Item | Version |
|---|---|
| Android | 8.0 (API 26) and higher |
| Target SDK | 35 |
| Compile SDK | 35 |
| JDK | 17 |

## Tech Stack

| Layer | Libraries |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | Domain / Data / UI layers, ViewModel, StateFlow |
| Media | Media3 ExoPlayer, MediaSession, foreground playback service |
| Database | Room schema 17 with exported migration schemas |
| Preferences | DataStore Preferences |
| Networking | Retrofit, OkHttp, streaming XmlPullParser, Jackson JSON |
| Images | Coil Compose, Coil SVG, persistent bounded WebP thumbnails |
| Background Work | WorkManager with Hilt workers |
| Dependency Injection | Hilt |
| Logging | Timber |
| Quality | Detekt, ktlint, Android Lint, JUnit 4, MockK, Turbine, AndroidX tests, Macrobenchmark, Perfetto |

## Build

Before changing packaged application content or producing a signed APK, follow
the tracked [application versioning policy](docs/VERSIONING.md).

### Prerequisites

- Android Studio
- JDK 17
- Android SDK 35

### Clone & Run

```bash
git clone https://github.com/mm92ff/pocastcloni-githubfolder.git
cd pocastcloni-githubfolder
```

Open the project in Android Studio and run it on a device or emulator with API
26 or newer.

### Build APK

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

### Run Checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:ktlintCheck :app:detekt :app:lintDebug
```

Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:ktlintCheck :app:detekt :app:lintDebug
```

### Release Gates

The `releaseSmoke` variant inherits the complete minified `release` configuration and
only replaces the signing configuration with the Android debug key. It is intended for
offline R8 and device smoke tests, never for distribution.

Run the fast quality gates independently so a timeout is attributable to one tool:

```bash
./gradlew --dependency-verification=strict :app:testDebugUnitTest
./gradlew --dependency-verification=strict :app:detekt
./gradlew --dependency-verification=strict :app:ktlintCheck
./gradlew --dependency-verification=strict :app:lintRelease
./gradlew --dependency-verification=strict :app:assembleReleaseSmoke
```

The versioned release gate runs the debug unit suite, verifies the merged manifest
allowlist, builds the unsigned release APK and records APK SHA-256/size plus R8 mapping
path/size:

```bash
./gradlew --dependency-verification=strict :app:releaseGate
cat app/build/reports/release-gate/release-artifacts.properties
```

On Windows, replace `./gradlew` with `.\gradlew.bat` and use `Get-Content`
instead of `cat` for the generated report.

The generated APK, mapping and report stay below ignored `build/` directories. The
unsigned `assembleRelease` output is not installable as a production update; use the
local fail-closed signing helper for a distributable APK.

Application-owned UI copy uses a complete English default and fallback catalog. Approved
locales provide complete translated UI catalogs, and Android Lint keeps its standard
translation checks enabled.

On an emulator, run only the focused minified smoke package during normal development:

```powershell
.\gradlew.bat -PinstrumentationBuildType=releaseSmoke :app:connectedReleaseSmokeAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.package=com.example.pocastcloni.release"
```

It launches the minified app, opens Settings, exports and re-imports a backup through
Android's document picker, and adds a loopback RSS feed with explicit HTTP and local-network
consent. The journey then returns Home, opens the locally served episode, starts playback,
and opens the full player. Historical migrations remain covered by their focused test suite;
full instrumentation and Macrobenchmarks are final release-candidate gates.

### Performance Benchmarks

The `:benchmark` module runs release-like Macrobenchmarks with Compose runtime
tracing. Use an API 30+ emulator; the reference setup is Android 15/API 35.
All podcast, artwork, and audio data used by player benchmarks is served locally.

```powershell
.\benchmark\scripts\run-benchmarks.ps1
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -PfullTracing=true `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.example.pocastcloni.benchmark.PlayerRenderingBenchmark"
.\benchmark\scripts\verify-compose-traces.ps1
```

Benchmark JSON and Perfetto traces are generated below `benchmark/build/` and
are intentionally not versioned. SQL templates for Perfetto Trace Processor are
stored in `benchmark/trace-queries/`. Emulator numbers are local regression
baselines and should not be compared directly with physical-device results.
Full tracing is opt-in because its larger traces and runtime overhead distort
frame baselines and can exhaust small emulator data partitions. The runner
executes benchmark classes separately so their temporary device traces are
released between classes, while reports are retained below `benchmark-reports/`.

## Project Structure

```text
app/src/main/java/com/example/pocastcloni/
├── data/       # Room entities, DAO, repositories, RSS/search APIs, workers
├── domain/     # Models, repository interfaces, player contracts, use cases
├── di/         # Hilt modules
├── playback/   # Media3 controller infrastructure and playback contracts
├── service/    # PodcastPlaybackService
├── ui/         # Compose screens, player UI, settings, theme, navigation
└── util/       # Constants, formatting, HTML, network and time helpers
```

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Load RSS feeds, podcast artwork, search results, and episode streams |
| `ACCESS_NETWORK_STATE` | Detect network state for downloads, sync, and statistics |
| `WRITE_EXTERNAL_STORAGE` | Legacy public Downloads support on Android 9 and older |
| `FOREGROUND_SERVICE` | Keep playback and long-running work stable |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback foreground service on newer Android versions |
| `FOREGROUND_SERVICE_DATA_SYNC` | Feed sync foreground service type on newer Android versions |
| `POST_NOTIFICATIONS` | Show playback and foreground-service notifications |

## Security & Privacy

- HTTPS is the default. Cleartext HTTP feeds and local/private network origins
  require explicit per-podcast approval.
- RSS bodies and redirects, backups, artwork, and downloads are processed with
  bounded size, count, and storage limits.
- Media-session commands are accepted only from trusted controllers.
- Backup imports are validated before mutation and use rollback-safe recovery.
- Persistent cover thumbnails are derived files stored in the app's
  `noBackupFilesDir`; they are not exported in Android backups and can be rebuilt
  from podcast metadata.

## Notes

- RSS feed parsing supports configurable Smart Stream limits and manual full
  refresh. Normal feed refresh never downloads episode audio by itself.
- On Android 10 and newer, saving to the public Downloads folder uses
  MediaStore.
- Episode download work requires a connected network. `CONNECTED` intentionally
  allows both metered mobile data and unmetered Wi-Fi; Android may defer work for
  other system constraints such as low storage.
- Downloaded episodes fall back to streaming when the local file is missing.
- The bottom navigation can be hidden in Clean Mode and revealed with an upward
  swipe or tap. The reveal touch area is configurable from 24 to 120 dp in 4 dp
  steps; the default remains 48 dp.
- Cover loading prefers memory, then the persistent thumbnail, then the network,
  and finally a neutral placeholder. Missing or damaged thumbnails are repaired
  immediately; normal validation is limited to once per week.
- Backups are JSON-based. Version 3 stores portable library state: podcast
  ordering and per-podcast auto-download, feed-scoped favorite/history/progress
  state, favorite added time and manual order, episode media metadata, and
  backup-supported user settings.
- Local download paths and download status are device-specific and are never
  exported or overwritten during restore. App-language selection and persistent
  cover thumbnails are also device-specific. Version 1 and 2 object backups and
  legacy JSON arrays of podcast URLs remain importable.
- Guaranteed in-place database upgrades start at release `v3.51-beta` (Room schema
  10). Authentic schemas 10 through 17 are tracked and migrated to the current schema
  in parameterized tests. Recovery migrations for schemas 1 through 9 remain in the
  app, but those versions have no authentic tracked release schema or database fixture
  and are therefore best-effort rather than a claimed support guarantee.

## Contributing

### Language policy

The complete default and fallback UI catalog is English. Approved locale resource catalogs
may translate application-owned UI copy, accessibility text, notifications, and stable error
messages. Code identifiers, comments, KDoc, logs, scripts, tests, changelog entries, and
project documentation remain English. External podcast metadata, user input, and protocol
payloads may contain other languages. Non-English test fixtures are allowed only when
language or Unicode content is explicitly the behavior under test, and the test purpose must
be documented in English. Raw localized exception or server text must not be presented
directly as application-owned UI copy.

To add another UI locale, provide a complete reviewed `values-<language>` catalog, add its
BCP 47 tag to the packaged locale allowlist and `SupportedAppLanguage`, extend the resource
parity and locale-matrix tests, and record the user-visible addition in `CHANGELOG.md`.
Placeholders, XLIFF IDs, plurals, and string-array item counts must remain compatible with
the English catalog. App language state stays in the AppCompat/platform locale store and is
never added to podcast backups or general user settings.

Run `scripts/verify_api35_locale_reboot.ps1 -Serial <isolated-api-35-emulator>` to verify the
one-time AppCompat locale migration and the first UI render after a reboot. The script rebuilds
and installs debug test artifacts, changes the target app's locale state, and reboots the named
device, so never point it at a user-owned emulator or physical device.

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/my-feature`).
3. Commit your changes (`git commit -m "feat: add my feature"`).
4. Push to the branch (`git push origin feature/my-feature`).
5. Open a Pull Request.

## License

This project is licensed under the [Mozilla Public License 2.0](LICENSE).
