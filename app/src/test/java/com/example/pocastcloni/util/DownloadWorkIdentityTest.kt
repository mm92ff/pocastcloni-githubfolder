package com.example.pocastcloni.util

import androidx.work.Operation
import androidx.work.WorkManager
import com.google.common.util.concurrent.Futures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DownloadWorkIdentityTest {
    @Test
    fun duplicateFeedGuidsStillProduceDistinctWorkNames() {
        val first = downloadWorkName(101L)
        val second = downloadWorkName(202L)

        assertNotEquals(first, second)
        assertEquals("download_work_id_101", first)
        assertEquals("download_work_id_202", second)
    }

    @Test
    fun cancellationCoversCurrentAndLegacyWorkNames() = runTest {
        val workManager = mockk<WorkManager>()
        val operation = mockk<Operation>()
        every { operation.result } returns Futures.immediateFuture(Operation.SUCCESS)
        every { workManager.cancelUniqueWork(any()) } returns operation

        workManager.cancelEpisodeDownloadWork(101L, "legacy-guid")

        verify(exactly = 1) { workManager.cancelUniqueWork(downloadWorkName(101L)) }
        verify(exactly = 1) { workManager.cancelUniqueWork("download_work_legacy-guid") }
        assertEquals(2, episodeDownloadWorkNames(101L, "legacy-guid").size)
    }

    @Test
    fun startingTransitionCancelsOnlyLegacyWorkName() = runTest {
        val workManager = mockk<WorkManager>()
        val operation = mockk<Operation>()
        every { operation.result } returns Futures.immediateFuture(Operation.SUCCESS)
        every { workManager.cancelUniqueWork(any()) } returns operation

        workManager.cancelLegacyDownloadWork("legacy-guid")

        verify(exactly = 1) { workManager.cancelUniqueWork("download_work_legacy-guid") }
        verify(exactly = 0) { workManager.cancelUniqueWork(downloadWorkName(101L)) }
    }

    @Test
    fun numericAndBlankLegacyGuidsCannotCollideWithCurrentWorkNames() {
        assertNotEquals(downloadWorkName(202L), legacyDownloadWorkName("202"))
        assertEquals("download_work_202", legacyDownloadWorkName("202"))
        assertEquals("download_work_", legacyDownloadWorkName(""))
        assertEquals(202L, episodeIdFromDownloadWorkTag(downloadWorkName(202L)))
        assertEquals(null, episodeIdFromDownloadWorkTag(legacyDownloadWorkName("202")))
    }
}
