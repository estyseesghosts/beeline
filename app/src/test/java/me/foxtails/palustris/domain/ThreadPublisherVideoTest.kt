package me.foxtails.palustris.domain

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The publisher's handling of video attachments: progress, and one retry after a server refuses WebM. */
class ThreadPublisherVideoTest {
    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val accountId = AccountId(connection, "owner")

    private class FakeSource(private val refuse: (MediaUploadRequest) -> Exception?) : SocialSource {
        override val capabilities = ServerCapabilities(
            timelines = setOf(Timeline.Home),
            audiences = setOf(Audience.Public),
            posting = PostingCapabilities(),
        )
        val uploads = mutableListOf<String>()
        var created = 0

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun create(request: CreatePostRequest): Post {
            created += 1
            return Post(
                id = EntityId("https://example.org", "created-$created"),
                author = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org"),
                text = request.text,
                publishedAtEpochMillis = 0L,
                audience = Audience.Public,
            )
        }

        override suspend fun uploadMedia(request: MediaUploadRequest): Attachment {
            uploads += request.mimeType
            refuse(request)?.let { throw it }
            return Attachment(id = "upload-${uploads.size}-${request.mimeType}", mimeType = request.mimeType)
        }
    }

    private class Prepared(override val fileName: String, override val mimeType: String, val log: MutableList<String>) :
        PreparedThreadImage {
        override fun open(): InputStream = ByteArrayInputStream(ByteArray(4))
        override fun release() {
            log += "release:$mimeType"
        }
    }

    /** Hands out WebM until a rejection, then MP4, the way the production preparer does. */
    private class WebmThenMp4(private val reportProgress: Boolean = false) : ThreadImagePreparer {
        val log = mutableListOf<String>()
        private var rejected = false

        override suspend fun prepare(
            accountId: AccountId?,
            draftId: String,
            media: ThreadPublicationMedia,
            compress: Boolean,
            limits: ThreadImageLimits,
            onProgress: (Float) -> Unit,
        ): PreparedThreadImage {
            if (reportProgress) {
                onProgress(0.25f)
                onProgress(1f)
            }
            return Prepared(media.fileName, if (rejected) "video/mp4" else "video/webm", log)
        }

        override fun uploadRejected(accountId: AccountId?, prepared: PreparedThreadImage, error: Exception): Boolean {
            val retry = prepared.mimeType == "video/webm" && error is SourceError.Unsupported
            if (retry) rejected = true
            return retry
        }
    }

    private fun publication() = ThreadPublication(
        draftId = "draft-1",
        entries = listOf(
            ThreadPublicationEntry(
                id = "e1",
                text = "clip",
                media = listOf(ThreadPublicationMedia(id = "m1", mimeType = "video/mp4", fileName = "clip.mp4")),
            ),
        ),
        audience = Audience.Public,
        accountId = accountId,
    )

    private fun refuseWebm(request: MediaUploadRequest): Exception? =
        if (request.mimeType == "video/webm") SourceError.Unsupported("media.file-type") else null

    @Test
    fun serverRefusalOfWebmPreparesAgainAndUploadsMp4() = runTest {
        val source = FakeSource(::refuseWebm)
        val preparer = WebmThenMp4()
        val result = ThreadPublisher(source, preparer, source.capabilities.posting, accountId).publish(publication())
        assertTrue(result is ThreadPublishResult.Success)
        assertEquals(listOf("video/webm", "video/mp4"), source.uploads)
        assertEquals(listOf("release:video/webm", "release:video/mp4"), preparer.log)
        assertEquals("upload-2-video/mp4", (result as ThreadPublishResult.Success).requests.single().attachments.single().id)
    }

    @Test
    fun theRetryHappensOnlyOnce() = runTest {
        val source = FakeSource { SourceError.Unsupported("media.file-type") }
        val preparer = WebmThenMp4()
        val result = ThreadPublisher(source, preparer, source.capabilities.posting, accountId).publish(publication())
        assertTrue(result is ThreadPublishResult.Failed)
        assertEquals(2, source.uploads.size)
        assertEquals(0, source.created)
        assertEquals(2, preparer.log.size)
    }

    @Test
    fun anUnrelatedFailureIsNotRetried() = runTest {
        val source = FakeSource { IOException("connection reset") }
        val preparer = WebmThenMp4()
        val result = ThreadPublisher(source, preparer, source.capabilities.posting, accountId).publish(publication())
        assertTrue(result is ThreadPublishResult.Failed)
        assertEquals(listOf("video/webm"), source.uploads)
        assertEquals(listOf("release:video/webm"), preparer.log)
    }

    @Test
    fun conversionProgressIsReportedThenCleared() = runTest {
        val source = FakeSource { null }
        val seen = mutableListOf<Float?>()
        ThreadPublisher(source, WebmThenMp4(reportProgress = true), source.capabilities.posting, accountId)
            .publish(publication(), onMediaProgress = { seen += it })
        assertEquals(listOf(0.25f, 1f, null), seen)
    }
}
