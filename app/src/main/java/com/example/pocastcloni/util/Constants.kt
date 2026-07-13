package com.example.pocastcloni.util

object Constants {
    // Worker Tags & Keys moved to top level for better visibility
    const val DOWNLOAD_WORKER_TAG = "download_worker"
    const val DOWNLOAD_WORKER_ID_UNIQUE_PREFIX = "download_work_id_"
    const val DOWNLOAD_WORKER_UNIQUE_PREFIX = "download_work_"
    const val DOWNLOAD_WORKER_EPISODE_ID = "episode_id"
    const val DOWNLOAD_WORKER_LEGACY_GUID = "episode_guid"
    const val DOWNLOAD_WORKER_URL = "download_url"
    const val DOWNLOAD_WORKER_FILENAME = "file_name"
    const val DOWNLOAD_WORKER_OUTPUT_PATH = "path"
    const val DOWNLOAD_WORKER_DEFAULT_FILENAME = "temp.mp3"
    const val DOWNLOADS_DIR = "downloads"
    const val DOWNLOAD_FILE_EXTENSION = ".mp3"

    // Preferences Keys moved to top level
    const val KEY_FEED_UPDATE_MODE = "feed_update_mode"

    const val FEED_UPDATE_WORK_NAME = "FeedUpdateWork"
    const val MIN_BACKGROUND_SYNC_INTERVAL_HOURS = 1
    const val FEED_UPDATE_WORK_TAG = "FeedUpdateWorkTag"

    const val SECONDS_IN_HOUR = 3600
    const val SECONDS_IN_MINUTE = 60
    const val COMPLETION_PERCENTAGE = 0.95

    const val UNKNOWN_PODCAST = "Unknown Podcast"
    const val EMPTY_STRING = ""

    object Player {
        const val NORMAL_BUFFER_DURATION_MS = 15000 // 15 seconds
        const val MAX_BUFFER_DURATION_MS = 50000 // 50 seconds

        // Maximal-mode (formerly "whole podcast")
        const val MAXIMAL_BUFFER_DURATION_MS = 7200000 // 2 hours
        const val MAX_BUFFER_FOR_PLAYBACK_MS = 2500
        const val MAX_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000
    }

    object PlayerDefaults {
        const val REWIND_INTERVAL_MS = 10000L
        const val FORWARD_INTERVAL_MS = 30000L
    }

    object Notification {
        const val CHANNEL_PLAYBACK_ID = "playback_channel"
    }

    object SettingsDefaults {
        const val MIN_BACKGROUND_CHECK_INTERVAL_HOURS = 1f
        const val MAX_BACKGROUND_CHECK_INTERVAL_HOURS = 12f
        const val MIN_AUTO_DOWNLOAD_LIMIT = 1f
        const val MAX_AUTO_DOWNLOAD_LIMIT = 10f
        const val MIN_GRID_SIZE_DP = 60f
        const val MAX_GRID_SIZE_DP = 200f
        const val MIN_PROGRESS_BAR_HEIGHT_DP = 20f
        const val MAX_PROGRESS_BAR_HEIGHT_DP = 60f
        const val MIN_NAV_BAR_HEIGHT_DP = 60f
        const val MAX_NAV_BAR_HEIGHT_DP = 120f
        const val MIN_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS = 2f
        const val MAX_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS = 30f
        const val MIN_MARK_PLAYED_DURATION_SECONDS = 0f
        const val MAX_MARK_PLAYED_DURATION_SECONDS = 30f
        const val MIN_CLEANUP_KEEP_LIMIT = 10f
        const val MAX_CLEANUP_KEEP_LIMIT = 500f
        const val MIN_CLEANUP_INTERVAL_HOURS = 1f
        const val MAX_CLEANUP_INTERVAL_HOURS = 168f
        const val MIN_INDICATOR_SIZE_DP = 4f
        const val MAX_INDICATOR_SIZE_DP = 50f
        const val MIN_INDICATOR_BORDER_DP = 0f
        const val MAX_INDICATOR_BORDER_DP = 20f
        const val MIN_INDICATOR_OFFSET_DP = 0f
        const val MAX_INDICATOR_OFFSET_DP = 20f
    }

    object Database {
        const val DATABASE_NAME = "pocast_cloni_db"
        const val DATABASE_VERSION = 15
        const val DEFAULT_SORT_ORDER = 0L
        const val TABLE_PODCASTS = "podcasts"
        const val TABLE_EPISODES = "episodes"
    }

