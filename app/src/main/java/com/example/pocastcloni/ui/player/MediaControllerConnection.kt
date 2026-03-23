package com.example.pocastcloni.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.pocastcloni.service.PodcastPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the low-level connection to the MediaSessionService.
 * Hides ListenableFuture complexity and connection retries.
 */
@Singleton
class MediaControllerConnection
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val RETRY_DELAY_MS = 500L
        private const val MAX_RETRIES = 20
    }

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    // We keep a reference to the active controller to avoid re-connecting if alive
    var activeController: MediaController? = null
        private set

    suspend fun connect(): MediaController? {
        if (activeController != null) return activeController
        if (mediaControllerFuture != null) {
            // Already connecting, wait for that one
            return try {
                mediaControllerFuture?.await()
            } catch (e: Exception) {
                null
            }
        }

        val sessionToken =
            SessionToken(
                context,
                ComponentName(context, PodcastPlaybackService::class.java)
            )

        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                val future = MediaController.Builder(context, sessionToken).buildAsync()
                mediaControllerFuture = future

                val controller = future.await()
                activeController = controller
                return controller
            } catch (t: Throwable) {
                Timber.w(t, "Connection attempt ${attempt + 1} failed")
                releaseFuture()
                attempt++
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        Timber.e("Failed to connect to MediaController after $MAX_RETRIES attempts")
        return null
    }

    fun release() {
        activeController?.let { controller ->
            runCatching { controller.release() }
                .onFailure { error ->
                    Timber.w(error, "Failed to release active MediaController cleanly")
                }
        }
        releaseFuture()
        activeController = null
    }

    private fun releaseFuture() {
        mediaControllerFuture?.let {
            runCatching { MediaController.releaseFuture(it) }
        }
        mediaControllerFuture = null
    }
}
