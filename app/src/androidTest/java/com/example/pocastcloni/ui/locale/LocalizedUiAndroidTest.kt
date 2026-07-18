package com.example.pocastcloni.ui.locale

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.common.formatEpisodeDuration
import com.example.pocastcloni.ui.common.formatEpisodePublishDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Covers the release plan's required UI surfaces with exact English and German fixtures.
 * The unsupported-locale assertion on every resource also proves the complete English fallback.
 */
@RunWith(AndroidJUnit4::class)
class LocalizedUiAndroidTest {
    private val targetContext = ApplicationProvider.getApplicationContext<Context>()
    private val englishContext = contextFor(Locale.US)
    private val germanContext = contextFor(Locale.GERMANY)
    private val unsupportedContext = contextFor(Locale.forLanguageTag("fr-FR"))

    @Test
    fun homeNavigationAndAddPodcastErrorsUseLocalizedResources() {
        assertLocalized(R.string.home_title, "My Podcasts", "Meine Podcasts")
        assertLocalized(R.string.home_empty, "No podcasts yet. Press +", "Noch keine Podcasts. Dr\u00fccke +")
        assertLocalized(R.string.nav_home, "Home", "Start")
        assertLocalized(R.string.nav_downloads, "Downloads", "Downloads")
        assertLocalized(R.string.nav_settings, "Settings", "Einstellungen")
        assertLocalized(R.string.title_add_podcast, "Add Podcast", "Podcast hinzuf\u00fcgen")
        assertLocalized(
            R.string.error_add_podcast_failed,
            "Could not load podcast. Check the URL.",
            "Podcast kon" + "nte nicht geladen werden. \u00dcberpr\u00fcfe die URL."
        )
    }

    @Test
    fun podcastDetailAndPlayerAccessibilityUseLocalizedFormatting() {
        assertLocalized(R.string.detail_not_found, "Podcast not found.", "Podcast nicht gefunden.")
        assertLocalized(R.string.detail_unknown_author, "Author unknown", "Autor unbekannt")
        assertLocalized(R.string.desc_play, "Play", "Wiedergeben")
        assertLocalized(R.string.desc_pause, "Pause", "Pause")
        assertLocalized(R.string.desc_open_full_player, "Open full player", "Vollbild-Player \u00f6ffnen")
        assertLocalized(
            R.string.progress_position_description,
            "Playback position 1:00 of 2:00",
            "Wiedergabeposition 1:00 von 2:00",
            "1:00",
            "2:00"
        )
        assertLocalized(
            R.string.playback_failed_error,
            "Playback failed. Please try again.",
            "Wiedergabe fehlgeschlagen. Versuche es erneut."
        )

        val zone = ZoneId.of("Europe/Zurich")
        val epochMs = LocalDateTime.of(2026, 6, 25, 10, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Jun 25, 2026", formatEpisodePublishDate(epochMs, Locale.US, zone))
        assertEquals("25.06.2026", formatEpisodePublishDate(epochMs, Locale.GERMANY, zone))
        assertEquals("1h 05min", formatEpisodeDuration(englishContext, 3_900_000L, Locale.US))
        assertEquals("1 Std. 05 Min.", formatEpisodeDuration(germanContext, 3_900_000L, Locale.GERMANY))
    }

    @Test
    fun favoritesHistoryDownloadsAndNotificationsUseLocalizedResources() {
        assertLocalized(R.string.favorites, "Favorites", "Favoriten")
        assertLocalized(R.string.favorites_empty, "No favorites yet.", "Noch keine Favoriten.")
        assertLocalized(R.string.history, "History", "Verlauf")
        assertLocalized(R.string.history_empty, "No playback history yet.", "Noch kein Wiedergabeverlauf.")
        assertLocalized(R.string.history_section_today, "Today", "Heute")
        assertLocalized(R.string.history_section_yesterday, "Yesterday", "Gestern")
        assertLocalized(R.string.no_downloads, "No downloads available", "Keine Downloads verf\u00fcgbar")
        assertLocalized(R.string.download_notification_channel_name, "Episode downloads", "Episoden-Downloads")
        assertLocalized(R.string.download_notification_title, "Downloading episode", "Episode wird heruntergeladen")
        assertLocalized(R.string.download_notification_progress, "50%", "50%", 50)

        val externalTitle = "Podcast \ud83c\udf0d"
        assertEquals(
            "$externalTitle \u2014 Starting",
            englishContext.getString(
                R.string.download_notification_content,
                externalTitle,
                englishContext.getString(R.string.download_notification_starting)
            )
        )
        assertEquals(
            "$externalTitle \u2014 Wird gestartet",
            germanContext.getString(
                R.string.download_notification_content,
                externalTitle,
                germanContext.getString(R.string.download_notification_starting)
            )
        )
        assertEquals(
            "$externalTitle \u2014 Starting",
            unsupportedContext.getString(
                R.string.download_notification_content,
                externalTitle,
                unsupportedContext.getString(R.string.download_notification_starting)
            )
        )
    }

    @Test
    fun settingsTabsLanguageSmartStreamAndManualRefreshUseLocalizedResources() {
        assertLocalized(R.string.settings_tab_design, "Design", "Design")
        assertLocalized(R.string.settings_tab_playback, "Playback", "Wiedergabe")
        assertLocalized(R.string.settings_tab_sync, "Sync", "Synchronisierung")
        assertLocalized(R.string.settings_tab_data, "Data", "Daten")
        assertEquals("Language", englishContext.getString(R.string.settings_app_language))
        assertEquals("Language", germanContext.getString(R.string.settings_app_language))
        assertEquals("Language", unsupportedContext.getString(R.string.settings_app_language))
        assertLocalized(
            R.string.settings_app_language_dialog_title,
            "Choose app language",
            "App-Sprache ausw\u00e4hlen"
        )
        assertLocalized(R.string.settings_language_system_default, "System default", "Systemstandard")
        assertLocalized(R.string.settings_language_english, "English", "English")
        assertLocalized(R.string.settings_language_german, "Deutsch", "Deutsch")
        assertLocalized(R.string.settings_update_method_smart_stream, "Smart Stream (Fast)", "Smart Stream (schnell)")
        assertLocalized(R.string.settings_smart_stream_item_limit, "Feed read limit", "Feed-Leselimit")
        assertLocalized(
            R.string.settings_manual_full_refresh,
            "Refresh all feeds completely",
            "Alle Feeds vollst\u00e4ndig aktualisieren"
        )
        assertLocalized(
            R.string.settings_manual_full_refresh_subtitle,
            "Ignores the feed read limit and downloads no audio.",
            "Ignoriert das Feed-Leselimit und l\u00e4dt keine Audiodateien herunter."
        )
    }

    @Test
    fun backupStatisticsUnitsPluralsAndResetUseLocalizedResources() {
        assertLocalized(R.string.settings_section_backup_restore, "Backup & Restore", "Sichern & Wiederherstellen")
        assertLocalized(R.string.settings_export_backup, "Export Backup", "Sicherung exportieren")
        assertLocalized(R.string.settings_import_backup, "Import Backup", "Sicherung importieren")
        assertLocalized(R.string.importing_backup, "Importing backup\u2026", "Sicherung wird importiert\u2026")
        assertLocalized(R.string.import_complete, "Import complete", "Import abgeschlossen")
        assertLocalized(R.string.exporting_backup, "Exporting backup\u2026", "Sicherung wird exportiert\u2026")
        assertLocalized(R.string.export_complete, "Export complete", "Export abgeschlossen")
        assertLocalized(
            R.string.import_success_message,
            "Import successful: 2 of 3 podcasts imported. Favorites skipped: 1.",
            "Import erfolgreich: 2 von 3 Podcasts importiert. \u00dcbersprungene Favoriten: 1.",
            2,
            3,
            1
        )
        assertLocalized(
            R.string.import_error_invalid_backup,
            "The selected backup is invalid or unsupported.",
            "Die ausgew\u00e4hlte Sicherung ist ung\u00fcltig oder wird nicht unterst\u00fctzt."
        )
        assertLocalized(R.string.export_success_message, "Export successful.", "Export erfolgreich.")

        assertLocalized(R.string.settings_section_statistics, "Statistics", "Statistik")
        assertLocalized(R.string.settings_statistics_total_episodes, "Total episodes", "Episoden insgesamt")
        assertLocalized(R.string.settings_statistics_days_singular, "day", "Tag")
        assertLocalized(R.string.settings_statistics_days_plural, "days", "Tage")
        assertLocalized(R.string.settings_unit_dp, "24 dp", "24 dp", 24)
        assertPlural(
            R.plurals.podcasts_updated_count,
            1,
            "1 podcast updated.",
            "1 Podcast aktualisiert.",
            1
        )
        assertPlural(
            R.plurals.podcasts_updated_count,
            2,
            "2 podcasts updated.",
            "2 Podcasts aktualisiert.",
            2
        )

        val statisticsEpochMs = Date(1_782_379_800_000L)
        assertEquals("Jun 25, 2026", DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US).format(statisticsEpochMs))
        assertEquals(
            "25.06.2026",
            DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.GERMANY).format(statisticsEpochMs)
        )

