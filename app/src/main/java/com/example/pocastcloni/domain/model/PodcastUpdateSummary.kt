package com.example.pocastcloni.domain.model

data class PodcastUpdateSummary(
    val totalCount: Int,
    val successfulCount: Int,
    val failureCount: Int
) {
    val hasFailures: Boolean
        get() = failureCount > 0

    val allFailed: Boolean
        get() = totalCount > 0 && successfulCount == 0 && failureCount == totalCount

    val isEmpty: Boolean
        get() = totalCount == 0
}
