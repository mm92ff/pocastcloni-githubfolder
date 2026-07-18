package com.example.pocastcloni.ui.locale

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class AppLocaleController internal constructor(
    private val localeGateway: AppLocaleGateway = AppCompatLocaleGateway,
    private val changeDispatcher: LocaleChangeDispatcher = MainThreadLocaleChangeDispatcher,
    private val changeNotifier: LocaleChangeNotifier = AppLocaleChangeNotifier
) {
    @Volatile
    private var pendingSelection: SupportedAppLanguage? = null

    val currentSelection: SupportedAppLanguage
        get() = SupportedAppLanguage.fromLanguageTags(localeGateway.currentLanguageTags())

    fun select(language: SupportedAppLanguage): Boolean {
        val currentRequest = pendingSelection
        val shouldSelect =
            currentRequest != language &&
                (currentRequest != null || currentSelection != language)
        if (shouldSelect) {
            pendingSelection = language
            changeDispatcher.dispatch {
                if (pendingSelection == language) {
                    try {
                        if (currentSelection != language) {
                            localeGateway.setLanguageTags(language.languageTags)
                            changeNotifier.notifyChanged(language.languageTags)
                        }
                    } finally {
                        if (pendingSelection == language) {
                            pendingSelection = null
                        }
                    }
                }
            }
        }
        return shouldSelect
    }
}

internal interface AppLocaleGateway {
    fun currentLanguageTags(): String

    fun setLanguageTags(languageTags: String)
}

private object AppCompatLocaleGateway : AppLocaleGateway {
    override fun currentLanguageTags(): String =
        AppCompatDelegate.getApplicationLocales().toLanguageTags()

    override fun setLanguageTags(languageTags: String) {
        val locales =
            if (languageTags.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTags)
            }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

internal fun interface LocaleChangeDispatcher {
    fun dispatch(change: () -> Unit)
}

private object MainThreadLocaleChangeDispatcher : LocaleChangeDispatcher {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun dispatch(change: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            change()
        } else {
            handler.post(change)
        }
    }
}
