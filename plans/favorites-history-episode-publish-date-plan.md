# Plan: Episode Publish Date in Favorites and History Rows

## Ziel

In den Listen-Eintraegen von `Favorites` und `History` soll das Erscheinungsdatum der Podcast-Folge sichtbar werden. Das Datum soll rechtsbuendig in derselben Zeile stehen, in der links der Podcast-Name angezeigt wird.

Gewuenschtes Layout:

```text
Episode Title
Podcast Name                                      24 Jun 2026
```

Die Anzeige soll nur in diesen zwei Screens aktiv sein:

- Favorites
- History

Andere Nutzungen von `ListableEpisodeItem` duerfen dadurch nicht automatisch ein Datum anzeigen.

## Aktueller Stand

### Datenbasis

`EpisodeDisplayModel` enthaelt bereits:

```kotlin
val pubDateMs: Long?
```

Das reicht fuer diese Anforderung aus. Es braucht keine Datenbankmigration und keine Aenderung an Repository oder UseCase.

### UI-Komponente

`ListableEpisodeItem` wird aktuell nur an diesen Stellen verwendet:

- `FavoritesScreen.kt`
- `HistoryScreen.kt`
- `ListableEpisodeItem.kt` selbst

Dadurch ist der Eingriff klein. Trotzdem sollte das Datum nicht standardmaessig immer angezeigt werden, sondern explizit per Parameter aktiviert werden.

### Bestehende Formatierung

In `EpisodeUiModel.kt` gibt es bereits lokale Datumsformatierung mit:

```kotlin
DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
```

Fuer die neue Anzeige sollte entweder diese Logik in einen kleinen gemeinsamen Helper verschoben oder direkt in `ListableEpisodeItem` mit `remember`/stabiler Formatierung verwendet werden. Ein gemeinsamer Helper ist sauberer.

## Vorgeschlagene Umsetzung

### API von `ListableEpisodeItem`

`ListableEpisodeItem` erhaelt einen optionalen Parameter:

```kotlin
showPublishDate: Boolean = false
```

Alternativ, falls mehr Kontrolle gewuenscht ist:

```kotlin
trailingMetadata: String? = null
```

Empfehlung: `showPublishDate`, weil die Anforderung konkret ist und `EpisodeDisplayModel.pubDateMs` schon im Item vorhanden ist.

### Rendering

Die zweite Textzeile wird von einem einzelnen `Text` zu einer `Row`:

```text
Row {
    Text(
        text = podcastTitle,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    Text(
        text = formattedPublishDate,
        maxLines = 1
    )
}
```

Wichtig:

- Podcast-Name links darf bei wenig Platz ellipsieren.
- Datum rechts darf nicht abgeschnitten werden, solange es kurz formatiert ist.
- Zwischen Podcast-Name und Datum braucht es Abstand.
- Wenn `pubDateMs == null`, wird rechts nichts angezeigt.
- Wenn `showPublishDate == false`, bleibt das heutige Layout unveraendert.

### Aktivierung in Screens

In `FavoritesScreen.kt`:

```kotlin
ListableEpisodeItem(
    episode = item.episode,
    podcast = item.podcast,
    showPublishDate = true,
    ...
)
```

In `HistoryScreen.kt`:

```kotlin
ListableEpisodeItem(
    episode = item.episode,
    podcast = item.podcast,
    showPublishDate = true,
    ...
)
```

Alle anderen Aufrufe bleiben entweder unveraendert oder werden bewusst nicht erweitert.

## Sprint 0: Analyse und Entscheidung

### Ziel

Die exakte Platzierung und das Datumsformat festlegen.

### Aufgaben

- Bestaetigen, dass `pubDateMs` das gewuenschte Datum ist.
- Bestaetigen, dass Anzeige nur in Favorites und History aktiv sein soll.
- Datumsformat festlegen:
  - bevorzugt lokalisiert `FormatStyle.MEDIUM`
  - alternativ kurz und numerisch, z.B. `24.06.2026`
- Pruefen, ob ein gemeinsamer Format-Helper angelegt werden soll.

### Akzeptanz

- Keine DB- oder Domain-Aenderung noetig.
- Datumsquelle ist eindeutig `EpisodeDisplayModel.pubDateMs`.
- UI-Ziel ist klar: rechte Seite der Podcast-Zeile.

## Sprint 1: Gemeinsame Datumsformatierung

### Ziel

Eine kleine, testbare Formatierungsfunktion bereitstellen.

### Aufgaben

- Helper anlegen oder vorhandene Formatierung extrahieren, z.B.:

```kotlin
fun formatEpisodePublishDate(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String
```

- Null-Handling im UI lassen oder als zweite Funktion anbieten:

```kotlin
fun formatEpisodePublishDateOrNull(epochMs: Long?): String?
```

### Tests

Unit-Tests:

- `null` ergibt `null` oder leere Anzeige, je nach API.
- bekannter Timestamp ergibt erwartbares Datum fuer fixe `ZoneId`.
- sehr alter oder epoch-naher Timestamp crasht nicht.

### Commands

- `./gradlew test --tests "*Date*"` falls ein gezielter Testfilter passt.
- Wenn Filter nicht greift: `./gradlew test`.

### Akzeptanz

- Datumsformatierung ist deterministisch testbar.
- Keine doppelte Datumslogik in mehreren UI-Dateien.

## Sprint 2: `ListableEpisodeItem` erweitern

### Ziel

Die gemeinsame Zeilenkomponente kann optional rechtsbuendig das Erscheinungsdatum anzeigen.

### Aufgaben

