package com.example.pocastcloni.data.local

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Date

data class PodcastWithEpisodes(
    @Embedded val podcast: PodcastEntity,
    @Relation(
        parentColumn = "rssUrl",
        entityColumn = "podcastRssUrl"
    )
    val episodes: List<EpisodeEntity>
)

data class PodcastLite(
    val rssUrl: String,
    val title: String,
    val imageUrl: String
)

data class EpisodeWithPodcastLite(
    @Embedded val episode: EpisodeEntity,
    @Embedded(prefix = "podcast_") val podcast: PodcastLite?
)

data class DownloadedPathRow(
    val episodeId: Long,
    val downloadPath: String?
)

data class EpisodeDownloadStateRow(
    val episodeId: Long,
    val downloadStatus: DownloadStatus,
    val downloadPath: String?
)

data class PodcastUnplayedCount(
    @ColumnInfo(name = "podcastRssUrl") val rssUrl: String,
    @ColumnInfo(name = "count") val count: Int
)

/**
 * NEW: Partial entity for sort order updates.
 * Contains ONLY the ID and the new position.
 * Prevents background updates (e.g. hasNewEpisodes) from being overwritten during reordering.
 */
@Entity
data class PodcastSortUpdate(
    @ColumnInfo(name = "rssUrl") val rssUrl: String,
    @ColumnInfo(name = "sortOrder") val sortOrder: Long
)

data class PodcastFeedUpdate(
    val rssUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val lastRefreshed: Date,
    val lastModifiedHeader: String?,
    val eTagHeader: String?
)

data class EpisodeFeedUpdate(
    val podcastRssUrl: String,
    val guid: String,
    val title: String,
    val description: String,
    val pubDate: Date?,
    val duration: Long,
    val link: String,
    val enclosureUrl: String,
    val fileSize: Long,
    val type: String
)

@Entity
data class FavoriteOrderUpdate(
    @ColumnInfo(name = "episodeId") val episodeId: Long,
    @ColumnInfo(name = "favoriteTimestamp") val favoriteTimestamp: Long
)

@Dao
interface PodcastDao {
    // --- PODCASTS ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPodcasts(podcasts: List<PodcastEntity>)

