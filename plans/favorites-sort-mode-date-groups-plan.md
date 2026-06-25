# Plan: Favorites Sort Mode and Date Group Separators

## Ziel

Im Favorites-Tab soll eine Umschaltleiste sichtbar sein, mit der zwischen zwei Ansichten gewechselt werden kann:

```text
Manuell | Hinzugefuegt
```

Die Ansicht `Manuell` behaelt die bestehende frei sortierbare Favoritenliste.

Die Ansicht `Hinzugefuegt` sortiert Favoriten nach dem echten Zeitpunkt, zu dem sie zu den Favoriten hinzugefuegt wurden, und zeigt horizontale Datumstrennlinien wie im History-Tab.

## Gewuenschtes Verhalten

### Modus: Manuell

- Bestehende Favoriten-Reihenfolge bleibt erhalten.
- Drag & Drop / Edit Mode bleibt aktiv.
- Edit-Button ist sichtbar.
- Keine Datumstrennlinien, weil die Reihenfolge frei ist.
- Neue Favoriten erscheinen standardmaessig oben.

### Modus: Hinzugefuegt

- Liste ist nach `favoriteAddedAt DESC` sortiert.
- Horizontale Abschnittstrenner erscheinen nach Zeitgruppen:
  - Heute
  - Gestern
  - Letzte Woche
  - Letzter Monat
  - 2 Monate
  - 5 Monate
  - 1 Jahr
  - Aelter
- Edit/Drag-Reorder ist in diesem Modus deaktiviert.
- Swipe-to-remove bleibt erlaubt.
- Episode-Click und Bild-Click bleiben unveraendert.

## Warum eine Datenmodell-Aenderung noetig ist

Aktuell gibt es nur:

```kotlin
favoriteTimestamp: Long?
```

Dieser Wert wird aktuell fuer zwei verschiedene Bedeutungen verwendet:

1. Zeitpunkt des Favorisierens
2. manuelle Reihenfolge nach Drag-Reorder

`ReorderFavoritesUseCase` setzt `favoriteTimestamp = timestamp - index`. Dadurch wird beim manuellen Umsortieren der urspruengliche Hinzufuege-Zeitpunkt ueberschrieben.

Fuer korrektes Verhalten braucht es getrennte Werte:

```text
favoriteAddedAt   = wann wurde die Episode favorisiert
favoriteTimestamp = manuelle Sortierung / bestehende Reihenfolge
```

`favoriteTimestamp` kann als bestehender Sortierwert erhalten bleiben, damit der Eingriff kleiner bleibt. Neu hinzu kommt `favoriteAddedAt`.

## Datenmodell-Ziel

### EpisodeEntity

Neu:

```kotlin
val favoriteAddedAt: Long? = null
```

Bestehend bleibt:

```kotlin
val favoriteTimestamp: Long? = null
```

### EpisodePresentation

Neu:

```kotlin
val favoriteAddedAtMs: Long?
```

### EpisodeDisplayModel

Neu:

```kotlin
val favoriteAddedAtMs: Long?
```

### Beim Favorisieren

Wenn eine Episode neu favorisiert wird:

```text
favoriteAddedAt = now
favoriteTimestamp = now
```

Wenn eine Episode entfernt wird:

```text
isFavorite = false
favoriteAddedAt = null
favoriteTimestamp = null
```

### Beim manuellen Reorder

Beim Drag-Reorder:

```text
favoriteTimestamp wird aktualisiert
favoriteAddedAt bleibt unveraendert
```

## Datenbank-Migration

Aktuelle Version:

```kotlin
Constants.Database.DATABASE_VERSION = 10
```

Ziel:

```kotlin
Constants.Database.DATABASE_VERSION = 11
```

Migration:

```sql
ALTER TABLE episodes ADD COLUMN favoriteAddedAt INTEGER;
UPDATE episodes
SET favoriteAddedAt = favoriteTimestamp
WHERE isFavorite = 1 AND favoriteAddedAt IS NULL;
CREATE INDEX IF NOT EXISTS index_episodes_isFavorite_favoriteAddedAt
ON episodes(isFavorite, favoriteAddedAt);
```

Legacy-Rebuild `createV10Tables` wird nicht veraendert, wenn Migrationen vor v10 weiterhin auf v10 gehen. Fuer frische Installationen mit Version 11 muss aber Room-Schema und Entity zusammenpassen. Deshalb muss geprueft werden, ob die App den aktuellen Schema-Export nutzt. Falls Schema JSONs im Projekt fehlen, muss mindestens `assembleDebug`, `lint` und ggf. Room-Migrationstest die Korrektheit absichern.

