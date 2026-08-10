package com.example.pocastcloni.data.cover

import com.example.pocastcloni.data.local.PodcastCoverStateEntity
import javax.inject.Inject

enum class PodcastCoverRefreshReason {
    FIRST_LOAD,
    MISSING_FILE,
    CHANGED_URL,
    PERIODIC_VALIDATION,
    MANUAL
}

sealed interface PodcastCoverRefreshDecision {
    data class Refresh(
        val sourceUrl: String,
        val reason: PodcastCoverRefreshReason
    ) : PodcastCoverRefreshDecision

    data class Wait(val delayMs: Long) : PodcastCoverRefreshDecision

    data object NoSource : PodcastCoverRefreshDecision
}

/** Pure policy separating feed observation from durable cover publication. */
class PodcastCoverRefreshPolicy
@Inject
constructor() {
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun decide(
        state: PodcastCoverStateEntity,
        latestFeedUrl: String,
        activeFileValid: Boolean,
        now: Long,
        force: Boolean = false
    ): PodcastCoverRefreshDecision {
        val active = state.activeSourceUrl.normalized()
        val pending = state.pendingSourceUrl.normalized()
        val latest = latestFeedUrl.normalized()
        val candidate = pending ?: active ?: latest ?: return PodcastCoverRefreshDecision.NoSource

        if (!force) {
            state.nextRetryAt?.let { retryAt ->
                if (now < retryAt) return PodcastCoverRefreshDecision.Wait(retryAt - now)
            }
        }
        if (force) {
            return PodcastCoverRefreshDecision.Refresh(candidate, PodcastCoverRefreshReason.MANUAL)
        }
        if (!activeFileValid) {
            val reason =
                if (state.thumbnailFileName == null && active == null) {
                    PodcastCoverRefreshReason.FIRST_LOAD
                } else {
                    PodcastCoverRefreshReason.MISSING_FILE
                }
            return PodcastCoverRefreshDecision.Refresh(candidate, reason)
        }

        val lastSuccess = state.lastSuccessfulCheckAt
            ?: return PodcastCoverRefreshDecision.Refresh(
                candidate,
                if (pending != null && pending != active) {
                    PodcastCoverRefreshReason.CHANGED_URL
                } else {
                    PodcastCoverRefreshReason.PERIODIC_VALIDATION
                }
            )
        val elapsed = (now - lastSuccess).coerceAtLeast(0L)
        if (elapsed < REFRESH_INTERVAL_MS) {
            return PodcastCoverRefreshDecision.Wait(REFRESH_INTERVAL_MS - elapsed)
        }
        return PodcastCoverRefreshDecision.Refresh(
            candidate,
            if (pending != null && pending != active) {
                PodcastCoverRefreshReason.CHANGED_URL
            } else {
                PodcastCoverRefreshReason.PERIODIC_VALIDATION
            }
        )
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    companion object {
        const val REFRESH_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

fun interface PodcastCoverClock {
    fun now(): Long
}

class SystemPodcastCoverClock
@Inject
constructor() : PodcastCoverClock {
    override fun now(): Long = System.currentTimeMillis()
}
