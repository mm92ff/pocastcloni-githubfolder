package com.example.pocastcloni.domain.player

interface PlaybackStarter {
    suspend fun play(episodeGuid: String)
}
