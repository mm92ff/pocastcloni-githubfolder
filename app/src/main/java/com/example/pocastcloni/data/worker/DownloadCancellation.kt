package com.example.pocastcloni.data.worker

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.downloadWorkName
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.util.UUID

internal suspend fun handleDownloadWorkerCancellation(
    workManager: WorkManager,
    workId: UUID,
    episodeId: Long,
    repository: PodcastRepository,
    stagingFiles: DownloadStagingFiles,
    publication: PendingDownloadPublication?
): Boolean = withContext(NonCancellable) {
    DownloadWorkStateCoordinator.withLock {
        when (resolveCancellationOwnership(workManager, workId, episodeId)) {
            CancellationOwnership.CURRENT_TERMINAL -> {
                repository.compareAndSetDownloadStatus(
                    episodeId = episodeId,
                    expectedStatuses =
                        listOf(
                            DownloadStatus.QUEUED,
                            DownloadStatus.DOWNLOADING,
                            DownloadStatus.DOWNLOADED
                        ),
                    status = DownloadStatus.NOT_DOWNLOADED,
                    path = null
                )
                runCatching { publication?.cleanup() }
                stagingFiles.delete()
                DownloadWorkStateCoordinator.clearAttemptIfOwnedBy(episodeId, workId)
                false
            }
            CancellationOwnership.CURRENT_NON_TERMINAL ->
                preserveResumableStop(episodeId, repository, stagingFiles, publication)
            CancellationOwnership.SUPERSEDED -> {
                runCatching { publication?.cleanup() }
                true
            }
            CancellationOwnership.UNKNOWN -> true
        }
    }
}

private suspend fun resolveCancellationOwnership(
    workManager: WorkManager,
    workId: UUID,
    episodeId: Long
): CancellationOwnership {
    val coordinatedOwner = DownloadWorkStateCoordinator.currentAttemptId(episodeId)
    if (coordinatedOwner != null && coordinatedOwner != workId) {
        return CancellationOwnership.SUPERSEDED
    }
    val currentWork =
        runCatching { workManager.getWorkInfoById(workId).await() }.getOrNull()
            ?: return CancellationOwnership.UNKNOWN
    val uniqueWork =
        runCatching { workManager.getWorkInfosForUniqueWork(downloadWorkName(episodeId)).await() }
            .getOrNull()
            ?: return CancellationOwnership.UNKNOWN
    if (uniqueWork.any { it.id != workId && it.state.isActiveDownloadAttempt() }) {
        return CancellationOwnership.SUPERSEDED
    }
    val matchingWork = uniqueWork.singleOrNull { it.id == workId }
        ?: return CancellationOwnership.UNKNOWN
    if (matchingWork.state != currentWork.state) return CancellationOwnership.UNKNOWN
    return if (currentWork.state == WorkInfo.State.CANCELLED) {
        CancellationOwnership.CURRENT_TERMINAL
    } else {
        CancellationOwnership.CURRENT_NON_TERMINAL
    }
}

private fun WorkInfo.State.isActiveDownloadAttempt(): Boolean =
    this == WorkInfo.State.ENQUEUED ||
        this == WorkInfo.State.BLOCKED ||
        this == WorkInfo.State.RUNNING

private enum class CancellationOwnership {
    CURRENT_TERMINAL,
    CURRENT_NON_TERMINAL,
    SUPERSEDED,
    UNKNOWN
}

private suspend fun preserveResumableStop(
    episodeId: Long,
    repository: PodcastRepository,
    stagingFiles: DownloadStagingFiles,
    publication: PendingDownloadPublication?
): Boolean {
    val transitionedToQueued =
        repository.compareAndSetDownloadStatus(
            episodeId = episodeId,
            expectedStatuses = listOf(DownloadStatus.DOWNLOADING),
            status = DownloadStatus.QUEUED,
            path = null
        )
    if (transitionedToQueued) {
        runCatching { publication?.cleanup() }
        val resumablePartialExists = publication == null && stagingFiles.partFile.isFile
        if (!resumablePartialExists) stagingFiles.delete()
        return resumablePartialExists
    }

    val currentStatus = repository.getEpisode(episodeId)?.downloadStatus
    val resourcesAreCommitted = currentStatus == DownloadStatus.DOWNLOADED
    val resumablePartialExists =
        currentStatus == DownloadStatus.QUEUED &&
            publication == null &&
            stagingFiles.partFile.isFile
    if (!resourcesAreCommitted && !resumablePartialExists) {
        runCatching { publication?.cleanup() }
        stagingFiles.delete()
    }
    return resourcesAreCommitted || resumablePartialExists
}
