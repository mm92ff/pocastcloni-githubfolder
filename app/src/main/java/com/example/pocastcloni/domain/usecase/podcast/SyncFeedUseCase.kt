package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.PodcastService
import com.example.pocastcloni.data.remote.RssItem
import com.example.pocastcloni.data.remote.RssSmartSyncParser
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
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
import com.example.pocastcloni.util.isAllowedPodcastResource
import com.example.pocastcloni.util.SizeLimitedInputStream
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.util.Date
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import javax.inject.Named

@Singleton
class SyncFeedUseCase
@Inject
constructor(
    private val podcastService: PodcastService,
    @Named("LocalPodcastService") private val localPodcastService: PodcastService,
    private val podcastRepositoryProvider: Provider<PodcastRepository>,
    private val downloadEpisodeUseCase: DownloadEpisodeUseCase,
    private val dispatcherProvider: DispatcherProvider,
    private val localNetworkAccessRegistry: LocalNetworkAccessRegistry
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
        allowInsecureHttp: Boolean = false,
        allowLocalNetwork: Boolean = false
    ) {
        withContext(dispatcherProvider.io) {
            try {
                val existingPodcast = repo.getPodcastEntityByUrl(url)
                val effectiveAllowInsecureHttp =
                    existingPodcast?.allowInsecureHttp ?: allowInsecureHttp
                val effectiveAllowLocalNetwork =
                    existingPodcast?.allowLocalNetwork ?: allowLocalNetwork
                requireApprovedNetworkUrl(
                    url,
                    effectiveAllowInsecureHttp,
                    effectiveAllowLocalNetwork
                )
                val service = if (effectiveAllowLocalNetwork) localPodcastService else podcastService

                if (mode == FeedUpdateMode.SMART_STREAM && !forceFull) {
                    syncSmart(
                        url,
                        existingPodcast,
                        downloadLimit,
                        effectiveAllowInsecureHttp,
                        effectiveAllowLocalNetwork,
                        service
                    )
                } else {
                    syncFull(
                        url,
                        existingPodcast,
                        downloadLimit,
                        forceFull,
                        sortOrder,
                        effectiveAllowInsecureHttp,
                        effectiveAllowLocalNetwork,
                        service
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
        allowInsecureHttp: Boolean,
        allowLocalNetwork: Boolean,
        service: PodcastService
    ) {
        val latestKnownGuid = repo.getLatestEpisodeGuid(url)
        val hasNoEpisodes = existing != null && latestKnownGuid == null
        val effectiveLastModified = if (hasNoEpisodes) null else existing?.lastModifiedHeader
        val effectiveEtag = if (hasNoEpisodes) null else existing?.eTagHeader

        val response = service.fetchRawFeed(url, effectiveLastModified, effectiveEtag)
        if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            existing?.let {
                repo.updatePodcastEntity(it.copy(lastRefreshed = Date()))
                if (it.autoDownloadEnabled) triggerAutoDownloads(url, downloadLimit)
            }
            return
        }
        if (!response.isSuccessful || response.body() == null) throw Exception("Smart Sync Fail: ${response.code()}")

        val body = response.body() ?: throw java.io.IOException("Empty response body from $url")
        rejectOversizedFeed(body.contentLength())
        val stream = SizeLimitedInputStream(
            body.byteStream(),
            Constants.SecurityLimits.MAX_FEED_BYTES
        )
        try {
            val result =
                streamParser.parse(
                    stream,
                    url,
                    effectiveFeedParserLimit(downloadLimit),
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
                allowInsecureHttp = allowInsecureHttp,
                allowLocalNetwork = allowLocalNetwork
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
        allowInsecureHttp: Boolean,
        allowLocalNetwork: Boolean,
        service: PodcastService
    ) {
        val hasNoEpisodes = existing != null && repo.getLatestEpisodeGuid(url) == null
        val effectiveForceFull = forceFull || hasNoEpisodes
        val lastModified = if (effectiveForceFull) null else existing?.lastModifiedHeader
        val etag = if (effectiveForceFull) null else existing?.eTagHeader
        val response = service.fetchRawFeed(url, lastModified, etag)

        if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            existing?.let {
                repo.updatePodcastEntity(it.copy(lastRefreshed = Date()))
                if (it.autoDownloadEnabled) triggerAutoDownloads(url, downloadLimit)
            }
            return
        }
        if (!response.isSuccessful || response.body() == null) throw Exception("Full Sync Fail: ${response.code()}")

        val body = response.body() ?: throw java.io.IOException("Empty response body from $url")
        rejectOversizedFeed(body.contentLength())
        val stream = SizeLimitedInputStream(
            body.byteStream(),
            Constants.SecurityLimits.MAX_FEED_BYTES
        )
        try {
            val result = streamParser.parse(
                stream,
                url,
                Constants.SecurityLimits.MAX_FEED_ITEMS,
                isFullSync = true
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
                sortOrder = sortOrder,
                downloadLimit = downloadLimit,
                allowInsecureHttp = allowInsecureHttp,
                allowLocalNetwork = allowLocalNetwork
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
        allowInsecureHttp: Boolean,
        allowLocalNetwork: Boolean
    ) {
        if (
            title.isNullOrBlank() ||
            title.length > Constants.SecurityLimits.MAX_TITLE_CHARS ||
            description.orEmpty().length > Constants.SecurityLimits.MAX_DESCRIPTION_CHARS ||
            imageUrl.isNullOrBlank() ||
            imageUrl.length > Constants.SecurityLimits.MAX_URL_CHARS ||
            lastModified.orEmpty().length > Constants.SecurityLimits.MAX_HEADER_CHARS ||
            etag.orEmpty().length > Constants.SecurityLimits.MAX_HEADER_CHARS ||
            !isAllowedPodcastResource(
                url,
                imageUrl,
                allowInsecureHttp,
                allowLocalNetwork
            )
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

        val feedEpisodes =
            newItems.mapNotNull { item ->
                // Use the mapper from PodcastMappers.kt which handles:
                // 1. Sanitizing dates (year 3000 fix)
                // 2. Parsing duration
                // 3. Setting defaults

                validateRssItemLimits(item)
                val entity = item.toEpisodeEntity(url)

                // An episode without an audio URL is useless
                if (!isAllowedPodcastResource(
                        url,
                        entity.enclosureUrl,
                        allowInsecureHttp,
                        allowLocalNetwork
                    )
                ) {
                    return@mapNotNull null
                }

                // Strip HTML if the mapper left it raw (description is passed through as-is)
                entity.copy(
                    title = entity.title.stripHtml(),
                    description = entity.description.stripHtml()
                )
            }.distinctBy { it.guid }

        val newEpisodeCount = feedEpisodes.count { it.guid !in existingEpisodes }

        val podcastEntity =
            existing?.copy(
                title = title,
                description = description?.stripHtml() ?: "",
                imageUrl = imageUrl,
                allowInsecureHttp = allowInsecureHttp,
                allowLocalNetwork = allowLocalNetwork,
                lastRefreshed = Date(),
                lastModifiedHeader = lastModified,
                eTagHeader = etag,
                hasNewEpisodes = existing.hasNewEpisodes || newEpisodeCount > 0
            ) ?: PodcastEntity(
                rssUrl = url,
                title = title,
                description = description?.stripHtml() ?: "",
                imageUrl = imageUrl,
                allowInsecureHttp = allowInsecureHttp,
                allowLocalNetwork = allowLocalNetwork,
                sortOrder = sortOrder ?: (repo.getMaxSortOrder() ?: 0) + 1,
                lastModifiedHeader = lastModified,
                eTagHeader = etag,
                hasNewEpisodes = newEpisodeCount > 0
            )

        if (existing == null) {
            repo.insertPodcastEntity(podcastEntity)
        } else {
            repo.updatePodcastEntity(podcastEntity)
        }

        if (allowLocalNetwork) {
            localNetworkAccessRegistry.approveFeed(url)
        }

        if (feedEpisodes.isNotEmpty()) {
            repo.insertEpisodes(feedEpisodes)
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
            downloadEpisodeUseCase(episode.episodeId)
        }
    }
}

internal fun rejectOversizedFeed(contentLength: Long) {
    if (contentLength > Constants.SecurityLimits.MAX_FEED_BYTES) {
        throw java.io.IOException("Feed response is too large")
    }
}

internal fun effectiveFeedParserLimit(requestedLimit: Int): Int =
    if (requestedLimit <= 0) {
        Constants.SecurityLimits.MAX_FEED_ITEMS
    } else {
        requestedLimit.coerceAtMost(Constants.SecurityLimits.MAX_FEED_ITEMS)
    }

private fun validateRssItemLimits(item: RssItem) {
    require(item.title.orEmpty().length <= Constants.SecurityLimits.MAX_TITLE_CHARS) {
        "Episode title is too long"
    }
    require(item.description.orEmpty().length <= Constants.SecurityLimits.MAX_DESCRIPTION_CHARS) {
        "Episode description is too long"
    }
    require(item.guid.orEmpty().length <= Constants.SecurityLimits.MAX_GUID_CHARS) {
        "Episode GUID is too long"
    }
    require(item.link.orEmpty().length <= Constants.SecurityLimits.MAX_URL_CHARS) {
        "Episode link is too long"
    }
    require(item.enclosure?.url.orEmpty().length <= Constants.SecurityLimits.MAX_URL_CHARS) {
        "Episode enclosure URL is too long"
    }
    require(item.pubDate.orEmpty().length <= Constants.SecurityLimits.MAX_HEADER_CHARS) {
        "Episode publication date is too long"
    }
    require(item.itunesDuration.orEmpty().length <= 128) {
        "Episode duration is too long"
    }
    require(item.enclosure?.type.orEmpty().length <= 256) {
        "Episode enclosure type is too long"
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
