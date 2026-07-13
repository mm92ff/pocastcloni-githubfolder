package com.example.pocastcloni.ui.common

import android.content.Context
import com.example.pocastcloni.R
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatEpisodeDuration(
    context: Context,
    durationMs: Long,
    locale: Locale = Locale.getDefault(Locale.Category.FORMAT)
): String {
    if (durationMs <= 0L) return ""

    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) - TimeUnit.HOURS.toMinutes(hours)
    val numberFormat = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }

    return if (hours > 0L) {
        val minuteFormat = NumberFormat.getIntegerInstance(locale).apply {
            isGroupingUsed = false
            minimumIntegerDigits = 2
        }
        context.getString(
            R.string.episode_duration_hours_minutes,
            numberFormat.format(hours),
            minuteFormat.format(minutes)
        )
    } else {
        context.getString(
            R.string.episode_duration_minutes,
            numberFormat.format(minutes.coerceAtLeast(1L))
        )
    }
}
