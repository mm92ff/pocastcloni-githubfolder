# Plan: Mini Player Timeline Time Overlay Setting

## Ziel

Unter `Settings` soll eine neue Option entstehen, mit der im Miniplayer die aktuelle Abspielzeit links und die Gesamtdauer rechts direkt auf der Timeline angezeigt werden kann.

Gewuenschtes Layout im Miniplayer:

```text
00:42                                      38:15
================ Timeline / Progress ================
```

Wichtig: Die Zeiten sollen als Overlay auf der Timeline liegen, nicht unterhalb der Timeline. Der Fullplayer bleibt unveraendert und zeigt seine Zeiten weiterhin unter der Progressbar.

## Gewuenschtes Verhalten

- In den Settings gibt es einen neuen Schalter fuer die Miniplayer-Zeitanzeige.
- Standardwert ist `false`, damit bestehendes Verhalten fuer alle Nutzer unveraendert bleibt.
- Wenn die Option deaktiviert ist:
  - Der Miniplayer sieht aus wie bisher.
  - Keine zusaetzlichen Zeitlabels sind sichtbar.
- Wenn die Option aktiviert ist:
  - Links auf der Miniplayer-Timeline steht die aktuelle Abspielzeit.
  - Rechts auf der Miniplayer-Timeline steht die Gesamtdauer der Episode.
  - Die Anzeige nutzt dieselbe Zeitformatierung wie der Fullplayer.
  - Taps und Drag-Seeking auf der Timeline funktionieren weiter.
- Die Einstellung wird in Backup/Restore mitgenommen.
- Alte Backups ohne diese Einstellung bleiben kompatibel.

## Aktueller Stand

### Player

Der Miniplayer rendert seine Progressbar in:

```text
app/src/main/java/com/example/pocastcloni/ui/player/MiniPlayer.kt
```

Die konkrete Progressbar kommt aus:

```text
app/src/main/java/com/example/pocastcloni/ui/player/CustomProgressBar.kt
```

`CustomProgressBar` ist Canvas-basiert und wird auch vom Fullplayer genutzt. Sie sollte nicht global geaendert werden, weil das sonst Fullplayer und andere Call-Sites unnoetig beruehren wuerde.

Der Fullplayer zeigt Zeitlabels bereits in:

```text
app/src/main/java/com/example/pocastcloni/ui/player/FullPlayerProgress.kt
```

Die Zeitformatierung existiert bereits zentral:

```text
app/src/main/java/com/example/pocastcloni/util/TimeUtils.kt
```

### Settings und Persistenz

Die App speichert Settings ueber `UserSettings` und DataStore:

```text
app/src/main/java/com/example/pocastcloni/domain/repository/UserSettings.kt
app/src/main/java/com/example/pocastcloni/domain/repository/UserPreferencesRepository.kt
app/src/main/java/com/example/pocastcloni/data/repository/UserPreferencesRepositoryImpl.kt
```

Die Settings-UI laeuft ueber:

```text
app/src/main/java/com/example/pocastcloni/ui/settings/SettingsContract.kt
app/src/main/java/com/example/pocastcloni/ui/settings/SettingsViewModel.kt
app/src/main/java/com/example/pocastcloni/ui/settings/SettingsContent.kt
app/src/main/java/com/example/pocastcloni/ui/settings/SettingsSections.kt
```

Die UseCase-Aktion laeuft ueber:

```text
app/src/main/java/com/example/pocastcloni/domain/usecase/app/UpdateUserSettingAction.kt
app/src/main/java/com/example/pocastcloni/domain/usecase/app/UpdateUserSettingsUseCase.kt
```

### Player-Datenfluss

Der Wert muss voraussichtlich durch diese Kette gereicht werden:

```text
MainActivity -> PlayerContainer -> ExpandablePlayer -> MiniPlayer
```

Aktuell wird `progressBarHeight` bereits so durchgereicht. Die neue Option kann dem gleichen Muster folgen.

