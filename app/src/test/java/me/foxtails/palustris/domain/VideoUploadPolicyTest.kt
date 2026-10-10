package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUploadPolicyTest {
    @Test
    fun efficiencyNeedsACommonCodecNoDownscaleAndALowBitrate() {
        val sixtySeconds = 60_000L
        val lean = source(5L * 1024 * 1024)
        assertTrue(VideoUploadPolicy.alreadyEfficient(lean, 640, 360, sixtySeconds))
        assertFalse(VideoUploadPolicy.alreadyEfficient(lean.copy(videoCodecMime = "video/hevc"), 640, 360, sixtySeconds))
        assertFalse(VideoUploadPolicy.alreadyEfficient(lean, 1920, 1080, sixtySeconds))
        assertFalse(VideoUploadPolicy.alreadyEfficient(source(80L * 1024 * 1024), 640, 360, sixtySeconds))
        assertFalse(VideoUploadPolicy.alreadyEfficient(lean, null, null, sixtySeconds))
    }

    @Test
    fun landscapeHeightCapIsTheTier() {
        assertEquals(900, VideoUploadPolicy.heightCap(1920, 1080))
        assertEquals(900, VideoUploadPolicy.heightCap(1080, 1080))
    }

    @Test
    fun portraitHeightCapKeepsTheShortEdgeAtTheTier() {
        assertEquals(1600, VideoUploadPolicy.heightCap(1080, 1920))
        assertEquals(1200, VideoUploadPolicy.heightCap(900, 1200))
    }

    @Test
    fun unknownSizeGetsTheLandscapeCap() {
        assertEquals(900, VideoUploadPolicy.heightCap(null, null))
        assertEquals(900, VideoUploadPolicy.heightCap(0, 100))
    }

    private val mp4 = "video/mp4"
    private val avc = "video/avc"
    private val misskey = VideoServerSupport(acceptedTypes = null)
    private val mastodonWithWebm = VideoServerSupport(setOf("video/mp4", "video/webm"))
    private val mastodonWithoutWebm = VideoServerSupport(setOf("video/mp4", "video/quicktime"))

    private fun source(bytes: Long, codec: String? = avc, container: String = mp4) = VideoUploadSource(container, codec, bytes)

    @Test
    fun smallAcceptedFileIsKeptEvenWhenWebmIsAvailable() {
        val choice = VideoUploadPolicy.choose(source(2L * 1024 * 1024 - 1), mastodonWithWebm, deviceCanEncodeWebm = true)
        assertEquals(VideoUploadFormat.Original, choice)
    }

    @Test
    fun exactlyTwoMebibytesIsTranscoded() {
        val choice = VideoUploadPolicy.choose(source(2L * 1024 * 1024), mastodonWithWebm, deviceCanEncodeWebm = true)
        assertEquals(VideoUploadFormat.Webm, choice)
    }

    @Test
    fun smallFileWithAnUnlistedContainerIsTranscoded() {
        val matroska = source(1_000_000, container = "video/x-matroska")
        assertEquals(VideoUploadFormat.Webm, VideoUploadPolicy.choose(matroska, mastodonWithWebm, true))
        assertEquals(VideoUploadFormat.H264Mp4, VideoUploadPolicy.choose(matroska, mastodonWithoutWebm, false))
    }

    @Test
    fun smallFileIsKeptWhateverItsCodec() {
        for (codec in listOf("video/hevc", "video/av01", null)) {
            val small = source(1_000_000, codec = codec)
            assertEquals(VideoUploadFormat.Original, VideoUploadPolicy.choose(small, mastodonWithoutWebm, false))
            assertEquals(VideoUploadFormat.Original, VideoUploadPolicy.choose(small, misskey, true))
        }
    }

    @Test
    fun emptyFileIsNeverTheOriginal() {
        assertEquals(VideoUploadFormat.H264Mp4, VideoUploadPolicy.choose(source(0), misskey, deviceCanEncodeWebm = false))
    }

    @Test
    fun webmNeedsBothTheServerAndTheDevice() {
        val big = source(50L * 1024 * 1024)
        assertEquals(VideoUploadFormat.Webm, VideoUploadPolicy.choose(big, mastodonWithWebm, true))
        assertEquals(VideoUploadFormat.H264Mp4, VideoUploadPolicy.choose(big, mastodonWithWebm, false))
        assertEquals(VideoUploadFormat.H264Mp4, VideoUploadPolicy.choose(big, mastodonWithoutWebm, true))
    }

    @Test
    fun serverWithoutAListIsTriedWithWebmUntilItRejectsIt() {
        val big = source(50L * 1024 * 1024)
        assertEquals(VideoUploadFormat.Webm, VideoUploadPolicy.choose(big, misskey, true))
        val rejected = misskey.copy(rejectsWebm = true)
        assertEquals(VideoUploadFormat.H264Mp4, VideoUploadPolicy.choose(big, rejected, true))
        assertFalse(VideoUploadPolicy.serverAcceptsWebm(rejected))
        assertTrue(VideoUploadPolicy.serverAcceptsWebm(misskey))
    }

    @Test
    fun bitrateKeepsTheFileUnderTheLimit() {
        assertNull(VideoUploadPolicy.bitrateForLimit(null, 10_000))
        assertNull(VideoUploadPolicy.bitrateForLimit(1_000_000, null))
        assertNull(VideoUploadPolicy.bitrateForLimit(1_000_000, 0))
        // 10 MB over 10 s leaves 7.2 Mbit/s for video after the 10% reserve.
        assertEquals(7_200_000, VideoUploadPolicy.bitrateForLimit(10_000_000, 10_000))
        assertEquals(100_000, VideoUploadPolicy.bitrateForLimit(1_000, 600_000))
    }
}
