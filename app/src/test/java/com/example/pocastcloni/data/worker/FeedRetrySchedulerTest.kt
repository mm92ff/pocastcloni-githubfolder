package com.example.pocastcloni.data.worker

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class FeedRetrySchedulerTest {
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val scheduler = FeedRetryScheduler(workManager)

    @Test
    fun `one unique constrained retry is scheduled per feed hash`() {
        val workName = slot<String>()
        val request = slot<OneTimeWorkRequest>()
        val feedUrl = "https://private.example/feed.xml?token=secret"

        scheduler.schedule(listOf(feedUrl, feedUrl))

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                capture(workName),
                ExistingWorkPolicy.KEEP,
                capture(request)
            )
        }
        assertEquals(FeedRetryScheduler.uniqueWorkName(feedUrl), workName.captured)
        assertFalse(workName.captured.contains("private.example"))
        assertEquals(feedUrl, request.captured.workSpec.input.getString(FeedUpdateWorker.KEY_FEED_URL))
        assertEquals(NetworkType.CONNECTED, request.captured.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.captured.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30L), request.captured.workSpec.backoffDelayDuration)
        assertTrue(request.captured.tags.contains(FeedRetryScheduler.WORK_TAG))
    }
}
