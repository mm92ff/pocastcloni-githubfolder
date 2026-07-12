package com.example.pocastcloni.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal const val MAX_NETWORK_REDIRECTS = 5

fun parseNetworkUrl(value: String): HttpUrl? {
    val parsed = value.trim().toHttpUrlOrNull() ?: return null
    if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
    if (isLocalOrPrivateHost(parsed.host)) return null
    return parsed
}

fun requireNetworkUrl(value: String): HttpUrl =
    requireNotNull(parseNetworkUrl(value)) { "Only HTTP(S) URLs without embedded credentials are supported." }

fun requireApprovedNetworkUrl(value: String, allowInsecureHttp: Boolean): HttpUrl {
    val parsed = requireNetworkUrl(value)
    require(parsed.isHttps || allowInsecureHttp) { "HTTP resource has not been explicitly approved." }
    return parsed
}

fun requiresCleartextConfirmation(value: String): Boolean = parseNetworkUrl(value)?.isHttps == false

fun isAllowedRemoteResource(value: String, allowInsecureHttp: Boolean): Boolean {
    return runCatching { requireApprovedNetworkUrl(value, allowInsecureHttp) }.isSuccess
}

internal fun isUnsafeRedirect(source: HttpUrl, target: HttpUrl): Boolean =
    source.isHttps && !target.isHttps

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

private fun isLocalOrPrivateHost(host: String): Boolean {
    if (host.equals("localhost", ignoreCase = true) || host.endsWith(".localhost", ignoreCase = true)) {
        return true
    }
    val isIpLiteral = host.contains(':') || host.matches(Regex("[0-9.]+"))
    if (!isIpLiteral) return false
    return runCatching { InetAddress.getByName(host) }
        .getOrNull()
        ?.let(::isLocalOrPrivateAddress)
        ?: true
}
