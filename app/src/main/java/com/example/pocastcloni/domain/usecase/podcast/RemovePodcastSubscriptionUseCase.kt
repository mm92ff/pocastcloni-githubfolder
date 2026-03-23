package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RemovePodcastSubscriptionUseCase
@Inject
constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(url: String) {
        withContext(dispatcherProvider.io) {
            repository.removePodcastByUrl(url)
        }
    }
}
