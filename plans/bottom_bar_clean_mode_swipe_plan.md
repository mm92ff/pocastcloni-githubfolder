# Plan: Ausblendbare Bottom-Bar mit Swipe-Reveal und Settings-Option

## Projektkonkrete Fassung fuer PocastCloni

Dieses Projekt ist eine native Android-App mit Jetpack Compose. Die generischen Flutter/React-Native/iOS-Hinweise weiter unten sind fuer diese App nicht massgeblich. Die Umsetzung soll konkret in der bestehenden Compose-Architektur erfolgen.

### Relevante Ist-Dateien

- Bottom-Bar: `app/src/main/java/com/example/pocastcloni/ui/main/MainActivity.kt`
  - `Scaffold(bottomBar = { AppBottomNavigation(...) })`
  - `AppBottomNavigation(...)`
- Mini-Player: `app/src/main/java/com/example/pocastcloni/ui/main/MainActivity.kt`
  - `PlayerContainer(...)` liegt im Content-`Box` mit `Modifier.align(Alignment.BottomCenter)`
- Settings-State: `app/src/main/java/com/example/pocastcloni/domain/repository/UserSettings.kt`
- DataStore: `app/src/main/java/com/example/pocastcloni/data/repository/UserPreferencesRepositoryImpl.kt`
- Settings UI: `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsContent.kt`
  - neue Option gehoert in `DesignSettingsContent -> SectionInterface`
- Settings Actions: `app/src/main/java/com/example/pocastcloni/domain/usecase/app/UpdateUserSettingAction.kt`
- Settings Persistenz: `app/src/main/java/com/example/pocastcloni/domain/usecase/app/UpdateUserSettingsUseCase.kt`
- Strings: `app/src/main/res/values/strings.xml`

### Konkrete technische Entscheidung

Die Bottom-Bar soll nicht komplett aus dem `Scaffold` entfernt werden. Stattdessen soll das bestehende `bottomBar`-Slot durch einen kleinen Host ersetzt werden:

- Clean Mode aus:
  - Host-Hoehe = `userSettings.navBarHeight.dp`
  - normale `NavigationBar`
  - Verhalten wie bisher
- Clean Mode an und Bottom-Bar versteckt:
  - Host-Hoehe = schmaler Swipe-/Handle-Bereich, z.B. `16.dp` bis `24.dp`
  - keine Bottom-Bar-Buttons im Composition-Baum bzw. keine tappbaren unsichtbaren Items
  - nur ein dezenter Handle/Gesture-Bereich
  - Content bekommt nur diesen kleinen Bottom-Padding-Wert vom `Scaffold`
  - Mini-Player bleibt oberhalb dieses Handle-Bereichs bedienbar
- Clean Mode an und Bottom-Bar eingeblendet:
  - Host-Hoehe = `userSettings.navBarHeight.dp`
  - normale `NavigationBar`
  - Swipe nach unten auf der Bar versteckt sie wieder

Diese Variante ist stabiler als ein komplettes Overlay, weil der bestehende `Scaffold` weiterhin die Bottom-Inset-Logik liefert. Gleichzeitig gewinnt die UI im versteckten Zustand fast die ganze Bottom-Bar-Hoehe zurueck.

### Konkrete neue Einstellung

Name im Domain-State:

- `bottomBarCleanModeEnabled: Boolean`

DataStore-Key:

- `Constants.Preferences.KEY_BOTTOM_BAR_CLEAN_MODE_ENABLED = "bottom_bar_clean_mode_enabled"`

Default:

- `Constants.Preferences.DEFAULT_BOTTOM_BAR_CLEAN_MODE_ENABLED = false`

Settings UI:

- Tab: `Design`
- Abschnitt: `Interface`
- Label: `Clean Mode`
- Subtitle: `Hide the bottom bar and reveal it with an upward swipe`
- Typ: `SettingsSwitchCard`

Backup/Restore:

