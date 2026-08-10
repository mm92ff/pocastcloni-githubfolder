package com.example.pocastcloni.data.cover

import com.example.pocastcloni.data.local.PodcastCoverStateEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastCoverRefreshPolicyTest {
    private val policy = PodcastCoverRefreshPolicy()
    private val now = 2_000_000_000L

    @Test
    fun `unchanged active cover waits until seven day boundary`() {
        val state = activeState(lastSuccessfulCheckAt = now - DAY_MS)

        assertEquals(
            PodcastCoverRefreshDecision.Wait(6L * DAY_MS),
            policy.decide(state, SOURCE_URL, activeFileValid = true, now = now)
        )
    }

    @Test
    fun `changed URL remains pending during cooldown`() {
        val state =
            activeState(lastSuccessfulCheckAt = now - DAY_MS).copy(
                pendingSourceUrl = NEW_SOURCE_URL,
                pendingFirstSeenAt = now
            )

        assertEquals(
            PodcastCoverRefreshDecision.Wait(6L * DAY_MS),
            policy.decide(state, NEW_SOURCE_URL, activeFileValid = true, now = now)
        )
    }

    @Test
    fun `missing local file bypasses cooldown`() {
        val state = activeState(lastSuccessfulCheckAt = now)

        assertEquals(
            PodcastCoverRefreshDecision.Refresh(
                SOURCE_URL,
                PodcastCoverRefreshReason.MISSING_FILE
            ),
            policy.decide(state, SOURCE_URL, activeFileValid = false, now = now)
        )
    }

    @Test
    fun `exact seven day boundary refreshes pending candidate`() {
        val state =
            activeState(lastSuccessfulCheckAt = now - 7L * DAY_MS).copy(
                pendingSourceUrl = NEW_SOURCE_URL
            )

        assertEquals(
            PodcastCoverRefreshDecision.Refresh(
                NEW_SOURCE_URL,
                PodcastCoverRefreshReason.CHANGED_URL
            ),
            policy.decide(state, NEW_SOURCE_URL, activeFileValid = true, now = now)
        )
    }

    @Test
    fun `clock rollback cannot make cooldown longer than seven days`() {
        val state = activeState(lastSuccessfulCheckAt = now + DAY_MS)

        assertEquals(
            PodcastCoverRefreshDecision.Wait(7L * DAY_MS),
            policy.decide(state, SOURCE_URL, activeFileValid = true, now = now)
        )
    }

    @Test
    fun `manual refresh bypasses cooldown and retry backoff`() {
        val state = activeState(lastSuccessfulCheckAt = now).copy(nextRetryAt = now + DAY_MS)

        assertEquals(
            PodcastCoverRefreshDecision.Refresh(SOURCE_URL, PodcastCoverRefreshReason.MANUAL),
            policy.decide(state, SOURCE_URL, activeFileValid = true, now = now, force = true)
        )
    }

    private fun activeState(lastSuccessfulCheckAt: Long) =
        PodcastCoverStateEntity(
            podcastRssUrl = RSS_URL,
            activeSourceUrl = SOURCE_URL,
            thumbnailFileName = "cover.webp",
            thumbnailRevision = 2L,
            lastSuccessfulCheckAt = lastSuccessfulCheckAt
        )

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val RSS_URL = "https://example.com/feed.xml"
        const val SOURCE_URL = "https://example.com/cover.webp"
        const val NEW_SOURCE_URL = "https://example.com/new-cover.webp"
    }
}
