package com.apkup

import android.content.SharedPreferences
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.UpdateHistoryEntry
import com.apkup.data.ui.UpdateInstallMode
import com.apkup.prefs.Prefs
import com.apkup.util.resolvePreviousVersionUrl
import com.kryptoprefs.preferences.KryptoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateHistoryTest {

    @Test
    fun `resolvePreviousVersionUrl builds accurate rollback links across all 5 sources`() {
        // 1. GitHub release asset link -> filtered releases page for oldVersion
        val githubUrl = resolvePreviousVersionUrl(
            packageName = "org.schabi.newpipe",
            appName = "NewPipe",
            oldVersion = "v0.27.2",
            sourceName = GitHubSource.name,
            linkUrl = "https://github.com/TeamNewPipe/NewPipe/releases/download/v0.27.3/NewPipe_v0.27.3.apk"
        )
        assertEquals("https://github.com/TeamNewPipe/NewPipe/releases?q=0.27.2", githubUrl)

        // 2. GitLab custom subgroup binding -> project releases archive
        val gitlabUrl = resolvePreviousVersionUrl(
            packageName = "com.aurora.store",
            appName = "Aurora Store",
            oldVersion = "4.6.1",
            sourceName = GitLabSource.name,
            repoBinding = RepoBinding(
                type = "gitlab",
                repo = "AuroraOSS/AuroraStore",
                host = "gitlab.com"
            )
        )
        assertEquals("https://gitlab.com/AuroraOSS/AuroraStore/-/releases", gitlabUrl)

        // 3. IzzyOnDroid -> official package index archive page
        val izzyUrl = resolvePreviousVersionUrl(
            packageName = "de.tutao.tutanota",
            appName = "Tuta Mail",
            oldVersion = "250.2410.0",
            sourceName = IzzyOnDroidSource.name
        )
        assertEquals("https://apt.izzysoft.de/fdroid/index/apk/de.tutao.tutanota", izzyUrl)

        // 4. APKMirror variant link -> canonical app release archive page
        val apkMirrorUrl = resolvePreviousVersionUrl(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            oldVersion = "19.43.41",
            sourceName = ApkMirrorSource.name,
            linkUrl = "https://www.apkmirror.com/apk/google-inc/youtube/youtube-19-44-39-release/youtube-19-44-39-android-apk-download/"
        )
        assertEquals("https://www.apkmirror.com/apk/google-inc/youtube/", apkMirrorUrl)

        // 5. Google Play -> developer-signed APKMirror release search for App Name + oldVersion
        val playFallbackUrl = resolvePreviousVersionUrl(
            packageName = "org.thoughtcrime.securesms",
            appName = "Signal",
            oldVersion = "7.23.2",
            sourceName = PlaySource.name
        )
        assertEquals(
            "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=Signal+7.23.2",
            playFallbackUrl
        )
    }

    @Test
    fun `Prefs recordUpdateHistory deduplicates within window, caps at max entries, and clears cleanly`() {
        val prefs = Prefs(InMemoryPrefs())
        val baseTime = 1_760_000_000_000L

        val first = UpdateHistoryEntry(
            packageName = "com.example.alpha",
            appName = "Alpha",
            oldVersion = "1.0.0",
            newVersion = "1.1.0",
            oldVersionCode = 10L,
            newVersionCode = 11L,
            sourceName = GitHubSource.name,
            installedAtMillis = baseTime,
            installModeName = UpdateInstallMode.MANUAL_SHIZUKU.name,
            previousVersionUrl = "https://github.com/example/alpha/releases?q=1.0.0"
        )
        prefs.recordUpdateHistory(first)
        // Duplicate emission 3 seconds later (e.g., Service + ViewModel) must be ignored
        prefs.recordUpdateHistory(first.copy(installedAtMillis = baseTime + 3_000L))
        assertEquals(1, prefs.readUpdateHistorySafe().size)

        // Cap enforcement
        for (idx in 1..60) {
            prefs.recordUpdateHistory(
                UpdateHistoryEntry(
                    packageName = "com.example.pkg$idx",
                    appName = "App $idx",
                    oldVersion = "1.0",
                    newVersion = "2.0",
                    newVersionCode = idx.toLong(),
                    installedAtMillis = baseTime + idx * 10_000L
                ),
                maxEntries = 50
            )
        }
        val capped = prefs.readUpdateHistorySafe()
        assertEquals(50, capped.size)
        assertEquals("com.example.pkg60", capped.first().packageName)

        prefs.clearUpdateHistory()
        assertTrue(prefs.readUpdateHistorySafe().isEmpty())
        assertTrue(prefs.updateHistoryFlow.value.isEmpty())
    }

    private class InMemoryPrefs : KryptoPrefs {
        private val map = mutableMapOf<String, Any>()
        override fun putString(key: String, value: String?): KryptoPrefs = put(key, value)
        override fun putBoolean(key: String, value: Boolean?): KryptoPrefs = put(key, value)
        override fun putInt(key: String, value: Int?): KryptoPrefs = put(key, value)
        override fun putLong(key: String, value: Long?): KryptoPrefs = put(key, value)
        override fun getString(key: String, defaultValue: String?): String? = map[key] as? String ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
        override fun remove(key: String): KryptoPrefs = apply { map.remove(key) }
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun clear() = map.clear()
        override fun name(): String = "history-test"
        override fun sharedPreferences(): SharedPreferences = error("Unused in unit test")
        private fun put(key: String, value: Any?): KryptoPrefs = apply {
            if (value == null) map.remove(key) else map[key] = value
        }
    }
}
