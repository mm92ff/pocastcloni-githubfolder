package com.example.pocastcloni.data.remote

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only observation of local-network approval propagation in instrumentation tests. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalNetworkRegistryEntryPoint {
    fun localNetworkAccessRegistry(): LocalNetworkAccessRegistry
}
