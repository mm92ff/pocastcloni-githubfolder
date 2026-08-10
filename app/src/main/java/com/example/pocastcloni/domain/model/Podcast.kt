package com.example.pocastcloni.domain.model

import java.util.Date

data class Podcast(
    val rssUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val lastRefreshed: Date = Date(),
    val autoDownloadEnabled: Boolean = false,
    val sortOrder: Long = DEFAULT_SORT_ORDER,
    val hasNewEpisodes: Boolean = false,
    val lastModifiedHeader: String? = null,
    val eTagHeader: String? = null,
    val latestEpisodeGuid: String? = null,
    val latestEpisodeDate: Date? = null,
    val isLatestEpisodePlayed: Boolean? = null,
    val allowInsecureHttp: Boolean = false,
    val allowLocalNetwork: Boolean = false,
    val coverFileName: String? = null,
    val coverRevision: Long = 0L
) {
    companion object {
        const val DEFAULT_SORT_ORDER = -1L
    }
}
