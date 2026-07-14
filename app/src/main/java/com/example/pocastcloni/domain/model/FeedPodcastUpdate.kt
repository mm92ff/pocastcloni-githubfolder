package com.example.pocastcloni.domain.model

import java.util.Date

data class FeedPodcastUpdate(
    val rssUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val lastRefreshed: Date,
    val lastModifiedHeader: String?,
    val eTagHeader: String?
)
