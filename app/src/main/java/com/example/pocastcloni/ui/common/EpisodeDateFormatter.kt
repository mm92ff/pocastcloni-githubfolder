package com.example.pocastcloni.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val episodePublishDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun formatEpisodePublishDate(
    epochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(zoneId)
        .format(episodePublishDateFormatter)

fun formatEpisodePublishDateOrNull(
    epochMs: Long?,
    zoneId: ZoneId = ZoneId.systemDefault()
): String? = epochMs?.let { formatEpisodePublishDate(it, zoneId) }
