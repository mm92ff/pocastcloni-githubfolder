package com.example.pocastcloni.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingStatisticsRecorderTest {
    @Test
    fun `many callbacks aggregate one exact threshold batch`() = runTest {
        val writer = RecordingWriter()
        val recorder = recorder(writer)

        repeat(CALLBACK_COUNT) {
            recorder.recordBytes(CALLBACK_BYTES, isWifi = it % 2 == 0)
        }
        runCurrent()

        assertTrue(writer.writes.isEmpty())

        recorder.recordBytes(
            bytes = StreamingStatisticsRecorder.FLUSH_THRESHOLD_BYTES -
                CALLBACK_COUNT * CALLBACK_BYTES,
            isWifi = true
        )
        runCurrent()

        assertEquals(
            listOf(StreamWrite(wifiBytes = 548_576L, mobileBytes = 500_000L)),
            writer.writes
        )
    }

    @Test
    fun `pending bytes flush after thirty seconds`() = runTest {
        val writer = RecordingWriter()
        val recorder = recorder(writer)
        recorder.recordBytes(120L, isWifi = true)
        recorder.recordBytes(45L, isWifi = false)
        runCurrent()

        advanceTimeBy(StreamingStatisticsRecorder.FLUSH_INTERVAL_MS - 1L)
        runCurrent()
        assertTrue(writer.writes.isEmpty())

        advanceTimeBy(1L)
        runCurrent()

        assertEquals(listOf(StreamWrite(120L, 45L)), writer.writes)
    }

    @Test
    fun `explicit flush writes a below-threshold batch immediately`() = runTest {
        val writer = RecordingWriter()
        val recorder = recorder(writer)
        recorder.recordBytes(7L, isWifi = true)
        recorder.recordBytes(11L, isWifi = false)

        recorder.requestFlush()
        runCurrent()

        assertEquals(listOf(StreamWrite(7L, 11L)), writer.writes)
    }

    @Test
    fun `failed flush requeues both network totals`() = runTest {
        val writer = RecordingWriter(IllegalStateException("disk unavailable"))
        val recorder = recorder(writer)
        recorder.recordBytes(13L, isWifi = true)
        recorder.recordBytes(17L, isWifi = false)

        recorder.requestFlush()
        runCurrent()
        recorder.requestFlush()
        runCurrent()

        val expected = StreamWrite(13L, 17L)
        assertEquals(listOf(expected, expected), writer.attempts)
        assertEquals(listOf(expected), writer.writes)
    }

    @Test
    fun `cancelled flush requeues bytes while recorder scope remains active`() = runTest {
        val writer = RecordingWriter(CancellationException("cancelled write"))
        val recorder = recorder(writer)
        recorder.recordBytes(19L, isWifi = true)
        recorder.recordBytes(23L, isWifi = false)

        recorder.requestFlush()
        runCurrent()
        recorder.requestFlush()
        runCurrent()

        val expected = StreamWrite(19L, 23L)
        assertEquals(listOf(expected, expected), writer.attempts)
        assertEquals(listOf(expected), writer.writes)
    }

    @Test
    fun `flush writes stay serialized while new callbacks arrive`() = runTest {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<StreamWrite>()
        var activeWrites = 0
        var maxActiveWrites = 0
        val writer =
            object : StreamStatisticsWriter {
                override suspend fun addStreamBytes(
                    wifiBytes: Long,
                    mobileBytes: Long
                ) {
                    activeWrites += 1
                    maxActiveWrites = maxOf(maxActiveWrites, activeWrites)
                    if (writes.isEmpty()) {
                        firstWriteStarted.complete(Unit)
                        releaseFirstWrite.await()
                    }
                    writes += StreamWrite(wifiBytes, mobileBytes)
                    activeWrites -= 1
                }
            }
        val recorder = recorder(writer)

        recorder.recordBytes(
            StreamingStatisticsRecorder.FLUSH_THRESHOLD_BYTES,
            isWifi = true
        )
        runCurrent()
        assertTrue(firstWriteStarted.isCompleted)

        recorder.recordBytes(
            StreamingStatisticsRecorder.FLUSH_THRESHOLD_BYTES,
            isWifi = false
        )
        runCurrent()
        assertEquals(1, activeWrites)

        releaseFirstWrite.complete(Unit)
        runCurrent()

        assertEquals(1, maxActiveWrites)
        assertEquals(
            listOf(
                StreamWrite(StreamingStatisticsRecorder.FLUSH_THRESHOLD_BYTES, 0L),
                StreamWrite(0L, StreamingStatisticsRecorder.FLUSH_THRESHOLD_BYTES)
            ),
            writes
        )
    }

    @Test
    fun `reset discards old bytes but preserves callbacks received during reset`() = runTest {
        val writer = RecordingWriter()
        val recorder = recorder(writer)
        val resetStarted = CompletableDeferred<Unit>()
        val finishReset = CompletableDeferred<Unit>()
        recorder.recordBytes(29L, isWifi = true)

        val reset =
            async {
                recorder.resetStatistics {
                    resetStarted.complete(Unit)
                    finishReset.await()
                }
            }
        runCurrent()
        assertTrue(resetStarted.isCompleted)

        recorder.recordBytes(31L, isWifi = false)
        recorder.requestFlush()
        runCurrent()
        assertTrue(writer.writes.isEmpty())

        finishReset.complete(Unit)
        reset.await()
        runCurrent()

        assertEquals(listOf(StreamWrite(0L, 31L)), writer.writes)
    }

    @Test
    fun `failed reset restores bytes for a later flush`() = runTest {
        val writer = RecordingWriter()
        val recorder = recorder(writer)
        recorder.recordBytes(37L, isWifi = true)
        recorder.recordBytes(41L, isWifi = false)

        val reset =
            async {
                runCatching {
                    recorder.resetStatistics {
                        error("reset failed")
                    }
                }
            }
        runCurrent()
        assertFalse(reset.await().isSuccess)

        recorder.requestFlush()
        runCurrent()

        assertEquals(listOf(StreamWrite(37L, 41L)), writer.writes)
    }

    private fun TestScope.recorder(writer: StreamStatisticsWriter): StreamingStatisticsRecorder =
        StreamingStatisticsRecorder(
            writer = writer,
            applicationScope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

    private class RecordingWriter(vararg failures: Exception) : StreamStatisticsWriter {
        private val failures = ArrayDeque(failures.toList())
        val attempts = mutableListOf<StreamWrite>()
        val writes = mutableListOf<StreamWrite>()

        override suspend fun addStreamBytes(
            wifiBytes: Long,
            mobileBytes: Long
        ) {
            val write = StreamWrite(wifiBytes, mobileBytes)
            attempts += write
            failures.pollFirst()?.let { throw it }
            writes += write
        }
    }

    private data class StreamWrite(
        val wifiBytes: Long,
        val mobileBytes: Long
    )

    private companion object {
        const val CALLBACK_COUNT = 10_000
        const val CALLBACK_BYTES = 100L
    }
}
