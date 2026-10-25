package com.apkup

import android.content.Context
import android.content.ContextWrapper
import com.apkup.service.DownloadTaskState
import com.apkup.util.DownloadNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadServiceLifecycleTest {

    private class FakeContext : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
    }

    @Test
    fun `Manifest declares required foreground service permissions and dataSync service component`() {
        val manifestFile = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("app/src/main/AndroidManifest.xml")

        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText()

        assertTrue(
            "Manifest must declare FOREGROUND_SERVICE permission",
            content.contains("android.permission.FOREGROUND_SERVICE\"")
        )
        assertTrue(
            "Manifest must declare FOREGROUND_SERVICE_DATA_SYNC permission",
            content.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC\"")
        )
        assertTrue(
            "Manifest must declare DownloadInstallService with foregroundServiceType dataSync",
            content.contains("android:name=\".service.DownloadInstallService\"") &&
            content.contains("android:foregroundServiceType=\"dataSync\"")
        )
        assertTrue(
            "DownloadInstallService must be exported=false",
            content.contains("android:exported=\"false\"")
        )
    }

    @Test
    fun `DownloadNotificationManager throttling accurately throttles rapid updates while permitting milestone deltas`() {
        val manager = DownloadNotificationManager(FakeContext())

        // First call sets lastUpdateTime and returns false (not throttled)
        val firstCall = manager.shouldThrottle(10)
        assertFalse("First update must pass through", firstCall)

        // Immediate subsequent call with minor delta (1%) and small time delta must be throttled
        val throttledCall = manager.shouldThrottle(11, force = false)
        assertTrue("Rapid update with <2% delta must be throttled", throttledCall)

        // Force = true must never be throttled
        val forcedCall = manager.shouldThrottle(11, force = true)
        assertFalse("Forced update must not be throttled", forcedCall)

        // Milestone 100% must never be throttled
        val completeCall = manager.shouldThrottle(100, force = false)
        assertFalse("100% complete milestone must not be throttled", completeCall)

        // Milestone 0% must never be throttled
        val startCall = manager.shouldThrottle(0, force = false)
        assertFalse("0% start milestone must not be throttled", startCall)
    }

    @Test
    fun `DownloadTaskState maintains correct lifecycle transitions`() {
        var task = DownloadTaskState(
            id = 42,
            packageName = "org.example.app",
            appName = "Example App",
            isInstalling = false,
            isQueued = true
        )

        assertTrue(task.isQueued)
        assertFalse(task.isInstalling)
        assertEquals(0L, task.progress)

        // Transition: active downloading
        task = task.copy(isQueued = false, progress = 5_000_000L, total = 10_000_000L)

        assertFalse(task.isQueued)
        assertEquals(5_000_000L, task.progress)
        assertEquals(10_000_000L, task.total)

        // Transition: installing
        task = task.copy(isInstalling = true)
        assertTrue(task.isInstalling)
    }
}
