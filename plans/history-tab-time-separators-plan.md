# Plan: History Tab Time Separators

## Ziel

Im History-Tab sollen gehoerte Episoden zeitlich gruppiert werden. Zwischen den Gruppen soll ein sichtbarer Abschnitt erscheinen, zum Beispiel:

- Heute
- Gestern
- Letzte Woche
- Letzter Monat
- Vor 2 Monaten
- Vor 5 Monaten
- Vor 1 Jahr
- Aelter

Die App zeigt aktuell eine flache Liste in `HistoryScreen.kt`. Die Liste ist bereits nach `datePlayedMs` absteigend sortiert. Die Umsetzung soll diese bestehende Reihenfolge erhalten, stabile LazyColumn-Keys behalten und die UI-Logik nicht in die Composable-Schicht verschieben.

## Verstaendnis der Anforderung

Der Nutzer hat "vertikale Trennlinie" geschrieben. Da der History-Tab eine vertikal scrollende Liste ist, ist die wahrscheinlich gemeinte UI ein horizontaler Abschnittstrenner zwischen Zeitgruppen. Falls stattdessen eine echte vertikale Timeline-Linie links neben den Eintraegen gewuenscht ist, waere das eine alternative Darstellungsvariante mit groesserem UI-Eingriff.

Empfohlene Basisumsetzung:

- Abschnittsheader mit Label.
- Darunter oder daneben eine dezente `HorizontalDivider`-Linie.
- Keine veraenderte Episode-Karte und keine neue Navigation.
- Keine Aenderung an Datenbank, Repository oder Playback-Logik.

## Betroffene Dateien

- `app/src/main/java/com/example/pocastcloni/ui/history/HistoryContract.kt`
- `app/src/main/java/com/example/pocastcloni/ui/history/HistoryViewModel.kt`
- `app/src/main/java/com/example/pocastcloni/ui/history/HistoryScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/pocastcloni/ui/history/HistoryViewModelTest.kt`
- Optional neu: `app/src/main/java/com/example/pocastcloni/ui/history/HistoryGrouping.kt`
- Optional neu: `app/src/test/java/com/example/pocastcloni/ui/history/HistoryGroupingTest.kt`

## Technischer Ansatz

### Datenmodell

Eine neue Display-Zeilenstruktur sollte eingefuehrt werden, statt `HistoryScreen` direkt nach Datum gruppieren zu lassen.

Vorschlag:

```kotlin
sealed interface HistoryListRow {
    val key: String

    data class SectionHeader(
        val bucket: HistoryTimeBucket,
        override val key: String = "section-${bucket.name}"
    ) : HistoryListRow

    data class EpisodeRow(
        val item: HistoryUiItem,
        override val key: String = "episode-${item.id}"
    ) : HistoryListRow
}
```

Dazu ein Bucket-Typ:

```kotlin
enum class HistoryTimeBucket {
    TODAY,
    YESTERDAY,
    LAST_WEEK,
    LAST_MONTH,
    LAST_TWO_MONTHS,
    LAST_FIVE_MONTHS,
    LAST_YEAR,
    OLDER
}
```

`HistoryUiState` kann weiterhin `historyItems` behalten, falls bestehende UI-Logik darauf zugreift. Zusaetzlich wird `historyRows` eingefuehrt, damit die UI Header und Episoden einheitlich rendern kann.

### Gruppierungslogik

Die Gruppierung sollte als pure Funktion testbar sein:

```kotlin
fun buildHistoryRows(
    items: List<HistoryUiItem>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): List<HistoryListRow>
```

Bucket-Regeln, empfohlen nach verstrichenen Kalendertagen in lokaler Zeitzone:

- `TODAY`: gleicher lokaler Tag
- `YESTERDAY`: 1 Tag Differenz
- `LAST_WEEK`: 2 bis 7 Tage
- `LAST_MONTH`: 8 bis 30 Tage
- `LAST_TWO_MONTHS`: 31 bis 60 Tage
- `LAST_FIVE_MONTHS`: 61 bis 150 Tage
- `LAST_YEAR`: 151 bis 365 Tage
- `OLDER`: mehr als 365 Tage oder fehlendes `datePlayedMs`

