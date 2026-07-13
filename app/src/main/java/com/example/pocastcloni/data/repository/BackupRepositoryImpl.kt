package com.example.pocastcloni.data.repository

import android.content.Context
import android.net.Uri
import com.example.pocastcloni.R
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.local.BackupImportJournalDao
import com.example.pocastcloni.data.local.BackupImportJournalEntity
import com.example.pocastcloni.data.local.settingsForRestore
import com.example.pocastcloni.data.manager.PodcastBackupHelper
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.BackupRepository
import com.example.pocastcloni.domain.repository.ImportResult
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.podcast.SyncFeedUseCase
import com.example.pocastcloni.util.isAllowedRemoteResource
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val transactionRunner: BackupImportTransactionRunner,
    private val backupImportJournalDao: BackupImportJournalDao,
    private val objectMapper: ObjectMapper,
    private val backupImportRecovery: BackupImportRecovery,
    @ApplicationContext private val context: Context
) : BackupRepository {
    private val importMutex = Mutex()

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
            importMutex.withLock {
                importFullBackupLocked(uri, downloadLimit, mode)
            }
        }
    }

    private suspend fun importFullBackupLocked(
        uri: Uri,
        downloadLimit: Int,
        mode: FeedUpdateMode
    ): ImportResult {
        backupImportRecovery.recoverInterruptedImport()
        val backupData = backupHelper.importBackup(uri, context.contentResolver)
        val total = backupData.podcasts.size
        val previousSettings = userPreferencesRepository.userSettingsFlow.first()
        val settingsToRestore = backupData.settingsForRestore(previousSettings)
        val pendingImport = BackupImportJournalEntity(
            previousSettingsJson = objectMapper.writeValueAsString(previousSettings)
        )
        backupImportJournalDao.savePendingImport(pendingImport)

        val syncTargets = mutableListOf<PodcastEntity>()
        try {
            settingsToRestore?.let { settings ->
                userPreferencesRepository.restoreSettingsOrThrow(settings)
            }
            transactionRunner.run {
                var currentMaxSortOrder = podcastDao.getMaxSortOrder() ?: 0L
                backupData.podcasts.forEach { backupPodcast ->
                    val existing = podcastDao.getPodcastByUrl(backupPodcast.url)
                    val safeImageUrl = backupPodcast.imageUrl.orEmpty().takeIf { imageUrl ->
                        isAllowedRemoteResource(
                            imageUrl,
                            allowInsecureHttp = existing?.allowInsecureHttp == true,
                            allowLocalNetwork = existing?.allowLocalNetwork == true
                        )
                    }.orEmpty()
                    val orderToUse = if (backupPodcast.sortOrder > 0) {
                        backupPodcast.sortOrder
                    } else {
                        ++currentMaxSortOrder
                    }
                    val stub = PodcastEntity(
                        rssUrl = backupPodcast.url,
                        title = backupPodcast.title ?: context.getString(R.string.import_fallback_title),
                        description = backupPodcast.description
                            ?: context.getString(R.string.import_fallback_description),
                        imageUrl = safeImageUrl,
                        allowInsecureHttp = false,
                        allowLocalNetwork = false,
                        sortOrder = orderToUse,
                        lastModifiedHeader = backupPodcast.lastModifiedHeader,
                        eTagHeader = backupPodcast.eTagHeader,
                        lastRefreshed = Date(0)
                    )
                    insertPodcastStubPreservingExisting(podcastDao, stub)?.let(syncTargets::add)
                }
                restoreAvailableFavorites(backupData.favorites)
                backupImportJournalDao.clearPendingImport()
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                rollbackInterruptedImport(previousSettings, error)
            }
            throw error
        } catch (error: Exception) {
            withContext(NonCancellable) {
                rollbackInterruptedImport(previousSettings, error)
            }
            throw error
        }

        syncTargets.forEach { storedPodcast ->
            if (
                maySyncImportedFeed(
                    storedPodcast.rssUrl,
                    storedPodcast.allowInsecureHttp,
                    storedPodcast.allowLocalNetwork
                )
            ) {
                try {
                    syncFeedUseCase.get().invoke(
                        storedPodcast.rssUrl,
                        downloadLimit,
                        mode,
                        sortOrder = null,
                        forceFull = false,
                        allowInsecureHttp = storedPodcast.allowInsecureHttp,
                        allowLocalNetwork = storedPodcast.allowLocalNetwork
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.w(error, "Post-import sync failed for ${storedPodcast.rssUrl}")
                }
            }
        }

        var skippedFavorites = 0
        transactionRunner.run {
            skippedFavorites = restoreAvailableFavorites(backupData.favorites)
        }
        return ImportResult(total, total, skippedFavorites)
    }

    private suspend fun restoreAvailableFavorites(
        favorites: List<com.example.pocastcloni.data.local.BackupFavorite>
    ): Int {
        var restored = 0
        favorites.forEach { favorite ->
            val episode = podcastDao.getEpisodeByFeedAndGuid(favorite.podcastUrl, favorite.episodeGuid)
            if (episode != null) {
                podcastDao.setFavoriteStatus(
                    episodeId = episode.episodeId,
                    isFavorite = true,
                    timestamp = favorite.timestamp,
                    favoriteAddedAt = favorite.timestamp
                )
                restored++
            }
        }
        val skipped = favorites.size - restored
        if (skipped > 0) {
            Timber.w("Skipped restoring %d favorites because episodes are unavailable.", skipped)
        }
        return skipped
    }

    private suspend fun rollbackInterruptedImport(
        previousSettings: UserSettings,
        importError: Throwable
    ) {
        try {
            userPreferencesRepository.restoreSettingsOrThrow(previousSettings)
            backupImportJournalDao.clearPendingImport()
        } catch (rollbackError: Throwable) {
            importError.addSuppressed(rollbackError)
        }
    }
}

internal suspend fun insertPodcastStubPreservingExisting(
    podcastDao: PodcastDao,
    stub: PodcastEntity
): PodcastEntity? {
    podcastDao.getPodcastByUrl(stub.rssUrl)?.let { return it }
    podcastDao.insertPodcasts(listOf(stub))
    return podcastDao.getPodcastByUrl(stub.rssUrl)
}

internal fun maySyncImportedFeed(
    feedUrl: String,
    hasExistingHttpApproval: Boolean,
    hasExistingLocalApproval: Boolean
): Boolean = isAllowedRemoteResource(
    feedUrl,
    allowInsecureHttp = hasExistingHttpApproval,
    allowLocalNetwork = hasExistingLocalApproval
)
