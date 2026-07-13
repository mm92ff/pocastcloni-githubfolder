package com.example.pocastcloni.data.cache

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MediaCacheLruAndroidTest {
    @Test
    fun leastRecentlyUsedEvictorRemovesOldDataAboveSmallBudget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDirectory = context.cacheDir.resolve("media-cache-lru-test")
        cacheDirectory.deleteRecursively()
        val cache =
            SimpleCache(
                cacheDirectory,
                LeastRecentlyUsedCacheEvictor(TEST_CACHE_BUDGET_BYTES),
                StandaloneDatabaseProvider(context)
            )
        val firstUri = Uri.parse("https://cache.test/first.mp3")
        val secondUri = Uri.parse("https://cache.test/second.mp3")
        val data = ByteArray(TEST_STREAM_BYTES) { 1 }

        try {
            writeToCache(cache, firstUri, data)
            Thread.sleep(LRU_TIMESTAMP_GAP_MS)
            writeToCache(cache, secondUri, data)

            assertTrue(cache.cacheSpace <= TEST_CACHE_BUDGET_BYTES)
            assertFalse(cache.isCached(firstUri.toString(), 0, data.size.toLong()))
            assertTrue(cache.isCached(secondUri.toString(), 0, data.size.toLong()))
        } finally {
            cache.release()
            cacheDirectory.deleteRecursively()
        }
    }

    private fun writeToCache(
        cache: SimpleCache,
        uri: Uri,
        data: ByteArray
    ) {
        val cacheDataSource =
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(DataSource.Factory { ByteArrayDataSource(data) })
                .createDataSourceForDownloading()
        val dataSpec =
            DataSpec.Builder()
                .setUri(uri)
                .setLength(data.size.toLong())
                .build()

        CacheWriter(cacheDataSource, dataSpec, null, null).cache()
    }

    private companion object {
        const val TEST_CACHE_BUDGET_BYTES = 1024L
        const val TEST_STREAM_BYTES = 768
        const val LRU_TIMESTAMP_GAP_MS = 10L
    }
}