- Weil `BackupData` den kompletten `UserSettings` serialisiert, muss das neue Feld in `UserSettings` enthalten sein.
- `UserPreferencesRepositoryImpl.restoreSettings(...)` muss den Wert explizit in DataStore schreiben.
- Alte Backups ohne Feld muessen wegen Default `false` weiterhin importierbar bleiben.

### Konkrete Navigation-/Gesture-Regeln

- Clean Mode aus:
  - Keine neuen Gesten aktiv.
  - Bottom-Bar ist permanent sichtbar.
- Clean Mode an:
  - Initial versteckt nach App-Start.
  - Swipe nach oben auf dem unteren Handle-Bereich zeigt die Bottom-Bar.
  - Tap auf den Handle macht nichts.
  - Swipe nach unten auf der sichtbaren Bottom-Bar versteckt sie.
  - Nach einem Bottom-Bar-Navigationsklick kann die Bar wieder versteckt werden, damit der Clean Mode klar bleibt.
  - Auto-Hide nach Zeit wird fuer Version 1 nicht empfohlen. Erst nach stabilem Grundverhalten nachziehen.

### Konkrete Compose-Bausteine

Empfohlene neue/angepasste Composables:

- `CleanModeBottomBarHost(...)`
  - kapselt sichtbaren/versteckten Zustand
  - entscheidet Host-Hoehe
  - rendert entweder Handle oder `AppBottomNavigation`
  - verarbeitet Swipe-Gesten
- `BottomBarRevealHandle(...)`
  - kleiner, dezenter Handle
  - keine Text-Erklaerung in der UI
- `AppBottomNavigation(...)`
  - bleibt weitgehend bestehen
  - bekommt optional `modifier`
  - bekommt optional `onNavigationItemClicked`
  - bekommt Swipe-down-Geste nur im Clean Mode

State im `MainActivity`-Compose-Baum:

- `val cleanModeEnabled = uiState.userSettings.bottomBarCleanModeEnabled`
- `var bottomBarRevealed by rememberSaveable { mutableStateOf(false) }`
- `val bottomBarVisible = !cleanModeEnabled || bottomBarRevealed`

Setting-Wechsel:

- `LaunchedEffect(cleanModeEnabled) { bottomBarRevealed = !cleanModeEnabled }`
- Clean Mode aus -> `bottomBarVisible == true`
- Clean Mode an -> initial `bottomBarVisible == false`
- Swipe nach oben -> `bottomBarRevealed = true`
- Swipe nach unten -> `bottomBarRevealed = false`

Diese Variante vermeidet doppelte Zustandssteuerung durch `rememberSaveable(cleanModeEnabled)` plus `LaunchedEffect`. Es gibt nur einen Reveal-State; die finale Sichtbarkeit wird aus Setting plus Reveal-State abgeleitet.

### Konkrete Padding- und Mini-Player-Regel

Wichtig: `Scaffold.bottomBar` steuert das `innerPadding`, das in `MainActivity` auf den gesamten Content angewendet wird. Dadurch bewegt sich auch der `PlayerContainer`, weil er im gepaddeten Content-`Box` unten ausgerichtet ist.

Regel:

- Clean Mode aus:
  - `bottomBarHostHeight = userSettings.navBarHeight.dp`
  - Content und Mini-Player bleiben wie bisher oberhalb der Bottom-Bar.
- Clean Mode an und versteckt:
  - `bottomBarHostHeight = BottomBarHandleHeight`, empfohlen `16.dp`, maximal `24.dp`
  - Content und Mini-Player rutschen nach unten und gewinnen fast die Bottom-Bar-Hoehe zurueck.
  - Der Handle darf nicht ueber Mini-Player-Controls liegen.
- Clean Mode an und sichtbar:
  - `bottomBarHostHeight = userSettings.navBarHeight.dp`
  - Content und Mini-Player verhalten sich wie bei sichtbarer Bottom-Bar.

Offener Umsetzungsentscheid:

