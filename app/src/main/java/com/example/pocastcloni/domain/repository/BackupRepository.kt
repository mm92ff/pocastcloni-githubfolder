package com.example.pocastcloni.domain.repository

data class ImportResult(
    val success: Int,
    val total: Int,
    val skippedFavorites: Int = 0
)

@JvmInline
value class BackupLocation(val value: String)

interface BackupRepository {
    suspend fun exportBackup(
        location: BackupLocation,
        settings: UserSettings
    )

    suspend fun importBackup(location: BackupLocation): ImportResult
}
