package com.example.pocastcloni.data.repository

import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingStatisticsRecorder
@Inject
constructor(
    private val writer: StreamStatisticsWriter,
    @ApplicationScope applicationScope: CoroutineScope,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) {
    private val pendingBytes = AtomicReference(StreamByteBatch())
    private val flushRequests = Channel<Unit>(capacity = Channel.CONFLATED)
    private val flushMutex = Mutex()
    private val timerLock = Any()
    private val applicationScope = applicationScope
    private val ioDispatcher = ioDispatcher
    private var timedFlushJob: Job? = null

    init {
        applicationScope.launch(ioDispatcher) {
            for (ignored in flushRequests) {
                flushPendingBytes()
                cancelTimedFlush()
                if (!pendingBytes.get().isEmpty) scheduleTimedFlush()
            }
        }
    }

    fun recordBytes(
        bytes: Long,
        isWifi: Boolean
    ) {
        if (bytes <= 0L) return

        val updated = addPending(StreamByteBatch.from(bytes, isWifi))
        if (updated.totalBytes >= FLUSH_THRESHOLD_BYTES) {
            requestFlush()
        } else {
            scheduleTimedFlush()
        }
    }

    fun requestFlush() {
        flushRequests.trySend(Unit)
    }

    suspend fun resetStatistics(resetAction: suspend () -> Unit) {
        cancelTimedFlush()
        flushMutex.withLock {
            val bytesBeforeReset = pendingBytes.getAndSet(StreamByteBatch())
            try {
                resetAction()
            } catch (error: Exception) {
                addPending(bytesBeforeReset)
                throw error
            }
        }
        if (!pendingBytes.get().isEmpty) scheduleTimedFlush()
    }

    private suspend fun flushPendingBytes() {
        flushMutex.withLock {
            val batch = pendingBytes.getAndSet(StreamByteBatch())
            if (batch.isEmpty) return

            try {
                writer.addStreamBytes(batch.wifiBytes, batch.mobileBytes)
            } catch (error: Exception) {
                addPending(batch)
                if (error is CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
                Timber.w(error, "Streaming statistics flush failed; bytes retained for retry")
            }
        }
    }

    private fun addPending(addition: StreamByteBatch): StreamByteBatch {
        while (true) {
            val current = pendingBytes.get()
            val updated = current + addition
            if (pendingBytes.compareAndSet(current, updated)) {
                return updated
            }
        }
    }

    private fun scheduleTimedFlush() {
        synchronized(timerLock) {
            if (timedFlushJob?.isActive == true) return
            timedFlushJob =
                applicationScope.launch(ioDispatcher) {
                    delay(FLUSH_INTERVAL_MS)
                    synchronized(timerLock) { timedFlushJob = null }
                    requestFlush()
                }
        }
    }

    private fun cancelTimedFlush() {
        val job = synchronized(timerLock) {
            timedFlushJob.also { timedFlushJob = null }
        }
        job?.cancel()
    }

    internal companion object {
        const val FLUSH_THRESHOLD_BYTES = 1024L * 1024L
        const val FLUSH_INTERVAL_MS = 30_000L
    }
}

private data class StreamByteBatch(
    val wifiBytes: Long = 0L,
    val mobileBytes: Long = 0L
) {
    val totalBytes: Long
        get() = wifiBytes + mobileBytes

    val isEmpty: Boolean
        get() = wifiBytes == 0L && mobileBytes == 0L

    operator fun plus(other: StreamByteBatch): StreamByteBatch =
        StreamByteBatch(
            wifiBytes = wifiBytes + other.wifiBytes,
            mobileBytes = mobileBytes + other.mobileBytes
        )

    companion object {
        fun from(
            bytes: Long,
            isWifi: Boolean
        ): StreamByteBatch =
            if (isWifi) {
                StreamByteBatch(wifiBytes = bytes)
            } else {
                StreamByteBatch(mobileBytes = bytes)
            }
    }
}
