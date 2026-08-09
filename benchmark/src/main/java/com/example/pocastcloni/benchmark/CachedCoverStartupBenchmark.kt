package com.example.pocastcloni.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class CachedCoverStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @After
    fun stopTargetProcess() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("am force-stop $TARGET_PACKAGE")
    }

    @Test
    fun cachedCoverColdStartupDoesNotRequestCoverAgain() {
        BenchmarkFeedServer().use { server ->
            server.start()
            var fixturePrepared = false
            var cachedCoverRequestCount = 0

            benchmarkRule.measureRepeated(
                packageName = TARGET_PACKAGE,
                metrics = listOf(StartupTimingMetric()),
                compilationMode = CompilationMode.None(),
                startupMode = StartupMode.COLD,
                iterations = ITERATIONS,
                setupBlock = {
                    val journey = BenchmarkJourneys(this)
                    if (!fixturePrepared) {
                        journey.resetAndStart()
                        journey.importFixture(server.feedUrl.toString())
                        cachedCoverRequestCount = waitForStableCoverRequestCount(server)
                        fixturePrepared = true
                    }
                    pressHome()
                }
            ) {
                startActivityAndWait()
                BenchmarkJourneys(this).waitForText(FIXTURE_PODCAST_TITLE)
                SystemClock.sleep(COVER_MISS_OBSERVATION_MS)
                assertEquals(
                    "A cached cold start issued another cover request",
                    cachedCoverRequestCount,
                    server.coverRequestCount
                )
            }
        }
    }

    private fun waitForStableCoverRequestCount(server: BenchmarkFeedServer): Int {
        val deadline = SystemClock.elapsedRealtime() + COVER_PREPARE_TIMEOUT_MS
        var lastCount = server.coverRequestCount
        var stableSince = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(COVER_COUNT_POLL_MS)
            val currentCount = server.coverRequestCount
            if (currentCount != lastCount) {
                lastCount = currentCount
                stableSince = SystemClock.elapsedRealtime()
            }
            if (currentCount > 0 && SystemClock.elapsedRealtime() - stableSince >= COVER_COUNT_STABLE_MS) {
                return currentCount
            }
        }
        error("Cover request did not settle; totalRequests=${server.requestCount}")
    }

    private companion object {
        const val ITERATIONS = 3
        const val COVER_MISS_OBSERVATION_MS = 1_500L
        const val COVER_PREPARE_TIMEOUT_MS = 10_000L
        const val COVER_COUNT_STABLE_MS = 750L
        const val COVER_COUNT_POLL_MS = 50L
    }
}
