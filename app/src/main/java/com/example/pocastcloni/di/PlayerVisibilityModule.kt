package com.example.pocastcloni.di

import com.example.pocastcloni.domain.player.PlayerVisibilityProvider
import com.example.pocastcloni.ui.player.PlayerVisibilityProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerVisibilityModule {

    @Binds
    abstract fun bindPlayerVisibilityProvider(
        impl: PlayerVisibilityProviderImpl
    ): PlayerVisibilityProvider
}
