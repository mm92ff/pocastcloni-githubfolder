package com.example.pocastcloni.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.pocastcloni.di.ApplicationScope // Ensure this Qualifier exists
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

interface ConnectivityProvider {
    val wifiStatus: StateFlow<Boolean>
}

@Singleton
class NetworkConnectivityProvider
@Inject
constructor(
    @ApplicationContext private val context: Context,
    // FIX: Inject the central Application Scope instead of creating a new one
    @ApplicationScope private val externalScope: CoroutineScope
) : ConnectivityProvider {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val wifiStatus: StateFlow<Boolean> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(isWifiConnected())
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        caps: NetworkCapabilities
                    ) {
                        trySend(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                    }
                }

            trySend(isWifiConnected())
            connectivityManager.registerDefaultNetworkCallback(callback)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }
            .distinctUntilChanged()
            .stateIn(
                scope = externalScope, // Use injected scope
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = isWifiConnected()
            )

    private fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
