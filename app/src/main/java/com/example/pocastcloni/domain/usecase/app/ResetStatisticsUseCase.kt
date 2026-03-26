package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.StatisticsRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ResetStatisticsUseCase
@Inject
constructor(
    private val statisticsRepository: StatisticsRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke() {
        withContext(dispatcherProvider.io) {
            statisticsRepository.resetStatistics()
        }
    }
}