- `SettingsListContent` berechnet aktuell eigenes Bottom-Padding aus `settings.navBarHeight + settings.progressBarHeight`, wenn der Player sichtbar ist.
- Bei Clean Mode aktiv/versteckt kann dieses Padding zu gross sein.
- Deshalb muss entweder:
  - `SettingsListContent` den effektiven Bottom-Bar-Host-Wert bekommen, oder
  - die konservative Padding-Logik bewusst beibehalten werden, wenn sie keine sichtbare Luecke erzeugt.

Empfehlung:

- In Version 1 `SettingsListContent` nicht blind erweitern.
- Nach Implementierung visuell pruefen, ob Settings bei Clean Mode versteckt unten zu viel Leerraum hat.
- Falls ja: `effectiveBottomBarHeight` als UI-Wert bis Settings durchreichen.

### Konkrete Settings-Pipeline

Das neue Setting muss vollstaendig durch die bestehende Pipeline gehen:

1. `Constants.Preferences`
   - Key `KEY_BOTTOM_BAR_CLEAN_MODE_ENABLED`
   - Default `DEFAULT_BOTTOM_BAR_CLEAN_MODE_ENABLED = false`
2. `UserSettings`
   - Feld `bottomBarCleanModeEnabled: Boolean`
3. `UserPreferencesRepository`
   - Methode `updateBottomBarCleanModeEnabled(enabled: Boolean)`
4. `UserPreferencesRepositoryImpl`
   - `booleanPreferencesKey`
   - Lesen im `userSettingsFlow`
   - Schreiben in eigener Update-Methode
   - Schreiben in `restoreSettings(...)`
5. `UpdateUserSettingAction`
   - `ToggleBottomBarCleanMode(enabled: Boolean)`
6. `UpdateUserSettingsUseCase`
   - Action an Repository weiterleiten
   - nicht debouncen
   - kein Worker-Side-Effect
7. `SettingsUiState.Success`
   - Feld `bottomBarCleanModeEnabled`
8. `SettingsViewModel.toUiState()`
   - Mapping von `UserSettings`
9. `SettingsContent -> SectionInterface`
   - Wert und Callback durchreichen
10. `SettingsSections.kt`
   - `SettingsSwitchCard` in `SectionInterface`
11. `strings.xml`
   - Label und Subtitle

Diese Liste ist Teil der Akzeptanz. Wenn eine Station fehlt, ist das Setting wahrscheinlich entweder nicht sichtbar, nicht persistent, nicht exportierbar oder nicht reaktiv im Main-Layout.

### Konkrete Backup-/Restore-Regel

Weil `BackupData` `UserSettings` serialisiert, muss das neue Feld sicher mit Backups funktionieren.

Akzeptanz:

- Altes Backup ohne `bottomBarCleanModeEnabled` importieren:
  - Import darf nicht fehlschlagen.
  - Wert faellt auf `false` zurueck.
- Neues Backup mit `bottomBarCleanModeEnabled = true` importieren:
  - Setting wird wiederhergestellt.
  - Nach App-Neustart ist Clean Mode weiterhin aktiv.

Empfohlene Tests:

- JVM-Test fuer Backup-Parsing mit fehlendem Feld.
- JVM- oder Instrumentation-Test fuer `restoreSettings(...)`, falls vorhandene Teststruktur das sinnvoll erlaubt.

### Konkrete UX-Regel fuer Settings

Wenn Clean Mode aktiv ist und die Bottom-Bar per Swipe eingeblendet wurde:

- Ein Navigationsklick darf die Bar wieder verstecken.
- Ausnahme/Empfehlung: Bei Navigation zu `Settings` die Bar nicht aggressiv verstecken.

Grund:

- User koennte gerade den Clean Mode konfigurieren.
- Ein sofortiges Verschwinden waehrend Settings-Bedienung fuehlt sich irritierend an.

Version-1-Regel:

- Kein Auto-Hide-Timer.
- Nach Tab-Klick darf die Bar versteckt werden, ausser wenn Ziel `Settings` ist.
- Wenn diese Sonderregel in der Umsetzung zu viel Komplexitaet erzeugt, hat einfache Stabilitaet Vorrang: Bottom-Bar bleibt nach Navigation sichtbar, bis User sie per Swipe nach unten versteckt.