- Optionalen Parameter `showPublishDate: Boolean = false` ergaenzen.
- Zweite Zeile als `Row` aufbauen.
- Podcast-Name links mit `Modifier.weight(1f)` rendern.
- Datum rechts nur anzeigen, wenn:
  - `showPublishDate == true`
  - `episode.pubDateMs != null`
- Visuelles Styling:
  - gleicher oder leicht kleinerer Textstil als Podcast-Name
  - Farbe `MaterialTheme.colorScheme.onSurfaceVariant`
  - kein neuer Card-/Badge-Stil

### Layout-Risiken

- Lange Podcast-Titel duerfen das Datum nicht ueberdecken.
- Datum darf keine dritte Zeile erzwingen.
- Auf schmalen Screens muss der Podcast-Titel ellipsieren.

### Tests

Je nach vorhandener Test-Infrastruktur:

- Compose UI Test fuer `ListableEpisodeItem` mit `showPublishDate = true`.
- Compose UI Test fuer `showPublishDate = false`, damit kein Datum sichtbar ist.
- Falls keine Compose-Teststruktur vorhanden ist: mindestens Unit-Test fuer Formatierung plus manuelle Screenshot-Pruefung.

### Akzeptanz

- Bestehendes Default-Verhalten bleibt unveraendert.
- Datum erscheint nur bei aktivierter Option.
- Layout bleibt einzeilig fuer Podcast-Metadaten.

## Sprint 3: Favorites und History aktivieren

### Ziel

Die Datumsanzeige wird nur in Favorites und History eingeschaltet.

### Aufgaben

- In `FavoritesScreen.kt` `showPublishDate = true` setzen.
- In `HistoryScreen.kt` `showPublishDate = true` setzen.
- Andere Aufrufe unveraendert lassen.
- Falls spaeter weitere Screens `ListableEpisodeItem` verwenden, bleibt der Default `false`.

### Tests

Manuelle Sichtpruefung:

- Favorites zeigt rechts das Erscheinungsdatum.
- History zeigt rechts das Erscheinungsdatum.
- Andere Listen zeigen kein neues Datum.
- Eintrag ohne `pubDateMs` zeigt keine leere Luecke oder kaputten Text.

### Commands

- `./gradlew test`
- `./gradlew lint`
- `./gradlew assembleDebug`

### Akzeptanz

- Favorites und History zeigen das Datum.
- Keine Veraenderung an Favoriten-Sortierung, History-Gruppierung, Swipe oder Drag-Reorder.
- Keine Regression beim Click auf Episode oder Bild.

## Sprint 4: Mobile QA und Feinschliff

### Ziel

Die Anzeige wirkt auf kleinen Android-Displays sauber.

### Aufgaben

- Emulator oder physisches Phone pruefen.
- Schmale Breite testen.
- Lange Podcast-Titel testen.
- Datum bei unterschiedlichen Locales pruefen, falls moeglich.
- Dark Mode pruefen.
- One-Handed Mode fuer Favorites und History pruefen.

### Akzeptanz

- Kein Text-Overlap.
- Datum ist rechtsbuendig und lesbar.
- Podcast-Titel ellipsiert sauber.
- Mini-Player, Bottom Navigation und System Navigation Bar werden nicht ueberlappt.

## Testmatrix

| Bereich | Test | Erwartung |
| --- | --- | --- |
| Favorites | Eintrag mit `pubDateMs` | Datum rechts in Podcast-Zeile sichtbar |
| History | Eintrag mit `pubDateMs` | Datum rechts in Podcast-Zeile sichtbar |
| Default | `ListableEpisodeItem` ohne Flag | kein Datum sichtbar |
| Null-Date | `pubDateMs == null` | keine kaputte Anzeige |
| Lange Podcast-Namen | langer Text links | Podcast-Name ellipsiert, Datum bleibt sichtbar |
| Kleine Breite | schmales Phone | kein Overlap |
| Dark Mode | dunkles Theme | Datum lesbar |
| One-Handed Favorites | Modus aktiv | Reorder/Scroll bleibt unveraendert |
| One-Handed History | Modus aktiv | History-Gruppen und Datum bleiben korrekt |
| Swipe Favorite | Swipe-to-remove | funktioniert unveraendert |
| Click Favorite | Episode/Bild antippen | funktioniert unveraendert |
| Click History | Episode antippen | funktioniert unveraendert |

## Risiken

- Wenn das Datumsformat zu lang ist, kann es auf kleinen Screens zu wenig Platz lassen. Deshalb ist ein kurzes lokalisiertes Format oder numerisches Format sinnvoll.
- `ListableEpisodeItem` ist eine gemeinsame Komponente. Der Parameter muss standardmaessig `false` sein, damit andere Screens unveraendert bleiben.
- Compose-UI-Tests koennen fehlen oder aufwaendig sein. Dann ist eine Kombination aus Formatierungs-Unit-Tests und manueller Emulator-Pruefung sinnvoll.

## Definition of Done

- `ListableEpisodeItem` unterstuetzt optionale Erscheinungsdatum-Anzeige.
- Favorites und History aktivieren diese Anzeige.
- Andere Screens bleiben unveraendert.
- Datum stammt aus `EpisodeDisplayModel.pubDateMs`.
- Keine DB-, Repository- oder UseCase-Aenderung.
- `./gradlew test`, `./gradlew lint` und `./gradlew assembleDebug` laufen erfolgreich.
- Mobile Sichtpruefung bestaetigt: kein Overlap, Datum rechtsbuendig, Podcast-Name ellipsiert sauber.
