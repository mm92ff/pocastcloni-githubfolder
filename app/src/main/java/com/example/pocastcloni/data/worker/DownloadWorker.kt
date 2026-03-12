package com.example.pocastcloni.data.worker

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.StatisticsRepository
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.ConnectivityProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val podcastRepository: PodcastRepository,
    private val statsRepo: StatisticsRepository,
    private val connectivityProvider: ConnectivityProvider,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    companion object {
        // Wir können jetzt sogar öfter updaten, da wir nicht mehr in die DB schreiben!
        private const val PROGRESS_MIN_INTERVAL_MS = 250L // War 750L
    }

    override suspend fun doWork(): Result {
        val guid = inputData.getString(Constants.DOWNLOAD_WORKER_GUID) ?: return Result.failure()
        val url = inputData.getString(Constants.DOWNLOAD_WORKER_URL) ?: return Result.failure()
        val fileName = inputData.getString(Constants.DOWNLOAD_WORKER_FILENAME)
            ?: Constants.DOWNLOAD_WORKER_DEFAULT_FILENAME

        return try {
            podcastRepository.updateDownloadStatus(guid, DownloadStatus.DOWNLOADING, null)
            // FIX: DB Update entfernt
            setProgressAsync(workDataOf("progress" to 0f))

            val file = downloadToFile(guid, url, fileName)

            val fileSize = file.length()
            if (fileSize > 0) {
                val isWifi = connectivityProvider.wifiStatus.value
                statsRepo.addDownloadBytes(fileSize, isWifi)
            }

            // Finaler Flush
            // FIX: DB Update entfernt
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

    private suspend fun downloadToFile(guid: String, url: String, fileName: String): File {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        try {
            if (!response.isSuccessful) {
                throw IOException("Server responded with error: ${response.code}")
            }

            val body = response.body ?: throw IOException("Response body is null")
            val totalBytes = body.contentLength()

            Timber.d("Expected size: $totalBytes Bytes")

            val dir = File(applicationContext.filesDir, Constants.DOWNLOADS_DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, fileName)

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
                        val percent = ((bytesCopied * 100L) / totalBytes)
                            .toInt()
                            .coerceIn(0, 100)

                        val now = SystemClock.elapsedRealtime()
                        val force = percent >= 100
                        val timeOk = (now - lastWriteAtMs) >= PROGRESS_MIN_INTERVAL_MS

                        if ((percent > lastWrittenPercent && timeOk) || force) {
                            // FIX: Statt DB Update nutzen wir die native WorkManager API
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
