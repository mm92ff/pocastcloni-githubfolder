package com.example.pocastcloni.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

class RedactingDebugTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        super.log(priority, tag, redactNetworkUrlsForLog(message), t)
    }
}

fun redactNetworkUrlsForLog(message: String): String =
    NETWORK_URL_TOKEN.replace(message) { match ->
        match.value.toHttpUrlOrNull()?.redactedOrigin() ?: REDACTED_URL
    }

private fun HttpUrl.redactedOrigin(): String {
    val formattedHost = if (host.contains(':')) "[$host]" else host
    return "$scheme://$formattedHost:$port"
}

private val NETWORK_URL_TOKEN = Regex("(?i)\\bhttps?://\\S+")
private const val REDACTED_URL = "[redacted-network-url]"
