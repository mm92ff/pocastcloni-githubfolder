package com.example.pocastcloni.data.worker

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.example.pocastcloni.R
import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadForegroundInfoAndroidTest {
    @Test
    fun foregroundInfoUsesLowImportanceDataSyncNotificationWithCancelAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info =
            createDownloadForegroundInfo(
                context = context,
                workManager = WorkManager.getInstance(context),
                workId = UUID.randomUUID(),
                episodeTitle = "Episode",
                progressPercent = 50
            )
        val notification = info.notification
        val channel =
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)

        assertEquals(R.drawable.ic_download, notification.smallIcon.resId)
        assertEquals(Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID, notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals(1, notification.actions.size)
        assertNotNull(notification.actions.first().actionIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        }
    }
}
