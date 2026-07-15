package com.example.pocastcloni.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-local, non-reentrant import lock shared by active imports and startup recovery.
 * [Mutex.withLock] preserves structured cancellation while ensuring only one journal owner exists.
 */
@Singleton
class BackupImportCoordinator
@Inject
constructor() {
    private val mutex = Mutex()

    suspend fun <T> runExclusive(block: suspend () -> T): T =
        mutex.withLock { block() }
}
