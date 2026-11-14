package com.apkup

import com.apkup.data.izzy.IzzyPackageInfo
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.AppsQuickFilter
import com.apkup.data.ui.CodebergSource
import com.apkup.data.ui.CustomFDroidPresets
import com.apkup.data.ui.CustomFDroidRepo
import com.apkup.data.ui.CustomFDroidSource
import com.apkup.data.ui.WatchedAppEntry
import com.apkup.data.ui.filterInstalledApps
import com.apkup.data.ui.isVerifiableVersionUpgrade
import com.apkup.data.ui.isWatchedApp
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.repository.IzzyOnDroidRepository
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedAppsAndCustomFDroidTest {

    @Test
    fun filterInstalledAppsWatchedChipReturnsOnlyWatchedApps() {
        val installed = AppInstalled(
            name = "Signal",
            packageName = "org.thoughtcrime.securesms",
            version = "7.0.0",
            versionCode = 700
        )
        val watched = AppInstalled(
            name = "Gadgetbridge",
            packageName = "nodomain.freeyourgadget.gadgetbridge",
            version = "",
            versionCode = -1L,
            isWatchedOnly = true
        )

        val watchedOnly = filterInstalledApps(
            apps = listOf(installed, watched),
            searchQuery = "",
            quickFilter = AppsQuickFilter.WATCHED
        )

        assertEquals(1, watchedOnly.size)
        assertEquals("nodomain.freeyourgadget.gadgetbridge", watchedOnly.first().packageName)
    }

    @Test
    fun watchedAppUpdatePassesVerifiableUpgradeAndSignatureSkip() {
        assertTrue(isVerifiableVersionUpgrade("", "1.5.0", installedVersionCode = -1L, availableVersionCode = 150L))

        val watchedUpdate = AppUpdate(
            name = "Gadgetbridge",
            packageName = "nodomain.freeyourgadget.gadgetbridge",
            version = "0.84.0",
            oldVersion = "",
            versionCode = 840,
            oldVersionCode = -1L,
            source = CodebergSource
        )
        assertTrue(watchedUpdate.isWatchedApp())

        val repo = IzzyOnDroidRepository(
            okHttpClient = OkHttpClient(),
            gson = Gson(),
            repoBaseUrl = "https://f-droid.org/repo",
            targetSource = CustomFDroidSource,
            repoDisplayName = "F-Droid"
        )
        val placeholder = AppInstalled(
            name = "App",
            packageName = "com.example.app",
            version = "",
            versionCode = -1L,
            signatureSha256 = "",
            isWatchedOnly = true
        )
        val pkg = IzzyPackageInfo(
            versionName = "2.1.0",
            versionCode = 210L,
            apkName = "app-210.apk",
            signer = "deadbeefcafebabe",
            minSdkVersion = 21
        )
        val chosen = repo.findBestPackage(
            app = placeholder,
            packages = listOf(pkg),
            supportedAbis = arrayOf("arm64-v8a"),
            currentSdk = 35
        )
        assertNotNull(chosen)
        assertEquals("app-210.apk", chosen!!.apkName)
    }

    @Test
    fun prefsPersistWatchedAppsAndCustomFDroidRepos() {
        val prefs = Prefs(InMemoryPrefsBackend())
        assertTrue(CustomFDroidPresets.presets.isNotEmpty())
        val fDroidOfficial = CustomFDroidPresets.presets.first()
        prefs.writeCustomFDroidRepos(listOf(fDroidOfficial))
        assertEquals(1, prefs.readCustomFDroidReposSafe().size)
        assertEquals(fDroidOfficial.baseUrl, prefs.readCustomFDroidReposSafe().first().baseUrl)

        prefs.addWatchedApp(WatchedAppEntry(packageName = "com.example.watch", appName = "Watch App"))
        assertEquals(1, prefs.readWatchedAppsSafe().size)
        prefs.removeWatchedApp("com.example.watch")
        assertFalse(prefs.readWatchedAppsSafe().any { it.packageName == "com.example.watch" })
    }

    @Test
    fun customFDroidPresetsExcludesMullvadAndNormalizedBaseUrlPreservesArchiveAndCustomPaths() {
        assertFalse(CustomFDroidPresets.presets.any { it.id == "mullvad" })

        assertEquals(
            "https://f-droid.org/repo/",
            CustomFDroidRepo(baseUrl = "https://f-droid.org").normalizedBaseUrl()
        )
        assertEquals(
            "https://f-droid.org/repo/",
            CustomFDroidRepo(baseUrl = "https://f-droid.org/").normalizedBaseUrl()
        )
        assertEquals(
            "https://apt.izzysoft.de/fdroid/repo/",
            CustomFDroidRepo(baseUrl = "https://apt.izzysoft.de/fdroid").normalizedBaseUrl()
        )
        assertEquals(
            "https://f-droid.org/archive/",
            CustomFDroidRepo(baseUrl = "https://f-droid.org/archive").normalizedBaseUrl()
        )
        assertEquals(
            "https://apt.izzysoft.de/fdroid/archive/",
            CustomFDroidRepo(baseUrl = "https://apt.izzysoft.de/fdroid/archive/").normalizedBaseUrl()
        )
        assertEquals(
            "https://repo.example.org/custom-apps/",
            CustomFDroidRepo(baseUrl = "https://repo.example.org/custom-apps").normalizedBaseUrl()
        )
    }

    @Test
    fun izzyOnDroidOptOutStopsDefaultPresetScanWhileAllowingCustomFDroidRepos() {
        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            useIzzyOnDroid.put(false)
            useCustomFDroidRepos.put(true)
        }
        val repo = IzzyOnDroidRepository(
            okHttpClient = OkHttpClient(),
            gson = Gson(),
            prefs = prefs
        )
        assertTrue(
            "IzzyOnDroidRepository must return no active repos when useIzzyOnDroid is false",
            repo.resolveActiveRepos().isEmpty()
        )

        prefs.useIzzyOnDroid.put(true)
        assertTrue(repo.resolveActiveRepos().any { it.id == com.apkup.data.fdroid.FDroidRepoConfig.ID_IZZYONDROID })
    }
}
