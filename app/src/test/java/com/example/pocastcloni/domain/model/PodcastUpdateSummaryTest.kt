package com.example.pocastcloni.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastUpdateSummaryTest {
    @Test
    fun allFailed_isTrue_onlyWhenEveryPodcastFails() {
        val summary =
            PodcastUpdateSummary(
                totalCount = 3,
                successfulCount = 0,
                failureCount = 3
            )

        assertTrue(summary.hasFailures)
        assertTrue(summary.allFailed)
        assertFalse(summary.isEmpty)
    }

    @Test
    fun partialFailure_isNotReportedAsAllFailed() {
        val summary =
            PodcastUpdateSummary(
                totalCount = 5,
                successfulCount = 3,
                failureCount = 2
            )

        assertTrue(summary.hasFailures)
        assertFalse(summary.allFailed)
        assertFalse(summary.isEmpty)
    }

    @Test
    fun emptySummary_isRecognized() {
        val summary =
            PodcastUpdateSummary(
                totalCount = 0,
                successfulCount = 0,
                failureCount = 0
            )

        assertFalse(summary.hasFailures)
        assertFalse(summary.allFailed)
        assertTrue(summary.isEmpty)
    }
}
