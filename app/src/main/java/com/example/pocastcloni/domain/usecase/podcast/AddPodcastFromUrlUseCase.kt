package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.FeedSyncRunner
import com.example.pocastcloni.domain.repository.FeedSyncStore
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AddPodcastFromUrlUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort,
    private val feedSyncStore: FeedSyncStore,
    private val feedSyncRunner: FeedSyncRunner,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(
        url: String,
        allowInsecureHttp: Boolean = false,
        allowLocalNetwork: Boolean = false
    ) {
        withContext(dispatcherProvider.io) {
            // 1. Load current settings
            val settings = userPreferencesRepository.userSettingsFlow.first()

            val normalizedUrl = url.trim()
            val existing = podcastQuery.getPodcast(normalizedUrl)
            if (existing != null) {
                val shouldApproveInsecureHttp = allowInsecureHttp && !existing.allowInsecureHttp
                val shouldApproveLocalNetwork = allowLocalNetwork && !existing.allowLocalNetwork
                if (!shouldApproveInsecureHttp && !shouldApproveLocalNetwork) return@withContext

                feedSyncStore.approvePodcastNetworkAccess(
                    rssUrl = normalizedUrl,
                    allowInsecureHttp = allowInsecureHttp,
                    allowLocalNetwork = allowLocalNetwork
                )
            }

            feedSyncRunner.sync(
                url = normalizedUrl,
                downloadLimit = settings.autoDownloadLimit,
                mode = settings.feedUpdateMode,
                sortOrder = existing?.sortOrder,
                forceFull = false,
                allowInsecureHttp = existing?.allowInsecureHttp == true || allowInsecureHttp,
                allowLocalNetwork = existing?.allowLocalNetwork == true || allowLocalNetwork
            )
        }
    }
}
