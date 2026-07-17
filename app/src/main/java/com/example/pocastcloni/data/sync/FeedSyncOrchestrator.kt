package com.example.pocastcloni.data.sync

import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import com.example.pocastcloni.data.remote.PodcastService
import com.example.pocastcloni.data.remote.RssItem
import com.example.pocastcloni.data.remote.RssSmartSyncParser
import com.example.pocastcloni.data.repository.toDomain
import com.example.pocastcloni.data.repository.toEpisodeEntity
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.model.FeedPodcastUpdate
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.EpisodeDownloadScheduler
import com.example.pocastcloni.domain.repository.FeedSyncRunner
import com.example.pocastcloni.domain.repository.FeedSyncStore
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.SizeLimitExceededException
import com.example.pocastcloni.util.SizeLimitedInputStream
import com.example.pocastcloni.util.hasSameOrigin
import com.example.pocastcloni.util.isAllowedPodcastResource
import com.example.pocastcloni.util.parseNetworkUrl
import com.example.pocastcloni.util.requireApprovedNetworkUrl
import com.example.pocastcloni.util.stripHtml
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.util.Date
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class FeedSyncOrchestrator
@Inject
constructor(
    private val podcastService: PodcastService,
    @Named("LocalPodcastService") private val localPodcastService: PodcastService,
    private val feedSyncStore: FeedSyncStore,
    private val episodeDownloadScheduler: EpisodeDownloadScheduler,
    private val dispatcherProvider: DispatcherProvider,
    private val localNetworkAccessRegistry: LocalNetworkAccessRegistry
) : FeedSyncRunner {
    private val streamParser = RssSmartSyncParser()

    override suspend fun sync(
        url: String,
        downloadLimit: Int,
        mode: FeedUpdateMode,
        sortOrder: Long?,
        forceFull: Boolean,
        allowInsecureHttp: Boolean,
        allowLocalNetwork: Boolean,
        feedItemLimit: Int
    ) {
        require(feedItemLimit in 1..Constants.SecurityLimits.MAX_FEED_ITEMS) {
            "Feed item limit is outside the supported range."
        }
        val request =
            FeedSyncRequest(
                url = url,
                downloadLimit = downloadLimit,
                mode = mode,
                sortOrder = sortOrder,
                forceFull = forceFull,
                allowInsecureHttp = allowInsecureHttp,
                allowLocalNetwork = allowLocalNetwork,
                feedItemLimit = feedItemLimit
            )
        withContext(dispatcherProvider.io) {
            runCatching { performSync(request) }
                .onFailure { error ->
                    if (error is Exception) {
                        Timber.e(error, "Error with ${request.url}")
                    }
                }.getOrThrow()
        }
    }

    @Suppress("LongParameterList")
    suspend operator fun invoke(
        url: String,
        downloadLimit: Int,
        mode: FeedUpdateMode,
        sortOrder: Long? = null,
        forceFull: Boolean = false,
        allowInsecureHttp: Boolean = false,
        allowLocalNetwork: Boolean = false,
        feedItemLimit: Int = Constants.SecurityLimits.MAX_FEED_ITEMS
    ) = sync(
        url = url,
        downloadLimit = downloadLimit,
        mode = mode,
        sortOrder = sortOrder,
        forceFull = forceFull,
        allowInsecureHttp = allowInsecureHttp,
        allowLocalNetwork = allowLocalNetwork,
        feedItemLimit = feedItemLimit
    )

    private suspend fun performSync(request: FeedSyncRequest) {
        val existingPodcast = feedSyncStore.getPodcastForSync(request.url)
        val accessPolicy =
            FeedAccessPolicy(
                allowInsecureHttp = existingPodcast?.allowInsecureHttp ?: request.allowInsecureHttp,
                allowLocalNetwork = existingPodcast?.allowLocalNetwork ?: request.allowLocalNetwork
            )
        requireApprovedNetworkUrl(
            request.url,
            accessPolicy.allowInsecureHttp,
            accessPolicy.allowLocalNetwork
        )
        val context =
            FeedSyncContext(
                request = request,
                existingPodcast = existingPodcast,
                accessPolicy = accessPolicy,
                service = if (accessPolicy.allowLocalNetwork) localPodcastService else podcastService
            )

        if (request.mode == FeedUpdateMode.SMART_STREAM && !request.forceFull) {
            syncSmart(context)
        } else {
            syncFull(context)
        }
    }

    private suspend fun syncSmart(context: FeedSyncContext) {
        val latestKnownGuid = feedSyncStore.getLatestEpisodeGuid(context.request.url)
        val hasNoEpisodes = context.existingPodcast != null && latestKnownGuid == null
        val lastModified = if (hasNoEpisodes) null else context.existingPodcast?.lastModifiedHeader
        val etag = if (hasNoEpisodes) null else context.existingPodcast?.eTagHeader
        val response = context.service.fetchRawFeed(context.request.url, lastModified, etag)

        if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            handleNotModified(context)
            return
        }
        if (!response.isSuccessful) throw retrofit2.HttpException(response)

        val body = response.body() ?: error("Empty response body from ${context.request.url}")
        rejectOversizedFeed(body.contentLength())
        val validators = originBoundFeedValidators(context.request.url, response)
        val result =
            SizeLimitedInputStream(
                body.byteStream(),
                Constants.SecurityLimits.MAX_FEED_BYTES
            ).use { stream ->
                streamParser.parse(
                    stream,
                    context.request.url,
                    context.request.feedItemLimit,
                    isFullSync = false,
                    latestKnownGuid = latestKnownGuid
                )
            }
        processParsedData(
            context,
            ParsedFeedData(
                result = result,
                validators = validators,
                sortOrder = context.existingPodcast?.sortOrder
            )
        )
    }

    private suspend fun syncFull(context: FeedSyncContext) {
        val hasNoEpisodes =
            context.existingPodcast != null &&
                feedSyncStore.getLatestEpisodeGuid(context.request.url) == null
        val forceFull = context.request.forceFull || hasNoEpisodes
        val lastModified = if (forceFull) null else context.existingPodcast?.lastModifiedHeader
        val etag = if (forceFull) null else context.existingPodcast?.eTagHeader
        val response = context.service.fetchRawFeed(context.request.url, lastModified, etag)

        if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            handleNotModified(context)
            return
        }
        if (!response.isSuccessful) throw retrofit2.HttpException(response)

        val body = response.body() ?: error("Empty response body from ${context.request.url}")
        rejectOversizedFeed(body.contentLength())
        val validators = originBoundFeedValidators(context.request.url, response)
        val result =
            SizeLimitedInputStream(
                body.byteStream(),
                Constants.SecurityLimits.MAX_FEED_BYTES
            ).use { stream ->
                streamParser.parse(
                    stream,
                    context.request.url,
                    Constants.SecurityLimits.MAX_FEED_ITEMS,
                    isFullSync = true
                )
            }
        processParsedData(
            context,
            ParsedFeedData(
                result = result,
                validators = validators,
                sortOrder = context.request.sortOrder
            )
        )
    }

    private suspend fun handleNotModified(context: FeedSyncContext) {
        if (context.existingPodcast == null) return

        val autoDownloadEnabled = feedSyncStore.touchLastRefreshed(context.request.url, Date())
        if (autoDownloadEnabled) {
            triggerAutoDownloads(context.request.url, context.request.downloadLimit)
        }
    }

    private suspend fun processParsedData(
        context: FeedSyncContext,
        parsedFeed: ParsedFeedData
    ) {
        val metadata = validateFeedMetadata(context, parsedFeed)
        val episodes = mapFeedEpisodes(context, parsedFeed.result.newItems)
        val lastRefreshed = Date()
        val update = createFeedUpdate(context, parsedFeed, metadata, lastRefreshed)
        val newPodcast = createNewPodcast(context, parsedFeed, metadata, lastRefreshed)
        val autoDownloadEnabled =
            feedSyncStore.persistFeedUpdate(
                update = update,
                newPodcast = newPodcast,
                episodes = episodes
            )

        if (context.accessPolicy.allowLocalNetwork) {
            localNetworkAccessRegistry.approveFeed(context.request.url)
        }
        if (autoDownloadEnabled) {
            triggerAutoDownloads(context.request.url, context.request.downloadLimit)
        }
    }

    private fun validateFeedMetadata(
        context: FeedSyncContext,
        parsedFeed: ParsedFeedData
    ): ValidatedFeedMetadata {
        val channel = parsedFeed.result.channel
        val title = channel.title
        val description = channel.description.orEmpty()
        val imageUrl = channel.finalImageUrl
        val errorMessage = "Feed missing title or image for ${context.request.url}"

        check(!title.isNullOrBlank()) { errorMessage }
        check(title.length <= Constants.SecurityLimits.MAX_TITLE_CHARS) { errorMessage }
        check(description.length <= Constants.SecurityLimits.MAX_DESCRIPTION_CHARS) { errorMessage }
        check(!imageUrl.isNullOrBlank()) { errorMessage }
        check(imageUrl.length <= Constants.SecurityLimits.MAX_URL_CHARS) { errorMessage }
        check(
            parsedFeed.validators.lastModified.orEmpty().length <=
                Constants.SecurityLimits.MAX_HEADER_CHARS
        ) { errorMessage }
        check(parsedFeed.validators.etag.orEmpty().length <= Constants.SecurityLimits.MAX_HEADER_CHARS) {
            errorMessage
        }
        check(
            isAllowedPodcastResource(
                context.request.url,
                imageUrl,
                context.accessPolicy.allowInsecureHttp,
                context.accessPolicy.allowLocalNetwork
            )
        ) { errorMessage }

        return ValidatedFeedMetadata(
            title = title,
            description = description.stripHtml(),
            imageUrl = imageUrl
        )
    }

    private fun mapFeedEpisodes(
        context: FeedSyncContext,
        items: List<RssItem>
    ): List<Episode> =
        items
            .mapNotNull { item -> mapFeedEpisode(context, item) }
            .distinctBy(Episode::guid)

    private fun mapFeedEpisode(
        context: FeedSyncContext,
        item: RssItem
    ): Episode? {
        validateRssItemLimits(item)
        val episode = item.toEpisodeEntity(context.request.url).toDomain()
        if (
            !isAllowedPodcastResource(
                context.request.url,
                episode.enclosureUrl,
                context.accessPolicy.allowInsecureHttp,
                context.accessPolicy.allowLocalNetwork
            )
        ) {
            return null
        }
        return episode.copy(
            title = episode.title.stripHtml(),
            description = episode.description.stripHtml()
        )
    }

    private fun createFeedUpdate(
        context: FeedSyncContext,
        parsedFeed: ParsedFeedData,
        metadata: ValidatedFeedMetadata,
        lastRefreshed: Date
    ) = FeedPodcastUpdate(
        rssUrl = context.request.url,
        title = metadata.title,
        description = metadata.description,
        imageUrl = metadata.imageUrl,
        lastRefreshed = lastRefreshed,
        lastModifiedHeader = parsedFeed.validators.lastModified,
        eTagHeader = parsedFeed.validators.etag
    )

    private suspend fun createNewPodcast(
        context: FeedSyncContext,
        parsedFeed: ParsedFeedData,
        metadata: ValidatedFeedMetadata,
        lastRefreshed: Date
    ): Podcast? {
        if (context.existingPodcast != null) return null

        return Podcast(
            rssUrl = context.request.url,
            title = metadata.title,
            description = metadata.description,
            imageUrl = metadata.imageUrl,
            lastRefreshed = lastRefreshed,
            autoDownloadEnabled = false,
            allowInsecureHttp = context.accessPolicy.allowInsecureHttp,
            allowLocalNetwork = context.accessPolicy.allowLocalNetwork,
            sortOrder = parsedFeed.sortOrder ?: (feedSyncStore.getMaxSortOrder() ?: 0) + 1,
            lastModifiedHeader = parsedFeed.validators.lastModified,
            eTagHeader = parsedFeed.validators.etag,
            hasNewEpisodes = false
        )
    }

    private suspend fun triggerAutoDownloads(
        url: String,
        downloadLimit: Int
    ) {
        val episodesToDownload =
            selectEpisodesForAutoDownload(
                episodes = feedSyncStore.getEpisodesForSync(url),
                downloadLimit = downloadLimit
            )

        episodesToDownload.forEach { episode ->
            episodeDownloadScheduler.queue(episode)
        }
    }
}