## UI-Platzierung

Der Umschalter soll nicht in die Topbar.

Empfohlenes Layout:

```text
[ <- ] Favorites                                      [ Edit ]

[ Manuell ] [ Hinzugefuegt ]

Liste...
```

Technisch:

- `Scaffold` bleibt.
- Im Content-Bereich wird statt nur `Box`/Liste eine `Column` verwendet.
- Direkt unter der Topbar bzw. im Content oberhalb der Liste kommt ein `SingleChoiceSegmentedButtonRow`.
- Der Umschalter ist nur sichtbar, wenn Favoriten vorhanden sind.
- Edit-Button wird nur im Modus `Manuell` angezeigt.

## UI-State

### Enum

```kotlin
enum class FavoritesSortMode {
    MANUAL,
    ADDED_DATE
}
```

### Row-Modell fuer Datumsliste

Analog zu History:

```kotlin
sealed interface FavoriteListRow {
    val key: String

    data class SectionHeader(val bucket: HistoryTimeBucket) : FavoriteListRow
    data class EpisodeRow(val item: FavoriteUiItem) : FavoriteListRow
}
```

Es kann entweder `HistoryTimeBucket` wiederverwendet oder in einen generischeren gemeinsamen Typ umbenannt werden. Empfehlung: fuer kleine Aenderung `HistoryTimeBucket` in einen neutralen Ort verschieben oder `DateBucket` neu einfuehren. Nicht zwei fast gleiche Bucket-Enums bauen.

### FavoritesUiState

Erweitern um:

```kotlin
val sortMode: FavoritesSortMode = FavoritesSortMode.MANUAL
val dateGroupedRows: ImmutableList<FavoriteListRow> = persistentListOf()
```

Optional:

```kotlin
val canEdit: Boolean = sortMode == FavoritesSortMode.MANUAL
```

### Actions

Neu:

```kotlin
data class ChangeSortMode(val mode: FavoritesSortMode) : FavoritesAction
```

Beim Wechsel zu `ADDED_DATE`:

- `isEditMode` muss auf `false` gesetzt werden.
- `_optimisticFavorites` sollte verworfen werden, falls Edit gerade aktiv war.

## Sprint 0: Final Scope und UX-Festlegung

### Ziel

Bestimmen, dass die saubere Variante B umgesetzt wird.

### Aufgaben

- Bestaetigen, dass `favoriteAddedAt` neu eingefuehrt wird.
- Bestaetigen, dass `favoriteTimestamp` als manuelle Sortierung erhalten bleibt.
- Bestaetigen, dass der Umschalter unter der Topbar sitzt.
- Bestaetigen, dass Edit nur in `Manuell` sichtbar ist.
- Bestaetigen, dass Datumstrenner nur in `Hinzugefuegt` sichtbar sind.

### Akzeptanz

- Es gibt eine klare Trennung von Hinzufuegedatum und manueller Reihenfolge.
- Keine UI-Aenderung wird mit DB-Migration vermischt, bevor Datenpfad klar ist.

## Sprint 1: Datenmodell und Migration

### Ziel

`favoriteAddedAt` wird dauerhaft gespeichert und durch den Datenpfad transportiert.

### Aufgaben

- `EpisodeEntity` um `favoriteAddedAt` erweitern.
- `Constants.Database.DATABASE_VERSION` von 10 auf 11 erhoehen.
- Migration v10 -> v11 in `AppDatabaseMigrations.incrementalMigrations` ergaenzen.
- Index fuer `isFavorite, favoriteAddedAt` ergaenzen.
- `EpisodePresentation` um `favoriteAddedAtMs` erweitern.
- `EpisodeDisplayModel` um `favoriteAddedAtMs` erweitern.
- Mapper `EpisodePresentation.from` und `EpisodeDisplayModel.from` anpassen.
- DAO-Update fuer Favoriten so erweitern, dass `favoriteAddedAt` geschrieben/geloescht wird.
- Repository-/UseCase-Aufruf so erweitern, dass beim Favorisieren beide Werte gesetzt werden.
- `ReorderFavoritesUseCase` darf nur `favoriteTimestamp` veraendern, nicht `favoriteAddedAt`.

### Tests

