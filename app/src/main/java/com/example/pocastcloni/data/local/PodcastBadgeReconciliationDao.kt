package com.example.pocastcloni.data.local

import androidx.room.Query
import androidx.room.Transaction

/** Set-based startup repair operations inherited by [PodcastDao]. */
interface PodcastBadgeReconciliationDao {
    /** Clears badge state for subscriptions without episodes. */
    @Query(
        """
        UPDATE podcasts
        SET hasNewEpisodes = 0,
            lastSeenEpisodeGuid = NULL,
            isLatestEpisodePlayed = NULL
        WHERE NOT EXISTS (
              SELECT 1 FROM episodes WHERE podcastRssUrl = podcasts.rssUrl
          )
          AND (
              hasNewEpisodes != 0
              OR lastSeenEpisodeGuid IS NOT NULL
              OR isLatestEpisodePlayed IS NOT NULL
          )
        """
    )
    suspend fun clearBadgesWithoutEpisodes(): Int

    /** Acknowledges current latest episodes that are already played. */
    @Query(
        """
        UPDATE podcasts
        SET hasNewEpisodes = 0,
            lastSeenEpisodeGuid = (
                SELECT guid FROM episodes
                WHERE podcastRssUrl = podcasts.rssUrl
                ORDER BY pubDate DESC, episodeId DESC LIMIT 1
            ),
            isLatestEpisodePlayed = 1
        WHERE (
              SELECT isPlayed FROM episodes
              WHERE podcastRssUrl = podcasts.rssUrl
              ORDER BY pubDate DESC, episodeId DESC LIMIT 1
          ) = 1
          AND (
              hasNewEpisodes != 0
              OR lastSeenEpisodeGuid IS NOT (
                  SELECT guid FROM episodes
                  WHERE podcastRssUrl = podcasts.rssUrl
                  ORDER BY pubDate DESC, episodeId DESC LIMIT 1
              )
              OR isLatestEpisodePlayed IS NOT 1
          )
        """
    )
    suspend fun acknowledgePlayedLatestEpisodes(): Int

    /** Activates badges where the unplayed current latest episode has not been acknowledged. */
    @Query(
        """
        UPDATE podcasts
        SET hasNewEpisodes = 1,
            isLatestEpisodePlayed = 0
        WHERE (
              SELECT isPlayed FROM episodes
              WHERE podcastRssUrl = podcasts.rssUrl
              ORDER BY pubDate DESC, episodeId DESC LIMIT 1
          ) = 0
          AND lastSeenEpisodeGuid IS NOT (
              SELECT guid FROM episodes
              WHERE podcastRssUrl = podcasts.rssUrl
              ORDER BY pubDate DESC, episodeId DESC LIMIT 1
          )
          AND (hasNewEpisodes != 1 OR isLatestEpisodePlayed IS NOT 0)
        """
    )
    suspend fun activateUnseenLatestEpisodeBadges(): Int

    /** Keeps acknowledged unplayed latest episodes badge-free. */
    @Query(
        """
        UPDATE podcasts
        SET hasNewEpisodes = 0,
            isLatestEpisodePlayed = 0
        WHERE (
              SELECT isPlayed FROM episodes
              WHERE podcastRssUrl = podcasts.rssUrl
              ORDER BY pubDate DESC, episodeId DESC LIMIT 1
          ) = 0
          AND lastSeenEpisodeGuid IS (
              SELECT guid FROM episodes
              WHERE podcastRssUrl = podcasts.rssUrl
              ORDER BY pubDate DESC, episodeId DESC LIMIT 1
          )
          AND (hasNewEpisodes != 0 OR isLatestEpisodePlayed IS NOT 0)
        """
    )
    suspend fun clearAcknowledgedLatestEpisodeBadges(): Int

    /** Repairs every badge from the canonical latest episode and persisted seen baseline. */
    @Transaction
    suspend fun reconcileLatestEpisodeBadges(): Int =
        clearBadgesWithoutEpisodes() +
            acknowledgePlayedLatestEpisodes() +
            activateUnseenLatestEpisodeBadges() +
            clearAcknowledgedLatestEpisodeBadges()
}
