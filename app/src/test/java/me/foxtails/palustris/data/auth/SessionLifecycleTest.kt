package me.foxtails.palustris.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.data.emoji.InMemoryEmojiCatalogRepository
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.CapabilityCacheKey
import me.foxtails.palustris.data.transport.HttpClientPool
import me.foxtails.palustris.data.notifications.NoOpNotificationStreamController
import me.foxtails.palustris.data.notifications.NoOpNotificationSyncController
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.data.notifications.NotificationSyncController
import me.foxtails.palustris.data.notifications.NotificationSyncState
import me.foxtails.palustris.data.notifications.push.NoOpPushRegistrationManager
import me.foxtails.palustris.data.notifications.push.PushRegistrationManager
import me.foxtails.palustris.data.notifications.push.PushRegistrationWorkResult
import me.foxtails.palustris.data.preferences.InMemoryEmojiPickerPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushSessionState
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SocialSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionLifecycleTest {
    @Test
    fun restoreLoadsIndexPendingAndActivatesActiveSession() = runTest {
        val store = RecordingStore()
        store.index = AccountIndex(accounts = listOf(ref(accountId)), activeAccountId = accountId)
        store.sessions[accountId] = session(accountId)
        store.pending = PendingLogin("https://example.org", "pending", System.currentTimeMillis())
        val sync = RecordingSync()
        val push = RecordingPush()

        val restoration = lifecycle(store, sync = sync, push = push).restore()

        assertEquals(accountId, restoration.active?.session?.accountId)
        assertNotNull(restoration.pending)
        assertEquals(listOf("register", "pushAvailable"), sync.events + push.events)
    }

    @Test
    fun restoreMapsStorageFailureToTypedFailure() = runTest {
        val store = RecordingStore().apply { readIndexFailure = IllegalStateException("broken") }

        val error = runCatching { lifecycle(store).restore() }.exceptionOrNull() as LifecycleException

        assertTrue(error.failure is SessionLifecycle.Failure.Storage)
    }

    @Test
    fun loginReplacementBumpsRevisionPreservesPushAndWritesTransaction() = runTest {
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId)), activeAccountId = accountId)
            sessions[accountId] = session(accountId, revision = 4, pushInstanceName = "instance")
            pending = PendingLogin("https://example.org", "pending", System.currentTimeMillis())
        }
        val login = LoginSession(
            origin = "https://example.org",
            token = "new-token",
            user = JSONObject("""{"id":"alice","username":"alice"}"""),
        )

        lifecycle(store).login(login)

        assertEquals(5, store.sessions.getValue(accountId).sessionRevision)
        assertEquals("instance", store.sessions.getValue(accountId).pushInstanceName)
        assertEquals(listOf("transaction", "write", "profile", "index", "clearPending"), store.events)
    }

    @Test
    fun switchPersistsIndexAndActivatesAccount() = runTest {
        val other = makeAccount("bob").id
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId), ref(other)), activeAccountId = accountId)
            sessions[other] = session(other)
        }
        val sync = RecordingSync()

        val result = lifecycle(store, sync = sync).switch(other)

        assertEquals(other, result?.index?.activeAccountId)
        assertEquals(other, result?.activation?.session?.accountId)
        assertEquals(listOf("index"), store.events)
        assertEquals(listOf("register"), sync.events)
    }

    @Test
    fun switchReportsUnavailableAccountAsTypedFailure() = runTest {
        val error = runCatching { lifecycle(RecordingStore()).switch(accountId) }.exceptionOrNull() as LifecycleException

        assertEquals(SessionLifecycle.Failure.AccountMissing(accountId), error.failure)
    }

    @Test
    fun removalOrdersDeliveryCleanupStorageAndNextActivation() = runTest {
        val other = makeAccount("bob").id
        val recorder = Recorder()
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId), ref(other)), activeAccountId = accountId)
            sessions[accountId] = session(accountId)
            sessions[other] = session(other)
        }
        val sync = RecordingSync(recorder)
        val push = RecordingPush(recorder)
        val streams = RecordingStreams(recorder)
        store.recorder = recorder

        val result = lifecycle(store, sync = sync, push = push, streams = streams).remove(accountId)

        assertEquals(listOf("stop"), streams.events)
        assertEquals(listOf("stop", "disable", "remove", "transaction", "delete", "index", "register"), recorder.events)
        assertEquals(listOf("remove", "register"), sync.events)
        assertEquals(listOf("disable", "pushAvailable"), push.events)
        assertEquals(listOf("transaction", "delete", "index"), store.events)
        assertEquals(other, result.index.activeAccountId)
        assertEquals(other, result.next?.session?.accountId)
        assertTrue(sync.registrationCount >= 1)
    }

    @Test
    fun staleWriterCannotWriteAfterLifecycleInvalidatesBeforeDeletion() = runTest {
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId)), activeAccountId = accountId)
            sessions[accountId] = session(accountId)
        }
        val authority = DirectMessageWriteAuthority()
        val writer = authority.activate(accountId)
        lifecycle(store, directMessageWriters = authority).remove(accountId)

        val accepted = authority.commitIfCurrent(accountId, writer) {
            store.events += "stale-write"
        }

        assertNull(accepted)
        assertTrue("stale-write" !in store.events)
    }

    @Test
    fun removalInvalidatesDraftWriterAndCapabilityCacheBeforeDeletingAccount() = runTest {
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId)), activeAccountId = accountId)
            sessions[accountId] = session(accountId)
        }
        val drafts = RecordingDraftStore()
        val draftWriters = DraftWriteAuthority()
        val draftGeneration = draftWriters.activate(accountId)
        val capabilityCache = CapabilityCache()
        val cacheKey = CapabilityCacheKey(
            origin = accountId.connection.origin,
            accountId = accountId,
            sessionRevision = 1L,
        )
        capabilityCache.put(
            cacheKey,
            ServerCapabilities(capabilitiesLastUpdated = System.currentTimeMillis()),
        )

        lifecycle(
            store,
            drafts = drafts,
            draftWriters = draftWriters,
            capabilityCache = capabilityCache,
        ).remove(accountId)

        assertEquals(1, drafts.deleteAllCount)
        assertTrue(!draftWriters.isCurrent(accountId, draftGeneration))
        val accepted = draftWriters.commitIfCurrent(accountId, draftGeneration) {
            drafts.save(PostDraft(accountId = accountId, text = "stale"))
        }
        assertNull(accepted)
        assertEquals(0, drafts.saveCount)
        assertNull(capabilityCache.get(cacheKey))
    }

    @Test
    fun removedAccountStaysRevokedWhileOtherAccountStaysUsable() = runTest {
        val other = makeAccount("bob").id
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId), ref(other)), activeAccountId = accountId)
            sessions[accountId] = session(accountId)
            sessions[other] = session(other)
        }
        val directMessageWriters = DirectMessageWriteAuthority()
        val draftWriters = DraftWriteAuthority()
        val firstDirectMessage = directMessageWriters.activate(accountId)
        val firstDraft = draftWriters.activate(accountId)
        val drafts = RecordingDraftStore()

        val result = lifecycle(
            store,
            directMessageWriters = directMessageWriters,
            drafts = drafts,
            draftWriters = draftWriters,
        ).remove(accountId)

        // Write invalidation ran before durable deletion. Stale writers stay rejected.
        assertNull(directMessageWriters.commitIfCurrent(accountId, firstDirectMessage) { "stale" })
        assertNull(draftWriters.commitIfCurrent(accountId, firstDraft) { "stale" })
        assertEquals(1, drafts.deleteAllCount)
        assertNull(store.sessions[accountId])
        // The other account keeps working in isolation. Removal activates the next
        // account, so the other account uses its live generation.
        val liveDirectMessage = directMessageWriters.activate(other)
        val liveDraft = draftWriters.activate(other)
        assertEquals("ok", directMessageWriters.commitIfCurrent(other, liveDirectMessage) { "ok" })
        assertEquals("ok", draftWriters.commitIfCurrent(other, liveDraft) { "ok" })
        // Replacement activation runs after removal with fresh generations.
        assertEquals(other, result.next?.session?.accountId)
        assertTrue(directMessageWriters.activate(accountId) != firstDirectMessage)
        assertTrue(draftWriters.activate(accountId) != firstDraft)
    }

    @Test
    fun removingActiveAccountActivatesNextAccount() = runTest {
        val other = makeAccount("bob").id
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId), ref(other)), activeAccountId = accountId)
            sessions[accountId] = session(accountId)
            sessions[other] = session(other)
        }

        val result = lifecycle(store).remove(accountId)

        assertEquals(other, result.next?.account?.id)
        assertEquals(other, result.index.activeAccountId)
    }

    @Test
    fun removalDeletesOnlyRemovedAccountDrafts() = runTest {
        val other = makeAccount("bob").id
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId), ref(other)), activeAccountId = accountId)
            sessions[accountId] = session(accountId)
            sessions[other] = session(other)
        }
        val drafts = InMemoryDraftStore()
        drafts.save(PostDraft(accountId = accountId, text = "leak"))
        drafts.save(PostDraft(accountId = other, text = "keep"))

        lifecycle(store, drafts = drafts).remove(accountId)

        assertTrue(drafts.list(accountId).isEmpty())
        assertEquals(listOf("keep"), drafts.list(other).map { it.text })
    }

    @Test
    fun removalCannotLeaveRecreatedDraftFromPendingSave() = runTest {
        val other = makeAccount("bob").id
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId), ref(other)), activeAccountId = accountId)
            sessions[accountId] = session(accountId)
            sessions[other] = session(other)
        }
        val saveGate = CompletableDeferred<Unit>()
        val enteredSave = CompletableDeferred<Unit>()
        val drafts = GatedDraftStore(saveGate = saveGate, enteredSave = enteredSave)
        drafts.saveDirect(PostDraft(accountId = other, text = "keep"))
        val draftWriters = DraftWriteAuthority()
        val generation = draftWriters.activate(accountId)
        val target = lifecycle(store, drafts = drafts, draftWriters = draftWriters)

        // A lifecycle-level late save holds the writer lock while removal waits.
        // The serialized delete still removes the late row, so no draft is recreated.
        val pendingSave = async {
            draftWriters.commitIfCurrent(accountId, generation) {
                drafts.save(PostDraft(accountId = accountId, text = "late"))
            }
        }
        enteredSave.await()
        val removal = async { target.remove(accountId) }
        saveGate.complete(Unit)
        pendingSave.await()
        removal.await()

        assertTrue(drafts.list(accountId).isEmpty())
        assertEquals(listOf("keep"), drafts.list(other).map { it.text })
    }

    @Test
    fun pendingSurvivesRestoreAndLoginClearsIt() = runTest {
        val store = RecordingStore()
        val pending = PendingLogin("https://example.org", "pending", System.currentTimeMillis())
        lifecycle(store).writePending(pending)

        val restoration = lifecycle(store).restore()

        assertEquals("pending", restoration.pending?.id)
        val login = LoginSession(
            origin = "https://example.org",
            token = "new-token",
            user = JSONObject("""{"id":"alice","username":"alice"}"""),
        )
        lifecycle(store).login(login)

        assertNull(store.readPending())
        assertTrue(store.events.contains("clearPending"))
    }

    @Test
    fun loginPreservesOtherAccountSessions() = runTest {
        val existing = session(accountId)
        val store = RecordingStore().apply {
            index = AccountIndex(accounts = listOf(ref(accountId)), activeAccountId = accountId)
            sessions[accountId] = existing
        }
        val login = LoginSession(
            origin = "https://other.example",
            token = "second-token",
            user = JSONObject("""{"id":"bob","username":"bob"}"""),
        )

        val result = lifecycle(store).login(login)

        assertEquals(existing, store.sessions[accountId])
        assertEquals("second-token", store.sessions.getValue(login.account.id).token)
        assertEquals(setOf(accountId, login.account.id), store.index.accounts.map { it.accountId }.toSet())
        assertEquals(login.account.id, result.index.activeAccountId)
    }

    @Test
    fun profilePersistenceRoundTripsBannerUrl() = runTest {
        val store = RecordingStore()
        val lifecycle = lifecycle(store)
        val profile = Account(
            id = accountId,
            displayName = "Alice",
            handle = "@alice@example.org",
            bannerUrl = "https://example.org/banner.jpg",
        )
        store.index = AccountIndex(
            accounts = listOf(AccountRef(profile.id, profile.handle, profile.avatarUrl, profile.displayName)),
            activeAccountId = profile.id,
        )

        lifecycle.updateProfile(profile)

        assertEquals(profile.bannerUrl, store.profile?.getString("bannerUrl"))
        assertTrue(store.transactionCount > 0)
    }

    private fun lifecycle(
        store: SessionStore,
        sync: NotificationSyncController = NoOpNotificationSyncController(),
        push: PushRegistrationManager = NoOpPushRegistrationManager(),
        streams: NotificationStreamController = NoOpNotificationStreamController(),
        directMessageWriters: DirectMessageWriteAuthority = DirectMessageWriteAuthority(),
        drafts: DraftStore = InMemoryDraftStore(),
        draftWriters: DraftWriteAuthority = DraftWriteAuthority(),
        capabilityCache: CapabilityCache = CapabilityCache(),
    ) = SessionLifecycle(
        store = store,
        sourceFactory = SocialSourceFactory(HttpClientPool(), capabilityCache = capabilityCache),
        notificationSync = sync,
        push = push,
        streams = streams,
        postPreferences = InMemoryPostPreferencesRepository(),
        photoGridPreferences = InMemoryPhotoGridPreferencesRepository(),
        directMessages = InMemoryDirectMessageStore(),
        directMessageWriters = directMessageWriters,
        emoji = InMemoryEmojiCatalogRepository(),
        emojiPicker = InMemoryEmojiPickerPreferencesRepository(),
        drafts = drafts,
        draftWriters = draftWriters,
        capabilityCache = capabilityCache,
        io = UnconfinedTestDispatcher(),
    )

    private class RecordingStore : SessionStore {
        val sessions = mutableMapOf<AccountId, Session>()
        var index = AccountIndex()
        var profile: JSONObject? = null
        var pending: PendingLogin? = null
        var readFailure: Exception? = null
        var readIndexFailure: Exception? = null
        val events = mutableListOf<String>()
        var recorder: Recorder? = null
        var transactionCount = 0

        override fun read(accountId: AccountId): Session? { readFailure?.let { throw it }; return sessions[accountId] }
        override fun write(accountId: AccountId, session: Session) { events += "write"; sessions[accountId] = session }
        override fun delete(accountId: AccountId) { events += "delete"; recorder?.events?.add("delete"); sessions.remove(accountId) }
        override fun readIndex(): AccountIndex { readIndexFailure?.let { throw it }; return index }
        override fun writeIndex(index: AccountIndex) {
            events += "index"
            recorder?.events?.add("index")
            this.index = index
        }
        override fun clear() { sessions.clear(); index = AccountIndex() }
        override fun <T> transaction(block: () -> T): T {
            transactionCount++
            events += "transaction"
            recorder?.events?.add("transaction")
            return block()
        }
        override fun writeProfile(accountId: AccountId, profile: JSONObject) { events += "profile"; this.profile = profile }
        override fun readPending() = pending
        override fun writePending(pending: PendingLogin) { this.pending = pending }
        override fun clearPending() { events += "clearPending"; pending = null }
    }

    private class Recorder {
        val events = mutableListOf<String>()
    }

    private class RecordingDraftStore : DraftStore {
        var saveCount = 0
        var deleteAllCount = 0

        override suspend fun list(accountId: AccountId?) = emptyList<PostDraft>()
        override suspend fun save(draft: PostDraft) { saveCount++ }
        override suspend fun delete(accountId: AccountId?, draftId: String) = Unit
        override suspend fun deleteAll(accountId: AccountId?) { deleteAllCount++ }
        override suspend fun migrateLegacy(accountId: AccountId?, preferences: android.content.SharedPreferences) = Unit
    }

    private class GatedDraftStore(
        var saveGate: CompletableDeferred<Unit>? = null,
        var enteredSave: CompletableDeferred<Unit>? = null,
    ) : DraftStore {
        private val backing = InMemoryDraftStore()

        suspend fun saveDirect(draft: PostDraft) { backing.save(draft) }

        override suspend fun list(accountId: AccountId?) = backing.list(accountId)

        override suspend fun save(draft: PostDraft) {
            enteredSave?.complete(Unit)
            saveGate?.await()
            backing.save(draft)
        }

        override suspend fun delete(accountId: AccountId?, draftId: String) { backing.delete(accountId, draftId) }

        override suspend fun deleteAll(accountId: AccountId?) { backing.deleteAll(accountId) }

        override suspend fun migrateLegacy(accountId: AccountId?, preferences: android.content.SharedPreferences) = Unit
    }

    private class RecordingSync(private val recorder: Recorder? = null) : NotificationSyncController {
        val events = mutableListOf<String>()
        var registrationCount = 0
        private val state = MutableStateFlow(NotificationSyncState())
        override fun observeAccount(accountId: AccountId): StateFlow<NotificationSyncState> = state
        override fun register(accountId: AccountId, source: SocialSource): NotificationSyncToken {
            events += "register"
            recorder?.events?.add("register")
            registrationCount++
            return NotificationSyncToken(accountId, registrationCount.toLong())
        }
        override fun unregister(accountId: AccountId) = Unit
        override suspend fun removeAccount(accountId: AccountId) {
            events += "remove"
            recorder?.events?.add("remove")
        }
    }

    private class RecordingPush(private val recorder: Recorder? = null) : PushRegistrationManager {
        val events = mutableListOf<String>()
        override fun onSessionAvailable(accountId: AccountId) { events += "pushAvailable" }
        override suspend fun enable(accountId: AccountId) = Unit
        override suspend fun retry(accountId: AccountId) = Unit
        override suspend fun processPendingEndpoint(accountId: AccountId) = PushRegistrationWorkResult.NoWork
        override suspend fun disable(accountId: AccountId) {
            events += "disable"
            recorder?.events?.add("disable")
        }
    }

    private class RecordingStreams(private val recorder: Recorder? = null) : NotificationStreamController {
        val events = mutableListOf<String>()
        override fun start(accountId: AccountId) = Unit
        override fun stop(accountId: AccountId) {
            events += "stop"
            recorder?.events?.add("stop")
        }
    }

    private fun session(id: AccountId, revision: Long = 1, pushInstanceName: String? = null) = Session(
        accountId = id,
        token = "token",
        capabilities = ServerCapabilities(),
        sessionRevision = revision,
        pushInstanceName = pushInstanceName,
        pushState = PushSessionState(),
    )

    private fun ref(id: AccountId) = AccountRef(id, "@${id.localId}@example.org", null, id.localId)

    private fun makeAccount(localId: String = "alice") = Account(
        id = AccountId(Connection("https://example.org", Protocol.MISSKEY), localId),
        displayName = localId,
        handle = "@$localId@example.org",
    )

    private companion object {
        val accountId = AccountId(Connection("https://example.org", Protocol.MISSKEY), "alice")
    }
}
