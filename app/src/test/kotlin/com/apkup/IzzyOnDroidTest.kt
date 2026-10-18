package com.apkup

import com.apkup.data.izzy.IzzyAppInfo
import com.apkup.data.izzy.IzzyIndexResponse
import com.apkup.data.izzy.IzzyPackageInfo
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.Link
import com.apkup.prefs.Prefs
import com.apkup.repository.IzzyOnDroidRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IzzyOnDroidTest {

    private val gson = Gson()
    private val repo = IzzyOnDroidRepository(
        okHttpClient = OkHttpClient(),
        gson = gson
    )

    private val validSha256 = "24dfc0b490f0559e8f495ca9b50b552fa82a20dc3e3a4e98f01f8e123456789a"
    private val differentSha256 = "1111111122222222333333334444444455555555666666667777777788888888"

    @Test
    fun signatureMatchingAcceptsIdenticalHashesRegardlessOfCaseAndColons() {
        val withColons = "24:DF:C0:B4:90:F0:55:9E:8F:49:5C:A9:B5:0B:55:2F:A8:2A:20:DC:3E:3A:4E:98:F0:1F:8E:12:34:56:78:9A"
        assertTrue(repo.isSignatureMatching(withColons, validSha256))
        assertTrue(repo.isSignatureMatching(validSha256, withColons))
        assertTrue(repo.isSignatureMatching(validSha256.uppercase(), validSha256.lowercase()))
    }

    @Test
    fun signatureMatchingRejectsMismatchesAndEmptyHashes() {
        assertFalse(repo.isSignatureMatching(validSha256, differentSha256))
        assertFalse(repo.isSignatureMatching("", validSha256))
        assertFalse(repo.isSignatureMatching(null, validSha256))
        assertFalse(repo.isSignatureMatching(validSha256, null))
        assertFalse(repo.isSignatureMatching("   ", validSha256))

        // Default empty SHA-256 (hash of empty byte array) must be strictly rejected
        assertFalse(repo.isSignatureMatching("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", validSha256))
    }

    @Test
    fun abiCompatibilityAcceptsUniversalAndMatchingAbis() {
        val supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a")

        // Universal (null or empty nativecode)
        assertTrue(repo.isAbiCompatible(null, supportedAbis))
        assertTrue(repo.isAbiCompatible(emptyList(), supportedAbis))

        // Matching ABI
        assertTrue(repo.isAbiCompatible(listOf("arm64-v8a"), supportedAbis))
        assertTrue(repo.isAbiCompatible(listOf("x86", "arm64-v8a"), supportedAbis))

        // Incompatible ABI
        assertFalse(repo.isAbiCompatible(listOf("x86"), supportedAbis))
        assertFalse(repo.isAbiCompatible(listOf("x86_64"), supportedAbis))
    }

    @Test
    fun sdkCompatibilityValidatesMinSdkThreshold() {
        val currentSdk = 34
        assertTrue(repo.isSdkCompatible(0, currentSdk))
        assertTrue(repo.isSdkCompatible(21, currentSdk))
        assertTrue(repo.isSdkCompatible(34, currentSdk))
        assertFalse(repo.isSdkCompatible(35, currentSdk))
    }

    @Test
    fun findBestPackageSelectsHighestCompatibleVersionCodeWithValidSignature() {
        val installedApp = AppInstalled(
            name = "Test App",
            packageName = "org.example.app",
            version = "1.0.0",
            versionCode = 100L,
            signatureSha256 = validSha256
        )

        val candidateOlder = IzzyPackageInfo(
            versionName = "0.9.0",
            versionCode = 90L,
            apkName = "app_90.apk",
            signer = validSha256
        )
        val candidateDifferentSignature = IzzyPackageInfo(
            versionName = "1.2.0",
            versionCode = 120L,
            apkName = "app_120.apk",
            signer = differentSha256
        )
        val candidateIncompatibleAbi = IzzyPackageInfo(
            versionName = "1.3.0",
            versionCode = 130L,
            apkName = "app_130.apk",
            signer = validSha256,
            nativecode = listOf("x86")
        )
        val candidateValid1 = IzzyPackageInfo(
            versionName = "1.1.0",
            versionCode = 110L,
            apkName = "app_110.apk",
            signer = validSha256,
            nativecode = listOf("arm64-v8a")
        )
        val candidateValid2Highest = IzzyPackageInfo(
            versionName = "1.1.5",
            versionCode = 115L,
            apkName = "app_115.apk",
            signer = validSha256,
            nativecode = listOf("arm64-v8a")
        )

        val packages = listOf(
            candidateOlder,
            candidateDifferentSignature,
            candidateIncompatibleAbi,
            candidateValid1,
            candidateValid2Highest
        )

        val best = repo.findBestPackage(
            app = installedApp,
            packages = packages,
            supportedAbis = arrayOf("arm64-v8a"),
            currentSdk = 34
        )

        assertNotNull(best)
        assertEquals(115L, best!!.versionCode)
        assertEquals("1.1.5", best.versionName)
        assertEquals("app_115.apk", best.apkName)
    }

    @Test
    fun parseIndexJsonStructureCorrectlyMapsAppsAndPackages() {
        val sampleJson = """
            {
              "repo": {
                "name": "IzzyOnDroid",
                "timestamp": 1700000000
              },
              "apps": [
                {
                  "packageName": "com.test.foobar",
                  "name": "Foo Bar App",
                  "summary": "Great open-source app"
                }
              ],
              "packages": {
                "com.test.foobar": [
                  {
                    "versionName": "2.0.0",
                    "versionCode": 200,
                    "size": 1234567,
                    "apkName": "foobar_200.apk",
                    "signer": "$validSha256",
                    "minSdkVersion": 26,
                    "nativecode": ["arm64-v8a"]
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = gson.fromJson(sampleJson, IzzyIndexResponse::class.java)
        assertEquals("IzzyOnDroid", parsed.repo?.name)
        assertEquals(1, parsed.apps.size)
        assertEquals("com.test.foobar", parsed.apps[0].packageName)
        assertEquals("Foo Bar App", parsed.apps[0].name)

        val pkgList = parsed.packages["com.test.foobar"]
        assertNotNull(pkgList)
        assertEquals(1, pkgList!!.size)
        assertEquals(200L, pkgList[0].versionCode)
        assertEquals("foobar_200.apk", pkgList[0].apkName)
    }

    @Test
    fun parseZipStreamAndSaveCreatesTargetFileAndCleansTempFile() {
        val tempTestDir = java.nio.file.Files.createTempDirectory("izzy_test").toFile()
        try {
            val testRepo = IzzyOnDroidRepository(
                okHttpClient = OkHttpClient(),
                gson = gson,
                cacheDirectory = tempTestDir
            )

            val sampleJson = """
                {
                  "repo": { "name": "IzzyOnDroid" },
                  "apps": [ { "packageName": "com.test.atomic", "name": "Atomic Test" } ],
                  "packages": {}
                }
            """.trimIndent()

            val byteOut = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(byteOut).use { zipOut ->
                zipOut.putNextEntry(java.util.zip.ZipEntry("index-v1.json"))
                zipOut.write(sampleJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            val parsed = testRepo.parseZipStreamAndSave(java.io.ByteArrayInputStream(byteOut.toByteArray()))
            assertEquals("IzzyOnDroid", parsed.repo?.name)
            assertEquals("com.test.atomic", parsed.apps[0].packageName)

            val targetFile = java.io.File(tempTestDir, "index-v1.json")
            val tempFile = java.io.File(tempTestDir, "index-v1.json.tmp")
            assertTrue("Target cache file must exist", targetFile.exists())
            assertTrue("Target cache file must not be empty", targetFile.length() > 0)
            assertFalse("Temp file must be cleaned up", tempFile.exists())
        } finally {
            tempTestDir.deleteRecursively()
        }
    }
}

