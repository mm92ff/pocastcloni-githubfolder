package com.example.pocastcloni.playback.infrastructure

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.pocastcloni.service.PodcastPlaybackService
import com.google.common.util.concurrent.ListenableFuture
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
 * Manages the low-level connection to the MediaSessionService.
 * Hides ListenableFuture complexity and connection retries.
 */
@Singleton
@Suppress("TooGenericExceptionCaught")
class MediaControllerConnection
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val RETRY_DELAY_MS = 500L
        private const val MAX_RETRIES = 20
    }

    private val connectMutex = Mutex()
    private val stateLock = Any()
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var connectingAttempt: MediaControllerConnectionAttempt? = null
    private var onDisconnected: ((MediaController) -> Unit)? = null

    @Volatile
    var activeController: MediaController? = null
        private set

    fun setOnDisconnected(listener: (MediaController) -> Unit) {
        synchronized(stateLock) { onDisconnected = listener }
    }

    suspend fun connect(): MediaController? =
        connectMutex.withLock {
            activeController?.let { return@withLock it }

            val sessionToken =
                SessionToken(
                    context,
                    ComponentName(context, PodcastPlaybackService::class.java)
                )

            var attempt = 0
            while (attempt < MAX_RETRIES) {
                val connection = startConnectionAttempt(sessionToken) ?: return@withLock null

                try {
                    val controller = connection.future.await()
                    val decision = recordAwaitedController(connection, controller)
                    when (decision) {
                        AwaitedControllerDecision.ACCEPTED -> return@withLock controller
                        AwaitedControllerDecision.INVALIDATED -> {
                            releaseFuture(connection.future)
                            return@withLock null
                        }
                        AwaitedControllerDecision.RETRY -> {
                            releaseFuture(connection.future)
                            attempt++
                            if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    clearFuture(connection.future, connection.attempt)
                    throw cancellation
                } catch (error: Exception) {
                    Timber.w(error, "Connection attempt ${attempt + 1} failed")
                    clearFuture(connection.future, connection.attempt)
                    attempt++
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
                }
            }

            Timber.e("Failed to connect to MediaController after $MAX_RETRIES attempts")
            null
        }

    private fun startConnectionAttempt(sessionToken: SessionToken): StartedControllerConnection? {
        val connectionAttempt = MediaControllerConnectionAttempt()
        synchronized(stateLock) { connectingAttempt = connectionAttempt }
        val future =
            MediaController.Builder(context, sessionToken)
                .setListener(
                    object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            handleDisconnected(controller, connectionAttempt)
                        }
                    }
                ).buildAsync()
        val futureAttached =
            synchronized(stateLock) {
                if (connectingAttempt === connectionAttempt) {
                    mediaControllerFuture = future
                    true
                } else {
                    false
                }
            }
        if (!futureAttached) {
            releaseFuture(future)
            return null
        }
        return StartedControllerConnection(connectionAttempt, future)
    }

    private fun recordAwaitedController(
        connection: StartedControllerConnection,
        controller: MediaController
    ): AwaitedControllerDecision =
        synchronized(stateLock) {
            when {
                mediaControllerFuture !== connection.future || connectingAttempt !== connection.attempt ->
                    AwaitedControllerDecision.INVALIDATED
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

    fun release() {
        val (controller, future) =
            synchronized(stateLock) {
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

    private fun clearFuture(
        future: ListenableFuture<MediaController>,
        connectionAttempt: MediaControllerConnectionAttempt
    ) {
        val shouldRelease =
            synchronized(stateLock) {
                if (mediaControllerFuture !== future || connectingAttempt !== connectionAttempt) {
                    false
                } else {
                    mediaControllerFuture = null
                    connectingAttempt = null
                    true
                }
            }
        if (shouldRelease) releaseFuture(future)
    }

    private fun releaseFuture(future: ListenableFuture<MediaController>) {
        runCatching { MediaController.releaseFuture(future) }
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
