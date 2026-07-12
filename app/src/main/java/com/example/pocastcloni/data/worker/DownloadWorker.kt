package com.example.pocastcloni.data.worker

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.StatisticsRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.util.ConnectivityProvider
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.requireApprovedPodcastResource
import com.example.pocastcloni.util.shouldUseLocalNetworkForResource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Named
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@HiltWorker
class DownloadWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val podcastRepository: PodcastRepository,
    private val statsRepo: StatisticsRepository,
    private val connectivityProvider: ConnectivityProvider,
    private val okHttpClient: OkHttpClient,
    @Named("LocalNetworkClient") private val localNetworkClient: OkHttpClient,
    private val userPreferencesRepository: UserPreferencesRepository,
) : CoroutineWorker(context, params) {
    companion object {
        private const val PROGRESS_MIN_INTERVAL_MS = 250L
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result {
        val guid = inputData.getString(Constants.DOWNLOAD_WORKER_GUID) ?: return Result.failure()
        val url = inputData.getString(Constants.DOWNLOAD_WORKER_URL) ?: return Result.failure()
        val fileName =
            inputData.getString(Constants.DOWNLOAD_WORKER_FILENAME)
                ?: Constants.DOWNLOAD_WORKER_DEFAULT_FILENAME

        val episode = podcastRepository.getEpisode(guid) ?: return Result.failure()
        val podcast = podcastRepository.getPodcastEntityByUrl(episode.podcastRssUrl)
        runCatching {
            requireApprovedPodcastResource(
                feedUrl = episode.podcastRssUrl,
                resourceUrl = url,
                allowInsecureHttp = podcast?.allowInsecureHttp == true,
                allowLocalNetwork = podcast?.allowLocalNetwork == true
            )
        }.onFailure {
            Timber.w(it, "Rejected unsafe download URL")
            return Result.failure()
        }

        return try {
            podcastRepository.updateDownloadStatus(guid, DownloadStatus.DOWNLOADING, null)
            setProgressAsync(workDataOf("progress" to 0f))

            val saveToDownloads = try {
                userPreferencesRepository.userSettingsFlow.first().saveToDownloadsFolder
            } catch (e: Exception) {
                Timber.w(e, "Could not read download location setting, using private storage")
                false
            }

            val downloadClient = if (
                shouldUseLocalNetworkForResource(
                    episode.podcastRssUrl,
                    url,
                    podcast?.allowLocalNetwork == true
                )
            ) {
                localNetworkClient
            } else {
                okHttpClient
            }

            val (storagePath, fileSize) =
                if (saveToDownloads && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    downloadViaMediaStore(url, fileName, downloadClient)
                } else {
                    val file = downloadToFile(url, fileName, saveToDownloads, downloadClient)
                    Pair(file.absolutePath, file.length())
                }

            if (fileSize > 0) {
                val isWifi = connectivityProvider.wifiStatus.value
                statsRepo.addDownloadBytes(fileSize, isWifi)
            }

            setProgressAsync(workDataOf("progress" to 1f))
            podcastRepository.updateDownloadStatus(guid, DownloadStatus.DOWNLOADED, storagePath)
            Result.success(workDataOf(Constants.DOWNLOAD_WORKER_OUTPUT_PATH to storagePath))
        } catch (e: CancellationException) {
            Timber.i("Download cancelled for %s", guid)
            podcastRepository.updateDownloadStatus(guid, DownloadStatus.NOT_DOWNLOADED, null)
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Download failed for $guid")
            if (shouldRetryDownloadFailure(e, runAttemptCount, MAX_RETRY_ATTEMPTS)) {
                podcastRepository.updateDownloadStatus(guid, DownloadStatus.QUEUED, null)
                Result.retry()
            } else {
                podcastRepository.updateDownloadStatus(guid, DownloadStatus.FAILED, null)
                Result.failure()
            }
        }
    }

    // API 29+: writes to the public Downloads folder via MediaStore.
    // Stores a content:// URI string in the DB instead of a file path.
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun downloadViaMediaStore(
        url: String,
        fileName: String,
        client: OkHttpClient
    ): Pair<String, Long> {
        val sanitizedFileName = sanitizeFileName(fileName)

        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            throw IOException("External storage not mounted, cannot save to Downloads folder")
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, sanitizedFileName)
            put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Downloads.IS_PENDING, 1) // hidden until download completes
        }
        val itemUri = applicationContext.contentResolver
            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore.insert failed for $sanitizedFileName")

        return runWithCleanupOnFailure(
            cleanup = { applicationContext.contentResolver.delete(itemUri, null, null) }
        ) {
            val output = applicationContext.contentResolver.openOutputStream(itemUri)
                ?: throw IOException("Cannot open OutputStream for MediaStore URI")
            val bytesCopied = try {
                performDownload(
                    url = url,
                    outputStream = output,
                    availableBytes = ::externalDownloadsAvailableBytes,
                    client = client
                )
            } catch (e: Exception) {
                // Close stream if performDownload threw before its internal .use {} could close it
                runCatching { output.close() }
                throw e
            }

            // Mark file as visible in the Downloads folder
            applicationContext.contentResolver.update(
                itemUri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null, null
            )
            Pair(itemUri.toString(), bytesCopied)
        }
    }

    // Private storage or API < 29 public Downloads via file path.
    private suspend fun downloadToFile(
        url: String,
        fileName: String,
        saveToDownloads: Boolean,
        client: OkHttpClient
    ): File {
        val dir = resolveDownloadDirectory(saveToDownloads)
        val file = File(dir, sanitizeFileName(fileName))
        return runWithCleanupOnFailure(cleanup = { file.delete() }) {
            performDownload(
                url = url,
                outputStream = FileOutputStream(file),
                availableBytes = { dir.usableSpace },
                client = client
            )
            file
        }
    }

    // Core download loop shared by both storage paths.
    private suspend fun performDownload(
        url: String,
        outputStream: OutputStream,
        availableBytes: () -> Long,
        client: OkHttpClient
    ): Long {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                throw DownloadHttpException(response.code)
            }
            val body = response.body ?: throw IOException("Response body is null")
            val totalBytes = body.contentLength()
            if (exceedsDownloadLimit(totalBytes)) {
                throw DownloadSizeLimitException()
            }
            if (totalBytes >= 0L) {
                ensureAvailableStorage(availableBytes(), totalBytes)
            } else {
                ensureAvailableStorage(
                    availableBytes(),
                    Constants.SecurityLimits.STORAGE_RECHECK_INTERVAL_BYTES
                )
            }

            Timber.d("Expected size: $totalBytes Bytes")

            var bytesCopied = 0L
            var nextStorageCheckAt = Constants.SecurityLimits.STORAGE_RECHECK_INTERVAL_BYTES
            var lastWrittenPercent = 0
            var lastWriteAtMs = 0L

            outputStream.use { output ->
                val input: InputStream = body.byteStream()
                val buffer = ByteArray(8 * 1024)

                while (true) {
                    if (isStopped) throw CancellationException("Worker stopped")
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    ensureDownloadChunkWithinLimit(bytesCopied, bytesRead)
                    if (bytesCopied + bytesRead >= nextStorageCheckAt) {
                        val remainingBytes = if (totalBytes >= 0L) {
                            (totalBytes - bytesCopied).coerceAtLeast(bytesRead.toLong())
                        } else {
                            Constants.SecurityLimits.STORAGE_RECHECK_INTERVAL_BYTES
                        }
                        ensureAvailableStorage(availableBytes(), remainingBytes)
                        nextStorageCheckAt = bytesCopied + bytesRead +
                            Constants.SecurityLimits.STORAGE_RECHECK_INTERVAL_BYTES
                    }
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead

                    if (totalBytes > 0L) {
                        val percent =
                            ((bytesCopied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        val now = SystemClock.elapsedRealtime()
                        val force = percent >= 100
                        val timeOk = (now - lastWriteAtMs) >= PROGRESS_MIN_INTERVAL_MS
                        if ((percent > lastWrittenPercent && timeOk) || force) {
                            setProgressAsync(workDataOf("progress" to (percent / 100f)))
                            lastWrittenPercent = percent
                            lastWriteAtMs = now
                        }
                    }
                }
                output.flush()
            }

            if (totalBytes > 0L && bytesCopied != totalBytes) {
                val msg = "Download incomplete! Expected: $totalBytes, Got: $bytesCopied"
                Timber.e(msg)
                throw IOException(msg)
            }

            return bytesCopied
        } finally {
            response.close()
        }
    }

    // Resolves the target directory for private storage or API < 29 public Downloads.
    // API 29+ public Downloads is handled separately via MediaStore.
    private fun resolveDownloadDirectory(saveToDownloads: Boolean): File {
        if (!saveToDownloads) {
            val dir = File(applicationContext.filesDir, Constants.DOWNLOADS_DIR)
            dir.mkdirs()
            return dir
        }

        // API < 29: public Downloads with WRITE_EXTERNAL_STORAGE permission
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        return if (ContextCompat.checkSelfPermission(
                applicationContext, permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val publicDownloads =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(publicDownloads, "Pocastcloni")
            appDir.mkdirs()
            appDir
        } else {
            Timber.w("WRITE_EXTERNAL_STORAGE not granted, falling back to private storage")
            File(applicationContext.filesDir, Constants.DOWNLOADS_DIR).also { it.mkdirs() }
        }
    }

    private fun externalDownloadsAvailableBytes(): Long =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).usableSpace

    private fun sanitizeFileName(fileName: String): String =
        fileName
            .replace("..", "")
            .replace("/", "")
            .replace("\\", "")
            .replace(" ", "_")
            .trim()
            .ifEmpty { "episode_download" }
}

