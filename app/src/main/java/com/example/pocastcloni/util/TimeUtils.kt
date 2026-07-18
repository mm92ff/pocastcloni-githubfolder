package com.example.pocastcloni.util

import android.content.Context
import com.example.pocastcloni.R
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formats milliseconds into a MM:SS string.
 */
fun formatTime(
    ms: Long,
    locale: Locale
): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val minutes = totalSeconds / Constants.SECONDS_IN_MINUTE
    val seconds = totalSeconds % Constants.SECONDS_IN_MINUTE

    return String.format(locale, Constants.Format.TIME_FORMAT, minutes, seconds)
}

/**
 * Formats seconds into a human-readable string (e.g. "1h 45min").
 */
fun formatDuration(
    context: Context,
    seconds: Long
): String {
    if (seconds <= 0) return context.getString(R.string.duration_placeholder)

    val hours = seconds / Constants.SECONDS_IN_HOUR
    val minutes = (seconds % Constants.SECONDS_IN_HOUR) / Constants.SECONDS_IN_MINUTE
    val numberFormat =
        NumberFormat.getIntegerInstance(context.appFormatLocale()).apply {
            isGroupingUsed = false
        }

    return if (hours > 0) {
        context.getString(
            R.string.duration_hours_minutes,
            numberFormat.format(hours),
            numberFormat.format(minutes)
        )
    } else {
        if (minutes == 0L) {
            context.getString(R.string.duration_less_than_a_minute)
        } else {
            context.resources.getQuantityString(
                R.plurals.duration_minutes,
                minutes.toInt(),
                numberFormat.format(minutes)
            )
        }
    }
}
