package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.MAX_NETWORK_REDIRECTS
import com.example.pocastcloni.util.isUnsafeRedirect
import com.example.pocastcloni.util.parseNetworkUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class SafeRedirectInterceptor(
    private val isAllowedUrl: (String) -> Boolean = { parseNetworkUrl(it) != null },
    private val allowOriginChange: Boolean = true,
    private val isAllowedRedirect: (okhttp3.HttpUrl, okhttp3.HttpUrl) -> Boolean = { _, _ -> true }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var redirectCount = 0

        while (true) {
            val response = chain.proceed(request)
            if (response.code !in REDIRECT_CODES) return response

            val location = response.header("Location") ?: return response
            val target = request.url.resolve(location) ?: return response
            if (
                !isAllowedUrl(target.toString()) ||
                isUnsafeRedirect(request.url, target) ||
                (!allowOriginChange && !hasSameOrigin(request.url, target)) ||
                !isAllowedRedirect(request.url, target)
            ) {
                response.close()
                throw IOException("Blocked unsafe redirect")
            }
            if (++redirectCount > MAX_NETWORK_REDIRECTS) {
                response.close()
                throw IOException("Too many redirects")
            }

            if (request.method != "GET" && request.method != "HEAD") {
                response.close()
                throw IOException("Redirects are only supported for safe read requests")
            }
            val nextBuilder = request.newBuilder().url(target)
            if (!hasSameOrigin(request.url, target)) {
                nextBuilder.removeHeader("Authorization")
            }
            response.close()
            request = nextBuilder.build()
        }
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

internal fun hasSameOrigin(source: okhttp3.HttpUrl, target: okhttp3.HttpUrl): Boolean =
    source.scheme == target.scheme && source.host == target.host && source.port == target.port
