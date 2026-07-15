package com.example.pocastcloni.data.worker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class DownloadPublicationRecoveryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `private recovery preserves readable reference and prunes invalid and orphan attempts`() = runTest {
        val privateDownloads = temporaryFolder.newFolder("private")
        val legacyDownloads = temporaryFolder.newFolder("legacy")
        val readable = privateAttempt(privateDownloads, FIRST_ATTEMPT, "kept.mp3", "audio")
        val invalid = privateAttempt(privateDownloads, SECOND_ATTEMPT, "invalid.mp3", "")
        val orphan = privateAttempt(privateDownloads, THIRD_ATTEMPT, "orphan.mp3", "orphan")
        val database =
            mutableMapOf(
                1L to DownloadPublicationRecord(1L, readable.absolutePath),
                2L to DownloadPublicationRecord(2L, invalid.absolutePath)
            )
        val recovery = DownloadPublicationRecovery(privateDownloads, legacyDownloads, null)

        val corrected = recovery.recover(database.values.toList()) { id, path -> database.resetExact(id, path) }
        val repeated = recovery.recover(database.values.toList()) { id, path -> database.resetExact(id, path) }

        assertEquals(1, corrected)
        assertEquals(0, repeated)
        assertTrue(readable.isFile)
        assertFalse(invalid.exists())
        assertFalse(orphan.exists())
        assertEquals(setOf(1L), database.keys)
    }

    @Test
    fun `private recovery keeps invalid attempt when exact database reset loses ownership`() = runTest {
        val privateDownloads = temporaryFolder.newFolder("private")
        val legacyDownloads = temporaryFolder.newFolder("legacy")
        val invalid = privateAttempt(privateDownloads, FIRST_ATTEMPT, "invalid.mp3", "")
        val record = DownloadPublicationRecord(1L, invalid.absolutePath)
        val recovery = DownloadPublicationRecovery(privateDownloads, legacyDownloads, null)

        val corrected = recovery.recover(listOf(record)) { _, _ -> false }

        assertEquals(0, corrected)
        assertTrue(invalid.exists())
    }

    @Test
    fun `legacy recovery finishes one matching attempt and is idempotent`() = runTest {
        val privateDownloads = temporaryFolder.newFolder("private")
        val legacyDownloads = temporaryFolder.newFolder("legacy")
        val pending = legacyAttempt(legacyDownloads, FIRST_ATTEMPT, "episode.mp3", "audio")
        val orphan = legacyAttempt(legacyDownloads, SECOND_ATTEMPT, "orphan.mp3", "orphan")
        val target = File(legacyDownloads, pending.name)
        val database = mutableMapOf(1L to DownloadPublicationRecord(1L, target.absolutePath))
        val recovery = DownloadPublicationRecovery(privateDownloads, legacyDownloads, null)

        val corrected = recovery.recover(database.values.toList()) { id, path -> database.resetExact(id, path) }
        val repeated = recovery.recover(database.values.toList()) { id, path -> database.resetExact(id, path) }

        assertEquals(0, corrected)
        assertEquals(0, repeated)
        assertEquals("audio", target.readText())
        assertFalse(pending.exists())
        assertFalse(orphan.exists())
        assertEquals(setOf(1L), database.keys)
    }

    @Test
    fun `legacy recovery resets exact row instead of choosing ambiguous attempt`() = runTest {
        val privateDownloads = temporaryFolder.newFolder("private")
        val legacyDownloads = temporaryFolder.newFolder("legacy")
        val first = legacyAttempt(legacyDownloads, FIRST_ATTEMPT, "episode.mp3", "first")
        val second = legacyAttempt(legacyDownloads, SECOND_ATTEMPT, "episode.mp3", "second")
        val target = File(legacyDownloads, "episode.mp3")
        val database = mutableMapOf(1L to DownloadPublicationRecord(1L, target.absolutePath))
        val recovery = DownloadPublicationRecovery(privateDownloads, legacyDownloads, null)

        val corrected = recovery.recover(database.values.toList()) { id, path -> database.resetExact(id, path) }

        assertEquals(1, corrected)
        assertTrue(database.isEmpty())
        assertFalse(target.exists())
        assertFalse(first.exists())
        assertFalse(second.exists())
    }

    @Test
    fun `media store recovery publishes valid reference and compensates failed publication`() = runTest {
        val privateDownloads = temporaryFolder.newFolder("private")
        val legacyDownloads = temporaryFolder.newFolder("legacy")
        val mediaStore =
            FakePendingMediaStore(
                VALID_URI to MediaRow(sizeBytes = 5L),
                FAILED_URI to MediaRow(sizeBytes = 5L, publishSucceeds = false),
                INVALID_URI to MediaRow(sizeBytes = 0L),
                ORPHAN_URI to MediaRow(sizeBytes = 5L)
            )
        val database =
            mutableMapOf(
                1L to DownloadPublicationRecord(1L, VALID_URI),
                2L to DownloadPublicationRecord(2L, FAILED_URI),
                3L to DownloadPublicationRecord(3L, INVALID_URI)
            )
        val recovery = DownloadPublicationRecovery(privateDownloads, legacyDownloads, mediaStore)

        val corrected = recovery.recover(database.values.toList()) { id, path -> database.resetExact(id, path) }
        val repeated = recovery.recover(database.values.toList()) { id, path -> database.resetExact(id, path) }

        assertEquals(2, corrected)
        assertEquals(0, repeated)
        assertEquals(setOf(1L), database.keys)
        assertFalse(requireNotNull(mediaStore.rows[VALID_URI]).pending)
        assertFalse(FAILED_URI in mediaStore.rows)
        assertFalse(INVALID_URI in mediaStore.rows)
        assertFalse(ORPHAN_URI in mediaStore.rows)
    }

    @Test
    fun `publication gate serializes publication and recovery transitions`() = runTest {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var secondEntered = false
        val first =
            launch {
                DownloadPublicationGate.withLock {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                }
            }
        firstEntered.await()
        val second = launch { DownloadPublicationGate.withLock { secondEntered = true } }

        testScheduler.runCurrent()
        assertFalse(secondEntered)
        releaseFirst.complete(Unit)
        first.join()
        second.join()

        assertTrue(secondEntered)
    }

    private fun privateAttempt(
        downloadsDirectory: File,
        attemptId: UUID,
        fileName: String,
        content: String
    ): File =
        File(downloadsDirectory, "$PRIVATE_PUBLICATION_ATTEMPT_DIRECTORY/$attemptId/$fileName").apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    private fun legacyAttempt(
        downloadsDirectory: File,
        attemptId: UUID,
        fileName: String,
        content: String
    ): File =
        File(downloadsDirectory, "$LEGACY_PUBLICATION_ATTEMPT_DIRECTORY/$attemptId/$fileName").apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    private fun MutableMap<Long, DownloadPublicationRecord>.resetExact(
        episodeId: Long,
        expectedPath: String
    ): Boolean =
        if (get(episodeId)?.path == expectedPath) {
            remove(episodeId)
            true
        } else {
            false
        }

    private class FakePendingMediaStore(vararg initialRows: Pair<String, MediaRow>) : PendingMediaStoreRecovery {
        val rows = initialRows.toMap(mutableMapOf())

        override fun pendingPublications(): List<PendingMediaStorePublication> =
            rows
                .filterValues(MediaRow::pending)
                .map { (path, row) -> PendingMediaStorePublication(path, row.sizeBytes) }

        override fun isReadable(path: String): Boolean = rows[path]?.readable == true

        override fun publish(path: String): Boolean =
            rows[path]
                ?.takeIf(MediaRow::publishSucceeds)
                ?.also { it.pending = false } != null

        override fun delete(path: String): Boolean = rows.remove(path) != null
    }

    private data class MediaRow(
        val sizeBytes: Long,
        val readable: Boolean = true,
        val publishSucceeds: Boolean = true,
        var pending: Boolean = true
    )

    private companion object {
        val FIRST_ATTEMPT: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val SECOND_ATTEMPT: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val THIRD_ATTEMPT: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
        const val VALID_URI = "content://media/external/downloads/1"
        const val FAILED_URI = "content://media/external/downloads/2"
        const val INVALID_URI = "content://media/external/downloads/3"
        const val ORPHAN_URI = "content://media/external/downloads/4"
    }
}
