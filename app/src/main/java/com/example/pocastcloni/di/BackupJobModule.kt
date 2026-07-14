package com.example.pocastcloni.di

import com.example.pocastcloni.data.worker.WorkManagerBackupJobScheduler
import com.example.pocastcloni.domain.backup.BackupJobScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupJobModule {
    @Binds
    @Singleton
    abstract fun bindBackupJobScheduler(
        implementation: WorkManagerBackupJobScheduler
    ): BackupJobScheduler
}