### Konkrete Swipe-Schwellen

Empfehlung:

- Minimum vertikale Drag-Distanz: `48.dp` in Pixel umgerechnet
- Horizontale Bewegung ignorieren, wenn sie deutlich groesser als vertikale Bewegung ist
- Tap ohne Drag darf nichts ausloesen

Compose-Option:

- `Modifier.pointerInput(cleanModeEnabled, bottomBarRevealed) { detectDragGestures(...) }`

Wichtig: Gestenerkennung soll nur im Bottom-Bar-Host liegen, nicht ueber dem Mini-Player.

### Konkrete Tests

Pflicht-Checks:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Manuelle Emulator-/P30-Checks:

- Default nach Installation: Bottom-Bar sichtbar.
- Setting `Clean Mode` einschalten: Bottom-Bar verschwindet, Handle bleibt unten.
- Tap auf Handle: Bottom-Bar bleibt versteckt.
- Swipe vom unteren Handle nach oben: Bottom-Bar erscheint.
- Swipe auf Bottom-Bar nach unten: Bottom-Bar verschwindet.
- Bottom-Bar-Item antippen: Navigation funktioniert.
- Mini-Player sichtbar: Play/Pause und Oeffnen funktionieren weiterhin.
- Mini-Player sichtbar + Clean Mode versteckt: Swipe-Bereich liegt nicht ueber Mini-Player-Buttons.
- App neu starten: Setting bleibt erhalten, Clean Mode ist aktiv, Bottom-Bar initial versteckt.
- Backup exportieren/importieren: Setting bleibt erhalten.

### Konkrete Risiken

- `Scaffold.bottomBar` beeinflusst `innerPadding`; der Mini-Player bewegt sich mit der Host-Hoehe. Das ist gewollt, muss aber visuell geprueft werden.
- Wenn der Handle zu hoch ist, geht Clean-Space verloren; wenn er zu niedrig ist, wird Swipe schwer auffindbar.
- Android-Systemgesten koennen mit sehr tiefen Swipes konkurrieren; deshalb auf P30 testen.
- Accessibility muss mindestens manuell geprueft werden. Da Navigation versteckt werden kann, darf der Modus nicht default aktiv sein.

## Ziel

Die App-Bottom-Bar mit den Eintraegen wie `Settings`, `Home`, `Downloads` und `Search` soll optional ausgeblendet werden koennen, damit die Hauptansicht cleaner wirkt.

Die Funktion soll unter `Settings` ein- und ausschaltbar sein.

Wenn die Funktion aktiv ist:

- Die Bottom-Bar wird im normalen Nutzungszustand ausgeblendet.
- Sie kann per Swipe vom unteren Bildschirmrand nach oben wieder eingeblendet werden.
- Ein einfacher Tap am unteren Rand soll die Bottom-Bar nicht einblenden, damit der Mini-Player nicht versehentlich ausgeloest wird.
- Die Bottom-Bar kann per Swipe nach unten wieder ausgeblendet werden.
- Optional blendet sie sich nach einigen Sekunden ohne Interaktion automatisch wieder aus.

Wenn die Funktion deaktiviert ist:

- Die Bottom-Bar bleibt dauerhaft sichtbar wie bisher.
- Es gibt kein Swipe-Reveal-Verhalten.

## Grundentscheidung

Ein Tap am unteren Rand wird nicht empfohlen.

Grund:

- Der Mini-Player liegt wahrscheinlich direkt oberhalb der Bottom-Bar.
- Ein Tap koennte versehentlich Play/Pause, Mini-Player-Oeffnen oder eine andere Mini-Player-Aktion ausloesen.
- Ein vertikaler Swipe ist absichtlicher und besser von normalen Taps unterscheidbar.

Empfohlene Geste:

- Einblenden: Swipe vom unteren Rand nach oben.
- Ausblenden: Swipe auf der Bottom-Bar nach unten.
- Kein Einblenden per einfachem Tap.

## UX-Verhalten

### Clean Mode aus

