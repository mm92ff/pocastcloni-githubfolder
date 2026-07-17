package com.example.pocastcloni.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManualFeedRefreshPlacementSourceTest {
    @Test
    fun `manual full refresh is a card in automation and not a download card`() {
        val automation =
            File("src/main/java/com/example/pocastcloni/ui/settings/SettingsSections.kt")
                .readText()
        val downloads =
            File("src/main/java/com/example/pocastcloni/ui/settings/SettingsDataSections.kt")
                .readText()

        assertTrue(automation.contains("ManualFullRefreshButton(onClick = onStartManualDownload)"))
        assertTrue(automation.contains("SettingsCard(onClick = onClick, emphasized = true)"))
        assertTrue(automation.contains("imageVector = Icons.Default.CloudDownload"))
        assertTrue(automation.contains("tint = MaterialTheme.colorScheme.primary"))
        assertTrue(automation.contains("contentDescription = null"))
        assertFalse(automation.contains("FilledTonalButton("))
        assertFalse(automation.contains("imageVector = Icons.Default.Refresh"))
        assertFalse(downloads.contains("onStartManualDownload"))
        assertFalse(downloads.contains("settings_manual_full_refresh"))
        assertFalse(downloads.contains("CloudDownload"))
    }

    @Test
    fun `transparent emphasis is twenty percent and only manual full refresh opts in`() {
        val sourceRoot = File("src/main/java/com/example/pocastcloni")
        val constants = File(sourceRoot, "util/Constants.kt").readText()
        val components = File(sourceRoot, "ui/settings/SettingsComponents.kt").readText()
        val transparentDefaults = File(sourceRoot, "ui/common/TransparentSurfaceDefaults.kt").readText()
        val settingsSources =
            File(sourceRoot, "ui/settings")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .joinToString("\n") { it.readText() }

        assertTrue(constants.contains("SETTINGS_ACTION_CARD_TRANSPARENT_ALPHA = 0.20f"))
        assertTrue(components.contains("emphasized: Boolean = false"))
        assertTrue(transparentDefaults.contains("transparentColor: Color = Color.Transparent"))
        assertTrue(components.contains("Constants.UI.SETTINGS_ACTION_CARD_TRANSPARENT_ALPHA"))
        assertTrue(Regex("emphasized = true").findAll(settingsSources).count() == 1)
    }

    @Test
    fun `sync tab routes the unchanged manual event through automation`() {
        val source =
            File("src/main/java/com/example/pocastcloni/ui/settings/SettingsSyncTab.kt")
                .readText()
        val automation = source.indexOf("SectionAutomation(")
        val event = source.indexOf("SettingsUiEvent.StartManualDownload")
        val downloads = source.indexOf("SectionDownloads(")

        assertTrue(automation >= 0)
        assertTrue(event > automation)
        assertTrue(downloads > event)
        assertTrue(source.indexOf("SettingsUiEvent.StartManualDownload", event + 1) < 0)
    }
}
