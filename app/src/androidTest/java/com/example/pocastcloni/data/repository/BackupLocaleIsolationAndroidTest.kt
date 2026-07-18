package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.settingsForRestore
import com.example.pocastcloni.data.manager.PodcastBackupHelper
import com.example.pocastcloni.data.serialization.JsonMapperFactory
import com.example.pocastcloni.di.DefaultDispatcherProvider
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupLocaleIsolationAndroidTest {
    @Test
    fun importingSettingsBackupDoesNotChangeExplicitApplicationLocale() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previousLocales = AppCompatDelegate.getApplicationLocales()
        val backupFile = File(context.cacheDir, "locale-isolation-backup.json")
        val importedSettings = UserSettings(theme = AppTheme.DARK)
        val helper =
            PodcastBackupHelper(
                objectMapper = JsonMapperFactory.create(),
                dispatcherProvider = DefaultDispatcherProvider()
            )
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            JsonMapperFactory.create().writeValue(
                backupFile,
                BackupData(settings = importedSettings)
            )
            setApplicationLocales(instrumentation, LocaleListCompat.forLanguageTags("de"))
            assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())

            val imported = helper.importBackup(backupFile.toUri(), context.contentResolver)
            val settingsForRestore = imported.settingsForRestore(UserSettings())

            assertEquals(importedSettings, settingsForRestore)
            assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        } finally {
            setApplicationLocales(instrumentation, previousLocales)
            backupFile.delete()
            activityScenario.close()
        }
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
}
