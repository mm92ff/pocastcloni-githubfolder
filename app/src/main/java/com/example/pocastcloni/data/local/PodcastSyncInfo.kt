package com.example.pocastcloni.data.local

import androidx.room.ColumnInfo

/**
 * Eine optimierte Projektion der PodcastEntity für Hintergrund-Operationen.
 * Lädt keine schweren Daten (Beschreibung, Bild-URLs etc.) in den Speicher.
 */
data class PodcastSyncInfo(
    @ColumnInfo(name = "rssUrl") val rssUrl: String,
    @ColumnInfo(name = "autoDownloadEnabled") val autoDownloadEnabled: Boolean
)