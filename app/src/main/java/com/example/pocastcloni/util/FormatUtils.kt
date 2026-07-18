package com.example.pocastcloni.util

import android.content.Context
import com.example.pocastcloni.R
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun formatBytes(
    context: Context,
    bytes: Long
): String =
    formatBytes(
        bytes = bytes,
        units = context.resources.getStringArray(R.array.byte_units),
        locale = context.appFormatLocale()
    )

internal fun formatBytes(
    bytes: Long,
    units: Array<String>,
    locale: Locale
): String {
    require(units.isNotEmpty()) { "Byte units must not be empty" }
    if (bytes <= 0) return String.format(locale, "%d %s", 0, units.first())

    val digitGroups = (log10(bytes.toDouble()) / log10(Constants.Format.BYTE_CONVERSION)).toInt()
    val safeIndex = digitGroups.coerceAtMost(units.size - 1)
    val value = bytes / Constants.Format.BYTE_CONVERSION.pow(safeIndex.toDouble())

    return String.format(locale, Constants.Format.BYTE_FORMAT, value, units[safeIndex])
}
