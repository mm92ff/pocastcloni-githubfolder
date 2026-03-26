package com.example.pocastcloni.domain.usecase.app

import android.content.Context
import coil.ImageLoader
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class ResetAppUseCase
@Inject
constructor(
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val imageLoader: ImageLoader,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke() {
        withContext(dispatcherProvider.io) {
            // 1. Reset user settings to defaults
            userPreferencesRepository.clearSettings()

            // 2. Clear database
            podcastRepository.resetDatabase()

            // 3. Clear Coil memory cache (important for the current session)
            imageLoader.memoryCache?.clear()

            // 4. Physically delete all disk caches from the device
            // This is the most robust method to avoid race conditions.
            try {
                val coilCache = File(context.cacheDir, "image_cache")
                if (coilCache.exists()) {
                    coilCache.deleteRecursively()
                    Timber.d("Coil disk cache deleted.")
                }

                val okHttpCache = File(context.cacheDir, "http_cache")
                if (okHttpCache.exists()) {
                    okHttpCache.deleteRecursively()
                    Timber.d("OkHttp disk cache deleted.")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete cache directories.")
            }
        }
    }
}
