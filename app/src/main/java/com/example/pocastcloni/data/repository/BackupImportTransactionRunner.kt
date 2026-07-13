package com.example.pocastcloni.data.repository

import androidx.room.withTransaction
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.BackupRoomSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImportTransactionRunner @Inject constructor(
    private val database: AppDatabase
) {
    suspend fun run(block: suspend () -> Unit) {
        runForResult(block)
    }

    suspend fun <T> runForResult(block: suspend () -> T): T =
        database.withTransaction { block() }

    suspend fun captureBackupSnapshot(): BackupRoomSnapshot =
        runForResult {
            val podcastDao = database.podcastDao()
            BackupRoomSnapshot(
                podcasts = podcastDao.getAllPodcastsForExport(),
                episodeStates = podcastDao.getPortableEpisodeStatesForExport()
            )
        }
}
