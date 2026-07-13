package com.example.pocastcloni.di

import com.example.pocastcloni.data.repository.BackupRepositoryImpl
import com.example.pocastcloni.data.repository.PodcastRepositoryImpl
import com.example.pocastcloni.data.repository.RoomFeedSyncPersistence
import com.example.pocastcloni.data.repository.StatisticsRepositoryImpl
import com.example.pocastcloni.data.repository.UserPreferencesRepositoryImpl
import com.example.pocastcloni.domain.repository.BackupRepository
import com.example.pocastcloni.domain.repository.FeedSyncPersistence
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.StatisticsRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPodcastRepository(podcastRepositoryImpl: PodcastRepositoryImpl): PodcastRepository

    @Binds
    @Singleton
    abstract fun bindFeedSyncPersistence(implementation: RoomFeedSyncPersistence): FeedSyncPersistence

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(userPreferencesRepositoryImpl: UserPreferencesRepositoryImpl): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindStatisticsRepository(statisticsRepositoryImpl: StatisticsRepositoryImpl): StatisticsRepository

    // Binding for the BackupRepository
    @Binds
    @Singleton
    abstract fun bindBackupRepository(backupRepositoryImpl: BackupRepositoryImpl): BackupRepository
}
