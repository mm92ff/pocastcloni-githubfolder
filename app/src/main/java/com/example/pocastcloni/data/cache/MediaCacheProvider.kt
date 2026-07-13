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
import javax.inject.Inject
import javax.inject.Singleton

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

    @Synchronized
    fun getCacheOrNull(): Cache? {
        cache?.let { return it }

        return try {
            cacheFactory().also { cache = it }
        } catch (error: IOException) {
            Timber.e(error, "Media cache initialization failed; continuing without cache")
            null
        } catch (error: IllegalStateException) {
            Timber.e(error, "Media cache initialization failed; continuing without cache")
            null
        }
    }

    @Synchronized
    fun close() {
        val cacheToRelease = cache ?: return
        cache = null
        cacheToRelease.release()
    }
}
