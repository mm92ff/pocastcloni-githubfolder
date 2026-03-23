package com.example.pocastcloni.util

import android.content.Context
import com.example.pocastcloni.R
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formats milliseconds into a MM:SS string.
 */
fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val minutes = totalSeconds / Constants.SECONDS_IN_MINUTE
    val seconds = totalSeconds % Constants.SECONDS_IN_MINUTE

    // FIX: Strictly use Locale.US for "Digital Clock" numbers.
    // Prevents Arab/Persian numerals (١٢:٣٠) from breaking the Player UI layout.
    return String.format(Locale.US, Constants.Format.TIME_FORMAT, minutes, seconds)
}

/**
 * Formats seconds into a human-readable string (e.g. "1h 45min").
 * The default locale is fine here since this is prose.
 */
fun formatDuration(
    context: Context,
    seconds: Long
): String {
    if (seconds <= 0) return context.getString(R.string.duration_placeholder)

    val hours = seconds / Constants.SECONDS_IN_HOUR
    val minutes = (seconds % Constants.SECONDS_IN_HOUR) / Constants.SECONDS_IN_MINUTE

    return if (hours > 0) {
        context.getString(R.string.duration_hours_minutes, hours, minutes)
    } else {
        if (minutes == 0L) {
            context.getString(R.string.duration_less_than_a_minute)
        } else {
            context.resources.getQuantityString(R.plurals.duration_minutes, minutes.toInt(), minutes.toInt())
        }
    }
}
