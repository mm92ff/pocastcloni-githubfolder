package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeDownloadStateRow
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.worker.DownloadPublicationGate
import com.example.pocastcloni.data.worker.DownloadWorkStateCoordinator

internal fun selectEpisodesToPrune(
    episodes: List<EpisodeEntity>,
    keepCount: Int
): List<EpisodeEntity> {
    if (keepCount <= 0 || episodes.size <= keepCount) return emptyList()

    return episodes
        .sortedByDescending { it.pubDate?.time ?: 0L }
        .drop(keepCount)
        .filter(::isSafeToPrune)
}

internal fun isSafeToPrune(episode: EpisodeEntity): Boolean {
    return episode.isPlayed &&
        !episode.isFavorite &&
        episode.downloadStatus == DownloadStatus.NOT_DOWNLOADED
}

internal fun shouldResetDownloadState(
    status: DownloadStatus,
    downloadPath: String?,
    fileIsReadable: (String) -> Boolean
): Boolean {
    return when (status) {
        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING
        -> true

        DownloadStatus.DOWNLOADED -> downloadPath.isNullOrBlank() || !fileIsReadable(downloadPath)

        DownloadStatus.NOT_DOWNLOADED,
        DownloadStatus.FAILED
        -> false
    }
}

internal suspend fun reconcileTransientDownloadState(
    initiallyActive: Boolean,
    isWorkActive: suspend () -> Boolean,
    compareAndReset: suspend () -> Boolean,
    deleteStaging: () -> Unit
): Int = DownloadWorkStateCoordinator.withLock {
    if (initiallyActive || isWorkActive()) return@withLock 0
    val reset = compareAndReset()
    if (reset) deleteStaging()
    if (reset) 1 else 0
}

/**
 * Validates downloaded paths only after any in-process publication transition has completed.
 *
 * The snapshot, readability check, and exact-row reset share [DownloadPublicationGate] with the
 * worker's database-commit-to-visibility phase. A legacy target therefore cannot be observed in
 * the intentional interval after its database path is committed but before its final move.
 */
internal suspend fun reconcileDownloadedReadability(
    loadDownloadedRows: suspend () -> List<EpisodeDownloadStateRow>,
    fileIsReadable: (String) -> Boolean,
    compareAndReset: suspend (EpisodeDownloadStateRow) -> Boolean
): Int = DownloadPublicationGate.withLock {
    var correctedEntries = 0
    loadDownloadedRows()
        .filter { row ->
            shouldResetDownloadState(
                status = row.downloadStatus,
                downloadPath = row.downloadPath,
                fileIsReadable = fileIsReadable
            )
        }
        .forEach { row ->
            if (compareAndReset(row)) correctedEntries++
        }
    correctedEntries
}
