package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pocastcloni.data.cover.PodcastCoverMaterializationResult
import com.example.pocastcloni.data.cover.PodcastCoverMaterializer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PodcastCoverRefreshWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val materializer: PodcastCoverMaterializer
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val rssUrl = inputData.getString(KEY_PODCAST_RSS_URL)?.trim().orEmpty()
        if (rssUrl.isEmpty()) return Result.failure()
        return when (materializer.materialize(rssUrl, inputData.getBoolean(KEY_FORCE, false))) {
            is PodcastCoverMaterializationResult.RetryableFailure ->
                if (WorkerRetryPolicy.canRetry(runAttemptCount)) Result.retry() else Result.failure()
            is PodcastCoverMaterializationResult.PermanentFailure -> Result.failure()
            is PodcastCoverMaterializationResult.Waiting -> Result.success()
            else -> Result.success()
        }
    }

    companion object {
        const val KEY_PODCAST_RSS_URL = "podcast_rss_url"
        const val KEY_FORCE = "force"
    }
}
