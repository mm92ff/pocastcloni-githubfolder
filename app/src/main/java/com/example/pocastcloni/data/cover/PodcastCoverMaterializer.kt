package com.example.pocastcloni.data.cover

import androidx.room.withTransaction
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.PodcastCoverStateDao
import com.example.pocastcloni.data.local.PodcastCoverStateEntity
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.util.requireApprovedPodcastResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.min

enum class PodcastCoverMaterializationSource {
    PERSISTENT,
    LEGACY_DISK,
    NETWORK,
    NOT_MODIFIED
}

sealed interface PodcastCoverMaterializationResult {
    data class Available(
        val file: File,
        val source: PodcastCoverMaterializationSource
    ) : PodcastCoverMaterializationResult

    data class Waiting(
        val file: File?,
        val delayMs: Long
    ) : PodcastCoverMaterializationResult

    data class RetryableFailure(val error: IOException) : PodcastCoverMaterializationResult

    data class PermanentFailure(val error: IOException) : PodcastCoverMaterializationResult

    data object NoSource : PodcastCoverMaterializationResult
}

/** Materializes one cover without coupling image I/O to the feed transaction. */
@Singleton
@OptIn(ExperimentalCoilApi::class)
class PodcastCoverMaterializer
@Inject
@Suppress("LongParameterList", "TooManyFunctions")
constructor(
    private val database: AppDatabase,
    private val podcastDao: PodcastDao,
    private val coverStateDao: PodcastCoverStateDao,
    private val thumbnailStore: PodcastCoverThumbnailStore,
    private val refreshPolicy: PodcastCoverRefreshPolicy,
    private val clock: SystemPodcastCoverClock,
    private val fileLifecycleLock: PodcastCoverFileLifecycleLock,
    private val imageDiskCache: DiskCache,
    @Named("ImageMediaClient") private val imageClient: OkHttpClient,
    private val dispatcherProvider: DispatcherProvider
) {
    private val podcastLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun materialize(
        podcastRssUrl: String,
        force: Boolean = false
    ): PodcastCoverMaterializationResult =
        podcastLocks.getOrPut(podcastRssUrl) { Mutex() }.withLock {
            withContext(dispatcherProvider.io) {
                materializeLocked(podcastRssUrl, force)
            }
        }

    suspend fun resolveForDisplay(
        podcastRssUrl: String,
        latestSourceUrl: String,
        claimedFileName: String?
    ): PodcastCoverMaterializationResult {
        thumbnailStore.validFile(claimedFileName)?.let { file ->
            return PodcastCoverMaterializationResult.Available(
                file,
                PodcastCoverMaterializationSource.PERSISTENT
            )
        }
        ensureState(podcastRssUrl, latestSourceUrl)
        return materialize(podcastRssUrl)
    }

    suspend fun ensureState(
        podcastRssUrl: String,
        latestSourceUrl: String
    ) {
        if (coverStateDao.getState(podcastRssUrl) != null) return
        val now = clock.now()
        database.withTransaction {
            if (coverStateDao.getState(podcastRssUrl) == null) {
                coverStateDao.observeFeedCandidate(podcastRssUrl, latestSourceUrl, now)
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun materializeLocked(
        podcastRssUrl: String,
        force: Boolean
    ): PodcastCoverMaterializationResult {
        val podcast = podcastDao.getPodcastByUrl(podcastRssUrl) ?: return PodcastCoverMaterializationResult.NoSource
        ensureState(podcastRssUrl, podcast.imageUrl)
        val state = coverStateDao.getState(podcastRssUrl) ?: return PodcastCoverMaterializationResult.NoSource
        val activeFile = thumbnailStore.validFile(state.thumbnailFileName)
        if (state.thumbnailFileName != null && activeFile == null) {
            coverStateDao.clearMissingFile(podcastRssUrl, state.thumbnailFileName)
        }
        val refreshedState = coverStateDao.getState(podcastRssUrl) ?: state
        return when (
            val decision = refreshPolicy.decide(
                state = refreshedState,
                latestFeedUrl = podcast.imageUrl,
                activeFileValid = activeFile != null,
                now = clock.now(),
                force = force
            )
        ) {
            PodcastCoverRefreshDecision.NoSource -> PodcastCoverMaterializationResult.NoSource
            is PodcastCoverRefreshDecision.Wait ->
                activeFile?.let {
                    PodcastCoverMaterializationResult.Available(
                        it,
                        PodcastCoverMaterializationSource.PERSISTENT
                    )
                } ?: PodcastCoverMaterializationResult.Waiting(null, decision.delayMs)
            is PodcastCoverRefreshDecision.Refresh ->
                refresh(
                    podcastRssUrl = podcastRssUrl,
                    sourceUrl = decision.sourceUrl,
                    state = refreshedState,
                    activeFile = activeFile,
                    allowInsecureHttp = podcast.allowInsecureHttp,
                    allowLocalNetwork = podcast.allowLocalNetwork,
                    mayReuseLegacyDisk = activeFile == null
                )
        }
    }

    @Suppress(
        "LongMethod",
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "ReturnCount",
        "TooGenericExceptionCaught"
    )
    private suspend fun refresh(
        podcastRssUrl: String,
        sourceUrl: String,
        state: PodcastCoverStateEntity,
        activeFile: File?,
        allowInsecureHttp: Boolean,
        allowLocalNetwork: Boolean,
        mayReuseLegacyDisk: Boolean
    ): PodcastCoverMaterializationResult {
        val approvedUrl =
            runCatching {
                requireApprovedPodcastResource(
                    feedUrl = podcastRssUrl,
                    resourceUrl = sourceUrl,
                    allowInsecureHttp = allowInsecureHttp,
                    allowLocalNetwork = allowLocalNetwork
                )
            }.getOrElse { error ->
                return permanentFailure(
                    podcastRssUrl,
                    sourceUrl,
                    state,
                    IOException("Podcast cover URL is not approved", error)
                )
            }

        if (mayReuseLegacyDisk) {
            materializeLegacyDisk(podcastRssUrl, sourceUrl, state)?.let { return it }
        }

        val requestBuilder = Request.Builder().url(approvedUrl)
        if (activeFile != null && state.activeSourceUrl == sourceUrl) {
            state.eTag?.let { requestBuilder.header("If-None-Match", it) }
            state.lastModified?.let { requestBuilder.header("If-Modified-Since", it) }
        }

        return try {
            imageClient.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.code == HTTP_NOT_MODIFIED && activeFile != null -> {
                        coverStateDao.recordNotModified(
                            rssUrl = podcastRssUrl,
                            sourceUrl = sourceUrl,
                            checkedAt = clock.now(),
                            eTag = response.header("ETag"),
                            lastModified = response.header("Last-Modified")
                        )
                        PodcastCoverMaterializationResult.Available(
                            activeFile,
                            PodcastCoverMaterializationSource.NOT_MODIFIED
                        )
                    }
                    response.code == HTTP_NOT_MODIFIED ->
                        retryableFailure(
                            podcastRssUrl,
                            sourceUrl,
                            state,
                            IOException("Podcast cover returned 304 without a valid local file")
                        )
                    !response.isSuccessful -> {
                        val error = IOException("Podcast cover HTTP ${response.code}")
                        if (response.code == HTTP_TOO_MANY_REQUESTS || response.code >= HTTP_SERVER_ERROR) {
                            retryableFailure(podcastRssUrl, sourceUrl, state, error)
                        } else {
                            permanentFailure(podcastRssUrl, sourceUrl, state, error)
                        }
                    }
                    else -> {
                        val body = response.body ?: return@use permanentFailure(
                            podcastRssUrl,
                            sourceUrl,
                            state,
                            IOException("Podcast cover response had no body")
                        )
                        val contentType = body.contentType()?.toString().orEmpty()
                        if (!contentType.startsWith("image/", ignoreCase = true)) {
                            return@use permanentFailure(
                                podcastRssUrl,
                                sourceUrl,
                                state,
                                IOException("Podcast cover response was not an image")
                            )
                        }
                        val contentLength = body.contentLength()
                        if (contentLength > PodcastCoverThumbnailStore.MAX_SOURCE_BYTES) {
                            return@use permanentFailure(
                                podcastRssUrl,
                                sourceUrl,
                                state,
                                IOException("Podcast cover response exceeded the source size limit")
                            )
                        }
                        fileLifecycleLock.withLock {
                            val published =
                                body.byteStream().use { input ->
                                    thumbnailStore.publish(podcastRssUrl, input)
                                }
                            promote(
                                podcastRssUrl = podcastRssUrl,
                                sourceUrl = sourceUrl,
                                state = state,
                                published = published,
                                eTag = response.header("ETag"),
                                lastModified = response.header("Last-Modified"),
                                source = PodcastCoverMaterializationSource.NETWORK
                            )
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: InvalidPodcastCoverException) {
            permanentFailure(podcastRssUrl, sourceUrl, state, error)
        } catch (error: IOException) {
            retryableFailure(podcastRssUrl, sourceUrl, state, error)
        } catch (error: RuntimeException) {
            permanentFailure(
                podcastRssUrl,
                sourceUrl,
                state,
                IOException("Podcast cover processing failed", error)
            )
        }
    }

    private suspend fun materializeLegacyDisk(
        podcastRssUrl: String,
        sourceUrl: String,
        state: PodcastCoverStateEntity
    ): PodcastCoverMaterializationResult? {
        val snapshot = imageDiskCache.openSnapshot(sourceUrl) ?: return null
        return try {
            fileLifecycleLock.withLock {
                val published = thumbnailStore.publishFile(podcastRssUrl, snapshot.data.toFile())
                promote(
                    podcastRssUrl = podcastRssUrl,
                    sourceUrl = sourceUrl,
                    state = state,
                    published = published,
                    eTag = null,
                    lastModified = null,
                    source = PodcastCoverMaterializationSource.LEGACY_DISK
                )
            }
        } catch (_: IOException) {
            null
        } finally {
            snapshot.close()
        }
    }

    private suspend fun promote(
        podcastRssUrl: String,
        sourceUrl: String,
        state: PodcastCoverStateEntity,
        published: PublishedPodcastCover,
        eTag: String?,
        lastModified: String?,
        source: PodcastCoverMaterializationSource
    ): PodcastCoverMaterializationResult {
        val checkedAt = clock.now()
        val identicalPromotion =
            state.contentSha256 == published.contentSha256 &&
                state.thumbnailFileName != null &&
                thumbnailStore.validFile(state.thumbnailFileName) != null
        val updated =
            if (identicalPromotion) {
                coverStateDao.promoteIdenticalThumbnail(
                    rssUrl = podcastRssUrl,
                    sourceUrl = sourceUrl,
                    checkedAt = checkedAt,
                    contentSha256 = published.contentSha256,
                    eTag = eTag,
                    lastModified = lastModified
                )
            } else {
                coverStateDao.promoteThumbnail(
                    rssUrl = podcastRssUrl,
                    sourceUrl = sourceUrl,
                    fileName = published.fileName,
                    checkedAt = checkedAt,
                    contentSha256 = published.contentSha256,
                    eTag = eTag,
                    lastModified = lastModified
                )
            }
        if (updated != 1) {
            if (published.createdNewFile) thumbnailStore.deleteFile(published.fileName)
            return PodcastCoverMaterializationResult.NoSource
        }
        if (!identicalPromotion && state.thumbnailFileName != published.fileName) {
            thumbnailStore.deleteFile(state.thumbnailFileName)
        }
        val activeName = if (identicalPromotion) state.thumbnailFileName else published.fileName
        val activeFile = checkNotNull(thumbnailStore.validFile(activeName))
        return PodcastCoverMaterializationResult.Available(activeFile, source)
    }

    private suspend fun retryableFailure(
        podcastRssUrl: String,
        sourceUrl: String,
        state: PodcastCoverStateEntity,
        error: IOException
    ): PodcastCoverMaterializationResult.RetryableFailure {
        recordFailure(podcastRssUrl, sourceUrl, state, retryable = true)
        return PodcastCoverMaterializationResult.RetryableFailure(error)
    }

    private suspend fun permanentFailure(
        podcastRssUrl: String,
        sourceUrl: String,
        state: PodcastCoverStateEntity,
        error: IOException
    ): PodcastCoverMaterializationResult.PermanentFailure {
        recordFailure(podcastRssUrl, sourceUrl, state, retryable = false)
        return PodcastCoverMaterializationResult.PermanentFailure(error)
    }

    private suspend fun recordFailure(
        podcastRssUrl: String,
        sourceUrl: String,
        state: PodcastCoverStateEntity,
        retryable: Boolean
    ) {
        val count = (state.failureCount + 1).coerceAtMost(MAX_FAILURE_COUNT)
        val delay =
            if (retryable) {
                min(BASE_RETRY_MS * (1L shl (count - 1)), MAX_RETRY_MS)
            } else {
                MAX_RETRY_MS
            }
        coverStateDao.recordFailure(podcastRssUrl, sourceUrl, count, clock.now() + delay)
    }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
        const val MAX_FAILURE_COUNT = 6
        const val BASE_RETRY_MS = 15L * 60L * 1000L
        const val MAX_RETRY_MS = 24L * 60L * 60L * 1000L
    }
}
