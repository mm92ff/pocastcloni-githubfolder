package com.example.pocastcloni.data.manager

import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.di.DispatcherProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class PodcastBackupStreamingExportTest {
    @Test
    fun `large export uses generator without materializing a JSON string`() {
        val objectMapper = NoStringMaterializationObjectMapper()
        val helper = PodcastBackupHelper(objectMapper, mockk<DispatcherProvider>())
        val output = ChunkTrackingOutputStream()
        val backupData =
            BackupData(
                podcasts = List(2_000) { index ->
                    BackupPodcast(
                        url = "https://example.com/feed-$index.xml",
                        title = "Podcast $index ${"x".repeat(256)}"
                    )
                }
            )

        helper.writeBackupToStream(backupData, output)

        assertFalse(objectMapper.stringMaterializationAttempted)
        assertTrue(output.maximumChunkSize <= BACKUP_EXPORT_BUFFER_BYTES)
        assertEquals(
            2_000,
            objectMapper.readTree(output.bytes()).path("podcasts").size()
        )
    }

    private class NoStringMaterializationObjectMapper : ObjectMapper() {
        var stringMaterializationAttempted = false

        init {
            registerKotlinModule()
        }

        override fun writeValueAsString(value: Any?): String {
            stringMaterializationAttempted = true
            error("Streaming export must not materialize the complete JSON document")
        }
    }

    private class ChunkTrackingOutputStream : OutputStream() {
        private val delegate = ByteArrayOutputStream()
        var maximumChunkSize = 0
            private set

        override fun write(value: Int) {
            maximumChunkSize = maxOf(maximumChunkSize, 1)
            delegate.write(value)
        }

        override fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ) {
            maximumChunkSize = maxOf(maximumChunkSize, length)
            delegate.write(buffer, offset, length)
        }

        fun bytes(): ByteArray = delegate.toByteArray()
    }
}
