package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.FeedUpdateRunner
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class FeedRefreshSource {
    STARTUP,
    BACKGROUND,
    MANUAL,
    BACKUP_RESTORE
}

private data class FeedRefreshRequest(
    val downloadLimit: Int,
    val mode: FeedUpdateMode,
    val forceFull: Boolean,
    val feedUrls: Set<String>?,
    val feedItemLimit: Int
)

private class RefreshFlight(
    val request: FeedRefreshRequest,
    val result: CompletableDeferred<PodcastUpdateSummary> = CompletableDeferred(),
    var waiters: Int = 0,
    var acceptingWaiters: Boolean = true,
    var job: Job? = null
)

@Singleton
class FeedRefreshCoordinator
@Inject
constructor(
    private val feedUpdateRunner: FeedUpdateRunner,
    private val preferences: UserPreferencesRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) {
    private val stateMutex = Mutex()
    private var activeFlight: RefreshFlight? = null
    private val pendingFlights = ArrayDeque<RefreshFlight>()

    suspend fun refresh(
        source: FeedRefreshSource,
        forceFull: Boolean = false,
        downloadLimitOverride: Int? = null,
        feedUrls: Set<String>? = null
    ): PodcastUpdateSummary {
        val settings = preferences.userSettingsFlow.first()
        val requiresFullRefresh =
            forceFull || source == FeedRefreshSource.MANUAL ||
                source == FeedRefreshSource.BACKUP_RESTORE ||
                settings.feedUpdateMode.requiresForceFullRefresh()
        val request =
            FeedRefreshRequest(
                downloadLimit = downloadLimitOverride ?: settings.autoDownloadLimit,
                mode = settings.feedUpdateMode,
                forceFull = requiresFullRefresh,
                feedUrls = feedUrls?.toSet(),
                feedItemLimit = settings.feedItemLimitFor(requiresFullRefresh)
            )

        val flight = registerWaiter(request)
        return try {
            flight.result.await()
        } finally {
            withContext(NonCancellable) {
                releaseWaiter(flight)
            }
        }
    }

    private suspend fun registerWaiter(request: FeedRefreshRequest): RefreshFlight =
        stateMutex.withLock {
            val active = activeFlight
            val flight =
                when {
                    active == null -> RefreshFlight(request).also(::startFlightLocked)
                    active.canSatisfy(request) -> active
                    else ->
                        pendingFlights.firstOrNull { it.canSatisfy(request) }
                            ?: RefreshFlight(request).also(pendingFlights::addLast)
                }
            flight.waiters += 1
            flight
        }

    private fun startFlightLocked(flight: RefreshFlight) {
        activeFlight = flight
        flight.job =
            applicationScope.launch(dispatcherProvider.io) {
                execute(flight)
            }
    }

    private suspend fun execute(flight: RefreshFlight) {
        try {
            val summary =
                feedUpdateRunner.updateAllPodcasts(
                    downloadLimit = flight.request.downloadLimit,
                    mode = flight.request.mode,
                    forceFull = flight.request.forceFull,
                    feedUrls = flight.request.feedUrls,
                    feedItemLimit = flight.request.feedItemLimit
                )
            withContext(NonCancellable) { finishFlight(flight) }
            flight.result.complete(summary)
        } catch (error: CancellationException) {
            withContext(NonCancellable) { finishFlight(flight) }
            flight.result.cancel(error)
            throw error
        } catch (error: Throwable) {
            withContext(NonCancellable) { finishFlight(flight) }
            flight.result.completeExceptionally(error)
        }
    }

    private suspend fun finishFlight(flight: RefreshFlight) {
        stateMutex.withLock {
            if (activeFlight !== flight) return
            activeFlight = null

            val next =
                generateSequence { pendingFlights.removeFirstOrNull() }
                    .firstOrNull { it.waiters > 0 }
            if (next != null) startFlightLocked(next)
        }
    }

    private suspend fun releaseWaiter(flight: RefreshFlight) {
        val jobToCancel =
            stateMutex.withLock {
                flight.waiters = (flight.waiters - 1).coerceAtLeast(0)
                when {
                    flight.waiters == 0 && pendingFlights.remove(flight) -> {
                        flight.result.cancel()
                        null
                    }

                    activeFlight === flight && flight.waiters == 0 && !flight.result.isCompleted -> {
                        flight.acceptingWaiters = false
                        flight.job
                    }

                    else -> null
                }
            }
        jobToCancel?.cancel()
    }

    private fun RefreshFlight.canSatisfy(request: FeedRefreshRequest): Boolean {
        val hasMatchingConfiguration =
            this.request.downloadLimit == request.downloadLimit &&
                this.request.mode == request.mode &&
                this.request.feedUrls == request.feedUrls &&
                this.request.feedItemLimit == request.feedItemLimit
        return when {
            !acceptingWaiters -> false
            !hasMatchingConfiguration -> false
            else -> this.request.forceFull || !request.forceFull
        }
    }
}

internal fun UserSettings.feedItemLimitFor(forceFull: Boolean): Int =
    smartStreamItemLimit
        .takeIf {
            !forceFull &&
                feedUpdateMode == FeedUpdateMode.SMART_STREAM &&
                it > Constants.Preferences.DEFAULT_SMART_STREAM_ITEM_LIMIT
        } ?: Constants.SecurityLimits.MAX_FEED_ITEMS
