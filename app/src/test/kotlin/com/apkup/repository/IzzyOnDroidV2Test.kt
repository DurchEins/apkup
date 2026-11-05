package com.apkup.repository

import com.apkup.data.fdroid.FDroidV2Entry
import com.apkup.data.fdroid.FDroidV2Index
import com.apkup.data.fdroid.getPreferredText
import com.apkup.data.fdroid.toIzzyIndexResponse
import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class IzzyOnDroidV2Test {

    private val gson = Gson()

    @Test
    fun `deserialize FDroidV2Entry from json correctly`() {
        val json = """
            {
                "timestamp": 1730761200000,
                "version": 20002,
                "maxDownloadThreads": 4,
                "index": {
                    "name": "/index-v2.json",
                    "sha256": "8e54d94750719eb8a879bc49130eaba0ecabaae5222ad685ea7005b31f2736dc",
                    "size": 184520,
                    "numPackages": 120
                }
            }
        """.trimIndent()

        val entry = gson.fromJson(json, FDroidV2Entry::class.java)
        assertEquals(1730761200000L, entry.timestamp)
        assertEquals(20002L, entry.version)
        assertNotNull(entry.index)
        assertEquals("/index-v2.json", entry.index?.name)
        assertEquals("8e54d94750719eb8a879bc49130eaba0ecabaae5222ad685ea7005b31f2736dc", entry.index?.sha256)
        assertEquals(184520L, entry.index?.size)
        assertEquals(120, entry.index?.numPackages)
    }

    @Test
    fun `deserialize and convert FDroidV2Index to IzzyIndexResponse with localized metadata`() {
        val json = """
            {
                "repo": {
                    "timestamp": 1730761200000,
                    "version": 20002,
                    "name": {
                        "en-US": "IzzyOnDroid F-Droid Repository",
                        "de-DE": "IzzyOnDroid F-Droid Paketquelle"
                    },
                    "description": {
                        "en-US": "Official Izzy repo",
                        "de-DE": "Offizielles Izzy Repo"
                    },
                    "address": "https://apt.izzysoft.de/fdroid/repo"
                },
                "packages": {
                    "com.example.sampleapp": {
                        "metadata": {
                            "added": 1700000000000,
                            "lastUpdated": 1730761200000,
                            "name": {
                                "en-US": "Sample App",
                                "de-DE": "Beispiel App"
                            },
                            "summary": {
                                "en-US": "A great sample app",
                                "de-DE": "Eine tolle Beispiel-App"
                            },
                            "categories": ["Utility", "System"],
                            "sourceCode": "https://github.com/example/sampleapp"
                        },
                        "versions": {
                            "hash1": {
                                "added": 1730761000000,
                                "file": {
                                    "name": "/com.example.sampleapp_102.apk",
                                    "sha256": "abcdef1234567890",
                                    "size": 5242880
                                },
                                "manifest": {
                                    "versionName": "1.2.0",
                                    "versionCode": 102,
                                    "usesSdk": {
                                        "minSdkVersion": 26,
                                        "targetSdkVersion": 34
                                    },
                                    "signer": {
                                        "sha256": ["11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff"]
                                    },
                                    "nativecode": ["arm64-v8a"]
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val indexV2 = gson.fromJson(json, FDroidV2Index::class.java)
        assertNotNull(indexV2.repo)
        assertEquals(1, indexV2.packages.size)

        // Test English locale conversion
        val convertedEn = indexV2.toIzzyIndexResponse(Locale.US)
        assertEquals("IzzyOnDroid F-Droid Repository", convertedEn.repo?.name)
        assertEquals("Official Izzy repo", convertedEn.repo?.description)
        assertEquals(1, convertedEn.apps.size)

        val appEn = convertedEn.apps.first()
        assertEquals("com.example.sampleapp", appEn.packageName)
        assertEquals("Sample App", appEn.name)
        assertEquals("A great sample app", appEn.summary)
        assertEquals(listOf("Utility", "System"), appEn.categories)
        assertEquals("https://github.com/example/sampleapp", appEn.source)

        // Test German locale conversion
        val convertedDe = indexV2.toIzzyIndexResponse(Locale.GERMANY)
        assertEquals("IzzyOnDroid F-Droid Paketquelle", convertedDe.repo?.name)
        val appDe = convertedDe.apps.first()
        assertEquals("Beispiel App", appDe.name)
        assertEquals("Eine tolle Beispiel-App", appDe.summary)

        // Check packages map and version details
        val pkgVersions = convertedEn.packages["com.example.sampleapp"]
        assertNotNull(pkgVersions)
        assertEquals(1, pkgVersions!!.size)

        val pkg = pkgVersions.first()
        assertEquals("1.2.0", pkg.versionName)
        assertEquals(102L, pkg.versionCode)
        assertEquals("com.example.sampleapp_102.apk", pkg.apkName) // leading slash stripped
        assertEquals("abcdef1234567890", pkg.hash)
        assertEquals("sha256", pkg.hashType)
        assertEquals("11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff", pkg.signer)
        assertEquals(26, pkg.minSdkVersion)
        assertEquals(34, pkg.targetSdkVersion)
        assertEquals(listOf("arm64-v8a"), pkg.nativecode)
        assertEquals(5242880L, pkg.size)
    }

    @Test
    fun `getPreferredText handles primitive strings and fallbacks gracefully`() {
        val primitiveJson = JsonPrimitive("Plain Text Title")
        assertEquals("Plain Text Title", primitiveJson.getPreferredText(Locale.GERMANY))

        val mapJson = gson.toJsonTree(mapOf("fr" to "Bonjour", "es" to "Hola"))
        // No German or English in map -> fallback to first available
        val text = mapJson.getPreferredText(Locale.GERMANY)
        assertTrue(text == "Bonjour" || text == "Hola")
    }
}
