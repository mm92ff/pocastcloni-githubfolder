package com.example.pocastcloni.data.worker

import com.example.pocastcloni.data.repository.AppResetMarkerStore
import com.example.pocastcloni.domain.repository.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSchedulingCoordinatorTest {
    private val dispatcher = StandardTestDispatcher()
    private val markerStore = mockk<AppResetMarkerStore>()
    private val backgroundScheduler = mockk<BackgroundSyncScheduler>(relaxed = true)
    private val cleanupScheduler = mockk<LibraryCleanupScheduler>(relaxed = true)
    private val coordinator =
        AppSchedulingCoordinator(
            markerStore,
            backgroundScheduler,
            cleanupScheduler
        )

    @Test
    fun `reset reconciles both schedulers before clearing marker and drops racing observer`() =
        runTest(dispatcher) {
            val resetStarted = CompletableDeferred<Unit>()
            val allowResetToFinish = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            every { markerStore.isPending() } returns false
            coEvery { backgroundScheduler.applySettings(any(), any()) } answers {
                events += "background:${firstArg<Boolean>()}:${secondArg<Int>()}"
            }
            coEvery { cleanupScheduler.applySettings(any(), any()) } answers {
                events += "cleanup:${firstArg<Boolean>()}:${secondArg<Int>()}"
            }
            every { markerStore.clear() } answers { events += "marker-cleared" }

            val resetJob =
                launch {
                    coordinator.runResetAndReconcile(
                        reset = {
                            events += "reset"
                            resetStarted.complete(Unit)
                            allowResetToFinish.await()
                        },
                        loadFinalSettings = { UserSettings() },
                        completeReset = markerStore::clear
                    )
                }
            resetStarted.await()
            val racingObserver =
                launch {
                    coordinator.applyObservedBackgroundSettings(
                        enabled = false,
                        intervalHours = 12
                    )
                }
            runCurrent()

            allowResetToFinish.complete(Unit)
            resetJob.join()
            racingObserver.join()

            assertEquals(
                listOf(
                    "reset",
                    "background:true:1",
                    "cleanup:true:24",
                    "marker-cleared"
                ),
                events
            )
            coVerify(exactly = 0) { backgroundScheduler.applySettings(false, 12) }
        }

    @Test
    fun `pending reset marker suppresses settings observers`() = runTest(dispatcher) {
        every { markerStore.isPending() } returns true

        coordinator.applyObservedBackgroundSettings(true, 1)
        coordinator.applyObservedCleanupSettings(true, 24)

        coVerify(exactly = 0) { backgroundScheduler.applySettings(any(), any()) }
        coVerify(exactly = 0) { cleanupScheduler.applySettings(any(), any()) }
    }
}
