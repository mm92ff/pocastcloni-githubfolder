package com.example.pocastcloni.di

import com.example.pocastcloni.data.repository.StatisticsDataStore
import com.example.pocastcloni.data.repository.StreamStatisticsWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StatisticsModule {
    @Binds
    @Singleton
    abstract fun bindStreamStatisticsWriter(dataStore: StatisticsDataStore): StreamStatisticsWriter
}
