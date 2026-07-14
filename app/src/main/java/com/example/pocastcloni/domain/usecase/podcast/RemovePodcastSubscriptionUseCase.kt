package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RemovePodcastSubscriptionUseCase
@Inject
constructor(
    private val podcastCommands: PodcastCommandPort,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(url: String) {
        withContext(dispatcherProvider.io) {
            podcastCommands.removePodcastByUrl(url)
        }
    }
}