    object Backup {
        const val BACKUP_VERSION = 1
        const val BACKUP_FILE_NAME = "pocast_backup.json"
        const val MIME_TYPE_JSON = "application/json"
        const val MIME_TYPE_ALL = "*/*"
        const val KEY_VERSION = "version"
        const val KEY_PODCASTS = "podcasts"
        const val KEY_SETTINGS = "settings"
        const val KEY_URL = "url"
        const val KEY_SORT_ORDER = "sortOrder"
    }

    object Parsing {
        const val RSS = "rss"
        const val CHANNEL = "channel"
        const val ITEM = "item"
        const val TITLE = "title"
        const val DESCRIPTION = "description"
        const val IMAGE = "image"
        const val ITUNES_IMAGE = "itunes:image"
        const val HREF = "href"
        const val LINK = "link"
        const val GUID = "guid"
        const val PUB_DATE = "pubDate"
        const val ITUNES_DURATION = "itunes:duration"
        const val ENCLOSURE = "enclosure"
        const val URL = "url"
        const val TYPE = "type"
        const val LENGTH = "length"
        val DATE_FORMATS =
            listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd HH:mm:ss")
        const val DEFAULT_ENCLOSURE_LENGTH = 0L
    }

    object Itunes {
        const val SEARCH_URL = "search?media=podcast"
        const val TERM = "term"
        const val KEY_RESULT_COUNT = "resultCount"
        const val KEY_RESULTS = "results"
        const val KEY_COLLECTION_NAME = "collectionName"
        const val KEY_ARTIST_NAME = "artistName"
        const val KEY_FEED_URL = "feedUrl"
        const val KEY_ARTWORK_URL_600 = "artworkUrl600"
    }

    object Statistics {
        const val DATASTORE_NAME = "statistics"
        const val KEY_DOWNLOAD_WIFI = "download_wifi"
        const val KEY_DOWNLOAD_MOBILE = "download_mobile"
        const val KEY_STREAM_WIFI = "stream_wifi"
        const val KEY_STREAM_MOBILE = "stream_mobile"
        const val KEY_UPLOAD = "upload"
        const val KEY_LISTENING_TIME = "listening_time"
        const val KEY_STATISTICS_STARTED_AT = "statistics_started_at"
    }

    object Preferences {
        const val DATASTORE_NAME = "settings"

        const val KEY_THEME = "theme"
        const val KEY_APP_COLOR = "app_color"
        const val KEY_COLOR_STRENGTH = "color_strength"
        const val KEY_BUFFER_MODE = "buffer_mode"
        const val KEY_LAYOUT_MODE = "layout_mode"
        const val KEY_GRID_SIZE = "grid_size"
        const val KEY_SHOW_GRID_TITLES = "show_grid_titles"
        const val KEY_CONFIRM_DELETE = "confirm_delete"
        const val KEY_PROGRESS_BAR_HEIGHT = "progress_bar_height"
        const val KEY_NAV_BAR_HEIGHT = "nav_bar_height"
        const val KEY_SHOW_MINI_PLAYER_TIME_OVERLAY = "show_mini_player_time_overlay"
        const val KEY_TRANSPARENT_MINI_PLAYER = "transparent_mini_player"
        const val KEY_TRANSPARENT_BOTTOM_BAR = "transparent_bottom_bar"
        const val KEY_ONE_HANDED_MODE = "one_handed_mode"
        const val KEY_BOTTOM_BAR_CLEAN_MODE_ENABLED = "bottom_bar_clean_mode_enabled"
        const val KEY_BOTTOM_BAR_AUTO_HIDE_ENABLED = "bottom_bar_auto_hide_enabled"
        const val KEY_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS = "bottom_bar_auto_hide_delay_seconds"
        const val KEY_GRADIENT_BACKGROUND_ENABLED = "gradient_background_enabled"
        const val KEY_GRADIENT_BACKGROUND_STRENGTH = "gradient_background_strength"
        const val KEY_GRADIENT_BACKGROUND_DIRECTION = "gradient_background_direction"
        const val KEY_TRANSPARENT_SEARCH_CARDS = "transparent_search_cards"
        const val KEY_TRANSPARENT_PODCAST_CARDS = "transparent_podcast_cards"
        const val KEY_TRANSPARENT_EPISODE_ROWS = "transparent_episode_rows"
        const val KEY_AUTO_DOWNLOAD_LIMIT = "auto_download_limit"
        const val KEY_AUTO_REFRESH_ON_START = "auto_refresh_on_start"
        const val KEY_BACKGROUND_CHECK_ENABLED = "background_check_enabled"
        const val KEY_BACKGROUND_CHECK_INTERVAL = "background_check_interval"
        const val KEY_MARK_PLAYED_DURATION = "mark_played_duration"
        const val KEY_INDICATOR_COLOR = "indicator_color"
        const val KEY_INDICATOR_SIZE = "indicator_size"
        const val KEY_INDICATOR_BORDER = "indicator_border"
        const val KEY_INDICATOR_X_OFFSET = "indicator_x_offset"
        const val KEY_INDICATOR_Y_OFFSET = "indicator_y_offset"
        const val KEY_SAVE_TO_DOWNLOADS_FOLDER = "save_to_downloads_folder"

