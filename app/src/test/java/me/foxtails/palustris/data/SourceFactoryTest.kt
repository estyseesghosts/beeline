package me.foxtails.palustris.data

import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.data.transport.HttpClientPool
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session authority for created sources. The factory requires an explicit
 * [SessionStore]. A source is current only while the store holds its session
 * revision. Construction performs no I/O, so these tests need no transport.
 *
 * Callback firing needs a live capability probe, so unit tests cannot invoke
 * the callbacks installed by [SocialSourceFactory.create]. Their revision
 * guard is covered at the store level. The factory wiring passes the source
 * account and revision by inspection.
 */
class SourceFactoryTest {
    private val misskeyId = AccountId(Connection("https://example.org", Protocol.MISSKEY), "alice")
    private val mastodonId = AccountId(Connection("https://example.com", Protocol.MASTODON), "bob")

    @Test
    fun misskeySessionCreatesMisskeySourceWithSessionCapabilities() {
        val store = FakeSessionStore()
        val session = session(misskeyId, revision = 1)
        store.write(misskeyId, session)
        val factory = factory(store)

        val source = factory.create(session)

        assertTrue(source is MisskeySource)
        assertEquals(session.capabilities, source.capabilities)
    }

    @Test
    fun mastodonSessionCreatesMastodonSourceWithSessionCapabilities() {
        val store = FakeSessionStore()
        // Non-empty timelines keep the adapter from applying its empty defaults,
        // so this asserts exact factory passthrough.
        val session = session(
            mastodonId,
            revision = 1,
            capabilities = ServerCapabilities(canPublish = true, timelines = setOf(Timeline.Home)),
        )
        store.write(mastodonId, session)
        val factory = factory(store)

        val source = factory.create(session)

        assertTrue(source is MastodonSource)
        assertEquals(session.capabilities, source.capabilities)
    }

    @Test
    fun currentSessionCheckAcceptsStoredRevision() {
        val store = FakeSessionStore()
        val session = session(misskeyId, revision = 2)
        store.write(misskeyId, session)

        assertTrue(factory(store).isCurrentSession(session))
    }

    @Test
    fun missingSessionFailsSessionCheck() {
        val store = FakeSessionStore()

        assertFalse(factory(store).isCurrentSession(session(misskeyId, revision = 1)))
    }

    @Test
    fun removedSessionFailsSessionCheck() {
        val store = FakeSessionStore()
        val session = session(misskeyId, revision = 1)
        store.write(misskeyId, session)
        store.delete(misskeyId)

        assertFalse(factory(store).isCurrentSession(session))
    }

    @Test
    fun replacedSessionFailsOldRevisionAndAcceptsReplacement() {
        val store = FakeSessionStore()
        val old = session(misskeyId, revision = 1)
        val replacement = session(misskeyId, revision = 2)
        store.write(misskeyId, old)
        val factory = factory(store)
        store.write(misskeyId, replacement)

        assertFalse(factory.isCurrentSession(old))
        assertTrue(factory.isCurrentSession(replacement))
    }

    private fun factory(store: SessionStore) =
        SocialSourceFactory(HttpClientPool(), store, CapabilityCache())

    private fun session(
        id: AccountId,
        revision: Long,
        capabilities: ServerCapabilities? = null,
    ) = Session(
        accountId = id,
        token = "token",
        capabilities = capabilities ?: ServerCapabilities(canPublish = true),
        sessionRevision = revision,
    )

    private class FakeSessionStore : SessionStore {
        private val sessions = mutableMapOf<AccountId, Session>()

        override fun read(accountId: AccountId): Session? = sessions[accountId]
        override fun write(accountId: AccountId, session: Session) {
            sessions[accountId] = session
        }
        override fun delete(accountId: AccountId) {
            sessions.remove(accountId)
        }
        override fun readIndex(): AccountIndex = AccountIndex()
        override fun writeIndex(index: AccountIndex) = Unit
        override fun clear() = sessions.clear()
    }
}
