package com.example.pocastcloni.domain.model

import com.example.pocastcloni.domain.model.Podcast

data class EpisodeWithPodcastInfo(
    val episode: EpisodePresentation,
    val podcast: Podcast?
)