        const val DEFAULT_GRID_SIZE = 100
        const val DEFAULT_SHOW_GRID_TITLES = true
        const val DEFAULT_CONFIRM_DELETE = true
        const val DEFAULT_PROGRESS_BAR_HEIGHT = 30
        const val DEFAULT_NAV_BAR_HEIGHT = 80
        const val DEFAULT_SHOW_MINI_PLAYER_TIME_OVERLAY = false
        const val DEFAULT_TRANSPARENT_MINI_PLAYER = false
        const val DEFAULT_TRANSPARENT_BOTTOM_BAR = false
        const val DEFAULT_ONE_HANDED_MODE = false
        const val DEFAULT_BOTTOM_BAR_CLEAN_MODE_ENABLED = false
        const val DEFAULT_BOTTOM_BAR_AUTO_HIDE_ENABLED = false
        const val DEFAULT_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS = 5
        const val DEFAULT_GRADIENT_BACKGROUND_ENABLED = false
        const val DEFAULT_GRADIENT_BACKGROUND_STRENGTH = 1.0f
        const val DEFAULT_GRADIENT_BACKGROUND_DIRECTION = "TOP_TO_BOTTOM"
        const val DEFAULT_TRANSPARENT_SEARCH_CARDS = false
        const val DEFAULT_TRANSPARENT_PODCAST_CARDS = false
        const val DEFAULT_TRANSPARENT_EPISODE_ROWS = false
        const val DEFAULT_AUTO_DOWNLOAD_LIMIT = 3
        const val DEFAULT_AUTO_REFRESH_ON_START = true
        const val DEFAULT_BACKGROUND_CHECK_ENABLED = true
        const val DEFAULT_BACKGROUND_CHECK_INTERVAL = 1
        const val DEFAULT_INDICATOR_COLOR = 0xFF4CAF50
        const val DEFAULT_INDICATOR_SIZE = 12
        const val DEFAULT_INDICATOR_BORDER = 1
        const val DEFAULT_INDICATOR_X_OFFSET = 2
        const val DEFAULT_INDICATOR_Y_OFFSET = 2
        const val NO_DOWNLOAD_LIMIT = 0
        const val DEFAULT_SAVE_TO_DOWNLOADS_FOLDER = false

        const val KEY_AUTO_CLEANUP_ENABLED = "auto_cleanup_enabled"
        const val KEY_CLEANUP_KEEP_LIMIT = "cleanup_keep_limit"
        const val KEY_CLEANUP_INTERVAL_HOURS = "cleanup_interval_hours"

        const val DEFAULT_AUTO_CLEANUP_ENABLED = true
        const val DEFAULT_CLEANUP_KEEP_LIMIT = 50
        const val DEFAULT_CLEANUP_INTERVAL_HOURS = 24
    }

    object Network {
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 30L
        const val WRITE_TIMEOUT_SECONDS = 30L

        // TODO: This placeholder base URL is problematic. RSS URLs should be user-provided per podcast.
        // Consider refactoring RSS client to not require a fixed base URL.
        const val RSS_BASE_URL = "https://example.com/"
        const val ITUNES_BASE_URL = "https://itunes.apple.com/"
        const val HEADER_LAST_MODIFIED = "Last-Modified"
        const val HEADER_ETAG = "ETag"
        const val HEADER_IF_MODIFIED_SINCE = "If-Modified-since"
        const val HEADER_IF_NONE_MATCH = "If-None-Match"
    }

