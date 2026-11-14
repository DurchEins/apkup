package com.apkup

import com.apkup.data.ui.InstallFailureReason
import com.apkup.util.ApkIntegrityException
import com.apkup.util.InstallPart
import com.apkup.util.ProcessRootShellExecutor
import com.apkup.util.RootCommandOutput
import com.apkup.util.RootInstaller
import com.apkup.util.RootShellExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RootInstallerTest {

    private class FakeRootShellExecutor(
        private val runHandler: (String) -> RootCommandOutput,
        private val pipeHandler: (String, ByteArray) -> RootCommandOutput = { _, _ -> RootCommandOutput(0, "Success") }
    ) : RootShellExecutor {
        val commands = mutableListOf<String>()

        override fun runCommand(command: String): RootCommandOutput {
            commands += command
            return runHandler(command)
        }

        override fun pipeToCommand(command: String, input: InputStream): RootCommandOutput {
            commands += command
            return pipeHandler(command, input.readBytes())
        }
    }

    @Test
    fun isRootAvailableDetectsUidZero() {
        val rooted = RootInstaller(FakeRootShellExecutor({ RootCommandOutput(0, "uid=0(root) gid=0(root)") }))
        val unrooted = RootInstaller(FakeRootShellExecutor({ RootCommandOutput(1, "Permission denied") }))

        assertTrue(rooted.isRootAvailable())
        assertFalse(unrooted.isRootAvailable())
    }

    @Test
    fun executeRootInstallRunsCreateWriteCommitInOrder() {
        val executor = FakeRootShellExecutor(
            runHandler = { cmd ->
                when {
                    cmd.startsWith("pm install-create") -> RootCommandOutput(0, "Success: created install session [4242]")
                    cmd == "pm install-commit 4242" -> RootCommandOutput(0, "Success")
                    else -> RootCommandOutput(1, "Unexpected: $cmd")
                }
            },
            pipeHandler = { cmd, bytes ->
                assertTrue(cmd.startsWith("pm install-write"))
                assertTrue(bytes.isNotEmpty())
                RootCommandOutput(0, "Success: streamed ${bytes.size} bytes")
            }
        )
        val installer = RootInstaller(executor)
        val parts = listOf(
            InstallPart("base.apk", 4L) { ByteArrayInputStream("BASE".toByteArray()) },
            InstallPart("split_1.apk", 5L) { ByteArrayInputStream("SPLIT".toByteArray()) }
        )

        val result = installer.executeRootInstall("com.example.app", parts, "com.apkup.debug")

        assertTrue(result.success)
        assertEquals(4, executor.commands.size)
        assertTrue(executor.commands[0].contains("pm install-create -r -i com.apkup.debug -S 9"))
        assertTrue(executor.commands[1] == "pm install-write -S 4 4242 base.apk -")
        assertTrue(executor.commands[2] == "pm install-write -S 5 4242 split_1.apk -")
        assertTrue(executor.commands[3] == "pm install-commit 4242")
    }

    @Test
    fun executeRootInstallDetectsSignatureMismatchAndAbandonsSession() {
        val executor = FakeRootShellExecutor(
            runHandler = { cmd ->
                when {
                    cmd.startsWith("pm install-create") -> RootCommandOutput(0, "Success: created install session [77]")
                    cmd == "pm install-commit 77" -> RootCommandOutput(1, "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]")
                    cmd == "pm install-abandon 77" -> RootCommandOutput(0, "Success")
                    else -> RootCommandOutput(1, "Unexpected")
                }
            }
        )
        val installer = RootInstaller(executor)
        val result = installer.executeRootInstall(
            "com.example.app",
            listOf(InstallPart("base.apk", 4L) { ByteArrayInputStream("BASE".toByteArray()) })
        )

        assertFalse(result.success)
        assertEquals(InstallFailureReason.SIGNATURE_MISMATCH, result.failureReason)
        assertTrue(executor.commands.contains("pm install-abandon 77"))
    }

    @Test
    fun executeRootInstallRejectsInvalidPackageAndOmitsInvalidInstallerFlag() {
        val executor = FakeRootShellExecutor(
            runHandler = { cmd ->
                when {
                    cmd.startsWith("pm install-create") -> RootCommandOutput(0, "Success: created install session [10]")
                    cmd == "pm install-commit 10" -> RootCommandOutput(0, "Success")
                    else -> RootCommandOutput(1, "Unexpected: $cmd")
                }
            }
        )
        val installer = RootInstaller(executor)

        val badTarget = installer.executeRootInstall(
            "com.evil;rm",
            listOf(InstallPart("base.apk", 4L) { ByteArrayInputStream("BASE".toByteArray()) })
        )
        assertFalse(badTarget.success)
        assertTrue(executor.commands.isEmpty())

        val singleSegment = installer.executeRootInstall(
            "singleword",
            listOf(InstallPart("base.apk", 4L) { ByteArrayInputStream("BASE".toByteArray()) })
        )
        assertFalse(singleSegment.success)
        assertTrue(executor.commands.isEmpty())

        val badInstallerFlagResult = installer.executeRootInstall(
            "com.example.app",
            listOf(InstallPart("base.apk", 4L) { ByteArrayInputStream("BASE".toByteArray()) }),
            installerPackageName = "bad_pkg_without_dot"
        )
        assertTrue(badInstallerFlagResult.success)
        assertEquals("pm install-create -r -S 4", executor.commands.first())
    }

    @Test
    fun executeRootInstallFailsClosedOnSha256OrInjectedIntegrityMismatchBeforeSu() {
        val executor = FakeRootShellExecutor({ RootCommandOutput(0, "Success: created install session [1]") })
        val realVerifierInstaller = RootInstaller(executor)
        val hashMismatch = realVerifierInstaller.executeRootInstall(
            "com.example.app",
            listOf(
                InstallPart(
                    name = "base.apk",
                    sizeBytes = 4L,
                    expectedSha256 = "0".repeat(64),
                    openStream = { ByteArrayInputStream("BASE".toByteArray()) }
                )
            )
        )
        assertFalse(hashMismatch.success)
        assertTrue(executor.commands.isEmpty())

        val sigVerifierInstaller = RootInstaller(
            shellExecutor = executor,
            integrityVerifier = { _, _ ->
                throw ApkIntegrityException("Signer mismatch", ApkIntegrityException.Reason.SIGNATURE_MISMATCH)
            }
        )
        val sigMismatch = sigVerifierInstaller.executeRootInstall(
            "com.example.app",
            listOf(InstallPart("base.apk", 4L) { ByteArrayInputStream("BASE".toByteArray()) })
        )
        assertFalse(sigMismatch.success)
        assertEquals(InstallFailureReason.SIGNATURE_MISMATCH, sigMismatch.failureReason)
        assertTrue(executor.commands.isEmpty())
    }

    @Test
    fun processRootShellExecutorDrainsLargeStdoutConcurrentlyWithStdinWithoutDeadlock() {
        // Bounded 1 KB OS-like pipes: if stdout were not drained concurrently while stdin is written,
        // the child thread writing 64 KB before reading stdin would deadlock permanently.
        val childStdoutSource = PipedOutputStream()
        val parentStdoutSink = PipedInputStream(childStdoutSource, 1024)
        val parentStdinSource = PipedOutputStream()
        val childStdinSink = PipedInputStream(parentStdinSource, 1024)
        val doneLatch = CountDownLatch(1)
        val exitCode = AtomicInteger(0)

        val fakeProcess = object : Process() {
            override fun getOutputStream(): OutputStream = parentStdinSource
            override fun getInputStream(): InputStream = parentStdoutSink
            override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
            override fun waitFor(): Int {
                doneLatch.await(10, TimeUnit.SECONDS)
                return exitCode.get()
            }
            override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
                doneLatch.await(timeout, unit)
            override fun exitValue(): Int = exitCode.get()
            override fun destroy() {
                doneLatch.countDown()
            }
        }

        val receivedBytes = java.io.ByteArrayOutputStream()
        val childThread = Thread {
            try {
                // Write 64 KB to stdout BEFORE draining stdin (classic pipe-deadlock trigger)
                val line = ("STDOUT_LINE_0123456789ABCDEF\n").repeat(2048).toByteArray()
                childStdoutSource.write(line)
                childStdoutSource.flush()
                childStdinSink.use { it.copyTo(receivedBytes) }
                childStdoutSource.write("Success\n".toByteArray())
                childStdoutSource.close()
            } catch (_: Throwable) {
                exitCode.set(1)
            } finally {
                doneLatch.countDown()
            }
        }.apply {
            isDaemon = true
            start()
        }

        val payload = ByteArray(32 * 1024) { (it % 127).toByte() }
        val result = ProcessRootShellExecutor.executeProcess(
            process = fakeProcess,
            input = ByteArrayInputStream(payload),
            timeoutSeconds = 5L
        )
        childThread.join(5_000L)

        assertEquals(0, result.exitCode)
        assertTrue(result.output.endsWith("Success"))
        assertEquals(payload.size, receivedBytes.size())
    }
}
