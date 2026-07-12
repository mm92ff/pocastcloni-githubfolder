package com.example.pocastcloni.util

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class SizeLimitExceededException(
    limitBytes: Long
) : IOException("Input exceeds the allowed limit of $limitBytes bytes")

class SizeLimitedInputStream(
    input: InputStream,
    private val limitBytes: Long
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = super.read()
        if (value != -1) recordBytes(1)
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) recordBytes(count)
        return count
    }

    private fun recordBytes(count: Int) {
        bytesRead += count
        if (bytesRead > limitBytes) throw SizeLimitExceededException(limitBytes)
    }
}
