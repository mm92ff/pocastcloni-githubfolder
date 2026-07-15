package com.example.pocastcloni.di

import com.example.pocastcloni.playback.api.PlaybackStarter
import com.example.pocastcloni.playback.api.PlaybackResetPort
import com.example.pocastcloni.playback.api.PlayerCommandPort
import com.example.pocastcloni.playback.api.PlayerStatePort
import com.example.pocastcloni.playback.api.PlayerVisibilityProvider
import com.example.pocastcloni.playback.infrastructure.ActivityAppForegroundMonitor
import com.example.pocastcloni.playback.infrastructure.AppForegroundMonitor
import com.example.pocastcloni.playback.infrastructure.AudioPlayerController
import com.example.pocastcloni.playback.infrastructure.HandlerMediaDispatcherFactory
import com.example.pocastcloni.playback.infrastructure.MediaDispatcherFactory
import com.example.pocastcloni.playback.infrastructure.MonotonicClock
import com.example.pocastcloni.playback.infrastructure.PlaybackTickSource
import com.example.pocastcloni.playback.infrastructure.PlaybackTicker
import com.example.pocastcloni.playback.infrastructure.PlaybackResetCoordinator
import com.example.pocastcloni.playback.infrastructure.SystemMonotonicClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun bindPlayerVisibilityProvider(impl: AudioPlayerController): PlayerVisibilityProvider

    @Binds
    @Singleton
    abstract fun bindPlaybackStarter(impl: AudioPlayerController): PlaybackStarter

    @Binds
    @Singleton
    abstract fun bindPlaybackResetPort(impl: PlaybackResetCoordinator): PlaybackResetPort

    @Binds
    @Singleton
    abstract fun bindPlayerCommandPort(impl: AudioPlayerController): PlayerCommandPort

    @Binds
    @Singleton
    abstract fun bindPlayerStatePort(impl: AudioPlayerController): PlayerStatePort

    @Binds
    abstract fun bindAppForegroundMonitor(impl: ActivityAppForegroundMonitor): AppForegroundMonitor

    @Binds
    abstract fun bindMonotonicClock(impl: SystemMonotonicClock): MonotonicClock

    @Binds
    abstract fun bindMediaDispatcherFactory(impl: HandlerMediaDispatcherFactory): MediaDispatcherFactory

    @Binds
    abstract fun bindPlaybackTickSource(impl: PlaybackTicker): PlaybackTickSource
}