    suspend fun updatePodcastFromFeed(update: PodcastFeedUpdate): Int =
        updatePodcastFromFeedFields(
            rssUrl = update.rssUrl,
            title = update.title,
            description = update.description,
            imageUrl = update.imageUrl,
            lastRefreshed = update.lastRefreshed,
            lastModifiedHeader = update.lastModifiedHeader,
            eTagHeader = update.eTagHeader
        )

    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE podcasts
        SET title = :title,
            description = :description,
            imageUrl = :imageUrl,
            lastRefreshed = :lastRefreshed,
            lastModifiedHeader = :lastModifiedHeader,
            eTagHeader = :eTagHeader
        WHERE rssUrl = :rssUrl
        """
    )
    suspend fun updatePodcastFromFeedFields(
        rssUrl: String,
        title: String,
        description: String,
        imageUrl: String,
        lastRefreshed: Date,
        lastModifiedHeader: String?,
        eTagHeader: String?
    ): Int

    @Query("UPDATE podcasts SET lastRefreshed = :lastRefreshed WHERE rssUrl = :rssUrl")
    suspend fun updatePodcastLastRefreshed(
        rssUrl: String,
        lastRefreshed: Date
    ): Int

    @Query("SELECT autoDownloadEnabled FROM podcasts WHERE rssUrl = :rssUrl")
    suspend fun getPodcastAutoDownloadEnabled(rssUrl: String): Boolean?

    @Query(
        """
        UPDATE podcasts
        SET allowInsecureHttp = allowInsecureHttp OR :allowInsecureHttp,
            allowLocalNetwork = allowLocalNetwork OR :allowLocalNetwork
        WHERE rssUrl = :rssUrl
        """
    )
    suspend fun approvePodcastNetworkAccess(
        rssUrl: String,
        allowInsecureHttp: Boolean,
        allowLocalNetwork: Boolean
    ): Int

    // NEW: Update method for efficient, partial reordering without data loss
    @Update(entity = PodcastEntity::class)
    suspend fun updatePodcastSortOrders(updates: List<PodcastSortUpdate>)

    @Delete
    suspend fun deletePodcast(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts")
    suspend fun deleteAllPodcasts()

    @Query("DELETE FROM podcasts WHERE rssUrl = :url")
    suspend fun deletePodcastByUrl(url: String)

    @Query("DELETE FROM episodes WHERE podcastRssUrl = :url")
    suspend fun deleteEpisodesByPodcastUrl(url: String)

    @Transaction
    suspend fun deletePodcastAtomic(url: String) {
        deleteEpisodesByPodcastUrl(url)
        deletePodcastByUrl(url)
    }

    @Query("UPDATE podcasts SET autoDownloadEnabled = :enabled WHERE rssUrl = :url")
    suspend fun updateAutoDownloadEnabled(
        url: String,
        enabled: Boolean
    )

    @Transaction
    @Query("SELECT * FROM podcasts ORDER BY sortOrder ASC, rssUrl ASC")
    fun getPodcastsWithEpisodesFlow(): Flow<List<PodcastWithEpisodes>>

    @Query("SELECT * FROM podcasts ORDER BY sortOrder ASC, rssUrl ASC")
    fun getAllPodcastsFlow(): Flow<List<PodcastEntity>>

    @Query("SELECT rssUrl FROM podcasts")
    suspend fun getAllPodcastUrls(): List<String>

    @Query("SELECT rssUrl, autoDownloadEnabled FROM podcasts ORDER BY sortOrder ASC, rssUrl ASC")
    suspend fun getAllPodcastsSyncInfo(): List<PodcastSyncInfo>

    @Query("SELECT * FROM podcasts ORDER BY sortOrder ASC, rssUrl ASC")
    suspend fun getAllPodcastsForExport(): List<PodcastEntity>

    @Query("SELECT MAX(sortOrder) FROM podcasts")
    suspend fun getMaxSortOrder(): Long?

    @Query("SELECT * FROM podcasts WHERE rssUrl = :url")
    fun getPodcastFlow(url: String): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcasts WHERE rssUrl = :url")
    suspend fun getPodcastByUrl(url: String): PodcastEntity?

    @Query("SELECT rssUrl FROM podcasts")
    fun getSubscribedUrlsFlow(): Flow<List<String>>

    @Query("UPDATE podcasts SET hasNewEpisodes = 0")
    suspend fun markAllAsSeen()

    @Query("UPDATE podcasts SET hasNewEpisodes = :hasNew WHERE rssUrl = :url")
    suspend fun updatePodcastNewFlag(
        url: String,
        hasNew: Boolean
    )

    @Query("UPDATE podcasts SET hasNewEpisodes = 1 WHERE rssUrl = :url")
    suspend fun markPodcastHasNewEpisodes(url: String): Int

    @Query("UPDATE podcasts SET isLatestEpisodePlayed = :isPlayed WHERE rssUrl = :url")
    suspend fun updateLatestEpisodePlayedFlag(url: String, isPlayed: Boolean)

    @Transaction
    suspend fun markAllAsSeenAtomic() {
        markLatestEpisodesAsPlayedBatch()
        markAllAsSeen()
    }

    @Transaction
    @Query(
        """
        UPDATE podcasts 
        SET sortOrder = 
            CASE 
                WHEN rssUrl = :url1 THEN (SELECT sortOrder FROM podcasts WHERE rssUrl = :url2)
                WHEN rssUrl = :url2 THEN (SELECT sortOrder FROM podcasts WHERE rssUrl = :url1)
            END
        WHERE rssUrl IN (:url1, :url2)
        """
    )
    suspend fun swapSortOrder(
        url1: String,
        url2: String
    )

    // --- EPISODES ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEpisode(episode: EpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodesIgnore(episodes: List<EpisodeEntity>): List<Long>

    suspend fun updateEpisodeMetadataByFeedKey(update: EpisodeFeedUpdate) =
        updateEpisodeMetadataByFeedKeyFields(
            podcastRssUrl = update.podcastRssUrl,
            guid = update.guid,
            title = update.title,
            description = update.description,
            pubDate = update.pubDate,
            duration = update.duration,
            link = update.link,
            enclosureUrl = update.enclosureUrl,
            fileSize = update.fileSize,
            type = update.type
        )

    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE episodes
        SET title = :title,
            description = :description,
            pubDate = :pubDate,
            duration = :duration,
            link = :link,
            enclosureUrl = :enclosureUrl,
            fileSize = :fileSize,
            type = :type
        WHERE podcastRssUrl = :podcastRssUrl AND guid = :guid
        """
    )
    suspend fun updateEpisodeMetadataByFeedKeyFields(
        podcastRssUrl: String,
        guid: String,
        title: String,
        description: String,
        pubDate: Date?,
        duration: Long,
        link: String,
        enclosureUrl: String,
        fileSize: Long,
        type: String
    )

    /**
     * Efficient upsert: insert new ones, update ONLY metadata for existing ones.
     */
    @Transaction
    suspend fun upsertEpisodesEfficient(episodes: List<EpisodeEntity>): List<Long> {
        if (episodes.isEmpty()) return emptyList()

        // 1. Insert new episodes (existing ones are ignored)
        val insertResults = insertEpisodesIgnore(episodes)

        // 2. Metadata update for ALL episodes (including existing ones)
        // Accesses the fields in EpisodeEntity
        episodes.forEach { episode ->
            updateEpisodeMetadataByFeedKey(
                EpisodeFeedUpdate(
                    podcastRssUrl = episode.podcastRssUrl,
                    guid = episode.guid,
                    title = episode.title,
                    description = episode.description,
                    pubDate = episode.pubDate,
                    duration = episode.duration,
                    link = episode.link,
                    enclosureUrl = episode.enclosureUrl,
                    fileSize = episode.fileSize,
                    type = episode.type
                )
            )
        }
        return insertResults
    }

    @Delete
    suspend fun deleteEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE podcastRssUrl = :rssUrl ORDER BY COALESCE(pubDate, 0) DESC")
    fun getEpisodesFlow(rssUrl: String): Flow<List<EpisodeEntity>>

    // NEW: Paging source for infinite lists
    @Query("SELECT * FROM episodes WHERE podcastRssUrl = :rssUrl ORDER BY COALESCE(pubDate, 0) DESC")
    fun getEpisodesPagingSource(rssUrl: String): PagingSource<Int, EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE podcastRssUrl = :rssUrl ORDER BY COALESCE(pubDate, 0) DESC")
    suspend fun getEpisodesForPodcastSync(rssUrl: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE downloadStatus IN (:statuses) ORDER BY COALESCE(pubDate, 0) DESC")
    fun getDownloadedEpisodes(statuses: List<DownloadStatus>): Flow<List<EpisodeEntity>>

    @Query("SELECT episodeId, downloadStatus, downloadPath FROM episodes WHERE downloadStatus IN (:statuses)")
    suspend fun getEpisodeDownloadStates(statuses: List<DownloadStatus>): List<EpisodeDownloadStateRow>

    @Query(
        """
        SELECT
            e.*,
            p.rssUrl AS podcast_rssUrl,
            p.title AS podcast_title,
            p.imageUrl AS podcast_imageUrl
        FROM episodes e
        LEFT JOIN podcasts p ON p.rssUrl = e.podcastRssUrl
        WHERE e.downloadStatus IN (:statuses)
        ORDER BY COALESCE(e.pubDate, 0) DESC
        """
    )
    fun getDownloadedEpisodesWithPodcastLiteFlow(statuses: List<DownloadStatus>): Flow<List<EpisodeWithPodcastLite>>

    @Query("SELECT * FROM episodes WHERE downloadStatus = :status AND isPlayed = 1")
    suspend fun getPlayedDownloadedEpisodes(status: DownloadStatus = DownloadStatus.DOWNLOADED): List<EpisodeEntity>

    @Query(
        """
        SELECT episodeId, downloadPath
        FROM episodes
        WHERE downloadStatus = 'DOWNLOADED'
          AND isPlayed = 1
          AND downloadPath IS NOT NULL
        """
    )
    suspend fun getPlayedDownloadedEpisodePaths(): List<DownloadedPathRow>

    @Query(
        """
        UPDATE episodes
        SET downloadStatus = :newStatus,
            downloadPath = NULL
        WHERE downloadStatus = :oldStatus
          AND isPlayed = 1
        """
    )
    suspend fun bulkResetPlayedDownloadedEpisodes(
        oldStatus: DownloadStatus = DownloadStatus.DOWNLOADED,
        newStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED
    )

    @Query("SELECT * FROM episodes WHERE episodeId = :episodeId")
    suspend fun getEpisodeById(episodeId: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE podcastRssUrl = :podcastRssUrl AND guid = :guid")
    suspend fun getEpisodeByFeedAndGuid(
        podcastRssUrl: String,
        guid: String
    ): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE episodes.guid = :guid")
    suspend fun getEpisodesByLegacyGuid(guid: String): List<EpisodeEntity>

    @Query("SELECT guid FROM episodes WHERE podcastRssUrl = :url ORDER BY COALESCE(pubDate, 0) DESC LIMIT 1")
    suspend fun getLatestEpisodeGuid(url: String): String?

    @Query("SELECT isPlayed FROM episodes WHERE podcastRssUrl = :url ORDER BY COALESCE(pubDate, 0) DESC LIMIT 1")
    suspend fun isLatestEpisodePlayed(url: String): Boolean?

    // --- SEARCH (Optimized with FTS) ---
    // Uses JOIN on episodes_fts for fast full-text search
    @Query(
        """
        SELECT e.* FROM episodes e
        JOIN episodes_fts fts ON e.rowid = fts.rowid
        WHERE episodes_fts MATCH :query
        ORDER BY COALESCE(e.pubDate, 0) DESC
        """
    )
    fun searchEpisodes(query: String): Flow<List<EpisodeEntity>>

    // --- FAVORITES ---

    @Query(
        """
        UPDATE episodes
        SET isFavorite = :isFavorite,
            favoriteTimestamp = :timestamp,
            favoriteAddedAt = :favoriteAddedAt
        WHERE episodeId = :episodeId
        """
    )
    suspend fun setFavoriteStatus(
        episodeId: Long,
        isFavorite: Boolean,
        timestamp: Long?,
        favoriteAddedAt: Long?
    )

    @Query(
        """
        SELECT * FROM episodes
        WHERE isFavorite = 1
        ORDER BY favoriteTimestamp DESC, favoriteAddedAt DESC, podcastRssUrl ASC, guid ASC
        """
    )
    fun getFavoriteEpisodes(): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT
            e.*,
            p.rssUrl AS podcast_rssUrl,
            p.title AS podcast_title,
            p.imageUrl AS podcast_imageUrl
        FROM episodes e
        LEFT JOIN podcasts p ON p.rssUrl = e.podcastRssUrl
        WHERE e.isFavorite = 1
        ORDER BY e.favoriteTimestamp DESC, e.favoriteAddedAt DESC, e.podcastRssUrl ASC, e.guid ASC
        """
    )
    fun getFavoriteEpisodesWithPodcastLiteFlow(): Flow<List<EpisodeWithPodcastLite>>

    @Query(
        """
        SELECT * FROM episodes
        WHERE isFavorite = 1
        ORDER BY favoriteTimestamp DESC, favoriteAddedAt DESC, podcastRssUrl ASC, guid ASC
        """
    )
    suspend fun getFavoriteEpisodesSync(): List<EpisodeEntity>

    @Query(
        """
        SELECT * FROM episodes
        WHERE isFavorite = 1 OR isPlayed = 1 OR playbackPositionMs > 0
        ORDER BY
            CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END ASC,
            favoriteTimestamp DESC,
            favoriteAddedAt DESC,
            podcastRssUrl ASC,
            guid ASC
        """
    )
    suspend fun getPortableEpisodeStatesForExport(): List<EpisodeEntity>

    @Update(entity = EpisodeEntity::class)
    suspend fun updateFavoriteOrderRows(updates: List<FavoriteOrderUpdate>): Int

    @Transaction
    suspend fun updateFavoriteOrder(updates: List<FavoriteOrderUpdate>) {
        check(updateFavoriteOrderRows(updates) == updates.size) {
            "Favorite reorder referenced a missing episode"
        }
    }

    @Query("SELECT isFavorite FROM episodes WHERE episodeId = :episodeId")
    fun isFavorite(episodeId: Long): Flow<Boolean>

    // --- STATUS UPDATES ---

    @Query("UPDATE episodes SET playbackPositionMs = :pos WHERE episodeId = :episodeId")
    suspend fun updateEpisodeProgressOnly(
        episodeId: Long,
        pos: Long
    )

    @Query("UPDATE episodes SET isPlayed = :isPlayed, datePlayed = :datePlayed WHERE episodeId = :episodeId")
    suspend fun markEpisodePlayed(
        episodeId: Long,
        isPlayed: Boolean,
        datePlayed: Date?
    )

    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE episodes
        SET isFavorite = :isFavorite,
            favoriteAddedAt = :favoriteAddedAt,
            favoriteTimestamp = CASE WHEN :isFavorite THEN favoriteTimestamp ELSE NULL END,
            isPlayed = :isPlayed,
            datePlayed = :datePlayed,
            playbackPositionMs = :playbackPositionMs,
            duration = CASE WHEN :restoreDuration = 1 THEN :duration ELSE duration END
        WHERE episodeId = :episodeId
        """
    )
    suspend fun updatePortableEpisodeState(
        episodeId: Long,
        isFavorite: Boolean,
        favoriteAddedAt: Long?,
        isPlayed: Boolean,
        datePlayed: Date?,
        playbackPositionMs: Long,
        duration: Long,
        restoreDuration: Boolean
    ): Int

    @Query("UPDATE episodes SET isPlayed = 1, datePlayed = :datePlayed WHERE episodeId = :episodeId AND isPlayed = 0")
    suspend fun markEpisodePlayedIfNeeded(
        episodeId: Long,
        datePlayed: Date
    ): Int

    @Query("UPDATE episodes SET isPlayed = 1, datePlayed = :datePlayed WHERE podcastRssUrl = :rssUrl AND isPlayed = 0")
    suspend fun markPodcastEpisodesPlayed(
        rssUrl: String,
        datePlayed: Date = Date()
    )

    @Query("UPDATE episodes SET downloadStatus = :status, downloadPath = :path WHERE episodeId = :episodeId")
    suspend fun updateDownloadStatus(
        episodeId: Long,
        status: DownloadStatus,
        path: String?
    )

    @Query(
        """
        UPDATE episodes
        SET downloadStatus = :newStatus,
            downloadPath = NULL
        WHERE downloadStatus IN (:oldStatuses)
        """
    )
    suspend fun bulkResetDownloadStates(
        oldStatuses: List<DownloadStatus>,
        newStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED
    ): Int

    @Query(
        """
        SELECT * FROM episodes 
        WHERE podcastRssUrl = :rssUrl 
          AND episodeId NOT IN (
              SELECT episodeId FROM episodes
              WHERE podcastRssUrl = :rssUrl 
              ORDER BY COALESCE(pubDate, 0) DESC 
              LIMIT :keepCount
          )
        """
    )
    suspend fun getOldEpisodesToRemove(
        rssUrl: String,
        keepCount: Int
    ): List<EpisodeEntity>

    @Query(
        """
        DELETE FROM episodes 
        WHERE podcastRssUrl = :rssUrl 
          AND episodeId NOT IN (
              SELECT episodeId FROM episodes
              WHERE podcastRssUrl = :rssUrl 
              ORDER BY COALESCE(pubDate, 0) DESC 
              LIMIT :keepCount
          )
        """
    )
    suspend fun deleteOldEpisodesExceedingCount(
        rssUrl: String,
        keepCount: Int
    )

    // --- HISTORY / IN PROGRESS ---
    @Query("SELECT * FROM episodes WHERE isPlayed = 1 ORDER BY datePlayed DESC")
    fun getPlaybackHistory(): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT
            e.*,
            p.rssUrl AS podcast_rssUrl,
            p.title AS podcast_title,
            p.imageUrl AS podcast_imageUrl
        FROM episodes e
        LEFT JOIN podcasts p ON p.rssUrl = e.podcastRssUrl
        WHERE e.isPlayed = 1
        ORDER BY e.datePlayed DESC
        """
    )
    fun getPlaybackHistoryWithPodcastLiteFlow(): Flow<List<EpisodeWithPodcastLite>>

    @Query(
        """
        SELECT
            e.*,
            p.rssUrl AS podcast_rssUrl,
            p.title AS podcast_title,
            p.imageUrl AS podcast_imageUrl
        FROM episodes e
        LEFT JOIN podcasts p ON p.rssUrl = e.podcastRssUrl
        WHERE e.playbackPositionMs > 0
          AND e.isPlayed = 0
        ORDER BY COALESCE(e.datePlayed, 0) DESC, COALESCE(e.pubDate, 0) DESC
        """
    )
    fun getEpisodesInProgressWithPodcastLiteFlow(): Flow<List<EpisodeWithPodcastLite>>

    // Badges for UI
    @Query("SELECT podcastRssUrl, COUNT(guid) as count FROM episodes WHERE isPlayed = 0 GROUP BY podcastRssUrl")
    fun getUnplayedCountsFlow(): Flow<List<PodcastUnplayedCount>>

    @Query("UPDATE episodes SET isPlayed = 0, datePlayed = null WHERE isPlayed = 1")
    suspend fun clearHistory()

    // --- BATCH OPERATIONS ---

    @Query(
        """
        UPDATE episodes
        SET isPlayed = 1
        WHERE episodeId IN (
            SELECT e.episodeId
            FROM episodes e
            INNER JOIN (
                SELECT podcastRssUrl, MAX(COALESCE(pubDate, 0)) as maxDate
                FROM episodes
                GROUP BY podcastRssUrl
            ) latest ON e.podcastRssUrl = latest.podcastRssUrl
                     AND COALESCE(e.pubDate, 0) = latest.maxDate
        )
        """
    )
    suspend fun markLatestEpisodesAsPlayedBatch()

    @Query(
        """
        SELECT e.* FROM episodes e
        INNER JOIN podcasts p ON e.podcastRssUrl = p.rssUrl
        WHERE p.autoDownloadEnabled = 1
          AND e.isPlayed = 0
          AND e.downloadStatus NOT IN (:excludeStatuses)
        ORDER BY COALESCE(e.pubDate, 0) DESC
        """
    )
    suspend fun getAutoDownloadCandidates(
        excludeStatuses: List<DownloadStatus> = listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADING)
    ): List<EpisodeEntity>

    // --- STATISTICS ---

    @Query("SELECT COUNT(guid) FROM episodes")
    fun getTotalEpisodeCount(): Flow<Int>

    @Query("SELECT COUNT(guid) FROM episodes WHERE isPlayed = 1")
    fun getPlayedEpisodesCount(): Flow<Int>

    @Query("SELECT COUNT(guid) FROM episodes WHERE playbackPositionMs > 0 AND isPlayed = 0")
    fun getEpisodesInProgressCount(): Flow<Int>
}
