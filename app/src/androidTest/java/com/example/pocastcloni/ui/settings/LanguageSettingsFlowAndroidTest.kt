package com.example.pocastcloni.ui.settings

import android.app.Activity
import android.app.Application
import android.content.res.Resources
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.swipeRight
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.ui.main.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LanguageSettingsFlowAndroidTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val activityCreations = AtomicInteger()
    private val activityCallbacks = MainActivityCreationCallbacks(activityCreations)
    private var originalLanguageTags = ""
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        originalLanguageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        application.registerActivityLifecycleCallbacks(activityCallbacks)
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        setApplicationLanguageForSetup(originalLanguageTags)
    }

    @Test
    fun pickerSwitchesBothDirectionsAndRetainsSettingsDesignDestination() {
        launchWithLanguage(ENGLISH_TAG)
        openSettingsFromHome()
        val baselineCreations = activityCreations.get()

        assertCombinedLanguageRow(ENGLISH_APP_LANGUAGE, ENGLISH_LANGUAGE)
        openLanguageDialog()
        assertLanguageDialog(selectedLabel = ENGLISH_LANGUAGE)
        composeRule.onNode(selectedOptionMatcher(GERMAN_LANGUAGE, selected = false)).performClick()

        awaitSettingsLanguage(
            languageTag = GERMAN_TAG,
            settingsTitle = GERMAN_SETTINGS_TITLE,
            languageTitle = GERMAN_APP_LANGUAGE,
            currentSelection = GERMAN_LANGUAGE
        )
        assertNoAdditionalRecreation(baselineCreations + 1)
        assertSettingsDesignDestination(GERMAN_SETTINGS_TITLE)
        assertCombinedLanguageRow(GERMAN_APP_LANGUAGE, GERMAN_LANGUAGE)

        openLanguageDialog()
        assertLanguageDialog(selectedLabel = GERMAN_LANGUAGE)
        composeRule.onNode(selectedOptionMatcher(ENGLISH_LANGUAGE, selected = false)).performClick()

        awaitSettingsLanguage(
            languageTag = ENGLISH_LANGUAGE_CODE,
            settingsTitle = ENGLISH_SETTINGS_TITLE,
            languageTitle = ENGLISH_APP_LANGUAGE,
            currentSelection = ENGLISH_LANGUAGE
        )
        assertNoAdditionalRecreation(baselineCreations + 2)
        assertSettingsDesignDestination(ENGLISH_SETTINGS_TITLE)
        assertCombinedLanguageRow(ENGLISH_APP_LANGUAGE, ENGLISH_LANGUAGE)
    }

    @Test
    fun systemDefaultUsesCurrentEnglishOrGermanSystemConfigurationWithoutMutatingDevice() {
        val systemLanguage = Resources.getSystem().configuration.locales[0].language
        assumeTrue(
            "Run this matrix test with an English or German emulator system locale",
            systemLanguage == ENGLISH_LANGUAGE_CODE || systemLanguage == GERMAN_TAG
        )
        val explicitLanguage = if (systemLanguage == ENGLISH_LANGUAGE_CODE) GERMAN_TAG else ENGLISH_TAG
        val explicitAppLanguage = if (systemLanguage == ENGLISH_LANGUAGE_CODE) GERMAN_APP_LANGUAGE else ENGLISH_APP_LANGUAGE
        val systemDefaultLabel =
            if (systemLanguage == ENGLISH_LANGUAGE_CODE) GERMAN_SYSTEM_DEFAULT else ENGLISH_SYSTEM_DEFAULT

        launchWithLanguage(explicitLanguage)
        openSettingsFromHome()
        val baselineCreations = activityCreations.get()
        assertCombinedLanguageRow(
            title = explicitAppLanguage,
            selection = if (explicitLanguage == GERMAN_TAG) GERMAN_LANGUAGE else ENGLISH_LANGUAGE
        )

        openLanguageDialog()
        composeRule.onNode(selectedOptionMatcher(systemDefaultLabel, selected = false)).performClick()

        val expectedSettingsTitle =
            if (systemLanguage == GERMAN_TAG) GERMAN_SETTINGS_TITLE else ENGLISH_SETTINGS_TITLE
        val expectedLanguageTitle =
            if (systemLanguage == GERMAN_TAG) GERMAN_APP_LANGUAGE else ENGLISH_APP_LANGUAGE
        val expectedSystemDefaultLabel =
            if (systemLanguage == GERMAN_TAG) GERMAN_SYSTEM_DEFAULT else ENGLISH_SYSTEM_DEFAULT
        awaitSettingsLanguage(
            languageTag = systemLanguage,
            settingsTitle = expectedSettingsTitle,
            languageTitle = expectedLanguageTitle,
            currentSelection = expectedSystemDefaultLabel,
            expectEmptyApplicationLocales = true
        )

        assertNoAdditionalRecreation(baselineCreations + 1)
        assertSettingsDesignDestination(expectedSettingsTitle)
        assertCombinedLanguageRow(expectedLanguageTitle, expectedSystemDefaultLabel)
    }

    @Test
    fun currentSelectionAndRapidDuplicateTapDoNotCauseExtraRecreation() {
        launchWithLanguage(ENGLISH_TAG)
        openSettingsFromHome()
        val baselineCreations = activityCreations.get()

        openLanguageDialog()
        composeRule.onNode(selectedOptionMatcher(ENGLISH_LANGUAGE, selected = true)).performClick()
        awaitDialogClosed()
        assertNoAdditionalRecreation(baselineCreations)
        assertSettingsDesignDestination(ENGLISH_SETTINGS_TITLE)

        openLanguageDialog()
        composeRule.onNode(selectedOptionMatcher(GERMAN_LANGUAGE, selected = false))
            .performTouchInput { doubleClick() }

        awaitSettingsLanguage(
            languageTag = GERMAN_TAG,
            settingsTitle = GERMAN_SETTINGS_TITLE,
            languageTitle = GERMAN_APP_LANGUAGE,
            currentSelection = GERMAN_LANGUAGE
        )
        assertNoAdditionalRecreation(baselineCreations + 1)
        assertSettingsDesignDestination(GERMAN_SETTINGS_TITLE)
    }

    private fun launchWithLanguage(languageTags: String) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        setApplicationLanguageForSetup(languageTags)
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            AppCompatDelegate.getApplicationLocales().toLanguageTags() == languageTags &&
                currentActivityLanguage() == languageTags.substringBefore('-')
        }
        composeRule.waitForIdle()
        SystemClock.sleep(RECREATION_SETTLE_MS)
        composeRule.waitForIdle()
    }

    private fun setApplicationLanguageForSetup(languageTags: String) {
        val locales =
            if (languageTags.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTags)
            }
        composeRule.runOnUiThread {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    private fun openSettingsFromHome() {
        val germanUi = currentActivityLanguage() == GERMAN_TAG
        val navLabel = if (germanUi) GERMAN_SETTINGS_TITLE else ENGLISH_SETTINGS_TITLE
        val homeTitle = if (germanUi) GERMAN_HOME_TITLE else ENGLISH_HOME_TITLE
        val settingsNavigation = hasText(navLabel) and hasClickAction()
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            hasNode(settingsNavigation) || hasVisibleText(homeTitle)
        }
        if (hasNode(settingsNavigation)) {
            composeRule.onNode(settingsNavigation).performClick()
        } else {
            composeRule.onRoot().performTouchInput { swipeRight() }
        }
        val languageTitle = if (currentActivityLanguage() == GERMAN_TAG) GERMAN_APP_LANGUAGE else ENGLISH_APP_LANGUAGE
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            hasVisibleText(languageTitle) && hasNode(hasTestTag(DESIGN_LIST_TAG))
        }
    }

    private fun openLanguageDialog() {
        val title = if (currentActivityLanguage() == GERMAN_TAG) GERMAN_APP_LANGUAGE else ENGLISH_APP_LANGUAGE
        val dialogTitle =
            if (currentActivityLanguage() == GERMAN_TAG) {
                GERMAN_LANGUAGE_DIALOG_TITLE
            } else {
                ENGLISH_LANGUAGE_DIALOG_TITLE
            }
        composeRule.onNodeWithText(title).assertHasClickAction().performClick()
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            hasVisibleText(dialogTitle)
        }
    }

    private fun assertLanguageDialog(selectedLabel: String) {
        composeRule.onAllNodes(languageOptionMatcher()).assertCountEquals(3)
        composeRule.onNode(selectedOptionMatcher(selectedLabel, selected = true))
            .assertIsDisplayed()
            .assertIsSelected()
    }

    private fun assertCombinedLanguageRow(
        title: String,
        selection: String
    ) {
        composeRule.onNode(hasText(title) and hasText(selection) and hasClickAction())
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    private fun awaitSettingsLanguage(
        languageTag: String,
        settingsTitle: String,
        languageTitle: String,
        currentSelection: String,
        expectEmptyApplicationLocales: Boolean = false
    ) {
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            val applicationLocales = AppCompatDelegate.getApplicationLocales()
            val localeMatches =
                if (expectEmptyApplicationLocales) {
                    applicationLocales.isEmpty
                } else {
                    applicationLocales[0]?.language == languageTag
                }
            localeMatches &&
                currentActivityLanguage() == languageTag &&
                hasVisibleText(settingsTitle) &&
                hasVisibleText(languageTitle) &&
                hasVisibleText(currentSelection) &&
                hasNode(hasTestTag(DESIGN_LIST_TAG))
        }
        composeRule.waitForIdle()
    }

    private fun awaitDialogClosed() {
        val dialogTitle =
            if (currentActivityLanguage() == GERMAN_TAG) {
                GERMAN_LANGUAGE_DIALOG_TITLE
            } else {
                ENGLISH_LANGUAGE_DIALOG_TITLE
            }
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            !hasVisibleText(dialogTitle)
        }
    }

    private fun assertNoAdditionalRecreation(expectedCreations: Int) {
        composeRule.waitForIdle()
        SystemClock.sleep(RECREATION_SETTLE_MS)
        composeRule.waitForIdle()
        assertEquals(expectedCreations, activityCreations.get())
    }

    private fun assertSettingsDesignDestination(settingsTitle: String) {
        composeRule.onAllNodesWithText(settingsTitle).onFirst().assertIsDisplayed()
        composeRule.onNode(hasTestTag(DESIGN_LIST_TAG)).assertIsDisplayed()
        composeRule.onNode(
            hasText(DESIGN_TAB_TITLE) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        ).assertIsSelected()
    }

    private fun currentActivityLanguage(): String {
        var language = ""
        scenario?.onActivity { activity ->
            language = activity.resources.configuration.locales[0].language
        }
        return language
    }

    private fun hasVisibleText(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hasNode(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun selectedOptionMatcher(
        label: String,
        selected: Boolean
    ): SemanticsMatcher =
        hasText(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun languageOptionMatcher(): SemanticsMatcher {
        val systemDefault =
            if (currentActivityLanguage() == GERMAN_TAG) {
                GERMAN_SYSTEM_DEFAULT
            } else {
                ENGLISH_SYSTEM_DEFAULT
            }
        return (
            hasText(systemDefault) or
                hasText(ENGLISH_LANGUAGE) or
                hasText(GERMAN_LANGUAGE)
            ) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    }

    private class MainActivityCreationCallbacks(
        private val creations: AtomicInteger
    ) : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?
        ) {
            if (activity is MainActivity) creations.incrementAndGet()
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        const val ENGLISH_TAG = "en-US"
        const val ENGLISH_LANGUAGE_CODE = "en"
        const val GERMAN_TAG = "de"
        const val ENGLISH_SETTINGS_TITLE = "Settings"
        const val GERMAN_SETTINGS_TITLE = "Einstellungen"
        const val ENGLISH_HOME_TITLE = "My Podcasts"
        const val GERMAN_HOME_TITLE = "Meine Podcasts"
        const val ENGLISH_APP_LANGUAGE = "Language"
        const val GERMAN_APP_LANGUAGE = "Language"
        const val ENGLISH_LANGUAGE = "English"
        const val GERMAN_LANGUAGE = "Deutsch"
        const val ENGLISH_SYSTEM_DEFAULT = "System default"
        const val GERMAN_SYSTEM_DEFAULT = "Systemstandard"
        const val ENGLISH_LANGUAGE_DIALOG_TITLE = "Choose app language"
        const val GERMAN_LANGUAGE_DIALOG_TITLE = "App-Sprache ausw\u00e4hlen"
        const val DESIGN_TAB_TITLE = "Design"
        const val DESIGN_LIST_TAG = "settings-list-design"
        const val TEST_TIMEOUT_MS = 15_000L
        const val RECREATION_SETTLE_MS = 500L
    }
}
