# Plan: Four Tabs for Settings

## Ziel

Die Settings sollen von einer langen, schwer zu ueberblickenden Scroll-Liste in vier klare Tabs aufgeteilt werden.

Vorgeschlagene Tabs:

```text
Design | Playback | Sync & Storage | Data
```

Das Ziel ist bessere Orientierung auf dem Phone, weniger langes Suchen durch Scrollen und eine klare thematische Gruppierung der vorhandenen Optionen.

## Gewuenschtes Verhalten

- Oben in den Settings erscheint unter der TopAppBar eine Tab-Leiste.
- Es gibt vier Tabs:
  - `Design`
  - `Playback`
  - `Sync & Storage`
  - `Data`
- Jeder Tab zeigt nur die zugehoerigen Settings.
- Der aktuell aktive Tab bleibt waehrend der Session erhalten, auch bei Recomposition.
- Settings-Werte und bestehende Aktionen bleiben unveraendert.
- Backup/Restore, Reset, Statistics und Smart Sections funktionieren weiter.
- Bottom-Padding bei sichtbarem Miniplayer bleibt korrekt.
- Keine neue Persistenz fuer den aktiven Tab noetig.

## Aktueller Stand

### SettingsScreen

Der Screen liegt hier:

```text
app/src/main/java/com/example/pocastcloni/ui/settings/SettingsScreen.kt
```

`SettingsScreen` rendert aktuell:

```text
Scaffold -> TopAppBar -> Box -> SettingsListContent
```

Die TopAppBar ist bereits vorhanden. Eine Tab-Leiste kann entweder im `Scaffold`-Content oberhalb der Liste oder als Teil einer `Column` unterhalb der TopAppBar eingebaut werden.

### SettingsContent

Die aktuelle Liste liegt hier:

```text
app/src/main/java/com/example/pocastcloni/ui/settings/SettingsContent.kt
```

Aktuell gibt es eine einzelne `LazyColumn` mit diesen Hauptitems:

```text
add_podcast
general_settings
statistics
backup
reset_zone
```

Innerhalb von `general_settings` werden viele Sections hintereinander gerendert:

```text
Appearance
Automation
Downloads
Download Location
Cleanup
Indicator
Interface
Playback
```

### SettingsSections

Die meisten Inhalte sind bereits sauber als Composables getrennt:

```text
SectionAddPodcast
SectionAppearance
SectionIndicator
SectionAutomation
SectionDownloads
SectionDownloadLocation
SectionCleanup
SectionInterface
SectionPlayback
SectionStatistics
SectionBackup
```

Das ist eine gute Grundlage. Die Umsetzung sollte diese Sections wiederverwenden, statt sie neu zu bauen.

## Zielstruktur der Tabs

### Tab 1: Design

Inhalt:

- `SectionAppearance`
- `SectionIndicator`
- `SectionInterface`

Begruendung:

- Theme, App-Farbe, Notification Dot, Layout, Grid, Bar-Hoehen und One-Handed Mode sind visuelle bzw. ergonomische Einstellungen.

### Tab 2: Playback

Inhalt:

- `SectionPlayback`

Optional spaeter:

- Weitere Player-spezifische Optionen koennen hier landen.

Begruendung:

- Mark-as-played, Buffer Mode und Mini player time overlay betreffen Wiedergabeverhalten bzw. Player-Anzeige.

### Tab 3: Sync & Storage

Inhalt:

- `SectionAddPodcast`
- `SectionAutomation`
- `SectionDownloads`
- `SectionDownloadLocation`
- `SectionCleanup`

Begruendung:

- RSS Import, Feed-Aktualisierung, Downloads, Speicherort und Auto Cleanup gehoeren logisch zu Datenfluss, Synchronisierung und Speicherverbrauch.

### Tab 4: Data

Inhalt:

- `SettingsStatisticsSectionSmart`
- `SectionBackup`
- Reset-Zone

Begruendung:

- Statistiken, Backup/Restore und Reset sind Datenverwaltung bzw. Wartung.

## UX-Entscheidung

### Empfohlene Tab-Komponente

Empfehlung:

```kotlin
ScrollableTabRow
```

Begruendung:

- Vier Labels koennen auf kleinen Displays eng werden.
- `Sync & Storage` ist relativ lang.
- `ScrollableTabRow` verhindert Textquetschen.

Alternative:

```kotlin
PrimaryTabRow
```

Nur sinnvoll, wenn die Labels kurz gehalten werden, z. B.:

