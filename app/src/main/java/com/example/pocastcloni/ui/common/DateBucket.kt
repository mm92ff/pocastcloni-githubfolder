package com.example.pocastcloni.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class DateBucket {
    TODAY,
    YESTERDAY,
    LAST_WEEK,
    LAST_MONTH,
    LAST_TWO_MONTHS,
    LAST_FIVE_MONTHS,
    LAST_YEAR,
    OLDER
}

fun Long?.toDateBucket(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): DateBucket {
    if (this == null) return DateBucket.OLDER

    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val date = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(date, today)

    return when {
        daysAgo <= 0 -> DateBucket.TODAY
        daysAgo == 1L -> DateBucket.YESTERDAY
        daysAgo <= 7L -> DateBucket.LAST_WEEK
        daysAgo <= 30L -> DateBucket.LAST_MONTH
        daysAgo <= 60L -> DateBucket.LAST_TWO_MONTHS
        daysAgo <= 150L -> DateBucket.LAST_FIVE_MONTHS
        daysAgo <= 365L -> DateBucket.LAST_YEAR
        else -> DateBucket.OLDER
    }
}
