package com.example.pocastcloni.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class SizeLimitedInputStreamTest {
    @Test
    fun `allows input exactly at the byte limit`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val result = SizeLimitedInputStream(ByteArrayInputStream(bytes), bytes.size.toLong()).readBytes()

        assertArrayEquals(bytes, result)
    }

    @Test
    fun `rejects input when content exceeds the byte limit`() {
        val stream = SizeLimitedInputStream(
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
            limitBytes = 4
        )

        assertThrows(SizeLimitExceededException::class.java) { stream.readBytes() }
    }
}
