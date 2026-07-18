package com.example.pocastcloni.ui.locale

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleStartupSynchronizerTest {
    @Test
    fun systemDefaultRendersWithoutRecreation() {
        val action = AppLocaleStartupSynchronizer.actionFor(
            snapshot(
                frameworkSyncComplete = true,
                applicationLanguageTags = "",
                configurationLanguageTag = "de-CH"
            )
        )

        assertEquals(AppLocaleStartupAction.RENDER, action)
    }

    @Test
    fun matchingExplicitLocaleRendersWithoutRecreation() {
        val action = AppLocaleStartupSynchronizer.actionFor(
            snapshot(
                frameworkSyncComplete = true,
                applicationLanguageTags = "en-US",
                configurationLanguageTag = "en-GB"
            )
        )

        assertEquals(AppLocaleStartupAction.RENDER, action)
    }

    @Test
    fun mismatchedExplicitLocaleRequestsRecreation() {
        val action = AppLocaleStartupSynchronizer.actionFor(
            snapshot(
                frameworkSyncComplete = true,
                applicationLanguageTags = "de",
                configurationLanguageTag = "en-US"
            )
        )

        assertEquals(AppLocaleStartupAction.RECREATE, action)
    }

    @Test
    fun waitsForFrameworkSyncBeforeChoosingStartupAction() = runTest {
        val snapshots =
            listOf(
                snapshot(
                    frameworkSyncComplete = false,
                    applicationLanguageTags = "",
                    configurationLanguageTag = "en-US"
                ),
                snapshot(
                    frameworkSyncComplete = true,
                    applicationLanguageTags = "de",
                    configurationLanguageTag = "en-US"
                )
            )
        var reads = 0
        var waits = 0
        val synchronizer =
            AppLocaleStartupSynchronizer(
                snapshotProvider = { snapshots[reads++.coerceAtMost(snapshots.lastIndex)] },
                waitForNextCheck = { waits++ },
                maximumChecks = snapshots.size
            )

        assertEquals(AppLocaleStartupAction.RECREATE, synchronizer.awaitAction())
        assertEquals(2, reads)
        assertEquals(1, waits)
    }

    @Test
    fun timeoutUsesLatestSafeDecisionInsteadOfBlockingStartup() = runTest {
        val synchronizer =
            AppLocaleStartupSynchronizer(
                snapshotProvider = {
                    snapshot(
                        frameworkSyncComplete = false,
                        applicationLanguageTags = "",
                        configurationLanguageTag = "en-US"
                    )
                },
                waitForNextCheck = {},
                maximumChecks = 2
            )

        assertEquals(AppLocaleStartupAction.RENDER, synchronizer.awaitAction())
    }

    private fun snapshot(
        frameworkSyncComplete: Boolean,
        applicationLanguageTags: String,
        configurationLanguageTag: String
    ) =
        AppLocaleStartupSnapshot(
            frameworkSyncComplete = frameworkSyncComplete,
            applicationLanguageTags = applicationLanguageTags,
            configurationLanguageTag = configurationLanguageTag
        )
}
