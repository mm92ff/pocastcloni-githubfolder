package com.example.pocastcloni.data.worker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal object DownloadWorkStateCoordinator {
    private val mutex = Mutex()
    private val currentAttempts = mutableMapOf<Long, UUID>()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun recordEnqueuedAttempt(
        episodeId: Long,
        workId: UUID
    ) {
        currentAttempts[episodeId] = workId
    }

    fun currentAttemptId(episodeId: Long): UUID? = currentAttempts[episodeId]

    fun clearAttempt(episodeId: Long) {
        currentAttempts.remove(episodeId)
    }

    fun clearAttemptIfOwnedBy(
        episodeId: Long,
        workId: UUID
    ) {
        currentAttempts.remove(episodeId, workId)
    }
}
