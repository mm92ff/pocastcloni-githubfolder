package com.example.pocastcloni.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern
import kotlin.math.abs

internal class BenchmarkJourneys(
    private val scope: MacrobenchmarkScope,
    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
) {
    fun resetAndStart() {
        device.executeShellCommand("pm clear $TARGET_PACKAGE")
        scope.startActivityAndWait()
        waitForText(HOME_TITLE)
    }

    fun openMainScreen(label: String) {
        findTextOrDescription(label).click()
        waitForText(if (label == "Home") HOME_TITLE else label)
    }

    fun openSettingsTab(label: String) {
        waitForText(label).click()
        device.waitForIdle()
    }

    fun importFixture(feedUrl: String) {
        openMainScreen("Settings")
        openSettingsTab("Sync")
        val input = waitFor(By.clazz("android.widget.EditText"), "RSS URL input")
        input.text = feedUrl
        enableImportPermission("Allow legacy HTTP media")
        enableImportPermission("Allow local network feed")
        waitForText("Add").click()
        waitForText("Podcast added successfully", timeoutMs = 20_000L)
        openMainScreen("Home")
        waitForText(FIXTURE_PODCAST_TITLE, timeoutMs = 20_000L)
    }

    fun openFixturePodcast() {
        waitForText(FIXTURE_PODCAST_TITLE)
        clickNodeOrAncestor(waitForDescription("Cover image"))
        waitFor(By.text(Pattern.compile("Benchmark Episode \\d+")), "visible fixture episode")
    }

    fun startFixtureEpisode() {
        val episode = waitFor(
            By.text(Pattern.compile("Benchmark Episode \\d+")),
            "visible fixture episode"
        )
        val playButton = device.findObjects(By.desc("Play/Pause"))
            .filter { candidate ->
                candidate.visibleBounds.width() > 0 && candidate.visibleBounds.height() > 0
            }
            .minByOrNull { candidate ->
                abs(candidate.visibleBounds.centerY() - episode.visibleBounds.centerY())
            }
            ?: fail("visible fixture play button")
        clickNodeOrAncestor(playButton)
        device.wait(Until.findObject(By.text("Allow")), 1_000L)?.click()
        waitForDescription(OPEN_FULL_PLAYER, timeoutMs = 20_000L)
    }

    fun openFullPlayer() {
        waitForDescription(OPEN_FULL_PLAYER).click()
        waitForDescription("Play/Pause", timeoutMs = 20_000L)
    }

    fun scrollDown() {
        val centerX = device.displayWidth / 2
        device.swipe(
            centerX,
            device.displayHeight * SWIPE_START_PERCENT / PERCENT_BASE,
            centerX,
            device.displayHeight * SWIPE_END_PERCENT / PERCENT_BASE,
            SWIPE_STEPS
        )
        device.waitForIdle()
    }

    fun clickDescription(description: String) {
        waitForDescription(description).click()
        device.waitForIdle()
    }

    fun waitForText(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): UiObject2 =
        waitFor(By.text(text), "text '$text'", timeoutMs)

    private fun waitForDescription(description: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): UiObject2 =
        waitFor(By.desc(description), "content description '$description'", timeoutMs)

    private fun findTextOrDescription(value: String): UiObject2 =
        device.findObject(By.text(value)) ?: device.findObject(By.desc(value))
            ?: fail("text or content description '$value'")

    private fun clickNodeOrAncestor(node: UiObject2) {
        var candidate: UiObject2? = node
        while (candidate != null && !candidate.isClickable) {
            candidate = candidate.parent
        }
        (candidate ?: node).click()
    }

    private fun enableImportPermission(label: String) {
        val labelNode = waitForText(label)
        val checkbox = device.findObjects(By.clazz("android.widget.CheckBox"))
            .minByOrNull { candidate ->
                abs(candidate.visibleBounds.centerY() - labelNode.visibleBounds.centerY())
            }
            ?: fail("checkbox for '$label'")
        if (!checkbox.isChecked) {
            checkbox.click()
            device.waitForIdle()
        }
    }

    private fun waitFor(selector: BySelector, label: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): UiObject2 =
        device.findObject(selector)
            ?: device.wait(Until.findObject(selector), timeoutMs)
            ?: device.findObject(selector)
            ?: fail(label)

    private fun fail(label: String): Nothing {
        val context = InstrumentationRegistry.getInstrumentation().context
        val output = File(context.getExternalFilesDir(null), "benchmark-failure.png")
        device.takeScreenshot(output)
        val visibleText =
            device.findObjects(By.text(Pattern.compile(".+")))
                .mapNotNull { node -> node.text }
                .distinct()
                .joinToString(" | ")
        error("Timed out waiting for $label. Visible text: $visibleText. Screenshot: ${output.absolutePath}")
    }

    private companion object {
        const val SWIPE_START_PERCENT = 75
        const val SWIPE_END_PERCENT = 25
        const val PERCENT_BASE = 100
        const val SWIPE_STEPS = 20
    }
}