- Migrationstest, falls Room-Migration-Testinfra vorhanden ist.
- Unit-Test fuer `ToggleFavoriteEpisodeUseCase`:
  - neues Favorite setzt `favoriteAddedAt`.
  - Entfernen loescht `favoriteAddedAt`.
- Unit-Test fuer `ReorderFavoritesUseCase`:
  - `favoriteTimestamp` wird neu gesetzt.
  - `favoriteAddedAt` bleibt erhalten.

### Commands

- `./gradlew test`
- `./gradlew lint`
- `./gradlew assembleDebug`

### Akzeptanz

- Bestehende Favoriten migrieren `favoriteAddedAt = favoriteTimestamp`.
- Neue Favoriten bekommen beide Werte.
- Manuelle Sortierung veraendert das Hinzufuegedatum nicht.

## Sprint 2: Gruppierungslogik fuer Favoriten

### Ziel

Favoriten koennen fuer den Modus `Hinzugefuegt` nach Datum gruppiert werden.

### Aufgaben

- Gemeinsame Date-Bucket-Logik aus History wiederverwenden oder neutralisieren.
- Funktion anlegen:

```kotlin
fun buildFavoriteDateRows(
    items: List<FavoriteUiItem>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): List<FavoriteListRow>
```

- Sortierung:

```text
favoriteAddedAtMs DESC
```

- Falls `favoriteAddedAtMs == null`, nach `OLDER` gruppieren.

### Tests

- Heute/Gestern/Letzte Woche usw. analog zu History.
- Header nur einmal pro Bucket.
- Items innerhalb Bucket bleiben nach `favoriteAddedAtMs DESC`.
- Null-Werte landen in `OLDER`.
- One-Handed Mode muss Header vor Episoden behalten, wenn die Reihenfolge gedreht wird.

### Akzeptanz

- Favoriten-DateRows sind deterministisch testbar.
- Keine Datumsberechnung in Composables.

## Sprint 3: Favorites ViewModel State

### Ziel

Der ViewModel-State liefert beide Darstellungsarten.

### Aufgaben

- `FavoritesSortMode` in Contract einfuehren.
- `_sortMode = MutableStateFlow(FavoritesSortMode.MANUAL)` einfuehren.
- `FavoritesUiState.sortMode` setzen.
- `FavoritesUiState.dateGroupedRows` setzen.
- `ChangeSortMode` Action implementieren.
- Beim Wechsel auf `ADDED_DATE`:
  - `isEditMode = false`
  - `_optimisticFavorites = null`
- `OnReorder` nur im Modus `MANUAL` ausfuehren.

### Tests

- Default ist `MANUAL`.
- `ChangeSortMode(ADDED_DATE)` setzt Mode.
- Wechsel zu `ADDED_DATE` beendet Edit Mode.
- Reorder im `ADDED_DATE` Mode ruft `ReorderFavoritesUseCase` nicht auf.
- `dateGroupedRows` enthaelt erwartete Header/EpisodeRows.

### Akzeptanz

- ViewModel entscheidet ueber Modus und Reihenfolge.
- UI bleibt moeglichst dumm.

## Sprint 4: Favorites UI Umschalter

### Ziel

Segmented Control unter der Topbar einfuegen.

### Aufgaben

- Content in `Column` strukturieren.
- `SingleChoiceSegmentedButtonRow` oder passende Material3-Komponente verwenden.
- Labels:
  - `Manuell`
  - `Hinzugefuegt`
- Strings in `strings.xml` anlegen.
- Edit-Button nur anzeigen, wenn:

```text
sortMode == MANUAL
```

- Im `MANUAL` Mode:
  - `ReorderableLazyColumn` mit `favorites`
- Im `ADDED_DATE` Mode:
  - normale `LazyColumn` mit `dateGroupedRows`
  - Header wie History
  - EpisodeRows mit `ListableEpisodeItem(showPublishDate = true)`
  - Swipe-to-remove bleibt moeglich

### Tests

Manuell:

- Umschalter sichtbar.
- Edit-Button sichtbar.
- Reorderable-Liste sichtbar.
- Keine Datumstrenner.

Hinzugefuegt:

- Umschalter sichtbar.
- Edit-Button nicht sichtbar.
- Datumstrenner sichtbar.
- Reihenfolge nach `favoriteAddedAt`.

### Akzeptanz

- Bar ist direkt unter der Topbar sichtbar.
- Kein Overlap mit Liste, Mini-Player oder Bottom Navigation.
- Kleiner Phone-Screen bleibt bedienbar.

## Sprint 5: Persistence des Sortiermodus

