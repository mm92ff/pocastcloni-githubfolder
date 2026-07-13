package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

class MarkEpisodePlayedUseCase
@Inject
constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(episodeId: Long) {
        withContext(dispatcherProvider.io) {
            repository.markEpisodePlayed(episodeId, true, Date())
        }
    }
}
