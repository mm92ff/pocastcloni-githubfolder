package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.isLocalOrPrivateAddress
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class PublicNetworkDns(
    private val delegate: Dns = Dns.SYSTEM
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty() || addresses.any(::isLocalOrPrivateAddress)) {
            throw UnknownHostException("Local and private network destinations are not allowed")
        }
        return addresses
    }
}
