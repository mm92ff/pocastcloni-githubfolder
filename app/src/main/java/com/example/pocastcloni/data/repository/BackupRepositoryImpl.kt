package com.example.pocastcloni.data.repository

import android.content.Context
import android.net.Uri
import com.example.pocastcloni.R
import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupEpisodeState
import com.example.pocastcloni.data.local.BackupFavorite
import com.example.pocastcloni.data.local.BackupImportJournalDao
import com.example.pocastcloni.data.local.BackupImportJournalEntity
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.FavoriteOrderUpdate
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.local.PodcastSortUpdate
import com.example.pocastcloni.data.local.settingsForRestore
import com.example.pocastcloni.data.manager.PodcastBackupHelper
import com.example.pocastcloni.data.manager.validateBackupData
import com.example.pocastcloni.data.worker.BackupPostImportSyncScheduler
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.BackupLocation
import com.example.pocastcloni.domain.repository.BackupRepository
import com.example.pocastcloni.domain.repository.ImportResult
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.isAllowedRemoteResource
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates full backup export and crash-recoverable import across Room and user preferences.
 *
 * Imports are serialized process-wide, validated before mutation, and journal the previous
 * settings before touching either store. Room changes commit transactionally; mutation failures
 * and cancellation restore settings before the journal can be cleared. Imported feed flags never
 * grant cleartext or local-network approval, and post-commit feed synchronization is best effort.
 */
