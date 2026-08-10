package com.example.pocastcloni.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.di.DefaultDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PodcastCoverThumbnailStoreAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = PodcastCoverThumbnailStore(context, DefaultDispatcherProvider())

    @After
    fun tearDown() = runBlocking {
        store.deletePodcastFiles(TEST_RSS_URL)
    }

    @Test
    fun publishBoundsThumbnailAndSurvivesOrdinaryCacheCleanup() = runBlocking {
        val source = createPng(width = 1_200, height = 600)
        val published = store.publish(TEST_RSS_URL, ByteArrayInputStream(source))
        val fileBeforeCleanup = store.validFile(published.fileName)

        assertNotNull(fileBeforeCleanup)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(fileBeforeCleanup?.absolutePath, bounds)
        assertTrue(bounds.outWidth <= PodcastCoverThumbnailStore.MAX_THUMBNAIL_EDGE)
        assertTrue(bounds.outHeight <= PodcastCoverThumbnailStore.MAX_THUMBNAIL_EDGE)

        context.cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
        assertNotNull(store.validFile(published.fileName))
    }

    @Test(expected = java.io.IOException::class)
    fun invalidImageNeverPublishes() {
        runBlocking {
            store.publish(TEST_RSS_URL, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))
        }
    }

    private fun createPng(
        width: Int,
        height: Int
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private companion object {
        const val TEST_RSS_URL = "https://instrumentation.example/cover-store-feed.xml"
    }
}
