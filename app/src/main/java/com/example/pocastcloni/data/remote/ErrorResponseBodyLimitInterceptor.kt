package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.Constants
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

class ErrorResponseBodyLimitInterceptor(
    private val maxBodyBytes: Long = Constants.SecurityLimits.MAX_RSS_ERROR_BODY_BYTES
) : Interceptor {
    init {
        require(maxBodyBytes in 1..Int.MAX_VALUE)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return response

        val originalBody = response.body ?: return response
        val contentType = originalBody.contentType()
        val limitedBytes =
            try {
                val buffer = Buffer()
                var remaining = maxBodyBytes
                while (remaining > 0) {
                    val read = originalBody.source().read(buffer, remaining)
                    if (read == -1L) break
                    remaining -= read
                }
                buffer.readByteArray()
            } finally {
                originalBody.close()
            }

        return response.newBuilder()
            .removeHeader("Content-Length")
            .header("Content-Length", limitedBytes.size.toString())
            .body(limitedBytes.toResponseBody(contentType))
            .build()
    }
}
