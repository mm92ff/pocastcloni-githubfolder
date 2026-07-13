package com.example.pocastcloni.data.worker

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal interface PendingDownloadPublication {
    val path: String
    val totalBytes: Long

    fun makeVisible()

    fun cleanup()
}

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
                    "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DOWNLOAD_DIRECTORY"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val resolver = context.contentResolver
        val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
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
                val changed =
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                        null,
                        null
                    )
                if (changed != 1) throw IOException("Could not publish MediaStore download")
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
        const val PUBLIC_DOWNLOAD_DIRECTORY = "Pocastcloni"
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
    val attemptDirectory = File(targetDirectory, ".published/$attemptId")
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
    val attemptDirectory = File(targetDirectory, ".staging/$attemptId")
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

private fun moveReplacing(
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
    } catch (error: Throwable) {
        runCatching(cleanupTarget)
        throw error
    }
}

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
