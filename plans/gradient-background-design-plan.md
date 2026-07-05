# Plan: Einstellbarer Design-Hintergrund mit vertikalem Farbverlauf

## Ziel

Unter `Settings > Design` soll eine neue Option entstehen, mit der der App-Hintergrund als vertikaler Verlauf dargestellt wird:

- Dark Theme: oben schwarz, unten die aktuell ausgewaehlte App-Farbe.
- Light Theme: oben weiss, unten die aktuell ausgewaehlte App-Farbe.
- System Theme: folgt dem effektiv aktiven Systemmodus.

Die Option soll standardmaessig aus sein, damit die bestehende Optik unveraendert bleibt.

## Verstaendnis der gewuenschten Funktion

Der Verlauf ist keine neue eigenstaendige Farbe, sondern eine Darstellungsoption fuer den Hintergrund. Die vorhandene `App Color` bleibt die Quelle fuer die untere Verlaufsfarbe. Wenn der Nutzer z. B. Rot waehlt, laeuft der Hintergrund in Light Theme von Weiss nach Rot und in Dark Theme von Schwarz nach Rot.

## Relevante Ist-Dateien

- Theme-Farben und Systembars:
  - `app/src/main/java/com/example/pocastcloni/ui/theme/Theme.kt`
- Root-App und Haupt-Scaffold:
  - `app/src/main/java/com/example/pocastcloni/ui/main/MainActivity.kt`
- Settings-Datenmodell:
  - `app/src/main/java/com/example/pocastcloni/domain/repository/UserSettings.kt`
- DataStore:
  - `app/src/main/java/com/example/pocastcloni/data/repository/UserPreferencesRepositoryImpl.kt`
  - `app/src/main/java/com/example/pocastcloni/domain/repository/UserPreferencesRepository.kt`
- Settings-UseCase und Actions:
  - `app/src/main/java/com/example/pocastcloni/domain/usecase/app/UpdateUserSettingAction.kt`
  - `app/src/main/java/com/example/pocastcloni/domain/usecase/app/UpdateUserSettingsUseCase.kt`
- Settings UI:
  - `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsContent.kt`
  - `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsContract.kt`
  - `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsSections.kt`
  - `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsViewModel.kt`
- Strings:
  - `app/src/main/res/values/strings.xml`
- Backup/Restore Tests:
  - `app/src/test/java/com/example/pocastcloni/data/manager/PodcastBackupHelperParsingTest.kt`
- Settings Tests:
  - `app/src/test/java/com/example/pocastcloni/ui/settings/SettingsViewModelTest.kt`
  - `app/src/test/java/com/example/pocastcloni/domain/usecase/app/UpdateUserSettingsUseCaseTest.kt`

## Technische Grundentscheidung

Der Verlauf sollte nicht als `MaterialTheme.colorScheme.background` modelliert werden, weil ein `ColorScheme` nur Farben, aber keinen `Brush` kennt. Stattdessen wird ein Root-Hintergrund eingefuehrt, der optional einen `Brush.verticalGradient(...)` zeichnet.

Empfohlen:

- `MaterialTheme.colorScheme.background` bleibt fuer bestehende Komponenten stabil.
- Ein neuer Hintergrund-Wrapper zeichnet bei aktiver Option den Verlauf hinter dem App-Inhalt.
- Relevante `Scaffold`s muessen `containerColor = Color.Transparent` bekommen oder ueber einen gemeinsamen Wrapper laufen, sonst verdecken sie den Verlauf.

## Neue Einstellung

Domain-State:

- `gradientBackgroundEnabled: Boolean`

DataStore-Key:

- `Constants.Preferences.KEY_GRADIENT_BACKGROUND_ENABLED = "gradient_background_enabled"`

Default:

- `Constants.Preferences.DEFAULT_GRADIENT_BACKGROUND_ENABLED = false`

Settings UI:

- Tab: `Design`
- Abschnitt: `Design` oder direkt unter `App Color`
- Label: `Gradient Background`
- Subtitle: `Fade from white or black to the selected app color`
- Typ: `SettingsSwitchCard`

## Verhalten

### Wenn die Option aus ist

- Keine sichtbare Aenderung.
- Bestehende Theme-Farben, Cards, Topbars, Bottom-Bar und Mini-Player bleiben wie bisher.
- Systembar-Farben bleiben beim bestehenden `colorScheme.background`.

### Wenn die Option an ist

- Root-Hintergrund zeichnet einen vertikalen Verlauf.
- Top-Farbe:
  - `Color.White` im effektiv hellen Theme.
  - `Color.Black` im effektiv dunklen Theme.
