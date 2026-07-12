package com.example.pocastcloni.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal const val MAX_NETWORK_REDIRECTS = 5

fun parseNetworkUrl(
    value: String,
    allowLocalNetwork: Boolean = false
): HttpUrl? {
    val parsed = value.trim().toHttpUrlOrNull() ?: return null
    if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
    if (!allowLocalNetwork && isLocalOrPrivateHost(parsed.host)) return null
    return parsed
}

fun requireNetworkUrl(value: String): HttpUrl =
    requireNotNull(parseNetworkUrl(value)) { "Only HTTP(S) URLs without embedded credentials are supported." }

fun requireApprovedNetworkUrl(
    value: String,
    allowInsecureHttp: Boolean,
    allowLocalNetwork: Boolean = false
): HttpUrl {
    val parsed = requireNotNull(parseNetworkUrl(value, allowLocalNetwork)) {
        "Network URL requires explicit local-network approval."
    }
    require(parsed.isHttps || allowInsecureHttp) { "HTTP resource has not been explicitly approved." }
    return parsed
}

fun requiresCleartextConfirmation(value: String): Boolean =
    parseNetworkUrl(value, allowLocalNetwork = true)?.isHttps == false

fun requiresLocalNetworkConfirmation(value: String): Boolean {
    val parsed = parseNetworkUrl(value, allowLocalNetwork = true) ?: return false
    return isLocalOrPrivateHost(parsed.host)
}

fun isAllowedRemoteResource(
    value: String,
    allowInsecureHttp: Boolean,
    allowLocalNetwork: Boolean = false
): Boolean {
    return runCatching {
        requireApprovedNetworkUrl(value, allowInsecureHttp, allowLocalNetwork)
    }.isSuccess
}

fun requireApprovedPodcastResource(
    feedUrl: String,
    resourceUrl: String,
    allowInsecureHttp: Boolean,
    allowLocalNetwork: Boolean
): HttpUrl {
    val feed = requireApprovedNetworkUrl(feedUrl, allowInsecureHttp, allowLocalNetwork)
    val resource = requireApprovedNetworkUrl(
        resourceUrl,
        allowInsecureHttp,
        allowLocalNetwork
    )
    val sharesApprovedLocalHost =
        allowLocalNetwork && resource.host.equals(feed.host, ignoreCase = true)
    if (isLocalOrPrivateHost(resource.host) || sharesApprovedLocalHost) {
        require(allowLocalNetwork && hasSameOrigin(feed, resource)) {
            "Local podcast resources must match the approved feed origin."
        }
    }
    return resource
}

fun isAllowedPodcastResource(
    feedUrl: String,
    resourceUrl: String,
    allowInsecureHttp: Boolean,
    allowLocalNetwork: Boolean
): Boolean = runCatching {
    requireApprovedPodcastResource(
        feedUrl,
        resourceUrl,
        allowInsecureHttp,
        allowLocalNetwork
    )
}.isSuccess

fun shouldUseLocalNetworkForResource(
    feedUrl: String,
    resourceUrl: String,
    allowLocalNetwork: Boolean
): Boolean {
    if (!allowLocalNetwork) return false
    val feed = parseNetworkUrl(feedUrl, allowLocalNetwork = true) ?: return false
    val resource = parseNetworkUrl(resourceUrl, allowLocalNetwork = true) ?: return false
    return hasSameOrigin(feed, resource)
}

fun networkOrigin(value: String): String? {
    val url = parseNetworkUrl(value, allowLocalNetwork = true) ?: return null
    return "${url.scheme}|${url.host}|${url.port}"
}

fun networkHost(value: String): String? =
    parseNetworkUrl(value, allowLocalNetwork = true)?.host

internal fun isUnsafeRedirect(source: HttpUrl, target: HttpUrl): Boolean =
    source.isHttps && !target.isHttps

internal fun hasSameOrigin(source: HttpUrl, target: HttpUrl): Boolean =
    source.scheme == target.scheme && source.host == target.host && source.port == target.port

internal fun isLocalOrPrivateAddress(address: InetAddress): Boolean {
    if (
        address.isAnyLocalAddress ||
        address.isLoopbackAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress
    ) {
        return true
    }
    return when (address) {
        is Inet4Address -> {
            val octets = address.address.map { it.toInt() and 0xFF }
            octets[0] == 0 ||
                (octets[0] == 100 && octets[1] in 64..127) ||
                (octets[0] == 198 && octets[1] in 18..19)
        }
        is Inet6Address -> (address.address.first().toInt() and 0xFE) == 0xFC
        else -> true
    }
}

internal fun isLocalOrPrivateHost(host: String): Boolean {
    if (
        host.equals("localhost", ignoreCase = true) ||
        host.endsWith(".localhost", ignoreCase = true) ||
        (!host.contains('.') && !host.contains(':')) ||
        host.endsWith(".local", ignoreCase = true) ||
        host.endsWith(".lan", ignoreCase = true) ||
        host.endsWith(".home.arpa", ignoreCase = true)
    ) {
        return true
    }
    val isIpLiteral = host.contains(':') || host.matches(Regex("[0-9.]+"))
    if (!isIpLiteral) return false
    return runCatching { InetAddress.getByName(host) }
        .getOrNull()
        ?.let(::isLocalOrPrivateAddress)
        ?: true
}
