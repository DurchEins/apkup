package com.apkup.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {
    private val startedActivityCount = AtomicInteger(0)
    private val recreatingActivityCount = AtomicInteger(0)
    private val lastRecreationStopMs = AtomicLong(0L)

    private const val RECREATION_GRACE_WINDOW_MS = 3_000L

    val isAppInForeground: Boolean
        get() {
            if (startedActivityCount.get() > 0) return true
            if (recreatingActivityCount.get() > 0) {
                val elapsed = System.currentTimeMillis() - lastRecreationStopMs.get()
                if (elapsed in 0..RECREATION_GRACE_WINDOW_MS) {
                    return true
                }
                recreatingActivityCount.set(0)
            }
            return false
        }

    override fun onActivityStarted(activity: Activity) {
        if (recreatingActivityCount.get() > 0) {
            recreatingActivityCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        }
        startedActivityCount.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity.isChangingConfigurations) {
            lastRecreationStopMs.set(System.currentTimeMillis())
            recreatingActivityCount.incrementAndGet()
        }
        startedActivityCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
