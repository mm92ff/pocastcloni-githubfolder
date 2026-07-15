package com.example.pocastcloni.data.worker

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal const val PRIVATE_PUBLICATION_ATTEMPT_DIRECTORY = ".published"
internal const val LEGACY_PUBLICATION_ATTEMPT_DIRECTORY = ".staging"
internal const val PUBLIC_DOWNLOAD_DIRECTORY = "Pocastcloni"

/**
 * Owns a prepared target until its database row and external visibility agree.
 *
 * A publication starts hidden or attempt-scoped. [makeVisible] is called only after the exact
 * download row has accepted [path]. Until then, or after a failed commit, the caller owns
 * [cleanup]. Implementations must keep cleanup scoped to the target created by this attempt.
 */
internal interface PendingDownloadPublication {
    val path: String
    val totalBytes: Long

    fun makeVisible()

    fun cleanup()
}

/**
 * Serializes process-local publication transitions with startup recovery.
 *
 * The gate is intentionally independent from queue and cancellation state. Callers hold it only
 * while preparing a completed transfer, compare-and-setting its database row, and publishing it,
 * or while reconciling those same artifacts after startup.
 */
internal object DownloadPublicationGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}

/** Prepares attempt-scoped targets that startup recovery can identify after process death. */
internal class DownloadPublisher(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun prepare(
        stagedFile: File,
        fileName: String,
        mimeType: String,
        saveToPublicDownloads: Boolean,
        attemptId: UUID
    ): PendingDownloadPublication = withContext(ioDispatcher) {
        require(stagedFile.isFile) { "Download staging file is missing" }
        when {
            saveToPublicDownloads && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                prepareMediaStore(stagedFile, fileName, mimeType)
            saveToPublicDownloads && canWriteLegacyPublicDownloads() ->
                prepareLegacyPublicFilePublication(
                    stagedFile,
                    legacyPublicDownloadsDirectory(),
                    fileName,
                    attemptId
                )
            else ->
                preparePrivateFilePublication(
                    stagedFile,
                    privateDownloadsDirectory(),
                    fileName,
                    attemptId
                )
        }
    }

    @Suppress("ThrowsCount", "TooGenericExceptionCaught")
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun prepareMediaStore(
        stagedFile: File,
        fileName: String,
        mimeType: String
    ): PendingDownloadPublication {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            throw DownloadStorageException()
        }
        ensureAvailableStorage(
            availableBytes = externalDownloadsAvailableBytes(),
            requiredBytes = stagedFile.length()
        )
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { DEFAULT_MIME_TYPE })
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DOWNLOAD_DIRECTORY/"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val resolver = context.contentResolver
        val uri =
            resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
            )
                ?: throw IOException("MediaStore insert failed")

        copyStagedDownload(
            stagedFile = stagedFile,
            openOutput = {
                resolver.openOutputStream(uri, "w")
                    ?: throw IOException("Could not open MediaStore output")
            },
            cleanupTarget = { resolver.delete(uri, null, null) }
        )

        return MediaStoreDownloadPublication(
            path = uri.toString(),
            totalBytes = stagedFile.length(),
            makeVisibleAction = {
                if (!MediaStorePendingPublisher.publish(resolver, uri)) {
                    throw IOException("Could not publish MediaStore download")
                }
            },
            cleanupAction = { resolver.delete(uri, null, null) }
        )
    }

    private fun privateDownloadsDirectory(): File = File(context.filesDir, Constants.DOWNLOADS_DIR)

    private fun legacyPublicDownloadsDirectory(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            PUBLIC_DOWNLOAD_DIRECTORY
        )

    private fun canWriteLegacyPublicDownloads(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    private fun externalDownloadsAvailableBytes(): Long =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).usableSpace

    private companion object {
        const val DEFAULT_MIME_TYPE = "audio/mpeg"
    }
}

internal class MediaStoreDownloadPublication(
    override val path: String,
    override val totalBytes: Long,
    private val makeVisibleAction: () -> Unit,
    private val cleanupAction: () -> Unit
) : PendingDownloadPublication {
    override fun makeVisible() = makeVisibleAction()

    override fun cleanup() = cleanupAction()
}

internal fun preparePrivateFilePublication(
    stagedFile: File,
    targetDirectory: File,
    fileName: String,
    attemptId: UUID
): PendingDownloadPublication {
    val attemptDirectory = File(targetDirectory, "$PRIVATE_PUBLICATION_ATTEMPT_DIRECTORY/$attemptId")
    ensureDirectoryExists(attemptDirectory)
    val target = File(attemptDirectory, fileName)
    moveReplacing(stagedFile, target)
    return PrivateFilePublication(target, attemptDirectory)
}