## Vorgeschlagene Einstellung

Interner Name:

```kotlin
showMiniPlayerTimeOverlay: Boolean
```

DataStore-Key:

```kotlin
KEY_SHOW_MINI_PLAYER_TIME_OVERLAY = "show_mini_player_time_overlay"
```

Default:

```kotlin
DEFAULT_SHOW_MINI_PLAYER_TIME_OVERLAY = false
```

Settings-Label:

```text
Mini player time overlay
```

Settings-Subtitle:

```text
Show elapsed and total time on the mini player timeline
```

Empfohlene Platzierung:

```text
Settings -> Playback
```

Begruendung: Die Option betrifft die Anzeige des laufenden Playbacks, nicht das allgemeine Layout wie Grid, Nav-Bar oder One-Handed Mode.

## Sprint 1: Settings-Modell und Persistenz

### Ziel

Die neue Einstellung existiert als persistierter Wert, kann gelesen, geschrieben, exportiert und wiederhergestellt werden.

### Umsetzung

1. `Constants.kt`
   - neuen DataStore-Key hinzufuegen,
   - neuen Default-Wert hinzufuegen.

2. `UserSettings.kt`
   - neues Feld `showMiniPlayerTimeOverlay: Boolean` mit Default hinzufuegen.

3. `UserPreferencesRepository.kt`
   - neue Methode `updateShowMiniPlayerTimeOverlay(enabled: Boolean)` hinzufuegen.

4. `UserPreferencesRepositoryImpl.kt`
   - Wert aus DataStore in `userSettingsFlow` lesen,
   - Update-Methode implementieren,
   - Wert in `restoreSettings(settings)` schreiben.

5. Backup/Restore
   - Keine separate Backup-Struktur noetig, weil `BackupData.settings` bereits `UserSettings` serialisiert.
   - Sicherstellen, dass alte Backups ohne Feld wegen Default-Wert weiter importierbar bleiben.

### Tests

- Unit-Test fuer Backup-Parsing optional erweitern:
  - Neues Backup mit `showMiniPlayerTimeOverlay=true` wird korrekt gelesen.
  - Altes Backup ohne Feld bleibt gueltig.
- Falls DataStore-Repository-Tests existieren oder leicht ergaenzbar sind:
  - Update-Methode persistiert `true`.
  - `restoreSettings()` stellt `true` wieder her.

### Akzeptanzkriterien

- App kompiliert.
- Default ist deaktiviert.
- Alte Backups bleiben kompatibel.
- Neue Backups enthalten die Option automatisch im Settings-Block.

## Sprint 2: Settings-UI und UseCase-Durchleitung

### Ziel

Der Nutzer kann die Option in den Settings ein- und ausschalten.

### Umsetzung

1. `UpdateUserSettingAction.kt`
   - neue Action `ToggleMiniPlayerTimeOverlay(enabled: Boolean)` hinzufuegen.

2. `UpdateUserSettingsUseCase.kt`
   - neue Action auf Repository-Methode mappen.
   - Kein Worker-Side-Effect noetig.
   - Keine Debounce-Logik noetig, weil es ein Switch ist.

3. `SettingsContract.kt`
   - Feld in `SettingsUiState.Success` aufnehmen.

4. `SettingsViewModel.kt`
   - `UserSettings.toUiState()` um das Feld erweitern.

5. `SettingsContent.kt`
   - Feld an `SectionPlayback` uebergeben.
   - Callback fuer Toggle anlegen.

6. `SettingsSections.kt`
   - In `SectionPlayback` einen `SettingsSwitchCard` einfuegen.
   - Platzierung bevorzugt nach `Mark as played after` und vor `Buffer Mode`, weil es eine sichtbare Playback-Anzeige betrifft.

7. `strings.xml`
   - Label und Subtitle hinzufuegen.

### Tests

