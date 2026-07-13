package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pocastcloni.domain.repository.AppStatistics
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private val Context.statsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.Statistics.DATASTORE_NAME
)

interface StreamStatisticsWriter {
    suspend fun addStreamBytes(
        wifiBytes: Long,
        mobileBytes: Long
    )
}

@Singleton
class StatisticsDataStore
@Inject
constructor(
    @ApplicationContext context: Context
) : StreamStatisticsWriter {
    private object Keys {
        val DOWNLOAD_WIFI = longPreferencesKey(Constants.Statistics.KEY_DOWNLOAD_WIFI)
        val DOWNLOAD_MOBILE = longPreferencesKey(Constants.Statistics.KEY_DOWNLOAD_MOBILE)
        val STREAM_WIFI = longPreferencesKey(Constants.Statistics.KEY_STREAM_WIFI)
        val STREAM_MOBILE = longPreferencesKey(Constants.Statistics.KEY_STREAM_MOBILE)
        val UPLOAD = longPreferencesKey(Constants.Statistics.KEY_UPLOAD)
        val LISTENING_TIME = longPreferencesKey(Constants.Statistics.KEY_LISTENING_TIME)
        val STATISTICS_STARTED_AT = longPreferencesKey(Constants.Statistics.KEY_STATISTICS_STARTED_AT)
    }

    private val dataStore = context.statsDataStore
    private val writeMutex = Mutex()
    private val resetGeneration = AtomicLong(0L)

    val statsFlow: Flow<AppStatistics> =
        dataStore.data.map { preferences ->
            AppStatistics(
                downloadWifiBytes = preferences[Keys.DOWNLOAD_WIFI] ?: 0L,
                downloadMobileBytes = preferences[Keys.DOWNLOAD_MOBILE] ?: 0L,
                streamWifiBytes = preferences[Keys.STREAM_WIFI] ?: 0L,
                streamMobileBytes = preferences[Keys.STREAM_MOBILE] ?: 0L,
                uploadBytes = preferences[Keys.UPLOAD] ?: 0L,
                totalListeningTimeMs = preferences[Keys.LISTENING_TIME] ?: 0L,
                statisticsStartedAt = preferences[Keys.STATISTICS_STARTED_AT] ?: 0L
            )
        }

    suspend fun initializeStatisticsStartedAt() {
        editCurrentGeneration { preferences ->
            if (preferences[Keys.STATISTICS_STARTED_AT] == null) {
                preferences[Keys.STATISTICS_STARTED_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun addDownloadBytes(
        bytes: Long,
        isWifi: Boolean
    ) {
        if (bytes <= 0L) return

        editCurrentGeneration { preferences ->
            val key = if (isWifi) Keys.DOWNLOAD_WIFI else Keys.DOWNLOAD_MOBILE
            preferences[key] = (preferences[key] ?: 0L) + bytes
        }
    }

    override suspend fun addStreamBytes(
        wifiBytes: Long,
        mobileBytes: Long
    ) {
        if (wifiBytes <= 0L && mobileBytes <= 0L) return

        editCurrentGeneration { preferences ->
            if (wifiBytes > 0L) {
                preferences[Keys.STREAM_WIFI] =
                    (preferences[Keys.STREAM_WIFI] ?: 0L) + wifiBytes
            }
            if (mobileBytes > 0L) {
                preferences[Keys.STREAM_MOBILE] =
                    (preferences[Keys.STREAM_MOBILE] ?: 0L) + mobileBytes
            }
        }
    }

    suspend fun addUploadBytes(bytes: Long) {
        if (bytes <= 0L) return

        editCurrentGeneration { preferences ->
            preferences[Keys.UPLOAD] = (preferences[Keys.UPLOAD] ?: 0L) + bytes
        }
    }

    suspend fun addListeningTime(ms: Long) {
        if (ms <= 0L) return

        editCurrentGeneration { preferences ->
            preferences[Keys.LISTENING_TIME] =
                (preferences[Keys.LISTENING_TIME] ?: 0L) + ms
        }
    }

    suspend fun resetStatistics() {
        writeMutex.withLock {
            dataStore.edit { preferences ->
                preferences.clear()
                preferences[Keys.STATISTICS_STARTED_AT] = System.currentTimeMillis()
            }
            resetGeneration.incrementAndGet()
        }
    }

    private suspend fun editCurrentGeneration(
        transform: suspend (MutablePreferences) -> Unit
    ) {
        val generationAtCall = resetGeneration.get()
        writeMutex.withLock {
            if (generationAtCall == resetGeneration.get()) {
                dataStore.edit(transform)
            }
        }
    }
}
