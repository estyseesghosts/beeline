@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.foxtails.palustris.ui.links

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostLinkResolverTest {
    private val connection = Connection("https://home.example", Protocol.MASTODON)
    private val me = AccountId(connection, "me")

    private fun post(id: String) = Post(EntityId(connection.origin, id), Account(me, "Me", "@me"), "t", 0, Audience.Public)

    private class FakeSource(var answer: Post? = null, var delayMillis: Long = 0, var failure: Exception? = null) : SocialSource {
        override val capabilities = ServerCapabilities()
        var lookups = 0
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun resolvePostUrl(url: String): Post? {
            lookups += 1
            if (delayMillis > 0) delay(delayMillis)
            failure?.let { throw it }
            return answer
        }
        override suspend fun post(id: EntityId): Post = answer ?: throw IllegalStateException("none")
    }

    @Test
    fun onlyAddressesShapedLikePostsAreTried() {
        val resolver = SessionPostLinkResolver(me, 1L, FakeSource())

        listOf(
            "https://remote.example/@alice/112233445566",
            "https://remote.example/users/alice/statuses/abc123",
            "https://remote.example/notes/9abc",
            "https://remote.example/notice/AbCd",
            "https://remote.example/objects/1234-5678",
        ).forEach { assertTrue(it, resolver.looksLikePost(it)) }
        listOf(
            "https://remote.example/",
            "https://remote.example/@alice",
            "https://remote.example/about",
            "https://remote.example/@alice/followers/more",
            "ftp://remote.example/notes/1",
        ).forEach { assertFalse(it, resolver.looksLikePost(it)) }
    }

    @Test
    fun aHitBecomesAnOwnedPostAndIsRemembered() = runTest {
        val source = FakeSource(answer = post("42"))
        val resolver = SessionPostLinkResolver(me, 3L, source)

        val first = resolver.resolve("https://remote.example/@alice/99")
        val second = resolver.resolve("https://remote.example/@alice/99")

        assertEquals("42", first?.post?.id?.value)
        assertEquals(3L, first?.sessionRevision)
        assertEquals(first, second)
        assertEquals(1, source.lookups)
    }

    @Test
    fun aMissIsNullAndAFailureIsAMiss() = runTest {
        val source = FakeSource(answer = null)
        val resolver = SessionPostLinkResolver(me, 1L, source)
        assertNull(resolver.resolve("https://remote.example/@alice/1"))

        source.failure = RuntimeException("boom")
        assertNull(resolver.resolve("https://remote.example/@alice/2"))
    }

    @Test
    fun aSlowLookupTimesOutAndIsNotRemembered() = runTest {
        val source = FakeSource(answer = post("1"), delayMillis = 10_000)
        val resolver = SessionPostLinkResolver(me, 1L, source, timeoutMillis = 3_000)

        assertNull(resolver.resolve("https://remote.example/@alice/1"))
        source.delayMillis = 0
        assertNotNull(resolver.resolve("https://remote.example/@alice/1"))
        assertEquals(2, source.lookups)
    }

    @Test
    fun aRetiredResolverAnswersNothing() = runTest {
        val resolver = SessionPostLinkResolver(me, 1L, FakeSource(answer = post("1")))
        resolver.retire()

        assertFalse(resolver.looksLikePost("https://remote.example/@alice/1"))
        assertNull(resolver.resolve("https://remote.example/@alice/1"))
        assertNull(resolver.load(EntityId(connection.origin, "1")))
    }

    @Test
    fun anIdLoadsThroughTheSource() = runTest {
        val resolver = SessionPostLinkResolver(me, 1L, FakeSource(answer = post("5")))

        assertEquals("5", resolver.load(EntityId(connection.origin, "5"))?.post?.id?.value)
    }
}
