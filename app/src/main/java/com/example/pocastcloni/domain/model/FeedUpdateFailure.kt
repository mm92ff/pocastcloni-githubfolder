package com.example.pocastcloni.domain.model

import com.example.pocastcloni.util.SizeLimitExceededException
import org.xmlpull.v1.XmlPullParserException
import retrofit2.HttpException
import java.io.IOException

private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_START = 500
private const val HTTP_SERVER_ERROR_END = 599

enum class FeedFailureKind {
    RETRYABLE,
    PERMANENT
}

data class FeedUpdateFailure(
    val feedUrl: String,
    val kind: FeedFailureKind
)

fun classifyFeedFailure(error: Throwable): FeedFailureKind {
    val causes = generateSequence(error) { it.cause }.toList()
    val httpError = causes.filterIsInstance<HttpException>().firstOrNull()
    return when {
        httpError != null -> {
            val statusCode = httpError.code()
            if (
                statusCode == HTTP_REQUEST_TIMEOUT ||
                statusCode == HTTP_TOO_MANY_REQUESTS ||
                statusCode in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END
            ) {
                FeedFailureKind.RETRYABLE
            } else {
                FeedFailureKind.PERMANENT
            }
        }

        causes.any { it.isPermanentFeedFailure() } -> FeedFailureKind.PERMANENT
        causes.any { it is IOException } -> FeedFailureKind.RETRYABLE
        else -> FeedFailureKind.PERMANENT
    }
}

private fun Throwable.isPermanentFeedFailure(): Boolean =
    when (this) {
        is SizeLimitExceededException,
        is XmlPullParserException,
        is IllegalArgumentException,
        is IllegalStateException -> true
        else -> false
    }