- Bottom-Farbe:
  - `Color(appColor.hexValue)`, ggf. mit vorsichtiger Alpha-/Blend-Logik, falls die Farbe im hellen Theme zu aggressiv wirkt.
- Statusbar:
  - Light Theme: helle Statusbar-Flaeche, dunkle Icons.
  - Dark Theme: dunkle Statusbar-Flaeche, helle Icons.
- Navigationbar:
  - Soll optisch zur unteren Verlaufsfarbe passen oder weiter den bestehenden Hintergrund verwenden. Empfehlung fuer Start: gleiche Farbe wie die aktuell berechnete Theme-Background-Farbe beibehalten, damit Android-Navigation nicht zu bunt oder schlecht lesbar wird.

## Sprint 1: Datenmodell und Persistenz

### Aufgaben

1. Neue Constants ergaenzen:
   - DataStore-Key.
   - Default-Wert.
2. `UserSettings` um `gradientBackgroundEnabled` erweitern.
3. `UserPreferencesRepository` um `updateGradientBackgroundEnabled(enabled: Boolean)` erweitern.
4. `UserPreferencesRepositoryImpl`:
   - Preference-Key anlegen.
   - Wert in `userSettingsFlow` lesen.
   - Update-Methode implementieren.
   - Wert in `restoreSettings(...)` schreiben.
5. `UpdateUserSettingAction` um `ToggleGradientBackground` erweitern.
6. `UpdateUserSettingsUseCase` an Repository weiterleiten.

### Tests

- `UpdateUserSettingsUseCaseTest`:
  - `ToggleGradientBackground` ruft `updateGradientBackgroundEnabled(true)` auf.
- `PodcastBackupHelperParsingTest`:
  - Export enthaelt `"gradientBackgroundEnabled":true`.
  - Alte Backups ohne Feld importieren mit Default `false`.

### Akzeptanz

- App kompiliert.
- Alte Backups bleiben kompatibel.
- Neue Setting-Kette ist vollstaendig persistiert.

## Sprint 2: Settings UI

### Aufgaben

1. `SettingsUiState.Success` um `gradientBackgroundEnabled` erweitern.
2. `SettingsViewModel.toUiState()` mappt den Wert.
3. `SettingsContent` reicht Wert und Callback an `SectionAppearance` oder passenden Design-Abschnitt weiter.
4. `SettingsSections.kt`:
   - Switch unter `App Color` oder unter `Color Strength` anzeigen.
   - Keine zusaetzlichen Slider im ersten Schritt.
5. Strings in `strings.xml` ergaenzen.

### Tests

- `SettingsViewModelTest`:
  - UI-State mappt `gradientBackgroundEnabled`.
  - Toggle-Action ist nicht debounced, damit der Hintergrund sofort sichtbar umschaltet.

### Akzeptanz

- Option ist unter `Settings > Design` sichtbar.
- Umschalten funktioniert ohne App-Neustart.
- UI bleibt im Light und Dark Theme lesbar.

## Sprint 3: Root-Hintergrund und Theme-Integration

### Aufgaben

1. Effektives Dark/Light Theme aus `PocastCloniTheme` nutzbar machen.
   - Entweder Helper-Funktion aus `Theme.kt` extrahieren.
   - Oder `gradientBackgroundEnabled` direkt an Theme/Root-Wrapper uebergeben.
2. In `MainActivity` einen App-Background-Wrapper einfuehren:
   - `Box(Modifier.fillMaxSize().background(brush))`
   - Darin der bestehende `Scaffold`.
3. Gradient-Brush:
   - `topColor = if (darkTheme) Color.Black else Color.White`
   - `bottomColor = Color(userSettings.appColor.hexValue)`
4. Root-`Scaffold` transparent machen:
   - `containerColor = Color.Transparent`
5. Pruefen, ob verschachtelte Screen-Scaffolds den Verlauf verdecken.

### Tests

- Manuelle Emulator-Pruefung:
  - Light Theme + Rot: oben weiss, unten rot.
  - Dark Theme + Rot: oben schwarz, unten rot.
  - App Color wechseln: Verlauf unten wechselt direkt mit.
  - Option aus: bisheriger Hintergrund.

### Akzeptanz

- Verlauf ist auf mindestens Root-/Screen-Hintergruenden sichtbar.
- Kein Text verschwindet wegen falscher Statusbar-Icon-Farbe.
- Bottom-Bar und Mini-Player bleiben optisch stabil.

## Sprint 4: Transparente Scaffolds und visuelle Konsistenz

### Aufgaben

1. Alle relevanten Screen-Scaffolds pruefen:
   - `HomeScreen`
   - `SettingsScreen`
   - `DownloadsScreen`
   - `FavoritesScreen`
   - `HistoryScreen`
   - `PodcastDetailScreen`
   - `AddPodcastScreen`
   - `FullPlayerScreen`
