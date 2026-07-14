package com.example.pocastcloni.playback.infrastructure

import android.os.SystemClock
import javax.inject.Inject

fun interface MonotonicClock {
    fun elapsedRealtimeMs(): Long
}

class SystemMonotonicClock
@Inject
constructor() : MonotonicClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
