package com.example.pocastcloni.data.cover

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes final-file publication with orphan reconciliation. */
@Singleton
class PodcastCoverFileLifecycleLock
@Inject
constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
