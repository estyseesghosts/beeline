package me.foxtails.palustris

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.foxtails.palustris.data.auth.DraftMediaStore
import me.foxtails.palustris.data.media.DraftMediaImporter
import me.foxtails.palustris.data.media.DraftThreadImagePreparer
import me.foxtails.palustris.data.media.WebmRejections
import me.foxtails.palustris.di.VideoModule
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ThreadImageLimits
import me.foxtails.palustris.domain.ThreadPublicationMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the production upload preparation on a device: pick, import into the encrypted draft store, and
 * prepare. It reads `cache/big.mp4` and `cache/small.mp4`, which the tester copies into the app cache
 * with `adb push` and `run-as`. Each stage has a timeout, so a stall fails with its stage name.
 */
@RunWith(AndroidJUnit4::class)
class VideoUploadFlowInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "flow-test")
    private val store = DraftMediaStore(context)
    private val rejections = WebmRejections()
    private val videoPreparer = VideoModule.provideVideoPreparer(context, rejections)
    private val preparer = DraftThreadImagePreparer(context, store, videoPreparer, rejections)

    @Before
    fun requireFixtures() {
        assumeTrue("fixtures pushed", File(context.cacheDir, "big.mp4").exists() && File(context.cacheDir, "small.mp4").exists())
    }

    private suspend fun <T> stage(name: String, block: suspend () -> T): T {
        val started = System.nanoTime()
        val result = try {
            withTimeout(180_000) { block() }
        } catch (error: Throwable) {
            Log.e(TAG, "stage $name failed after ${(System.nanoTime() - started) / 1_000_000} ms: $error")
            throw AssertionError("stage $name: $error", error)
        }
        Log.i(TAG, "stage $name took ${(System.nanoTime() - started) / 1_000_000} ms")
        return result
    }

    private suspend fun prepare(name: String, limits: ThreadImageLimits): Triple<String, Long, Boolean> {
        val source = File(context.cacheDir, name)
        val draft = "flow-${System.nanoTime()}"
        val media = stage("import $name") { DraftMediaImporter(context, store).import(account, draft, Uri.fromFile(source)) }
        assertTrue(media.mimeType, media.mimeType.startsWith("video/"))
        val publication = ThreadPublicationMedia(media.id, media.mimeType, name, byteSize = media.byteSize)
        val last = floatArrayOf(0f)
        val prepared = stage("prepare $name ${limits.acceptedTypes}") {
            preparer.prepare(account, draft, publication, compress = true, limits = limits) { last[0] = it }
        }
        val bytes = prepared.open().use { it.readBytes().size.toLong() }
        Log.i(TAG, "$name -> ${prepared.mimeType} ${prepared.fileName} $bytes bytes, last progress ${last[0]}")
        val result = Triple(prepared.mimeType, bytes, last[0] > 0f)
        prepared.release()
        store.deleteDraft(account, draft)
        return result
    }

    @Test
    fun smallVideoIsSentAsItIs() = runBlocking {
        val (mime, bytes, _) = prepare("small.mp4", ThreadImageLimits(maxVideoBytes = 99L shl 20))
        assertEquals("video/mp4", mime)
        assertEquals(File(context.cacheDir, "small.mp4").length(), bytes)
    }

    @Test
    fun bigVideoOnMisskeyBecomesWebm() = runBlocking {
        assumeTrue(Build.SUPPORTED_ABIS.first() == "arm64-v8a")
        val (mime, _, _) = prepare("big.mp4", ThreadImageLimits(maxVideoBytes = 100L shl 20))
        assertEquals("video/webm", mime)
    }

    @Test
    fun bigVideoOnMastodonWithoutWebmBecomesMp4() = runBlocking {
        val types = setOf("image/png", "video/mp4", "video/quicktime")
        val (mime, _, _) = prepare("big.mp4", ThreadImageLimits(acceptedTypes = types, maxVideoBytes = 99L shl 20))
        assertEquals("video/mp4", mime)
    }

    @Test
    fun bigVideoOnMastodonWithWebmBecomesWebm() = runBlocking {
        assumeTrue(Build.SUPPORTED_ABIS.first() == "arm64-v8a")
        val types = setOf("image/png", "video/mp4", "video/webm")
        val (mime, _, _) = prepare("big.mp4", ThreadImageLimits(acceptedTypes = types, maxVideoBytes = 99L shl 20))
        assertEquals("video/webm", mime)
    }

    /** Reports the probe facts and the choice for each real clip in the cache. Not an assertion: it is a diagnostic. */
    @Test
    fun realClipsReportTheirChoice() = runBlocking {
        val names = listOf("real-cam.mp4", "real-mov.mov", "real-tt.mp4").filter { File(context.cacheDir, it).exists() }
        assumeTrue("real clips present", names.isNotEmpty())
        val configs = mapOf(
            "misskey" to ThreadImageLimits(maxVideoBytes = 100L shl 20),
            "mastodon-nowebm" to ThreadImageLimits(acceptedTypes = setOf("video/mp4", "video/quicktime"), maxVideoBytes = 99L shl 20),
        )
        for (name in names) {
            val facts = me.foxtails.palustris.data.media.MediaMetadataVideoProbe().probe(File(context.cacheDir, name))
            Log.i(TAG, "$name size=${File(context.cacheDir, name).length()} facts=$facts")
            for ((label, limits) in configs) {
                runCatching {
                    val (mime, bytes, progressed) = prepare(name, limits)
                    Log.i(TAG, "RESULT $name $label -> $mime $bytes bytes progress=$progressed")
                }.onFailure { Log.e(TAG, "RESULT $name $label failed: $it") }
            }
        }
    }

    /** Diagnostic: the raw VP9 benchmark on a real clip, without the cache. Tune with `setprop debug.beeline.*`. */
    @Test
    fun rawBenchmarkOnARealClip() = runBlocking {
        assumeTrue(Build.SUPPORTED_ABIS.first() == "arm64-v8a")
        val clip = File(context.cacheDir, "real-cam.mp4")
        assumeTrue(clip.exists())
        val factor = me.foxtails.palustris.data.media.FfmpegBridge().benchmark(clip, 900)
        Log.i(TAG, "BENCH real-cam $factor")
        Unit
    }

    private companion object {
        const val TAG = "VideoFlow"
    }
}
