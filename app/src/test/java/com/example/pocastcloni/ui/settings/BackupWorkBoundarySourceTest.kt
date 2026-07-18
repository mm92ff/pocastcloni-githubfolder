package com.example.pocastcloni.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupWorkBoundarySourceTest {
    @Test
    fun `dismissing backup result never prunes unrelated work`() {
        val source =
            File("src/main/java/com/example/pocastcloni/ui/settings/SettingsBackupViewModel.kt")
                .readText()

        assertFalse(source.contains("pruneWork"))
        assertFalse(source.contains("dismissedJobIds"))
        assertTrue(source.contains("trackedJobId"))
        assertTrue(source.contains("SavedStateHandle"))
        assertFalse(source.contains("androidx.work"))
        assertFalse(source.contains("BackupWorker"))
        assertFalse(source.contains("WorkManager"))
    }

    @Test
    fun `backup worker propagates cancellation before generic failure handling`() {
        val source =
            File("src/main/java/com/example/pocastcloni/data/worker/BackupWorker.kt")
                .readText()
        val cancellationCatch = source.indexOf("catch (error: CancellationException)")
        val cancellationRethrow = source.indexOf("throw error", cancellationCatch)
        val genericCatch = source.indexOf("catch (e: Exception)")

        assertTrue(cancellationCatch >= 0)
        assertTrue(cancellationRethrow > cancellationCatch)
        assertTrue(cancellationRethrow < genericCatch)
        assertTrue(genericCatch > cancellationCatch)
        assertTrue(source.contains("KEY_IMPORT_SKIPPED_FAVORITES"))
    }

    @Test
    fun `user facing failure paths rethrow cancellation and log the original throwable`() {
        val expectations =
            listOf(
                FailureBoundaryExpectation(
                    path = "src/main/java/com/example/pocastcloni/data/worker/BackupWorker.kt",
                    cancellationCatch = "catch (error: CancellationException)",
                    cancellationRethrow = "throw error",
                    followingCatch = "catch (e: Exception)",
                    throwableLog = "Timber.e(e, \"Backup operation failed\")"
                ),
                FailureBoundaryExpectation(
                    path = "src/main/java/com/example/pocastcloni/ui/home/add/AddPodcastViewModel.kt",
                    cancellationCatch = "catch (e: CancellationException)",
                    cancellationRethrow = "throw e",
                    followingCatch = "catch (_: IOException)",
                    throwableLog = "Timber.e(e, \"Failed to toggle podcast subscription for %s\", url)"
                ),
                FailureBoundaryExpectation(
                    path = "src/main/java/com/example/pocastcloni/ui/settings/SettingsUrlImportViewModel.kt",
                    cancellationCatch = "catch (ce: CancellationException)",
                    cancellationRethrow = "throw ce",
                    followingCatch = "catch (t: Throwable)",
                    throwableLog = "Timber.e(t, \"Add podcast via URL failed\")"
                )
            )

        expectations.forEach { expectation ->
            val source = File(expectation.path).readText()
            val cancellationCatch = source.indexOf(expectation.cancellationCatch)
            val cancellationRethrow = source.indexOf(expectation.cancellationRethrow, cancellationCatch)
            val followingCatch = source.indexOf(expectation.followingCatch, cancellationCatch)

            assertTrue(expectation.path, cancellationCatch >= 0)
            assertTrue(expectation.path, cancellationRethrow > cancellationCatch)
            assertTrue(expectation.path, cancellationRethrow < followingCatch)
            assertTrue(expectation.path, source.contains(expectation.throwableLog))
        }
    }

    private data class FailureBoundaryExpectation(
        val path: String,
        val cancellationCatch: String,
        val cancellationRethrow: String,
        val followingCatch: String,
        val throwableLog: String
    )
}
