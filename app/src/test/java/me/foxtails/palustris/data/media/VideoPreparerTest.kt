package me.foxtails.palustris.data.media

import java.io.File
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.VideoUploadFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VideoPreparerTest {
    private lateinit var directory: File
    private lateinit var source: File
    private val rejections = WebmRejections()
    private val store = MemoryBenchmarkStore()

    @Before
    fun setUp() {
        directory = File.createTempFile("video-prep-test", "").also { it.delete(); it.mkdirs() }
        source = File(directory, "clip.mp4").also { it.writeBytes(ByteArray(5 * 1024 * 1024)) }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private val facts = VideoProbeResult("video/mp4", "video/avc", durationMs = 10_000, width = 1920, height = 1080)

    private fun preparer(
        probe: VideoProbeResult? = facts,
        mp4: FakeTranscoder = FakeTranscoder(),
        webm: FakeWebm = FakeWebm(buildId = null),
        abi: String? = "arm64-v8a",
    ) = VideoPreparer(
        workDirectory = File(directory, "out"),
        probe = { probe },
        mp4 = mp4,
        webm = webm,
        device = DeviceVideoCapabilities(DeviceVideoIdentity("Pixel", 35, abi), webm, store),
        rejections = rejections,
    )

    private val misskey = VideoServerTarget("https://m.example", acceptedTypes = null, maxBytes = null)
    private val mastodon = VideoServerTarget("https://a.example", setOf("video/mp4", "video/webm"), maxBytes = null)

    @Test
    fun smallAcceptedFileIsSentAsItIsAndNotDeleted() = runBlocking {
        val small = File(directory, "small.mp4").also { it.writeBytes(ByteArray(1024)) }
        val mp4 = FakeTranscoder()
        val prepared = preparer(mp4 = mp4).prepare(small, "small.mp4", misskey)
        assertEquals(VideoUploadFormat.Original, prepared.format)
        assertEquals(small, prepared.file)
        prepared.release()
        assertTrue(small.exists())
        assertEquals(0, mp4.calls)
    }

    @Test
    fun largeFileBecomesH264Mp4WhenWebmIsUnavailable() = runBlocking {
        val mp4 = FakeTranscoder()
        val prepared = preparer(mp4 = mp4).prepare(source, "clip.mov", misskey)
        assertEquals(VideoUploadFormat.H264Mp4, prepared.format)
        assertEquals("video/mp4", prepared.mimeType)
        assertEquals("clip.mp4", prepared.fileName)
        assertEquals(900, mp4.lastMaxHeight)
        assertTrue(prepared.file.exists())
        prepared.release()
        assertFalse(prepared.file.exists())
        assertTrue(source.exists())
    }

    @Test
    fun webmIsUsedWhenServerAndDeviceAllowIt() = runBlocking {
        val webm = FakeWebm(buildId = "ff-1", factor = 2.0)
        val prepared = preparer(webm = webm).prepare(source, "clip.mp4", mastodon)
        assertEquals(VideoUploadFormat.Webm, prepared.format)
        assertEquals("video/webm", prepared.mimeType)
        assertEquals("clip.webm", prepared.fileName)
        assertEquals(1, webm.benchmarks)
    }

    @Test
    fun mastodonWithoutWebmInItsListUsesMp4AndSkipsTheBenchmark() = runBlocking {
        val webm = FakeWebm(buildId = "ff-1", factor = 2.0)
        val server = mastodon.copy(acceptedTypes = setOf("video/mp4"))
        val prepared = preparer(webm = webm).prepare(source, "clip.mp4", server)
        assertEquals(VideoUploadFormat.H264Mp4, prepared.format)
        assertEquals(0, webm.benchmarks)
    }

    @Test
    fun aServerThatRejectedWebmGetsMp4() = runBlocking {
        rejections.record("https://m.example")
        val webm = FakeWebm(buildId = "ff-1", factor = 2.0)
        val prepared = preparer(webm = webm).prepare(source, "clip.mp4", misskey)
        assertEquals(VideoUploadFormat.H264Mp4, prepared.format)
        assertEquals(0, webm.benchmarks)
    }

    @Test
    fun aWebmEncodeFailureFallsBackToMp4() = runBlocking {
        val webm = FakeWebm(buildId = "ff-1", factor = 2.0, failTranscode = true)
        val prepared = preparer(webm = webm).prepare(source, "clip.mp4", misskey)
        assertEquals(VideoUploadFormat.H264Mp4, prepared.format)
        assertEquals(1, webm.transcodes)
        assertEquals(1, File(directory, "out").listFiles()?.size)
    }

    @Test
    fun slowOrNonArm64DevicesUseMp4() = runBlocking {
        val slow = FakeWebm(buildId = "ff-1", factor = 1.4)
        assertEquals(VideoUploadFormat.H264Mp4, preparer(webm = slow).prepare(source, "a.mp4", misskey).format)
        val x86 = FakeWebm(buildId = "ff-1", factor = 5.0)
        assertEquals(VideoUploadFormat.H264Mp4, preparer(webm = x86, abi = "x86_64").prepare(source, "a.mp4", misskey).format)
        assertEquals(0, x86.benchmarks)
    }

    @Test
    fun outputOverTheServerLimitIsAResourceLimit() = runBlocking {
        val mp4 = FakeTranscoder(outputBytes = 3_000)
        val server = misskey.copy(maxBytes = 2_000)
        try {
            preparer(mp4 = mp4).prepare(source, "clip.mp4", server)
            fail("expected a resource limit")
        } catch (error: SourceError.ResourceLimit) {
            assertEquals("media.file-size", error.feature)
        }
        assertNull(File(directory, "out").listFiles()?.firstOrNull())
    }

    @Test
    fun theBitrateFollowsTheServerLimit() = runBlocking {
        val mp4 = FakeTranscoder()
        preparer(mp4 = mp4).prepare(source, "clip.mp4", misskey.copy(maxBytes = 10_000_000))
        assertEquals(7_200_000, mp4.lastBitrate)
    }

    @Test
    fun anUnreadableFileIsRejected() = runBlocking {
        try {
            preparer(probe = null).prepare(source, "clip.mp4", misskey)
            fail("expected a transcode exception")
        } catch (error: VideoTranscodeException) {
            assertTrue(error.message!!.contains("readable"))
        }
    }

    @Test
    fun aShortSourceIsNotScaledUp() = runBlocking {
        val mp4 = FakeTranscoder()
        val small = facts.copy(width = 640, height = 360)
        preparer(probe = small, mp4 = mp4).prepare(source, "clip.mp4", misskey)
        assertEquals(360, mp4.lastMaxHeight)
    }

    @Test
    fun aPortraitSourceKeepsNineHundredPixelsOnItsShortEdge() = runBlocking {
        val mp4 = FakeTranscoder()
        val portrait = facts.copy(width = 1080, height = 1920)
        preparer(probe = portrait, mp4 = mp4).prepare(source, "clip.mp4", misskey)
        // 900 wide at the source aspect ratio is 1600 tall, not 900 tall.
        assertEquals(1600, mp4.lastMaxHeight)
    }

    @Test
    fun aPortraitSourceShorterThanItsCapIsNotScaledUp() = runBlocking {
        val mp4 = FakeTranscoder()
        val portrait = facts.copy(width = 540, height = 960)
        preparer(probe = portrait, mp4 = mp4).prepare(source, "clip.mp4", misskey)
        assertEquals(960, mp4.lastMaxHeight)
    }
}

private class MemoryBenchmarkStore : VideoBenchmarkStore {
    val values = mutableMapOf<String, Double>()
    override fun read(key: String): Double? = values[key]
    override fun write(key: String, realtimeFactor: Double) {
        values[key] = realtimeFactor
    }
}

private class FakeTranscoder(private val outputBytes: Int = 1_000) : VideoTranscoder {
    var calls = 0
    var lastMaxHeight = 0
    var lastBitrate: Int? = null

    override suspend fun transcode(input: File, output: File, maxHeight: Int, videoBitrate: Int?, onProgress: (Float) -> Unit) {
        calls++
        lastMaxHeight = maxHeight
        lastBitrate = videoBitrate
        output.writeBytes(ByteArray(outputBytes))
        onProgress(1f)
    }
}

private class FakeWebm(
    override val buildId: String?,
    private val factor: Double = 0.0,
    private val failTranscode: Boolean = false,
) : WebmEncoder {
    var benchmarks = 0
    var transcodes = 0

    override suspend fun benchmark(fixture: File, maxHeight: Int): Double {
        benchmarks++
        return factor
    }

    override suspend fun transcode(input: File, output: File, maxHeight: Int, videoBitrate: Int?, onProgress: (Float) -> Unit) {
        transcodes++
        if (failTranscode) throw VideoTranscodeException("encoder failed")
        output.writeBytes(ByteArray(500))
    }
}
