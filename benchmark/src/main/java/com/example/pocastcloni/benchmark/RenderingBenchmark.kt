package com.example.pocastcloni.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class RenderingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun mainNavigation() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = NAVIGATION_ITERATIONS,
            setupBlock = { BenchmarkJourneys(this).resetAndStart() }
        ) {
            val journey = BenchmarkJourneys(this)
            journey.openMainScreen("Settings")
            journey.openMainScreen("Home")
            journey.openMainScreen("Downloads")
            journey.openMainScreen("Search")
            journey.openMainScreen("Home")
        }

    @Test
    fun settingsTabsAndScroll() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = NAVIGATION_ITERATIONS,
            setupBlock = {
                BenchmarkJourneys(this).apply {
                    resetAndStart()
                    openMainScreen("Settings")
                }
            }
        ) {
            val journey = BenchmarkJourneys(this)
            journey.openSettingsTab("Playback")
            journey.openSettingsTab("Sync")
            journey.scrollDown()
            journey.openSettingsTab("Data")
            journey.openSettingsTab("Design")
            journey.scrollDown()
        }

    private companion object {
        const val NAVIGATION_ITERATIONS = 5
    }
}
