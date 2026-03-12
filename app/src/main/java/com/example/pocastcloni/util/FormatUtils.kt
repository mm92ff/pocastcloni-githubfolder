package com.example.pocastcloni.util

import android.content.Context
import com.example.pocastcloni.R
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun formatBytes(context: Context, bytes: Long): String {
    if (bytes <= 0) return Constants.Format.ZERO_BYTES

    // OPTIMIZATION: In a RecyclerView, calling getStringArray every time is expensive.
    // Ideally, pass this array in or cache it. For now, we fix the crash.
    val units = context.resources.getStringArray(R.array.byte_units)

    val digitGroups = (log10(bytes.toDouble()) / log10(Constants.Format.BYTE_CONVERSION)).toInt()

    // SAFETY FIX: Clamp the index to prevent crash on huge files (e.g. TB/PB)
    // if the array doesn't have enough units defined.
    val safeIndex = digitGroups.coerceAtMost(units.size - 1)

    val value = bytes / Constants.Format.BYTE_CONVERSION.pow(safeIndex.toDouble())

    return String.format(Locale.US, Constants.Format.BYTE_FORMAT, value, units[safeIndex])
}