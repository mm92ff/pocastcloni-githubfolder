package com.example.pocastcloni.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern

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
        waitForText("Add").click()
        waitForText("Podcast added successfully", timeoutMs = 20_000L)
        openMainScreen("Home")
        waitForText(FIXTURE_PODCAST_TITLE, timeoutMs = 20_000L)
    }

    fun openFixturePodcast() {
        waitForText(FIXTURE_PODCAST_TITLE)
        clickNodeOrAncestor(waitForDescription("Cover image"))
        waitForText(FIXTURE_EPISODE_TITLE)
    }

    fun startFixtureEpisode() {
        waitForDescription("Play/Pause").click()
        device.wait(Until.findObject(By.text("Allow")), 1_000L)?.click()
        waitForDescription(OPEN_FULL_PLAYER, timeoutMs = 20_000L)
    }

    fun openFullPlayer() {
        waitForDescription(OPEN_FULL_PLAYER).click()
        waitForDescription("Play/Pause", timeoutMs = 20_000L)
    }

    fun scrollDown() {
        val scrollable = waitFor(By.scrollable(true), "scrollable content")
        scrollable.scroll(Direction.DOWN, SCROLL_PERCENT)
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
        const val SCROLL_PERCENT = 0.75f
    }
}