- Bottom-Bar ist immer sichtbar.
- App verhaelt sich wie bisher.

### Clean Mode an

- Bottom-Bar ist standardmaessig versteckt.
- Unten bleibt ein schmaler Gesture-Bereich oder Handle-Bereich.
- Der Mini-Player bleibt sichtbar und bedienbar.
- Nur ein klarer Swipe nach oben vom unteren Rand blendet die Bottom-Bar ein.
- Die eingeblendete Bottom-Bar liegt unterhalb oder hinter dem Mini-Player, ohne dessen Buttons zu ueberdecken.
- Ein Swipe nach unten auf der Bottom-Bar blendet sie wieder aus.
- Optional: Auto-Hide nach 4 Sekunden ohne Interaktion.

## Settings-Option

Neue Einstellung:

- Label: `Clean Mode`
- Beschreibung: `Bottom-Bar ausblenden und per Swipe wieder einblenden`
- Typ: Toggle/Switch
- Default: aus

Optional weitere Einstellung:

- Label: `Bottom-Bar automatisch ausblenden`
- Typ: Toggle/Switch
- Default: an, wenn Clean Mode aktiv ist

Pragmatische Empfehlung:

- Erst nur einen Toggle bauen: `Clean Mode`.
- Auto-Hide fest auf z.B. 4 Sekunden setzen.
- Erweiterte Optionen nur ergaenzen, wenn es spaeter wirklich gebraucht wird.

## Sprint 1: Ist-Zustand und Architektur klaeren

### Aufgaben

- Herausfinden, welches Framework genutzt wird:
  - Flutter
  - React Native
  - Android Compose
  - Native Android XML/View
  - anderes Framework
- Aktuelle Bottom-Bar-Komponente identifizieren.
- Aktuelle Settings-Ansicht identifizieren.
- Klaeren, ob der Mini-Player Teil derselben Layout-Hierarchie ist oder als Overlay gerendert wird.
- Pruefen, wie Gesten aktuell verarbeitet werden.

### Akzeptanzkriterien

- Es ist klar, wo die Bottom-Bar ein- und ausgeblendet werden kann.
- Es ist klar, wo die Settings-Option gespeichert wird.
- Es ist klar, ob der Mini-Player durch den Gesture-Bereich beeinflusst werden koennte.

### Tests

- App startet.
- Bottom-Bar ist im aktuellen Zustand sichtbar.
- Mini-Player funktioniert unveraendert.

## Sprint 2: Persistente Settings-Option einbauen

### Aufgaben

- Neue Einstellung `cleanBottomBarEnabled` oder aehnlich anlegen.
- Toggle in der Settings-Ansicht hinzufuegen.
- Einstellung persistent speichern:
  - Flutter: z.B. `SharedPreferences`, Riverpod/Bloc/Provider-State plus Persistenz
  - React Native: z.B. `AsyncStorage`
  - Android Compose: z.B. `DataStore`
  - Native Android: z.B. `DataStore` oder `SharedPreferences`
- Beim App-Start gespeicherten Wert laden.

### Akzeptanzkriterien

- Toggle ist unter Settings sichtbar.
- Toggle kann ein- und ausgeschaltet werden.
- Zustand bleibt nach App-Neustart erhalten.
- Default ist `aus`, damit bestehendes Verhalten erhalten bleibt.

### Tests

- Toggle anzeigen.
- Toggle einschalten.
- Settings verlassen und erneut oeffnen.
- App neu starten und pruefen, dass Einstellung erhalten bleibt.
- Toggle ausschalten und Verhalten wieder auf Standard pruefen.

## Sprint 3: Bottom-Bar ausblendbar machen

### Aufgaben

- Bottom-Bar-Zustand einfuehren:
  - sichtbar
  - versteckt
- Wenn Clean Mode aus ist:
  - Bottom-Bar immer sichtbar.
- Wenn Clean Mode an ist:
  - Bottom-Bar standardmaessig versteckt.
