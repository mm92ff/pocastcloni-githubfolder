package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.work.WorkManager
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import com.example.pocastcloni.data.cache.MediaCacheProvider
import com.example.pocastcloni.data.repository.AppResetMarkerStore
import com.example.pocastcloni.domain.repository.AppResetGateway
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
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
    @ApplicationContext private val context: Context
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
        val failures = mutableListOf<Exception>()

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

        runResetStep("close media cache") { mediaCacheProvider.close() }
        runResetStep("clear database") { resetDatabase() }
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
