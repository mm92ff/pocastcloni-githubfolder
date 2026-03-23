package com.example.pocastcloni.di

import android.content.Context
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.PodcastDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun providePodcastDao(database: AppDatabase): PodcastDao {
        return database.podcastDao()
    }
}