### Ziel

Entscheiden, ob die Auswahl nur Session-State ist oder gespeichert wird.

### Empfehlung

Fuer erste Umsetzung:

- Session-State im ViewModel reicht.

Optional spaeter:

- `FavoritesSortMode` in `UserSettings`/DataStore speichern.

### Tests

Wenn persistent:

- Repository/DataStore-Test fuer Speichern/Laden.
- ViewModel startet mit gespeicherter Auswahl.

Wenn nicht persistent:

- Keine Zusatztests noetig.

### Akzeptanz

- Entscheidung ist dokumentiert.
- Kein unbeabsichtigter Settings-Scope entsteht.

## Sprint 6: Full Validation und Emulator QA

### Pflichtchecks

- `./gradlew test`
- `./gradlew lint`
- `./gradlew assembleDebug`

### Emulator/Manuelle Pruefung

- App installieren.
- Favoriten oeffnen.
- Umschalter sichtbar.
- Modus `Manuell`:
  - Edit-Button sichtbar.
  - Drag-Reorder funktioniert.
  - Reihenfolge bleibt nach Reorder.
- Modus `Hinzugefuegt`:
  - Edit-Button weg.
  - horizontale Trennlinien sichtbar.
  - Sortierung nach Hinzufuegedatum.
  - Swipe-to-remove funktioniert.
- Zurueck zu `Manuell`:
  - manuelle Reihenfolge ist noch da.
- One-Handed Mode:
  - Header stehen vor Episoden.
  - Liste ist bedienbar.
- Dark Mode:
  - Segmented Control und Header lesbar.

## Testmatrix

| Bereich | Test | Erwartung |
| --- | --- | --- |
| Migration | v10 Favorit mit `favoriteTimestamp` | `favoriteAddedAt` wird auf gleichen Wert gesetzt |
| Migration | nicht-favorisierte Episode | `favoriteAddedAt` bleibt null |
| Neuer Favorit | Toggle von false auf true | `favoriteTimestamp` und `favoriteAddedAt` werden gesetzt |
| Favorit entfernen | Toggle von true auf false | beide Favorite-Zeitwerte werden null |
| Reorder | manuelle Sortierung | nur `favoriteTimestamp` aendert sich |
| Default Mode | ViewModel Start | `MANUAL` |
| Mode Toggle | `ADDED_DATE` | Edit Mode aus, Gruppierung sichtbar |
| Manual UI | `MANUAL` | Edit sichtbar, keine Header |
| Added UI | `ADDED_DATE` | Edit weg, Header sichtbar |
| Grouping | mehrere Favoriten gleicher Tag | ein Header pro Bucket |
| Grouping | verschiedene Tage | Buckets in Datumssortierung |
| Null AddedAt | fehlender Wert | `OLDER` |
| Swipe | Added Mode | Entfernen funktioniert |
| Reorder Guard | Added Mode | Reorder wird ignoriert |
| One-Handed | Added Mode | Header vor Episoden |
| Publish Date | beide Modes | Erscheinungsdatum pro Eintrag bleibt sichtbar |

## Risiken

- DB-Migration erfordert sorgfaeltige Versionierung. Ohne korrektes Schema kann die App beim Start crashen.
- Bestehende Favoriten haben historisch nur `favoriteTimestamp`; Migration kann nur bestmoeglich rueckfuellen.
- Segmented Control kann auf kleinen Screens Platz brauchen; deshalb nicht in die Topbar.
- Reorder und Header duerfen nicht in derselben Liste vermischt werden, sonst stimmen Drag-Indizes nicht.
- Wenn `favoriteAddedAt` fehlt, muss defensiv auf `OLDER` oder `favoriteTimestamp` als Fallback entschieden werden. Empfehlung: Migration fuellt bestehende Werte, danach null nur als Edge Case.

## Definition of Done

- Favorites-Tab zeigt eine Umschaltleiste `Manuell | Hinzugefuegt` unter der Topbar.
- `Manuell` behaelt bestehende Drag-Reorder-Funktion.
- `Hinzugefuegt` zeigt nach Hinzufuegedatum sortierte Gruppen mit horizontalen Trennlinien.
- `favoriteAddedAt` ist getrennt von manueller Sortierung gespeichert.
- Existing Favorites werden migriert.
- Edit-Button ist nur im manuellen Modus sichtbar.
- Alle Pflichtchecks laufen gruen.
- APK ist im Emulator installierbar und die UI ist dort pruefbar.
