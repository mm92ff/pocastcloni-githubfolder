package com.example.pocastcloni.domain.repository

import android.net.Uri
import com.example.pocastcloni.domain.model.FeedUpdateMode

data class ImportResult(val success: Int, val total: Int)

interface BackupRepository {
    suspend fun exportFullBackup(
        uri: Uri,
        settings: UserSettings
    )

    suspend fun importFullBackup(
        uri: Uri,
        downloadLimit: Int,
        mode: FeedUpdateMode
    ): ImportResult
}
