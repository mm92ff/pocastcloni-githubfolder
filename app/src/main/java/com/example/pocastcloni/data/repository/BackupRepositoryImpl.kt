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

            // 1. Settings wiederherstellen
            backupData.settings?.let { userPreferencesRepository.restoreSettings(it) }

            // 2. Podcasts importieren (Offline-First Strategie)
            var success = 0
            val total = backupData.podcasts.size

            // Ermittle die nächste Sortierreihenfolge, falls im Backup 0 steht
            var currentMaxSortOrder = podcastDao.getMaxSortOrder() ?: 0L

            backupData.podcasts.forEach { backupPodcast ->
                val url = backupPodcast.url
                if (url.isNotBlank()) {
                    // Schritt A: "Stub" Entity erstellen und sofort einfügen (falls noch nicht existiert)
                    // Das garantiert, dass der Podcast da ist, auch wenn der Sync fehlschlägt (Offline).
                    val orderToUse = if (backupPodcast.sortOrder > 0) backupPodcast.sortOrder else ++currentMaxSortOrder

                    val stubEntity =
                        PodcastEntity(
                            rssUrl = url,
                            title = backupPodcast.title ?: context.getString(R.string.import_fallback_title),
                            description = backupPodcast.description ?: context.getString(R.string.import_fallback_description),
                            imageUrl = backupPodcast.imageUrl ?: "",
                            sortOrder = orderToUse,
                            // Hier stellen wir die Caching-Header wieder her:
                            lastModifiedHeader = backupPodcast.lastModifiedHeader,
                            eTagHeader = backupPodcast.eTagHeader,
                            lastRefreshed = Date(0) // Markiert als "braucht update"
                        )

                    // Insert Ignore: Wenn er schon da ist, überschreiben wir ihn NICHT (um lokale Updates zu schützen)
                    // Wenn er neu ist, ist er jetzt sichtbar.
                    runCatching {
                        podcastDao.insertPodcast(stubEntity)
                    }.onFailure { Timber.w(it, "Failed to insert stub for $url") }

                    // Schritt B: Sync versuchen (Netzwerk)
                    // Wir nutzen 'forceFull = false', damit ETag/LastModified genutzt werden, falls vorhanden!
                    runCatching {
                        syncFeedUseCase.get().invoke(
                            url,
                            downloadLimit,
                            mode,
                            sortOrder = null, // SortOrder nicht überschreiben, da oben schon gesetzt
                            forceFull = false // Smart Update nutzen!
                        )
                        success++
                    }.onFailure {
                        Timber.w(it, "Sync failed during import for $url (Offline?)")
                        // Trotz Sync-Fehler zählen wir es als "Halb-Erfolg", da der Podcast nun in der DB ist.
                        // Wenn der Stub erfolgreich eingefügt wurde, ist es für den User okay.
                        if (podcastDao.getPodcastByUrl(url) != null) {
                            success++
                        }
                    }
                }
            }

            // 3. Favorites wiederherstellen
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
