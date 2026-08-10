package com.example.pocastcloni.data.worker

import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import com.example.pocastcloni.data.cover.PodcastCoverFileLifecycleLock
import com.example.pocastcloni.data.cover.PodcastCoverThumbnailStore
import com.example.pocastcloni.data.cache.MediaCacheProvider
import com.example.pocastcloni.data.repository.AppResetMarkerStore
import com.example.pocastcloni.domain.repository.AppResetGateway
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.playback.api.PlaybackResetPort
import com.example.pocastcloni.service.PodcastPlaybackService
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoilApi::class)
class AndroidAppResetGateway
@Inject
@Suppress("LongParameterList")
constructor(
    private val mediaCacheProvider: MediaCacheProvider,
    private val imageLoader: ImageLoader,
    private val okHttpClient: OkHttpClient,
    @Named("LocalNetworkClient") private val localNetworkClient: OkHttpClient,
    @Named("ApprovedMediaClient") private val approvedMediaClient: OkHttpClient,
    private val workManager: WorkManager,
    private val markerStore: AppResetMarkerStore,
    private val schedulingCoordinator: AppSchedulingCoordinator,
    private val playbackResetPort: PlaybackResetPort,
    private val podcastCoverThumbnailStore: PodcastCoverThumbnailStore? = null,
    @ApplicationContext private val context: Context,
    private val podcastCoverFileLifecycleLock: PodcastCoverFileLifecycleLock? = null
) : AppResetGateway {
    override suspend fun runResetAndReconcile(
        clearSettings: suspend () -> Unit,
        resetDatabase: suspend () -> Unit,
        loadFinalSettings: suspend () -> UserSettings,
        markPending: Boolean
    ) {
        schedulingCoordinator.runResetAndReconcile(
            reset = {
                if (markPending) markerStore.markPending()
                clearSettings()
                workManager.cancelAllWork().result.await()
                clearInfrastructure(resetDatabase)
            },
            loadFinalSettings = loadFinalSettings,
            completeReset = markerStore::clear
        )
    }

    override suspend fun isPending(): Boolean = markerStore.isPending()

    private suspend fun clearInfrastructure(resetDatabase: suspend () -> Unit) {
        val cacheReset = mediaCacheProvider.beginReset()
        val failures = mutableListOf<Exception>()
        var mediaCacheClosed = false

        try {
            // Reset steps are best-effort; retain every non-cancellation failure while continuing cleanup.
            @Suppress("TooGenericExceptionCaught")
            suspend fun runResetStep(
                description: String,
                block: suspend () -> Unit
            ) {
                try {
                    block()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += error
                    Timber.w(error, "Reset step failed: %s", description)
                }
            }

            runResetStep("stop playback") {
                try {
                    playbackResetPort.stopAndReleaseForReset()
                } finally {
                    context.stopService(Intent(context, PodcastPlaybackService::class.java))
                }
            }
            runResetStep("await playback shutdown") {
                awaitPlaybackShutdown()
            }
            runResetStep("close media cache") {
                mediaCacheProvider.closeForReset()
                mediaCacheClosed = true
            }
            runResetStep("clear database") { resetDatabase() }
            runResetStep("clear persistent podcast covers") {
                podcastCoverThumbnailStore?.let { store ->
                    val lifecycleLock = podcastCoverFileLifecycleLock
                    if (lifecycleLock == null) {
                        store.clearAll()
                    } else {
                        lifecycleLock.withLock { store.clearAll() }
                    }
                }
            }
            runResetStep("clear Coil memory cache") { imageLoader.memoryCache?.clear() }
            runResetStep("clear Coil disk cache") { imageLoader.diskCache?.clear() }
            runResetStep("evict default HTTP cache") { okHttpClient.cache?.evictAll() }
            runResetStep("evict local HTTP cache") { localNetworkClient.cache?.evictAll() }
            runResetStep("evict approved media HTTP cache") { approvedMediaClient.cache?.evictAll() }

            Constants.Cache.MANAGED_CACHE_DIRS
                .filterNot { it == Constants.Cache.MEDIA_CACHE_DIR && !mediaCacheClosed }
                .forEach { directoryName ->
                    runResetStep("delete $directoryName") {
                        val directory = context.cacheDir.resolve(directoryName)
                        if (directory.exists() && !directory.deleteRecursively()) {
                            throw IOException("Failed to delete managed cache directory: $directoryName")
                        }
                    }
                }

            throwResetFailures(failures)
        } finally {
            cacheReset.close()
        }
    }

    private fun awaitPlaybackShutdown() {
        if (!mediaCacheProvider.awaitNoActiveLeases(CACHE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw IOException("Timed out waiting for playback to release the media cache")
        }
    }

    private fun throwResetFailures(failures: List<Exception>) {
        failures.firstOrNull()?.let { primaryFailure ->
            failures.drop(1).forEach(primaryFailure::addSuppressed)
            throw primaryFailure
        }
    }

    private companion object {
        const val CACHE_SHUTDOWN_TIMEOUT_SECONDS = 10L
    }
}