```text
Design | Playback | Sync | Data
```

Empfehlung fuer diese App:

- Sichtbarer Text: `Design`, `Playback`, `Sync`, `Data`
- Interner oder ContentDescription-Text: `Sync & Storage`

Damit passen die Tabs besser auf mobile Breite.

## Sprint 1: Tab-Modell und Struktur vorbereiten

### Ziel

Eine klare, kleine Struktur fuer die vier Settings-Tabs schaffen, ohne Verhalten zu veraendern.

### Umsetzung

1. In `SettingsContent.kt` ein internes Tab-Modell anlegen:

```kotlin
private enum class SettingsTab {
    DESIGN,
    PLAYBACK,
    SYNC_STORAGE,
    DATA
}
```

2. Kleine Helper fuer Label-Strings vorsehen:

```kotlin
SettingsTab.labelRes
```

3. Neue String-Ressourcen ergaenzen:

```text
settings_tab_design
settings_tab_playback
settings_tab_sync
settings_tab_sync_storage
settings_tab_data
```

4. Bestehende Section-Funktionen noch nicht verschieben.

### Tests

- Keine Unit-Tests zwingend noetig.
- Compile-Check reicht fuer diesen Sprint.

### Akzeptanzkriterien

- Projekt kompiliert.
- Noch keine sichtbare Verhaltensaenderung, falls Sprint isoliert umgesetzt wird.
- Tab-Namen sind zentral und nicht als rohe Strings im UI-Code verstreut.

## Sprint 2: SettingsListContent auf Tabs umbauen

### Ziel

Die lange Settings-Liste wird durch eine Tab-Leiste plus tab-spezifische `LazyColumn` ersetzt.

### Umsetzung

1. `SettingsListContent` erweitert intern um:

```kotlin
var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.DESIGN) }
```

2. Layout von `SettingsListContent`:

```text
Column(fillMaxSize)
  ScrollableTabRow
  LazyColumn(tab-specific content)
```

3. Bottom-Padding bleibt erhalten:

```kotlin
bottomPadding =
    if (isPlayerVisible) {
        (settings.navBarHeight + settings.progressBarHeight).dp + Dimens.PaddingMedium
    } else {
        Dimens.PaddingMedium
    }
```

4. `LazyColumn` bekommt weiterhin:

```kotlin
contentPadding = PaddingValues(...)
verticalArrangement = Arrangement.spacedBy(Dimens.PaddingLarge)
```

5. Tab-Inhalt aus `when (selectedTab)` rendern:

```kotlin
when (selectedTab) {
    SettingsTab.DESIGN -> DesignSettingsContent(...)
    SettingsTab.PLAYBACK -> PlaybackSettingsContent(...)
    SettingsTab.SYNC_STORAGE -> SyncStorageSettingsContent(...)
    SettingsTab.DATA -> DataSettingsContent(...)
}
```

### Wichtige Detailentscheidung

Die bisherige `GeneralSettingsContent` ist zu breit geworden. Empfehlung:

- `GeneralSettingsContent` aufloesen oder nur noch als Wrapper entfernen.
- Stattdessen kleine tab-spezifische private Composables bauen:
  - `DesignSettingsContent`
  - `PlaybackSettingsContent`
  - `SyncStorageSettingsContent`
  - `DataSettingsContent`

Diese Composables nehmen wie bisher primitive Werte und Callbacks bzw. `settings` + `onEvent`, damit die Aenderung klein bleibt.

### Tests

- Compile-Check.
- Manuelle UI-Pruefung:
  - Settings oeffnet auf Tab `Design`.
  - Tabs sind sichtbar.
  - Tab-Wechsel zeigt andere Inhalte.
  - Scrollposition pro Tab muss nicht zwingend erhalten bleiben.

### Akzeptanzkriterien

- Es gibt eine sichtbare Tab-Leiste.
- Jeder Tab zeigt nur seine eigenen Sections.
- Kein Settings-Wert geht verloren.
- Bottom-Padding mit Miniplayer bleibt korrekt.

## Sprint 3: Sections sauber auf die vier Tabs verteilen

### Ziel

Die Inhalte sind logisch gruppiert und behalten ihre bestehenden Aktionen.

### Umsetzung

