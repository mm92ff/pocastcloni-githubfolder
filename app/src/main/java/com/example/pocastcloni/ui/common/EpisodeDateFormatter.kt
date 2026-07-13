package com.example.pocastcloni.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatEpisodePublishDate(
    epochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(Locale.Category.FORMAT)
): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

fun formatEpisodePublishDateOrNull(
    epochMs: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(Locale.Category.FORMAT)
): String? = epochMs?.let { formatEpisodePublishDate(it, zoneId, locale) }
