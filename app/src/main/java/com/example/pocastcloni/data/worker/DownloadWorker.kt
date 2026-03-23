package com.example.pocastcloni.data.worker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

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
    private val userPreferencesRepository: UserPreferencesRepository,
) : CoroutineWorker(context, params) {
    companion object {
        // We can now update even more frequently since we no longer write to the DB!
        private const val PROGRESS_MIN_INTERVAL_MS = 250L // Was 750L
    }

    override suspend fun doWork(): Result {
        val guid = inputData.getString(Constants.DOWNLOAD_WORKER_GUID) ?: return Result.failure()
        val url = inputData.getString(Constants.DOWNLOAD_WORKER_URL) ?: return Result.failure()
        val fileName =
            inputData.getString(Constants.DOWNLOAD_WORKER_FILENAME)
                ?: Constants.DOWNLOAD_WORKER_DEFAULT_FILENAME

        return try {
            podcastRepository.updateDownloadStatus(guid, DownloadStatus.DOWNLOADING, null)
            // FIX: DB update removed
            setProgressAsync(workDataOf("progress" to 0f))

            val file = downloadToFile(guid, url, fileName)

            val fileSize = file.length()
            if (fileSize > 0) {
                val isWifi = connectivityProvider.wifiStatus.value
                statsRepo.addDownloadBytes(fileSize, isWifi)
            }

            // Final flush
            // FIX: DB update removed
            setProgressAsync(workDataOf("progress" to 1f))

            podcastRepository.updateDownloadStatus(guid, DownloadStatus.DOWNLOADED, file.absolutePath)

            Result.success(workDataOf(Constants.DOWNLOAD_WORKER_OUTPUT_PATH to file.absolutePath))
        } catch (e: CancellationException) {
            Timber.i("Download cancelled for %s", guid)
            podcastRepository.updateDownloadStatus(guid, DownloadStatus.NOT_DOWNLOADED, null)
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Download failed for $guid")
            podcastRepository.updateDownloadStatus(guid, DownloadStatus.FAILED, null)
            Result.failure()
        }
    }

    private suspend fun resolveDownloadDirectory(): File {
        val saveToDownloads = try {
            userPreferencesRepository.userSettingsFlow.first().saveToDownloadsFolder
        } catch (e: Exception) {
            Timber.w(e, "Could not read download location setting, using private storage")
            false
        }

        if (!saveToDownloads) {
            val dir = File(applicationContext.filesDir, Constants.DOWNLOADS_DIR)
            dir.mkdirs()
            return dir
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: app-specific external Downloads, no permission needed
            val externalDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (externalDir != null && (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED)) {
                externalDir.mkdirs()
                externalDir
            } else {
                Timber.w("External storage not available, falling back to private storage")
                File(applicationContext.filesDir, Constants.DOWNLOADS_DIR).also { it.mkdirs() }
            }
        } else {
            // API < 29: public Downloads with WRITE_EXTERNAL_STORAGE permission
            val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED) {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(publicDownloads, "Pocastcloni")
                appDir.mkdirs()
                appDir
            } else {
                Timber.w("WRITE_EXTERNAL_STORAGE not granted, falling back to private storage")
                File(applicationContext.filesDir, Constants.DOWNLOADS_DIR).also { it.mkdirs() }
            }
        }
    }

    private suspend fun downloadToFile(
        guid: String,
        url: String,
        fileName: String
    ): File {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        try {
            if (!response.isSuccessful) {
                throw IOException("Server responded with error: ${response.code}")
            }

            val body = response.body ?: throw IOException("Response body is null")
            val totalBytes = body.contentLength()

            Timber.d("Expected size: $totalBytes Bytes")

            val dir = resolveDownloadDirectory()

            // Sanitize filename to prevent path traversal attacks
            // Only block actual path traversal characters, preserve everything else
            val sanitizedFileName = fileName
                .replace("..", "")           // Remove traversal sequences
                .replace("/", "")            // Remove Unix path separators
                .replace("\\", "")           // Remove Windows path separators
                .trim()
                .ifEmpty { "episode_download" }  // Fallback if filename becomes empty
            val file = File(dir, sanitizedFileName)

            var input: InputStream? = null
            var output: FileOutputStream? = null

            try {
                input = body.byteStream()
                output = FileOutputStream(file)

                val buffer = ByteArray(8 * 1024)
                var bytesCopied = 0L

                var lastWrittenPercent = 0
                var lastWriteAtMs = 0L

                while (true) {
                    if (isStopped) throw CancellationException("Worker stopped")

                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead

                    if (totalBytes > 0L) {
                        val percent =
                            ((bytesCopied * 100L) / totalBytes)
                                .toInt()
                                .coerceIn(0, 100)

                        val now = SystemClock.elapsedRealtime()
                        val force = percent >= 100
                        val timeOk = (now - lastWriteAtMs) >= PROGRESS_MIN_INTERVAL_MS

                        if ((percent > lastWrittenPercent && timeOk) || force) {
                            // Use the native WorkManager API instead of a DB update
                            setProgressAsync(workDataOf("progress" to (percent / 100f)))

                            lastWrittenPercent = percent
                            lastWriteAtMs = now
                        }
                    }
                }

                output.flush()
            } catch (e: Exception) {
                if (file.exists()) file.delete()
                throw e
            } finally {
                try {
                    output?.close()
                } catch (_: Exception) {
                }
                try {
                    input?.close()
                } catch (_: Exception) {
                }
            }

            val actualSize = file.length()
            if (totalBytes > 0L && actualSize != totalBytes) {
                val msg = "Download incomplete! Expected: $totalBytes, Got: $actualSize"
                Timber.e(msg)
                if (file.exists()) file.delete()
                throw IOException(msg)
            }

            return file
        } finally {
            response.close()
        }
    }
}