1. `DesignSettingsContent`
   - rendert `SectionAppearance`
   - rendert `SectionIndicator`
   - rendert `SectionInterface`
   - nutzt die bestehenden Actions:
     - `SetAppTheme`
     - `SetAppColor`
     - `SetColorStrength`
     - `SetIndicatorColor`
     - `SetIndicatorSize`
     - `SetIndicatorBorderWidth`
     - `SetIndicatorXOffset`
     - `SetIndicatorYOffset`
     - `SetLayoutMode`
     - `SetGridSize`
     - `ToggleShowGridTitles`
     - `ToggleOneHandedMode`
     - `SetProgressBarHeight`
     - `SetNavBarHeight`
     - `ToggleConfirmDelete`

2. `PlaybackSettingsContent`
   - rendert `SectionPlayback`
   - nutzt:
     - `SetMarkPlayedDuration`
     - `ToggleMiniPlayerTimeOverlay`
     - `SetBufferSettings`

3. `SyncStorageSettingsContent`
   - rendert `SectionAddPodcast`
   - rendert `SectionAutomation`
   - rendert `SectionDownloads`
   - rendert `SectionDownloadLocation`
   - rendert `SectionCleanup`
   - nutzt:
     - `ToggleAutoRefreshOnStart`
     - `ToggleBackgroundCheck`
     - `SetBackgroundCheckInterval`
     - `SetFeedUpdateMode`
     - `SetAutoDownloadLimit`
     - `StartManualDownload`
     - `ToggleSaveToDownloadsFolder`
     - `ToggleAutoCleanup`
     - `SetCleanupKeepLimit`
     - `SetCleanupIntervalHours`

4. `DataSettingsContent`
   - rendert `SettingsStatisticsSectionSmart`
   - rendert `SectionBackup`
   - rendert Reset-Zone
   - nutzt:
     - `onExportClick`
     - `onImportClick`
     - `OnResetClicked`

### Tests

- Manuelle Mapping-Pruefung anhand UI:
  - Design Tab enthaelt Theme und Notification Dot.
  - Playback Tab enthaelt Mini player time overlay.
  - Sync Tab enthaelt Add Podcast, Automation, Downloads und Cleanup.
  - Data Tab enthaelt Statistics, Backup/Restore und Reset.

### Akzeptanzkriterien

- Alle vorher vorhandenen Settings sind weiterhin erreichbar.
- Keine Section ist doppelt vorhanden.
- Keine Section ist verschwunden.
- Reset bleibt bewusst im Data Tab und nicht auf jedem Tab sichtbar.

## Sprint 4: State, Navigation und Accessibility

### Ziel

Tabs verhalten sich stabil und sind fuer Screenreader sowie Tests gut auffindbar.

### Umsetzung

1. `rememberSaveable` fuer aktiven Tab verwenden.
2. Tabs mit stabilem Index rendern:

```kotlin
SettingsTab.entries.forEachIndexed { index, tab -> ... }
```

3. ContentDescription fuer `Sync` optional vollstaendig machen:

```text
Sync & Storage
```

4. Falls Compose Semantics sinnvoll:
   - Tab-Text bleibt sichtbar.
   - Kein extra TestTag zwingend noetig.

5. Keine Persistenz des aktiven Tabs:
   - App-Neustart beginnt wieder bei `Design`.
   - Rotation sollte wegen `rememberSaveable` den Tab behalten.

### Tests

- Manuell:
  - Tab auswaehlen.
  - Screen drehen, falls Emulator/Device einfach verfuegbar.
  - Pruefen, ob aktiver Tab bleibt.

- Optional UI-Test:
  - Settings oeffnen.
  - Auf `Playback` klicken.
  - `Mini player time overlay` ist sichtbar.
  - Auf `Data` klicken.
  - `Backup & Restore` ist sichtbar.

### Akzeptanzkriterien

- Tab-Auswahl ist stabil bei Recomposition.
- Tab-Texte sind klar lesbar.
- Kein horizontaler Text-Overflow auf kleinen Displays.

## Sprint 5: Tests und Regression

### Ziel

Sicherstellen, dass der Umbau keine Settings-Funktion kaputt macht.

### Unit Tests

Bestehende Tests sollten weiterlaufen:

```powershell
./gradlew test
```

Wichtig:

- `SettingsViewModelTest` muss unveraendert oder mit minimalen Anpassungen bestehen.
- UseCase-Tests bleiben unveraendert.
- Backup-Tests bleiben unveraendert.

Da der Umbau primaer UI-Komposition betrifft, sind Unit-Tests nur begrenzt aussagekraeftig.

### Lint

```powershell
./gradlew lint
```

Ziel:

- Keine neuen Lint-Fehler.
- Keine ungenutzten Imports.

