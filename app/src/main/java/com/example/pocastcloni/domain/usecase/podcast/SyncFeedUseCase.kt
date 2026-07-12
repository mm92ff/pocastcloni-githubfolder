package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.PodcastService
import com.example.pocastcloni.data.remote.RssItem
import com.example.pocastcloni.data.remote.RssSmartSyncParser
// TODO: ARCHITECTURE BOUNDARY VIOLATION - Domain layer importing data mapper functions
// FIXME: Domain use cases should not depend on data layer implementation details.
// This mapper function should be moved to domain or accessed via repository interface.
import com.example.pocastcloni.data.repository.toEpisodeEntity
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.stripHtml
import com.example.pocastcloni.util.requireApprovedNetworkUrl
import com.example.pocastcloni.util.isAllowedRemoteResource
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.util.Date
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SyncFeedUseCase
@Inject
constructor(
    private val podcastService: PodcastService,
    private val podcastRepositoryProvider: Provider<PodcastRepository>,
    private val downloadEpisodeUseCase: DownloadEpisodeUseCase,
    private val dispatcherProvider: DispatcherProvider
) {
    private val streamParser = RssSmartSyncParser()

    // Note: dateFormats removed; date parsing is now handled in PodcastMappers.kt.

    private val repo: PodcastRepository
        get() = podcastRepositoryProvider.get()

    suspend operator fun invoke(
        url: String,
        downloadLimit: Int,
        mode: FeedUpdateMode,
        sortOrder: Long? = null,
        forceFull: Boolean = false,
        allowInsecureHttp: Boolean = false
    ) {
        withContext(dispatcherProvider.io) {
            try {
                val existingPodcast = repo.getPodcastEntityByUrl(url)
                val effectiveAllowInsecureHttp =
                    existingPodcast?.allowInsecureHttp ?: allowInsecureHttp
                requireApprovedNetworkUrl(url, effectiveAllowInsecureHttp)

                if (mode == FeedUpdateMode.SMART_STREAM && !forceFull) {
                    syncSmart(url, existingPodcast, downloadLimit, effectiveAllowInsecureHttp)
                } else {
                    syncFull(
                        url,
                        existingPodcast,
                        downloadLimit,
                        forceFull,
                        sortOrder,
                        effectiveAllowInsecureHttp
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error with $url")
                throw e
            }
        }
    }

    private suspend fun syncSmart(
        url: String,
        existing: PodcastEntity?,
        downloadLimit: Int,
        allowInsecureHttp: Boolean
    ) {
        val latestKnownGuid = repo.getLatestEpisodeGuid(url)
        val hasNoEpisodes = existing != null && latestKnownGuid == null
        val effectiveLastModified = if (hasNoEpisodes) null else existing?.lastModifiedHeader
        val effectiveEtag = if (hasNoEpisodes) null else existing?.eTagHeader

        val response = podcastService.fetchRawFeed(url, effectiveLastModified, effectiveEtag)
        if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            existing?.let {
                repo.updatePodcastEntity(it.copy(lastRefreshed = Date()))
                if (it.autoDownloadEnabled) triggerAutoDownloads(url, downloadLimit)
            }
            return
        }
        if (!response.isSuccessful || response.body() == null) throw Exception("Smart Sync Fail: ${response.code()}")

        val body = response.body() ?: throw java.io.IOException("Empty response body from $url")
        val stream = body.byteStream()
        try {
            val result =
                streamParser.parse(
                    stream,
                    url,
                    downloadLimit,
                    isFullSync = false,
                    latestKnownGuid = latestKnownGuid
                )
            processParsedData(
                url = url,
                existing = existing,
                title = result.channel.title,
                description = result.channel.description,
                imageUrl = result.channel.finalImageUrl,
                newItems = result.newItems,
                lastModified = response.headers()[Constants.Network.HEADER_LAST_MODIFIED],
                etag = response.headers()[Constants.Network.HEADER_ETAG],
                sortOrder = existing?.sortOrder,
                downloadLimit = downloadLimit,
                allowInsecureHttp = allowInsecureHttp
            )
        } finally {
            stream.close()
        }
    }

    private suspend fun syncFull(
        url: String,
        existing: PodcastEntity?,
        downloadLimit: Int,
        forceFull: Boolean,
        sortOrder: Long?,
        allowInsecureHttp: Boolean
    ) {
        val hasNoEpisodes = existing != null && repo.getLatestEpisodeGuid(url) == null
        val effectiveForceFull = forceFull || hasNoEpisodes
        val lastModified = if (effectiveForceFull) null else existing?.lastModifiedHeader
        val etag = if (effectiveForceFull) null else existing?.eTagHeader
        val response = podcastService.fetchRawFeed(url, lastModified, etag)

        if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            existing?.let {
                repo.updatePodcastEntity(it.copy(lastRefreshed = Date()))
                if (it.autoDownloadEnabled) triggerAutoDownloads(url, downloadLimit)
            }
            return
        }
        if (!response.isSuccessful || response.body() == null) throw Exception("Full Sync Fail: ${response.code()}")

        val body = response.body() ?: throw java.io.IOException("Empty response body from $url")
        val stream = body.byteStream()
        try {
            val result = streamParser.parse(stream, url, Int.MAX_VALUE, isFullSync = true)
            processParsedData(
                url = url,
                existing = existing,
                title = result.channel.title,
                description = result.channel.description,
                imageUrl = result.channel.finalImageUrl,
                newItems = result.newItems,
                lastModified = response.headers()[Constants.Network.HEADER_LAST_MODIFIED],
                etag = response.headers()[Constants.Network.HEADER_ETAG],
                sortOrder = sortOrder,
                downloadLimit = downloadLimit,
                allowInsecureHttp = allowInsecureHttp
            )
        } finally {
            stream.close()
        }
    }

    private suspend fun processParsedData(
        url: String,
        existing: PodcastEntity?,
        title: String?,
        description: String?,
        imageUrl: String?,
        newItems: List<RssItem>,
        lastModified: String?,
        etag: String?,
        sortOrder: Long?,
        downloadLimit: Int,
        allowInsecureHttp: Boolean
    ) {
        if (
            title.isNullOrBlank() ||
            imageUrl.isNullOrBlank() ||
            !isAllowedRemoteResource(imageUrl, allowInsecureHttp)
        ) {
            throw IllegalStateException("Feed missing title or image for $url")
        }

        // Deduplicate against DB *before* computing hasNewEpisodes.
        // A full sync passes ALL feed items as newItems (no latestKnownGuid
        // cutoff), so newItems.isNotEmpty() is always true even when every
        // episode is already played. Using episodesToInsert instead means the
        // dot only lights up when episodes that are genuinely new arrive.
        val existingEpisodes = if (newItems.isNotEmpty()) {
            repo.getEpisodesForSync(url).associateBy { it.guid }
        } else {
            emptyMap()
        }

        val episodesToInsert =
            newItems.mapNotNull { item ->
                // Use the mapper from PodcastMappers.kt which handles:
                // 1. Sanitizing dates (year 3000 fix)
                // 2. Parsing duration
                // 3. Setting defaults

                val entity = item.toEpisodeEntity(url)

                // Duplicate check
                if (existingEpisodes.containsKey(entity.guid)) return@mapNotNull null

                // An episode without an audio URL is useless
                if (!isAllowedRemoteResource(entity.enclosureUrl, allowInsecureHttp)) {
                    return@mapNotNull null
                }

                // Strip HTML if the mapper left it raw (description is passed through as-is)
                entity.copy(
                    title = entity.title.stripHtml(),
                    description = entity.description.stripHtml()
                )
            }

        val podcastEntity =
            existing?.copy(
                title = title,
                description = description?.stripHtml() ?: "",
                imageUrl = imageUrl,
                allowInsecureHttp = allowInsecureHttp,
                lastRefreshed = Date(),
                lastModifiedHeader = lastModified,
                eTagHeader = etag,
                hasNewEpisodes = existing.hasNewEpisodes || episodesToInsert.isNotEmpty()
            ) ?: PodcastEntity(
                rssUrl = url,
                title = title,
                description = description?.stripHtml() ?: "",
                imageUrl = imageUrl,
                allowInsecureHttp = allowInsecureHttp,
                sortOrder = sortOrder ?: (repo.getMaxSortOrder() ?: 0) + 1,
                lastModifiedHeader = lastModified,
                eTagHeader = etag,
                hasNewEpisodes = episodesToInsert.isNotEmpty()
            )

        if (existing == null) {
            repo.insertPodcastEntity(podcastEntity)
        } else {
            repo.updatePodcastEntity(podcastEntity)
        }

        if (episodesToInsert.isNotEmpty()) {
            repo.insertEpisodes(episodesToInsert)
        }

        if (podcastEntity.autoDownloadEnabled) {
            triggerAutoDownloads(url, downloadLimit)
        }
    }

    private suspend fun triggerAutoDownloads(
        url: String,
        downloadLimit: Int
    ) {
        val episodesToDownload =
            selectEpisodesForAutoDownload(
                episodes = repo.getEpisodesForSync(url),
                downloadLimit = downloadLimit
            )

        episodesToDownload.forEach { episode ->
            downloadEpisodeUseCase(episode.guid)
        }
    }
}

internal fun selectEpisodesForAutoDownload(
    episodes: List<EpisodeEntity>,
    downloadLimit: Int
): List<EpisodeEntity> {
    if (downloadLimit <= Constants.Preferences.NO_DOWNLOAD_LIMIT) return emptyList()

    return episodes
        .sortedByDescending { it.pubDate?.time ?: 0L }
        .take(downloadLimit)
        .filter { episode ->
            episode.downloadStatus == DownloadStatus.NOT_DOWNLOADED &&
                episode.enclosureUrl.isNotBlank()
        }
}
