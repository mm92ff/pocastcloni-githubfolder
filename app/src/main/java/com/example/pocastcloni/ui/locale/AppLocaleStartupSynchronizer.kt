package com.example.pocastcloni.ui.locale

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppLocalesMetadataHolderService
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.Locale

internal enum class AppLocaleStartupAction {
    RENDER,
    RECREATE
}

internal data class AppLocaleStartupSnapshot(
    val frameworkSyncComplete: Boolean,
    val applicationLanguageTags: String,
    val configurationLanguageTag: String
)

internal class AppLocaleStartupSynchronizer(
    private val snapshotProvider: () -> AppLocaleStartupSnapshot,
    private val waitForNextCheck: suspend () -> Unit,
    private val maximumChecks: Int
) {
    init {
        require(maximumChecks > 0) { "maximumChecks must be positive" }
    }

    suspend fun awaitAction(): AppLocaleStartupAction {
        var latestSnapshot = snapshotProvider()
        repeat(maximumChecks - 1) {
            if (latestSnapshot.frameworkSyncComplete) {
                return actionFor(latestSnapshot)
            }
            waitForNextCheck()
            latestSnapshot = snapshotProvider()
        }

        if (!latestSnapshot.frameworkSyncComplete) {
            Timber.w("Timed out while waiting for AppCompat locale synchronization")
        }
        return actionFor(latestSnapshot)
    }

    internal companion object {
        fun actionFor(snapshot: AppLocaleStartupSnapshot): AppLocaleStartupAction {
            if (snapshot.applicationLanguageTags.isBlank()) {
                return AppLocaleStartupAction.RENDER
            }

            val requestedLocale =
                Locale.forLanguageTag(snapshot.applicationLanguageTags.substringBefore(','))
            val configurationLocale = Locale.forLanguageTag(snapshot.configurationLanguageTag)
            val languageMatches = requestedLocale.language == configurationLocale.language
            val scriptMatches =
                requestedLocale.script.isEmpty() ||
                    configurationLocale.script.isEmpty() ||
                    requestedLocale.script == configurationLocale.script
            return if (languageMatches && scriptMatches) {
                AppLocaleStartupAction.RENDER
            } else {
                AppLocaleStartupAction.RECREATE
            }
        }
    }
}

internal suspend fun awaitAppLocaleStartupAction(context: Context): AppLocaleStartupAction {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return AppLocaleStartupSnapshotReader(context).action()
    }

    return AppLocaleStartupSynchronizer(
        snapshotProvider = { AppLocaleStartupSnapshotReader(context).read() },
        waitForNextCheck = { delay(FRAMEWORK_SYNC_POLL_INTERVAL_MS) },
        maximumChecks = FRAMEWORK_SYNC_MAXIMUM_CHECKS
    ).awaitAction()
}

private class AppLocaleStartupSnapshotReader(
    private val context: Context
) {
    fun action(): AppLocaleStartupAction = AppLocaleStartupSynchronizer.actionFor(read())

    fun read(): AppLocaleStartupSnapshot =
        AppLocaleStartupSnapshot(
            frameworkSyncComplete = frameworkSyncComplete(),
            applicationLanguageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags(),
            configurationLanguageTag = context.resources.configuration.locales.get(0).toLanguageTag()
        )

    private fun frameworkSyncComplete(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        val component = ComponentName(context, AppLocalesMetadataHolderService::class.java)
        return context.packageManager.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}

private const val FRAMEWORK_SYNC_POLL_INTERVAL_MS = 10L
private const val FRAMEWORK_SYNC_MAXIMUM_CHECKS = 201
