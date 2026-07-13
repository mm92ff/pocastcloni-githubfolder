package com.example.pocastcloni.data.manager

import android.content.ContentResolver
import android.net.Uri
import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupFavorite
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.data.local.BackupSettingsFieldPresence
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.parseNetworkUrl
import com.example.pocastcloni.util.SizeLimitedInputStream
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.core.JsonToken
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
                validateBackupSettings(settings)
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
                        BufferedReader(
                            InputStreamReader(
                                SizeLimitedInputStream(
                                    inputStream,
                                    Constants.SecurityLimits.MAX_BACKUP_BYTES
                                )
                            )
                        ).use { reader ->
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
    prevalidateBackupJson(jsonString, objectMapper)

    try {
        val backupData = objectMapper.readValue(jsonString, BackupData::class.java)
        return validateBackupData(
            backupData.copy(
                settingsFieldPresence = readSettingsFieldPresence(jsonString, objectMapper)
            )
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

private fun readSettingsFieldPresence(
    jsonString: String,
    objectMapper: ObjectMapper
): BackupSettingsFieldPresence? {
    val settingsNode = objectMapper.readTree(jsonString).get(Constants.Backup.KEY_SETTINGS)
    if (settingsNode == null || !settingsNode.isObject) return null

    val fields = settingsNode.fieldNames().asSequence().toSet()
    val indicatorFields = settingsNode.get("indicator")
        ?.takeIf { it.isObject }
        ?.fieldNames()
        ?.asSequence()
        ?.map(::canonicalIndicatorFieldName)
        ?.toSet()
        .orEmpty()
    return BackupSettingsFieldPresence(fields, indicatorFields)
}

private fun canonicalIndicatorFieldName(field: String): String = when (field) {
    "xoffset" -> "xOffset"
    "yoffset" -> "yOffset"
    else -> field
}

internal fun prevalidateBackupJson(
    jsonString: String,
    objectMapper: ObjectMapper
) {
    val arrays = mutableListOf<BackupArrayFrame>()
    var depth = 0
    objectMapper.factory.createParser(jsonString).use { parser ->
        while (parser.nextToken() != null) {
            val token = parser.currentToken
            arrays.lastOrNull()?.takeIf { frame ->
                depth == frame.depth &&
                    token != JsonToken.END_ARRAY &&
                    token != JsonToken.FIELD_NAME
            }?.let { frame ->
                frame.entries++
                enforceBackupArrayCount(frame)
            }
            when (parser.currentToken) {
                JsonToken.START_OBJECT -> {
                    depth++
                    require(depth <= 100) { "Backup nesting is too deep." }
                }
                JsonToken.END_OBJECT -> depth--
                JsonToken.START_ARRAY -> {
                    depth++
                    require(depth <= 100) { "Backup nesting is too deep." }
                    arrays += BackupArrayFrame(parser.currentName(), depth)
                }
                JsonToken.END_ARRAY -> {
                    arrays.removeLastOrNull()
                    depth--
                }
                JsonToken.VALUE_STRING -> {
                    val value = parser.text
                    val isLegacyUrl = arrays.lastOrNull()?.let { it.name == null && depth == it.depth } == true
                    enforceBackupStringLimit(parser.currentName(), value.length, isLegacyUrl)
                }
                else -> Unit
            }
        }
    }
}

private data class BackupArrayFrame(
    val name: String?,
    val depth: Int,
    var entries: Int = 0
)

private fun enforceBackupArrayCount(frame: BackupArrayFrame) {
    val limit = when (frame.name) {
        "favorites" -> Constants.SecurityLimits.MAX_BACKUP_FAVORITES
        "podcasts", null -> Constants.SecurityLimits.MAX_BACKUP_PODCASTS
        else -> Constants.SecurityLimits.MAX_BACKUP_FAVORITES
    }
    require(frame.entries <= limit) { "Backup array contains too many entries." }
}

private fun enforceBackupStringLimit(
    fieldName: String?,
    length: Int,
    isLegacyUrl: Boolean
) {
    val limit = if (isLegacyUrl) {
        Constants.SecurityLimits.MAX_URL_CHARS
    } else when (fieldName) {
        "url", "image_url", "podcast_url" -> Constants.SecurityLimits.MAX_URL_CHARS
        "title" -> Constants.SecurityLimits.MAX_TITLE_CHARS
        "episode_guid" -> Constants.SecurityLimits.MAX_GUID_CHARS
        "last_modified", "etag" -> Constants.SecurityLimits.MAX_HEADER_CHARS
        else -> Constants.SecurityLimits.MAX_DESCRIPTION_CHARS
    }
    require(length <= limit) { "Backup string field is too long." }
}

internal fun validateBackupData(backupData: BackupData): BackupData {
    if (backupData.podcasts.isEmpty() && backupData.favorites.isEmpty() && backupData.settings == null) {
        throw IllegalArgumentException("Backup file does not contain any restorable data.")
    }
    require(backupData.version in 1..Constants.Backup.BACKUP_VERSION) {
        "Backup version is not supported."
    }
    require(backupData.podcasts.size <= Constants.SecurityLimits.MAX_BACKUP_PODCASTS) {
        "Backup contains too many podcasts."
    }
    require(backupData.favorites.size <= Constants.SecurityLimits.MAX_BACKUP_FAVORITES) {
        "Backup contains too many favorites."
    }
    backupData.settings?.let(::validateBackupSettings)
    if (backupData.podcasts.any { parseNetworkUrl(it.url, allowLocalNetwork = true) == null }) {
        throw IllegalArgumentException("Backup contains an invalid podcast URL.")
    }
    backupData.podcasts.forEach { podcast ->
        require(podcast.url.length <= Constants.SecurityLimits.MAX_URL_CHARS)
        require(podcast.title.orEmpty().length <= Constants.SecurityLimits.MAX_TITLE_CHARS)
        require(podcast.description.orEmpty().length <= Constants.SecurityLimits.MAX_DESCRIPTION_CHARS)
        require(podcast.imageUrl.orEmpty().length <= Constants.SecurityLimits.MAX_URL_CHARS)
        require(podcast.lastModifiedHeader.orEmpty().length <= Constants.SecurityLimits.MAX_HEADER_CHARS)
        require(podcast.eTagHeader.orEmpty().length <= Constants.SecurityLimits.MAX_HEADER_CHARS)
    }
    backupData.favorites.forEach { favorite ->
        require(favorite.podcastUrl.length <= Constants.SecurityLimits.MAX_URL_CHARS)
        require(favorite.episodeGuid.length <= Constants.SecurityLimits.MAX_GUID_CHARS)
    }
    if (backupData.podcasts.any { podcast ->
            !podcast.imageUrl.isNullOrBlank() &&
                parseNetworkUrl(podcast.imageUrl, allowLocalNetwork = true) == null
        }
    ) {
        throw IllegalArgumentException("Backup contains an invalid image URL.")
    }
    return backupData
}

internal fun validateBackupSettings(settings: UserSettings) {
    val limits = Constants.SecurityLimits
    require(settings.colorStrength.isFinite() && settings.colorStrength in 0f..1f) {
        "Backup color strength is outside the supported range."
    }
    require(settings.gridSize in limits.MIN_BACKUP_GRID_SIZE..limits.MAX_BACKUP_GRID_SIZE)
    require(settings.progressBarHeight in limits.MIN_BACKUP_UI_HEIGHT..limits.MAX_BACKUP_UI_HEIGHT)
    require(settings.navBarHeight in limits.MIN_BACKUP_UI_HEIGHT..limits.MAX_BACKUP_UI_HEIGHT)
    require(
        settings.bottomBarAutoHideDelaySeconds in
            limits.MIN_BACKUP_AUTO_HIDE_SECONDS..limits.MAX_BACKUP_AUTO_HIDE_SECONDS
    )
    require(
        settings.gradientBackgroundStrength.isFinite() &&
            settings.gradientBackgroundStrength in 0f..1f
    ) { "Backup gradient strength is outside the supported range." }
    require(
        settings.autoDownloadLimit == Constants.Preferences.NO_DOWNLOAD_LIMIT ||
            settings.autoDownloadLimit in 1..limits.MAX_BACKUP_AUTO_DOWNLOAD_LIMIT
    )
    require(
        settings.backgroundCheckInterval in
            limits.MIN_BACKUP_BACKGROUND_INTERVAL_HOURS..limits.MAX_BACKUP_BACKGROUND_INTERVAL_HOURS
    )
    require(
        settings.markPlayedDurationSeconds in
            0..limits.MAX_BACKUP_MARK_PLAYED_SECONDS
    )
    require(settings.indicator.colorArgb in 0L..0xFFFF_FFFFL)
    require(
        settings.indicator.size in
            limits.MIN_BACKUP_INDICATOR_SIZE..limits.MAX_BACKUP_INDICATOR_SIZE
    )
    require(
        settings.indicator.borderWidth in
            0..limits.MAX_BACKUP_INDICATOR_BORDER
    )
    require(
        settings.indicator.xOffset in
            -limits.MAX_BACKUP_INDICATOR_OFFSET_ABS..limits.MAX_BACKUP_INDICATOR_OFFSET_ABS
    )
    require(
        settings.indicator.yOffset in
            -limits.MAX_BACKUP_INDICATOR_OFFSET_ABS..limits.MAX_BACKUP_INDICATOR_OFFSET_ABS
    )
    require(
        settings.cleanupKeepLimit in
            0..limits.MAX_BACKUP_CLEANUP_KEEP_LIMIT
    )
    require(
        settings.cleanupIntervalHours in
            limits.MIN_BACKUP_CLEANUP_INTERVAL_HOURS..limits.MAX_BACKUP_CLEANUP_INTERVAL_HOURS
    )
}