### Build

```powershell
./gradlew assembleDebug
```

Ziel:

- Debug APK baut erfolgreich.

### Emulator-Test

1. APK installieren:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

2. App starten.
3. Settings oeffnen.
4. Sichtpruefung:
   - Vier Tabs sichtbar.
   - `Design` initial aktiv.
   - `Playback` zeigt Playback-Optionen.
   - `Sync` zeigt Add Podcast/Automation/Downloads/Cleanup.
   - `Data` zeigt Statistics/Backup/Reset.
5. Jede Tab-Liste scrollen.
6. Mindestens einen Switch in einem Nicht-Design-Tab toggeln und pruefen, ob die App nicht crasht.

### Akzeptanzkriterien

- `./gradlew test` erfolgreich.
- `./gradlew lint` erfolgreich.
- `./gradlew assembleDebug` erfolgreich.
- Emulator-Sichtpruefung bestanden.

## Sprint 6: Finalisierung

### Ziel

Die Aenderung ist nachvollziehbar, klein genug reviewbar und lokal gesichert.

### Umsetzung

1. Git-Diff pruefen:

```powershell
git diff --stat
git diff --check
```

2. Sicherstellen:
   - Keine unnoetigen Architektur-Aenderungen.
   - Keine Persistenz-Aenderung fuer Tab-Auswahl.
   - Keine bestehenden Strings geloescht.

3. Lokalen Commit erstellen, falls Umsetzung gewuenscht ist:

```text
Split settings into four tabs
```

4. Kein Push.

## Risiken und Gegenmassnahmen

### Risiko: Tabs sind auf kleinen Displays zu eng

Gegenmassnahme:

- `ScrollableTabRow` verwenden.
- Sichtbares Label `Sync` statt `Sync & Storage`.

### Risiko: Nutzer findet Add Podcast nicht mehr

Gegenmassnahme:

- Add Podcast in `Sync` relativ weit oben platzieren.
- Tab-Name `Sync` bzw. `Sync & Storage` macht die Zuordnung plausibel.

### Risiko: Data Tab wirkt wie gefaehrlicher Bereich

Gegenmassnahme:

- Reset bleibt ganz unten.
- Backup/Restore bleibt vor Reset.
- Reset-Zone behaelt Error-Farbe.

### Risiko: Zu viele private Composables in `SettingsContent.kt`

Gegenmassnahme:

- Nur kleine tab-spezifische Wrapper bauen.
- Bestehende Section-Composables in `SettingsSections.kt` weiterverwenden.
- Keine neue Datei, solange `SettingsContent.kt` noch gut lesbar bleibt.

### Risiko: One-Handed Mode und Tabs oben widersprechen sich

Gegenmassnahme:

- Tabs trotzdem oben lassen, weil Settings selbst bereits eine TopBar oben hat.
- Keine Bottom-Tab-Leiste in Settings einfuehren, weil dort schon die App-Navigation/Miniplayer-Reserve sitzt.

### Risiko: Scrollposition pro Tab geht verloren

Gegenmassnahme:

- Fuer erste Umsetzung akzeptieren.
- Falls es stoert, spaeter pro Tab einen eigenen `LazyListState` speichern.

## Nicht-Ziele

- Keine neue Settings-Suche.
- Keine Persistenz des aktiven Tabs.
- Keine Aenderung an den eigentlichen Settings-Werten.
- Keine Aenderung an Backup-Dateiformat.
- Keine neue Navigation-Route pro Settings-Tab.
- Keine Umbenennung der bestehenden Sections ausser Tab-Labels.

## Empfohlene Implementierungsreihenfolge

1. Tab enum und String-Ressourcen.
2. `SettingsListContent` in `Column + ScrollableTabRow + LazyColumn` umbauen.
3. Vier tab-spezifische Content-Composables erstellen.
4. Bestehende Sections in die richtigen Tabs verschieben.
5. Build/Test.
6. Emulator-Sichtpruefung.
7. Lokaler Commit.

## Definition of Done

- Settings haben vier Tabs.
- Alle bisherigen Settings sind erreichbar.
- Tab-Gruppierung entspricht:
  - Design
  - Playback
  - Sync
  - Data
- Keine Settings-Action ist kaputt.
- Bottom-Padding mit Miniplayer bleibt korrekt.
- Tests und Build sind erfolgreich.
- Emulator-Sichtpruefung ist bestanden.
- Git ist nach lokalem Commit sauber.
