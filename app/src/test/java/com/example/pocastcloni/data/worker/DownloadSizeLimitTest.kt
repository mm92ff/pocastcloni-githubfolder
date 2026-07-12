package com.example.pocastcloni.data.worker

import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.test.runTest
import java.io.File

class DownloadSizeLimitTest {
    @Test
    fun `accepts exact limit and rejects one byte more`() {
        assertFalse(exceedsDownloadLimit(Constants.SecurityLimits.MAX_DOWNLOAD_BYTES))
        assertTrue(exceedsDownloadLimit(Constants.SecurityLimits.MAX_DOWNLOAD_BYTES + 1))
    }

    @Test
    fun `unknown content length is checked during streaming instead`() {
        assertFalse(exceedsDownloadLimit(-1))
    }

    @Test
    fun `chunk is rejected before it would cross the limit`() {
        ensureDownloadChunkWithinLimit(Constants.SecurityLimits.MAX_DOWNLOAD_BYTES - 1, 1)
        assertThrows(DownloadSizeLimitException::class.java) {
            ensureDownloadChunkWithinLimit(Constants.SecurityLimits.MAX_DOWNLOAD_BYTES - 1, 2)
        }
    }

    @Test
    fun `size limit failure is permanent and is not retried`() {
        assertFalse(
            shouldRetryDownloadFailure(
                DownloadSizeLimitException(),
                runAttemptCount = 0,
                maxRetryAttempts = 3
            )
        )
    }

    @Test
    fun `private partial file is deleted after permanent limit failure`() = runTest {
        val file = File.createTempFile("podcast-limit", ".part")
        file.writeBytes(byteArrayOf(1, 2, 3))

        try {
            runWithCleanupOnFailure(cleanup = { file.delete() }) {
                throw DownloadSizeLimitException()
            }
        } catch (_: DownloadSizeLimitException) {
            // Expected.
        }
        assertFalse(file.exists())
    }

    @Test
    fun `media store cleanup callback runs after permanent limit failure`() = runTest {
        var deleted = false

        try {
            runWithCleanupOnFailure(cleanup = { deleted = true }) {
                throw DownloadSizeLimitException()
            }
        } catch (_: DownloadSizeLimitException) {
            // Expected.
        }
        assertTrue(deleted)
    }
}
