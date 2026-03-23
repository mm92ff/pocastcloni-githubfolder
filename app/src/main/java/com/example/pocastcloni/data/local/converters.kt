package com.example.pocastcloni.data.local

import androidx.room.TypeConverter
import timber.log.Timber
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDownloadStatus(status: String): DownloadStatus {
        return try {
            DownloadStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            Timber.w("Unknown download status: $status")
            DownloadStatus.NOT_DOWNLOADED
        }
    }
}