@Singleton
class BackupRepositoryImpl
@Inject
constructor(
    private val podcastDao: PodcastDao,
    private val backupHelper: PodcastBackupHelper,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val transactionRunner: BackupImportTransactionRunner,
    private val backupImportJournalDao: BackupImportJournalDao,
    private val objectMapper: ObjectMapper,
    private val backupImportRecovery: BackupImportRecovery,
    private val importCoordinator: BackupImportCoordinator,
    private val postImportSyncScheduler: BackupPostImportSyncScheduler,
    @ApplicationContext private val context: Context
) : BackupRepository {
    override suspend fun exportBackup(
        location: BackupLocation,
        settings: UserSettings
    ) = exportFullBackup(Uri.parse(location.value), settings)

    suspend fun exportFullBackup(
        uri: Uri,
        settings: UserSettings
    ) {
        withContext(dispatcherProvider.io) {
            val snapshot = transactionRunner.captureBackupSnapshot()

            val backupPodcasts = snapshot.podcasts.map { it.toBackupPodcast() }
            val backupEpisodeStates = snapshot.episodeStates.toBackupEpisodeStates()

            backupHelper.exportBackup(
                podcasts = backupPodcasts,
                episodeStates = backupEpisodeStates,
                settings = settings,
                uri = uri,
                contentResolver = context.contentResolver
            )
        }
    }

    override suspend fun importBackup(location: BackupLocation): ImportResult =
        importFullBackup(Uri.parse(location.value))

    suspend fun importFullBackup(uri: Uri): ImportResult {
        return withContext(dispatcherProvider.io) {
            importCoordinator.runExclusive {
                importFullBackupLocked(uri)
            }
        }
    }

    private suspend fun importFullBackupLocked(uri: Uri): ImportResult {
        val backupData = validateBackupData(backupHelper.importBackup(uri, context.contentResolver))
        backupImportRecovery.recoverInterruptedImportLocked()
        val total = backupData.podcasts.size
        val importedPodcasts = backupData.podcasts.normalizedForImport()
        val previousSettings = userPreferencesRepository.userSettingsFlow.first()
        val settingsToRestore = backupData.settingsForRestore(previousSettings)
        val pendingImport = BackupImportJournalEntity(
            previousSettingsJson = objectMapper.writeValueAsString(previousSettings)
        )
        backupImportJournalDao.savePendingImport(pendingImport)

        var restoreResult = EpisodeRestoreResult()
        try {
            settingsToRestore?.let { settings ->
                userPreferencesRepository.restoreSettingsOrThrow(settings)
            }
            transactionRunner.run {
                val existingPodcasts = podcastDao.getAllPodcastsForExport()
                importedPodcasts.forEach { backupPodcast ->
                    val existing = podcastDao.getPodcastByUrl(backupPodcast.url)
                    val safeImageUrl = backupPodcast.imageUrl.orEmpty().takeIf { imageUrl ->
                        isAllowedRemoteResource(
                            imageUrl,
                            allowInsecureHttp = existing?.allowInsecureHttp == true,
                            allowLocalNetwork = existing?.allowLocalNetwork == true
                        )
                    }.orEmpty()
                    val stub = PodcastEntity(
                        rssUrl = backupPodcast.url,
                        title = backupPodcast.title ?: context.getString(R.string.import_fallback_title),
                        description = backupPodcast.description
                            ?: context.getString(R.string.import_fallback_description),
                        imageUrl = safeImageUrl,
                        autoDownloadEnabled = backupPodcast.autoDownloadEnabled,
                        allowInsecureHttp = false,
                        allowLocalNetwork = false,
                        sortOrder = 0,
                        lastModifiedHeader = null,
                        eTagHeader = null,
                        lastRefreshed = Date(0)
                    )
                    insertPodcastStubPreservingExisting(podcastDao, stub)
                    if (backupData.version >= 2) {
                        podcastDao.updateAutoDownloadEnabled(
                            backupPodcast.url,
                            backupPodcast.autoDownloadEnabled
                        )
                    }
                }
                podcastDao.updatePodcastSortOrders(
                    mergedPodcastSortUpdates(importedPodcasts, existingPodcasts)
                )
                restoreResult = restoreAvailableEpisodeStates(
                    podcastDao = podcastDao,
                    backupData = backupData,
                    restoreDuration = true
                )
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

        if (importedPodcasts.isNotEmpty()) {
            try {
                postImportSyncScheduler.schedule()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Could not schedule post-import feed refresh")
            }
        }
        return ImportResult(
            success = total,
            total = total,
            skippedFavorites = restoreResult.requestedFavorites - restoreResult.restoredFavorites
        )
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

internal data class EpisodeRestoreResult(
    val requestedFavorites: Int = 0,
    val restoredFavorites: Int = 0
)

private data class EpisodeBackupKey(
    val podcastUrl: String,
    val episodeGuid: String
)

internal fun List<BackupPodcast>.normalizedForImport() =
    sortedWith(compareBy<BackupPodcast> { it.sortOrder }.thenBy { it.url })

internal fun mergedPodcastSortUpdates(
    imported: List<BackupPodcast>,
    existing: List<PodcastEntity>
): List<PodcastSortUpdate> {
    val importedUrls = imported.map { it.url }
    val importedSet = importedUrls.toSet()
    val mergedUrls = importedUrls + existing.map { it.rssUrl }.filterNot(importedSet::contains)
    return mergedUrls.mapIndexed { index, rssUrl ->
        PodcastSortUpdate(rssUrl = rssUrl, sortOrder = index.toLong())
    }
}

internal suspend fun restoreAvailableEpisodeStates(
    podcastDao: PodcastDao,
    backupData: BackupData,
    restoreDuration: Boolean
): EpisodeRestoreResult {
    val stateKeys = backupData.episodeStates.mapTo(mutableSetOf()) { it.backupKey() }
    val orderedImportedFavoriteIds = mutableListOf<Long>()

    PortableEpisodeStateRestorer.restore(podcastDao, backupData.episodeStates, restoreDuration)

    val orderedV2Favorites = backupData.episodeStates
        .filter { it.isFavorite }
        .sortedWith(
            compareBy<BackupEpisodeState> { it.favoriteOrder }
                .thenBy { it.podcastUrl }
                .thenBy { it.episodeGuid }
        )
    orderedV2Favorites.forEach { state ->
        podcastDao.getEpisodeByFeedAndGuid(state.podcastUrl, state.episodeGuid)
            ?.episodeId
            ?.let(orderedImportedFavoriteIds::add)
    }

    backupData.favorites.forEach { favorite ->
        if (favorite.backupKey() in stateKeys) return@forEach
        val episode = podcastDao.getEpisodeByFeedAndGuid(favorite.podcastUrl, favorite.episodeGuid)
            ?: insertEpisodePlaceholderIfParentExists(podcastDao, favorite.toPlaceholder())
            ?: return@forEach
        podcastDao.setFavoriteStatus(
            episodeId = episode.episodeId,
            isFavorite = true,
            favoriteTimestamp = favorite.timestamp,
            favoriteAddedAt = favorite.timestamp
        )
        orderedImportedFavoriteIds += episode.episodeId
    }

    val currentFavorites = podcastDao.getFavoriteEpisodesSync()
    val importedFavoriteSet = orderedImportedFavoriteIds.toSet()
    val mergedFavoriteIds = orderedImportedFavoriteIds.distinct() +
        currentFavorites.map { it.episodeId }.filterNot(importedFavoriteSet::contains)
    if (mergedFavoriteIds.isNotEmpty()) {
        podcastDao.updateFavoriteOrder(
            mergedFavoriteIds.mapIndexed { index, episodeId ->
                FavoriteOrderUpdate(
                    episodeId = episodeId,
                    favoriteTimestamp = (mergedFavoriteIds.lastIndex - index).toLong()
                )
            }
        )
    }

    val requestedFavoriteKeys = orderedV2Favorites.mapTo(mutableSetOf()) { it.backupKey() }
    backupData.favorites
        .map { it.backupKey() }
        .filterNot(stateKeys::contains)
        .forEach(requestedFavoriteKeys::add)
    val result = EpisodeRestoreResult(
        requestedFavorites = requestedFavoriteKeys.size,
        restoredFavorites = orderedImportedFavoriteIds.distinct().size
    )
    val skipped = result.requestedFavorites - result.restoredFavorites
    if (skipped > 0) {
        Timber.w("Skipped restoring %d favorites because episodes are unavailable.", skipped)
    }
    return result
}

private object PortableEpisodeStateRestorer {
    suspend fun restore(
        podcastDao: PodcastDao,
        episodeStates: List<BackupEpisodeState>,
        restoreDuration: Boolean
    ) {
        episodeStates.forEach { state ->
            val episode = podcastDao.getEpisodeByFeedAndGuid(state.podcastUrl, state.episodeGuid)
                ?: insertEpisodePlaceholderIfParentExists(podcastDao, state.toPlaceholder())
                ?: return@forEach
            check(
                podcastDao.updatePortableEpisodeState(
                    episodeId = episode.episodeId,
                    isFavorite = state.isFavorite,
                    favoriteAddedAt = state.favoriteAddedAt,
                    isPlayed = state.isPlayed,
                    datePlayed = state.datePlayed?.let(::Date),
                    playbackPositionMs = state.playbackPositionMs,
                    duration = state.duration,
                    restoreDuration = restoreDuration
                ) == 1
            ) { "Backup episode state referenced a missing episode" }
        }
    }
}

private fun BackupEpisodeState.backupKey() = EpisodeBackupKey(podcastUrl, episodeGuid)

private fun BackupFavorite.backupKey() = EpisodeBackupKey(podcastUrl, episodeGuid)

private fun BackupEpisodeState.toPlaceholder() =
    EpisodeEntity(
        guid = episodeGuid,
        podcastRssUrl = podcastUrl,
        title = title.ifBlank { episodeGuid },
        description = description,
        pubDate = publishedAt?.let(::Date),
        link = "",
        enclosureUrl = "",
        duration = duration
    )

private fun BackupFavorite.toPlaceholder() =
    EpisodeEntity(
        guid = episodeGuid,
        podcastRssUrl = podcastUrl,
        title = episodeGuid,
        description = "",
        pubDate = null,
        link = "",
        enclosureUrl = ""
    )

private suspend fun insertEpisodePlaceholderIfParentExists(
    podcastDao: PodcastDao,
    placeholder: EpisodeEntity
): EpisodeEntity? {
    if (podcastDao.getPodcastByUrl(placeholder.podcastRssUrl) == null) return null
    podcastDao.insertEpisodesIgnore(listOf(placeholder))
    return podcastDao.getEpisodeByFeedAndGuid(placeholder.podcastRssUrl, placeholder.guid)
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
