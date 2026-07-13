package com.example.pocastcloni.di

import com.example.pocastcloni.data.repository.InstallationStateProvider
import com.example.pocastcloni.data.repository.PackageInstallationStateProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsMigrationModule {
    @Binds
    @Singleton
    abstract fun bindInstallationStateProvider(
        implementation: PackageInstallationStateProvider
    ): InstallationStateProvider
}
