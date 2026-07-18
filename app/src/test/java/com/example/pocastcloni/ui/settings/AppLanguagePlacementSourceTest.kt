package com.example.pocastcloni.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppLanguagePlacementSourceTest {
    @Test
    fun `interface section is first in the Design settings content`() {
        val source =
            File("src/main/java/com/example/pocastcloni/ui/settings/SettingsDesignTab.kt")
                .readText()
        val contentBody = source.substringAfter("internal fun DesignSettingsContent(")
        val interfaceSection = contentBody.indexOf("DesignInterfaceSettings(settings = settings")
        val appearanceSection = contentBody.indexOf("SectionAppearance(")
        val indicatorSection = contentBody.indexOf("SectionIndicator(")

        assertTrue("Interface section call is missing", interfaceSection >= 0)
        assertTrue("Appearance section call is missing", appearanceSection >= 0)
        assertTrue("Indicator section call is missing", indicatorSection >= 0)
        assertTrue("Interface must precede Appearance", interfaceSection < appearanceSection)
        assertTrue("Interface must precede Indicator", interfaceSection < indicatorSection)
    }

    @Test
    fun `app language is the first row in the Interface section`() {
        val source =
            File("src/main/java/com/example/pocastcloni/ui/settings/SettingsInterfaceSections.kt")
                .readText()
        val interfaceBody =
            source.substringAfter("fun SectionInterface(")
                .substringBefore("fun SectionPlayback(")
        val sectionTitle = interfaceBody.indexOf("SettingsSectionTitle(")
        val languageRow = interfaceBody.indexOf("AppLanguageSettings()")
        val layoutCard = interfaceBody.indexOf("SettingsCard {")

        assertTrue("Interface section title is missing", sectionTitle >= 0)
        assertTrue("App language row is missing", languageRow >= 0)
        assertTrue("Layout card is missing", layoutCard >= 0)
        assertTrue("App language must follow the section title", languageRow > sectionTitle)
        assertTrue("App language must precede every regular Interface card", languageRow < layoutCard)
    }
}
