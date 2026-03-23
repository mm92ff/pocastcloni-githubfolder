package com.example.pocastcloni.data.manager

import android.content.ContentResolver
import android.net.Uri
import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupFavorite
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastBackupHelper
@Inject
constructor(
    private val objectMapper: ObjectMapper,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun exportBackup(
        podcasts: List<BackupPodcast>,
        favorites: List<BackupFavorite>,
        settings: UserSettings,
        uri: Uri,
        contentResolver: ContentResolver
    ) {
        withContext(dispatcherProvider.io) {
            try {
                val backupData =
                    BackupData(
                        version = Constants.Backup.BACKUP_VERSION,
                        podcasts = podcasts,
                        settings = settings,
                        favorites = favorites
                    )

                val json = objectMapper.writeValueAsString(backupData)

                val outputStream =
                    contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Could not open the backup destination.")

                outputStream.use {
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(json)
                    }
                }
                Timber.d("Backup exported successfully to $uri")
            } catch (e: Exception) {
                Timber.e(e, "Failed to export backup")
                throw e
            }
        }
    }

    suspend fun importBackup(
        uri: Uri,
        contentResolver: ContentResolver
    ): BackupData {
        return withContext(dispatcherProvider.io) {
            val jsonString =
                try {
                    val inputStream =
                        contentResolver.openInputStream(uri)
                            ?: throw IOException("Could not open the backup file.")

                    inputStream.use {
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            reader.readText()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to read backup file from URI: $uri")
                    throw e
                }

            parseBackupJson(jsonString, objectMapper)
        }
    }
}

internal fun parseBackupJson(
    jsonString: String,
    objectMapper: ObjectMapper
): BackupData {
    if (jsonString.isBlank()) {
        throw IllegalArgumentException("Backup file is empty.")
    }

    try {
        return validateBackupData(
            objectMapper.readValue(jsonString, BackupData::class.java)
        )
    } catch (e: Exception) {
        Timber.w(e, "Standard import failed. Attempting legacy format fallback.")
    }

    try {
        val listType = object : TypeReference<List<String>>() {}
        val oldUrls: List<String> = objectMapper.readValue(jsonString, listType)

        val backupPodcasts =
            oldUrls.mapIndexed { index, url ->
                BackupPodcast(url = url, sortOrder = index.toLong())
            }
        Timber.i("Legacy backup restored with ${backupPodcasts.size} podcasts.")
        return validateBackupData(BackupData(podcasts = backupPodcasts))
    } catch (e: Exception) {
        Timber.e(e, "Critical: Failed to parse backup file in both formats.")
        throw IllegalArgumentException("Backup file format is invalid.", e)
    }
}

internal fun validateBackupData(backupData: BackupData): BackupData {
    if (backupData.podcasts.isEmpty() && backupData.favorites.isEmpty() && backupData.settings == null) {
        throw IllegalArgumentException("Backup file does not contain any restorable data.")
    }
    return backupData
}
