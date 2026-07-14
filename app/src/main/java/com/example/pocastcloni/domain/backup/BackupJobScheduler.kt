package com.example.pocastcloni.domain.backup

import kotlinx.coroutines.flow.Flow

interface BackupJobScheduler {
    val jobs: Flow<List<BackupJob>>

    fun enqueue(
        operation: BackupJobOperation,
        path: String
    )
}

enum class BackupJobOperation {
    IMPORT,
    EXPORT
}

data class BackupJob(
    val id: String,
    val generation: Int,
    val operation: BackupJobOperation,
    val state: BackupJobState,
    val progress: BackupJobProgress? = null
)

sealed interface BackupJobState {
    data object Enqueued : BackupJobState

    data object Running : BackupJobState

    data object Blocked : BackupJobState

    data class Succeeded(
        val result: BackupJobResult? = null
    ) : BackupJobState

    data class Failed(
        val message: String
    ) : BackupJobState

    data object Cancelled : BackupJobState
}

data class BackupJobResult(
    val importedCount: Int,
    val totalCount: Int,
    val skippedFavorites: Int
)

data class BackupJobProgress(
    val completedCount: Int,
    val totalCount: Int
)
