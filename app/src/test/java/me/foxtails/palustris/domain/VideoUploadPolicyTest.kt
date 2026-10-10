package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUploadPolicyTest {
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
    fun smallFileWithAnUnlistedContainerOrCodecIsTranscoded() {
        val quicktime = source(1_000_000, container = "video/x-matroska")
        assertEquals(VideoUploadFormat.Webm, VideoUploadPolicy.choose(quicktime, mastodonWithWebm, true))
        val hevc = source(1_000_000, codec = "video/hevc")
        assertEquals(VideoUploadFormat.H264Mp4, VideoUploadPolicy.choose(hevc, mastodonWithoutWebm, false))
        val unknown = source(1_000_000, codec = null)
        assertEquals(VideoUploadFormat.H264Mp4, VideoUploadPolicy.choose(unknown, mastodonWithoutWebm, false))
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