private data class FeedSyncRequest(
    val url: String,
    val downloadLimit: Int,
    val mode: FeedUpdateMode,
    val sortOrder: Long?,
    val forceFull: Boolean,
    val allowInsecureHttp: Boolean,
    val allowLocalNetwork: Boolean,
    val feedItemLimit: Int
)

private data class FeedAccessPolicy(
    val allowInsecureHttp: Boolean,
    val allowLocalNetwork: Boolean
)

private data class FeedSyncContext(
    val request: FeedSyncRequest,
    val existingPodcast: Podcast?,
    val accessPolicy: FeedAccessPolicy,
    val service: PodcastService
)

private data class ParsedFeedData(
    val result: RssSmartSyncParser.ParseResult,
    val validators: FeedResponseValidators,
    val sortOrder: Long?
)

private data class ValidatedFeedMetadata(
    val title: String,
    val description: String,
    val imageUrl: String
)

internal fun rejectOversizedFeed(contentLength: Long) {
    if (contentLength > Constants.SecurityLimits.MAX_FEED_BYTES) {
        throw SizeLimitExceededException(Constants.SecurityLimits.MAX_FEED_BYTES)
    }
}

internal data class FeedResponseValidators(
    val lastModified: String?,
    val etag: String?
)

/**
 * Retains response validators only when the final response has the requested origin.
 * Cross-origin validators must not affect a later request to the original feed.
 */
