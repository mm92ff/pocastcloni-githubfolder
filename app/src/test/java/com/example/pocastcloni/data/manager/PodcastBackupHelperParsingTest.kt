package com.example.pocastcloni.data.manager

import com.example.pocastcloni.data.local.BackupData
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PodcastBackupHelperParsingTest {
    private val objectMapper = ObjectMapper().apply {
        registerKotlinModule()
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Test
    fun parseBackupJson_acceptsCurrentBackupFormat() {
        val result = parseBackupJson(
            """
            {
              "version": 1,
              "podcasts": [
                {
                  "url": "https://example.com/feed.xml",
                  "sortOrder": 1
                }
              ]
            }
            """.trimIndent(),
            objectMapper
        )

        assertEquals(1, result.podcasts.size)
        assertEquals("https://example.com/feed.xml", result.podcasts.first().url)
    }

    @Test
    fun parseBackupJson_acceptsLegacyUrlList() {
        val result = parseBackupJson(
            """
            [
              "https://example.com/feed-a.xml",
              "https://example.com/feed-b.xml"
            ]
            """.trimIndent(),
            objectMapper
        )

        assertEquals(2, result.podcasts.size)
        assertEquals(0L, result.podcasts[0].sortOrder)
        assertEquals(1L, result.podcasts[1].sortOrder)
    }

    @Test
    fun parseBackupJson_rejectsBlankOrContentFreeBackups() {
        assertThrows(IllegalArgumentException::class.java) {
            parseBackupJson("", objectMapper)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateBackupData(BackupData())
        }
    }

    @Test
    fun parseBackupJson_rejectsInvalidFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            parseBackupJson("""{"podcasts":"not-a-list"}""", objectMapper)
        }
    }
}
