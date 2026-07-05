# Podcastplayer Best Practices Audit Plan

## Ziel

Dieser Plan übersetzt die Regeln aus `Prompt_Android_studio_v10.0.md` und `Prompt_splitted.docx` in einen realistischen Audit- und Verbesserungsplan für PocastCloni.

Der Fokus liegt nicht auf Enterprise-Overengineering, sondern auf den Risiken, die bei einem Podcastplayer wirklich zählen:

- stabile Wiedergabe,
- korrekte Queue- und Player-Zustände,
- sichere Downloads und Streaming,
- robuste Backup-/Restore-Funktion,
- saubere Room-Datenintegrität,
- gute Compose-Performance bei Player, Listen, History und Favorites,
- wartbare UI/Domain/Data-Grenzen.

## Grundregeln

- Analyse und Umsetzung sprintweise, nicht alles auf einmal.
- Pro Sprint nur ein Risikobereich.
- Kleine, lokale Refactorings bevorzugen.
- Keine neuen Libraries, außer es gibt einen klaren stabilen Nutzen.
- Keine Alpha/Beta/RC-Abhängigkeiten einführen.
- Keine Pushes, nur lokale Commits nach erfolgreich geprüften Änderungen.
- Offizielle Android-Empfehlungen als Orientierung, nicht als dogmatische Pflicht.
- Nach jedem Sprint mit Codeänderungen wird eine Debug-APK gebaut, im Emulator installiert und die App gestartet.
- Tiefe Emulator-Smoke-Tests werden nur für Sprints gemacht, die UI, Playback, Downloads, Settings oder Navigation betreffen.
- P30-/Release-APKs werden nicht nach jedem Sprint gebaut, sondern nur bei explizitem Bedarf oder im Release-Readiness-Sprint.

## Severity-Modell

### Critical

Muss vor einem Release oder Push behoben werden.

Beispiele:
- Datenverlust durch Migration, Backup oder Restore.
- Playback-Service läuft falsch oder verliert Zustand.
- Download schreibt falsche Dateien oder markiert Status falsch.
- Crash durch Null-/Threading-/Lifecycle-Fehler.
- Main-Thread-Blocker bei Netzwerk, Datei- oder Datenbankarbeit.

### Warning

Sollte priorisiert behoben werden, kann aber in kleinen Schritten passieren.

Beispiele:
- UI/Data-Layer vermischt.
- unnötige Recomposition im Player oder in langen Listen.
- `SELECT *` in stark genutzten Listenpfaden.
- fehlende Tests für wichtige UseCases.
- zu große oder zu breit verantwortliche Dateien.

### Info

Nice-to-have oder Stilverbesserung.

Beispiele:
- einzelne lokale `.dp`-Werte.
- mögliche Type-Safe-Navigation-Migration.
- kleine Komponenten-Splits.
- Naming-/Organisationsthemen.

## Sprint 0 - Audit-Basis und Safety Net

### Ziel

Eine saubere, messbare Ausgangsbasis schaffen, bevor einzelne Bereiche verändert werden.

### Analyse

- Git-Status prüfen.
- Prüfen, ob alle aktuellen Änderungen lokal committed sind.
- Kotlin-Dateien über 20 KB prüfen.
- Aktuelle Gradle-Abhängigkeiten auf Alpha/Beta/RC prüfen.
- Aktuelle Build- und Testlage dokumentieren.

### Tests

- `git status --short`
- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`
- Optional: `./gradlew.bat :app:lintDebug`
- Emulator-Installation nur optional, weil Sprint 0 primär Baseline/Analyse ist.

### Akzeptanz

- Arbeitsbaum ist vor Start sauber oder bewusst dokumentiert.
- Compile und Unit-Tests laufen grün.
- Keine Kotlin-Datei über 20 KB oder Ausnahmen sind begründet.

## Sprint 1 - Playback, Media3 und Player-Lifecycle

### Warum zuerst?

Playback ist der Kern des Podcastplayers. Ein UI-Fehler ist ärgerlich, ein instabiler Player ist produktkritisch.

### Prüfen

- `PodcastPlaybackService`
- `AudioPlayerController`
- `PlayerViewModel`
- `PlaybackAnalyticsHandler`
- `PreparePlaybackUseCase`
- `SmoothedProgress`
- Fullplayer und Miniplayer State-Flows

### Audit-Fragen

- Laufen Media3-/Player-Interaktionen auf dem richtigen Dispatcher?
- Wird der Player-Zustand sauber wiederhergestellt?
- Werden Progress, Duration, Buffering und Error-State korrekt modelliert?
- Gibt es unnötige Recomposition durch hochfrequenten Progress-State?
- Werden Player-Events eindeutig über ViewModel/Controller geführt?
- Gibt es doppelte oder konkurrierende Quellen für Playback-State?

### Mögliche Fixes

- Hochfrequente Progress-Werte stärker in draw/layout phase lesen.
- Player-State klarer zwischen Domain/Controller/UI trennen.
- Error-State im Player userfreundlich und testbar abbilden.
- Analytics/Mark-as-played gegen doppelte Ausführung absichern.

### Tests

- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`
- APK im Emulator installieren und App starten.
- Emulator-Smoke-Test:
  - Episode starten,
  - Play/Pause,
  - Seek,
  - Fullplayer öffnen/schließen,
  - Miniplayer-Cover Detail-Toggle,
  - App in Hintergrund und zurück,
  - Wiedergabe läuft weiter oder pausiert wie erwartet.

