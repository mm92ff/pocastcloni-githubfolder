# Settings Slider Card Stable Layout Plan

## Ziel

Die `SettingsSliderCard` soll auf schmalen Android-Geraeten stabiler und schoener aussehen. Besonders im Settings-Tab `Sync` wirkt die Karte `Keep Limit (episodes per podcast)` aktuell gedrungen, weil Titel und Wert in einer einzigen Zeile ohne klare Breitenregeln konkurrieren.

Der Fix soll die gemeinsame Slider-Komponente verbessern, damit auch andere Slider in Settings von der stabileren Darstellung profitieren.

## Ausgangslage

- Komponente: `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsComponents.kt`
- Betroffene Nutzung: `SectionCleanup` in `app/src/main/java/com/example/pocastcloni/ui/settings/SettingsSections.kt`
- Aktuelles Layout:
  - `Row(fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween)`
  - links normaler Titeltext
  - rechts beliebiger `valueDisplay`
  - keine definierte Gewichtung, Max-Breite, Textausrichtung oder Overflow-Regel
- Sichtbares Problem:
  - langer Titel plus langer Wert passen auf schmalen Viewports schlecht in eine Zeile
  - der Wert bricht unruhig um
  - Titel und Wert wirken optisch zusammengedrueckt

## Zielbild

Die Slider-Karte soll bei langen Texten stabil bleiben:

- Titel bleibt lesbar und darf kontrolliert umbrechen.
- Wert steht rechtsbuendig und wirkt wie eine klare aktuelle Wertanzeige.
- Der Slider bleibt unter der Header-Zeile unveraendert bedienbar.
- Kartenhoehe darf natuerlich wachsen, aber keine Texte duerfen ueberlappen.
- Kein Sonder-Hack nur fuer `Keep Limit`; die Loesung sitzt in `SettingsSliderCard`.

## Nicht-Ziele

- Keine komplette Neugestaltung des Settings-Screens.
- Keine Aenderung der gespeicherten Settings-Werte.
- Keine Aenderung an Backup, Import, Sync-Logik oder Cleanup-Logik.
- Keine neue Dependency.
- Keine Aenderung an Slider-Min/Max-Werten.

## Sprint 1 - Layout-Analyse und Komponentendesign

### Aufgaben

1. Alle Nutzungen von `SettingsSliderCard` pruefen.
2. Festlegen, welche Aufrufe lange Titel oder lange Werttexte haben.
3. Designentscheidung fuer die gemeinsame Komponente:
   - Header als `Row`.
   - Linker Bereich mit `Modifier.weight(1f)`.
   - Rechter Wertbereich mit `wrapContentWidth(Alignment.End)` und rechtsbuendigem Text.
   - Genug Abstand zwischen Titel und Wert.
   - Kontrolliertes Textverhalten mit `maxLines`/`overflow`, wo sinnvoll.
4. Entscheiden, ob `valueDisplay` allgemein bleiben soll oder ob ein optionaler `valueText`-Parameter besser waere.

### Akzeptanz

- Der Plan fuer die Komponente ist kompatibel mit allen bestehenden `SettingsSliderCard`-Aufrufen.
- Keine API-Aenderung, wenn eine interne Layout-Verbesserung ausreicht.
- Wenn API-Aenderung noetig ist, dann minimal und mechanisch migrierbar.

## Sprint 2 - Stabile `SettingsSliderCard` umsetzen

### Aufgaben

1. `SettingsSliderCard` in `SettingsComponents.kt` anpassen.
2. Header-Zeile robust machen:
   - Titelspalte bekommt Gewicht.
   - Titel nutzt `MaterialTheme.typography.titleMedium`.
   - Wertbereich bekommt eine klare rechtsbuendige Ausrichtung.
   - Zwischen Titel und Wert wird ein fester Abstand gesetzt.
3. `valueDisplay` so einbetten, dass lange Texte nicht mehr gegen den Titel laufen.
4. Sicherstellen, dass der Slider weiterhin volle Breite nutzt.
5. Keine Verhaltenaenderung bei `onValueChangeFinished`.

### Akzeptanz

- Keep-Limit-Karte zeigt keinen gequetschten Titel/Wert-Header mehr.
- Wertanzeige ist rechtsbuendig und optisch getrennt.
- Slider-Interaktion bleibt unveraendert.
- Andere Slider-Karten bleiben funktional.

