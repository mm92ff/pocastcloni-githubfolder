package com.example.pocastcloni.ui.home.common

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.pocastcloni.R
import com.example.pocastcloni.util.Constants
import java.security.MessageDigest

internal enum class PodcastCoverSize(
    val pixels: Int
) {
    LIST(Constants.Image.IMAGE_SIZE_LIST),
    GRID(Constants.Image.IMAGE_SIZE_GRID)
}

internal data class PodcastCoverRequestIdentity(
    val normalizedUrl: String,
    val pixelSize: Int,
    val memoryCacheKey: String,
    val diskCacheKey: String,
    val diagnosticId: String
)

/** Metadata used by the global Coil listener without exposing a podcast title or network URL. */
internal data class PodcastCoverRequestTag(
    val diagnosticId: String,
    val pixelSize: Int
)

internal object PodcastCoverRequestFactory {
    fun identity(
        url: String,
        size: PodcastCoverSize
    ): PodcastCoverRequestIdentity? {
        val normalizedUrl = url.trim().takeIf(String::isNotEmpty) ?: return null
        return PodcastCoverRequestIdentity(
            normalizedUrl = normalizedUrl,
            pixelSize = size.pixels,
            memoryCacheKey = "$normalizedUrl#${size.pixels}",
            diskCacheKey = normalizedUrl,
            diagnosticId = normalizedUrl.sha256Prefix()
        )
    }

    fun create(
        context: Context,
        url: String,
        size: PodcastCoverSize
    ): ImageRequest? {
        val identity = identity(url, size) ?: return null
        return ImageRequest.Builder(context)
            .data(identity.normalizedUrl)
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_launcher_foreground)
            .fallback(R.drawable.ic_launcher_foreground)
            .size(identity.pixelSize)
            .precision(Precision.EXACT)
            .memoryCacheKey(identity.memoryCacheKey)
            .diskCacheKey(identity.diskCacheKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .tag(PodcastCoverRequestTag(identity.diagnosticId, identity.pixelSize))
            .build()
    }
}

private fun String.sha256Prefix(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .take(DIAGNOSTIC_HASH_BYTES)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

private const val DIAGNOSTIC_HASH_BYTES = 6
private const val BYTE_MASK = 0xff
