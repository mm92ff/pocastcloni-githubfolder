package com.example.pocastcloni.data.manager

import android.content.ContentResolver
import android.net.Uri
import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupEpisodeState
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
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
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
        episodeStates: List<BackupEpisodeState>,
        settings: UserSettings,
        uri: Uri,
        contentResolver: ContentResolver
    ) {
        withContext(dispatcherProvider.io) {
            try {
                val backupData =
                    validateBackupData(
                        BackupData(
                            version = Constants.Backup.BACKUP_VERSION,
                            podcasts = podcasts,
                            settings = settings,
                            episodeStates = episodeStates
                        )
                    )

                val outputStream =
                    contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Could not open the backup destination.")

                writeBackupToStream(backupData, outputStream)
                Timber.d("Backup exported successfully to $uri")
            } catch (e: Exception) {
                Timber.e(e, "Failed to export backup")
                throw e
            }
        }
    }

    internal fun writeBackupToStream(
        backupData: BackupData,
        outputStream: OutputStream
    ) {
        BufferedOutputStream(outputStream, BACKUP_EXPORT_BUFFER_BYTES).use { bufferedOutput ->
            objectMapper.factory.createGenerator(bufferedOutput).use { generator ->
                objectMapper.writeValue(generator, backupData)
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

internal const val BACKUP_EXPORT_BUFFER_BYTES = 16 * 1024

internal fun parseBackupJson(
    jsonString: String,
    objectMapper: ObjectMapper
): BackupData {
    if (jsonString.isBlank()) {
        throw IllegalArgumentException("Backup file is empty.")
    }
    prevalidateBackupJson(jsonString, objectMapper)

    try {
        val parsedBackupData = objectMapper.readValue(jsonString, BackupData::class.java)
        val backupData = if (
            objectMapper.readTree(jsonString).has(Constants.Backup.KEY_VERSION)
        ) {
            parsedBackupData
        } else {
            parsedBackupData.copy(version = 1)
        }
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
        return validateBackupData(BackupData(version = 1, podcasts = backupPodcasts))
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
        Constants.Backup.KEY_EPISODE_STATES,
        "episode_states" -> Constants.SecurityLimits.MAX_BACKUP_EPISODE_STATES
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
    val limit =
        if (isLegacyUrl) {
            Constants.SecurityLimits.MAX_URL_CHARS
        } else {
            when (fieldName) {
                "url", "image_url", "podcast_url", "podcastUrl" -> Constants.SecurityLimits.MAX_URL_CHARS
                "title" -> Constants.SecurityLimits.MAX_TITLE_CHARS
                "episode_guid", "episodeGuid" -> Constants.SecurityLimits.MAX_GUID_CHARS
                "last_modified", "etag" -> Constants.SecurityLimits.MAX_HEADER_CHARS
                else -> Constants.SecurityLimits.MAX_DESCRIPTION_CHARS
            }
        }
    require(length <= limit) { "Backup string field is too long." }
}

internal fun validateBackupData(backupData: BackupData): BackupData {
    val hasRestorableEntries =
        backupData.podcasts.isNotEmpty() ||
            backupData.favorites.isNotEmpty() ||
            backupData.episodeStates.isNotEmpty()
    require(hasRestorableEntries || backupData.settings != null) {
        "Backup file does not contain any restorable data."
    }
    require(backupData.version in 1..Constants.Backup.BACKUP_VERSION) {
        "Backup version is not supported."
    }
    require(backupData.version >= 2 || backupData.episodeStates.isEmpty()) {
        "Backup version 1 cannot contain episode state entries."
    }
    require(backupData.podcasts.size <= Constants.SecurityLimits.MAX_BACKUP_PODCASTS) {
        "Backup contains too many podcasts."
    }
    require(backupData.favorites.size <= Constants.SecurityLimits.MAX_BACKUP_FAVORITES) {
        "Backup contains too many favorites."
    }
    require(backupData.episodeStates.size <= Constants.SecurityLimits.MAX_BACKUP_EPISODE_STATES) {
        "Backup contains too many episode states."
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
    require(backupData.podcasts.map { it.url }.toSet().size == backupData.podcasts.size) {
        "Backup contains duplicate podcasts."
    }
    backupData.favorites.forEach { favorite ->
        require(favorite.podcastUrl.length <= Constants.SecurityLimits.MAX_URL_CHARS)
        require(favorite.episodeGuid.length <= Constants.SecurityLimits.MAX_GUID_CHARS)
        require(favorite.episodeGuid.isNotBlank())
        require(parseNetworkUrl(favorite.podcastUrl, allowLocalNetwork = true) != null)
    }
    require(
        backupData.favorites.map { it.podcastUrl to it.episodeGuid }.toSet().size ==
            backupData.favorites.size
    ) { "Backup contains duplicate legacy favorites." }
    validateEpisodeStates(backupData)
    if (
        backupData.podcasts.any { podcast ->
            !podcast.imageUrl.isNullOrBlank() &&
                parseNetworkUrl(podcast.imageUrl, allowLocalNetwork = true) == null
        }
    ) {
        throw IllegalArgumentException("Backup contains an invalid image URL.")
    }
    return backupData
}

private fun validateEpisodeStates(backupData: BackupData) {
    val podcastUrls = backupData.podcasts.mapTo(mutableSetOf()) { it.url }
    val keys = mutableSetOf<Pair<String, String>>()
    backupData.episodeStates.forEach { state ->
        require(parseNetworkUrl(state.podcastUrl, allowLocalNetwork = true) != null) {
            "Backup contains an invalid episode podcast URL."
        }
        require(state.episodeGuid.isNotBlank()) { "Backup contains an empty episode GUID." }
        require(state.podcastUrl.length <= Constants.SecurityLimits.MAX_URL_CHARS)
        require(state.episodeGuid.length <= Constants.SecurityLimits.MAX_GUID_CHARS)
        require(state.title.length <= Constants.SecurityLimits.MAX_TITLE_CHARS)
        require(state.description.length <= Constants.SecurityLimits.MAX_DESCRIPTION_CHARS)
        require(state.duration >= 0) { "Backup contains a negative episode duration." }
        require(state.playbackPositionMs >= 0) { "Backup contains a negative playback position." }
        require(state.favoriteAddedAt == null || state.favoriteAddedAt >= 0)
        require(state.favoriteOrder == null || state.favoriteOrder >= 0)
        require(state.datePlayed == null || state.datePlayed >= 0)
        require(keys.add(state.podcastUrl to state.episodeGuid)) {
            "Backup contains duplicate episode states."
        }
        if (backupData.version >= 2) {
            require(state.podcastUrl in podcastUrls) {
                "Backup episode state references a podcast outside the backup."
            }
            if (state.isFavorite) {
                require(state.favoriteAddedAt != null && state.favoriteOrder != null) {
                    "Backup favorite state is incomplete."
                }
            } else {
                require(state.favoriteAddedAt == null && state.favoriteOrder == null) {
                    "Backup non-favorite state contains favorite metadata."
                }
            }
        }
    }
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
