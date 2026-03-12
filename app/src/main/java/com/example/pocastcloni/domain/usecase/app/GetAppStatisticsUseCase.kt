package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.domain.repository.AppStatistics
import com.example.pocastcloni.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAppStatisticsUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {
    operator fun invoke(): Flow<AppStatistics> {
        return statisticsRepository.statsFlow
    }
}