    object SecurityLimits {
        const val MAX_FEED_BYTES = 20L * 1024L * 1024L
        const val MAX_FEED_ITEMS = 5_000
        const val MAX_DOWNLOAD_BYTES = 2L * 1024L * 1024L * 1024L
        const val MIN_FREE_STORAGE_RESERVE_BYTES = 64L * 1024L * 1024L
        const val STORAGE_RECHECK_INTERVAL_BYTES = 8L * 1024L * 1024L
        const val MAX_BACKUP_BYTES = 25L * 1024L * 1024L
        const val MAX_BACKUP_PODCASTS = 5_000
        const val MAX_BACKUP_FAVORITES = 100_000
        const val MAX_URL_CHARS = 8_192
        const val MAX_TITLE_CHARS = 2_000
        const val MAX_DESCRIPTION_CHARS = 500_000
        const val MAX_GUID_CHARS = 8_192
        const val MAX_HEADER_CHARS = 8_192
        const val MIN_BACKUP_GRID_SIZE = 1
        const val MAX_BACKUP_GRID_SIZE = 200
        const val MIN_BACKUP_UI_HEIGHT = 1
        const val MAX_BACKUP_UI_HEIGHT = 200
        const val MIN_BACKUP_AUTO_HIDE_SECONDS = 1
        const val MAX_BACKUP_AUTO_HIDE_SECONDS = 300
        const val MAX_BACKUP_AUTO_DOWNLOAD_LIMIT = 100
        const val MIN_BACKUP_BACKGROUND_INTERVAL_HOURS = 1
        const val MAX_BACKUP_BACKGROUND_INTERVAL_HOURS = 168
        const val MAX_BACKUP_MARK_PLAYED_SECONDS = 3_600
        const val MIN_BACKUP_INDICATOR_SIZE = 1
        const val MAX_BACKUP_INDICATOR_SIZE = 100
        const val MAX_BACKUP_INDICATOR_BORDER = 50
        const val MAX_BACKUP_INDICATOR_OFFSET_ABS = 100
        const val MAX_BACKUP_CLEANUP_KEEP_LIMIT = 10_000
        const val MIN_BACKUP_CLEANUP_INTERVAL_HOURS = 1
        const val MAX_BACKUP_CLEANUP_INTERVAL_HOURS = 8_760
    }

    object ViewModel {
        const val STATE_IN_TIMEOUT = 5000L
        const val DEFAULT_PROGRESS_BAR_HEIGHT = 4
        const val DEFAULT_NAV_BAR_HEIGHT = 80
    }

    object Weights {
        const val FULL = 1f
    }

    object Animation {
        const val STIFFNESS = 400f
    }

    object Format {
        const val ZERO_BYTES = "0 B"
        const val BYTE_CONVERSION = 1024.0
        const val BYTE_FORMAT = "%.1f %s"
        const val TIME_FORMAT = "%02d:%02d"
    }

    object UI {
        const val PODCAST_SEARCH_ITEM_MAX_LINES = 2
        const val INDICATOR_Z_INDEX = 1f
        const val DELETE_ICON_Z_INDEX = 2f
        const val APP_BAR_TITLE_MAX_LINES = 1
        const val GRID_ITEM_TITLE_MAX_LINES = 2
        const val GRID_ITEM_SELECTED_SCALE = 1.05f
        const val ASPECT_RATIO_1F = 1f
        const val EDIT_MODE_NON_SELECTED_ALPHA = 0.6f
        const val EDIT_MODE_NON_SELECTED_ALPHA_GRID = 0.5f
        const val SETTINGS_CARD_ALPHA = 0.6f
        val INDICATOR_COLORS = listOf(0xFF4CAF50, 0xFF2196F3, 0xFFFFC107, 0xFFF44336, 0xFF9C27B0, 0xFF607D8B)
        const val CHECK_ICON_COLOR_WHITE = 0xFFFFFFFF
    }

    object Image {
        const val IMAGE_SIZE_LIST = 300
        const val IMAGE_SIZE_GRID = 400
    }

    object Validation {
        // Allows dates up to 7 days in the future (to account for timezone/server differences).
        // Anything further in the future is treated as an error.
        const val MAX_FUTURE_DATE_THRESHOLD_MS = 1000L * 60 * 60 * 24 * 7
    }
}
