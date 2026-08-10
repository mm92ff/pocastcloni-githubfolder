package com.example.pocastcloni.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Persisted lifecycle state for the durable thumbnail of one subscribed podcast. */
@Entity(
    tableName = "podcast_cover_state",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["rssUrl"],
            childColumns = ["podcastRssUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PodcastCoverStateEntity(
    @PrimaryKey val podcastRssUrl: String,
    val activeSourceUrl: String? = null,
    val pendingSourceUrl: String? = null,
    val pendingFirstSeenAt: Long? = null,
    val thumbnailFileName: String? = null,
    val thumbnailRevision: Long = 0L,
    val lastSuccessfulCheckAt: Long? = null,
    val contentSha256: String? = null,
    val eTag: String? = null,
    val lastModified: String? = null,
    val failureCount: Int = 0,
    val nextRetryAt: Long? = null
)
