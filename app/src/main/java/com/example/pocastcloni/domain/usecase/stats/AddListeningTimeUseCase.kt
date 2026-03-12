package com.example.pocastcloni.domain.usecase.stats

import com.example.pocastcloni.domain.repository.StatisticsRepository
import javax.inject.Inject

class AddListeningTimeUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {
    suspend operator fun invoke(time: Long) {
        if (time > 0) {
            statisticsRepository.addListeningTime(time)
        }
    }
}
