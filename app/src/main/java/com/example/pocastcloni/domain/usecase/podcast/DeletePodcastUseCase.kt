package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.FeedSyncStore
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastRemovalGateway
import javax.inject.Inject

class DeletePodcastUseCase
@Inject
constructor(
    private val feedSyncStore: FeedSyncStore,
    private val podcastCommands: PodcastCommandPort,
    private val removalGateway: PodcastRemovalGateway
) {
    suspend operator fun invoke(podcast: Podcast) {
        val episodes = feedSyncStore.getEpisodesForSync(podcast.rssUrl)
        removalGateway.cancelActiveDownloads(episodes)
        podcastCommands.deletePodcast(podcast)
        removalGateway.deleteDownloadedFiles(episodes)
        removalGateway.deletePodcastCover(podcast.rssUrl)
    }
}
