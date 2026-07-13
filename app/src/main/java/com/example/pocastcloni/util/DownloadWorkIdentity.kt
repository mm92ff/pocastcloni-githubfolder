package com.example.pocastcloni.util

import androidx.work.WorkManager
import kotlinx.coroutines.guava.await

fun downloadWorkName(episodeId: Long): String {
    require(episodeId > 0L) { "episodeId must be positive" }
    return "${Constants.DOWNLOAD_WORKER_ID_UNIQUE_PREFIX}$episodeId"
}

fun legacyDownloadWorkName(guid: String): String =
    "${Constants.DOWNLOAD_WORKER_UNIQUE_PREFIX}$guid"

fun episodeIdFromDownloadWorkTag(tag: String): Long? =
    tag.takeIf { it.startsWith(Constants.DOWNLOAD_WORKER_ID_UNIQUE_PREFIX) }
        ?.removePrefix(Constants.DOWNLOAD_WORKER_ID_UNIQUE_PREFIX)
        ?.toLongOrNull()

fun episodeDownloadWorkNames(
    episodeId: Long,
    legacyGuid: String
): Set<String> = buildSet {
    add(downloadWorkName(episodeId))
    add(legacyDownloadWorkName(legacyGuid))
}

suspend fun WorkManager.cancelLegacyDownloadWork(legacyGuid: String) {
    cancelUniqueWork(legacyDownloadWorkName(legacyGuid)).result.await()
}

suspend fun WorkManager.cancelEpisodeDownloadWork(
    episodeId: Long,
    legacyGuid: String
) {
    episodeDownloadWorkNames(episodeId, legacyGuid).forEach { workName ->
        cancelUniqueWork(workName).result.await()
    }
}
