package com.example.pocastcloni.domain.player

interface PlaybackStarter {
    suspend fun play(episodeId: Long)
}