internal fun exceedsDownloadLimit(bytes: Long): Boolean =
    bytes > Constants.SecurityLimits.MAX_DOWNLOAD_BYTES

internal fun ensureDownloadChunkWithinLimit(
    bytesCopied: Long,
    nextChunkBytes: Int
) {
    if (nextChunkBytes < 0 || bytesCopied > Constants.SecurityLimits.MAX_DOWNLOAD_BYTES - nextChunkBytes) {
        throw DownloadSizeLimitException()
    }
}

internal class DownloadSizeLimitException : IOException("Download exceeds the maximum allowed size")

internal fun ensureAvailableStorage(
    availableBytes: Long,
    requiredBytes: Long
) {
    val reserve = Constants.SecurityLimits.MIN_FREE_STORAGE_RESERVE_BYTES
    if (availableBytes < reserve || requiredBytes < 0L || requiredBytes > availableBytes - reserve) {
        throw DownloadStorageException()
    }
}

internal class DownloadStorageException : IOException("Not enough free storage for download")

internal suspend fun <T> runWithCleanupOnFailure(
    cleanup: () -> Unit,
    block: suspend () -> T
): T = try {
    block()
} catch (error: Throwable) {
    runCatching(cleanup)
    throw error
}

internal class DownloadHttpException(
    val statusCode: Int
) : IOException("Server responded with error: $statusCode")

internal fun shouldRetryDownloadFailure(
    error: Throwable,
    runAttemptCount: Int,
    maxRetryAttempts: Int
): Boolean {
    if (runAttemptCount >= maxRetryAttempts) return false
    return when (error) {
        is DownloadSizeLimitException, is DownloadStorageException -> false
        is DownloadHttpException ->
            error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500
        is IOException -> true
        else -> false
    }
}