## Sprint 3 - Keep-Limit-Text verifizieren

### Aufgaben

1. `SectionCleanup` im Sync-Tab pruefen.
2. Entscheiden, ob der bestehende Werttext `The latest %d episodes` beibehalten werden kann.
3. Falls der Text trotz Komponentenfix noch zu lang wirkt, nur minimal den Werttext verbessern, z. B.:
   - `Latest %d episodes`
   - oder `50 episodes`
4. Titel `Keep Limit (episodes per podcast)` nur dann kuerzen, wenn die gemeinsame Komponente nicht ausreicht.

### Akzeptanz

- Auf Medium-Phone-Breite ist die Keep-Limit-Karte ruhig und professionell lesbar.
- Kein Text liegt ueber anderem Text.
- Der Wert ist als aktueller Slider-Wert erkennbar.

## Sprint 4 - Build- und Unit-Checks

### Aufgaben

1. Kotlin/Compose-Kompilierung pruefen:
   - `./gradlew.bat :app:compileDebugKotlin`
2. Relevante Unit-Tests laufen lassen:
   - `./gradlew.bat :app:testDebugUnitTest`
3. Falls Layout-spezifische Compose-Tests existieren, relevante Tests mitlaufen lassen.

### Akzeptanz

- `:app:compileDebugKotlin` ist erfolgreich.
- `:app:testDebugUnitTest` ist erfolgreich.
- Keine neuen Lint-/Compile-Fehler durch geaenderte Imports oder Compose Modifier.

## Sprint 5 - Visuelle Pruefung im Emulator

### Aufgaben

1. Debug APK bauen:
   - `./gradlew.bat :app:assembleDebug`
2. App im Emulator installieren und starten.
3. Zu `Settings > Sync > Auto Cleanup` navigieren.
4. Screenshot mit Medium-Phone-Breite pruefen.
5. Falls moeglich auch helles Theme kurz pruefen.

### Akzeptanz

- Keep-Limit-Karte wirkt auf dem Medium-Phone-Screenshot sauber.
- Header-Texte ueberlappen nicht.
- Slider bleibt sichtbar und bedienbar.
- Darstellung ist in dunklem und hellem Theme lesbar.

## Tests

### Pflichttests

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

### Visuelle Tests

- Emulator: Medium Phone
- Pfad: `Settings > Sync > Auto Cleanup`
- Pruefen:
  - Keep-Limit-Titel
  - Keep-Limit-Wertanzeige
  - Slider unterhalb der Texte
  - keine Ueberlappung
  - keine abgeschnittenen Worte

### Regression-Checks

- Playback-Tab Slider pruefen.
- Design-Tab Slider pruefen.
- Sync-Tab Cleanup Interval pruefen.
- Settings-Tabs bleiben klickbar und layouten unveraendert.

## Risiken

- Eine gemeinsame Komponenten-Aenderung betrifft mehrere Settings-Slider.
- Zu starke `maxLines`-Begrenzung koennte lange Titel abschneiden.
- Ein zu breiter Wertbereich koennte Titel auf kleinen Geraeten zu stark einengen.
- `valueDisplay` ist frei komponierbar; die Komponente darf nicht voraussetzen, dass immer nur einfacher Text kommt.

## Empfohlene Umsetzung

Die beste stabile Option ist eine interne Layout-Verbesserung in `SettingsSliderCard`, ohne die bestehende API zu brechen:

1. Header bleibt eine `Row`.
2. Titel bekommt `Modifier.weight(1f)`.
3. Zwischen Titel und Wert kommt ein `Spacer`.
4. Wert wird in eine rechtsbuendige `Box` gelegt.
5. Der Slider bleibt darunter unveraendert.

Falls das visuell noch nicht reicht, wird im zweiten Schritt nur der Keep-Limit-Werttext von `The latest %d episodes` auf eine kuerzere, klarere Form gebracht.

## Definition of Done

- Plan ist in `plans/settings-slider-card-stable-layout-plan.md` gespeichert.
- Umsetzung verbessert die gemeinsame `SettingsSliderCard`.
- Keep-Limit-Darstellung ist auf schmaler Breite stabil.
- Build und Unit-Tests sind gruen.
- Visuelle Pruefung im Emulator ist dokumentiert.
