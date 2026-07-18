package com.example.pocastcloni.ui.locale

import java.util.concurrent.CopyOnWriteArraySet

internal fun interface AppLocaleChangeListener {
    fun onAppLocaleChanged(languageTags: String)
}

internal interface LocaleChangeNotifier {
    fun notifyChanged(languageTags: String)
}

internal object AppLocaleChangeNotifier : LocaleChangeNotifier {
    private val listeners = CopyOnWriteArraySet<AppLocaleChangeListener>()

    fun addListener(listener: AppLocaleChangeListener) {
        listeners += listener
    }

    fun removeListener(listener: AppLocaleChangeListener) {
        listeners -= listener
    }

    override fun notifyChanged(languageTags: String) {
        listeners.forEach { listener -> listener.onAppLocaleChanged(languageTags) }
    }
}