- `SettingsViewModelTest`
  - prueft Mapping `UserSettings(showMiniPlayerTimeOverlay = true)` -> `SettingsUiState.Success.showMiniPlayerTimeOverlay == true`.
  - prueft, dass Toggle-Action an `UpdateUserSettingsUseCase` weitergegeben wird.

- `UpdateUserSettingsUseCaseTest`
  - prueft, dass `ToggleMiniPlayerTimeOverlay(true)` `repository.updateShowMiniPlayerTimeOverlay(true)` aufruft.
  - prueft optional, dass kein Background Worker getriggert wird.

### Akzeptanzkriterien

- Settings-Switch ist sichtbar.
- Toggle schreibt den Wert.
- Toggle ist sofort wirksam, ohne App-Neustart.
- Keine bestehende Settings-Option verliert Verhalten.

## Sprint 3: MiniPlayer Overlay Rendering

### Ziel

Der Miniplayer zeigt bei aktivierter Option die Zeiten direkt auf der Timeline an.

### Umsetzung

1. `MainActivity.kt`
   - `uiState.userSettings.showMiniPlayerTimeOverlay` an `PlayerContainer` uebergeben.

2. `PlayerContainer.kt`
   - neuen Boolean-Parameter annehmen und an `ExpandablePlayer` weiterreichen.

3. `ExpandablePlayer.kt`
   - Parameter an `MiniPlayer` weiterreichen.
   - Fullplayer bleibt unveraendert.

4. `MiniPlayer.kt`
   - `MiniPlayer` und `MiniPlayerProgressBar` um `showTimeOverlay` erweitern.
   - Bestehende `CustomProgressBar` in eine `Box` legen.
   - Bei `showTimeOverlay == true` eine `Row` mit zwei `Text`-Elementen darueberlegen.
   - Links aktuelle Position, rechts Dauer anzeigen.
   - `formatTime()` aus `TimeUtils.kt` verwenden.
   - Zeitwerte auf Sekundenebene sammeln, damit das Overlay nicht mit 60 FPS recomposed.
   - Overlay nur anzeigen, wenn `durationMs > 0`.

### UI-Details

- Overlay darf die Hoehe des Miniplayers nicht vergroessern.
- Text soll innerhalb der Timeline bleiben.
- Textfarbe muss auf Track und Progress lesbar sein.
- Empfohlen:
  - `MaterialTheme.typography.labelSmall`
  - `maxLines = 1`
  - `softWrap = false`
  - horizontaler Innenabstand
  - dezenter Shadow oder halbtransparenter Scrim, falls Kontrast sonst schwach ist.

### Touch/Seek-Verhalten

- Das Overlay darf Tap-Seek und Drag-Seek nicht blockieren.
- Text-Elemente bekommen keine `clickable`- oder `pointerInput`-Modifier.
- Die bestehende `CustomProgressBar` bleibt der einzige Pointer-Input-Pfad.

### Tests

- Manueller Emulator-Test:
  - Option aus: keine Zeitlabels auf Miniplayer-Timeline.
  - Option an: linke und rechte Zeit sichtbar.
  - Tap auf Timeline seekt weiterhin.
  - Drag auf Timeline seekt weiterhin.
  - Miniplayer expandiert weiterhin bei Klick auf Karte.
  - Fullplayer zeigt Zeitlabels weiterhin unter der Timeline.

- Optional Compose/UI-Test, falls mit vertretbarem Aufwand:
  - Miniplayer mit aktiviertem Overlay rendert beide Zeittexte.
  - Miniplayer mit deaktiviertem Overlay rendert sie nicht.

### Akzeptanzkriterien

- Overlay ist nur im Miniplayer sichtbar.
- Fullplayer bleibt unveraendert.
- Kein Layout-Sprung beim Aktivieren.
- Kein Absturz bei unbekannter Dauer.
- Seeking funktioniert unveraendert.