- Animation einbauen:
  - Slide nach unten beim Ausblenden
  - Slide nach oben beim Einblenden
  - kurze Dauer, z.B. 180-250 ms
- Layout so anpassen, dass der Mini-Player nicht verdeckt oder verschoben wird.

### Akzeptanzkriterien

- Bottom-Bar kann technisch ausgeblendet werden.
- Keine Buttons der Bottom-Bar sind im versteckten Zustand tappbar.
- Mini-Player bleibt sichtbar.
- Content bekommt durch das Ausblenden etwas mehr ruhige Flaeche.

### Tests

- Clean Mode aus: Bottom-Bar sichtbar.
- Clean Mode an: Bottom-Bar versteckt.
- Wechsel zwischen den Modi funktioniert ohne Neustart.
- Mini-Player kann weiterhin bedient werden.
- Keine unsichtbaren Bottom-Bar-Buttons reagieren auf Taps.

## Sprint 4: Swipe-Reveal einbauen

### Aufgaben

- Einen schmalen Gesture-Bereich am unteren Rand einfuehren.
- Geste erkennen:
  - vertikaler Swipe nach oben
  - Mindestdistanz z.B. 40-60 px
  - horizontale Bewegung ignorieren
  - einfacher Tap macht nichts
- Bottom-Bar bei gueltigem Swipe einblenden.
- Swipe nach unten auf der sichtbaren Bottom-Bar blendet sie wieder aus.
- Gesture-Bereich so platzieren, dass er nicht ueber Mini-Player-Controls liegt.

### Akzeptanzkriterien

- Ein Swipe von unten nach oben blendet die Bottom-Bar ein.
- Ein einfacher Tap am unteren Rand macht nichts.
- Ein Tap auf den Mini-Player loest weiterhin nur Mini-Player-Aktionen aus.
- Swipe nach unten auf der Bottom-Bar blendet sie aus.
- Bei deaktiviertem Clean Mode sind diese Gesten inaktiv.

### Tests

- Swipe nach oben: Bottom-Bar erscheint.
- Tap unten: Bottom-Bar bleibt versteckt.
- Tap auf Mini-Player Play/Pause: nur Mini-Player reagiert.
- Swipe nach unten auf Bottom-Bar: Bottom-Bar verschwindet.
- Horizontaler Swipe: Bottom-Bar bleibt unveraendert.

## Sprint 5: Auto-Hide und Interaktionsregeln

### Aufgaben

- Wenn Bottom-Bar durch Swipe eingeblendet wurde:
  - Auto-Hide-Timer starten, z.B. 4 Sekunden.
- Timer pausieren oder zuruecksetzen, wenn:
  - User mit Bottom-Bar interagiert
  - User einen Tab wechselt
  - User Settings oeffnet
- Bottom-Bar nicht automatisch verstecken, solange Settings aktiv sind.
- Beim Wechsel auf eine neue Hauptseite entscheiden:
  - Empfehlung: Bottom-Bar wieder verstecken, wenn Clean Mode aktiv bleibt.

### Akzeptanzkriterien

- Bottom-Bar verschwindet nach Inaktivitaet automatisch.
- Interaktion mit Bottom-Bar fuehlt sich nicht hektisch an.
- Settings bleiben erreichbar und werden nicht waehrend der Bedienung ausgeblendet.

### Tests

- Bottom-Bar einblenden, 4 Sekunden warten, Bottom-Bar verschwindet.
- Bottom-Bar einblenden, Button antippen, Timer startet neu oder wird sauber beendet.
- Settings oeffnen, Bottom-Bar wird nicht mitten in der Bedienung ausgeblendet.

## Sprint 6: Visuelles Feintuning

### Aufgaben

- Optional kleinen Handle unten mittig anzeigen:
  - sehr dezent
  - keine textliche Erklaerung
  - nur wenn Clean Mode aktiv und Bottom-Bar versteckt ist
- Animation testen:
  - keine Spruenge
  - keine Ueberdeckung mit Mini-Player
  - keine abgeschnittenen Icons
- Safe-Area beachten:
  - Android Gesture Bar
  - iPhone Home Indicator
  - verschiedene Bildschirmgroessen

