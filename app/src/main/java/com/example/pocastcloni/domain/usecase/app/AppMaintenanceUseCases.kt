package com.example.pocastcloni.domain.usecase.app

import javax.inject.Inject

data class AppMaintenanceUseCases
@Inject
constructor(
    val manageBackup: ManageBackupUseCase,
    val manualFeedUpdate: ManualFeedUpdateUseCase,
    val resetApp: ResetAppUseCase,
    val resetStatistics: ResetStatisticsUseCase
)
