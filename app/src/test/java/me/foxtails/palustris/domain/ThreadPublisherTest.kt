package me.foxtails.palustris.domain

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ThreadPublisherTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")

    private fun post(id: String) = Post(
        id = EntityId(connection.origin, id),
        author = Account(accountId, "Owner", "@owner@example.org"),
        text = id,
        publishedAtEpochMillis = 0L,
        audience = Audience.Public,
    )

    private inner class FakeSource(
        val capabilitiesPosting: PostingCapabilities = PostingCapabilities(clientCompression = true),
        var failCreateAt: Int = Int.MAX_VALUE,
        var failUploadAt: Int = Int.MAX_VALUE,
        var gateSecondCreate: CompletableDeferred<Unit>? = null,
    ) : SocialSource {
        override val capabilities = ServerCapabilities(
            timelines = setOf(Timeline.Home),
            audiences = setOf(Audience.Public, Audience.Followers),
            posting = capabilitiesPosting,
        )
        val creates = mutableListOf<CreatePostRequest>()
        val uploads = mutableListOf<MediaUploadRequest>()
        val deletedUploads = mutableListOf<String>()
        var createdCount = 0

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun create(request: CreatePostRequest): Post {
            creates += request
            if (creates.size == 2) gateSecondCreate?.await()
            if (creates.size >= failCreateAt) throw IOException("create failed")
            createdCount += 1
            return this@ThreadPublisherTest.post("created-$createdCount")
        }

        override suspend fun uploadMedia(request: MediaUploadRequest): Attachment {
            uploads += request
            request.open().use { it.readBytes() }
            if (uploads.size >= failUploadAt) throw IOException("upload failed")
            return Attachment(id = "upload-${uploads.size}", mimeType = request.mimeType)
        }

        override suspend fun deleteUpload(attachmentId: String) {
            deletedUploads += attachmentId
        }
    }

    private class FakeImage(
        override val fileName: String,
        override val mimeType: String,
        val released: MutableList<String>,
    ) : PreparedThreadImage {
        override fun open(): InputStream = ByteArrayInputStream("bytes".toByteArray())
        override fun release() {
            released += fileName
        }
    }

    private class FakePreparer(
        val released: MutableList<String> = mutableListOf(),
        var failAt: Int = Int.MAX_VALUE,
        val seenCompress: MutableList<Boolean> = mutableListOf(),
    ) : ThreadImagePreparer {
        var calls = 0
        override suspend fun prepare(
            accountId: AccountId?,
            draftId: String,
            media: ThreadPublicationMedia,
            compress: Boolean,
            limits: ThreadImageLimits,
        ): PreparedThreadImage {
            calls += 1
            seenCompress += compress
            if (calls >= failAt) throw ImageUnreadableForTest()
            return FakeImage(media.fileName, media.mimeType, released)
        }
    }

    private class ImageUnreadableForTest : IOException("cannot decode")

    private fun media(id: String, alt: String? = null) = ThreadPublicationMedia(
        id = id,
        mimeType = "image/png",
        fileName = "$id.png",
        description = alt,
        sensitive = false,
    )

    private fun entry(id: String, text: String, warning: String? = null, media: List<ThreadPublicationMedia> = emptyList()) =
        ThreadPublicationEntry(id = id, text = text, contentWarning = warning, media = media)

    private fun publication(
        draft: String = "draft-1",
        entries: List<ThreadPublicationEntry>,
        audience: Audience = Audience.Followers,
        replyTo: EntityId? = EntityId(connection.origin, "target"),
        quoteOf: EntityId? = EntityId(connection.origin, "quote"),
        compress: Boolean = true,
    ) = ThreadPublication(
        draftId = draft,
        entries = entries,
        audience = audience,
        replyTo = replyTo,
        quoteOf = quoteOf,
        compress = compress,
        accountId = accountId,
    )

    @Test
    fun threeEntriesPostInOrderWithOneAudienceAndQuoteOnlyFirst() = runTest {
        val source = FakeSource()
        val preparer = FakePreparer()
        val publisher = ThreadPublisher(source, preparer, source.capabilities.posting, accountId)
        val progress = mutableListOf<Pair<Int, Int>>()
        val posted = mutableListOf<List<Post>>()
        val result = publisher.publish(
            publication(entries = listOf(entry("e1", "one"), entry("e2", "two"), entry("e3", "three"))),
        ) { done, _ ->
            posted += listOf(done)
            progress += done.size to 3
        }

        val success = result as ThreadPublishResult.Success
        assertEquals(listOf("created-1", "created-2", "created-3"), success.created.map { it.id.value })
        assertEquals(listOf("one", "two", "three"), source.creates.map { it.text })
        // Entry 2 replies to entry 1 and entry 3 replies to entry 2.
        assertEquals("target", source.creates[0].replyTo?.value)
        assertEquals("created-1", source.creates[1].replyTo?.value)
        assertEquals("created-2", source.creates[2].replyTo?.value)
        // Every entry uses the first entry's audience.
        assertTrue(source.creates.all { it.audience == Audience.Followers })
        // The quote travels only with entry 1.
        assertEquals("quote", source.creates[0].quoteOf?.value)
        assertNull(source.creates[1].quoteOf)
        assertNull(source.creates[2].quoteOf)
        // Each request carries its idempotency key for retries.
        assertEquals(listOf("draft-1-e1", "draft-1-e2", "draft-1-e3"), source.creates.map { it.idempotencyKey })
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(3, posted.size)
    }

    @Test
    fun failureOnEntryTwoKeepsOnePostedAndARetryPostsOnlyWhatRemains() = runTest {
        val source = FakeSource(failCreateAt = 2)
        val publisher = ThreadPublisher(source, FakePreparer(), source.capabilities.posting, accountId)
        val result = publisher.publish(
            publication(entries = listOf(entry("e1", "one"), entry("e2", "two"), entry("e3", "three"))),
        ) { _, _ -> }

        val failed = result as ThreadPublishResult.Failed
        assertEquals(1, failed.posted.size)
        assertEquals("e2", failed.failedEntryId)
        assertEquals(1, failed.failedIndex)
        // Entry 1 left the draft: the retry starts at entry 2 and replies to entry 1.
        assertEquals(listOf("e2", "e3"), failed.remaining.entries.map { it.id })
        assertEquals("created-1", failed.remaining.replyTo?.value)
        assertNull(failed.remaining.quoteOf)

        source.failCreateAt = Int.MAX_VALUE
        val retry = ThreadPublisher(source, FakePreparer(), source.capabilities.posting, accountId)
        val retryResult = retry.publish(failed.remaining) { _, _ -> } as ThreadPublishResult.Success
        assertEquals(2, retryResult.created.size)
        assertEquals("created-1", source.creates[2].replyTo?.value)
        assertEquals("draft-1-e2", source.creates[2].idempotencyKey)
    }

    @Test
    fun uploadFailureStopsBeforeCreateAndCleansTheEntryUploads() = runTest {
        val source = FakeSource()
        val preparer = FakePreparer()
        // The second image of entry 2 fails during upload after the first uploaded.
        var uploadCalls = 0
        val failingUploads = object : SocialSource by source {
            override suspend fun uploadMedia(request: MediaUploadRequest): Attachment {
                uploadCalls += 1
                if (uploadCalls == 3) throw IOException("upload failed")
                return source.uploadMedia(request)
            }
        }
        val publisher = ThreadPublisher(failingUploads, preparer, source.capabilities.posting, accountId)
        val result = publisher.publish(
            publication(
                entries = listOf(
                    entry("e1", "one", media = listOf(media("m1"))),
                    entry("e2", "two", media = listOf(media("m2"), media("m3"))),
                ),
            ),
        ) { _, _ -> }

        val failed = result as ThreadPublishResult.Failed
        assertEquals(1, failed.posted.size)
        assertEquals("e2", failed.failedEntryId)
        // No create ran for the entry whose upload failed.
        assertEquals(1, source.creates.size)
        // The entry's earlier upload was deleted as unused.
        assertEquals(listOf("upload-2"), source.deletedUploads)
        // Every prepared image was released after its upload.
        assertEquals(preparer.calls, preparer.released.size)
    }

    @Test
    fun preparationFailureStopsBeforeAnyRequest() = runTest {
        val source = FakeSource()
        val preparer = FakePreparer(failAt = 1)
        val publisher = ThreadPublisher(source, preparer, source.capabilities.posting, accountId)
        val result = publisher.publish(
            publication(entries = listOf(entry("e1", "one", media = listOf(media("m1"))))),
        ) { _, _ -> }

        val failed = result as ThreadPublishResult.Failed
        assertTrue(failed.posted.isEmpty())
        assertTrue(source.creates.isEmpty())
        assertTrue(source.uploads.isEmpty())
    }

    @Test
    fun cancellationStopsTheJobAndPostsNothingMore() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSource(gateSecondCreate = gate)
        val publisher = ThreadPublisher(source, FakePreparer(), source.capabilities.posting, accountId)
        val job = launch {
            try {
                publisher.publish(
                    publication(entries = listOf(entry("e1", "one"), entry("e2", "two"), entry("e3", "three"))),
                ) { _, _ -> }
                fail("Cancellation must stop the publish job.")
            } catch (_: CancellationException) {
                // Expected: a session change cancels the job.
            }
        }
        // Wait until the second create blocks, then replace the session.
        while (source.creates.size < 2) kotlinx.coroutines.delay(1)
        job.cancelAndJoin()
        gate.complete(Unit)

        assertEquals(2, source.creates.size)
    }

    @Test
    fun altTextAndSensitivityReachTheUpload() = runTest {
        val source = FakeSource()
        val preparer = FakePreparer()
        val publisher = ThreadPublisher(source, preparer, source.capabilities.posting, accountId)
        publisher.publish(
            publication(
                entries = listOf(
                    entry("e1", "one", warning = "cw", media = listOf(media("m1", "a cat").copy(sensitive = true))),
                ),
            ),
        ) { _, _ -> }

        assertEquals("a cat", source.uploads.single().description)
        assertTrue(source.uploads.single().sensitive)
        assertTrue(preparer.seenCompress.single())
    }
}
