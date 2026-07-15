package com.example.pocastcloni.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.pocastcloni.R
import com.example.pocastcloni.data.cache.MediaCacheProvider
import com.example.pocastcloni.data.repository.StreamingStatisticsRecorder
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.BufferMode
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
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named

/**
 * Owns the service-scoped player, media session, streaming cache, and transfer accounting.
 *
 * Buffer-mode changes replace the ExoPlayer while preserving queue and playback state in the
 * existing session. Controller admission is limited to trusted controllers or this app's own
 * package/UID pair, and all owned resources are released when the service is destroyed.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PodcastPlaybackService : MediaSessionService() {
    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject lateinit var streamingStatisticsRecorder: StreamingStatisticsRecorder

    @Inject lateinit var connectivityProvider: ConnectivityProvider

    @Inject
    @Named("ApprovedMediaClient")
    lateinit var okHttpClient: OkHttpClient

    @Inject lateinit var mediaCacheProvider: MediaCacheProvider

    private lateinit var serviceScope: CoroutineScope

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

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

        initializeStaticComponents()
        currentBufferMode = BufferMode.NORMAL
        recreatePlayer(BufferMode.NORMAL)

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
        val statsListener = StreamingStatsListener()
        val httpDataSourceFactory =
            OkHttpDataSource.Factory(okHttpClient)
                .setTransferListener(statsListener)

        val upstreamFactory: DataSource.Factory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val cacheFactory = playbackDataSourceFactory(upstreamFactory, mediaCacheProvider.getCacheOrNull())

        mediaSourceFactory =
            ProgressiveMediaSource.Factory(cacheFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy())

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
     * Rebuilds the player for a new buffer policy and restores its queue, position, playback
     * intent, repeat/shuffle state, and speed. The existing MediaSession is retained when Media3
     * accepts the player swap; otherwise the session is rebuilt around the replacement player.
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
                streamingStatisticsRecorder.recordBytes(
                    bytes = bytesTransferred.toLong(),
                    isWifi = connectivityProvider.wifiStatus.value
                )
            }
        }

        override fun onTransferEnd(
            source: DataSource,
            dataSpec: androidx.media3.datasource.DataSpec,
            isNetwork: Boolean
        ) {
            if (isNetwork) {
                streamingStatisticsRecorder.requestFlush()
            }
        }
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
        if (this::mediaSession.isInitialized) {
            runCatching { mediaSession.release() }
        }
        if (this::player.isInitialized) {
            runCatching { player.release() }
        }
        try {
            mediaCacheProvider.close()
        } catch (e: Exception) {
            Timber.w(e, "Failed to close media cache during service teardown")
        }
        streamingStatisticsRecorder.requestFlush()
        if (this::serviceScope.isInitialized) {
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availablePlayerCommands =
                mediaControllerCommands(
                    isTrusted = isMediaControllerAllowed(
                        isTrusted = controller.isTrusted,
                        controllerPackageName = controller.packageName,
                        controllerUid = controller.uid,
                        appPackageName = packageName,
                        appUid = Process.myUid()
                    ),
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

internal fun isMediaControllerAllowed(
    isTrusted: Boolean,
    controllerPackageName: String,
    controllerUid: Int,
    appPackageName: String,
    appUid: Int
): Boolean = isTrusted || (controllerPackageName == appPackageName && controllerUid == appUid)

internal fun mediaControllerCommands(
    isTrusted: Boolean,
    availableCommands: Player.Commands
): Player.Commands? {
    if (!isTrusted) return null
    return availableCommands
}

@OptIn(UnstableApi::class)
internal fun playbackDataSourceFactory(
    upstreamFactory: DataSource.Factory,
    cache: Cache?
): DataSource.Factory =
    cache?.let {
        CacheDataSource.Factory()
            .setCache(it)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    } ?: upstreamFactory
