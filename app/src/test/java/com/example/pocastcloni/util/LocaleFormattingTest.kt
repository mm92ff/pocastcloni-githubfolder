package com.example.pocastcloni.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocaleFormattingTest {
    private val units = arrayOf("B", "KB", "MB")

    @Test
    fun `byte formatting uses the supplied locale`() {
        val bytes = 1_536L

        assertEquals("1.5 KB", formatBytes(bytes, units, Locale.US))
        assertEquals("1,5 KB", formatBytes(bytes, units, Locale.GERMANY))
    }

    @Test
    fun `byte formatting localizes zero and clamps very large values`() {
        assertEquals("0 B", formatBytes(0L, units, Locale.GERMANY))
        assertEquals("1048576,0 MB", formatBytes(1L shl 40, units, Locale.GERMANY))
    }

    @Test
    fun `clock formatting uses the supplied locale`() {
        val arabic = Locale.forLanguageTag("ar-EG")

        assertEquals("\u0660\u0661:\u0660\u0662", formatTime(62_000L, arabic))
    }
}
