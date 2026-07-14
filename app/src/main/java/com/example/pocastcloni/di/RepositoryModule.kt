package com.example.pocastcloni.di

import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import com.example.pocastcloni.data.repository.BackupRepositoryImpl
import com.example.pocastcloni.data.repository.PodcastRepositoryImpl
import com.example.pocastcloni.data.repository.PodcastPagingSource
import com.example.pocastcloni.data.repository.RoomFeedSyncPersistence
import com.example.pocastcloni.data.repository.StatisticsRepositoryImpl
import com.example.pocastcloni.data.repository.UserPreferencesRepositoryImpl
import com.example.pocastcloni.data.sync.FeedSyncOrchestrator
import com.example.pocastcloni.data.sync.FeedUpdateOrchestrator
import com.example.pocastcloni.data.worker.AndroidAppResetGateway
import com.example.pocastcloni.data.worker.AndroidPodcastRemovalGateway
import com.example.pocastcloni.data.worker.WorkManagerEpisodeDownloadScheduler
import com.example.pocastcloni.domain.repository.AppResetGateway
import com.example.pocastcloni.domain.repository.BackupRepository
import com.example.pocastcloni.domain.repository.EpisodeDownloadScheduler
import com.example.pocastcloni.domain.repository.FeedSyncRunner
import com.example.pocastcloni.domain.repository.FeedSyncStore
import com.example.pocastcloni.domain.repository.FeedUpdateRunner
import com.example.pocastcloni.domain.repository.LibraryMaintenancePort
import com.example.pocastcloni.domain.repository.LocalNetworkApprovalPort
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.PodcastRemovalGateway
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
    abstract fun bindPodcastQuery(podcastRepositoryImpl: PodcastRepositoryImpl): PodcastQueryPort

    @Binds
    @Singleton
    abstract fun bindPodcastCommands(podcastRepositoryImpl: PodcastRepositoryImpl): PodcastCommandPort

    @Binds
    @Singleton
    abstract fun bindLibraryMaintenance(podcastRepositoryImpl: PodcastRepositoryImpl): LibraryMaintenancePort

    @Binds
    @Singleton
    abstract fun bindPodcastPagingSource(podcastRepositoryImpl: PodcastRepositoryImpl): PodcastPagingSource

    @Binds
    @Singleton
    abstract fun bindFeedSyncStore(implementation: RoomFeedSyncPersistence): FeedSyncStore

    @Binds
    @Singleton
    abstract fun bindFeedSyncRunner(implementation: FeedSyncOrchestrator): FeedSyncRunner

    @Binds
    @Singleton
    abstract fun bindFeedUpdateRunner(implementation: FeedUpdateOrchestrator): FeedUpdateRunner

    @Binds
    @Singleton
    abstract fun bindEpisodeDownloadScheduler(
        implementation: WorkManagerEpisodeDownloadScheduler
    ): EpisodeDownloadScheduler

    @Binds
    @Singleton
    abstract fun bindPodcastRemovalGateway(
        implementation: AndroidPodcastRemovalGateway
    ): PodcastRemovalGateway

    @Binds
    @Singleton
    abstract fun bindAppResetGateway(implementation: AndroidAppResetGateway): AppResetGateway

    @Binds
    @Singleton
    abstract fun bindLocalNetworkApproval(
        implementation: LocalNetworkAccessRegistry
    ): LocalNetworkApprovalPort

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