internal fun originBoundFeedValidators(
    requestedUrl: String,
    response: retrofit2.Response<okhttp3.ResponseBody>
): FeedResponseValidators {
    val requested = parseNetworkUrl(requestedUrl, allowLocalNetwork = true)
    val finalUrl = response.raw().request.url
    if (requested == null || !hasSameOrigin(requested, finalUrl)) {
        return FeedResponseValidators(lastModified = null, etag = null)
    }
    return FeedResponseValidators(
        lastModified = response.headers()[Constants.Network.HEADER_LAST_MODIFIED],
        etag = response.headers()[Constants.Network.HEADER_ETAG]
    )
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
    require(item.itunesDuration.orEmpty().length <= MAX_EPISODE_DURATION_CHARS) {
        "Episode duration is too long"
    }
    require(item.enclosure?.type.orEmpty().length <= MAX_EPISODE_ENCLOSURE_TYPE_CHARS) {
        "Episode enclosure type is too long"
    }
}

internal fun selectEpisodesForAutoDownload(
    episodes: List<Episode>,
    downloadLimit: Int
): List<Episode> {
    if (downloadLimit <= Constants.Preferences.NO_DOWNLOAD_LIMIT) return emptyList()

    return episodes
        .sortedByDescending { it.pubDate?.time ?: 0L }
        .take(downloadLimit)
        .filter { episode ->
            episode.downloadStatus == DownloadStatus.NOT_DOWNLOADED &&
                episode.enclosureUrl.isNotBlank()
        }
}

private const val MAX_EPISODE_DURATION_CHARS = 128
private const val MAX_EPISODE_ENCLOSURE_TYPE_CHARS = 256
