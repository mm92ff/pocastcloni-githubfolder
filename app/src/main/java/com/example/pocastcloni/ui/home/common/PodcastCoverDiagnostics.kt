package com.example.pocastcloni.ui.home.common

import coil.EventListener
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import timber.log.Timber

internal enum class PodcastCoverLoadOutcome {
    SUCCESS,
    ERROR,
    CANCELLED
}

internal data class PodcastCoverLoadEvent(
    val diagnosticId: String,
    val pixelSize: Int,
    val dataSource: DataSource?,
    val outcome: PodcastCoverLoadOutcome,
    val durationMs: Long,
    val failureCategory: String? = null
)

internal class PodcastCoverLoadTracker(
    private val tag: PodcastCoverRequestTag,
    private val clockNanos: () -> Long,
    private val reporter: (PodcastCoverLoadEvent) -> Unit
) {
    private var startNanos = clockNanos()
    private var completed = false

    fun start() {
        startNanos = clockNanos()
    }

    fun success(dataSource: DataSource) {
        reportOnce(
            outcome = PodcastCoverLoadOutcome.SUCCESS,
            dataSource = dataSource
        )
    }

    fun error(throwable: Throwable) {
        reportOnce(
            outcome = PodcastCoverLoadOutcome.ERROR,
            dataSource = null,
            failureCategory = throwable.javaClass.simpleName.takeIf(String::isNotBlank) ?: "Unknown"
        )
    }

    fun cancel() {
        reportOnce(
            outcome = PodcastCoverLoadOutcome.CANCELLED,
            dataSource = null
        )
    }

    private fun reportOnce(
        outcome: PodcastCoverLoadOutcome,
        dataSource: DataSource?,
        failureCategory: String? = null
    ) {
        if (completed) return
        completed = true
        val elapsedNanos = (clockNanos() - startNanos).coerceAtLeast(0L)
        reporter(
            PodcastCoverLoadEvent(
                diagnosticId = tag.diagnosticId,
                pixelSize = tag.pixelSize,
                dataSource = dataSource,
                outcome = outcome,
                durationMs = elapsedNanos / NANOS_PER_MILLISECOND,
                failureCategory = failureCategory
            )
        )
    }
}

internal class PodcastCoverEventListenerFactory(
    private val enabled: Boolean,
    private val clockNanos: () -> Long = System::nanoTime,
    private val reporter: (PodcastCoverLoadEvent) -> Unit = ::logPodcastCoverLoadEvent
) : EventListener.Factory {
    override fun create(request: ImageRequest): EventListener =
        if (enabled) {
            request.tags.tag<PodcastCoverRequestTag>()?.let(::createListener) ?: EventListener.NONE
        } else {
            EventListener.NONE
        }

    private fun createListener(tag: PodcastCoverRequestTag): EventListener {
        val tracker = PodcastCoverLoadTracker(tag, clockNanos, reporter)
        return object : EventListener {
            override fun onStart(request: ImageRequest) {
                tracker.start()
            }

            override fun onSuccess(
                request: ImageRequest,
                result: SuccessResult
            ) {
                tracker.success(result.dataSource)
            }

            override fun onError(
                request: ImageRequest,
                result: ErrorResult
            ) {
                tracker.error(result.throwable)
            }

            override fun onCancel(request: ImageRequest) {
                tracker.cancel()
            }
        }
    }
}

private fun logPodcastCoverLoadEvent(event: PodcastCoverLoadEvent) {
    Timber.tag("PodcastCover").d(
        "id=%s size=%d source=%s outcome=%s durationMs=%d failure=%s",
        event.diagnosticId,
        event.pixelSize,
        event.dataSource?.name ?: "NONE",
        event.outcome.name,
        event.durationMs,
        event.failureCategory ?: "NONE"
    )
}

private const val NANOS_PER_MILLISECOND = 1_000_000L