Wichtig: `datePlayedMs` ist bereits in `EpisodeDisplayModel` vorhanden. Es braucht keine Datenbankmigration.

### UI

`HistoryScreen` rendert statt `historyItems` die neuen `historyRows`.

Episode-Zeilen:

- unveraendert mit `ListableEpisodeItem`
- gleicher Click-Handler
- stabile Keys

Header-Zeilen:

- kleines Label mit `MaterialTheme.typography.labelMedium` oder `titleSmall`
- dezente Farbe via `MaterialTheme.colorScheme.onSurfaceVariant`
- `HorizontalDivider` als sichtbarer Trenner
- ausreichend Padding, aber keine Card-in-Card-Struktur

### One-Handed Mode

Aktuell nutzt `LazyColumn(reverseLayout = uiState.oneHandedMode)`. Das ist der groesste UI-Risikopunkt.

Empfohlene Entscheidung:

1. Zunaechst pruefen, ob `reverseLayout` im History-Tab wirklich notwendig ist.
2. Falls es erhalten bleiben muss, darf die Gruppierungsreihenfolge nicht blind durch `reverseLayout` invertiert werden.
3. Akzeptanzkriterium: Header muessen visuell immer vor den zugehoerigen Episoden stehen, auch bei aktiviertem One-Handed Mode.

Moegliche Loesungen:

- Variante A: `reverseLayout` fuer History-Gruppen deaktivieren und nur Padding/Scrollposition fuer One-Handed Mode erhalten.
- Variante B: `historyRows` je nach `oneHandedMode` speziell bauen, sodass Header visuell korrekt bleiben.
- Variante C: Gruppen als einzelne `item`-Bloecke rendern, innen mit Episodenliste, wodurch `reverseLayout` weniger riskant ist.

Empfehlung: Variante A nur waehlen, wenn One-Handed Mode im History-Tab nicht zwingend die Listenreihenfolge umkehren soll. Sonst Variante C.

## Sprint 0: Vorbereitung und Entscheidung

### Ziel

Die genaue visuelle Variante festlegen und die aktuelle History-Struktur sichern.

### Aufgaben

- Festlegen, ob "vertikale Trennlinie" als Abschnittstrenner oder als linke Timeline-Linie umgesetzt wird.
- Festlegen, ob `reverseLayout` im History-Tab erhalten bleiben muss.
- Bestehende `HistoryScreen`, `HistoryViewModel`, `HistoryContract` und Tests erneut kurz pruefen.
- Keine funktionale Aenderung.

### Tests

- Kein voller Testlauf noetig, da noch keine Codeaenderung.
- Optional: `git status --short`, um saubere Ausgangslage zu bestaetigen.

### Akzeptanz

- UI-Variante ist entschieden.
- Bucket-Regeln sind bestaetigt.
- Scope bleibt nur History-Tab.

## Sprint 1: Gruppierungsmodell und Unit-Tests

### Ziel

Die Zeitgruppierung wird als testbare, UI-unabhaengige Logik eingefuehrt.

### Aufgaben

- `HistoryTimeBucket` einfuehren.
- `HistoryListRow` einfuehren.
- Pure Funktion fuer Bucket-Zuordnung und Row-Erzeugung bauen.
- `HistoryUiState` um `historyRows` erweitern.
- `HistoryViewModel` so erweitern, dass aus sortierten `HistoryUiItem` die Display-Rows entstehen.
- Bestehendes `historyItems` falls sinnvoll beibehalten, damit Empty-State und Clear-Button einfach bleiben.

### Tests

Neue Unit-Tests fuer Bucket-Grenzen:

- Episode von heute landet in `TODAY`.
- Episode von gestern landet in `YESTERDAY`.
- Episode von vor 2 bis 7 Tagen landet in `LAST_WEEK`.
- Episode von vor 8 bis 30 Tagen landet in `LAST_MONTH`.
- Episode von vor 31 bis 60 Tagen landet in `LAST_TWO_MONTHS`.
- Episode von vor 61 bis 150 Tagen landet in `LAST_FIVE_MONTHS`.
- Episode von vor 151 bis 365 Tagen landet in `LAST_YEAR`.
- Episode aelter als 365 Tage landet in `OLDER`.
- Episode ohne `datePlayedMs` landet in `OLDER`.

