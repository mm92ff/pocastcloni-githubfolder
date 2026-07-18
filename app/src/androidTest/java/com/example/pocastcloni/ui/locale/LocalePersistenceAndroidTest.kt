package com.example.pocastcloni.ui.locale

import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.main.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalePersistenceAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun restoreSystemDefaultLocale() {
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun explicitLocaleSurvivesActivityRecreationAndCanBeChangedAgain() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            selectLocale("de")
            assertActivityLocale(scenario, expectedLanguage = "de")

            scenario.recreate()
            assertActivityLocale(scenario, expectedLanguage = "de")

            selectLocale("en-US")
            assertActivityLocale(scenario, expectedLanguage = "en")
            scenario.onActivity { activity ->
                assertEquals("Settings", activity.getString(R.string.title_settings))
            }
        }
    }

    private fun selectLocale(languageTags: String) {
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTags))
        }
        instrumentation.waitForIdleSync()
        assertEquals(languageTags, AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    private fun assertActivityLocale(
        scenario: ActivityScenario<MainActivity>,
        expectedLanguage: String
    ) {
        val deadline = SystemClock.uptimeMillis() + LOCALE_RECREATION_TIMEOUT_MS
        var actualLanguage = ""
        do {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                actualLanguage = activity.resources.configuration.locales[0].language
            }
            if (actualLanguage == expectedLanguage) return
            SystemClock.sleep(LOCALE_RECREATION_POLL_MS)
        } while (SystemClock.uptimeMillis() < deadline)

        assertEquals(expectedLanguage, actualLanguage)
    }

    private companion object {
        const val LOCALE_RECREATION_TIMEOUT_MS = 5_000L
        const val LOCALE_RECREATION_POLL_MS = 50L
    }
}
