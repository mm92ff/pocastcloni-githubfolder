package com.example.pocastcloni.domain.model

import com.example.pocastcloni.util.SizeLimitExceededException
import org.xmlpull.v1.XmlPullParserException
import retrofit2.HttpException
import java.io.IOException

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
    if (httpError != null) {
        val statusCode = httpError.code()
        return if (statusCode == 408 || statusCode == 429 || statusCode in 500..599) {
            FeedFailureKind.RETRYABLE
        } else {
            FeedFailureKind.PERMANENT
        }
    }

    if (
        causes.any {
            it is SizeLimitExceededException ||
                it is XmlPullParserException ||
                it is IllegalArgumentException ||
                it is IllegalStateException
        }
    ) {
        return FeedFailureKind.PERMANENT
    }

    return if (causes.any { it is IOException }) {
        FeedFailureKind.RETRYABLE
    } else {
        FeedFailureKind.PERMANENT
    }
}
