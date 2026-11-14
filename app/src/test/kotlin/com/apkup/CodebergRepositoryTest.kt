package com.apkup

import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.repomapping.RepoUrlParser
import com.apkup.data.ui.AppInstalled
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.repository.CodebergRepository
import com.apkup.repository.RepoMappingManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodebergRepositoryTest {

    @Test
    fun repoUrlParserRecognizesCodebergAndSelfHostedForgejo() {
        val cb = RepoUrlParser.parseRepoUrl("https://codeberg.org/freeyourgadget/Gadgetbridge/releases/latest")
        assertNotNull(cb)
        assertTrue(cb!!.isCodeberg)
        assertEquals("freeyourgadget/Gadgetbridge", cb.repo)
        assertEquals("codeberg.org", cb.resolvedHost)

        val forgejo = RepoUrlParser.parseRepoUrl("https://forgejo.example.org/team/my-app", hintSource = "Codeberg")
        assertNotNull(forgejo)
        assertTrue(forgejo!!.isCodeberg)
        assertEquals("team/my-app", forgejo.repo)
        assertEquals("forgejo.example.org", forgejo.resolvedHost)

        val gitlab = RepoUrlParser.parseRepoUrl("https://gitlab.com/videolan/vlc-android/-/releases")
        assertNotNull(gitlab)
        assertTrue(gitlab!!.isGitLab)
        assertEquals("videolan/vlc-android", gitlab.repo)
    }

    @Test
    fun fetchLatestReleaseParsesForgejoV1JsonAndSendsAuthTokenForCodeberg() = runBlocking {
        var capturedUrl = ""
        var capturedAuth: String? = null
        val jsonBody = """
            [
              {
                "tag_name": "v0.84.0",
                "name": "0.84.0",
                "body": "Bugfixes and new devices",
                "published_at": "2026-11-10T10:00:00Z",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "Gadgetbridge-0.84.0.apk",
                    "size": 12345678,
                    "browser_download_url": "https://codeberg.org/attachments/abc",
                    "content_type": "application/vnd.android.package-archive"
                  }
                ]
              }
            ]
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedUrl = req.url.toString()
                capturedAuth = req.header("Authorization")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("ETag", "\"cb-1\"")
                    .body(jsonBody.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            codebergToken.put("cb_secret_token")
        }
        val repo = CodebergRepository(
            okHttpClient = client,
            gson = Gson(),
            prefs = prefs
        )

        val release = repo.fetchLatestRelease(
            RepoBinding(type = "codeberg", repo = "Freeyourgadget/Gadgetbridge", host = "codeberg.org")
        )
        assertNotNull(release)
        assertEquals("v0.84.0", release!!.tag_name)
        assertEquals("https://codeberg.org/api/v1/repos/Freeyourgadget/Gadgetbridge/releases?limit=5", capturedUrl)
        assertEquals("token cb_secret_token", capturedAuth)

        val apk = ReleaseAssetFilter.findBestApk(release.assets, { it.name })
        assertEquals("Gadgetbridge-0.84.0.apk", apk?.name)

        // Third-party Forgejo instance must NOT receive the Codeberg.org personal access token
        var forgejoAuth: String? = null
        val customClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                forgejoAuth = req.header("Authorization")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        val customRepo = CodebergRepository(okHttpClient = customClient, gson = Gson(), prefs = prefs)
        val missing = customRepo.fetchLatestRelease(
            RepoBinding(type = "codeberg", repo = "org/app", host = "git.disroot.org")
        )
        assertNull(missing)
        assertNull(forgejoAuth)
    }

    @Test
    fun http401And403AreCountedAsFailedRequestsInsteadOfSuccess() = runBlocking {
        for (statusCode in listOf(401, 403)) {
            val prefs = Prefs(InMemoryPrefsBackend())
            val manager = RepoMappingManager(context = null, prefs = prefs, gson = Gson())
            manager.setCustomBinding(
                "org.example.cb",
                RepoBinding(type = "codeberg", repo = "org/app", host = "codeberg.org")
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message("Auth/RateLimit")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build()
            val repo = CodebergRepository(okHttpClient = client, gson = Gson(), prefs = prefs, repoMappingManager = manager)
            val result = repo.updates(
                listOf(AppInstalled(name = "CB App", packageName = "org.example.cb", version = "1.0.0", versionCode = 10L))
            ).first()
            assertEquals("HTTP $statusCode must increment failedRequests", 1, result.failedRequests)
            assertEquals("HTTP $statusCode must not count as successfulRequests", 0, result.successfulRequests)
            assertNotNull(result.failureSummary)
        }
    }

    @Test
    fun downloadUrlValidationRejectsUserInfoAndNon443Ports() = runBlocking {
        val prefs = Prefs(InMemoryPrefsBackend())
        val manager = RepoMappingManager(context = null, prefs = prefs, gson = Gson())
        manager.setCustomBinding(
            "org.example.userinfo",
            RepoBinding(type = "codeberg", repo = "org/app1", host = "codeberg.org")
        )
        manager.setCustomBinding(
            "org.example.badport",
            RepoBinding(type = "codeberg", repo = "org/app2", host = "codeberg.org")
        )
        manager.setCustomBinding(
            "org.example.port443",
            RepoBinding(type = "codeberg", repo = "org/app3", host = "codeberg.org")
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val downloadUrl = when {
                    url.contains("app1") -> "https://user:pass@codeberg.org/attachments/bad1.apk"
                    url.contains("app2") -> "https://codeberg.org:8443/attachments/bad2.apk"
                    else -> "https://codeberg.org:443/attachments/good.apk"
                }
                val body = """
                    [
                      {
                        "tag_name": "v2.0.0",
                        "draft": false,
                        "prerelease": false,
                        "assets": [
                          {
                            "name": "app-2.0.0.apk",
                            "size": 1024,
                            "browser_download_url": "$downloadUrl"
                          }
                        ]
                      }
                    ]
                """.trimIndent()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val repo = CodebergRepository(okHttpClient = client, gson = Gson(), prefs = prefs, repoMappingManager = manager)
        val result = repo.updates(
            listOf(
                AppInstalled(name = "App1", packageName = "org.example.userinfo", version = "1.0.0", versionCode = 10L),
                AppInstalled(name = "App2", packageName = "org.example.badport", version = "1.0.0", versionCode = 10L),
                AppInstalled(name = "App3", packageName = "org.example.port443", version = "1.0.0", versionCode = 10L)
            )
        ).first()

        assertEquals(1, result.updates.size)
        assertEquals("org.example.port443", result.updates.single().packageName)
    }
}
