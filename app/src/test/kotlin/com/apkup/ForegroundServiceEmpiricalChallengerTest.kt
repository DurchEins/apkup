package com.apkup

import android.content.Context
import android.content.ContextWrapper
import com.apkup.service.DownloadTaskState
import com.apkup.util.DownloadNotificationManager
import com.apkup.util.Downloader
import com.apkup.util.wrapStreamIfGzip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class ForegroundServiceEmpiricalChallengerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeContext : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
    }

    private class TrackedFakeCall(
        private val request: Request,
        val isCanceledFlag: AtomicBoolean = AtomicBoolean(false)
    ) : Call {
        val cancelCallCount = AtomicInteger(0)

        override fun cancel() {
            isCanceledFlag.set(true)
            cancelCallCount.incrementAndGet()
        }

        override fun isCanceled(): Boolean = isCanceledFlag.get()
        override fun isExecuted(): Boolean = false
        override fun clone(): Call = TrackedFakeCall(request, isCanceledFlag)
        override fun request(): Request = request
        override fun execute(): Response = throw UnsupportedOperationException("execute not supported on TrackedFakeCall")
        override fun enqueue(responseCallback: Callback) = throw UnsupportedOperationException("enqueue not supported on TrackedFakeCall")
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    }

    // =========================================================================
    // 1. IN-FLIGHT CANCELLATION & IMMEDIATE SOCKET ABORTION (Focus Area 1 & 2)
    // =========================================================================

    @Test
    fun testImmediateSocketAbortionDuringActiveStreamPurgesPartialFile() = runBlocking {
        val totalBytes = 500_000L
        val bytesEmitted = AtomicInteger(0)
        val downloadAborted = AtomicBoolean(false)
        val cancelTriggerLatch = CountDownLatch(1)

        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val fakeSource = object : Source {
                private var emitted = 0L
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (chain.call().isCanceled()) {
                        downloadAborted.set(true)
                        throw IOException("Socket aborted via Call.cancel()")
                    }
                    if (emitted >= totalBytes) return -1L

                    val toEmit = minOf(byteCount, 4096L, totalBytes - emitted)
                    sink.write(ByteArray(toEmit.toInt()) { 0x41 })
                    emitted += toEmit
                    bytesEmitted.addAndGet(toEmit.toInt())

                    // Trigger cancellation once 20KB has been transferred
                    if (emitted >= 20_000L && cancelTriggerLatch.count > 0) {
                        cancelTriggerLatch.countDown()
                    }
                    Thread.sleep(2)
                    return toEmit
                }

                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {}
            }

            val body = object : ResponseBody() {
                override fun contentType() = "application/vnd.android.package-archive".toMediaType()
                override fun contentLength() = totalBytes
                override fun source(): BufferedSource = fakeSource.buffer()
            }

            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        }

        val testClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val downloader = Downloader(testClient, testClient)

        val targetDir = tempFolder.newFolder("staging_test_1")
        val targetFile = File(targetDir, "base.apk")
        val callTag = "update_test_101"

        var reportedBytes = 0L
        val downloadJob = launch(Dispatchers.IO) {
            downloader.downloadToFile(
                url = "https://example.com/app.apk",
                targetFile = targetFile,
                callTag = callTag,
                onBytesRead = { chunk -> reportedBytes += chunk }
            )
        }

        // Wait until at least 20KB is transferred
        assertTrue("Stream must start transferring bytes", cancelTriggerLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Call tag must be active in Downloader registry", downloader.isCallActive(callTag))

        // Adversarial cancel call
        val didCancel = downloader.cancel(callTag)
        assertTrue("cancel(tag) must return true for active call", didCancel)

        // Coroutine must abort with CancellationException
        try {
            downloadJob.join()
        } catch (ignored: Exception) {}

        // Assertions:
        // 1. Partial file must NOT exist (Tier 1 cleanup immediately purged it)
        assertFalse(
            "Incomplete download file must be deleted upon cancellation (Tier 1 cleanup)",
            targetFile.exists()
        )
        // 2. Call must be unregistered
        assertFalse("Tag must be unregistered after cancellation", downloader.isCallActive(callTag))
        // 3. Bytes were actually transferred before abort
        assertTrue("Bytes must have been read before cancellation", bytesEmitted.get() >= 20_000)
        // 4. Target file is clean and can be written to without file-lock leaks
        assertTrue("Target file must be freely creatable and deletable without lock leak", targetFile.createNewFile())
        assertTrue(targetFile.delete())
    }

    @Test
    fun testCancellationBeforeResponseReturnsThrowsCancellationExceptionAndCleansUp() = runBlocking {
        val interceptLatch = CountDownLatch(1)
        val interceptor = Interceptor { chain ->
            interceptLatch.countDown()
            // Simulate slow server response header delay
            while (!chain.call().isCanceled()) {
                Thread.sleep(10)
            }
            throw IOException("Canceled")
        }

        val testClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val downloader = Downloader(testClient, testClient)

        val targetDir = tempFolder.newFolder("staging_test_2")
        val targetFile = File(targetDir, "base.apk")
        val callTag = "update_test_102"

        val downloadJob = async(Dispatchers.IO) {
            downloader.downloadToFile(
                url = "https://example.com/slow_headers.apk",
                targetFile = targetFile,
                callTag = callTag
            )
        }

        assertTrue("Must enter interceptor", interceptLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Call tag must be registered before execute finishes", downloader.isCallActive(callTag))

        downloader.cancel(callTag)

        try {
            downloadJob.await()
            fail("Expected CancellationException when download is aborted before response headers")
        } catch (e: CancellationException) {
            // Expected
        }

        assertFalse("Target file must not exist", targetFile.exists())
        assertFalse("Tag must no longer be active", downloader.isCallActive(callTag))
    }

    @Test
    fun testCoroutineScopeCancellationAbortsOkHttpStreamAndPurgesFile() = runBlocking {
        val totalBytes = 300_000L
        val bytesReadLatch = CountDownLatch(1)

        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val fakeSource = object : Source {
                private var emitted = 0L
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (emitted >= totalBytes) return -1L
                    val toEmit = minOf(byteCount, 4096L, totalBytes - emitted)
                    sink.write(ByteArray(toEmit.toInt()) { 0x42 })
                    emitted += toEmit
                    if (emitted >= 10_000L && bytesReadLatch.count > 0) {
                        bytesReadLatch.countDown()
                    }
                    Thread.sleep(5)
                    return toEmit
                }
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {}
            }

            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(object : ResponseBody() {
                    override fun contentType() = "application/vnd.android.package-archive".toMediaType()
                    override fun contentLength() = totalBytes
                    override fun source(): BufferedSource = fakeSource.buffer()
                })
                .build()
        }

        val testClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val downloader = Downloader(testClient, testClient)

        val targetDir = tempFolder.newFolder("staging_test_3")
        val targetFile = File(targetDir, "base.apk")
        val callTag = "update_test_103"

        val job = launch(Dispatchers.IO) {
            downloader.downloadToFile(
                url = "https://example.com/app.apk",
                targetFile = targetFile,
                callTag = callTag
            )
        }

        assertTrue(bytesReadLatch.await(5, TimeUnit.SECONDS))
        // Cancel the coroutine directly
        job.cancel()
        job.join()

        assertFalse("Target file must be deleted when coroutine is cancelled", targetFile.exists())
        assertFalse("Call tag must be unregistered", downloader.isCallActive(callTag))
    }

    @Test
    fun testRapidCancelAndRestartConcurrencyDoesNotLeakOrDeadlock() = runBlocking {
        val downloader = Downloader(OkHttpClient(), OkHttpClient())
        val iterations = 50

        // Concurrently register, check, and cancel calls across 10 coroutines
        val jobs = (1..10).map { threadId ->
            launch(Dispatchers.Default) {
                for (i in 1..iterations) {
                    val tag = "update_${threadId}_$i"
                    val call = TrackedFakeCall(Request.Builder().url("https://example.com/$tag").build())
                    downloader.registerCall(tag, call)
                    assertTrue(downloader.isCallActive(tag))

                    if (i % 2 == 0) {
                        downloader.cancel(tag)
                        assertFalse(downloader.isCallActive(tag))
                        assertTrue(call.isCanceled())
                    } else {
                        downloader.unregisterCall(tag, call)
                        assertFalse(downloader.isCallActive(tag))
                    }
                }
            }
        }
        jobs.joinAll()
    }

    // =========================================================================
    // 2. NETWORK ERROR RESILIENCE & FILE CLEANUP HYGIENE (Tier 1 & 3)
    // =========================================================================

    @Test
    fun testMidStreamSocketResetReturnsFalseAndPurgesPartialFile() = runBlocking {
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val fakeSource = object : Source {
                private var emitted = 0
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (emitted >= 15_000) {
                        throw IOException("Connection reset by peer")
                    }
                    val toEmit = 5000L
                    sink.write(ByteArray(toEmit.toInt()) { 0x33 })
                    emitted += toEmit.toInt()
                    return toEmit
                }
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {}
            }

            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(object : ResponseBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = 50_000L
                    override fun source(): BufferedSource = fakeSource.buffer()
                })
                .build()
        }

        val testClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val downloader = Downloader(testClient, testClient)

        val targetDir = tempFolder.newFolder("staging_reset")
        val targetFile = File(targetDir, "base.apk")

        val result = downloader.downloadToFile(
            url = "https://example.com/flaky.apk",
            targetFile = targetFile,
            callTag = "update_reset_1"
        )

        assertFalse("Network drop mid-stream must return false", result)
        assertFalse("Partial file must be deleted upon connection reset", targetFile.exists())
        assertFalse(downloader.isCallActive("update_reset_1"))
    }

    @Test
    fun testZeroByteResponseReturnsFalseAndDeletesFile() = runBlocking {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }

        val testClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val downloader = Downloader(testClient, testClient)

        val targetDir = tempFolder.newFolder("staging_zero")
        val targetFile = File(targetDir, "base.apk")

        val result = downloader.downloadToFile(
            url = "https://example.com/empty.apk",
            targetFile = targetFile,
            callTag = "update_zero"
        )

        assertFalse("0-byte download must return false", result)
        assertFalse("Zero-byte file must be deleted by Tier 1 cleanup", targetFile.exists())
    }

    @Test
    fun testHttpErrorCodesDoNotCreatePartialFiles() = runBlocking {
        val errorCodes = listOf(400, 403, 404, 429, 500, 503)

        for (code in errorCodes) {
            val interceptor = Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("HTTP Error $code")
                    .body("Error details".toResponseBody("text/plain".toMediaType()))
                    .build()
            }

            val testClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
            val downloader = Downloader(testClient, testClient)

            val targetDir = tempFolder.newFolder("staging_err_$code")
            val targetFile = File(targetDir, "base.apk")

            val result = downloader.downloadToFile(
                url = "https://example.com/error_$code.apk",
                targetFile = targetFile,
                callTag = "update_err_$code"
            )

            assertFalse("HTTP $code must result in failure", result)
            assertFalse("Target file must not be created on HTTP $code", targetFile.exists())
            assertFalse(downloader.isCallActive("update_err_$code"))
        }
    }

    @Test
    fun testTier3CleanupWithDeeplyNestedOrphansAndNonDirectories() {
        val cacheDir = tempFolder.newFolder("cache_root")
        val downloader = Downloader(OkHttpClient(), OkHttpClient())

        // 1. Deeply nested download_staging directory
        val downloadDir = File(cacheDir, "download_staging_1001").apply { mkdirs() }
        val nestedSubdir = File(downloadDir, "nested/splits").apply { mkdirs() }
        File(nestedSubdir, "split_config.arm64.apk").writeBytes(ByteArray(2048) { 0x11 })
        File(downloadDir, "base.apk").writeBytes(ByteArray(4096) { 0x22 })

        // 2. Shizuku staging directory
        val shizukuDir = File(cacheDir, "shizuku_staging_2002").apply { mkdirs() }
        File(shizukuDir, "base.apk").writeBytes(ByteArray(1024) { 0x33 })

        // 3. Self-update staging directory
        val selfUpdateDir = File(cacheDir, "self_update_staging").apply { mkdirs() }
        File(selfUpdateDir, "update.apk").writeBytes(ByteArray(512) { 0x44 })

        // 4. Regular file matching prefix but NOT a directory (should be preserved)
        val regularFileWithPrefix = File(cacheDir, "download_staging_log.txt").apply {
            writeText("keep this log")
        }

        // 5. Unrelated directory (should be preserved)
        val imageCacheDir = File(cacheDir, "image_cache").apply { mkdirs() }
        File(imageCacheDir, "banner.webp").writeBytes(ByteArray(500))

        assertTrue(downloadDir.exists())
        assertTrue(shizukuDir.exists())
        assertTrue(selfUpdateDir.exists())
        assertTrue(regularFileWithPrefix.exists())
        assertTrue(imageCacheDir.exists())

        // Execute Tier 3 cleanup
        downloader.cleanupOrphanStagingFiles(cacheDir)

        assertFalse("download_staging_1001 must be deleted recursively", downloadDir.exists())
        assertFalse("shizuku_staging_2002 must be deleted recursively", shizukuDir.exists())
        assertFalse("self_update_staging must be deleted recursively", selfUpdateDir.exists())

        assertTrue("Regular file with prefix must be preserved", regularFileWithPrefix.exists())
        assertTrue("Non-staging directory must be preserved", imageCacheDir.exists())

        // Calling on non-existent directory must not throw
        val nonExistent = File(cacheDir, "does_not_exist")
        downloader.cleanupOrphanStagingFiles(nonExistent)
    }

    // =========================================================================
    // 3. GZIP STREAM DETECTION & DECOMPRESSION TESTS
    // =========================================================================

    @Test
    fun testWrapStreamIfGzipDecompressesValidGzipDataAccurately() {
        val originalText = "Test gzip payload decompression for ApkUp Downloader"
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzos ->
            gzos.write(originalText.toByteArray(Charsets.UTF_8))
        }
        val gzippedBytes = baos.toByteArray()

        val wrapped = wrapStreamIfGzip(ByteArrayInputStream(gzippedBytes))
        val decompressedText = wrapped.bufferedReader(Charsets.UTF_8).use { it.readText() }

        assertEquals(originalText, decompressedText)
    }

    @Test
    fun testWrapStreamIfGzipPreservesNonGzipDataUnchanged() {
        // Standard PK ZIP magic (0x50, 0x4B, 0x03, 0x04)
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        val wrapped = wrapStreamIfGzip(ByteArrayInputStream(zipHeader))
        val readBytes = wrapped.readBytes()

        assertTrue(zipHeader.contentEquals(readBytes))
    }

    @Test
    fun testWrapStreamIfGzipSub2ByteStreamsHandleSafelyWithoutHanging() {
        // Empty stream
        val emptyStream = wrapStreamIfGzip(ByteArrayInputStream(ByteArray(0)))
        assertEquals(-1, emptyStream.read())

        // 1-byte stream with byte 0x1F (first byte of GZIP magic, but incomplete)
        val oneByteStream = wrapStreamIfGzip(ByteArrayInputStream(byteArrayOf(0x1F)))
        assertEquals(0x1F, oneByteStream.read())
        assertEquals(-1, oneByteStream.read())
    }

    // =========================================================================
    // 4. DOWNLOAD NOTIFICATION MANAGER THROTTLING TESTS (Focus Area 3)
    // =========================================================================

    @Test
    fun testThrottlingReducesRapidBurstsWhilePermittingMilestones() {
        val manager = DownloadNotificationManager(FakeContext())

        // 0% start milestone must never be throttled
        assertFalse("0% start milestone must not be throttled", manager.shouldThrottle(0))

        // First non-zero percentage (10%) sets baseline
        assertFalse("Initial 10% update must pass through", manager.shouldThrottle(10))

        // Rapid updates within <2% delta must be throttled
        assertTrue("Subsequent 10% (delta 0) must be throttled", manager.shouldThrottle(10))
        assertTrue("11% (delta 1) must be throttled", manager.shouldThrottle(11))

        // 2% delta (10% -> 12%) must pass through
        assertFalse("12% (delta 2 from 10%) must pass through", manager.shouldThrottle(12))

        // Rapid update within <2% delta (13%) must be throttled
        assertTrue("13% (delta 1 from 12%) must be throttled", manager.shouldThrottle(13))

        // 100% completion milestone must never be throttled
        assertFalse("100% milestone must never be throttled", manager.shouldThrottle(100))

        // 0% milestone must never be throttled
        assertFalse("0% milestone must never be throttled", manager.shouldThrottle(0))

        // Force = true must never be throttled
        assertFalse("force=true must never be throttled", manager.shouldThrottle(50, force = true))
        assertFalse("force=true immediately again must never be throttled", manager.shouldThrottle(50, force = true))
    }

    @Test
    fun testThrottlingElapsedMillisecondsWindow() {
        val manager = DownloadNotificationManager(FakeContext())

        // First call records time
        assertFalse(manager.shouldThrottle(10))

        // Immediate call with same percent -> throttled
        assertTrue(manager.shouldThrottle(10))

        // Sleep past 250ms throttle window
        Thread.sleep(260)

        // Same percent after 250ms must now pass
        assertFalse("Same percent after 250ms elapsed must pass", manager.shouldThrottle(10))
    }

    @Test
    fun testThrottlingUnderHighFrequencyMultiThreadedContention() = runBlocking {
        val manager = DownloadNotificationManager(FakeContext())
        val unthrottledCount = AtomicInteger(0)
        val throttledCount = AtomicInteger(0)

        // 10 concurrent threads issuing rapid progress updates for the active task (e.g. 45%)
        val jobs = (1..10).map { threadId ->
            launch(Dispatchers.Default) {
                for (chunk in 1..100) {
                    val throttled = manager.shouldThrottle(45)
                    if (throttled) {
                        throttledCount.incrementAndGet()
                    } else {
                        unthrottledCount.incrementAndGet()
                    }
                }
            }
        }
        jobs.joinAll()

        assertTrue("At least the first update must pass", unthrottledCount.get() >= 1)
        assertTrue("Under rapid multi-threaded burst, overwhelming majority must be throttled", throttledCount.get() >= 900)
    }

    // =========================================================================
    // 5. MULTI-DOWNLOAD QUEUE STATE & TAG ISOLATION (Focus Area 4)
    // =========================================================================

    @Test
    fun testDownloaderMultiCallTagIsolationUnderCancellation() {
        val downloader = Downloader(OkHttpClient(), OkHttpClient())

        val callsTag1 = (1..3).map { TrackedFakeCall(Request.Builder().url("https://example.com/1/$it").build()) }
        val callsTag2 = (1..3).map { TrackedFakeCall(Request.Builder().url("https://example.com/2/$it").build()) }

        callsTag1.forEach { downloader.registerCall("update_1", it) }
        callsTag2.forEach { downloader.registerCall("update_2", it) }

        assertTrue(downloader.isCallActive("update_1"))
        assertTrue(downloader.isCallActive("update_2"))

        // Cancel only tag 1
        val cancelled = downloader.cancel("update_1")
        assertTrue(cancelled)

        // Tag 1 calls must be cancelled
        assertFalse(downloader.isCallActive("update_1"))
        callsTag1.forEach { assertTrue("Call for tag 1 must be cancelled", it.isCanceled()) }

        // Tag 2 calls must remain completely active and untouched!
        assertTrue(downloader.isCallActive("update_2"))
        callsTag2.forEach { assertFalse("Call for tag 2 must NOT be cancelled", it.isCanceled()) }

        // Cancel all
        downloader.cancelAll()
        assertFalse(downloader.isCallActive("update_2"))
        callsTag2.forEach { assertTrue("Call for tag 2 must be cancelled after cancelAll", it.isCanceled()) }
    }

    @Test
    fun testDownloadTaskStateConcurrentMutations() = runBlocking {
        val task = DownloadTaskState(
            id = 505,
            packageName = "com.test.concurrent",
            appName = "Concurrent Test",
            isInstalling = false,
            isQueued = true
        )

        // Mutate progress concurrently from multiple coroutines
        val jobs = (1..20).map { i ->
            launch(Dispatchers.Default) {
                task.progress = (i * 10_000).toLong()
                task.total = 200_000L
                if (i == 10) task.isQueued = false
                if (i == 20) task.isInstalling = true
            }
        }
        jobs.joinAll()

        assertFalse("isQueued must be false after transitions", task.isQueued)
        assertTrue("isInstalling must be true after completion", task.isInstalling)
        assertEquals(200_000L, task.total)
        assertTrue("Progress must be greater than 0", task.progress > 0L)
    }
}
