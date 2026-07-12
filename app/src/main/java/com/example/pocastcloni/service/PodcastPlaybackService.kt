package com.example.pocastcloni.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.StatisticsRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.ui.main.MainActivity
import com.example.pocastcloni.util.ConnectivityProvider
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PodcastPlaybackService : MediaSessionService() {
    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var podcastRepository: PodcastRepository

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject lateinit var statisticsRepository: StatisticsRepository

    @Inject lateinit var connectivityProvider: ConnectivityProvider

    private lateinit var serviceScope: CoroutineScope

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private var cache: Cache? = null
    private lateinit var mediaSourceFactory: ProgressiveMediaSource.Factory
    private lateinit var sessionActivityPendingIntent: PendingIntent

    private val audioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
    }

    private var currentBufferMode: BufferMode? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(dispatcherProvider.main + SupervisorJob())

        // One-time setup (cache, data source, notification provider, session activity intent)
        initializeStaticComponents()

        // React to buffer mode changes while the service stays alive (e.g. playback ongoing).
        // Only rebuild the player when bufferMode actually changes.
        serviceScope.launch {
            userPreferencesRepository.userSettingsFlow
                .map { it.bufferMode }
                .distinctUntilChanged()
                .catch { e ->
                    Timber.e(e, "Failed to observe UserSettings. Falling back to NORMAL buffer mode.")
                    emit(BufferMode.NORMAL)
                }
                .collectLatest { newMode ->
                    if (currentBufferMode != newMode) {
                        currentBufferMode = newMode
                        recreatePlayer(newMode)
                    }
                }
        }
    }

    private fun initializeStaticComponents() {
        cache =
            try {
                val cacheFolder = File(cacheDir, "media_cache")
                SimpleCache(cacheFolder, NoOpCacheEvictor(), StandaloneDatabaseProvider(this))
            } catch (t: Throwable) {
                Timber.e(t, "Cache init failed - continuing without cache")
                null
            }

        // Listener for statistics (counts streamed bytes)
        val statsListener = StreamingStatsListener()

        // Attach stats listener to HTTP data source
        val httpDataSourceFactory =
            DefaultHttpDataSource.Factory()
                .setTransferListener(statsListener)

        // DefaultDataSource wraps HTTP + file
        val upstreamFactory: DataSource.Factory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // Optional cache layer
        val cacheFactory: DataSource.Factory =
            cache?.let {
                CacheDataSource.Factory()
                    .setCache(it)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            } ?: upstreamFactory

        mediaSourceFactory =
            ProgressiveMediaSource.Factory(cacheFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy())

        // Session activity intent for notification
        val intent =
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, MainActivity::class.java)

        sessionActivityPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val notificationProvider =
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(Constants.Notification.CHANNEL_PLAYBACK_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
        setMediaNotificationProvider(notificationProvider)
    }

    /**
     * Rebuilds the player (LoadControl/buffer config) and swaps it into the existing MediaSession.
     * Keeps playback as seamless as possible by restoring the queue & state.
     */
    private fun recreatePlayer(bufferMode: BufferMode) {
        val oldPlayer: ExoPlayer? = if (this::player.isInitialized) player else null
        val snapshot = oldPlayer?.toSnapshot()

        oldPlayer?.release()

        val loadControl = createLoadControl(bufferMode)
        val newPlayer =
            ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .build()

        player = newPlayer

        if (this::mediaSession.isInitialized) {
            // Media3 supports swapping players on an existing MediaSession.
            runCatching { mediaSession.player = newPlayer }
                .onFailure { t ->
                    Timber.w(t, "Failed to swap player on MediaSession; recreating MediaSession")
                    runCatching { mediaSession.release() }
                    mediaSession = buildMediaSession(newPlayer)
                }
        } else {
            mediaSession = buildMediaSession(newPlayer)
        }

        snapshot?.restoreTo(newPlayer)
    }

    private fun buildMediaSession(player: ExoPlayer): MediaSession {
        return MediaSession.Builder(this, player)
            .setCallback(CustomMediaSessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    private fun createLoadControl(mode: BufferMode): LoadControl {
        val (minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs) =
            when (mode) {
                BufferMode.NORMAL -> {
                    Quad(
                        Constants.Player.NORMAL_BUFFER_DURATION_MS,
                        Constants.Player.MAX_BUFFER_DURATION_MS,
                        Constants.Player.MAX_BUFFER_FOR_PLAYBACK_MS,
                        Constants.Player.MAX_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                    )
                }

                BufferMode.MAXIMAL -> {
                    Quad(
                        Constants.Player.MAX_BUFFER_DURATION_MS,
                        Constants.Player.MAXIMAL_BUFFER_DURATION_MS,
                        Constants.Player.MAX_BUFFER_FOR_PLAYBACK_MS,
                        Constants.Player.MAX_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                    )
                }
            }

        return DefaultLoadControl.Builder()
            // Correct order: (minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs
            )
            .build()
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private data class PlayerSnapshot(
        val mediaItems: List<MediaItem>,
        val currentIndex: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val repeatMode: Int,
        val shuffleModeEnabled: Boolean,
        val playbackParameters: PlaybackParameters
    ) {
        fun restoreTo(player: ExoPlayer) {
            if (mediaItems.isEmpty()) return

            val safeIndex = currentIndex.coerceIn(0, mediaItems.lastIndex)
            player.setMediaItems(mediaItems, safeIndex, positionMs)
            player.repeatMode = repeatMode
            player.shuffleModeEnabled = shuffleModeEnabled
            player.playbackParameters = playbackParameters
            player.playWhenReady = playWhenReady
            player.prepare()
        }
    }

    private fun ExoPlayer.toSnapshot(): PlayerSnapshot {
        val items = (0 until mediaItemCount).map { getMediaItemAt(it) }
        return PlayerSnapshot(
            mediaItems = items,
            currentIndex = currentMediaItemIndex,
            positionMs = currentPosition,
            playWhenReady = playWhenReady,
            repeatMode = repeatMode,
            shuffleModeEnabled = shuffleModeEnabled,
            playbackParameters = playbackParameters
        )
    }

    private inner class StreamingStatsListener : TransferListener {
        override fun onTransferInitializing(
            source: DataSource,
            dataSpec: androidx.media3.datasource.DataSpec,
            isNetwork: Boolean
        ) = Unit

        override fun onTransferStart(
            source: DataSource,
            dataSpec: androidx.media3.datasource.DataSpec,
            isNetwork: Boolean
        ) = Unit

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: androidx.media3.datasource.DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int
        ) {
            if (isNetwork && bytesTransferred > 0) {
                serviceScope.launch(dispatcherProvider.io) {
                    val isWifi = connectivityProvider.wifiStatus.value
                    statisticsRepository.addStreamBytes(bytesTransferred.toLong(), isWifi)
                }
            }
        }

        override fun onTransferEnd(
            source: DataSource,
            dataSpec: androidx.media3.datasource.DataSpec,
            isNetwork: Boolean
        ) = Unit
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return if (this::mediaSession.isInitialized) mediaSession else null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (this::player.isInitialized && !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (this::serviceScope.isInitialized) {
            serviceScope.cancel()
        }
        if (this::mediaSession.isInitialized) {
            runCatching { mediaSession.release() }
        }
        if (this::player.isInitialized) {
            runCatching { player.release() }
        }
        runCatching { cache?.release() }
        cache = null
        super.onDestroy()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availablePlayerCommands =
                mediaControllerCommands(
                    isTrusted = controller.isTrusted,
                    availableCommands = player.availableCommands
                )
            if (availablePlayerCommands == null) {
                Timber.w("Rejected untrusted media controller")
                return MediaSession.ConnectionResult.reject()
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(availablePlayerCommands)
                .build()
        }
    }
}

internal fun mediaControllerCommands(
    isTrusted: Boolean,
    availableCommands: Player.Commands
): Player.Commands? {
    if (!isTrusted) return null
    return availableCommands
}
