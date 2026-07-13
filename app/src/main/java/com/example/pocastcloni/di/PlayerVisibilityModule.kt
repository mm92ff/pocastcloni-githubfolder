package com.example.pocastcloni.di

import com.example.pocastcloni.domain.player.PlaybackStarter
import com.example.pocastcloni.domain.player.PlayerVisibilityProvider
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.ui.player.ActivityAppForegroundMonitor
import com.example.pocastcloni.ui.player.AppForegroundMonitor
import com.example.pocastcloni.ui.player.MonotonicClock
import com.example.pocastcloni.ui.player.HandlerMediaDispatcherFactory
import com.example.pocastcloni.ui.player.MediaDispatcherFactory
import com.example.pocastcloni.ui.player.PlaybackTickSource
import com.example.pocastcloni.ui.player.PlaybackTicker
import com.example.pocastcloni.ui.player.PlayerVisibilityProviderImpl
import com.example.pocastcloni.ui.player.SystemMonotonicClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerVisibilityModule {
    @Binds
    abstract fun bindPlayerVisibilityProvider(impl: PlayerVisibilityProviderImpl): PlayerVisibilityProvider

    @Binds
    abstract fun bindPlaybackStarter(impl: AudioPlayerController): PlaybackStarter

    @Binds
    abstract fun bindAppForegroundMonitor(impl: ActivityAppForegroundMonitor): AppForegroundMonitor

    @Binds
    abstract fun bindMonotonicClock(impl: SystemMonotonicClock): MonotonicClock

    @Binds
    abstract fun bindMediaDispatcherFactory(impl: HandlerMediaDispatcherFactory): MediaDispatcherFactory

    @Binds
    abstract fun bindPlaybackTickSource(impl: PlaybackTicker): PlaybackTickSource
}
