package com.example.pocastcloni.di

import android.content.Context
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.pocastcloni.BuildConfig
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.home.common.PodcastCoverEventListenerFactory
import com.example.pocastcloni.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("ImageMediaClient") okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            // Podcast cover URLs are the cache version; unchanged URLs should remain offline-first
            // even when their origin omits HTTP freshness headers.
            .respectCacheHeaders(false)
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(Constants.Cache.IMAGE_CACHE_DIR))
                    .maxSizePercent(0.02)
                    .build()
            }
            .eventListenerFactory(PodcastCoverEventListenerFactory(enabled = BuildConfig.DEBUG))
            // Shows a placeholder if loading fails
            .error(R.drawable.ic_launcher_foreground)
            .fallback(R.drawable.ic_launcher_foreground)
            .build()
    }
}
