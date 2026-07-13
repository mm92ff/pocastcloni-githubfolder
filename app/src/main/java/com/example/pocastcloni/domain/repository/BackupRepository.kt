package com.example.pocastcloni.domain.repository

import android.net.Uri

data class ImportResult(
    val success: Int,
    val total: Int,
    val skippedFavorites: Int = 0
)

interface BackupRepository {
    suspend fun exportFullBackup(
        uri: Uri,
        settings: UserSettings
    )

    suspend fun importFullBackup(uri: Uri): ImportResult
}
