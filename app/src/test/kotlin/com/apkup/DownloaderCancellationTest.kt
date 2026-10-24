package com.apkup

import com.apkup.util.Downloader
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class DownloaderCancellationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeCall(val canceled: AtomicBoolean = AtomicBoolean(false)) : Call {
        var cancelCallCount = 0

        override fun cancel() {
            canceled.set(true)
            cancelCallCount++
        }

        override fun isCanceled(): Boolean = canceled.get()
        override fun isExecuted(): Boolean = false
        override fun clone(): Call = FakeCall(canceled)
        override fun request(): Request = Request.Builder().url("https://example.com/test.apk").build()
        override fun execute(): Response = throw UnsupportedOperationException("Not implemented in fake")
        override fun enqueue(responseCallback: Callback) = throw UnsupportedOperationException("Not implemented in fake")
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    }

    @Test
    fun `Downloader call registration, unregistration, and active check`() {
        val downloader = Downloader(OkHttpClient(), OkHttpClient())
        val fakeCall = FakeCall()
        val tag = "update_1001"

        assertFalse(downloader.isCallActive(tag))

        downloader.registerCall(tag, fakeCall)
        assertTrue(downloader.isCallActive(tag))

        downloader.unregisterCall(tag, fakeCall)
        assertFalse(downloader.isCallActive(tag))
    }

    @Test
    fun `Downloader cancel aborts OkHttp call and closes socket`() {
        val downloader = Downloader(OkHttpClient(), OkHttpClient())
        val fakeCall1 = FakeCall()
        val fakeCall2 = FakeCall()

        val tag = "update_42"
        downloader.registerCall(tag, fakeCall1)
        downloader.registerCall(tag, fakeCall2)

        val cancelled = downloader.cancel(tag)

        assertTrue("cancel must return true when active calls are aborted", cancelled)
        assertTrue("fakeCall1 must be marked canceled", fakeCall1.isCanceled())
        assertTrue("fakeCall2 must be marked canceled", fakeCall2.isCanceled())
        assertEquals(1, fakeCall1.cancelCallCount)
        assertEquals(1, fakeCall2.cancelCallCount)
        assertFalse("Tag must no longer be active after cancel", downloader.isCallActive(tag))
    }

    @Test
    fun `Downloader cancelAll aborts all registered calls across tags`() {
        val downloader = Downloader(OkHttpClient(), OkHttpClient())
        val callA = FakeCall()
        val callB = FakeCall()

        downloader.registerCall("update_1", callA)
        downloader.registerCall("update_2", callB)

        downloader.cancelAll()

        assertTrue("callA must be cancelled", callA.isCanceled())
        assertTrue("callB must be cancelled", callB.isCanceled())
        assertEquals(1, callA.cancelCallCount)
        assertEquals(1, callB.cancelCallCount)
        assertFalse(downloader.isCallActive("update_1"))
        assertFalse(downloader.isCallActive("update_2"))
    }

    @Test
    fun `Downloader Tier 3 cleanup purges orphaned staging directories`() {
        val downloader = Downloader(OkHttpClient(), OkHttpClient())
        val rootDir = tempFolder.newFolder("cache")

        val orphanDownload = File(rootDir, "download_staging_101").apply { mkdirs() }
        File(orphanDownload, "partial.apk").writeBytes(ByteArray(1024))

        val orphanShizuku = File(rootDir, "shizuku_staging_202").apply { mkdirs() }
        File(orphanShizuku, "base.apk").writeBytes(ByteArray(2048))

        val orphanSelfUpdate = File(rootDir, "self_update_staging").apply { mkdirs() }
        File(orphanSelfUpdate, "update.apk").writeBytes(ByteArray(512))

        val keepDir = File(rootDir, "images_cache").apply { mkdirs() }
        File(keepDir, "icon.png").writeBytes(ByteArray(128))

        assertTrue(orphanDownload.exists())
        assertTrue(orphanShizuku.exists())
        assertTrue(orphanSelfUpdate.exists())
        assertTrue(keepDir.exists())

        downloader.cleanupOrphanStagingFiles(rootDir)

        assertFalse("download_staging_101 must be deleted", orphanDownload.exists())
        assertFalse("shizuku_staging_202 must be deleted", orphanShizuku.exists())
        assertFalse("self_update_staging must be deleted", orphanSelfUpdate.exists())
        assertTrue("images_cache must be preserved", keepDir.exists())
    }
}
