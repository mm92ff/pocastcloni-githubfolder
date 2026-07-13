package com.example.pocastcloni.domain.usecase.app

import android.content.Context
import androidx.work.WorkManager
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import com.example.pocastcloni.data.cache.MediaCacheProvider
import com.example.pocastcloni.data.repository.AppResetMarkerStore
import com.example.pocastcloni.data.worker.AppSchedulingCoordinator
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

@OptIn(ExperimentalCoilApi::class)
class ResetAppUseCase
@Inject
// Hilt keeps the independently scoped reset collaborators explicit at this lifecycle boundary.
@Suppress("LongParameterList")
constructor(
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaCacheProvider: MediaCacheProvider,
    private val imageLoader: ImageLoader,
    private val okHttpClient: OkHttpClient,
    @Named("LocalNetworkClient") private val localNetworkClient: OkHttpClient,
    @Named("ApprovedMediaClient") private val approvedMediaClient: OkHttpClient,
    private val workManager: WorkManager,
    private val markerStore: AppResetMarkerStore,
    private val schedulingCoordinator: AppSchedulingCoordinator,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context
) {
    private val resetMutex = Mutex()

    suspend operator fun invoke() {
        withContext(dispatcherProvider.io) {
            resetMutex.withLock {
                schedulingCoordinator.runResetAndReconcile(
                    reset = {
                        markerStore.markPending()
                        executePendingReset()
                    },
                    loadFinalSettings = { userPreferencesRepository.userSettingsFlow.first() },
                    completeReset = markerStore::clear
                )
            }
        }
    }

    suspend fun resumeIfPending(): Boolean =
        withContext(dispatcherProvider.io) {
            resetMutex.withLock {
                if (!markerStore.isPending()) return@withLock false
                schedulingCoordinator.runResetAndReconcile(
                    reset = ::executePendingReset,
                    loadFinalSettings = { userPreferencesRepository.userSettingsFlow.first() },
                    completeReset = markerStore::clear
                )
                true
            }
        }

    private suspend fun executePendingReset() {
        userPreferencesRepository.clearSettings()
        workManager.cancelAllWork().result.await()

        val failures = mutableListOf<Exception>()
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

        runResetStep("close media cache") { mediaCacheProvider.close() }
        runResetStep("clear database") { podcastRepository.resetDatabase() }
        runResetStep("clear Coil memory cache") { imageLoader.memoryCache?.clear() }
        runResetStep("clear Coil disk cache") { imageLoader.diskCache?.clear() }
        runResetStep("evict default HTTP cache") { okHttpClient.cache?.evictAll() }
        runResetStep("evict local HTTP cache") { localNetworkClient.cache?.evictAll() }
        runResetStep("evict approved media HTTP cache") { approvedMediaClient.cache?.evictAll() }

        Constants.Cache.MANAGED_CACHE_DIRS.forEach { directoryName ->
            runResetStep("delete $directoryName") {
                val directory = context.cacheDir.resolve(directoryName)
                if (directory.exists() && !directory.deleteRecursively()) {
                    throw IOException("Failed to delete managed cache directory: $directoryName")
                }
            }
        }

        failures.firstOrNull()?.let { primaryFailure ->
            failures.drop(1).forEach(primaryFailure::addSuppressed)
            throw primaryFailure
        }
    }
}
