package com.example.pocastcloni.playback.infrastructure

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.pocastcloni.service.PodcastPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the process-level connection to [PodcastPlaybackService].
 *
 * [connect] calls are serialized and share an active controller. Each failed construction or
 * asynchronous future consumes one retry and uses the same cleanup, logging, and backoff path.
 * Coroutine cancellation is propagated unless [release] invalidated the attempt and caused the
 * future cancellation itself.
 *
 * Every [release] advances a lifecycle generation. Attempts from an older generation may finish,
 * but identity and generation checks prevent them from publishing a controller or beginning a
 * later retry. Release is intentionally reusable rather than terminal: a later [connect] captures
 * the new generation and may establish a fresh controller.
 */
@Singleton
@Suppress("TooGenericExceptionCaught")
class MediaControllerConnection private constructor(
    private val controllerFutureFactory: MediaControllerFutureFactory,
    private val retryPolicy: ControllerConnectionRetryPolicy
) {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        controllerFutureFactory = defaultControllerFutureFactory(context),
        retryPolicy = ControllerConnectionRetryPolicy(RETRY_DELAY_MS, MAX_RETRIES)
    )

    internal constructor(
        controllerFutureFactory: MediaControllerFutureFactory,
        retryDelayMs: Long,
        maxAttempts: Int
    ) : this(
        controllerFutureFactory = controllerFutureFactory,
        retryPolicy = ControllerConnectionRetryPolicy(retryDelayMs, maxAttempts)
    )

    private companion object {
        private const val RETRY_DELAY_MS = 500L
        private const val MAX_RETRIES = 20

        private fun defaultControllerFutureFactory(context: Context) =
            MediaControllerFutureFactory { listener ->
                val sessionToken =
                    SessionToken(
                        context,
                        ComponentName(context, PodcastPlaybackService::class.java)
                    )
                MediaController.Builder(context, sessionToken)
                    .setListener(listener)
                    .buildAsync()
            }
    }

    private val connectMutex = Mutex()
    private val stateLock = Any()
    private var lifecycleGeneration = 0L
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var connectingAttempt: MediaControllerConnectionAttempt? = null
    private var onDisconnected: ((MediaController) -> Unit)? = null

    @Volatile
    var activeController: MediaController? = null
        private set

    internal val hasConnectingAttempt: Boolean
        get() = synchronized(stateLock) { connectingAttempt != null }

    fun setOnDisconnected(listener: (MediaController) -> Unit) {
        synchronized(stateLock) { onDisconnected = listener }
    }

    /**
     * Returns the active controller or retries construction within the caller's coroutine.
     *
     * The caller owns cancellation of this suspended operation. A concurrent [release] invalidates
     * its generation, releases any attached future, and makes this call return `null` without
     * allowing an older retry loop to continue.
     */
    suspend fun connect(): MediaController? =
        connectMutex.withLock {
            activeController?.let { return@withLock it }
            val connectGeneration = synchronized(stateLock) { lifecycleGeneration }

            var attemptNumber = 1
            while (attemptNumber <= retryPolicy.maxAttempts) {
                if (!isCurrentGeneration(connectGeneration)) return@withLock null

                val connectionAttempt = MediaControllerConnectionAttempt()
                var future: ListenableFuture<MediaController>? = null
                try {
                    val connection =
                        startConnectionAttempt(connectionAttempt, connectGeneration)
                            ?: return@withLock null
                    future = connection.future
                    val controller = connection.future.await()
                    when (recordAwaitedController(connection, controller, connectGeneration)) {
                        AwaitedControllerDecision.ACCEPTED -> return@withLock controller
                        AwaitedControllerDecision.INVALIDATED -> return@withLock null
                        AwaitedControllerDecision.RETRY -> {
                            releaseFuture(connection.future)
                            attemptNumber++
                            delayBeforeRetry(attemptNumber, connectGeneration)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    clearConnectionAttempt(connectionAttempt, future)
                    if (!isCurrentGeneration(connectGeneration)) return@withLock null
                    throw cancellation
                } catch (error: Exception) {
                    Timber.w(error, "Connection attempt $attemptNumber failed")
                    clearConnectionAttempt(connectionAttempt, future)
                    if (!isCurrentGeneration(connectGeneration)) return@withLock null
                    attemptNumber++
                    delayBeforeRetry(attemptNumber, connectGeneration)
                }
            }

            Timber.e("Failed to connect to MediaController after ${retryPolicy.maxAttempts} attempts")
            null
        }

    private suspend fun delayBeforeRetry(
        nextAttemptNumber: Int,
        connectGeneration: Long
    ) {
        if (nextAttemptNumber <= retryPolicy.maxAttempts && isCurrentGeneration(connectGeneration)) {
            delay(retryPolicy.delayMs)
        }
    }

    private fun startConnectionAttempt(
        connectionAttempt: MediaControllerConnectionAttempt,
        connectGeneration: Long
    ): StartedControllerConnection? {
        val attemptStarted =
            synchronized(stateLock) {
                if (lifecycleGeneration != connectGeneration) {
                    false
                } else {
                    connectingAttempt = connectionAttempt
                    true
                }
            }
        return if (!attemptStarted) {
            null
        } else {
            val future =
                controllerFutureFactory.build(
                    object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            handleDisconnected(controller, connectionAttempt)
                        }
                    }
                )
            val futureAttached =
                synchronized(stateLock) {
                    if (
                        lifecycleGeneration == connectGeneration &&
                        connectingAttempt === connectionAttempt
                    ) {
                        mediaControllerFuture = future
                        true
                    } else {
                        false
                    }
                }
            if (futureAttached) {
                StartedControllerConnection(connectionAttempt, future)
            } else {
                releaseFuture(future)
                null
            }
        }
    }

    private fun recordAwaitedController(
        connection: StartedControllerConnection,
        controller: MediaController,
        connectGeneration: Long
    ): AwaitedControllerDecision =
        synchronized(stateLock) {
            when {
                lifecycleGeneration != connectGeneration ||
                    mediaControllerFuture !== connection.future ||
                    connectingAttempt !== connection.attempt -> AwaitedControllerDecision.INVALIDATED
                connection.attempt.awaitedControllerDecision(controller) == AwaitedControllerDecision.RETRY -> {
                    mediaControllerFuture = null
                    connectingAttempt = null
                    AwaitedControllerDecision.RETRY
                }
                else -> {
                    activeController = controller
                    connectingAttempt = null
                    AwaitedControllerDecision.ACCEPTED
                }
            }
        }

    /**
     * Releases active and in-flight resources and invalidates delayed retries.
     *
     * This operation does not close a permanent scope or terminally disable the connection; a
     * subsequent [connect] starts in the newly advanced lifecycle generation.
     */
    fun release() {
        val (controller, future) =
            synchronized(stateLock) {
                lifecycleGeneration += 1L
                val current = activeController
                val currentFuture = mediaControllerFuture
                activeController = null
                mediaControllerFuture = null
                connectingAttempt = null
                current to currentFuture
            }
        controller?.let {
            runCatching { it.release() }
                .onFailure { error ->
                    Timber.w(error, "Failed to release active MediaController cleanly")
                }
        }
        future?.let(::releaseFuture)
    }

    private fun handleDisconnected(
        disconnectedController: MediaController,
        connectionAttempt: MediaControllerConnectionAttempt
    ) {
        var future: ListenableFuture<MediaController>? = null
        var callback: ((MediaController) -> Unit)? = null
        synchronized(stateLock) {
            if (activeController === disconnectedController) {
                activeController = null
                future = mediaControllerFuture
                mediaControllerFuture = null
                connectingAttempt = null
                callback = onDisconnected
            } else if (connectingAttempt === connectionAttempt) {
                connectionAttempt.onDisconnected(disconnectedController)
            }
        }
        future?.let(::releaseFuture)
        callback?.invoke(disconnectedController)
    }

    private fun clearConnectionAttempt(
        connectionAttempt: MediaControllerConnectionAttempt,
        future: ListenableFuture<MediaController>?
    ) {
        val shouldRelease =
            synchronized(stateLock) {
                if (connectingAttempt !== connectionAttempt) {
                    false
                } else {
                    connectingAttempt = null
                    if (future != null && mediaControllerFuture === future) {
                        mediaControllerFuture = null
                        true
                    } else {
                        false
                    }
                }
            }
        if (shouldRelease && future != null) releaseFuture(future)
    }

    private fun isCurrentGeneration(connectGeneration: Long): Boolean =
        synchronized(stateLock) { lifecycleGeneration == connectGeneration }

    private fun releaseFuture(future: ListenableFuture<MediaController>) {
        val terminalAfterRelease =
            runCatching {
                MediaController.releaseFuture(future)
                future.isDone
            }.getOrDefault(false)
        if (!terminalAfterRelease) {
            future.addListener(
                {
                    runCatching { future.get().release() }
                        .onFailure { error -> Timber.w(error, "Late MediaController could not be released") }
                },
                MoreExecutors.directExecutor()
            )
        }
    }
}

/** Builds the complete controller future so JVM tests can control synchronous and async failure. */
internal fun interface MediaControllerFutureFactory {
    fun build(listener: MediaController.Listener): ListenableFuture<MediaController>
}

private data class ControllerConnectionRetryPolicy(
    val delayMs: Long,
    val maxAttempts: Int
) {
    init {
        require(delayMs >= 0L) { "Retry delay must not be negative" }
        require(maxAttempts > 0) { "At least one connection attempt is required" }
    }
}

private data class StartedControllerConnection(
    val attempt: MediaControllerConnectionAttempt,
    val future: ListenableFuture<MediaController>
)

internal class MediaControllerConnectionAttempt {
    private var disconnectedController: MediaController? = null

    fun onDisconnected(controller: MediaController) {
        disconnectedController = controller
    }

    fun awaitedControllerDecision(controller: MediaController): AwaitedControllerDecision =
        if (disconnectedController === controller) {
            AwaitedControllerDecision.RETRY
        } else {
            AwaitedControllerDecision.ACCEPTED
        }
}

internal enum class AwaitedControllerDecision {
    ACCEPTED,
    RETRY,
    INVALIDATED
}
