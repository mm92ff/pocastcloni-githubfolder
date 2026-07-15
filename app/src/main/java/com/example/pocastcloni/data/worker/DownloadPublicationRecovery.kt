package com.example.pocastcloni.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.pocastcloni.util.Constants
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class DownloadPublicationRecord(
    val episodeId: Long,
    val path: String
)

internal data class PendingMediaStorePublication(
    val path: String,
    val sizeBytes: Long
)

internal interface PendingMediaStoreRecovery {
    fun pendingPublications(): List<PendingMediaStorePublication>

    fun isReadable(path: String): Boolean

    fun publish(path: String): Boolean

    fun delete(path: String): Boolean
}

/**
 * Reconstructs publication state from downloaded database paths and app-owned attempt namespaces.
 *
 * No journal is needed: private targets live under `.published/<attempt-id>`, legacy copies under
 * `.staging/<attempt-id>`, and MediaStore rows are selected by owner package plus the app's exact
 * relative path. Readable database targets win. A missing legacy target is completed only when one
 * attempt can own it unambiguously; otherwise [resetDownload] compare-and-sets the exact status and
 * path before recovery removes its artifacts. Repetition is safe because completed publications
 * leave a readable target and cleanup never leaves the app-owned namespaces.
 */
internal class DownloadPublicationRecovery(
    private val privateDownloadsDirectory: File,
    private val legacyDownloadsDirectory: File,
    private val pendingMediaStore: PendingMediaStoreRecovery?
) {
    suspend fun recover(
        records: List<DownloadPublicationRecord>,
        resetDownload: suspend (episodeId: Long, expectedPath: String) -> Boolean
    ): Int {
        val recordsByPath = records.groupBy(DownloadPublicationRecord::path)
        var resetRows = recoverPrivateAttempts(recordsByPath, resetDownload)
        resetRows += recoverLegacyAttempts(recordsByPath, resetDownload)
        resetRows += recoverPendingMediaStore(recordsByPath, resetDownload)
        return resetRows
    }

    private suspend fun recoverPrivateAttempts(
        recordsByPath: Map<String, List<DownloadPublicationRecord>>,
        resetDownload: suspend (episodeId: Long, expectedPath: String) -> Boolean
    ): Int {
        var resetRows = 0
        attemptDirectories(privateDownloadsDirectory, PRIVATE_PUBLICATION_ATTEMPT_DIRECTORY)
            .forEach { attemptDirectory ->
                resetRows += recoverPrivateAttemptDirectory(attemptDirectory, recordsByPath, resetDownload)
            }
        deleteIfEmpty(File(privateDownloadsDirectory, PRIVATE_PUBLICATION_ATTEMPT_DIRECTORY))
        return resetRows
    }

    private suspend fun recoverPrivateAttemptDirectory(
        attemptDirectory: File,
        recordsByPath: Map<String, List<DownloadPublicationRecord>>,
        resetDownload: suspend (episodeId: Long, expectedPath: String) -> Boolean
    ): Int {
        var resetRows = 0
        attemptFiles(attemptDirectory).forEach { target ->
            val path = target.absolutePath
            val records = recordsByPath[path]
            when {
                records == null -> target.delete()
                isReadableFile(target) -> Unit
                else -> {
                    val reset = resetRecords(records, path, resetDownload)
                    resetRows += reset.count
                    if (reset.allReset) target.delete()
                }
            }
        }
        deleteIfEmpty(attemptDirectory)
        return resetRows
    }

    private suspend fun recoverLegacyAttempts(
        recordsByPath: Map<String, List<DownloadPublicationRecord>>,
        resetDownload: suspend (episodeId: Long, expectedPath: String) -> Boolean
    ): Int {
        val attempts =
            attemptDirectories(legacyDownloadsDirectory, LEGACY_PUBLICATION_ATTEMPT_DIRECTORY)
                .flatMap { directory -> attemptFiles(directory).map { LegacyAttempt(it, directory) } }
        val attemptsByTarget = attempts.groupBy { File(legacyDownloadsDirectory, it.pendingFile.name).absolutePath }
        val databaseTargets = recordsByPath.filterKeys(::isDirectLegacyTarget)
        var resetRows = 0

        databaseTargets.forEach { (path, records) ->
            resetRows += recoverLegacyTarget(path, records, attemptsByTarget[path].orEmpty(), resetDownload)
        }

        attemptsByTarget
            .filterKeys { it !in databaseTargets }
            .values
            .flatten()
            .forEach(::deleteLegacyAttempt)
        attemptDirectories(legacyDownloadsDirectory, LEGACY_PUBLICATION_ATTEMPT_DIRECTORY)
            .forEach(::deleteIfEmpty)
        deleteIfEmpty(File(legacyDownloadsDirectory, LEGACY_PUBLICATION_ATTEMPT_DIRECTORY))
        return resetRows
    }

    private suspend fun recoverLegacyTarget(
        path: String,
        records: List<DownloadPublicationRecord>,
        matchingAttempts: List<LegacyAttempt>,
        resetDownload: suspend (episodeId: Long, expectedPath: String) -> Boolean
    ): Int {
        val target = File(path)
        val candidate = matchingAttempts.singleOrNull()?.takeIf { isReadableFile(it.pendingFile) }
        return when {
            isReadableFile(target) -> {
                matchingAttempts.forEach(::deleteLegacyAttempt)
                0
            }
            candidate != null && moveLegacyAttempt(candidate, target) -> {
                matchingAttempts.forEach(::deleteLegacyAttempt)
                0
            }
            else -> {
                val reset = resetRecords(records, path, resetDownload)
                if (reset.allReset) matchingAttempts.forEach(::deleteLegacyAttempt)
                reset.count
            }
        }
    }

    private fun moveLegacyAttempt(
        attempt: LegacyAttempt,
        target: File
    ): Boolean {
        var moved = false
        val recovered =
            runCatching {
                moveReplacing(attempt.pendingFile, target)
                moved = true
                isReadableFile(target)
            }.getOrDefault(false)
        if (!recovered && moved) target.delete()
        return recovered
    }

    private suspend fun recoverPendingMediaStore(
        recordsByPath: Map<String, List<DownloadPublicationRecord>>,
        resetDownload: suspend (episodeId: Long, expectedPath: String) -> Boolean
    ): Int {
        val mediaStore = pendingMediaStore ?: return 0
        var resetRows = 0
        mediaStore.pendingPublications().forEach { pending ->
            val records = recordsByPath[pending.path]
            if (records == null) {
                mediaStore.delete(pending.path)
                return@forEach
            }
            val published =
                pending.sizeBytes > 0L &&
                    mediaStore.isReadable(pending.path) &&
                    runCatching {
                        mediaStore.publish(pending.path) && mediaStore.isReadable(pending.path)
                    }.getOrDefault(false)
            if (!published) {
                val reset = resetRecords(records, pending.path, resetDownload)
                resetRows += reset.count
                if (reset.allReset) runCatching { mediaStore.delete(pending.path) }
            }
        }
        return resetRows
    }

    private fun isDirectLegacyTarget(path: String): Boolean {
        val file = File(path).absoluteFile
        return file.parentFile?.absolutePath == legacyDownloadsDirectory.absoluteFile.absolutePath &&
            file.name != LEGACY_PUBLICATION_ATTEMPT_DIRECTORY
    }

    private suspend fun resetRecords(
        records: List<DownloadPublicationRecord>,
        path: String,
        resetDownload: suspend (episodeId: Long, expectedPath: String) -> Boolean
    ): ResetResult {
        var count = 0
        records.forEach { record ->
            if (resetDownload(record.episodeId, path)) count++
        }
        return ResetResult(count, count == records.size)
    }

    private data class LegacyAttempt(
        val pendingFile: File,
        val directory: File
    )

    private data class ResetResult(
        val count: Int,
        val allReset: Boolean
    )

    private fun deleteLegacyAttempt(attempt: LegacyAttempt) {
        attempt.pendingFile.delete()
        deleteIfEmpty(attempt.directory)
    }

    companion object {
        fun from(context: Context): DownloadPublicationRecovery =
            DownloadPublicationRecovery(
                privateDownloadsDirectory = File(context.filesDir, Constants.DOWNLOADS_DIR),
                legacyDownloadsDirectory =
                    File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        PUBLIC_DOWNLOAD_DIRECTORY
                    ),
                pendingMediaStore =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        AndroidPendingMediaStoreRecovery(context)
                    } else {
                        null
                    }
            )
    }
}

