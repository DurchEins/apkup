package com.apkup.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {
    private val startedActivityCount = AtomicInteger(0)

    val isAppInForeground: Boolean
        get() = startedActivityCount.get() > 0

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount.decrementAndGet()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