Neue Tests fuer Row-Erzeugung:

- Header wird pro Bucket nur einmal eingefuegt.
- Episoden bleiben innerhalb der Gruppe absteigend sortiert.
- Leere Liste erzeugt leere `historyRows`.
- Mehrere Buckets erscheinen in erwarteter Reihenfolge.

### Commands

- `./gradlew test --tests "*History*"`
- Falls Testfilter unter Windows/Gradle nicht greift: `./gradlew test`

### Akzeptanz

- Gruppierungslogik ist deterministisch testbar.
- Keine Compose-UI-Logik enthaelt Datumsberechnung.
- Bestehende History-Actions funktionieren unveraendert.

## Sprint 2: Compose-Darstellung der Abschnittstrenner

### Ziel

Der History-Tab rendert Abschnittsheader und Trenner sichtbar und stabil.

### Aufgaben

- `HistoryScreen` von `historyItems` auf `historyRows` umstellen.
- Header-Composable einfuehren, zum Beispiel `HistorySectionHeader`.
- `HorizontalDivider` und Label mit MaterialTheme-Farben nutzen.
- Stable Keys fuer Header und Episoden verwenden.
- Empty-State weiterhin an `historyItems.isEmpty()` koppeln.
- Clear-Button weiterhin nur anzeigen, wenn History nicht leer ist.
- Episode-Click unveraendert lassen.

### UI-Anforderungen

- Kein grosses Redesign.
- Keine Cards fuer Abschnittsheader.
- Gute Lesbarkeit im hellen und dunklen Theme.
- Header duerfen nicht mit Player/NavBar-Padding kollidieren.
- Trenner sollen Gruppen trennen, aber nicht lauter wirken als Episoden.

### Tests

Unit-/ViewModel-Tests:

- `uiState.historyRows` enthaelt erwartete Header und EpisodeRows.
- Click-Action einer Episode funktioniert weiter.
- Clear-History-Dialog-Tests bleiben gruen.

Manuelle UI-Pruefung:

- History mit Eintraegen aus mehreren Zeitraeumen oeffnen.
- Sichtpruefung: Header erscheinen zwischen den Gruppen.
- Sichtpruefung: Keine Ueberlappung mit Mini-Player, Bottom Navigation oder System Navigation Bar.
- Sichtpruefung: Scrollen bleibt fluessig.

### Commands

- `./gradlew test`
- `./gradlew lint`
- `./gradlew assembleDebug`

### Akzeptanz

- Nutzer sieht zeitliche Gruppen im History-Tab.
- Header stehen visuell bei den richtigen Episoden.
- Keine Regression bei Empty-State, Clear-Button oder Episode-Click.

## Sprint 3: One-Handed Mode und Edge Cases

### Ziel

Die Gruppierung bleibt auch bei Spezialfaellen korrekt.

### Aufgaben

- `oneHandedMode` mit gruppierter Liste pruefen.
- Falls Header durch `reverseLayout` falsch stehen, eine der geplanten Varianten umsetzen.
- Zeitzonen-/Mitternachtsgrenzen testen.
- Sehr alte und undatierte Eintraege pruefen.
- Sehr lange History-Liste mit stabilen Keys pruefen.

### Tests

Gezielte Tests:

- Bucket-Berechnung mit fixer `ZoneId`.
- Mitternachtsfall: Episode kurz vor Mitternacht vs. heute.
- Undatierte Episode.
- Gleiche Episode-GUID wird nicht als Header-Key verwendet.

Manuelle Checks:

- One-Handed Mode aus.
- One-Handed Mode an.
- Dark Mode.
- Kleines Display oder Emulator mit schmaler Breite.

### Commands

- `./gradlew test`
- `./gradlew lint`
- Optional, falls Emulator verfuegbar: `./gradlew connectedDebugAndroidTest`

### Akzeptanz

