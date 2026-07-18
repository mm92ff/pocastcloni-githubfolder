package com.example.pocastcloni.ui.locale

import java.util.Locale

enum class SupportedAppLanguage(
    val languageTags: String
) {
    SYSTEM_DEFAULT(""),
    ENGLISH("en-US"),
    GERMAN("de");

    companion object {
        fun fromLanguageTags(languageTags: String): SupportedAppLanguage {
            if (languageTags.isBlank()) return SYSTEM_DEFAULT

            val primaryLocale = Locale.forLanguageTag(languageTags.substringBefore(','))
            return entries.firstOrNull { candidate ->
                candidate.languageTags.isNotEmpty() &&
                    Locale.forLanguageTag(candidate.languageTags).language == primaryLocale.language
            } ?: throw IllegalArgumentException("Unsupported application locale: ${primaryLocale.toLanguageTag()}")
        }
    }
}
