package com.apkup.shared

import com.apkup.shared.engine.KmpAssetSelector
import com.apkup.shared.engine.KmpBatchVersionRequest
import com.apkup.shared.engine.KmpVersionEngine
import com.apkup.shared.model.KmpFDroidV2Index
import com.apkup.shared.network.KmpJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmpCoreTest {

    @Test
    fun `semver comparison and batch evaluation work across platforms`() {
        assertTrue(KmpVersionEngine.isNewer("1.46.0", "v1.47.0"))
        assertFalse(KmpVersionEngine.isNewer("1.47.0", "1.47.0-beta.1"))
        assertTrue(KmpVersionEngine.isNewer("1.47.0-beta.1", "1.47.0"))
        assertFalse(KmpVersionEngine.isNewer("2.4.0-261014", "v2026072802"))

        val batch = KmpVersionEngine.evaluateBatch(
            listOf(
                KmpBatchVersionRequest("a", "1.0.0", "v1.2.0-rc.1"),
                KmpBatchVersionRequest("b", "2.0.0", "v2.0.0")
            )
        )
        assertEquals(2, batch.size)
        assertTrue(batch[0].isNewer)
        assertTrue(batch[0].isPreRelease)
        assertEquals("RC", batch[0].preReleaseChannel)
        assertFalse(batch[1].isNewer)
    }

    @Test
    fun `abi asset selector picks arm64 foss asset cleanly`() {
        val assets = listOf(
            "app-armeabi-v7a.apk",
            "app-arm64-v8a-foss.apk",
            "app-arm64-v8a-gplay.apk",
            "app-universal.apk"
        )
        val picked = KmpAssetSelector.findBestApk(
            assets = assets,
            getFilename = { it },
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            preferFossFlavor = true
        )
        assertEquals("app-arm64-v8a-foss.apk", picked)
    }

    @Test
    fun `kotlinx serialization decodes FDroid V2 index cleanly`() {
        val sample = """
            {
              "repo": { "timestamp": 1730761200000, "version": 20002, "name": { "en-US": "IzzyOnDroid" } },
              "packages": {
                "com.example.demo": {
                  "metadata": { "name": { "en-US": "Demo App" } },
                  "versions": {
                    "h1": {
                      "file": { "name": "/demo_10.apk", "sha256": "abc", "size": 1234 },
                      "manifest": { "versionName": "1.0", "versionCode": 10, "nativecode": ["arm64-v8a"] }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val parsed = KmpJson.instance.decodeFromString<KmpFDroidV2Index>(sample)
        assertEquals(1, parsed.packages.size)
        assertEquals(10L, parsed.packages["com.example.demo"]?.versions?.values?.first()?.manifest?.versionCode)
    }
}
