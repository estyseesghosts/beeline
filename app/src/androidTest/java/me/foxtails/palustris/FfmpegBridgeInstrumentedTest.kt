package me.foxtails.palustris

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.media.FfmpegBridge
import me.foxtails.palustris.data.media.MediaMetadataVideoProbe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real FFmpeg bridge on an arm64 device. The tests are skipped where the library is absent,
 * for example on the x86_64 emulator, so a skip is not evidence that WebM works.
 */
@RunWith(AndroidJUnit4::class)
class FfmpegBridgeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val bridge = FfmpegBridge()
    private val probe = MediaMetadataVideoProbe()
    private lateinit var directory: File

    @Before
    fun setUp() {
        assumeTrue("arm64-v8a only", Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a")
        assumeTrue("FFmpeg library present", bridge.buildId != null)
        directory = File(instrumentation.targetContext.cacheDir, "ffmpeg-instrumented").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    private fun asset(name: String): File {
        val target = File(directory, name)
        instrumentation.context.assets.open(name).use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    private fun trackMimeTypes(file: File): List<String> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            (0 until extractor.trackCount).mapNotNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
        } finally {
            extractor.release()
        }
    }

    @Test
    fun landscapeClipBecomesWebmWithVp9AndOpus() = runBlocking {
        val output = File(directory, "landscape.webm")
        var last = 0f
        bridge.transcode(asset("sample-landscape.mp4"), output, 900, null) { last = it }
        assertTrue(output.length() > 0)
        assertEquals(1f, last, 0f)
        val facts = probe.probe(output)!!
        assertEquals("video/webm", facts.containerMimeType)
        assertEquals(360, facts.height)
        assertEquals(640, facts.width)
        assertTrue("duration ${facts.durationMs}", facts.durationMs in 2_000L..4_000L)
        val tracks = trackMimeTypes(output)
        assertTrue(tracks.toString(), "video/x-vnd.on2.vp9" in tracks)
        assertTrue(tracks.toString(), "audio/opus" in tracks)
    }

    @Test
    fun rotatedClipComesOutUpright() = runBlocking {
        val output = File(directory, "rotated.webm")
        bridge.transcode(asset("sample-rotated.mp4"), output, 900, 500_000) {}
        val facts = probe.probe(output)!!
        assertEquals(360, facts.width)
        assertEquals(640, facts.height)
    }

    @Test
    fun theHeightCapScalesDown() = runBlocking {
        val output = File(directory, "small.webm")
        bridge.transcode(asset("sample-landscape.mp4"), output, 180, null) {}
        assertEquals(180, probe.probe(output)!!.height)
    }

    @Test
    fun cancellingStopsTheEncodeAndRemovesTheOutput() = runBlocking {
        val output = File(directory, "cancelled.webm")
        val source = asset("sample-landscape.mp4")
        lateinit var job: Deferred<Unit>
        job = async(Dispatchers.Default) {
            // The first progress report arrives after about one percent, long before the clip ends.
            bridge.transcode(source, output, 900, null) { job.cancel() }
        }
        try {
            job.await()
            fail("expected cancellation")
        } catch (expected: CancellationException) {
            assertFalse(output.exists())
        }
    }

    @Test
    fun aFileThatIsNotAVideoFailsCleanly() = runBlocking {
        val broken = File(directory, "broken.mp4").apply { writeText("not a video") }
        val output = File(directory, "broken.webm")
        try {
            bridge.transcode(broken, output, 900, null) {}
            fail("expected a failure")
        } catch (expected: me.foxtails.palustris.data.media.VideoTranscodeException) {
            assertFalse(output.exists())
        }
    }

    @Test
    fun theBenchmarkReportsARealtimeFactor() = runBlocking {
        val factor = bridge.benchmark(asset("sample-landscape.mp4"), 900)
        Log.i("FfmpegBridgeTest", "VP9 900p realtime factor on ${Build.MODEL}: $factor (build ${bridge.buildId})")
        assertNotNull(bridge.buildId)
        assertTrue("factor $factor", factor > 0.0)
    }
}