### Akzeptanz

- Kein Playback-Regression.
- Keine falsche Thread-Nutzung erkennbar.
- Player UI bleibt flüssig.

## Sprint 2 - Room, Datenintegrität, History und Favorites

### Ziel

Sicherstellen, dass Podcast-, Episoden-, History-, Favoriten- und Download-Zustände korrekt gespeichert und wiederhergestellt werden.

### Prüfen

- `PodcastDao`
- `PodcastEntity`
- `EpisodeEntity`
- `EpisodeFts`
- `AppDatabase`
- `AppDatabaseMigrations`
- Favorite-/History-ViewModels und UseCases

### Audit-Fragen

- Gibt es Foreign Keys und passende Indizes für häufige Queries?
- Sind Multi-Table-Operationen mit `@Transaction` geschützt?
- Gibt es unnötige `SELECT *` in häufig gerenderten Listen?
- Sind Favorite/History-Zeitgruppen korrekt und testbar?
- Sind `datePlayed`, `favoriteAddedAt`, `favoriteTimestamp`, `pubDate` sauber definiert?
- Sind Migrationen vollständig und ohne destructive fallback?

### Mögliche Fixes

- Projektionen für Listenansichten einführen, wo `SELECT *` nicht nötig ist.
- Fehlende `@Transaction` bei konsistenzkritischen Operationen ergänzen.
- DAO-Queries für Downloads/History/Favorites gezielter machen.
- Tests für Zeitgruppen und Sortierungen ergänzen.

### Tests

- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`
- APK im Emulator installieren und App starten.
- Falls Emulator stabil:
  - `./gradlew.bat :app:connectedDebugAndroidTest`
- Spezifische Tests:
  - Favorites-Sortierung manuell vs. hinzugefügt,
  - History-Gruppen Heute/Gestern/Woche/Monat/Jahr/älter,
  - Downloadstatus-Reconciliation,
  - Migrationstest falls Schema betroffen.

### Akzeptanz

- Keine Datenverlustpfade erkennbar.
- History/Favorites bleiben korrekt sortiert.
- Room-Integrität bleibt stabil.

## Sprint 3 - Backup, Restore und Settings-Persistenz

### Ziel

Backup/Restore muss alle Einstellungen, Podcasts, Favorites und relevante Zustände korrekt sichern und wiederherstellen.

### Prüfen

- `PodcastBackupHelper`
- `BackupRepositoryImpl`
- `BackupModels`
- `UserPreferencesRepositoryImpl`
- `UserPreferenceKeys`
- `UserSettingsPreferencesMapper`
- `UpdateUserSettingsUseCase`
- Settings-Tests

### Audit-Fragen

- Sind alle `UserSettings` im Backup-Modell enthalten?
- Werden neue Optionen wie Gradient, transparente Cards/Rows, Bottom-Bar-Auto-Hide, MiniPlayer-Time-Overlay, Indicator-Offets korrekt gesichert?
- Gibt es Default-Werte für alte Backups?
- Wird fehlerhaftes Backup robust abgefangen?
- Verwendet ViewModel weiterhin `UiText` statt Context-Strings?

### Mögliche Fixes

- Fehlende Settings im Backup ergänzen.
- Mapper-Tests für alle Felder erweitern.
- Legacy-Backup-Fallback klarer testen.
- Import-Ergebnis userfreundlich modellieren.

### Tests

- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`
- APK im Emulator installieren und App starten.
- Spezifische Tests:
  - `PodcastBackupHelperParsingTest`
  - `UpdateUserSettingsUseCaseTest`
  - `SettingsViewModelTest`
- Emulator-Test:
  - Backup exportieren,
  - App-Daten zurücksetzen,
  - Backup importieren,
  - Settings prüfen:
    - Horizontal Offset,
    - Vertical Offset,
    - Gradient,
    - transparente Cards,
    - transparente Episode Rows,
    - Bottom-Bar-Clean-Mode,
    - Auto-Hide-Zeit,
    - Miniplayer-Time-Overlay.

