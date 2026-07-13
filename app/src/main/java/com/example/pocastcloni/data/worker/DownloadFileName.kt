package com.example.pocastcloni.data.worker

import java.net.URI
import java.security.MessageDigest

internal const val MAX_DOWNLOAD_FILE_NAME_BYTES = 240
private const val FILE_NAME_HASH_CHARACTERS = 16
private const val FIRST_PRINTABLE_CHARACTER_CODE = 32

internal fun createDownloadFileName(
    podcastTitle: String,
    episodeTitle: String,
    mimeType: String,
    sourceUrl: String,
    episodeId: Long,
    maxBytes: Int = MAX_DOWNLOAD_FILE_NAME_BYTES
): String {
    require(episodeId > 0L) { "episodeId must be positive" }
    val extension = downloadExtension(mimeType, sourceUrl)
    val rawBase = listOf(podcastTitle, episodeTitle)
        .map(::sanitizeFileNamePart)
        .filter { it.isNotBlank() }
        .joinToString("_")
        .ifBlank { "episode" }
    val hash = sha256Hex("$episodeId|$rawBase|$extension").take(FILE_NAME_HASH_CHARACTERS)
    val suffix = "_$hash"
    val baseBudget = maxBytes - utf8Length(extension) - utf8Length(suffix)
    require(baseBudget > 0) { "maxBytes is too small for a safe file name" }
    val truncatedBase = truncateUtf8(rawBase, baseBudget).trim('.', '_').ifBlank { "episode" }
    return "$truncatedBase$suffix$extension"
}

private fun sanitizeFileNamePart(value: String): String =
    buildString {
        value.trim().forEach { character ->
            append(
                when {
                    character.code < FIRST_PRINTABLE_CHARACTER_CODE -> '_'
                    character in INVALID_FILE_NAME_CHARACTERS -> '_'
                    character.isWhitespace() -> '_'
                    else -> character
                }
            )
        }
    }
        .replace(Regex("_+"), "_")
        .replace("..", ".")
        .trim('.', '_')

private fun downloadExtension(
    mimeType: String,
    sourceUrl: String
): String {
    val normalizedMime = mimeType.substringBefore(';').trim().lowercase()
    val mimeExtension =
        when (normalizedMime) {
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "audio/mp4", "audio/x-m4a", "audio/aac", "audio/aacp" -> ".m4a"
            "audio/ogg", "application/ogg" -> ".ogg"
            "audio/flac", "audio/x-flac" -> ".flac"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/webm" -> ".webm"
            else -> null
        }
    if (mimeExtension != null) return mimeExtension

    val urlExtension =
        runCatching { URI(sourceUrl).path.substringAfterLast('.', "") }
            .getOrDefault("")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{2,8}")) }
    return urlExtension?.let { ".$it" } ?: ".audio"
}

private fun truncateUtf8(
    value: String,
    maxBytes: Int
): String {
    if (utf8Length(value) <= maxBytes) return value
    val result = StringBuilder()
    var index = 0
    var usedBytes = 0
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        val text = String(Character.toChars(codePoint))
        val bytes = utf8Length(text)
        if (usedBytes + bytes > maxBytes) break
        result.append(text)
        usedBytes += bytes
        index += Character.charCount(codePoint)
    }
    return result.toString()
}

private fun utf8Length(value: String): Int = value.toByteArray(Charsets.UTF_8).size

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

private val INVALID_FILE_NAME_CHARACTERS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
