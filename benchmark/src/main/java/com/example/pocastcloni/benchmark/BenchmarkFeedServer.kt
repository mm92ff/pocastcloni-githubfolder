package com.example.pocastcloni.benchmark

import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

internal class BenchmarkFeedServer : Closeable {
    private val server = MockWebServer()

    val feedUrl: HttpUrl
        get() = server.url(FEED_PATH).newBuilder().host(LOOPBACK_ADDRESS).build()

    val requestCount: Int
        get() = server.requestCount

    fun start() {
        server.start()
        server.dispatcher = FeedDispatcher()
    }

    override fun close() {
        server.shutdown()
    }

    private inner class FeedDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            when (request.path) {
                FEED_PATH -> response("application/rss+xml", feedXml())
                COVER_PATH -> response("image/png", TINY_PNG)
                AUDIO_PATH -> response("audio/wav", SILENT_WAV)
                else -> MockResponse().setResponseCode(404)
            }
    }

    private fun feedXml(): String {
        val coverUrl = server.url(COVER_PATH).newBuilder().host(LOOPBACK_ADDRESS).build()
        val audioUrl = server.url(AUDIO_PATH).newBuilder().host(LOOPBACK_ADDRESS).build()
        val episodes =
            (1..EPISODE_COUNT).joinToString("\n") { number ->
                """
                <item>
                  <title>Benchmark Episode $number</title>
                  <description>Deterministic local benchmark episode $number.</description>
                  <link>https://benchmark.invalid/episode-$number</link>
                  <guid>benchmark-episode-$number</guid>
                  <pubDate>Wed, ${number.toString().padStart(2, '0')} Jul 2026 10:00:00 GMT</pubDate>
                  <itunes:duration>00:00:15</itunes:duration>
                  <enclosure url="$audioUrl" type="audio/wav" length="${SILENT_WAV.size}" />
                </item>
                """.trimIndent()
            }

        return """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>$FIXTURE_PODCAST_TITLE</title>
                <description>Deterministic local performance feed.</description>
                <link>https://benchmark.invalid/podcast</link>
                <image><url>$coverUrl</url></image>
                $episodes
              </channel>
            </rss>
        """.trimIndent()
    }

    private fun response(contentType: String, body: String): MockResponse =
        MockResponse().setHeader("Content-Type", contentType).setBody(body)

    private fun response(contentType: String, body: ByteArray): MockResponse =
        MockResponse().setHeader("Content-Type", contentType).setBody(Buffer().write(body))

    private companion object {
        const val FEED_PATH = "/feed.xml"
        const val COVER_PATH = "/cover.png"
        const val AUDIO_PATH = "/episode.wav"
        const val EPISODE_COUNT = 12
        const val LOOPBACK_ADDRESS = "127.0.0.1"

        val TINY_PNG: ByteArray =
            Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNoaGgAAAMEAYFL09IQAAAAAElFTkSuQmCC"
            )
        val SILENT_WAV: ByteArray = createSilentWav(seconds = 15)

        fun createSilentWav(seconds: Int): ByteArray {
            val sampleRate = 8_000
            val bitsPerSample = 16
            val channels = 1
            val bytesPerSample = bitsPerSample / 8
            val dataSize = sampleRate * seconds * channels * bytesPerSample
            val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(36 + dataSize)
            buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16)
            buffer.putShort(1)
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * channels * bytesPerSample)
            buffer.putShort((channels * bytesPerSample).toShort())
            buffer.putShort(bitsPerSample.toShort())
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataSize)
            repeat(dataSize / bytesPerSample) { buffer.putShort(0) }
            return buffer.array()
        }
    }
}