2. Wo noetig `containerColor = Color.Transparent` setzen.
3. Bei Screens, die bewusst eine eigene Surface-Farbe brauchen, entscheiden:
   - Verlauf sichtbar lassen.
   - Oder gezielt Surface behalten, wenn Lesbarkeit besser ist.
4. Karten/Listen nicht transparent machen. Cards sollen weiterhin Leseflaechen bilden.

### Tests

- Emulator visuell durchklicken:
  - Home
  - Search
  - Downloads
  - Settings
  - Favorites
  - History
  - Podcast Detail
  - Full Player
- Pruefen:
  - keine unlesbaren Titel
  - keine flackernden Hintergruende
  - keine weissen Flaechen im Dark Theme
  - keine schwarzen Flaechen im Light Theme ausser bewusstem Text/Icon

### Akzeptanz

- Verlauf wirkt appweit konsistent.
- Keine Screen-spezifischen harten Hintergrundbrueche.
- Settings bleiben gut scanbar.

## Sprint 5: Systembars und P30-spezifische Pruefung

### Aufgaben

1. `Theme.kt` Systembar-Logik anpassen:
   - Wenn Gradient aktiv und Light Theme: Statusbar-Icons dunkel.
   - Wenn Gradient aktiv und Dark Theme: Statusbar-Icons hell.
2. Navigationbar-Farbe bewusst setzen:
   - konservativ: bestehende `colorScheme.background`
   - alternative spaeter: untere Verlaufsfarbe
3. Huawei P30 Pro besonders pruefen:
   - Android 10/EMUI Navigation-Bar-Balken
   - Kontrast unten
   - Gesten-/3-Button-Navigation

### Tests

- Emulator:
  - Light Theme, Gradient an.
  - Dark Theme, Gradient an.
  - Clean Mode Bottom-Bar an/aus.
  - Auto-hide Bottom-Bar an/aus.
- P30:
  - Installieren der Test-APK.
  - Statusbar oben sichtbar?
  - Android-Navigationbar unten farblich akzeptabel?
  - App-Farbe unten sichtbar?

### Akzeptanz

- Systembar-Icons sind lesbar.
- P30 zeigt keine weisse Android-Navigationbar, wenn das Theme eigentlich dunkel/gradient ist.
- Gradient kollidiert nicht mit Clean Mode.

## Sprint 6: Regression und Build

### Aufgaben

1. Unit-Tests laufen lassen.
2. Kotlin kompiliert.
3. Debug-APK fuer Emulator/P30 bauen.
4. Optional Release/unsigned build pruefen.
5. Git-Diff pruefen und lokalen Commit erstellen, wenn vom Nutzer gewuenscht.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat :app:testDebugUnitTest`
- `.\gradlew.bat :app:assembleDebug`
- `.\gradlew.bat :app:ktlintMainSourceSetCheck`
  - Erwartung: Bestehende Altstellen koennen weiterhin fehlschlagen.
  - Neue Dateien duerfen keine neuen Ktlint-Fehler erzeugen.

### Akzeptanz

- Build erfolgreich.
- Tests erfolgreich.
- Keine neuen Ktlint-Fehler in geaenderten Dateien.
- APK ist installierbar.

## Risiken

- Viele verschachtelte `Scaffold`s koennen den Verlauf verdecken.
- Light Theme mit starker App-Farbe kann unten zu dominant wirken.
- Statusbar-/Navigationbar-Kontrast kann je nach Android-Version variieren.
- Einige Screens nutzen `surface` absichtlich als Hintergrund, z. B. Full Player; dort muss bewusst entschieden werden, ob Verlauf sichtbar sein soll.
- Wenn `colorScheme.background` selbst auf Verlauf-Logik umgebogen wuerde, koennten Cards, Dialoge und Listen ungewollt flach oder schlecht lesbar werden. Deshalb ist ein separater Brush-Wrapper stabiler.

## Nicht im ersten Schritt

- Kein Verlaufstaerke-Slider.
- Keine eigene zweite Verlaufsfarbe.
- Keine diagonalen oder radialen Verlaeufe.
- Keine Animation.
- Keine per-Screen-Auswahl.

## Empfohlene erste Umsetzung

1. Nur ein Switch: `Gradient Background`.
2. Verlauf global im Root-Hintergrund.
3. Top-Farbe automatisch nach effektivem Theme:
   - Light: Weiss
   - Dark: Schwarz
4. Bottom-Farbe aus `AppColor`.
5. Scaffolds nur dort transparent machen, wo sie den Verlauf sichtbar verdecken.

