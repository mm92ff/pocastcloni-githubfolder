package com.example.pocastcloni.ui.history

import com.example.pocastcloni.ui.common.DateBucket
import com.example.pocastcloni.ui.common.toDateBucket
import java.time.ZoneId

fun buildHistoryRows(
    items: List<HistoryUiItem>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): List<HistoryListRow> {
    val seenBuckets = mutableSetOf<DateBucket>()

    return items.flatMap { item ->
        val bucket = item.episode.datePlayedMs.toDateBucket(nowMillis, zoneId)
        buildList {
            if (seenBuckets.add(bucket)) {
                add(HistoryListRow.SectionHeader(bucket))
            }
            add(HistoryListRow.EpisodeRow(item))
        }
    }
}
