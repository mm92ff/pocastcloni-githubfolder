package com.example.pocastcloni.data.repository

import android.content.Context
import android.net.Uri
import com.example.pocastcloni.R
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.manager.PodcastBackupHelper
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.BackupRepository
import com.example.pocastcloni.domain.repository.ImportResult
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.podcast.SyncFeedUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl
@Inject
constructor(
    private val podcastDao: PodcastDao,
    private val backupHelper: PodcastBackupHelper,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncFeedUseCase: Provider<SyncFeedUseCase>,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context
) : BackupRepository {
    override suspend fun exportFullBackup(
        uri: Uri,
        settings: UserSettings
    ) {
        withContext(dispatcherProvider.io) {
            val podcasts = podcastDao.getAllPodcastsForExport()
            val favorites = podcastDao.getFavoriteEpisodesSync()

            val backupPodcasts = podcasts.map { it.toBackupPodcast() }
            val backupFavorites = favorites.map { it.toBackupFavorite() }

            backupHelper.exportBackup(
                podcasts = backupPodcasts,
                favorites = backupFavorites,
                settings = settings,
                uri = uri,
                contentResolver = context.contentResolver
            )
        }
    }

    override suspend fun importFullBackup(
        uri: Uri,
        downloadLimit: Int,
        mode: FeedUpdateMode
    ): ImportResult {
        return withContext(dispatcherProvider.io) {
            val backupData = backupHelper.importBackup(uri, context.contentResolver)

            // 1. Restore settings
            backupData.settings?.let { userPreferencesRepository.restoreSettings(it) }

            // 2. Import podcasts (offline-first strategy)
            var success = 0
            val total = backupData.podcasts.size

            // Determine the next sort order in case the backup has 0
            var currentMaxSortOrder = podcastDao.getMaxSortOrder() ?: 0L

            backupData.podcasts.forEach { backupPodcast ->
                val url = backupPodcast.url
                if (url.isNotBlank()) {
                    // Step A: create a "stub" entity and insert it immediately (if not already present)
                    // This guarantees the podcast exists even if the sync fails (offline).
                    val orderToUse = if (backupPodcast.sortOrder > 0) backupPodcast.sortOrder else ++currentMaxSortOrder

                    val stubEntity =
                        PodcastEntity(
                            rssUrl = url,
                            title = backupPodcast.title ?: context.getString(R.string.import_fallback_title),
                            description = backupPodcast.description ?: context.getString(R.string.import_fallback_description),
                            imageUrl = backupPodcast.imageUrl ?: "",
                            sortOrder = orderToUse,
                            // Restore the caching headers here:
                            lastModifiedHeader = backupPodcast.lastModifiedHeader,
                            eTagHeader = backupPodcast.eTagHeader,
                            lastRefreshed = Date(0) // Markiert als "braucht update"
                        )

                    // Insert Ignore: if it already exists, we do NOT overwrite it (to protect local updates)
                    // If it is new, it is now visible.
                    runCatching {
                        podcastDao.insertPodcast(stubEntity)
                    }.onFailure { Timber.w(it, "Failed to insert stub for $url") }

                    // Step B: attempt sync (network)
                    // We use 'forceFull = false' so that ETag/LastModified is used if available!
                    runCatching {
                        syncFeedUseCase.get().invoke(
                            url,
                            downloadLimit,
                            mode,
                            sortOrder = null, // Do not overwrite sortOrder, it was already set above
                            forceFull = false // Use smart update!
                        )
                        success++
                    }.onFailure {
                        Timber.w(it, "Sync failed during import for $url (Offline?)")
                        // Despite the sync failure we count it as a "partial success" since the podcast is now in the DB.
                        // If the stub was inserted successfully, it is acceptable for the user.
                        if (podcastDao.getPodcastByUrl(url) != null) {
                            success++
                        }
                    }
                }
            }

            // 3. Restore favorites
            val favoriteGuids = backupData.favorites.map { it.episodeGuid }.distinct()
            val existingFavoriteGuids =
                if (favoriteGuids.isEmpty()) {
                    emptySet()
                } else {
                    podcastDao.getExistingGuids(favoriteGuids).toSet()
                }

            val restorableFavorites =
                backupData.favorites
                    .filter { it.episodeGuid in existingFavoriteGuids }
            restorableFavorites.forEach { fav ->
                podcastDao.setFavoriteStatus(fav.episodeGuid, true, fav.timestamp)
            }

            val skippedFavorites = backupData.favorites.size - restorableFavorites.size
            if (skippedFavorites > 0) {
                Timber.w("Skipped restoring %d favorites because the episodes are not available locally.", skippedFavorites)
            }

            ImportResult(success, total)
        }
    }
}
