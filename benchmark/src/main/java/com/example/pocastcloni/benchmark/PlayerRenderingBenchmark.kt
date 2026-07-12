package com.example.pocastcloni.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class PlayerRenderingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @After
    fun stopPlaybackProcess() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("am force-stop $TARGET_PACKAGE")
    }

    @Test
    fun miniPlayerIdlePlayback() =
        withFeedServer { server ->
            benchmarkRule.measureRepeated(
                packageName = TARGET_PACKAGE,
                metrics = listOf(FrameTimingMetric()),
                compilationMode = CompilationMode.None(),
                iterations = PLAYER_ITERATIONS,
                setupBlock = {
                    BenchmarkJourneys(this).apply {
                        resetAndStart()
                        importFixtureWithDiagnostics(server)
                        openFixturePodcast()
                        startFixtureEpisode()
                        openMainScreen("Home")
                    }
                }
            ) {
                Thread.sleep(IDLE_PLAYBACK_MS)
            }
        }

    @Test
    fun podcastDetailPlaybackAndScroll() =
        withFeedServer { server ->
            benchmarkRule.measureRepeated(
                packageName = TARGET_PACKAGE,
                metrics = listOf(FrameTimingMetric()),
                compilationMode = CompilationMode.None(),
                iterations = PLAYER_ITERATIONS,
                setupBlock = {
                    BenchmarkJourneys(this).apply {
                        resetAndStart()
                        importFixtureWithDiagnostics(server)
                        openFixturePodcast()
                        startFixtureEpisode()
                    }
                }
            ) {
                Thread.sleep(IDLE_BEFORE_SCROLL_MS)
                BenchmarkJourneys(this).scrollDown()
                Thread.sleep(IDLE_AFTER_SCROLL_MS)
            }
        }

    @Test
    fun fullPlayerPlaybackAndPauseResume() =
        withFeedServer { server ->
            benchmarkRule.measureRepeated(
                packageName = TARGET_PACKAGE,
                metrics = listOf(FrameTimingMetric()),
                compilationMode = CompilationMode.None(),
                iterations = PLAYER_ITERATIONS,
                setupBlock = {
                    BenchmarkJourneys(this).apply {
                        resetAndStart()
                        importFixtureWithDiagnostics(server)
                        openFixturePodcast()
                        startFixtureEpisode()
                        openFullPlayer()
                    }
                }
            ) {
                val journey = BenchmarkJourneys(this)
                Thread.sleep(IDLE_BEFORE_ACTION_MS)
                journey.clickDescription("Play/Pause")
                Thread.sleep(PAUSE_MS)
                journey.clickDescription("Play/Pause")
                Thread.sleep(IDLE_AFTER_ACTION_MS)
            }
        }

    private inline fun withFeedServer(block: (BenchmarkFeedServer) -> Unit) {
        BenchmarkFeedServer().use { server ->
            server.start()
            block(server)
        }
    }

    private fun BenchmarkJourneys.importFixtureWithDiagnostics(server: BenchmarkFeedServer) {
        try {
            importFixture(server.feedUrl.toString())
        } catch (error: IllegalStateException) {
            throw IllegalStateException("${error.message}; localServerRequests=${server.requestCount}", error)
        }
    }

    private companion object {
        const val PLAYER_ITERATIONS = 5
        const val IDLE_PLAYBACK_MS = 5_000L
        const val IDLE_BEFORE_SCROLL_MS = 2_000L
        const val IDLE_AFTER_SCROLL_MS = 3_000L
        const val IDLE_BEFORE_ACTION_MS = 2_000L
        const val PAUSE_MS = 500L
        const val IDLE_AFTER_ACTION_MS = 2_500L
    }
}