        assertLocalized(R.string.settings_reset_dialog_title, "Reset app?", "App zur\u00fccksetzen?")
        assertLocalized(
            R.string.settings_reset_dialog_message,
            "All subscriptions, settings, playlists, and listening history will be permanently deleted.\n\n" +
                "Note: Already downloaded episodes will remain on the device.",
            "Alle Abonnements, Einstellungen, Wiedergabelisten und der Wiedergabeverlauf " +
                "werden dauerhaft gel\u00f6scht.\n\n" +
                "Hin" + "weis: Bereits heruntergeladene Episoden bleiben auf dem Ger\u00e4t."
        )
        assertLocalized(R.string.settings_reset_dialog_confirm, "Delete everything", "Alles l\u00f6schen")
    }

    private fun assertLocalized(
        resourceId: Int,
        english: String,
        german: String,
        vararg formatArguments: Any
    ) {
        assertEquals(english, englishContext.getString(resourceId, *formatArguments))
        assertEquals(german, germanContext.getString(resourceId, *formatArguments))
        assertEquals(english, unsupportedContext.getString(resourceId, *formatArguments))
    }

    private fun assertPlural(
        resourceId: Int,
        quantity: Int,
        english: String,
        german: String,
        vararg formatArguments: Any
    ) {
        assertEquals(english, englishContext.resources.getQuantityString(resourceId, quantity, *formatArguments))
        assertEquals(german, germanContext.resources.getQuantityString(resourceId, quantity, *formatArguments))
        assertEquals(english, unsupportedContext.resources.getQuantityString(resourceId, quantity, *formatArguments))
    }

    private fun contextFor(locale: Locale): Context {
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocale(locale)
        }
        return targetContext.createConfigurationContext(configuration)
    }
}
