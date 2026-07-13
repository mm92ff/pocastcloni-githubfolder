package com.example.pocastcloni.data.worker

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.downloadWorkName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWorkRequestTest {
    @Test
    fun `factory creates constrained identity-only unique work`() {
        val episodeId = 42L
        val downloadWork = DownloadWorkRequestFactory.create(episodeId)
        val workSpec = downloadWork.request.workSpec

        assertEquals(downloadWorkName(episodeId), downloadWork.uniqueWorkName)
        assertEquals(ExistingWorkPolicy.KEEP, downloadWork.existingWorkPolicy)
        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertTrue(workSpec.constraints.requiresStorageNotLow())
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertTrue(downloadWork.request.tags.contains(Constants.DOWNLOAD_WORKER_TAG))
        assertTrue(downloadWork.request.tags.contains(downloadWorkName(episodeId)))
        assertEquals(
            mapOf(Constants.DOWNLOAD_WORKER_EPISODE_ID to episodeId),
            workSpec.input.keyValueMap
        )
        assertFalse(workSpec.input.keyValueMap.containsKey(Constants.DOWNLOAD_WORKER_URL))
        assertFalse(workSpec.input.keyValueMap.containsKey(Constants.DOWNLOAD_WORKER_FILENAME))
    }
}
