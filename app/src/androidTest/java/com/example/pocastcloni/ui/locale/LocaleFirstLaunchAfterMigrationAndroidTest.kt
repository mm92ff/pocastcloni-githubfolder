package com.example.pocastcloni.ui.locale

import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.LocaleList
import android.os.SystemClock
import androidx.appcompat.app.AppLocalesMetadataHolderService
import androidx.core.app.AppLocalesStorageHelper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LocaleFirstLaunchAfterMigrationAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun prepareLegacyGermanLocaleForReboot() {
        clearFrameworkLocale()
        AppLocalesStorageHelper.persistLocales(context, GERMAN_LANGUAGE_TAG)
        resetFrameworkSyncMarker()

        assertEquals("", localeManager().applicationLocales.toLanguageTags())
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            context.packageManager.getComponentEnabledSetting(localeStorageComponent())
        )
    }

    @Test
    fun firstLaunchAfterRebootUsesMigratedGermanLocale() {
        try {
            ActivityScenario.launch(MainActivity::class.java).use(::assertGermanActivity)
        } finally {
            clearFrameworkLocale()
            AppLocalesStorageHelper.persistLocales(context, "")
            resetFrameworkSyncMarker()
        }
    }

    private fun assertGermanActivity(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.uptimeMillis() + FIRST_LAUNCH_TIMEOUT_MS
        var actualLanguage = ""
        var actualTitle = ""
        do {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                actualLanguage = activity.resources.configuration.locales[0].language
                actualTitle = activity.getString(R.string.home_title)
            }
            if (actualLanguage == GERMAN_LANGUAGE_TAG && actualTitle == GERMAN_HOME_TITLE) {
                return
            }
            SystemClock.sleep(FIRST_LAUNCH_POLL_MS)
        } while (SystemClock.uptimeMillis() < deadline)

        assertEquals(GERMAN_LANGUAGE_TAG, actualLanguage)
        assertEquals(GERMAN_HOME_TITLE, actualTitle)
    }

    private fun clearFrameworkLocale() {
        localeManager().applicationLocales = LocaleList.getEmptyLocaleList()
    }

    private fun resetFrameworkSyncMarker() {
        context.packageManager.setComponentEnabledSetting(
            localeStorageComponent(),
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun localeManager(): LocaleManager =
        context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager

    private fun localeStorageComponent(): ComponentName =
        ComponentName(context, AppLocalesMetadataHolderService::class.java)

    private companion object {
        const val GERMAN_LANGUAGE_TAG = "de"
        const val GERMAN_HOME_TITLE = "Meine Podcasts"
        const val FIRST_LAUNCH_TIMEOUT_MS = 5_000L
        const val FIRST_LAUNCH_POLL_MS = 50L
    }
}
