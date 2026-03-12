package com.example.pocastcloni.domain.usecase.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pocastcloni.data.worker.FeedUpdateWorker
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class UpdateBackgroundWorkerUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(enabled: Boolean, intervalHours: Int) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<FeedUpdateWorker>(
                intervalHours.toLong(),
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(Constants.FEED_UPDATE_WORK_TAG)
                .build()
            workManager.enqueueUniquePeriodicWork(
                Constants.FEED_UPDATE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(Constants.FEED_UPDATE_WORK_NAME)
        }
    }
}
