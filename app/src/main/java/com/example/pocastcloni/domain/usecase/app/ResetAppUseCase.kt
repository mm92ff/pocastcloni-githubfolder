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

class ResetAppUseCase @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val imageLoader: ImageLoader,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke() {
        withContext(dispatcherProvider.io) {
            // 1. User Settings auf Default zurücksetzen
            userPreferencesRepository.clearSettings()

            // 2. Datenbank leeren
            podcastRepository.resetDatabase()

            // 3. Coil Memory Cache leeren (wichtig für die aktuelle Sitzung)
            imageLoader.memoryCache?.clear()

            // 4. Alle Disk-Caches physisch vom Gerät löschen
            // Dies ist die robusteste Methode, um Race Conditions zu vermeiden.
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
