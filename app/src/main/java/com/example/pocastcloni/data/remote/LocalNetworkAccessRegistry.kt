package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.networkOrigin
import com.example.pocastcloni.util.networkHost
import com.example.pocastcloni.domain.repository.LocalNetworkApprovalPort
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Dns
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNetworkAccessRegistry
@Inject
constructor() : LocalNetworkApprovalPort {
    private val approvedOrigins = ConcurrentHashMap.newKeySet<String>()
    private val approvedHosts = ConcurrentHashMap.newKeySet<String>()

    override fun approveFeed(feedUrl: String) {
        networkOrigin(feedUrl)?.let(approvedOrigins::add)
        networkHost(feedUrl)?.let(approvedHosts::add)
    }

    fun replaceApprovedFeeds(feedUrls: Collection<String>) {
        val origins = feedUrls.mapNotNull(::networkOrigin).toSet()
        val hosts = feedUrls.mapNotNull(::networkHost).toSet()
        approvedOrigins.clear()
        approvedOrigins.addAll(origins)
        approvedHosts.clear()
        approvedHosts.addAll(hosts)
    }

    fun isApproved(url: String): Boolean =
        networkOrigin(url)?.let(approvedOrigins::contains) == true

    fun isHostApproved(host: String): Boolean = approvedHosts.any { approved ->
        approved.equals(host, ignoreCase = true)
    }
}

class ApprovedLocalRequestInterceptor(
    private val registry: LocalNetworkAccessRegistry
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val approvedOrigin = registry.isApproved(url.toString())
        if (
            (!approvedOrigin && registry.isHostApproved(url.host)) ||
            (!approvedOrigin && com.example.pocastcloni.util.parseNetworkUrl(url.toString()) == null)
        ) {
            throw IOException("Local network origin is not approved")
        }
        return chain.proceed(chain.request())
    }
}

class ApprovedOriginDns(
    private val registry: LocalNetworkAccessRegistry,
    private val publicDns: Dns = PublicNetworkDns(),
    private val systemDns: Dns = Dns.SYSTEM
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        if (registry.isHostApproved(hostname)) {
            systemDns.lookup(hostname)
        } else {
            publicDns.lookup(hostname)
        }
}
