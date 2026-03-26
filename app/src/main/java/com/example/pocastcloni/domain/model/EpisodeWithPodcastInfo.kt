package com.example.pocastcloni.domain.model

data class EpisodeWithPodcastInfo(
    val episode: EpisodePresentation,
    val podcast: Podcast?
)
