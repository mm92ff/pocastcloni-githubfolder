package com.example.pocastcloni.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the process-wide media cache shared by all playback-service generations.
 *
 * Playback services hold a [Lease] for their complete lifetime. Whole-app maintenance first opens
 * a [ResetHandle], which prevents new cache-backed services, and may close the cache only after all
 * existing leases have ended.
 */
@OptIn(UnstableApi::class)
@Singleton
class MediaCacheProvider
internal constructor(
    private val cacheFactory: () -> Cache
) {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        cacheFactory = {
            SimpleCache(
                File(context.cacheDir, Constants.Cache.MEDIA_CACHE_DIR),
                LeastRecentlyUsedCacheEvictor(Constants.Cache.MEDIA_CACHE_MAX_BYTES),
                StandaloneDatabaseProvider(context)
            )
        }
    )

    private var cache: Cache? = null
    private var activeLeases = 0
    private var resetInProgress = false
    private var idleSignal = CountDownLatch(0)

    @Synchronized
    fun getCacheOrNull(): Cache? =
        if (resetInProgress) null else cache ?: createCacheOrNull()

    private fun createCacheOrNull(): Cache? =
        try {
            cacheFactory().also { cache = it }
        } catch (error: IOException) {
            Timber.e(error, "Media cache initialization failed; continuing without cache")
            null
        } catch (error: IllegalStateException) {
            Timber.e(error, "Media cache initialization failed; continuing without cache")
            null
        }

    @Synchronized
    fun acquire(): Lease {
        val acquiredCache = getCacheOrNull() ?: return Lease(cache = null, releaseAction = {})
        if (activeLeases == 0) idleSignal = CountDownLatch(1)
        activeLeases += 1
        return Lease(acquiredCache) { releaseLease(acquiredCache) }
    }

    @Synchronized
    fun beginReset(): ResetHandle {
        check(!resetInProgress) { "Media cache reset is already active" }
        resetInProgress = true
        return ResetHandle(::finishReset)
    }

    fun awaitNoActiveLeases(
        timeout: Long,
        unit: TimeUnit
    ): Boolean {
        val signal = synchronized(this) { idleSignal }
        return signal.await(timeout, unit)
    }

    @Synchronized
    fun closeForReset() {
        check(resetInProgress) { "Media cache can only be closed during a reset" }
        check(activeLeases == 0) { "Media cache is still used by $activeLeases playback service(s)" }
        val cacheToRelease = cache ?: return
        cache = null
        cacheToRelease.release()
    }

    @Synchronized
    private fun releaseLease(acquiredCache: Cache) {
        check(cache === acquiredCache) { "Playback lease refers to a stale media cache" }
        check(activeLeases > 0) { "Media cache lease count is already zero" }
        activeLeases -= 1
        if (activeLeases == 0) idleSignal.countDown()
    }

    @Synchronized
    private fun finishReset() {
        resetInProgress = false
    }

    class Lease
    internal constructor(
        val cache: Cache?,
        private val releaseAction: () -> Unit
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) releaseAction()
        }
    }

    class ResetHandle
    internal constructor(
        private val finishAction: () -> Unit
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) finishAction()
        }
    }
}
