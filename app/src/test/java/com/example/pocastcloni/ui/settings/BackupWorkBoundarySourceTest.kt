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
        val genericCatch = source.indexOf("catch (e: Exception)")

        assertTrue(cancellationCatch >= 0)
        assertTrue(genericCatch > cancellationCatch)
        assertTrue(source.contains("KEY_IMPORT_SKIPPED_FAVORITES"))
    }
}
