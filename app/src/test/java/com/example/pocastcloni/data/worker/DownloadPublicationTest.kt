package com.example.pocastcloni.data.worker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class DownloadPublicationTest {
    @Test
    fun `database rejection cleans uncommitted target`() {
        val publication = FakePublication()

        assertThrows(StaleDownloadWorkerException::class.java) {
            runTest {
                commitDownloadPublication(publication, commitDatabase = { false }, compensateDatabase = {})
            }
        }

        assertTrue(publication.cleaned)
        assertFalse(publication.visible)
    }

    @Test
    fun `visibility failure compensates database and cleans target`() {
        val publication = FakePublication(visibilityFailure = IOException("visibility failed"))
        var compensated = false

        assertThrows(IOException::class.java) {
            runTest {
                commitDownloadPublication(
                    publication,
                    commitDatabase = { true },
                    compensateDatabase = { compensated = true }
                )
            }
        }

        assertTrue(compensated)
        assertTrue(publication.cleaned)
    }

    @Test
    fun `successful publication remains committed`() = runTest {
        val publication = FakePublication()

        commitDownloadPublication(publication, commitDatabase = { true }, compensateDatabase = {})

        assertTrue(publication.visible)
        assertFalse(publication.cleaned)
    }

    @Test
    fun `private publications use attempt-specific final paths`() = runTest {
        val targetDirectory = temporaryFolder.newFolder("private-downloads")
        val oldPublication =
            preparePrivateFilePublication(
                stagedFile = stagedFile("private-old.part", "old"),
                targetDirectory = targetDirectory,
                fileName = LOGICAL_FILE_NAME,
                attemptId = OLD_ATTEMPT_ID
            )
        val newPublication =
            preparePrivateFilePublication(
                stagedFile = stagedFile("private-new.part", "new"),
                targetDirectory = targetDirectory,
                fileName = LOGICAL_FILE_NAME,
                attemptId = NEW_ATTEMPT_ID
            )
        var databasePath: String? = null
        commitDownloadPublication(oldPublication, { databasePath = it; true }, {})
        commitDownloadPublication(newPublication, { databasePath = it; true }, {})

        oldPublication.cleanup()

        assertNotEquals(oldPublication.path, newPublication.path)
        assertEquals(newPublication.path, databasePath)
        assertEquals("new", File(newPublication.path).readText())
    }

    @Test
    fun `legacy cleanup cannot delete newer replacement with same display name`() = runTest {
        val targetDirectory = temporaryFolder.newFolder("legacy-downloads")
        val oldPublication =
            prepareLegacyPublicFilePublication(
                stagedFile = stagedFile("legacy-old.part", "old"),
                targetDirectory = targetDirectory,
                fileName = LOGICAL_FILE_NAME,
                attemptId = OLD_ATTEMPT_ID
            )
        var databasePath: String? = null
        commitDownloadPublication(oldPublication, { databasePath = it; true }, {})
        val newPublication =
            prepareLegacyPublicFilePublication(
                stagedFile = stagedFile("legacy-new.part", "new"),
                targetDirectory = targetDirectory,
                fileName = LOGICAL_FILE_NAME,
                attemptId = NEW_ATTEMPT_ID
            )
        commitDownloadPublication(newPublication, { databasePath = it; true }, {})

        oldPublication.cleanup()

        assertEquals(newPublication.path, databasePath)
        assertEquals(oldPublication.path, newPublication.path)
        assertEquals("new", File(newPublication.path).readText())
    }

    @Test
    fun `media store cleanup is scoped to the inserted row`() = runTest {
        val rows = mutableSetOf(OLD_MEDIA_URI, NEW_MEDIA_URI)
        val oldPublication =
            MediaStoreDownloadPublication(OLD_MEDIA_URI, 3L, {}, { rows.remove(OLD_MEDIA_URI) })
        val newPublication =
            MediaStoreDownloadPublication(NEW_MEDIA_URI, 3L, {}, { rows.remove(NEW_MEDIA_URI) })
        var databasePath: String? = null
        commitDownloadPublication(oldPublication, { databasePath = it; true }, {})
        commitDownloadPublication(newPublication, { databasePath = it; true }, {})

        oldPublication.cleanup()

        assertEquals(NEW_MEDIA_URI, databasePath)
        assertFalse(OLD_MEDIA_URI in rows)
        assertTrue(NEW_MEDIA_URI in rows)
    }

    @Test
    fun `media store pending copy cancellation deletes pending target and propagates`() = runBlocking {
        assertCancelledPublicCopyCleansTarget("media-store.pending")
    }

    @Test
    fun `legacy public copy cancellation deletes final target and propagates`() = runBlocking {
        assertCancelledPublicCopyCleansTarget("legacy-public.mp3")
    }

    private suspend fun assertCancelledPublicCopyCleansTarget(targetName: String) {
        val stagedFile = temporaryFolder.newFile("$targetName.part").apply {
            writeBytes(ByteArray(DEFAULT_BUFFER_SIZE * 2) { 1 })
        }
        val target = temporaryFolder.root.resolve(targetName)
        val firstChunkCopied = CompletableDeferred<Unit>()
        val job =
            kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                copyStagedDownload(
                    stagedFile = stagedFile,
                    openOutput = { target.outputStream() },
                    cleanupTarget = { target.delete() },
                    onChunkCopied = {
                        firstChunkCopied.complete(Unit)
                        awaitCancellation()
                    }
                )
            }
        firstChunkCopied.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertFalse(target.exists())
    }

    private fun stagedFile(
        name: String,
        contents: String
    ) = temporaryFolder.newFile(name).apply { writeText(contents) }

    private class FakePublication(
        private val visibilityFailure: IOException? = null
    ) : PendingDownloadPublication {
        override val path: String = "/download.mp3"
        override val totalBytes: Long = 10L
        var visible = false
        var cleaned = false

        override fun makeVisible() {
            visibilityFailure?.let { throw it }
            visible = true
        }

        override fun cleanup() {
            cleaned = true
        }
    }

    @get:org.junit.Rule
    val temporaryFolder = org.junit.rules.TemporaryFolder()

    private companion object {
        const val LOGICAL_FILE_NAME = "Podcast_Episode.mp3"
        const val OLD_MEDIA_URI = "content://downloads/7"
        const val NEW_MEDIA_URI = "content://downloads/8"
        val OLD_ATTEMPT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000007")
        val NEW_ATTEMPT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000008")
    }
}
