package com.example.pocastcloni.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocaleControllerTest {
    @Test
    fun currentSelectionComesFromThePlatformGateway() {
        val gateway = FakeAppLocaleGateway("de-DE")
        val controller = AppLocaleController(gateway, ImmediateLocaleChangeDispatcher)

        assertEquals(SupportedAppLanguage.GERMAN, controller.currentSelection)
    }

    @Test
    fun selectingSystemDefaultWritesAnEmptyLocaleList() {
        val gateway = FakeAppLocaleGateway("de")
        val controller = AppLocaleController(gateway, ImmediateLocaleChangeDispatcher)

        assertTrue(controller.select(SupportedAppLanguage.SYSTEM_DEFAULT))
        assertEquals(listOf(""), gateway.writes)
        assertEquals(SupportedAppLanguage.SYSTEM_DEFAULT, controller.currentSelection)
    }

    @Test
    fun selectingTheCurrentLanguageIsIdempotent() {
        val gateway = FakeAppLocaleGateway("en-US")
        val controller = AppLocaleController(gateway, ImmediateLocaleChangeDispatcher)

        assertFalse(controller.select(SupportedAppLanguage.ENGLISH))
        assertTrue(gateway.writes.isEmpty())
    }

    @Test
    fun localeChangesAreDelegatedBeforeThePlatformIsUpdated() {
        val gateway = FakeAppLocaleGateway("")
        val dispatcher = RecordingLocaleChangeDispatcher()
        val controller = AppLocaleController(gateway, dispatcher)

        assertTrue(controller.select(SupportedAppLanguage.GERMAN))
        assertTrue(gateway.writes.isEmpty())

        dispatcher.runPendingChange()

        assertEquals(listOf("de"), gateway.writes)
    }

    @Test
    fun aCompletedPlatformChangeNotifiesRunningComponents() {
        val gateway = FakeAppLocaleGateway("")
        val notifier = RecordingLocaleChangeNotifier()
        val controller =
            AppLocaleController(
                localeGateway = gateway,
                changeDispatcher = ImmediateLocaleChangeDispatcher,
                changeNotifier = notifier
            )

        assertTrue(controller.select(SupportedAppLanguage.GERMAN))

        assertEquals(listOf("de"), notifier.languageTags)
    }

    @Test
    fun inProcessLocaleListenersCanBeRemoved() {
        val receivedTags = mutableListOf<String>()
        val listener = AppLocaleChangeListener { languageTags -> receivedTags += languageTags }
        AppLocaleChangeNotifier.addListener(listener)

        try {
            AppLocaleChangeNotifier.notifyChanged("de")
        } finally {
            AppLocaleChangeNotifier.removeListener(listener)
        }
        AppLocaleChangeNotifier.notifyChanged("en-US")

        assertEquals(listOf("de"), receivedTags)
    }

    @Test
    fun queuedDuplicateSelectionDoesNotWriteTwice() {
        val gateway = FakeAppLocaleGateway("")
        val dispatcher = RecordingLocaleChangeDispatcher()
        val controller = AppLocaleController(gateway, dispatcher)

        assertTrue(controller.select(SupportedAppLanguage.GERMAN))
        assertFalse(controller.select(SupportedAppLanguage.GERMAN))

        dispatcher.runPendingChange()

        assertEquals(listOf("de"), gateway.writes)
    }

    @Test
    fun aNewQueuedSelectionSupersedesThePreviousRequest() {
        val gateway = FakeAppLocaleGateway("")
        val dispatcher = RecordingLocaleChangeDispatcher()
        val controller = AppLocaleController(gateway, dispatcher)

        assertTrue(controller.select(SupportedAppLanguage.GERMAN))
        assertTrue(controller.select(SupportedAppLanguage.ENGLISH))

        dispatcher.runPendingChange()
        dispatcher.runPendingChange()

        assertEquals(listOf("en-US"), gateway.writes)
    }

    private class FakeAppLocaleGateway(
        private var languageTags: String
    ) : AppLocaleGateway {
        val writes = mutableListOf<String>()

        override fun currentLanguageTags(): String = languageTags

        override fun setLanguageTags(languageTags: String) {
            writes += languageTags
            this.languageTags = languageTags
        }
    }

    private object ImmediateLocaleChangeDispatcher : LocaleChangeDispatcher {
        override fun dispatch(change: () -> Unit) = change()
    }

    private class RecordingLocaleChangeDispatcher : LocaleChangeDispatcher {
        private val pendingChanges = ArrayDeque<() -> Unit>()

        override fun dispatch(change: () -> Unit) {
            pendingChanges += change
        }

        fun runPendingChange() {
            pendingChanges.removeFirst().invoke()
        }
    }

    private class RecordingLocaleChangeNotifier : LocaleChangeNotifier {
        val languageTags = mutableListOf<String>()

        override fun notifyChanged(languageTags: String) {
            this.languageTags += languageTags
        }
    }
}
