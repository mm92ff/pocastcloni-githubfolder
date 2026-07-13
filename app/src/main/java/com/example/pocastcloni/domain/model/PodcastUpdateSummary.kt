package com.example.pocastcloni.domain.model

data class PodcastUpdateSummary(
    val totalCount: Int,
    val successfulCount: Int,
    val failureCount: Int,
    val failures: List<FeedUpdateFailure> = emptyList()
) {
    val hasFailures: Boolean
        get() = failureCount > 0

    val allFailed: Boolean
        get() = totalCount > 0 && successfulCount == 0 && failureCount == totalCount

    val isEmpty: Boolean
        get() = totalCount == 0

    val retryableFailedUrls: Set<String>
        get() = failures.filter { it.kind == FeedFailureKind.RETRYABLE }.mapTo(linkedSetOf()) { it.feedUrl }

    val permanentFailedUrls: Set<String>
        get() = failures.filter { it.kind == FeedFailureKind.PERMANENT }.mapTo(linkedSetOf()) { it.feedUrl }
}
