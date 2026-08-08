package com.example.pocastcloni.playback.api

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only access to playback boundaries for process-level instrumentation tests. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackControllerEntryPoint {
    fun playbackStarter(): PlaybackStarter

    fun playerCommandPort(): PlayerCommandPort

    fun playerStatePort(): PlayerStatePort
}
