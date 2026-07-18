package com.example.pocastcloni.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocaleFormattingSourceTest {
    private val sourceRoot = locateSourceRoot()

    @Test
    fun `user visible formatters require a resource derived locale`() {
        val formatterFiles =
            listOf(
                "util/TimeUtils.kt",
                "ui/common/EpisodeDateFormatter.kt",
                "ui/common/EpisodeDurationFormatter.kt"
            )

        formatterFiles.forEach { relativePath ->
            val source = sourceRoot.resolve(relativePath).readText()
            assertFalse(
                "$relativePath must not default to the process locale",
                source.contains("Locale.getDefault")
            )
        }
    }

    @Test
    fun `episode UI model stores raw date and duration values`() {
        val source = sourceRoot.resolve("ui/home/detail/EpisodeUiModel.kt").readText()

        assertTrue(source.contains("val pubDateEpochMs: Long?"))
        assertTrue(source.contains("val durationMs: Long"))
        assertFalse(source.contains("val date: String"))
        assertFalse(source.contains("val duration: String"))
    }

    @Test
    fun `background notification resources use the application language context`() {
        val backgroundFiles =
            listOf(
                "data/worker/DownloadWorker.kt",
                "data/worker/FeedUpdateWorker.kt",
                "service/PodcastPlaybackService.kt"
            )

        backgroundFiles.forEach { relativePath ->
            val source = sourceRoot.resolve(relativePath).readText()
            assertTrue(
                "$relativePath must use the application language context",
                source.contains("ContextCompat.getContextForLanguage")
            )
        }

        val downloadWorker = sourceRoot.resolve("data/worker/DownloadWorker.kt").readText()
        assertTrue(downloadWorker.contains("R.string.download_notification_content"))
        assertFalse(downloadWorker.contains("\$episodeTitle - \$progressText"))

        val playbackService = sourceRoot.resolve("service/PodcastPlaybackService.kt").readText()
        assertTrue(playbackService.contains("AppLocaleChangeNotifier.addListener"))
        assertTrue(playbackService.contains("override fun onConfigurationChanged"))
        assertTrue(playbackService.contains("channel.name = localizedContext.getString"))
    }

    @Test
    fun `playback errors remain resource backed until display`() {
        val contracts = sourceRoot.resolve("playback/api/PlayerContracts.kt").readText()
        val controller = sourceRoot.resolve("playback/infrastructure/AudioPlayerController.kt").readText()

        assertTrue(contracts.contains("val error: UiText?"))
        assertTrue(controller.contains("UiText.StringResource"))
        assertFalse(controller.contains("context.getString"))
    }

    private fun locateSourceRoot(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        var candidate: File? = File(workingDirectory).absoluteFile
        while (candidate != null) {
            val appSource = candidate.resolve("app/src/main/java/com/example/pocastcloni")
            if (appSource.isDirectory) return appSource

            val moduleSource = candidate.resolve("src/main/java/com/example/pocastcloni")
            if (moduleSource.isDirectory) return moduleSource
            candidate = candidate.parentFile
        }
        error("Could not locate app source root from ${System.getProperty("user.dir")}")
    }
}
