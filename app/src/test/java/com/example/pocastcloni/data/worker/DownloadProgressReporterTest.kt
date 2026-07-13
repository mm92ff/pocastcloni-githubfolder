package com.example.pocastcloni.data.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressReporterTest {
    @Test
    fun `reports at most once per second after at least one percentage point`() {
        var now = 0L
        val reporter = DownloadProgressReporter { now }

        now = 999L
        assertNull(reporter.report(downloadedBytes = 50L, totalBytes = 100L))
        now = 1_000L
        assertEquals(50, reporter.report(downloadedBytes = 50L, totalBytes = 100L))
        now = 2_000L
        assertNull(reporter.report(downloadedBytes = 50L, totalBytes = 100L))
        assertEquals(51, reporter.report(downloadedBytes = 51L, totalBytes = 100L))
    }

    @Test
    fun `transfer progress reserves one hundred percent for committed completion`() {
        var now = 0L
        val reporter = DownloadProgressReporter { now }

        now = 1_000L
        assertEquals(99, reporter.report(downloadedBytes = 100L, totalBytes = 100L))
        now += 1_000L
        assertNull(reporter.report(downloadedBytes = 100L, totalBytes = null))
    }
}