### Akzeptanz

- Kein bekanntes Setting fehlt im Backup.
- Alte Backups bleiben importierbar.
- Importfehler crashen nicht.

## Sprint 4 - Netzwerk, RSS Sync und Downloads

### Ziel

RSS-Sync, iTunes-Suche, Streaming und Downloads sollen robust, effizient und offline-tolerant sein.

### Prüfen

- `NetworkModule`
- `PodcastService`
- `ItunesSearchApi`
- RSS Parser und Sync UseCases
- `DownloadWorker`
- `PodcastDownloader`
- `FeedUpdateWorker`
- `LibraryCleanupWorker`

### Audit-Fragen

- Sind OkHttp-Timeouts gesetzt?
- Gibt es HTTP-Cache?
- Nutzt Audio-/Dateidownload `@Streaming`?
- Werden Download-Abbrüche und Netzwerkfehler korrekt gespeichert?
- Wird Mobile/WiFi-Traffic korrekt für Statistik erfasst?
- Sind ETag/Last-Modified möglich oder bewusst nicht implementiert?
- Gibt es Retry-/Backoff-Verhalten für Background Work?

### Mögliche Fixes

- Conditional RSS Fetching prüfen und ggf. planen.
- Download-Statusmaschine härten.
- Fehlerfälle im Worker klarer differenzieren.
- Tests mit MockWebServer für Timeout/Fehler/Redirect ergänzen.

### Tests

- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`
- APK im Emulator installieren und App starten.
- MockWebServer-Tests:
  - RSS 200,
  - RSS malformed,
  - Timeout,
  - 404/500,
  - Audio Download Stream.
- Emulator-Test:
  - Podcast hinzufügen,
  - Feed refresh,
  - Episode herunterladen,
  - Offline abspielen,
  - Download löschen/cleanup prüfen.

### Akzeptanz

- Netzwerkfehler führen zu sichtbaren, sicheren Zuständen.
- Downloads verlieren keine Statuskonsistenz.
- Große Audiodateien werden nicht in den Speicher geladen.

## Sprint 5 - Compose Performance: Player, Listen, Settings

### Ziel

Unnötige Recompositions und Layout-Arbeit in Hot Paths reduzieren.

### Prüfen

- Miniplayer,
- Fullplayer,
- Custom Progress Bar,
- Home Grid/List,
- Favorites,
- History,
- Downloads,
- Settings Slider Cards.

### Audit-Fragen

- Werden Lazy-Listen mit stabilen Keys gerendert?
- Werden Progress-/Timer-Werte unnötig im Composable Body gelesen?
- Sind teure Formatierungen mit `remember` geschützt?
- Gibt es instabile Lambdas in tiefen Listen?
- Sind UI-State-Modelle immutable genug?
- Gibt es dynamische Textgrößen oder Layoutsprünge?

### Mögliche Fixes

- `remember`/`derivedStateOf` gezielt ergänzen.
- List Item Models leichter machen.
- Progress-Bar-Reads in draw/layout phase verschieben.
- Stable Keys konsequent prüfen.

### Tests

- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`
- APK im Emulator installieren und App starten.
- Emulator-Manual:
  - lange Home-Liste scrollen,
  - History/Favorites scrollen,
  - Player laufen lassen,
  - Settings Slider bewegen,
  - One-Hand-Mode prüfen.
- Optional Android Studio Layout Inspector/Recomposition Counts.

### Akzeptanz

- Kein sichtbares Ruckeln in Player und Listen.
- Progress läuft stabil.
- Scrollpositionen und Sortierungen bleiben korrekt.

## Sprint 6 - Architektur-Grenzen und Domain-Modelle

### Ziel

UI, Domain und Data weiter entkoppeln, ohne Overengineering.

### Prüfen

- Imports von `ui.*` in `data.*` oder `domain.*`.
- UI-Nutzung von Room Entities.
- DTO/Entity-Leaks in ViewModels.
- UseCase-Granularität.
- Contract-Dateien für Screens.

### Bekannter Punkt

Es gibt bereits eine dokumentierte Boundary-Schwäche: `UserPreferencesRepositoryImpl` importiert UI-Enums wie `AppTheme`, `AppColor`, `BufferMode`. Das ist kein akuter Bug, aber architektonisch unsauber.

### Mögliche Fixes

- `AppTheme`, `AppColor`, `BufferMode` langfristig nach `domain.model` verschieben.
- UI-Models klar von Data-Entities trennen.
- Kleine Contract-Dateien behalten, aber keine UseCase-Klassenexplosion erzeugen.

### Tests

- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:testDebugUnitTest`
- Wenn Code geändert wurde:
  - `./gradlew.bat :app:assembleDebug`
  - APK im Emulator installieren und App starten.
- Import-Audit:
  - `rg "import com.example.pocastcloni.ui" app/src/main/java/com/example/pocastcloni/data app/src/main/java/com/example/pocastcloni/domain`
  - `rg "import com.example.pocastcloni.data.local" app/src/main/java/com/example/pocastcloni/ui`

### Akzeptanz

- Keine neuen Layer-Verletzungen.
- Bestehende Boundary-Verletzungen sind entweder behoben oder dokumentiert.
- Keine künstliche UseCase-Aufblähung.

## Sprint 7 - Navigation und App-Shell

### Ziel

Navigation stabil und verständlich halten.

### Prüfen

- `Screen`
- `MainNavigation`
- `AppBottomNavigation`
- `MainActivity`
- Detail-Toggle vom Miniplayer
- Backstack-Verhalten bei Home/Favorites/History/PodcastDetail

### Audit-Fragen

- Gibt es Navigation durch recomposition statt User/Event?
- Sind Route-Argumente korrekt encoded/decoded?
- Ist String-basierte Navigation aktuell akzeptabel?
- Wäre Navigation 2.8 Type-Safe Navigation sinnvoll oder zu viel Migration?
- Sind Swipe-Gesten und Bottom-Bar-Gesten konfliktarm?

### Mögliche Fixes

- Navigation-Events stärker kapseln.
- Route-Konstanten und Argument-Namen zentral halten.
- Type-Safe Navigation nur planen, wenn Dependencies stabil aktualisiert werden.

### Tests

- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`
- APK im Emulator installieren und App starten.
- Emulator-Test:
  - Bottom-Bar Navigation,
  - Main-Screen Swipe,
  - Detail öffnen,
  - Miniplayer Cover Toggle,
  - Back Button,
  - Search suppresses Miniplayer.

### Akzeptanz

- Backstack ist vorhersehbar.
- Keine versehentlichen Screenwechsel durch Slider oder Scrolls.

## Sprint 8 - Release Readiness und GitHub-Vorbereitung

### Ziel

Prüfen, ob Code lokal release- und push-ready ist.

### Prüfen

- Git dirty status.
- Lokale Commits.
- Remote-Konfiguration.
- Build/Tests.
- APK-Signing für P30-Test.

### Tests

- `git status --short`
- `git log -3 --oneline`
- `git remote -v`
- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`
- Final debug APK im Emulator installieren und App starten.
- Optional:
  - `./gradlew.bat :app:assembleRelease`
  - `apksigner verify`

### Akzeptanz

- Git ist sauber.
- Alle geplanten lokalen Commits existieren.
- Keine untracked Source-Dateien.
- Build und Tests grün.
- Remote ist bewusst vorhanden oder bewusst nicht vorhanden.

## Empfohlene Reihenfolge

1. Sprint 0: Safety Net.
2. Sprint 1: Playback/Media3.
3. Sprint 3: Backup/Settings-Persistenz.
4. Sprint 2: Room/History/Favorites.
5. Sprint 4: Netzwerk/Downloads.
6. Sprint 5: Compose Performance.
7. Sprint 6: Architektur-Grenzen.
8. Sprint 7: Navigation.
9. Sprint 8: Release/GitHub Readiness.

## Minimaler Audit-Befehlssatz pro Sprint

```powershell
git status --short
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
Get-ChildItem -Path app\src -Recurse -Filter *.kt | Where-Object { $_.Length -gt 20KB }
```

## Standard Emulator Smoke pro Code-Sprint

Nach jedem Sprint mit Codeänderungen:

```powershell
.\gradlew.bat :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am start -n com.example.pocastcloni/.ui.main.MainActivity
```

Basis-Erwartung:

- Installation erfolgreich.
- App startet ohne Crash.
- Main screen wird geöffnet.

Zusätzliche sprintabhängige Checks:

- Playback-Sprint: Miniplayer/Fullplayer öffnen, Play/Pause/Seek prüfen.
- Settings-/Backup-Sprint: Settings öffnen, Tabs wechseln, relevante Optionen bewegen/toggeln.
- Room-/History-/Favorites-Sprint: History/Favorites öffnen und Sortierung/Gruppen grob prüfen.
- Download-/Netzwerk-Sprint: Search/Add/Downloads öffnen, Statusanzeigen prüfen.
- Navigation-Sprint: Bottom-Bar, Main-Swipe, Backstack und Miniplayer-Detail-Toggle prüfen.

## Abschlusskriterien Gesamtplan

- Keine bekannten Critical-Issues.
- Alle sprintbezogenen Tests grün oder begründete Ausnahme dokumentiert.
- Kein Kotlin-File über 20 KB.
- Backup/Restore ist vollständig getestet.
- Playback und Downloads sind emulator-getestet.
- Git ist sauber und lokal committed.
