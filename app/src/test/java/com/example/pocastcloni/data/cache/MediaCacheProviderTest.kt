package com.example.pocastcloni.data.cache

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import com.example.pocastcloni.util.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(UnstableApi::class)
class MediaCacheProviderTest {
    @Test
    fun `cache constants define the complete managed set and fixed budget`() {
        assertEquals(
            setOf(
                "image_cache",
                "http_cache",
                "local_http_cache",
                "approved_media_http_cache",
                "media_cache"
            ),
            Constants.Cache.MANAGED_CACHE_DIRS
        )
        assertEquals(256L * 1024L * 1024L, Constants.Cache.MEDIA_CACHE_MAX_BYTES)
        assertTrue(Constants.Cache.MEDIA_CACHE_MAX_BYTES > 0L)
        assertTrue(Constants.Cache.MEDIA_CACHE_MAX_BYTES <= MAX_EXPECTED_MEDIA_CACHE_BYTES)
    }

    @Test
    fun `initialization failure returns null and a later call can retry`() {
        val cache = mockk<Cache>(relaxed = true)
        val attempts = AtomicInteger()
        val provider =
            MediaCacheProvider {
                if (attempts.getAndIncrement() == 0) throw IOException("cache unavailable")
                cache
            }

        assertNull(provider.getCacheOrNull())
        assertSame(cache, provider.getCacheOrNull())
        assertEquals(2, attempts.get())
    }

    @Test
    fun `repeated calls reuse one cache instance`() {
        val cache = mockk<Cache>(relaxed = true)
        val factoryCalls = AtomicInteger()
        val provider = MediaCacheProvider {
            factoryCalls.incrementAndGet()
            cache
        }

        assertSame(cache, provider.getCacheOrNull())
        assertSame(cache, provider.getCacheOrNull())
        assertEquals(1, factoryCalls.get())
    }

    @Test
    fun `concurrent callers initialize one cache instance`() {
        val cache = mockk<Cache>(relaxed = true)
        val factoryCalls = AtomicInteger()
        val provider = MediaCacheProvider {
            factoryCalls.incrementAndGet()
            cache
        }
        val ready = CountDownLatch(THREAD_COUNT)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(THREAD_COUNT)

        try {
            val futures =
                List(THREAD_COUNT) {
                    executor.submit<Cache?> {
                        ready.countDown()
                        check(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        provider.getCacheOrNull()
                    }
                }

            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(
                futures.all { future ->
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) === cache
                }
            )
            assertEquals(1, factoryCalls.get())
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `overlapping service leases delay reset close until the final release`() {
        val cache = mockk<Cache>(relaxed = true)
        val provider = MediaCacheProvider { cache }
        val firstLease = provider.acquire()
        val secondLease = provider.acquire()
        val reset = provider.beginReset()

        try {
            assertSame(cache, firstLease.cache)
            assertSame(cache, secondLease.cache)
            assertNull(provider.acquire().cache)

            firstLease.close()
            assertFalse(provider.awaitNoActiveLeases(0L, TimeUnit.MILLISECONDS))
            secondLease.close()
            assertTrue(provider.awaitNoActiveLeases(TIMEOUT_SECONDS, TimeUnit.SECONDS))

            provider.closeForReset()
        } finally {
            reset.close()
        }

        verify(exactly = 1) { cache.release() }
    }

    @Test
    fun `reset gate serves cacheless leases and allows a fresh generation afterwards`() {
        val firstCache = mockk<Cache>(relaxed = true)
        val secondCache = mockk<Cache>(relaxed = true)
        val caches = ArrayDeque(listOf(firstCache, secondCache))
        val provider = MediaCacheProvider { caches.removeFirst() }

        assertSame(firstCache, provider.getCacheOrNull())
        val reset = provider.beginReset()
        try {
            assertNull(provider.getCacheOrNull())
            assertNull(provider.acquire().cache)
            provider.closeForReset()
        } finally {
            reset.close()
        }

        val nextLease = provider.acquire()
        assertSame(secondCache, nextLease.cache)
        nextLease.close()

        verify(exactly = 1) { firstCache.release() }
        verify(exactly = 0) { secondCache.release() }
    }

    @Test
    fun `failed reset close clears the reference before a later retry`() {
        val firstCache = mockk<Cache>()
        val secondCache = mockk<Cache>(relaxed = true)
        val caches = ArrayDeque(listOf(firstCache, secondCache))
        val provider = MediaCacheProvider { caches.removeFirst() }
        every { firstCache.release() } throws IOException("release failed")

        assertSame(firstCache, provider.getCacheOrNull())
        val reset = provider.beginReset()
        try {
            assertThrows(IOException::class.java) { provider.closeForReset() }
        } finally {
            reset.close()
        }
        assertSame(secondCache, provider.getCacheOrNull())

        verify(exactly = 1) { firstCache.release() }
    }

    private companion object {
        const val THREAD_COUNT = 8
        const val TIMEOUT_SECONDS = 5L
        const val MAX_EXPECTED_MEDIA_CACHE_BYTES = 256L * 1024L * 1024L
    }
}
