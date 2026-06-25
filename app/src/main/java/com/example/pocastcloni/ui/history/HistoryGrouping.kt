package com.example.pocastcloni.ui.history

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun buildHistoryRows(
    items: List<HistoryUiItem>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): List<HistoryListRow> {
    val seenBuckets = mutableSetOf<HistoryTimeBucket>()

    return items.flatMap { item ->
        val bucket = item.episode.datePlayedMs.toHistoryTimeBucket(nowMillis, zoneId)
        buildList {
            if (seenBuckets.add(bucket)) {
                add(HistoryListRow.SectionHeader(bucket))
            }
            add(HistoryListRow.EpisodeRow(item))
        }
    }
}

fun Long?.toHistoryTimeBucket(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): HistoryTimeBucket {
    if (this == null) return HistoryTimeBucket.OLDER

    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val playedDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(playedDate, today)

    return when {
        daysAgo <= 0 -> HistoryTimeBucket.TODAY
        daysAgo == 1L -> HistoryTimeBucket.YESTERDAY
        daysAgo <= 7L -> HistoryTimeBucket.LAST_WEEK
        daysAgo <= 30L -> HistoryTimeBucket.LAST_MONTH
        daysAgo <= 60L -> HistoryTimeBucket.LAST_TWO_MONTHS
        daysAgo <= 150L -> HistoryTimeBucket.LAST_FIVE_MONTHS
        daysAgo <= 365L -> HistoryTimeBucket.LAST_YEAR
        else -> HistoryTimeBucket.OLDER
    }
}
