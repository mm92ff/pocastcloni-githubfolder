package com.example.pocastcloni.data.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFileNameTest {
    @Test
    fun `unicode filename truncation is byte safe and preserves extension`() {
        val fileName =
            createDownloadFileName(
                podcastTitle = "Podcast ".repeat(20),
                episodeTitle = "Folge 🎙️ äöü".repeat(40),
                mimeType = "audio/mpeg",
                sourceUrl = "https://example.com/audio",
                episodeId = 99L
            )

        assertTrue(fileName.toByteArray(Charsets.UTF_8).size <= MAX_DOWNLOAD_FILE_NAME_BYTES)
        assertTrue(fileName.endsWith(".mp3"))
        assertEquals(fileName, fileName.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }

    @Test
    fun `stable hash includes episode identity and prevents title collisions`() {
        val first = createDownloadFileName("Same", "Title", "audio/mpeg", "https://example.com/a", 1L)
        val repeated = createDownloadFileName("Same", "Title", "audio/mpeg", "https://example.com/a", 1L)
        val second = createDownloadFileName("Same", "Title", "audio/mpeg", "https://example.com/a", 2L)

        assertEquals(first, repeated)
        assertNotEquals(first, second)
    }

    @Test
    fun `unsafe path characters cannot escape target directory`() {
        val fileName =
            createDownloadFileName("../Podcast", "folder\\episode", "audio/ogg", "https://example.com/a", 5L)

        assertTrue(fileName.endsWith(".ogg"))
        assertTrue('/' !in fileName)
        assertTrue('\\' !in fileName)
        assertTrue(".." !in fileName)
    }
}
