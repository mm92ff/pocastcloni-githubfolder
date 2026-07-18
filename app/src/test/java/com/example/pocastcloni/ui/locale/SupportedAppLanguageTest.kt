package com.example.pocastcloni.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SupportedAppLanguageTest {
    @Test
    fun supportedLanguagesHaveStableCanonicalTagsAndOrder() {
        assertEquals(
            listOf(
                SupportedAppLanguage.SYSTEM_DEFAULT,
                SupportedAppLanguage.ENGLISH,
                SupportedAppLanguage.GERMAN
            ),
            SupportedAppLanguage.entries
        )
        assertEquals("", SupportedAppLanguage.SYSTEM_DEFAULT.languageTags)
        assertEquals("en-US", SupportedAppLanguage.ENGLISH.languageTags)
        assertEquals("de", SupportedAppLanguage.GERMAN.languageTags)
    }

    @Test
    fun emptyLanguageTagsMapToSystemDefault() {
        assertEquals(
            SupportedAppLanguage.SYSTEM_DEFAULT,
            SupportedAppLanguage.fromLanguageTags("")
        )
    }

    @Test
    fun regionalTagsMapToTheirSupportedLanguage() {
        assertEquals(
            SupportedAppLanguage.ENGLISH,
            SupportedAppLanguage.fromLanguageTags("en-GB")
        )
        assertEquals(
            SupportedAppLanguage.GERMAN,
            SupportedAppLanguage.fromLanguageTags("de-CH,en-US")
        )
    }

    @Test
    fun unsupportedLanguageTagsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SupportedAppLanguage.fromLanguageTags("fr")
        }
    }
}
