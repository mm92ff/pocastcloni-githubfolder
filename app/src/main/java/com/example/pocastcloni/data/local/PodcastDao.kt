package com.example.pocastcloni.data.local

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Date

data class PodcastLite(
    val rssUrl: String,
    val title: String,
    val imageUrl: String,
    val coverFileName: String?,
    val coverRevision: Long
)

data class PodcastWithCover(
    @Embedded val podcast: PodcastEntity,
    val coverFileName: String?,
    val coverRevision: Long
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

/** Updates only a podcast's user-controlled sort position. */
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

/** Updates only the manual order of a favorite episode. */
data class FavoriteOrderUpdate(
    @ColumnInfo(name = "episodeId") val episodeId: Long,
    @ColumnInfo(name = "favoriteTimestamp") val favoriteTimestamp: Long
)

/**
 * Room persistence boundary for podcasts and episodes.
 *
 * Feed refreshes update only feed-owned metadata. Playback, favorite, download, and ordering state
 * remain user-owned. Date-ordered episode collections use [EpisodeEntity.episodeId] as their final
 * tie-breaker so paging and reactive lists remain deterministic.
 */
@Suppress("TooManyFunctions")
@Dao
interface PodcastDao : PodcastBadgeReconciliationDao {
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

    /** Updates feed-owned podcast metadata without touching user settings or ordering. */
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

    /**
     * Adds network permissions to an existing subscription.
     *
     * Approval is monotonic: a `false` argument keeps the stored permission unchanged.
     */
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

    @Update(entity = PodcastEntity::class)
    suspend fun updatePodcastSortOrders(updates: List<PodcastSortUpdate>)

    @Delete
    suspend fun deletePodcast(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts")
    suspend fun deleteAllPodcasts()

    @Query("DELETE FROM podcasts WHERE rssUrl = :rssUrl")
    suspend fun deletePodcastByUrl(rssUrl: String): Int

    @Query("DELETE FROM episodes WHERE podcastRssUrl = :rssUrl")
    suspend fun deleteEpisodesByPodcastUrl(rssUrl: String): Int

    @Transaction
    suspend fun deletePodcastAtomic(rssUrl: String) {
        deleteEpisodesByPodcastUrl(rssUrl)
        deletePodcastByUrl(rssUrl)
    }

    @Query("UPDATE podcasts SET autoDownloadEnabled = :enabled WHERE rssUrl = :rssUrl")
    suspend fun updateAutoDownloadEnabled(
        rssUrl: String,
        enabled: Boolean
    ): Int

    @Query("SELECT rssUrl FROM podcasts WHERE allowLocalNetwork = 1 ORDER BY rssUrl ASC")
    fun getApprovedLocalFeedUrlsFlow(): Flow<List<String>>

    @Query("SELECT * FROM podcasts ORDER BY sortOrder ASC, rssUrl ASC")
    fun getAllPodcastsFlow(): Flow<List<PodcastEntity>>

    @Query(
        """
        SELECT p.*,
               c.thumbnailFileName AS coverFileName,
               COALESCE(c.thumbnailRevision, 0) AS coverRevision
        FROM podcasts p
        LEFT JOIN podcast_cover_state c ON c.podcastRssUrl = p.rssUrl
        ORDER BY p.sortOrder ASC, p.rssUrl ASC
        """
    )
    fun getAllPodcastsWithCoverFlow(): Flow<List<PodcastWithCover>>

    @Query("SELECT rssUrl FROM podcasts ORDER BY rssUrl ASC")
    suspend fun getAllPodcastUrls(): List<String>

    @Query("SELECT * FROM podcasts ORDER BY sortOrder ASC, rssUrl ASC")
    suspend fun getAllPodcastsForExport(): List<PodcastEntity>

    @Query("SELECT MAX(sortOrder) FROM podcasts")
    suspend fun getMaxSortOrder(): Long?

    @Query("SELECT * FROM podcasts WHERE rssUrl = :rssUrl")
    fun getPodcastFlow(rssUrl: String): Flow<PodcastEntity?>

    @Query(
        """
        SELECT p.*,
               c.thumbnailFileName AS coverFileName,
               COALESCE(c.thumbnailRevision, 0) AS coverRevision
        FROM podcasts p
        LEFT JOIN podcast_cover_state c ON c.podcastRssUrl = p.rssUrl
        WHERE p.rssUrl = :rssUrl
        """
    )
    fun getPodcastWithCoverFlow(rssUrl: String): Flow<PodcastWithCover?>

    @Query("SELECT * FROM podcasts WHERE rssUrl = :rssUrl")
    suspend fun getPodcastByUrl(rssUrl: String): PodcastEntity?

    @Query(
        """
        SELECT p.*,
               c.thumbnailFileName AS coverFileName,
               COALESCE(c.thumbnailRevision, 0) AS coverRevision
        FROM podcasts p
        LEFT JOIN podcast_cover_state c ON c.podcastRssUrl = p.rssUrl
        WHERE p.rssUrl = :rssUrl
        """
    )
    suspend fun getPodcastWithCoverByUrl(rssUrl: String): PodcastWithCover?

    @Query("SELECT rssUrl FROM podcasts ORDER BY rssUrl ASC")
    fun getSubscribedUrlsFlow(): Flow<List<String>>

    /** Acknowledges each current latest episode without changing playback state or history. */
    @Query(
        """
        UPDATE podcasts
        SET hasNewEpisodes = 0,
            lastSeenEpisodeGuid = (
                SELECT guid
                FROM episodes
                WHERE podcastRssUrl = podcasts.rssUrl
                ORDER BY pubDate DESC, episodeId DESC
                LIMIT 1
            ),
            isLatestEpisodePlayed = (
                SELECT isPlayed
                FROM episodes
                WHERE podcastRssUrl = podcasts.rssUrl
                ORDER BY pubDate DESC, episodeId DESC
                LIMIT 1
            )
        """
    )
    suspend fun markAllAsSeen(): Int

    @Query("UPDATE podcasts SET hasNewEpisodes = :hasNew WHERE rssUrl = :rssUrl")
    suspend fun updatePodcastNewFlag(
        rssUrl: String,
        hasNew: Boolean
    ): Int

    @Query("UPDATE podcasts SET hasNewEpisodes = 1 WHERE rssUrl = :rssUrl")
    suspend fun markPodcastHasNewEpisodes(rssUrl: String): Int

    @Query("UPDATE podcasts SET isLatestEpisodePlayed = :isPlayed WHERE rssUrl = :rssUrl")
    suspend fun updateLatestEpisodePlayedFlag(rssUrl: String, isPlayed: Boolean): Int

    @Query(
        """
        UPDATE podcasts
        SET hasNewEpisodes = :hasNewEpisodes,
            lastSeenEpisodeGuid = :lastSeenEpisodeGuid,
            isLatestEpisodePlayed = :isLatestEpisodePlayed
        WHERE rssUrl = :rssUrl
          AND (
              hasNewEpisodes != :hasNewEpisodes
              OR lastSeenEpisodeGuid IS NOT :lastSeenEpisodeGuid
              OR isLatestEpisodePlayed IS NOT :isLatestEpisodePlayed
          )
        """
    )
    suspend fun updatePodcastEpisodeBadgeState(
        rssUrl: String,
        hasNewEpisodes: Boolean,
        lastSeenEpisodeGuid: String?,
        isLatestEpisodePlayed: Boolean?
    ): Int

    @Query(
        """
        UPDATE podcasts
        SET lastSeenEpisodeGuid = NULL
        WHERE rssUrl = :rssUrl
          AND lastSeenEpisodeGuid = :episodeGuid
        """
    )
    suspend fun clearLastSeenEpisodeIfMatches(
        rssUrl: String,
        episodeGuid: String
    ): Int

    /** Recomputes one podcast badge from its latest episode and persisted seen baseline. */
    @Transaction
    suspend fun reconcilePodcastLatestEpisodeBadge(
        rssUrl: String,
        acknowledgeCurrentEpisode: Boolean = false
    ): Int {
        val podcast = getPodcastByUrl(rssUrl) ?: return 0
        val latestGuid = getLatestEpisodeGuid(rssUrl)
        val latestPlayed = isLatestEpisodePlayed(rssUrl)
        val lastSeenGuid =
            when {
                latestGuid == null -> null
                acknowledgeCurrentEpisode || latestPlayed == true -> latestGuid
                else -> podcast.lastSeenEpisodeGuid
            }
        val hasNewEpisodes =
            latestGuid != null && latestPlayed == false && lastSeenGuid != latestGuid
        return updatePodcastEpisodeBadgeState(
            rssUrl = rssUrl,
            hasNewEpisodes = hasNewEpisodes,
            lastSeenEpisodeGuid = lastSeenGuid,
            isLatestEpisodePlayed = latestPlayed
        )
    }

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
     * Inserts new episodes and refreshes feed-owned metadata for all supplied identities.
     *
     * User-owned playback, favorite, download, and ordering fields are deliberately preserved.
     * The returned list follows Room's `IGNORE` contract: inserted row IDs or `-1` for conflicts.
     */
    @Transaction
    suspend fun upsertEpisodesEfficient(episodes: List<EpisodeEntity>): List<Long> {
        if (episodes.isEmpty()) return emptyList()

        val insertResults = insertEpisodesIgnore(episodes)

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

    @Query("SELECT * FROM episodes WHERE podcastRssUrl = :rssUrl ORDER BY pubDate DESC, episodeId DESC")
    fun getEpisodesFlow(rssUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastRssUrl = :rssUrl ORDER BY pubDate DESC, episodeId DESC")
    fun getEpisodesPagingSource(rssUrl: String): PagingSource<Int, EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE podcastRssUrl = :rssUrl ORDER BY pubDate DESC, episodeId DESC")
    suspend fun getEpisodesForPodcastSync(rssUrl: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE downloadStatus IN (:statuses) ORDER BY pubDate DESC, episodeId DESC")
    fun getDownloadedEpisodes(statuses: List<DownloadStatus>): Flow<List<EpisodeEntity>>

    @Query("SELECT episodeId, downloadStatus, downloadPath FROM episodes WHERE downloadStatus IN (:statuses)")
    suspend fun getEpisodeDownloadStates(statuses: List<DownloadStatus>): List<EpisodeDownloadStateRow>

    @Query(
        """
        SELECT
            e.*,
            p.rssUrl AS podcast_rssUrl,
            p.title AS podcast_title,
            p.imageUrl AS podcast_imageUrl,
            c.thumbnailFileName AS podcast_coverFileName,
            COALESCE(c.thumbnailRevision, 0) AS podcast_coverRevision
        FROM episodes e
        LEFT JOIN podcasts p ON p.rssUrl = e.podcastRssUrl
        LEFT JOIN podcast_cover_state c ON c.podcastRssUrl = p.rssUrl
        WHERE e.downloadStatus IN (:statuses)
        ORDER BY e.pubDate DESC, e.episodeId DESC
        """
    )
    fun getDownloadedEpisodesWithPodcastLiteFlow(statuses: List<DownloadStatus>): Flow<List<EpisodeWithPodcastLite>>

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
    ): Int

    @Query("SELECT * FROM episodes WHERE episodeId = :episodeId")
    suspend fun getEpisodeById(episodeId: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE podcastRssUrl = :podcastRssUrl AND guid = :guid")
    suspend fun getEpisodeByFeedAndGuid(
        podcastRssUrl: String,
        guid: String
    ): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE episodes.guid = :guid ORDER BY podcastRssUrl ASC, episodeId ASC")
    suspend fun getEpisodesByLegacyGuid(guid: String): List<EpisodeEntity>

    @Query("SELECT guid FROM episodes WHERE podcastRssUrl = :rssUrl ORDER BY pubDate DESC, episodeId DESC LIMIT 1")
    suspend fun getLatestEpisodeGuid(rssUrl: String): String?

    @Query("SELECT isPlayed FROM episodes WHERE podcastRssUrl = :rssUrl ORDER BY pubDate DESC, episodeId DESC LIMIT 1")
    suspend fun isLatestEpisodePlayed(rssUrl: String): Boolean?

    // --- SEARCH ---
    @Query(
        """
        SELECT e.* FROM episodes e
        JOIN episodes_fts fts ON e.rowid = fts.rowid
        WHERE episodes_fts MATCH :query
        ORDER BY e.pubDate DESC, e.episodeId DESC
        """
    )
    fun searchEpisodes(query: String): Flow<List<EpisodeEntity>>

    // --- FAVORITES ---

    /**
     * Updates favorite membership and its two ordering timestamps.
     *
     * [favoriteTimestamp] is mutable manual order; [favoriteAddedAt] records when the episode was
     * originally added to favorites.
     */
    @Query(
        """
        UPDATE episodes
        SET isFavorite = :isFavorite,
            favoriteTimestamp = :favoriteTimestamp,
            favoriteAddedAt = :favoriteAddedAt
        WHERE episodeId = :episodeId
        """
    )
    suspend fun setFavoriteStatus(
        episodeId: Long,
        isFavorite: Boolean,
        favoriteTimestamp: Long?,
        favoriteAddedAt: Long?
    ): Int

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
            p.imageUrl AS podcast_imageUrl,
            c.thumbnailFileName AS podcast_coverFileName,
            COALESCE(c.thumbnailRevision, 0) AS podcast_coverRevision
        FROM episodes e
        LEFT JOIN podcasts p ON p.rssUrl = e.podcastRssUrl
        LEFT JOIN podcast_cover_state c ON c.podcastRssUrl = p.rssUrl
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

    /** Applies a complete favorite order or rolls back if any referenced episode is missing. */
    @Transaction
    suspend fun updateFavoriteOrder(updates: List<FavoriteOrderUpdate>) {
        check(updateFavoriteOrderRows(updates) == updates.size) {
            "Favorite reorder referenced a missing episode"
        }
    }

    @Query("SELECT isFavorite FROM episodes WHERE episodeId = :episodeId")
    fun isFavorite(episodeId: Long): Flow<Boolean>

    // --- STATUS UPDATES ---

    @Query("UPDATE episodes SET playbackPositionMs = :positionMs WHERE episodeId = :episodeId")
    suspend fun updateEpisodeProgressOnly(
        episodeId: Long,
        positionMs: Long
    ): Int

    @Query("UPDATE episodes SET isPlayed = :isPlayed, datePlayed = :datePlayed WHERE episodeId = :episodeId")
    suspend fun markEpisodePlayed(
        episodeId: Long,
        isPlayed: Boolean,
        datePlayed: Date?
    ): Int

    /**
     * Restores portable user state without overwriting feed-owned metadata.
     *
     * Existing manual favorite order is retained for favorites, and duration is restored only when
     * the imported value is explicitly considered trustworthy.
     */
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

    /** Writes played state and reconciles the owning podcast badge in one Room transaction. */
    @Transaction
    suspend fun markEpisodePlayedAndReconcileBadge(
        episodeId: Long,
        isPlayed: Boolean,
        datePlayed: Date?
    ): Int {
        val episode = getEpisodeById(episodeId) ?: return 0
        return writeEpisodePlayedAndReconcileBadge(episode, isPlayed, datePlayed)
    }

    /** Re-reads and toggles stored played state together with badge reconciliation. */
    @Transaction
    suspend fun toggleEpisodePlayedAndReconcileBadge(
        episodeId: Long,
        datePlayed: Date
    ): Int {
        val episode = getEpisodeById(episodeId) ?: return 0
        return writeEpisodePlayedAndReconcileBadge(
            episode = episode,
            isPlayed = !episode.isPlayed,
            datePlayed = datePlayed
        )
    }

    @Query("UPDATE episodes SET downloadStatus = :status, downloadPath = :path WHERE episodeId = :episodeId")
    suspend fun updateDownloadStatus(
        episodeId: Long,
        status: DownloadStatus,
        path: String?
    ): Int

    /** Changes download state only when the stored state is one of [expectedStatuses]. */
    @Query(
        """
        UPDATE episodes
        SET downloadStatus = :status,
            downloadPath = :path
        WHERE episodeId = :episodeId
          AND downloadStatus IN (:expectedStatuses)
        """
    )
    suspend fun compareAndSetDownloadStatus(
        episodeId: Long,
        expectedStatuses: List<DownloadStatus>,
        status: DownloadStatus,
        path: String?
    ): Int

    /** Changes download state only when both the stored status and path still match. */
    @Query(
        """
        UPDATE episodes
        SET downloadStatus = :status,
            downloadPath = :path
        WHERE episodeId = :episodeId
          AND downloadStatus = :expectedStatus
          AND (
              (downloadPath IS NULL AND :expectedPath IS NULL)
              OR downloadPath = :expectedPath
          )
        """
    )
    suspend fun compareAndSetDownloadStatusAndPath(
        episodeId: Long,
        expectedStatus: DownloadStatus,
        expectedPath: String?,
        status: DownloadStatus,
        path: String?
    ): Int

    // --- HISTORY / IN PROGRESS ---
    @Query("SELECT * FROM episodes WHERE isPlayed = 1 ORDER BY datePlayed DESC, episodeId DESC")
    fun getPlaybackHistory(): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT
            e.*,
            p.rssUrl AS podcast_rssUrl,
            p.title AS podcast_title,
            p.imageUrl AS podcast_imageUrl,
            c.thumbnailFileName AS podcast_coverFileName,
            COALESCE(c.thumbnailRevision, 0) AS podcast_coverRevision
        FROM episodes e
        LEFT JOIN podcasts p ON p.rssUrl = e.podcastRssUrl
        LEFT JOIN podcast_cover_state c ON c.podcastRssUrl = p.rssUrl
        WHERE e.isPlayed = 1
        ORDER BY e.datePlayed DESC, e.episodeId DESC
        """
    )
    fun getPlaybackHistoryWithPodcastLiteFlow(): Flow<List<EpisodeWithPodcastLite>>

    @Query(
        """
        SELECT
            e.*,
            p.rssUrl AS podcast_rssUrl,
            p.title AS podcast_title,
            p.imageUrl AS podcast_imageUrl,
            c.thumbnailFileName AS podcast_coverFileName,
            COALESCE(c.thumbnailRevision, 0) AS podcast_coverRevision
        FROM episodes e
        LEFT JOIN podcasts p ON p.rssUrl = e.podcastRssUrl
        LEFT JOIN podcast_cover_state c ON c.podcastRssUrl = p.rssUrl
        WHERE e.playbackPositionMs > 0
          AND e.isPlayed = 0
        ORDER BY e.datePlayed DESC, e.pubDate DESC, e.episodeId DESC
        """
    )
    fun getEpisodesInProgressWithPodcastLiteFlow(): Flow<List<EpisodeWithPodcastLite>>

    @Query(
        """
        SELECT podcastRssUrl, COUNT(guid) AS count
        FROM episodes
        WHERE isPlayed = 0
        GROUP BY podcastRssUrl
        ORDER BY podcastRssUrl ASC
        """
    )
    fun getUnplayedCountsFlow(): Flow<List<PodcastUnplayedCount>>

    /** Removes played markers and history dates while preserving playback positions. */
    @Query("UPDATE episodes SET isPlayed = 0, datePlayed = null WHERE isPlayed = 1")
    suspend fun clearHistory(): Int

    // --- STATISTICS ---

    @Query("SELECT COUNT(guid) FROM episodes")
    fun getTotalEpisodeCount(): Flow<Int>

    @Query("SELECT COUNT(guid) FROM episodes WHERE isPlayed = 1")
    fun getPlayedEpisodesCount(): Flow<Int>

    @Query("SELECT COUNT(guid) FROM episodes WHERE playbackPositionMs > 0 AND isPlayed = 0")
    fun getEpisodesInProgressCount(): Flow<Int>
}

private suspend fun PodcastDao.writeEpisodePlayedAndReconcileBadge(
    episode: EpisodeEntity,
    isPlayed: Boolean,
    datePlayed: Date?
): Int {
    val changedRows =
        if (isPlayed) {
            markEpisodePlayedIfNeeded(episode.episodeId, datePlayed ?: Date())
        } else {
            markEpisodePlayed(episode.episodeId, false, null)
        }

    val rssUrl = episode.podcastRssUrl
    if (rssUrl.isNotBlank() && getLatestEpisodeGuid(rssUrl) == episode.guid) {
        if (!isPlayed) clearLastSeenEpisodeIfMatches(rssUrl, episode.guid)
        reconcilePodcastLatestEpisodeBadge(
            rssUrl = rssUrl,
            acknowledgeCurrentEpisode = isPlayed
        )
    }
    return changedRows
}
