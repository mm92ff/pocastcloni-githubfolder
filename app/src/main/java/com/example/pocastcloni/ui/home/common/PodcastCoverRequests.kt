package com.example.pocastcloni.ui.home.common

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.pocastcloni.R
import com.example.pocastcloni.data.cover.PodcastCoverRequestData
import com.example.pocastcloni.domain.model.Podcast
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

internal data class PersistentPodcastCoverIdentity(
    val diagnosticId: String,
    val thumbnailRevision: Long,
    val pixelSize: Int,
    val memoryCacheKey: String
)

/** Metadata used by the global Coil listener without exposing a podcast title or network URL. */
internal data class PodcastCoverRequestTag(
    val diagnosticId: String,
    val pixelSize: Int
)

internal object PodcastCoverRequestFactory {
    fun persistentIdentity(
        rssUrl: String,
        thumbnailFileName: String?,
        thumbnailRevision: Long,
        size: PodcastCoverSize
    ): PersistentPodcastCoverIdentity? {
        val normalizedRssUrl = rssUrl.trim().takeIf(String::isNotEmpty) ?: return null
        val rssDigest = normalizedRssUrl.sha256Hex()
        val diagnosticId = rssDigest.take(DIAGNOSTIC_HASH_CHARS)
        val fileIdentity = thumbnailFileName?.trim()?.takeIf(String::isNotEmpty) ?: PENDING_FILE_IDENTITY
        return PersistentPodcastCoverIdentity(
            diagnosticId = diagnosticId,
            thumbnailRevision = thumbnailRevision,
            pixelSize = size.pixels,
            memoryCacheKey = "cover:$rssDigest:$thumbnailRevision:$fileIdentity:${size.pixels}"
        )
    }

    fun create(
        context: Context,
        podcast: Podcast,
        size: PodcastCoverSize
    ): ImageRequest? =
        create(
            context = context,
            rssUrl = podcast.rssUrl,
            sourceUrl = podcast.imageUrl,
            thumbnailFileName = podcast.coverFileName,
            thumbnailRevision = podcast.coverRevision,
            size = size
        )

    fun create(
        context: Context,
        rssUrl: String,
        sourceUrl: String,
        thumbnailFileName: String?,
        thumbnailRevision: Long,
        size: PodcastCoverSize
    ): ImageRequest? {
        val persistentIdentity = persistentIdentity(rssUrl, thumbnailFileName, thumbnailRevision, size)
        val normalizedRssUrl = rssUrl.trim()
        val normalizedSourceUrl = sourceUrl.trim().takeIf(String::isNotEmpty)
        if (persistentIdentity == null || normalizedSourceUrl == null) return null
        return ImageRequest.Builder(context)
            .data(
                PodcastCoverRequestData(
                    podcastRssUrl = normalizedRssUrl,
                    sourceUrl = normalizedSourceUrl,
                    thumbnailFileName = thumbnailFileName,
                    thumbnailRevision = thumbnailRevision
                )
            )
            .placeholder(R.drawable.ic_podcast_placeholder)
            .error(R.drawable.ic_podcast_placeholder)
            .fallback(R.drawable.ic_podcast_placeholder)
            .size(size.pixels)
            .precision(Precision.EXACT)
            .memoryCacheKey(persistentIdentity.memoryCacheKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.DISABLED)
            .tag(PodcastCoverRequestTag(persistentIdentity.diagnosticId, size.pixels))
            .build()
    }

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
            .placeholder(R.drawable.ic_podcast_placeholder)
            .error(R.drawable.ic_podcast_placeholder)
            .fallback(R.drawable.ic_podcast_placeholder)
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

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

private fun String.sha256Prefix(): String = sha256Hex().take(DIAGNOSTIC_HASH_CHARS)

private const val DIAGNOSTIC_HASH_CHARS = 12
private const val BYTE_MASK = 0xff
private const val PENDING_FILE_IDENTITY = "pending"
