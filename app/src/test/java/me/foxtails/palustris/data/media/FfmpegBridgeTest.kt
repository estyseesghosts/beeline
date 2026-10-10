package me.foxtails.palustris.data.media

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FfmpegBridgeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakeNative(
        override val buildId: String? = "test-build",
        val onTranscode: (File, AtomicBoolean, FfmpegProgress) -> Boolean = { _, _, _ -> true },
        val factor: Double = 2.0,
    ) : FfmpegBridge.NativeApi {
        var lastHeight = 0
        var lastBitrate = 0

        override fun transcode(
            input: File,
            output: File,
            maxHeight: Int,
            bitrate: Int,
            progress: FfmpegProgress,
            cancel: AtomicBoolean,
        ): Boolean {
            lastHeight = maxHeight
            lastBitrate = bitrate
            return onTranscode(output, cancel, progress)
        }

        override fun benchmark(fixture: File, maxHeight: Int): Double = factor
    }

    @Test
    fun anAbsentLibraryReportsNoBuild() {
        assertNull(FfmpegBridge(FakeNative(buildId = null)).buildId)
    }

    @Test
    fun aFinishedEncodeKeepsTheOutputAndPassesTheSettings() = runBlocking {
        val output = folder.newFile("out.webm")
        val native = FakeNative(onTranscode = { out, _, progress ->
            out.writeText("webm")
            progress.onProgress(0.5f)
            true
        })
        val seen = mutableListOf<Float>()
        FfmpegBridge(native).transcode(folder.newFile("in.mp4"), output, 900, null) { seen += it }
        assertTrue(output.exists())
        assertEquals(900, native.lastHeight)
        assertEquals(0, native.lastBitrate)
        assertEquals(listOf(0.5f), seen)
    }

    @Test
    fun aNativeFailureBecomesATranscodeExceptionAndRemovesTheOutput() = runBlocking {
        val output = folder.newFile("out.webm")
        val native = FakeNative(onTranscode = { _, _, _ -> throw RuntimeException("open input: Invalid data") })
        try {
            FfmpegBridge(native).transcode(folder.newFile("in.mp4"), output, 900, 1_000_000) {}
            fail("expected a failure")
        } catch (error: VideoTranscodeException) {
            assertTrue(error.message.orEmpty().contains("WebM"))
        }
        assertFalse(output.exists())
    }

    @Test
    fun cancellingTheCallerRaisesTheFlagWaitsForNativeCodeAndRemovesTheOutput() = runBlocking {
        val output = folder.newFile("out.webm")
        val started = CountDownLatch(1)
        var sawFlag = false
        val native = FakeNative(onTranscode = { out, cancel, _ ->
            out.writeText("partial")
            started.countDown()
            // The real loop checks the flag between packets.
            while (!cancel.get()) Thread.sleep(5)
            sawFlag = true
            false
        })
        val job = async(Dispatchers.Default) {
            FfmpegBridge(native).transcode(folder.newFile("in.mp4"), output, 900, null) {}
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(sawFlag)
        assertFalse(output.exists())
    }

    @Test
    fun aStoppedEncodeIsACancellationNotAFailure() = runBlocking {
        val output = folder.newFile("out.webm")
        val native = FakeNative(onTranscode = { _, _, _ -> false })
        try {
            FfmpegBridge(native).transcode(folder.newFile("in.mp4"), output, 900, null) {}
            fail("expected cancellation")
        } catch (error: CancellationException) {
            assertFalse(output.exists())
        }
    }

    @Test
    fun theBenchmarkReturnsTheNativeFactor() = runBlocking {
        assertEquals(3.5, FfmpegBridge(FakeNative(factor = 3.5)).benchmark(folder.newFile("clip.mp4"), 900), 0.0)
    }
}
