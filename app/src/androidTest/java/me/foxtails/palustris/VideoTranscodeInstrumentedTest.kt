@file:Suppress("UnsafeOptInUsageError")

package me.foxtails.palustris

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.media.Media3Mp4Transcoder
import me.foxtails.palustris.data.media.MediaMetadataVideoProbe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real H.264 MP4 path on a device: build a one second HEVC clip from a still image, read it
 * back with the probe, convert it with a lower height cap, and check that the result is H.264.
 */
@RunWith(AndroidJUnit4::class)
class VideoTranscodeInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "video-instrumented").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun probeAndMp4TranscodeRoundTrip() = runBlocking {
        val source = buildSourceClip(File(directory, "source.mp4"))
        val probe = MediaMetadataVideoProbe()
        val facts = probe.probe(source)!!
        assertEquals("video/mp4", facts.containerMimeType)
        assertEquals("video/hevc", facts.videoCodecMime)
        assertEquals(240, facts.height)
        assertTrue("duration ${facts.durationMs}", facts.durationMs in 700L..1600L)

        val output = File(directory, "out.mp4")
        var last = 0f
        Media3Mp4Transcoder(context).transcode(source, output, maxHeight = 120, videoBitrate = 300_000) { last = it }
        assertTrue(output.length() > 0)
        assertEquals(1f, last, 0f)
        val converted = probe.probe(output)!!
        assertEquals("video/avc", converted.videoCodecMime)
        // Hardware encoders align the frame to 16 pixels, so 120 may come back as 128.
        assertTrue("height ${converted.height}", converted.height in 120..128)
    }

    @Test
    fun aFileThatIsNotAVideoIsNotProbed() {
        val text = File(directory, "note.txt").apply { writeText("not a video") }
        assertNull(MediaMetadataVideoProbe().probe(text))
    }

    /** A still image becomes a one second, 30 fps clip. Transformer needs a looper, so this runs on the main thread. */
    private suspend fun buildSourceClip(target: File): File {
        val image = File(directory, "frame.png")
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(30, 90, 160))
            drawCircle(160f, 120f, 70f, Paint().apply { color = Color.YELLOW })
        }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H265)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            done.complete(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            done.completeExceptionally(exportException)
                        }
                    },
                )
                .build()
            val still = MediaItem.Builder().setUri(Uri.fromFile(image)).setImageDurationMs(1_000L).build()
            val item = EditedMediaItem.Builder(still).setFrameRate(30).build()
            transformer.start(item, target.path)
            done.await()
        }
        return target
    }
}
