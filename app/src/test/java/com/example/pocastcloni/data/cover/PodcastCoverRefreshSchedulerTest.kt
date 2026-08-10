package com.example.pocastcloni.data.cover

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.PodcastCoverStateDao
import com.example.pocastcloni.data.local.PodcastCoverStateEntity
import com.example.pocastcloni.data.local.PodcastDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastCoverRefreshSchedulerTest {
    @Test
    fun `normal work is private constrained deduplicated and delayed until cooldown expires`() = runTest {
        val now = 2_000_000_000L
        val requestSlot = slot<OneTimeWorkRequest>()
        val nameSlot = slot<String>()
        val policySlot = slot<ExistingWorkPolicy>()
        val workManager = mockk<WorkManager>()
        every {
            workManager.enqueueUniqueWork(capture(nameSlot), capture(policySlot), capture(requestSlot))
        } returns mockk<Operation>(relaxed = true)
        val coverDao = mockk<PodcastCoverStateDao>()
        coEvery { coverDao.getState(FEED_URL) } returns
            PodcastCoverStateEntity(
                podcastRssUrl = FEED_URL,
                activeSourceUrl = COVER_URL,
                thumbnailFileName = "cover.webp",
                lastSuccessfulCheckAt = now
            )
        val clock = mockk<SystemPodcastCoverClock>()
        every { clock.now() } returns now
        val scheduler =
            PodcastCoverRefreshScheduler(
                workManager = workManager,
                podcastDao = mockk<PodcastDao>(),
                coverStateDao = coverDao,
                clock = clock
            )

        scheduler.enqueue(FEED_URL)

        assertEquals(ExistingWorkPolicy.KEEP, policySlot.captured)
        assertFalse(nameSlot.captured.contains(FEED_URL))
        assertTrue(nameSlot.captured.startsWith("podcast_cover_"))
        assertEquals(PodcastCoverRefreshPolicy.REFRESH_INTERVAL_MS, requestSlot.captured.workSpec.initialDelay)
        assertEquals(NetworkType.CONNECTED, requestSlot.captured.workSpec.constraints.requiredNetworkType)
        assertTrue(requestSlot.captured.workSpec.constraints.requiresBatteryNotLow())

        scheduler.enqueue(FEED_URL, replaceExisting = true)
        assertEquals(ExistingWorkPolicy.REPLACE, policySlot.captured)
    }

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
        const val COVER_URL = "https://example.com/cover.png"
    }
}
