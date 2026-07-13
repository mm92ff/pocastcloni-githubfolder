package com.example.pocastcloni.service

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import io.mockk.mockk
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(UnstableApi::class)
class PodcastPlaybackServiceCacheTest {
    @Test
    fun `null cache leaves the upstream factory unchanged`() {
        val upstreamFactory = mockk<DataSource.Factory>()

        assertSame(upstreamFactory, playbackDataSourceFactory(upstreamFactory, null))
    }

    @Test
    fun `available cache wraps the upstream factory`() {
        val upstreamFactory = mockk<DataSource.Factory>()
        val cache = mockk<Cache>()

        assertNotSame(upstreamFactory, playbackDataSourceFactory(upstreamFactory, cache))
    }
}