### Akzeptanzkriterien

- UI wirkt cleaner.
- Bottom-Bar ist trotzdem auffindbar.
- Mini-Player und System-Gesten kommen sich nicht sichtbar in die Quere.
- Auf kleinen Screens bleibt alles bedienbar.

### Tests

- Android kleiner Screen.
- Android grosser Screen.
- iOS falls relevant.
- Mini-Player sichtbar und ausgeblendet testen.
- Landscape falls die App Landscape unterstuetzt.

## Sprint 7: Regressionstests und Edge Cases

### Aufgaben

- Pruefen, ob alle Navigationspunkte weiter erreichbar sind:
  - Settings
  - Home
  - Downloads
  - Search
- Pruefen, ob Deep Links oder App-Restore korrekt funktionieren.
- Pruefen, ob Screenreader/Accessibility noch Zugriff auf Navigation hat.
- Optional: Wenn Clean Mode aktiv ist, per Accessibility eine alternative Navigation anbieten.

### Akzeptanzkriterien

- Keine Navigation geht verloren.
- App ist nach Neustart im korrekten Clean-Mode-Zustand.
- Mini-Player bleibt bedienbar.
- Gesten fuehlen sich eindeutig an.

### Tests

- Navigation zu jedem Tab.
- App schließen und neu starten.
- Clean Mode an/aus mehrfach wechseln.
- Mini-Player starten, pausieren, oeffnen.
- Swipe-Reveal bei aktivem Mini-Player.
- Swipe-Reveal ohne Mini-Player.

## Technische Hinweise nach Framework

### Flutter

- Bottom-Bar z.B. mit `AnimatedSlide`, `AnimatedOpacity` oder `AnimatedContainer` ausblenden.
- Gesture-Bereich mit `GestureDetector`.
- Settings mit `SwitchListTile`.
- Persistenz mit `SharedPreferences`.

### React Native

- Bottom-Bar z.B. ueber `Animated.View` oder Reanimated verschieben.
- Gesture-Erkennung mit `react-native-gesture-handler`.
- Settings mit Switch.
- Persistenz mit `AsyncStorage`.

### Android Compose

- Bottom-Bar mit `AnimatedVisibility` oder `Modifier.offset`.
- Gesture mit `pointerInput` und `detectVerticalDragGestures`.
- Settings mit `Switch`.
- Persistenz mit `DataStore`.

### Native Android Views

- BottomNavigationView mit `translationY` animieren.
- Gesture-Bereich als transparente View unten.
- Settings mit SwitchPreference.
- Persistenz mit DataStore oder SharedPreferences.

## Wichtige Risiken

### Risiko: Mini-Player wird versehentlich bedient

Gegenmassnahme:

- Kein Tap-to-Reveal.
- Nur Swipe nach oben.
- Gesture-Bereich nicht ueber wichtige Mini-Player-Buttons legen.

### Risiko: Navigation wird zu schwer auffindbar

Gegenmassnahme:

- Dezenten Handle anzeigen.
- Setting standardmaessig deaktiviert lassen.
- Optional kurzer Hinweis beim ersten Aktivieren.

### Risiko: Konflikt mit Android-Systemgesten

Gegenmassnahme:

- Safe-Area/System-Inset beachten.
- Swipe-Schwelle nicht zu niedrig setzen.
- Test auf echten Geraeten oder Emulatoren mit Gesture Navigation.

## Definition of Done

- Setting zum Ein-/Ausschalten existiert.
- Default bleibt bisheriges Verhalten.
- Bei aktivem Clean Mode ist die Bottom-Bar versteckt.
- Swipe von unten nach oben blendet die Bottom-Bar ein.
- Tap am unteren Rand blendet sie nicht ein.
- Swipe nach unten blendet sie aus.
- Mini-Player bleibt bedienbar.
- Einstellung bleibt nach Neustart erhalten.
- Tests fuer Settings, Swipe, Mini-Player-Kollision und Navigation sind vorhanden.
