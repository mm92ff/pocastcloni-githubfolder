package com.example.pocastcloni.ui.favorites

import com.example.pocastcloni.ui.common.DateBucket
import com.example.pocastcloni.ui.common.toDateBucket
import java.time.ZoneId

fun buildFavoriteDateRows(
    items: List<FavoriteUiItem>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    reverseDisplayOrder: Boolean = false
): List<FavoriteListRow> {
    val sortedItems =
        items.sortedWith(
            compareByDescending<FavoriteUiItem> { it.episode.favoriteAddedAtMs ?: Long.MIN_VALUE }
                .thenBy { it.id }
        )
    val displayItems = if (reverseDisplayOrder) sortedItems.asReversed() else sortedItems
    val seenBuckets = mutableSetOf<DateBucket>()

    return displayItems.flatMap { item ->
        val bucket = item.episode.favoriteAddedAtMs.toDateBucket(nowMillis, zoneId)
        buildList {
            if (seenBuckets.add(bucket)) {
                add(FavoriteListRow.SectionHeader(bucket))
            }
            add(FavoriteListRow.EpisodeRow(item))
        }
    }
}
