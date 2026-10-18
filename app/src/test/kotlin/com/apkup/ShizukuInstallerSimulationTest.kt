package com.apkup

import android.content.SharedPreferences
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.BackgroundCheckInterval
import com.apkup.prefs.Prefs
import com.apkup.repository.UpdateCheckProgressTracker
import com.apkup.ui.component.UpdateCardContent
import com.apkup.util.InstallLog
import com.apkup.util.InstallPart
import com.apkup.util.SessionInstaller
import com.apkup.util.ShizukuBridge
import com.apkup.worker.resolvePeriodicIntervalHours
import com.kryptoprefs.preferences.KryptoPrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ShizukuInstallerSimulationTest {

    @Test
    fun `setup status reflects installed, binder running, and permission states`() {
        val bridge = FakeShizukuBridge()
        val installer = createSimulatedInstaller(bridge)

        // State 1: Not installed
        val s1 = installer.getShizukuSetupStatus()
        assertFalse(s1.isInstalled)
        assertFalse(s1.isBinderRunning)
        assertFalse(s1.isPermissionGranted)
        assertFalse(s1.isReady)

        // State 2: Installed, binder not running
        bridge.installed = true
        val s2 = installer.getShizukuSetupStatus()
        assertTrue(s2.isInstalled)
        assertFalse(s2.isBinderRunning)
        assertFalse(s2.isPermissionGranted)
        assertFalse(s2.isReady)

        // State 3: Installed + binder running, permission not granted
        bridge.binderRunning = true
        val s3 = installer.getShizukuSetupStatus()
        assertTrue(s3.isInstalled)
        assertTrue(s3.isBinderRunning)
        assertFalse(s3.isPermissionGranted)
        assertFalse(s3.isReady)

        // State 4: Ready
        bridge.permissionGranted = true
        val s4 = installer.getShizukuSetupStatus()
        assertTrue(s4.isInstalled)
        assertTrue(s4.isBinderRunning)
        assertTrue(s4.isPermissionGranted)
        assertTrue(s4.isReady)
    }

    @Test
    fun `requestShizukuPermission and observeShizukuEvents propagate binder and permission transitions`() {
        val bridge = FakeShizukuBridge(installed = true, binderRunning = false, permissionGranted = false)
        val installer = createSimulatedInstaller(bridge)

        var lastPermissionResult: Boolean? = null
        var binderChangeCount = 0
        val unregister = installer.observeShizukuEvents(
            onPermissionResult = { granted -> lastPermissionResult = granted },
            onBinderChanged = { binderChangeCount++ }
        )

        // Cannot request permission while binder is inactive
        assertFalse(installer.requestShizukuPermission())
        assertEquals(0, bridge.permissionRequestsCount)

        // Simulate binder starting
        bridge.binderRunning = true
        bridge.dispatchBinderChanged()
        assertEquals(1, binderChangeCount)

        // Request permission -> triggers dialog
        assertFalse(installer.requestShizukuPermission())
        assertEquals(1, bridge.permissionRequestsCount)

        // User grants permission
        bridge.permissionGranted = true
        bridge.dispatchPermissionResult(true)
        assertEquals(true, lastPermissionResult)
        assertTrue(installer.requestShizukuPermission())

        unregister()
        bridge.dispatchBinderChanged()
        assertEquals(1, binderChangeCount)
    }

    @Test
    fun `install uses Shizuku when enabled and ready without invoking PackageInstaller fallback`() = runBlocking {
        val bridge = FakeShizukuBridge(
            installed = true,
            binderRunning = true,
            permissionGranted = true,
            shizukuInstallOutcome = { true }
        )
        var fallbackInvoked = false
        val fallbackNotices = mutableListOf<Int>()
        val prefs = Prefs(InMemoryKryptoPrefs()).apply {
            useShizukuInstaller.put(true)
        }
        val installer = createSimulatedInstaller(
            bridge = bridge,
            prefs = prefs,
            fallbackRunner = { _, _, _ ->
                fallbackInvoked = true
                true
            },
            onFallbackNotice = { fallbackNotices.add(it) }
        )

        val result = installer.installStreamProviders(
            id = 42,
            packageName = "com.example.app",
            streamProviders = listOf { ByteArrayInputStream("apk-bytes".toByteArray()) }
        )

        assertTrue(result)
        assertEquals(1, bridge.shizukuInstallCalls)
        assertFalse(fallbackInvoked)
        assertTrue(fallbackNotices.isEmpty())
    }

    @Test
    fun `install falls back to PackageInstaller when Shizuku binder is inactive`() = runBlocking {
        val bridge = FakeShizukuBridge(
            installed = true,
            binderRunning = false,
            permissionGranted = false
        )
        var fallbackInvoked = false
        val fallbackNotices = mutableListOf<Int>()
        val prefs = Prefs(InMemoryKryptoPrefs()).apply {
            useShizukuInstaller.put(true)
        }
        val installer = createSimulatedInstaller(
            bridge = bridge,
            prefs = prefs,
            fallbackRunner = { _, _, _ ->
                fallbackInvoked = true
                true
            },
            onFallbackNotice = { fallbackNotices.add(it) }
        )

        val result = installer.installStreamProviders(
            id = 77,
            packageName = "com.example.inactive",
            streamProviders = listOf { ByteArrayInputStream("apk-bytes".toByteArray()) }
        )

        assertTrue(result)
        assertEquals(0, bridge.shizukuInstallCalls)
        assertTrue(fallbackInvoked)
        assertEquals(listOf(R.string.shizuku_fallback_not_running), fallbackNotices)
    }

    @Test
    fun `install falls back to PackageInstaller when Shizuku permission is denied`() = runBlocking {
        val bridge = FakeShizukuBridge(
            installed = true,
            binderRunning = true,
            permissionGranted = false,
            awaitPermissionGranted = false
        )
        var fallbackInvoked = false
        val fallbackNotices = mutableListOf<Int>()
        val prefs = Prefs(InMemoryKryptoPrefs()).apply {
            useShizukuInstaller.put(true)
        }
        val installer = createSimulatedInstaller(
            bridge = bridge,
            prefs = prefs,
            fallbackRunner = { _, _, _ ->
                fallbackInvoked = true
                true
            },
            onFallbackNotice = { fallbackNotices.add(it) }
        )

        val result = installer.installStreamProviders(
            id = 88,
            packageName = "com.example.denied",
            streamProviders = listOf { ByteArrayInputStream("apk-bytes".toByteArray()) }
        )

        assertTrue(result)
        assertEquals(0, bridge.shizukuInstallCalls)
        assertTrue(fallbackInvoked)
        assertEquals(listOf(R.string.shizuku_fallback_no_permission), fallbackNotices)
    }

    @Test
    fun `install falls back to PackageInstaller when Shizuku process throws IOException`() = runBlocking {
        val bridge = FakeShizukuBridge(
            installed = true,
            binderRunning = true,
            permissionGranted = true,
            shizukuInstallOutcome = { throw IOException("Simulated binder death during pm install-write") }
        )
        var fallbackInvoked = false
        val fallbackNotices = mutableListOf<Int>()
        val prefs = Prefs(InMemoryKryptoPrefs()).apply {
            useShizukuInstaller.put(true)
        }
        val installer = createSimulatedInstaller(
            bridge = bridge,
            prefs = prefs,
            fallbackRunner = { _, _, _ ->
                fallbackInvoked = true
                true
            },
            onFallbackNotice = { fallbackNotices.add(it) }
        )

        val result = installer.installStreamProviders(
            id = 99,
            packageName = "com.example.crash",
            streamProviders = listOf { ByteArrayInputStream("apk-bytes".toByteArray()) }
        )

        assertTrue(result)
        assertEquals(1, bridge.shizukuInstallCalls)
        assertTrue(fallbackInvoked)
        assertEquals(listOf(R.string.shizuku_fallback_not_running), fallbackNotices)
    }

    @Test
    fun `background check intervals and active source progress tracking work as expected`() {
        assertEquals(6L, resolvePeriodicIntervalHours(6))
        assertEquals(12L, resolvePeriodicIntervalHours(12))
        assertEquals(24L, resolvePeriodicIntervalHours(24))
        assertEquals(48L, resolvePeriodicIntervalHours(48))
        assertEquals(168L, resolvePeriodicIntervalHours(168))
        assertEquals(24L, resolvePeriodicIntervalHours(999))
        assertEquals(BackgroundCheckInterval.DAYS_7, BackgroundCheckInterval.fromHours(168))

        val tracker = UpdateCheckProgressTracker(
            packageNames = listOf("com.example.one", "com.example.two"),
            sourceNames = listOf("APKMirror", "Google Play Store")
        )
        assertEquals("APKMirror", tracker.activeSourceName())
        tracker.markBatchSucceeded("APKMirror", listOf("com.example.one"))
        assertEquals("APKMirror", tracker.activeSourceName())
        tracker.markSourceFinished("APKMirror")
        assertEquals("Google Play Store", tracker.activeSourceName())
        tracker.markSourceFinished("Google Play Store")
        assertNull(tracker.activeSourceName())

        val cardMulti = UpdateCardContent(
            appName = "App",
            packageName = "com.example.one",
            installedVersion = "1.0",
            availableVersion = "2.0",
            source = ApkMirrorSource,
            isInstalling = false,
            additionalSourcesCount = 1
        )
        val cardSingle = cardMulti.copy(additionalSourcesCount = 0)
        assertEquals("APKMirror +1", cardMulti.formatSourceWithCount("APKMirror"))
        assertEquals("Google Play Store", cardSingle.formatSourceWithCount("Google Play Store"))
    }

    private fun createSimulatedInstaller(
        bridge: FakeShizukuBridge,
        prefs: Prefs = Prefs(InMemoryKryptoPrefs()),
        fallbackRunner: suspend (Int, String, List<InstallPart>) -> Boolean = { _, _, _ -> true },
        onFallbackNotice: (Int) -> Unit = {}
    ): SessionInstaller = SessionInstaller(
        context = null,
        installLog = InstallLog(),
        prefs = prefs,
        shizukuBridgeOverride = bridge,
        packageInstallerFallbackOverride = fallbackRunner,
        onFallbackNotice = onFallbackNotice
    )

    private class FakeShizukuBridge(
        var installed: Boolean = false,
        var binderRunning: Boolean = false,
        var permissionGranted: Boolean = false,
        var awaitPermissionGranted: Boolean = false,
        var shizukuInstallOutcome: () -> Boolean = { true }
    ) : ShizukuBridge {
        var permissionRequestsCount = 0
        var shizukuInstallCalls = 0
        private var permissionListener: ((Boolean) -> Unit)? = null
        private var binderListener: (() -> Unit)? = null

        override fun isPackageInstalled(): Boolean = installed
        override fun pingBinder(): Boolean = binderRunning
        override fun isPermissionGranted(): Boolean = permissionGranted

        override fun requestPermission(requestCode: Int) {
            permissionRequestsCount++
        }

        override fun observeEvents(
            onPermissionResult: (Boolean) -> Unit,
            onBinderChanged: () -> Unit
        ): () -> Unit {
            permissionListener = onPermissionResult
            binderListener = onBinderChanged
            return {
                permissionListener = null
                binderListener = null
            }
        }

        override suspend fun awaitPermissionResult(timeoutMs: Long): Boolean {
            permissionRequestsCount++
            if (awaitPermissionGranted) {
                permissionGranted = true
            }
            return awaitPermissionGranted
        }

        override fun executeShizukuInstall(
            id: Int,
            packageName: String,
            parts: List<InstallPart>
        ): Boolean {
            shizukuInstallCalls++
            return shizukuInstallOutcome()
        }

        fun dispatchPermissionResult(granted: Boolean) {
            permissionListener?.invoke(granted)
        }

        fun dispatchBinderChanged() {
            binderListener?.invoke()
        }
    }

    private class InMemoryKryptoPrefs : KryptoPrefs {
        private val values = mutableMapOf<String, Any>()
        override fun putString(key: String, value: String?): KryptoPrefs = put(key, value)
        override fun putBoolean(key: String, value: Boolean?): KryptoPrefs = put(key, value)
        override fun putInt(key: String, value: Int?): KryptoPrefs = put(key, value)
        override fun putLong(key: String, value: Long?): KryptoPrefs = put(key, value)
        override fun getString(key: String, defaultValue: String?): String? = values[key] as? String ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun remove(key: String): KryptoPrefs = apply { values.remove(key) }
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun clear() = values.clear()
        override fun name(): String = "sim-test"
        override fun sharedPreferences(): SharedPreferences = error("Unused")
        private fun put(key: String, value: Any?): KryptoPrefs = apply {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
