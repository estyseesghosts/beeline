@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.foxtails.palustris.ui.posts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostMention
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.emoji.MentionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionProfileOwnerTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val me = AccountId(connection, "me")

    private class FakeSource(private val connection: Connection) : SocialSource {
        override val capabilities = ServerCapabilities()
        val profileCalls = mutableListOf<String>()
        val searchCalls = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var running = 0
        var peak = 0
        val failing = mutableSetOf<String>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun profile(id: AccountId): Account {
            profileCalls += id.localId
            running++
            peak = maxOf(peak, running)
            try {
                gate?.await()
                if (id.localId in failing) throw SourceError.NetworkUnavailable
                return Account(id, "Name ${id.localId}", "@${id.localId}@example.org")
            } finally {
                running--
            }
        }

        override suspend fun searchAccounts(query: String): List<Account> {
            searchCalls += query
            if (query in failing) throw SourceError.NetworkUnavailable
            return listOf(Account(AccountId(connection, "found"), "Found", "@$query"))
        }
    }

    private fun mention(name: String, id: String? = name) =
        PostMention("@$name@example.org", id?.let { AccountId(connection, it) })

    @Test
    fun aHandleIsRequestedOnceAndThenResolved() = runTest {
        val source = FakeSource(connection)
        val owner = MentionProfileOwner(me, source, this)

        owner.request(mention("a"))
        owner.request(mention("A"))
        assertEquals(MentionProfile.Pending, owner.profile(mention("a")))
        advanceUntilIdle()
        owner.request(mention("a"))
        advanceUntilIdle()

        assertEquals(listOf("a"), source.profileCalls)
        assertEquals("Name a", (owner.profile(mention("a")) as MentionProfile.Resolved).account.displayName)
    }

    @Test
    fun aMentionWithoutAnAccountIdLooksTheHandleUp() = runTest {
        val source = FakeSource(connection)
        val owner = MentionProfileOwner(me, source, this)

        owner.request(mention("x", id = null))
        advanceUntilIdle()

        assertEquals(listOf("x@example.org"), source.searchCalls)
        assertTrue(source.profileCalls.isEmpty())
    }

    @Test
    fun atMostTwoRequestsRunTogether() = runTest {
        val source = FakeSource(connection).apply { gate = CompletableDeferred() }
        val owner = MentionProfileOwner(me, source, this)

        listOf("a", "b", "c", "d").forEach { owner.request(mention(it)) }
        advanceUntilIdle()
        assertEquals(2, source.running)
        source.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, source.peak)
        assertEquals(4, source.profileCalls.size)
    }

    @Test
    fun aFailedHandleIsNotRetried() = runTest {
        val source = FakeSource(connection).apply { failing += "bad" }
        val owner = MentionProfileOwner(me, source, this)

        owner.request(mention("bad"))
        advanceUntilIdle()
        owner.request(mention("bad"))
        advanceUntilIdle()

        assertEquals(MentionProfile.Failed, owner.profile(mention("bad")))
        assertEquals(listOf("bad"), source.profileCalls)
    }

    @Test
    fun theMapKeepsTheNewestHandlesOnly() = runTest {
        val source = FakeSource(connection)
        val owner = MentionProfileOwner(me, source, this, limit = 2)

        listOf("a", "b", "c").forEach { owner.request(mention(it)) }
        advanceUntilIdle()

        assertEquals(2, owner.size)
        assertNull(owner.profile(mention("a")))
        assertTrue(owner.profile(mention("c")) is MentionProfile.Resolved)
    }

    @Test
    fun aSeededAccountCostsNoRequest() = runTest {
        val source = FakeSource(connection)
        val owner = MentionProfileOwner(me, source, this)
        owner.seed(listOf(Account(AccountId(connection, "author"), "Author", "@author@example.org")))

        owner.request(mention("author"))
        advanceUntilIdle()

        assertTrue(source.profileCalls.isEmpty())
        assertEquals("Author", (owner.profile(mention("author")) as MentionProfile.Resolved).account.displayName)
    }

    @Test
    fun retiringCancelsRequestsAndClearsTheMap() = runTest {
        val source = FakeSource(connection).apply { gate = CompletableDeferred() }
        val owner = MentionProfileOwner(me, source, this)
        owner.request(mention("a"))
        advanceUntilIdle()

        owner.retire()
        advanceUntilIdle()
        owner.request(mention("b"))

        assertEquals(0, owner.size)
        assertNull(owner.profile(mention("a")))
        assertEquals(0, source.running)
    }
}
