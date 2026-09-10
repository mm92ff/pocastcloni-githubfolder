package com.example.pocastcloni.ui.home.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeDownloadActionAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downloadingEpisodeExposesClickableCancelAction() {
        var downloadClicks = 0
        composeRule.setContent {
            MaterialTheme {
                EpisodeListItem(
                    episode = downloadingEpisode(),
                    isPlaying = false,
                    onPlayClick = {},
                    onDownloadClick = { downloadClicks += 1 },
                    onTogglePlayed = {},
                    onToggleFavorite = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Cancel episode download")
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, downloadClicks) }
    }

    private fun downloadingEpisode() =
        EpisodeUiModel(
            episodeId = 1L,
            guid = "episode-guid",
            podcastUrl = "https://example.com/feed.xml",
            title = "Episode",
            podcastTitle = "Podcast",
            imageUrl = null,
            downloadStatus = DownloadStatusUiModel.DOWNLOADING,
            downloadProgress = 0.5f,
            isPlayed = false,
            isFavorite = false,
            positionMs = 0L,
            description = null,
            podcastImageUrl = null,
            pubDateEpochMs = null,
            durationMs = 0L
        )
}
