package com.apkup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apkup.data.apkmirror.AppExistsRequest
import com.apkup.data.ui.AppInstalled
import com.apkup.repository.ApkMirrorRepository
import com.apkup.service.ApkMirrorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Opt-in live test. It sends only two fixed package IDs and never installs an APK.
 * Run with -e includeApkMirrorLiveTest true in the instrumentation arguments.
 */
@RunWith(AndroidJUnit4::class)
class ApkMirrorLiveInstrumentedTest : KoinComponent {

    private val service: ApkMirrorService by inject()
    private val repository: ApkMirrorRepository by inject()

    @Test
    fun appRetrofitClientQueriesKnownAndUnknownPackages() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        assumeTrue(
            "Opt-in required: includeApkMirrorLiveTest=true",
            InstrumentationRegistry.getArguments().getString("includeApkMirrorLiveTest") == "true"
        )

        assertEquals("com.apkup", targetContext.packageName)

        val response = service.appExists(
            AppExistsRequest(
                pnames = listOf("com.spotify.music", "org.example.apkupdiagnostic.no.such.app")
            )
        )
        assertEquals(2, response.data.size)
        assertTrue(response.data.single { it.pname == "com.spotify.music" }.exists == true)
        assertFalse(response.data.single { it.pname == "org.example.apkupdiagnostic.no.such.app" }.exists == true)

        val emptyResult = repository.updates(
            listOf(
                AppInstalled(
                    name = "Diagnostic placeholder",
                    packageName = "org.example.apkupdiagnostic.no.such.app",
                    version = "",
                    versionCode = Long.MAX_VALUE
                )
            )
        ).first()
        assertEquals(1, emptyResult.successfulRequests)
        assertEquals(0, emptyResult.failedRequests)
        assertTrue(emptyResult.updates.isEmpty())
    }
}
