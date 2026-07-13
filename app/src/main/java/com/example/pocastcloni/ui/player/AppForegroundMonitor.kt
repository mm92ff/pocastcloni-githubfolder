package com.example.pocastcloni.ui.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface AppForegroundMonitor {
    val isForeground: StateFlow<Boolean>
}

internal class ForegroundActivityCounter {
    private var startedActivities = 0

    fun onActivityStarted(): Boolean {
        startedActivities += 1
        return startedActivities > 0
    }

    fun onActivityStopped(): Boolean {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        return startedActivities > 0
    }
}

@Singleton
class ActivityAppForegroundMonitor
@Inject
constructor(
    @ApplicationContext context: Context
) : AppForegroundMonitor, Application.ActivityLifecycleCallbacks {
    private val application = context.applicationContext as Application
    private val counter = ForegroundActivityCounter()
    private val _isForeground = MutableStateFlow(false)
    private var initialized = false

    override val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun initialize() {
        if (initialized) return
        initialized = true
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        _isForeground.value = counter.onActivityStarted()
    }

    override fun onActivityStopped(activity: Activity) {
        _isForeground.value = counter.onActivityStopped()
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
    ) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