- Header-Reihenfolge ist in beiden One-Handed-Zustaenden korrekt.
- Keine sichtbaren Layout-Spruenge beim Scrollen.
- Keine instabilen LazyColumn-Key-Warnungen.

## Sprint 4: Feinschliff und Review

### Ziel

Die Umsetzung wird auf Scope, Lesbarkeit und Regressionen geprueft.

### Aufgaben

- Diff pruefen: Nur History-relevante Dateien plus Strings/Tests.
- Strings auf klare Sprache pruefen.
- Sicherstellen, dass keine Datenbank-/Repository-Aenderung eingeschlichen ist.
- Optional Screenshot des History-Tabs erstellen.
- Lokale Commit-Vorbereitung, falls der Nutzer Umsetzung und Commit wuenscht.

### Tests

Pflicht:

- `./gradlew test`
- `./gradlew lint`
- `./gradlew assembleDebug`

Optional:

- Emulator-Start und manuelle Sichtpruefung.
- Screenshot-Vergleich vor/nach Umsetzung.

### Akzeptanz

- Alle Pflichtchecks laufen erfolgreich.
- Worktree enthaelt nur erwartete Dateien.
- Feature ist lokal nachvollziehbar und klein genug fuer einen separaten Commit.

## Testmatrix

| Bereich | Test | Erwartung |
| --- | --- | --- |
| Bucket heute | `datePlayedMs` am aktuellen lokalen Tag | Header `Heute` |
| Bucket gestern | 1 Kalendertag Differenz | Header `Gestern` |
| Bucket letzte Woche | 2-7 Tage Differenz | Header `Letzte Woche` |
| Bucket letzter Monat | 8-30 Tage Differenz | Header `Letzter Monat` |
| Bucket zwei Monate | 31-60 Tage Differenz | Header `Vor 2 Monaten` |
| Bucket fuenf Monate | 61-150 Tage Differenz | Header `Vor 5 Monaten` |
| Bucket ein Jahr | 151-365 Tage Differenz | Header `Vor 1 Jahr` |
| Bucket aelter | >365 Tage oder kein Datum | Header `Aelter` |
| Reihenfolge | gemischte Episoden | Neueste Gruppen zuerst |
| Header-Deduplizierung | mehrere Episoden gleicher Gruppe | ein Header pro Gruppe |
| Empty State | keine History | bestehender Empty-State bleibt |
| Clear History | History vorhanden | Button und Dialog bleiben |
| Click | Episode antippen | `HistoryAction.OnEpisodeClick` bleibt |
| One-Handed aus | normale Liste | Header vor Episoden |
| One-Handed an | invertierte/angepasste Liste | Header weiterhin korrekt |
| Dark Mode | dunkles Theme | Trenner und Label lesbar |

## Risiken

- `reverseLayout` kann Header visuell falsch positionieren, wenn Header und Episoden als einzelne Rows gerendert werden.
- Kalenderbasierte Begriffe wie "letzte Woche" koennen unterschiedlich verstanden werden. Der Plan nutzt bewusst Tagesbereiche.
- `System.currentTimeMillis()` direkt im ViewModel erschwert Tests. Deshalb soll die Gruppierung eine pure Funktion mit `nowMillis`-Parameter bekommen.
- Undatierte History-Eintraege muessen defensiv behandelt werden, obwohl echte History-Eintraege normalerweise ein Datum haben sollten.

## Empfohlene Reihenfolge fuer Umsetzung

1. Gruppierungslogik plus Tests.
2. ViewModel-State um Rows erweitern.
3. UI-Rendering fuer Header und Divider.
4. One-Handed Mode pruefen und falls noetig korrigieren.
5. Voller lokaler Checklauf.

## Definition of Done

- History-Tab zeigt Zeitabschnitte mit sichtbarer Trennlinie.
- Keine Aenderung am Playback-, Repository- oder Datenbankschema.
- Unit-Tests decken Bucket-Grenzen und Row-Aufbau ab.
- `./gradlew test`, `./gradlew lint` und `./gradlew assembleDebug` sind erfolgreich.
- Worktree ist nach lokalem Commit sauber, falls ein Commit beauftragt wird.
