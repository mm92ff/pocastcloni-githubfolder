package com.example.pocastcloni.ui.home.add

import androidx.compose.runtime.Immutable

@Immutable
data class PodcastSearchResult(
    val feedUrl: String,
    val artworkUrl: String,
    val title: String,
    val artist: String
)