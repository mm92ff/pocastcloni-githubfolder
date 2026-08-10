package com.example.pocastcloni.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.example.pocastcloni.di.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class PublishedPodcastCover(
    val fileName: String,
    val contentSha256: String,
    val createdNewFile: Boolean
)

class InvalidPodcastCoverException(message: String) : IOException(message)

/** Owns bounded, durable podcast thumbnails and never accepts caller-controlled paths. */
@Singleton
@Suppress("TooManyFunctions")
class PodcastCoverThumbnailStore
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) {
    private val directory: File
        get() = context.noBackupFilesDir.resolve(COVER_DIRECTORY)

    suspend fun publish(
        podcastRssUrl: String,
        input: InputStream
    ): PublishedPodcastCover =
        withContext(dispatcherProvider.io) {
            ensureDirectory()
            val sourceFile = attemptFile(podcastRssUrl, SOURCE_ATTEMPT_SUFFIX)
            try {
                copyBounded(input, sourceFile)
                publishSourceFile(podcastRssUrl, sourceFile)
            } finally {
                sourceFile.delete()
            }
        }

    suspend fun publishFile(
        podcastRssUrl: String,
        source: File
    ): PublishedPodcastCover =
        FileInputStream(source).use { input -> publish(podcastRssUrl, input) }

    suspend fun validFile(fileName: String?): File? =
        withContext(dispatcherProvider.io) { validFileBlocking(fileName) }

    suspend fun validFileForPodcast(
        podcastRssUrl: String,
        fileName: String?
    ): File? =
        withContext(dispatcherProvider.io) {
            validFileForPodcastBlocking(podcastRssUrl, fileName)
        }

    fun validFileForPodcastBlocking(
        podcastRssUrl: String,
        fileName: String?
    ): File? {
        if (!isOwnedByPodcast(podcastRssUrl, fileName)) return null
        return validFileBlocking(fileName)
    }

    @Suppress("ReturnCount")
    fun validFileBlocking(fileName: String?): File? {
        if (fileName == null || !FINAL_FILE_PATTERN.matches(fileName)) return null
        val root = ensureDirectory().canonicalFile
        val candidate = root.resolve(fileName).canonicalFile
        if (candidate.parentFile != root || !candidate.isFile) return null
        if (candidate.length() !in 1L..MAX_ENCODED_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(candidate.absolutePath, bounds)
        return candidate.takeIf {
            bounds.outWidth in 1..MAX_THUMBNAIL_EDGE &&
                bounds.outHeight in 1..MAX_THUMBNAIL_EDGE
        }
    }

    suspend fun readArtworkBytes(fileName: String?): ByteArray? =
        withContext(dispatcherProvider.io) {
            validFileBlocking(fileName)?.takeIf { it.length() <= MAX_ARTWORK_BYTES }?.readBytes()
        }

    suspend fun readArtworkBytes(
        podcastRssUrl: String,
        fileName: String?
    ): ByteArray? =
        withContext(dispatcherProvider.io) {
            validFileForPodcastBlocking(podcastRssUrl, fileName)
                ?.takeIf { it.length() <= MAX_ARTWORK_BYTES }
                ?.readBytes()
        }

    suspend fun deleteFile(fileName: String?) =
        withContext(dispatcherProvider.io) {
            validOwnedFile(fileName)?.delete()
        }

    suspend fun deletePodcastFiles(podcastRssUrl: String) =
        withContext(dispatcherProvider.io) {
            val prefix = "${podcastRssUrl.sha256().take(PODCAST_HASH_CHARS)}-"
            ensureDirectory().listFiles().orEmpty()
                .filter { file -> file.name.startsWith(prefix) }
                .forEach(File::delete)
        }

    suspend fun reconcile(activeFileNames: Set<String>) =
        withContext(dispatcherProvider.io) {
            val active = activeFileNames.filter(FINAL_FILE_PATTERN::matches).toSet()
            ensureDirectory().listFiles().orEmpty().forEach { file ->
                val isAttempt = file.name.endsWith(ATTEMPT_SUFFIX)
                val isOrphan = FINAL_FILE_PATTERN.matches(file.name) && file.name !in active
                val isUnexpected = !FINAL_FILE_PATTERN.matches(file.name) && !isAttempt
                if (isAttempt || isOrphan || isUnexpected) file.delete()
            }
        }

    suspend fun clearAll() =
        withContext(dispatcherProvider.io) {
            val root = directory
            if (root.exists() && !root.deleteRecursively()) {
                throw IOException("Failed to clear persistent podcast covers")
            }
        }

    @Suppress("NestedBlockDepth", "ThrowsCount")
    private suspend fun publishSourceFile(
        podcastRssUrl: String,
        sourceFile: File
    ): PublishedPodcastCover {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        validateSourceDimensions(bounds.outWidth, bounds.outHeight)

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val bitmap =
            BitmapFactory.decodeFile(
                sourceFile.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: throw InvalidPodcastCoverException("Podcast cover could not be decoded")
        coroutineContext.ensureActive()

        val scaled = scaleDown(bitmap)
        if (scaled !== bitmap) bitmap.recycle()
        val encodedAttempt = attemptFile(podcastRssUrl, IMAGE_ATTEMPT_SUFFIX)
        try {
            try {
                FileOutputStream(encodedAttempt).use { output ->
                    val format =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                    if (!scaled.compress(format, WEBP_QUALITY, output)) {
                        throw InvalidPodcastCoverException("Podcast cover could not be encoded")
                    }
                    output.fd.sync()
                }
            } finally {
                scaled.recycle()
            }
            coroutineContext.ensureActive()
            if (encodedAttempt.length() !in 1L..MAX_ENCODED_BYTES) {
                throw InvalidPodcastCoverException("Encoded podcast cover exceeds the size limit")
            }

            val contentHash = encodedAttempt.sha256()
            val podcastHash = podcastRssUrl.sha256().take(PODCAST_HASH_CHARS)
            val finalFile = ensureDirectory().resolve("$podcastHash-$contentHash.webp")
            if (validFileBlocking(finalFile.name) != null) {
                return PublishedPodcastCover(finalFile.name, contentHash, createdNewFile = false)
            }
            if (finalFile.exists() && !finalFile.delete()) {
                throw IOException("Invalid podcast cover could not be replaced")
            }
            publishAtomically(encodedAttempt, finalFile)
            if (validFileBlocking(finalFile.name) == null) {
                finalFile.delete()
                throw InvalidPodcastCoverException("Published podcast cover failed validation")
            }
            return PublishedPodcastCover(finalFile.name, contentHash, createdNewFile = true)
        } finally {
            encodedAttempt.delete()
        }
    }

    private suspend fun copyBounded(
        input: InputStream,
        destination: File
    ) {
        var copied = 0L
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                copied += count
                if (copied > MAX_SOURCE_BYTES) {
                    throw InvalidPodcastCoverException("Podcast cover exceeds the source size limit")
                }
                output.write(buffer, 0, count)
            }
            output.fd.sync()
        }
        if (copied == 0L) throw InvalidPodcastCoverException("Podcast cover response was empty")
    }

    private fun validateSourceDimensions(
        width: Int,
        height: Int
    ) {
        val pixels = width.toLong() * height.toLong()
        val invalidDimensions =
            width <= 0 ||
                height <= 0 ||
                width > MAX_SOURCE_EDGE ||
                height > MAX_SOURCE_EDGE ||
                pixels > MAX_SOURCE_PIXELS
        if (invalidDimensions) {
            throw InvalidPodcastCoverException("Podcast cover dimensions are invalid")
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int
    ): Int {
        var sample = 1
        while (maxOf(width, height) / sample > MAX_THUMBNAIL_EDGE * 2) sample *= 2
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val largestEdge = maxOf(bitmap.width, bitmap.height)
        if (largestEdge <= MAX_THUMBNAIL_EDGE) return bitmap
        val ratio = MAX_THUMBNAIL_EDGE.toFloat() / largestEdge.toFloat()
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun publishAtomically(
        attempt: File,
        target: File
    ) {
        try {
            Files.move(attempt.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            if (!attempt.renameTo(target)) throw IOException("Podcast cover could not be published atomically")
        }
    }

    private fun attemptFile(
        podcastRssUrl: String,
        suffix: String
    ): File {
        val podcastHash = podcastRssUrl.sha256().take(PODCAST_HASH_CHARS)
        return ensureDirectory().resolve("$podcastHash-${UUID.randomUUID()}$suffix")
    }

    private fun validOwnedFile(fileName: String?): File? {
        if (fileName == null || !FINAL_FILE_PATTERN.matches(fileName)) return null
        val root = ensureDirectory().canonicalFile
        return root.resolve(fileName).canonicalFile.takeIf { it.parentFile == root }
    }

    private fun isOwnedByPodcast(
        podcastRssUrl: String,
        fileName: String?
    ): Boolean {
        if (fileName == null || !FINAL_FILE_PATTERN.matches(fileName)) return false
        val podcastHash = podcastRssUrl.sha256().take(PODCAST_HASH_CHARS)
        return fileName.startsWith("$podcastHash-")
    }

    private fun ensureDirectory(): File = directory.apply {
        if (!exists() && !mkdirs()) throw IOException("Persistent podcast cover directory is unavailable")
        if (!isDirectory) throw IOException("Persistent podcast cover path is not a directory")
    }

    private fun File.sha256(): String = inputStream().use { input -> input.sha256() }

    private fun String.sha256(): String = byteInputStream(Charsets.UTF_8).use { input -> input.sha256() }

    private fun InputStream.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val count = read(buffer)
            if (count == -1) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
    }

    companion object {
        const val COVER_DIRECTORY = "podcast_covers"
        const val MAX_THUMBNAIL_EDGE = 400
        const val MAX_SOURCE_BYTES = 20L * 1024L * 1024L
        const val MAX_ENCODED_BYTES = 2L * 1024L * 1024L
        const val MAX_ARTWORK_BYTES = MAX_ENCODED_BYTES
        private const val MAX_SOURCE_EDGE = 20_000
        private const val MAX_SOURCE_PIXELS = 100_000_000L
        private const val WEBP_QUALITY = 82
        private const val COPY_BUFFER_BYTES = 16 * 1024
        private const val PODCAST_HASH_CHARS = 32
        private const val BYTE_MASK = 0xff
        private const val ATTEMPT_SUFFIX = ".tmp"
        private const val SOURCE_ATTEMPT_SUFFIX = ".source.tmp"
        private const val IMAGE_ATTEMPT_SUFFIX = ".image.tmp"
        private val FINAL_FILE_PATTERN = Regex("[a-f0-9]{32}-[a-f0-9]{64}\\.webp")
    }
}