private fun attemptDirectories(
    downloadsDirectory: File,
    namespace: String
): List<File> =
    File(downloadsDirectory, namespace)
        .listFiles()
        .orEmpty()
        .filter { candidate -> candidate.isDirectory && runCatching { UUID.fromString(candidate.name) }.isSuccess }

private fun attemptFiles(directory: File): List<File> =
    directory.listFiles().orEmpty().filter { it.isFile }

private fun isReadableFile(file: File): Boolean = file.isFile && file.canRead() && file.length() > 0L

private fun deleteIfEmpty(directory: File) {
    if (directory.list().isNullOrEmpty()) directory.delete()
}

@RequiresApi(Build.VERSION_CODES.Q)
private class AndroidPendingMediaStoreRecovery(
    context: Context
) : PendingMediaStoreRecovery {
    private val resolver: ContentResolver = context.contentResolver
    private val ownerPackageName: String = context.packageName

    override fun pendingPublications(): List<PendingMediaStorePublication> {
        val publications = mutableListOf<PendingMediaStorePublication>()
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DOWNLOAD_DIRECTORY/"
        val legacyRelativePath = relativePath.removeSuffix("/")
        val cursor =
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE),
                "${MediaStore.Downloads.IS_PENDING} = ? AND " +
                    "${MediaStore.Downloads.OWNER_PACKAGE_NAME} = ? AND " +
                    "(${MediaStore.Downloads.RELATIVE_PATH} = ? OR ${MediaStore.Downloads.RELATIVE_PATH} = ?)",
                arrayOf("1", ownerPackageName, relativePath, legacyRelativePath),
                null
            ) ?: throw IOException("Could not query pending MediaStore downloads")
        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            while (it.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, it.getLong(idColumn))
                publications += PendingMediaStorePublication(uri.toString(), it.getLong(sizeColumn))
            }
        }
        return publications
    }

    override fun isReadable(path: String): Boolean =
        runCatching {
            resolver.openFileDescriptor(Uri.parse(path), "r")?.use { descriptor ->
                descriptor.statSize != 0L
            } ?: false
        }.getOrDefault(false)

    override fun publish(path: String): Boolean =
        resolver.update(
            Uri.parse(path),
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null
        ) == 1

    override fun delete(path: String): Boolean = resolver.delete(Uri.parse(path), null, null) == 1
}
