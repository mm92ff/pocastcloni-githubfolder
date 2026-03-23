package com.example.pocastcloni.domain.repository

import kotlinx.coroutines.flow.Flow

data class AppStatistics(
    val downloadWifiBytes: Long = 0,
    val downloadMobileBytes: Long = 0,
    val streamWifiBytes: Long = 0,
    val streamMobileBytes: Long = 0,
    val uploadBytes: Long = 0,
    val totalListeningTimeMs: Long = 0,
    val totalEpisodes: Int = 0,
    val episodesInProgress: Int = 0,
    val episodesPlayed: Int = 0
)

interface StatisticsRepository {
    val statsFlow: Flow<AppStatistics>

    suspend fun addDownloadBytes(
        bytes: Long,
        isWifi: Boolean
    )

    suspend fun addStreamBytes(
        bytes: Long,
        isWifi: Boolean
    )

    suspend fun addUploadBytes(bytes: Long)

    suspend fun addListeningTime(ms: Long)

    suspend fun resetStatistics()
}
