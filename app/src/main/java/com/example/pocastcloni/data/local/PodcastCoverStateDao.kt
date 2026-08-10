package com.example.pocastcloni.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface PodcastCoverStateDao {
    @Query("SELECT * FROM podcast_cover_state WHERE podcastRssUrl = :rssUrl")
    suspend fun getState(rssUrl: String): PodcastCoverStateEntity?

    @Query("SELECT * FROM podcast_cover_state WHERE podcastRssUrl = :rssUrl")
    fun observeState(rssUrl: String): Flow<PodcastCoverStateEntity?>

    @Query("SELECT * FROM podcast_cover_state ORDER BY podcastRssUrl")
    suspend fun getAllStates(): List<PodcastCoverStateEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(state: PodcastCoverStateEntity): Long

    @Query(
        """
        INSERT OR IGNORE INTO podcast_cover_state (
            podcastRssUrl, activeSourceUrl, pendingSourceUrl, pendingFirstSeenAt,
            thumbnailFileName, thumbnailRevision, lastSuccessfulCheckAt,
            contentSha256, eTag, lastModified, failureCount, nextRetryAt
        )
        SELECT rssUrl, NULL, NULLIF(trim(imageUrl), ''), :now, NULL, 0, NULL,
               NULL, NULL, NULL, 0, NULL
        FROM podcasts
        """
    )
    suspend fun seedMissingPodcastStates(now: Long)

    @Query(
        """
        UPDATE podcast_cover_state
        SET pendingSourceUrl = :pendingSourceUrl,
            pendingFirstSeenAt = :pendingFirstSeenAt,
            failureCount = 0,
            nextRetryAt = NULL
        WHERE podcastRssUrl = :rssUrl
        """
    )
    suspend fun setPendingCandidate(
        rssUrl: String,
        pendingSourceUrl: String?,
        pendingFirstSeenAt: Long?
    ): Int

    /** Records the latest feed candidate without rewriting an unchanged cover row. */
    @Transaction
    @Suppress("ReturnCount")
    suspend fun observeFeedCandidate(
        rssUrl: String,
        sourceUrl: String,
        observedAt: Long
    ): Boolean {
        val normalized = sourceUrl.trim().takeIf(String::isNotEmpty)
        val existing = getState(rssUrl)
        if (existing == null) {
            insertIfMissing(
                PodcastCoverStateEntity(
                    podcastRssUrl = rssUrl,
                    pendingSourceUrl = normalized,
                    pendingFirstSeenAt = normalized?.let { observedAt }
                )
            )
            return normalized != null
        }

        val nextPending = normalized?.takeUnless { it == existing.activeSourceUrl }
        if (nextPending == existing.pendingSourceUrl) return false
        setPendingCandidate(
            rssUrl = rssUrl,
            pendingSourceUrl = nextPending,
            pendingFirstSeenAt = nextPending?.let { observedAt }
        )
        return true
    }

    @Query(
        """
        UPDATE podcast_cover_state
        SET activeSourceUrl = :sourceUrl,
            pendingSourceUrl = CASE WHEN pendingSourceUrl = :sourceUrl THEN NULL ELSE pendingSourceUrl END,
            pendingFirstSeenAt = CASE WHEN pendingSourceUrl = :sourceUrl THEN NULL ELSE pendingFirstSeenAt END,
            thumbnailFileName = :fileName,
            thumbnailRevision = thumbnailRevision + 1,
            lastSuccessfulCheckAt = :checkedAt,
            contentSha256 = :contentSha256,
            eTag = :eTag,
            lastModified = :lastModified,
            failureCount = 0,
            nextRetryAt = NULL
        WHERE podcastRssUrl = :rssUrl
          AND (pendingSourceUrl = :sourceUrl OR (pendingSourceUrl IS NULL AND activeSourceUrl = :sourceUrl))
        """
    )
    suspend fun promoteThumbnail(
        rssUrl: String,
        sourceUrl: String,
        fileName: String,
        checkedAt: Long,
        contentSha256: String,
        eTag: String?,
        lastModified: String?
    ): Int

    @Query(
        """
        UPDATE podcast_cover_state
        SET activeSourceUrl = :sourceUrl,
            pendingSourceUrl = CASE WHEN pendingSourceUrl = :sourceUrl THEN NULL ELSE pendingSourceUrl END,
            pendingFirstSeenAt = CASE WHEN pendingSourceUrl = :sourceUrl THEN NULL ELSE pendingFirstSeenAt END,
            lastSuccessfulCheckAt = :checkedAt,
            eTag = :eTag,
            lastModified = :lastModified,
            failureCount = 0,
            nextRetryAt = NULL
        WHERE podcastRssUrl = :rssUrl
          AND contentSha256 = :contentSha256
          AND thumbnailFileName IS NOT NULL
          AND (pendingSourceUrl = :sourceUrl OR (pendingSourceUrl IS NULL AND activeSourceUrl = :sourceUrl))
        """
    )
    suspend fun promoteIdenticalThumbnail(
        rssUrl: String,
        sourceUrl: String,
        checkedAt: Long,
        contentSha256: String,
        eTag: String?,
        lastModified: String?
    ): Int

    @Query(
        """
        UPDATE podcast_cover_state
        SET lastSuccessfulCheckAt = :checkedAt,
            eTag = COALESCE(:eTag, eTag),
            lastModified = COALESCE(:lastModified, lastModified),
            failureCount = 0,
            nextRetryAt = NULL
        WHERE podcastRssUrl = :rssUrl
          AND activeSourceUrl = :sourceUrl
          AND pendingSourceUrl IS NULL
          AND thumbnailFileName IS NOT NULL
        """
    )
    suspend fun recordNotModified(
        rssUrl: String,
        sourceUrl: String,
        checkedAt: Long,
        eTag: String?,
        lastModified: String?
    ): Int

    @Query(
        """
        UPDATE podcast_cover_state
        SET thumbnailFileName = NULL,
            contentSha256 = NULL,
            eTag = NULL,
            lastModified = NULL,
            nextRetryAt = NULL
        WHERE podcastRssUrl = :rssUrl AND thumbnailFileName = :fileName
        """
    )
    suspend fun clearMissingFile(
        rssUrl: String,
        fileName: String
    ): Int

    @Query(
        """
        UPDATE podcast_cover_state
        SET failureCount = :failureCount,
            nextRetryAt = :nextRetryAt
        WHERE podcastRssUrl = :rssUrl
          AND (pendingSourceUrl = :sourceUrl OR (pendingSourceUrl IS NULL AND activeSourceUrl = :sourceUrl))
        """
    )
    suspend fun recordFailure(
        rssUrl: String,
        sourceUrl: String,
        failureCount: Int,
        nextRetryAt: Long?
    ): Int
}
