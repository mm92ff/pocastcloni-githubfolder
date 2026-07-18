package com.example.pocastcloni.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.main.MainActivity
import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DownloadForegroundInfoAndroidTest {
    @Test
    fun foregroundInfoAndExistingChannelFollowExplicitLocaleChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val previousLocales = AppCompatDelegate.getApplicationLocales()
        val episodeTitle = "Podcast 🌍"
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            setApplicationLocale(instrumentation, "en-US")
            awaitActivityLocale(activityScenario, "en")
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                    "Outdated channel name",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            val englishContext = ContextCompat.getContextForLanguage(context)
            val englishInfo =
                createDownloadForegroundInfo(
                    context = context,
                    workManager = WorkManager.getInstance(context),
                    workId = UUID.randomUUID(),
                    episodeTitle = episodeTitle,
                    progressPercent = 50
                )

            assertForegroundInfo(
                localizedContext = englishContext,
                notificationManager = notificationManager,
                info = englishInfo,
                episodeTitle = episodeTitle,
                progressPercent = 50
            )

            setApplicationLocale(instrumentation, "de")
            awaitActivityLocale(activityScenario, "de")
            val germanContext = ContextCompat.getContextForLanguage(context)
            val germanInfo =
                createDownloadForegroundInfo(
                    context = context,
                    workManager = WorkManager.getInstance(context),
                    workId = UUID.randomUUID(),
                    episodeTitle = episodeTitle,
                    progressPercent = null
                )

            assertForegroundInfo(
                localizedContext = germanContext,
                notificationManager = notificationManager,
                info = germanInfo,
                episodeTitle = episodeTitle,
                progressPercent = null
            )
            assertEquals("de", germanContext.resources.configuration.locales[0].language)
            assertEquals(
                germanContext.getString(R.string.download_notification_channel_name),
                notificationManager
                    .getNotificationChannel(Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                    .name
                    .toString()
            )
            assertEquals(
                germanContext.getString(R.string.download_notification_title),
                germanInfo.notification.extras
                    .getCharSequence(Notification.EXTRA_TITLE)
                    ?.toString()
            )
        } finally {
            setApplicationLocales(instrumentation, previousLocales)
            notificationManager.deleteNotificationChannel(Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            activityScenario.close()
        }
    }

    private fun assertForegroundInfo(
        localizedContext: Context,
        notificationManager: NotificationManager,
        info: androidx.work.ForegroundInfo,
        episodeTitle: String,
        progressPercent: Int?
    ) {
        val notification = info.notification
        val channel =
            notificationManager.getNotificationChannel(Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)

        assertEquals(R.drawable.ic_download, notification.smallIcon.resId)
        assertEquals(Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID, notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals(
            localizedContext.getString(R.string.download_notification_channel_name),
            channel.name.toString()
        )
        assertEquals(
            localizedContext.getString(
                R.string.download_notification_content,
                episodeTitle,
                progressPercent?.let {
                    localizedContext.getString(R.string.download_notification_progress, it)
                } ?: localizedContext.getString(R.string.download_notification_starting)
            ),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )
        assertEquals(1, notification.actions.size)
        assertNotNull(notification.actions.first().actionIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        }
    }

    private fun setApplicationLocale(
        instrumentation: android.app.Instrumentation,
        languageTags: String
    ) {
        setApplicationLocales(instrumentation, LocaleListCompat.forLanguageTags(languageTags))
        assertEquals(languageTags, AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    private fun setApplicationLocales(
        instrumentation: android.app.Instrumentation,
        locales: LocaleListCompat
    ) {
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(locales)
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitActivityLocale(
        scenario: ActivityScenario<MainActivity>,
        expectedLanguage: String
    ) {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (SystemClock.elapsedRealtime() < deadline) {
            var currentLanguage: String? = null
            scenario.onActivity { activity ->
                currentLanguage = activity.resources.configuration.locales[0].language
            }
            if (currentLanguage == expectedLanguage) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        error("Timed out waiting for activity locale $expectedLanguage")
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val POLL_INTERVAL_MS = 50L
    }
}
