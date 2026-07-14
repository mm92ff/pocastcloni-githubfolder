package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.AppResetGateway
import com.example.pocastcloni.domain.repository.LibraryMaintenancePort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ResetAppUseCase
@Inject
constructor(
    private val maintenance: LibraryMaintenancePort,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val resetGateway: AppResetGateway,
    private val dispatcherProvider: DispatcherProvider
) {
    private val resetMutex = Mutex()

    suspend operator fun invoke() {
        withContext(dispatcherProvider.io) {
            resetMutex.withLock {
                resetGateway.runResetAndReconcile(
                    clearSettings = userPreferencesRepository::clearSettings,
                    resetDatabase = maintenance::resetDatabase,
                    loadFinalSettings = { userPreferencesRepository.userSettingsFlow.first() },
                    markPending = true
                )
            }
        }
    }

    suspend fun resumeIfPending(): Boolean =
        withContext(dispatcherProvider.io) {
            resetMutex.withLock {
                if (!resetGateway.isPending()) return@withLock false
                resetGateway.runResetAndReconcile(
                    clearSettings = userPreferencesRepository::clearSettings,
                    resetDatabase = maintenance::resetDatabase,
                    loadFinalSettings = { userPreferencesRepository.userSettingsFlow.first() },
                    markPending = false
                )
                true
            }
        }
}
