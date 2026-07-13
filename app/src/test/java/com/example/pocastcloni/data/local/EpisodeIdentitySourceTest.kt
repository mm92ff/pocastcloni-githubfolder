package com.example.pocastcloni.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpisodeIdentitySourceTest {
    @Test
    fun runtimeEpisodeMutationsUseInternalIds() {
        val daoSource = File("src/main/java/com/example/pocastcloni/data/local/PodcastDao.kt").readText()
        val repositorySource = File("src/main/java/com/example/pocastcloni/domain/repository/PodcastRepository.kt").readText()

        assertTrue(daoSource.contains("WHERE episodeId = :episodeId"))
        assertFalse(Regex("WHERE\\s+guid\\s*=\\s*:guid", RegexOption.IGNORE_CASE).containsMatchIn(daoSource))
        assertFalse(repositorySource.contains("suspend fun getEpisode(guid: String)"))
        assertFalse(repositorySource.contains("fun isFavorite(guid: String)"))
    }

    @Test
    fun missingFeedGuidDoesNotUseWallClockIdentity() {
        val mapperSource = File("src/main/java/com/example/pocastcloni/data/repository/PodcastMappers.kt").readText()
        val fallbackBody = mapperSource.substringAfter("private fun RssItem.stableEpisodeGuid")
            .substringBefore("private fun sanitizeDate")

        assertTrue(fallbackBody.contains("SHA-256"))
        assertFalse(fallbackBody.contains("System.currentTimeMillis"))
    }

    @Test
    fun legacyDownloadWorkIsResolvedBeforeUsingAnEpisodeId() {
        val workerSource = File("src/main/java/com/example/pocastcloni/data/worker/DownloadWorker.kt").readText()

        assertTrue(workerSource.contains("DOWNLOAD_WORKER_LEGACY_GUID"))
        assertTrue(workerSource.contains("resolveLegacyDownloadEpisode"))
    }

    @Test
    fun everyDownloadEntryPointReconcilesLegacyWorkNames() {
        val manualSource =
            File("src/main/java/com/example/pocastcloni/domain/usecase/episode/DownloadEpisodeUseCase.kt")
                .readText()
        val automaticSource =
            File("src/main/java/com/example/pocastcloni/data/manager/PodcastDownloader.kt")
                .readText()
        val deletionSource =
            File("src/main/java/com/example/pocastcloni/domain/usecase/podcast/DeletePodcastUseCase.kt")
                .readText()

        assertTrue(manualSource.contains("cancelLegacyDownloadWork(episode.guid)"))
        assertTrue(manualSource.contains("cancelEpisodeDownloadWork(episode.episodeId, episode.guid)"))
        assertTrue(automaticSource.contains("cancelLegacyDownloadWork(episode.guid)"))
        assertTrue(automaticSource.contains("cancelEpisodeDownloadWork(episode.episodeId, episode.guid)"))
        assertTrue(deletionSource.contains("cancelEpisodeDownloadWork(ep.episodeId, ep.guid)"))
    }
}