internal suspend fun prepareLegacyPublicFilePublication(
    stagedFile: File,
    targetDirectory: File,
    fileName: String,
    attemptId: UUID
): PendingDownloadPublication {
    ensureDirectoryExists(targetDirectory)
    ensureAvailableStorage(targetDirectory.usableSpace, stagedFile.length())
    val attemptDirectory = File(targetDirectory, "$LEGACY_PUBLICATION_ATTEMPT_DIRECTORY/$attemptId")
    ensureDirectoryExists(attemptDirectory)
    val pendingTarget = File(attemptDirectory, fileName)
    copyStagedDownload(
        stagedFile = stagedFile,
        openOutput = { pendingTarget.outputStream() },
        cleanupTarget = { pendingTarget.delete() }
    )
    return LegacyPublicFilePublication(
        pendingTarget = pendingTarget,
        target = File(targetDirectory, fileName),
        attemptDirectory = attemptDirectory,
        attemptId = attemptId
    )
}

private class PrivateFilePublication(
    private val target: File,
    private val attemptDirectory: File
) : PendingDownloadPublication {
    override val path: String = target.absolutePath
    override val totalBytes: Long = target.length()

    override fun makeVisible() = Unit

    override fun cleanup() {
        target.delete()
        attemptDirectory.delete()
    }
}

private class LegacyPublicFilePublication(
    private val pendingTarget: File,
    private val target: File,
    private val attemptDirectory: File,
    private val attemptId: UUID
) : PendingDownloadPublication {
    override val path: String = target.absolutePath
    override val totalBytes: Long = pendingTarget.length()

    override fun makeVisible() {
        PublishedFileOwnership.publish(pendingTarget, target, attemptId)
        attemptDirectory.delete()
    }

    override fun cleanup() {
        pendingTarget.delete()
        attemptDirectory.delete()
        PublishedFileOwnership.cleanup(target, attemptId)
    }
}

private object PublishedFileOwnership {
    private val lock = Any()
    private val owners = mutableMapOf<String, UUID>()

    fun publish(
        pendingTarget: File,
        target: File,
        attemptId: UUID
    ) {
        synchronized(lock) {
            moveReplacing(pendingTarget, target)
            owners[target.absolutePath] = attemptId
        }
    }

    fun cleanup(
        target: File,
        attemptId: UUID
    ) {
        synchronized(lock) {
            if (owners[target.absolutePath] == attemptId) {
                target.delete()
                owners.remove(target.absolutePath)
            }
        }
    }
}

private fun ensureDirectoryExists(directory: File) {
    if (!directory.exists() && !directory.mkdirs()) {
        throw IOException("Could not create download directory")
    }
}

internal fun moveReplacing(
    source: File,
    target: File
) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

@Suppress("TooGenericExceptionCaught")
internal suspend fun copyStagedDownload(
    stagedFile: File,
    openOutput: () -> OutputStream,
    cleanupTarget: () -> Unit,
    onChunkCopied: suspend () -> Unit = {}
) {
    try {
        currentCoroutineContext().ensureActive()
        openOutput().use { output ->
            copyStagedBytes(stagedFile, output, onChunkCopied)
        }
    } catch (error: Throwable) {
        runCatching(cleanupTarget)
        throw error
    }
}

private suspend fun copyStagedBytes(
    stagedFile: File,
    output: OutputStream,
    onChunkCopied: suspend () -> Unit
) {
    stagedFile.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break
            output.write(buffer, 0, bytesRead)
            onChunkCopied()
            currentCoroutineContext().ensureActive()
        }
    }
    output.flush()
}

/**
 * Commits the database path before making the prepared target visible.
 *
 * If visibility fails after the compare-and-set, compensation resets only the row that still owns
 * this exact path. Cleanup remains attempt-scoped and may be repeated by startup recovery.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun commitDownloadPublication(
    publication: PendingDownloadPublication,
    commitDatabase: suspend (path: String) -> Boolean,
    compensateDatabase: suspend (path: String) -> Unit
) {
    var databaseCommitted = false
    try {
        if (!commitDatabase(publication.path)) throw StaleDownloadWorkerException()
        databaseCommitted = true
        publication.makeVisible()
    } catch (error: Throwable) {
        if (databaseCommitted) runCatching { compensateDatabase(publication.path) }
        runCatching { publication.cleanup() }
        throw error
    }
}

internal class StaleDownloadWorkerException : IOException("Download state changed while work was running")