## Sprint 4: Regression, Build und APK-Pruefung

### Ziel

Die Aenderung ist technisch und visuell verifiziert.

### Tests und Checks

1. Unit Tests:

```powershell
./gradlew test
```

2. Lint, falls Zeit und Umgebung passen:

```powershell
./gradlew lint
```

3. Debug APK bauen:

```powershell
./gradlew assembleDebug
```

4. Emulator installieren:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

5. Manuelle Sichtpruefung im Emulator:
   - Settings oeffnen.
   - Playback-Section finden.
   - Option aktivieren.
   - Episode starten.
   - Miniplayer kontrollieren.
   - Seek per Tap/Drag testen.
   - Fullplayer oeffnen und Regression pruefen.

### Akzeptanzkriterien

- `./gradlew test` besteht.
- Debug APK laesst sich bauen.
- App startet im Emulator.
- Option ist sichtbar und persistiert.
- Overlay erscheint nur nach Aktivierung.
- Fullplayer-Progress bleibt unveraendert.

## Sprint 5: Finalisierung

### Ziel

Die Aenderung ist lokal nachvollziehbar und bereit fuer den naechsten Schritt.

### Umsetzung

1. Git-Diff pruefen.
2. Nur relevante Dateien stagen.
3. Lokalen Commit erstellen, falls der Nutzer Implementierung und Commit wuenscht.
4. Kein Push.

### Vorgeschlagene Commit Message

```text
Add mini player time overlay setting
```

## Risiken und Gegenmassnahmen

### Risiko: Text ist auf der Timeline schlecht lesbar

Gegenmassnahme:

- Kleine kontrastreiche Typografie verwenden.
- Bei Bedarf Text-Shadow oder dezente halbtransparente Scrim hinter den Labels nutzen.

### Risiko: Overlay blockiert Seeking

Gegenmassnahme:

- Overlay ohne Pointer-Input bauen.
- Manuell Tap und Drag testen.

### Risiko: Progressbar-Hoehe ist zu klein

Gegenmassnahme:

- `labelSmall` verwenden.
- Text auf eine Zeile begrenzen.
- Bei sehr kleiner Hoehe trotzdem stabil rendern und nicht clippen lassen, soweit moeglich.

### Risiko: Zu viele Recompositions

Gegenmassnahme:

- Anzeige auf Sekundenwerte reduzieren.
- Bestehenden `rememberSmoothedProgressState` nur fuer die Bar behalten.
- Textlabels nicht mit jedem Frame aktualisieren.

### Risiko: Backup-Kompatibilitaet

Gegenmassnahme:

- Default im `UserSettings`-Konstruktor setzen.
- Restore-Pfad explizit um neues Feld erweitern.
- Optional Backup-Parsing-Test ergaenzen.

## Nicht-Ziele

- Keine Aenderung am Fullplayer-Layout.
- Keine globale Aenderung an `CustomProgressBar`.
- Keine neue Player-Architektur.
- Keine Aenderung an History/Favorites-Listen.
- Keine automatische Aktivierung fuer bestehende Nutzer.

## Empfohlene Reihenfolge bei Umsetzung

1. Persistenz und Domain-Setting.
2. Settings-UI und Toggle-Aktion.
3. Player-Parameter durchreichen.
4. Miniplayer-Overlay rendern.
5. Unit Tests ergaenzen.
6. Build und Emulator-Sichtpruefung.

## Definition of Done

- Neue Settings-Option existiert unter Playback.
- Standard ist deaktiviert.
- Aktivierte Option zeigt Zeiten auf der Miniplayer-Timeline.
- Deaktivierte Option zeigt den bisherigen Miniplayer.
- Fullplayer bleibt unveraendert.
- Backup/Restore umfasst die Einstellung.
- Unit Tests fuer Settings-Mapping und UseCase bestehen.
- App baut erfolgreich.
- Emulator-Sichttest ist bestanden.
