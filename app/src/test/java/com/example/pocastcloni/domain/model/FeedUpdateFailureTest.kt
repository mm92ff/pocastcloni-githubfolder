package com.example.pocastcloni.domain.model

import com.example.pocastcloni.util.SizeLimitExceededException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class FeedUpdateFailureTest {
    @Test
    fun `408 429 server errors and IO are retryable`() {
        listOf(408, 429, 500, 503).forEach { status ->
            assertEquals(FeedFailureKind.RETRYABLE, classifyFeedFailure(httpError(status)))
        }
        assertEquals(FeedFailureKind.RETRYABLE, classifyFeedFailure(IOException("offline")))
    }

    @Test
    fun `other client parser and validation errors are permanent`() {
        assertEquals(FeedFailureKind.PERMANENT, classifyFeedFailure(httpError(404)))
        assertEquals(FeedFailureKind.PERMANENT, classifyFeedFailure(IllegalArgumentException("bad feed")))
        assertEquals(
            FeedFailureKind.PERMANENT,
            classifyFeedFailure(SizeLimitExceededException(10L))
        )
    }

    @Test
    fun `summary exposes only retryable failed URLs for targeted scheduling`() {
        val summary =
            PodcastUpdateSummary(
                totalCount = 3,
                successfulCount = 1,
                failureCount = 2,
                failures = listOf(
                    FeedUpdateFailure("https://example.com/transient", FeedFailureKind.RETRYABLE),
                    FeedUpdateFailure("https://example.com/permanent", FeedFailureKind.PERMANENT)
                )
            )

        assertEquals(setOf("https://example.com/transient"), summary.retryableFailedUrls)
        assertEquals(setOf("https://example.com/permanent"), summary.permanentFailedUrls)
    }

    private fun httpError(status: Int): HttpException =
        HttpException(Response.error<Unit>(status, "error".toResponseBody()))
}
