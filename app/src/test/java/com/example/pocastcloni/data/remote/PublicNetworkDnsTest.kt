package com.example.pocastcloni.data.remote

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class PublicNetworkDnsTest {
    @Test
    fun `accepts public DNS answers`() {
        val publicAddress = InetAddress.getByName("93.184.216.34")
        val dns = PublicNetworkDns(dnsReturning(publicAddress))

        assertEquals(listOf(publicAddress), dns.lookup("example.com"))
    }

    @Test
    fun `rejects private and mixed DNS answers`() {
        val publicAddress = InetAddress.getByName("93.184.216.34")
        val privateAddress = InetAddress.getByName("10.0.0.1")

        assertThrows(UnknownHostException::class.java) {
            PublicNetworkDns(dnsReturning(privateAddress)).lookup("example.com")
        }
        assertThrows(UnknownHostException::class.java) {
            PublicNetworkDns(dnsReturning(publicAddress, privateAddress)).lookup("example.com")
        }
    }

    @Test
    fun `propagates resolver failures`() {
        val failure = UnknownHostException("not found")
        val dns = PublicNetworkDns(
            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = throw failure
            }
        )

        val thrown = assertThrows(UnknownHostException::class.java) {
            dns.lookup("missing.example")
        }
        assertEquals(failure, thrown)
    }

    private fun dnsReturning(vararg addresses: InetAddress): Dns =
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = addresses.toList()
        }
}
