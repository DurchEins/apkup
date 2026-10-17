package com.apkup

import com.apkup.data.apkmirror.AppExistsRequest
import com.apkup.data.apkmirror.AppExistsResponse
import com.apkup.service.ApkMirrorService
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.POST

class ApkMirrorContractTest {

    @Test
    fun requestSerializesPackageIdsAndStableReleaseExclusions() {
        val json = Gson().toJson(AppExistsRequest(listOf("example.one", "example.two")))

        assertEquals(
            """{"pnames":["example.one","example.two"],"exclude":["alpha","beta"]}""",
            json
        )
    }

    @Test
    fun serviceUsesExpectedPostEndpoint() {
        val method = ApkMirrorService::class.java.declaredMethods.single { it.name == "appExists" }

        val post = requireNotNull(method.getAnnotation(POST::class.java))
        assertEquals("/wp-json/apkm/v1/app_exists/", post.value)
    }

    @Test
    fun responseModelsDeserializeValidAndNotFoundPackages() {
        val response = Gson().fromJson(
            """{
                "status": 200,
                "data": [
                    {
                        "pname": "example.updated",
                        "exists": true,
                        "release": {"version": "2.0"},
                        "apks": [{
                            "version_code": 20,
                            "link": "/apk/example/",
                            "arches": ["arm64-v8a"],
                            "minapi": "23",
                            "signatures-sha1": ["matching-signature"]
                        }]
                    },
                    {"pname": "example.current", "exists": false, "apks": []}
                ]
            }""",
            AppExistsResponse::class.java
        )

        val update = response.data.single { it.pname == "example.updated" }
        assertTrue(update.exists == true)
        assertEquals("2.0", update.release.version)
        assertEquals(20L, update.apks.single().versionCode)
        assertEquals(listOf("arm64-v8a"), update.apks.single().arches)

        val noMatch = response.data.single { it.pname == "example.current" }
        assertFalse(noMatch.exists == true)
        assertTrue(noMatch.apks.isEmpty())
    }
}
