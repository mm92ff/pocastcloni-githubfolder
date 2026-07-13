package com.example.pocastcloni.domain.usecase.app

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import com.example.pocastcloni.data.cache.MediaCacheProvider
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

@OptIn(ExperimentalCoilApi::class)
class ResetAppUseCase
@Inject
constructor(
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaCacheProvider: MediaCacheProvider,
    private val imageLoader: ImageLoader,
    private val okHttpClient: OkHttpClient,
    @Named("LocalNetworkClient") private val localNetworkClient: OkHttpClient,
    @Named("ApprovedMediaClient") private val approvedMediaClient: OkHttpClient,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke() {
        withContext(dispatcherProvider.io) {
            val failures = mutableListOf<Exception>()

            suspend fun runResetStep(
                description: String,
                block: suspend () -> Unit
            ) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += e
                    Timber.w(e, "Reset step failed: %s", description)
                }
            }

            runResetStep("close media cache") { mediaCacheProvider.close() }
            runResetStep("clear user settings") { userPreferencesRepository.clearSettings() }
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
}
