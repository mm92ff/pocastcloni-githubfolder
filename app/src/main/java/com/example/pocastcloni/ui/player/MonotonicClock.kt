package com.example.pocastcloni.ui.player

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
