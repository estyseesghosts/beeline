package me.foxtails.palustris.data.notifications.push

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.transport.HttpClientPool
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationPermissionController
import me.foxtails.palustris.data.notifications.NotificationPresentation
import me.foxtails.palustris.data.notifications.NotificationPresenter
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushProviderInfo
import me.foxtails.palustris.domain.PushRegistration
import me.foxtails.palustris.domain.PushSessionState
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.ValidatedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Push source selection stays bound to its notification token. A failed token
 * lookup ends as a no-op. It never escalates to account lookup or a transient
 * factory source, which could belong to a replacement session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnifiedPushRegistrationManagerTest {
    private val account = AccountId(Connection("https://push-token.example", Protocol.MISSKEY), "receiver")
    private val otherAccount = AccountId(Connection("https://push-token.example", Protocol.MISSKEY), "other")
    private val endpoint = checkNotNull(ValidatedUrl.https("https://push.example/endpoint"))

    @Before
    fun initializeWorkManager() {
        // Synchronous test driver. A real WorkManager database tracker crashes
        // under Robolectric and poisons later tests with uncaught exceptions.
        runCatching {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                ApplicationProvider.getApplicationContext(),
            )
        }
    }

    @Test
    fun currentTokenSelectsOnlyItsRegisteredSource() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        val source = CountingSource()
        setup.registry.register(token, source)
        try {
            setup.manager.enable(account)

            assertEquals(listOf("main.metadata"), source.calls)
            assertEquals(
                NotificationPushRegistrationState.RegisteringWithDistributor,
                setup.repository.pushRegistration(account)?.state,
            )
            assertNull(setup.repository.pushRegistration(account)?.lastErrorCategory)
            assertEquals(listOf("push-instance"), setup.connector.registered)
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun oldTokenMissesAfterReplacementAndWritesNothing() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val old = NotificationSyncToken(account, 1)
        setup.repository.activate(old)
        val stale = CountingSource("stale")
        setup.registry.register(old, stale)
        setup.repository.activate(NotificationSyncToken(account, 2))
        try {
            setup.manager.enable(account)

            assertTrue(stale.calls.isEmpty())
            // prepare() still writes its idle shell, but a stale token must
            // not advance it. A factory escalation would hit the network, fail,
            // and record a failure state. Off with no error proves no source
            // was touched.
            val stored = setup.repository.pushRegistration(account)
            assertEquals(NotificationPushRegistrationState.Off, stored?.state)
            assertNull(stored?.lastErrorCategory)
            assertEquals(0, stored?.retryCount)
            assertTrue(setup.connector.registered.isEmpty())
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun tokenForAnotherAccountMakesNoRequest() = runBlocking {
        val store = FakeSessionStore(mapOf(
            account to session(account, revision = 1),
            otherAccount to session(otherAccount, revision = 1),
        ))
        val setup = setup(store)
        val otherToken = NotificationSyncToken(otherAccount, 1)
        setup.repository.activate(NotificationSyncToken(account, 1))
        setup.repository.activate(otherToken)
        val otherSource = CountingSource("other")
        setup.registry.register(otherToken, otherSource)
        try {
            setup.manager.enable(account)

            assertTrue(otherSource.calls.isEmpty())
            val stored = setup.repository.pushRegistration(account)
            assertEquals(NotificationPushRegistrationState.Off, stored?.state)
            assertNull(stored?.lastErrorCategory)
            assertTrue(setup.connector.registered.isEmpty())
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun replacementBetweenSessionReadAndLookupMakesNoRequest() = runBlocking {
        val first = session(account, revision = 1)
        val replacement = session(account, revision = 2)
        val store = FakeSessionStore(mapOf(account to first))
        // The replacement lands after the reconcile reads and before the
        // ownership recheck. Counting from the first read keeps the arrange
        // phase outside the script.
        var reads = 0
        store.onRead = { _, _ ->
            reads++
            if (reads >= 3) replacement else first
        }
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        val source = CountingSource()
        setup.registry.register(token, source)
        try {
            setup.manager.enable(account)

            assertTrue(source.calls.isEmpty())
            assertTrue(setup.repository.pushRegistration(account)?.state !=
                NotificationPushRegistrationState.AccessDenied)
        } finally {
            setup.manager.close()
            store.onRead = null
        }
    }

    @Test
    fun replacementWhileMetadataWaitsNeverReachesMutation() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        setup.repository.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "push-instance",
            sessionRevision = 1,
        ))
        val enteredQuery = CompletableDeferred<Unit>()
        val releaseQuery = CompletableDeferred<PushSubscription?>()
        val source = CountingSource(queryEntered = enteredQuery, queryGate = releaseQuery)
        setup.registry.register(token, source)
        try {
            val pending = async(Dispatchers.Default) { setup.manager.processPendingEndpoint(account) }
            withTimeout(10_000) { enteredQuery.await() }
            store.sessions[account] = session(account, revision = 2)
            releaseQuery.complete(PushSubscription(account, endpoint, "remote-1"))
            val outcome = withTimeout(10_000) { pending.await() }

            assertEquals(PushRegistrationWorkResult.NoWork, outcome)
            assertEquals(listOf("main.query"), source.calls)
            assertTrue(setup.repository.pushRegistration(account)?.state !=
                NotificationPushRegistrationState.Connected)
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun registryReplacementWithOldRepositoryTokenEndsAsNoWork() = runBlocking {        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val old = NotificationSyncToken(account, 1)
        setup.repository.activate(old)
        setup.repository.updatePushRegistration(old, PushRegistration(
            accountId = account,
            generation = old.generation,
            instanceName = "push-instance",
            sessionRevision = 1,
        ))
        val replacement = CountingSource("replacement")
        setup.registry.register(NotificationSyncToken(account, 2), replacement)
        try {
            val outcome = setup.manager.processPendingEndpoint(account)

            assertEquals(PushRegistrationWorkResult.NoWork, outcome)
            assertTrue(replacement.calls.isEmpty())
            assertTrue(setup.repository.pushRegistration(account)?.state !=
                NotificationPushRegistrationState.Connected)
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun newerEndpointDuringMetadataRetriesWithoutMutation() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        setup.repository.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "push-instance",
            sessionRevision = 1,
        ))
        val enteredQuery = CompletableDeferred<Unit>()
        val releaseQuery = CompletableDeferred<PushSubscription?>()
        val source = CountingSource(queryEntered = enteredQuery, queryGate = releaseQuery)
        setup.registry.register(token, source)
        val newerEndpoint = checkNotNull(ValidatedUrl.https("https://push.example/endpoint-2"))
        try {
            val pending = async(Dispatchers.Default) { setup.manager.processPendingEndpoint(account) }
            withTimeout(10_000) { enteredQuery.await() }
            // A newer distributor endpoint lands while metadata waits. The old
            // work must retry as fresh work, never mutate with its endpoint.
            store.sessions[account] = session(
                account,
                revision = 1,
                endpoint = newerEndpoint,
                endpointGeneration = 4,
            )
            releaseQuery.complete(null)
            val outcome = withTimeout(10_000) { pending.await() }

            assertEquals(PushRegistrationWorkResult.Retry, outcome)
            assertEquals(listOf("main.query"), source.calls)
            assertTrue(setup.repository.pushRegistration(account)?.state !=
                NotificationPushRegistrationState.Connected)
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun replacementDuringCreationSkipsPolicyAsNoWork() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        setup.repository.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "push-instance",
            sessionRevision = 1,
        ))
        val enteredCreate = CompletableDeferred<Unit>()
        val releaseCreate = CompletableDeferred<PushSubscription>()
        val source = CountingSource(
            queryResult = null,
            createEntered = enteredCreate,
            createGate = releaseCreate,
        )
        setup.registry.register(token, source)
        try {
            val pending = async(Dispatchers.Default) { setup.manager.processPendingEndpoint(account) }
            withTimeout(10_000) { enteredCreate.await() }
            store.sessions[account] = session(account, revision = 2)
            releaseCreate.complete(PushSubscription(account, endpoint, "remote-1"))
            val outcome = withTimeout(10_000) { pending.await() }

            assertEquals(PushRegistrationWorkResult.NoWork, outcome)
            assertEquals(listOf("main.query", "main.create"), source.calls)
            assertTrue(setup.repository.pushRegistration(account)?.state !=
                NotificationPushRegistrationState.Connected)
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun registryReplacementDuringMetadataEndsAsNoWork() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        setup.repository.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "push-instance",
            sessionRevision = 1,
        ))
        val enteredQuery = CompletableDeferred<Unit>()
        val releaseQuery = CompletableDeferred<PushSubscription?>()
        val source = CountingSource(queryEntered = enteredQuery, queryGate = releaseQuery)
        setup.registry.register(token, source)
        val replacement = CountingSource("replacement")
        try {
            val pending = async(Dispatchers.Default) { setup.manager.processPendingEndpoint(account) }
            withTimeout(10_000) { enteredQuery.await() }
            // The registry now points at a replacement source. The captured
            // source no longer owns the token, so old work must stop.
            setup.registry.register(NotificationSyncToken(account, 2), replacement)
            releaseQuery.complete(null)
            val outcome = withTimeout(10_000) { pending.await() }

            assertEquals(PushRegistrationWorkResult.NoWork, outcome)
            assertEquals(listOf("main.query"), source.calls)
            assertTrue(replacement.calls.isEmpty())
            assertTrue(setup.repository.pushRegistration(account)?.state !=
                NotificationPushRegistrationState.Connected)
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun lateUnauthorizedFailurePreservesReplacementCapabilities() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        setup.repository.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "push-instance",
            sessionRevision = 1,
        ))
        val enteredQuery = CompletableDeferred<Unit>()
        val releaseQuery = CompletableDeferred<PushSubscription?>()
        val source = CountingSource(
            queryEntered = enteredQuery,
            queryGate = releaseQuery,
            failAfterGate = SourceError.Unauthorized,
        )
        setup.registry.register(token, source)
        try {
            val pending = async(Dispatchers.Default) { setup.manager.processPendingEndpoint(account) }
            withTimeout(10_000) { enteredQuery.await() }
            store.sessions[account] = session(account, revision = 2)
            releaseQuery.complete(null)
            val outcome = withTimeout(10_000) { pending.await() }

            // Stale work ends as a no-op. It must not mark the replacement
            // session denied or record access denial against it.
            assertEquals(PushRegistrationWorkResult.NoWork, outcome)
            assertEquals(
                CapabilityStatus.Unknown,
                store.sessions[account]?.capabilities?.notifications?.webPush,
            )
            assertTrue(setup.repository.pushRegistration(account)?.state !=
                NotificationPushRegistrationState.AccessDenied)
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun replacementDuringCleanupMetadataSkipsRemovalButOptsOut() = runBlocking {
        val first = session(account, revision = 1)
        val store = FakeSessionStore(mapOf(account to first))
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        setup.repository.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "push-instance",
            sessionRevision = 1,
            state = NotificationPushRegistrationState.Connected,
        ))
        val enteredQuery = CompletableDeferred<Unit>()
        val releaseQuery = CompletableDeferred<PushSubscription?>()
        val source = CountingSource(queryEntered = enteredQuery, queryGate = releaseQuery)
        setup.registry.register(token, source)
        try {
            val pending = async(Dispatchers.Default) { setup.manager.disable(account) }
            withTimeout(10_000) { enteredQuery.await() }
            store.sessions[account] = session(account, revision = 2)
            releaseQuery.complete(PushSubscription(account, endpoint, "remote-1"))
            withTimeout(10_000) { pending.await() }

            // The replacement owns the server subscription now. Cleanup skips
            // the remote removal but still completes local opt-out.
            assertEquals(listOf("main.query"), source.calls)
            assertEquals(listOf("push-instance"), setup.connector.unregistered)
            assertNull(setup.repository.pushRegistration(account))
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun validTokenNullCleanupSkipsAccountRegistrySource() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        // No token is active, so cleanup cannot match the account registry
        // entry. The validated snapshot builds a transient source instead.
        // Misskey query with no known endpoint returns null without I/O.
        // The registry source would confirm a subscription, so any use of
        // that entry is directly observable.
        val registered = CountingSource(
            "registered",
            queryResult = PushSubscription(account, endpoint, "remote-9"),
        )
        setup.registry.register(NotificationSyncToken(account, 9), registered)
        try {
            setup.manager.disable(account)

            assertTrue(registered.calls.isEmpty())
            assertTrue(setup.connector.unregistered.isEmpty())
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun tokenNullCleanupNeverSelectsAccountRegistrySource() = runBlocking {
        val first = session(account, revision = 1)
        val replacement = session(account, revision = 2)
        val store = FakeSessionStore(mapOf(account to first))
        var reads = 0
        store.onRead = { _, _ ->
            reads++
            if (reads >= 2) replacement else first
        }
        val setup = setup(store)
        // The registry holds a newer generation, but there is no token to
        // match it. The account entry must stay untouched.
        val registered = CountingSource("registered")
        setup.registry.register(NotificationSyncToken(account, 9), registered)
        try {
            setup.manager.disable(account)

            assertTrue(registered.calls.isEmpty())
        } finally {
            setup.manager.close()
            store.onRead = null
        }
    }

    @Test
    fun remoteCleanupFailureStillCompletesLocalDisabling() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        setup.repository.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "push-instance",
            sessionRevision = 1,
            state = NotificationPushRegistrationState.Connected,
        ))
        val failing = CountingSource(queryFailure = RuntimeException("server gone"))
        setup.registry.register(token, failing)
        try {
            setup.manager.disable(account)

            assertEquals(listOf("main.query"), failing.calls)
            assertEquals(listOf("push-instance"), setup.connector.unregistered)
            assertNull(setup.repository.pushRegistration(account))
        } finally {
            setup.manager.close()
        }
    }

    @Test
    fun validEndpointFlowStillConnects() = runBlocking {
        val store = FakeSessionStore(mapOf(account to session(account, revision = 1)))
        val setup = setup(store)
        val token = NotificationSyncToken(account, 1)
        setup.repository.activate(token)
        setup.repository.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "push-instance",
            sessionRevision = 1,
        ))
        val source = CountingSource(queryResult = PushSubscription(account, endpoint, "remote-1"))
        setup.registry.register(token, source)
        try {
            val outcome = setup.manager.processPendingEndpoint(account)

            assertEquals(PushRegistrationWorkResult.Success, outcome)
            assertEquals(listOf("main.query", "main.create", "main.policy"), source.calls)
            assertEquals(
                NotificationPushRegistrationState.Connected,
                setup.repository.pushRegistration(account)?.state,
            )
            assertEquals(endpoint, setup.repository.pushRegistration(account)?.serverEndpoint)
        } finally {
            setup.manager.close()
        }
    }

    private fun session(
        accountId: AccountId,
        revision: Long,
        endpoint: ValidatedUrl = this.endpoint,
        endpointGeneration: Long = 3,
    ) = Session(
        accountId,
        "session-token",
        ServerCapabilities(),
        pushInstanceName = "push-instance",
        sessionRevision = revision,
        pushState = PushSessionState(
            endpoint = endpoint,
            publicKey = "key",
            authSecret = "secret",
            endpointGeneration = endpointGeneration,
            endpointCallbackPending = true,
        ),
    )

    private fun setup(store: FakeSessionStore): Setup {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = NotificationRepository(InMemoryNotificationStore())
        val registry = AccountSourceRegistry()
        val connector = RecordingConnector()
        val presenter = NoPresenter()
        val scheduler = NotificationWorkScheduler(context)
        val registrationRepository = PushRegistrationRepository(repository, store)
        val factory = SocialSourceFactory(HttpClientPool(), store, CapabilityCache())
        val manager = UnifiedPushRegistrationManager(
            registrationRepository,
            repository,
            store,
            registry,
            factory,
            scheduler,
            presenter,
            connector,
            GrantedPermission,
            context,
            UnifiedPushMessageHandler(
                registrationRepository,
                repository,
                store,
                registry,
                factory,
                scheduler,
                presenter,
                PushMessageDecoder(context),
            ),
        )
        return Setup(manager, repository, registry, connector)
    }

    private data class Setup(
        val manager: UnifiedPushRegistrationManager,
        val repository: NotificationRepository,
        val registry: AccountSourceRegistry,
        val connector: RecordingConnector,
    )

    private class FakeSessionStore(initial: Map<AccountId, Session>) : SessionStore {
        val sessions = initial.toMutableMap()
        val script = ArrayDeque<Session?>()
        var onRead: ((Int, AccountId) -> Session?)? = null
        private var reads = 0

        override fun read(accountId: AccountId): Session? {
            reads++
            onRead?.let { return it(reads, accountId) }
            return script.removeFirstOrNull() ?: sessions[accountId]
        }
        override fun write(accountId: AccountId, session: Session) {
            sessions[accountId] = session
        }
        override fun delete(accountId: AccountId) {
            sessions.remove(accountId)
        }
        override fun readIndex(): AccountIndex = AccountIndex(
            accounts = sessions.keys.map { AccountRef(it, "@${it.localId}@example.org", null, it.localId) },
        )
        override fun writeIndex(index: AccountIndex) = Unit
        override fun clear() = sessions.clear()
    }

    private class CountingSource(
        private val name: String = "main",
        private val pushInfo: PushProviderInfo = PushProviderInfo(CapabilityStatus.Supported, "vapid-key"),
        private val queryResult: PushSubscription? = null,
        private val queryEntered: CompletableDeferred<Unit>? = null,
        private val queryGate: CompletableDeferred<PushSubscription?>? = null,
        private val failAfterGate: Throwable? = null,
        private val createEntered: CompletableDeferred<Unit>? = null,
        private val createGate: CompletableDeferred<PushSubscription>? = null,
        private val queryFailure: Throwable? = null,
    ) : SocialSource {
        val calls = mutableListOf<String>()

        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun pushProviderInfo(): PushProviderInfo {
            calls += "$name.metadata"
            return pushInfo
        }
        override suspend fun queryOwnedPushSubscription(knownEndpoint: ValidatedUrl?): PushSubscription? {
            calls += "$name.query"
            queryEntered?.complete(Unit)
            queryFailure?.let { throw it }
            queryGate?.let { gate ->
                val result = gate.await()
                failAfterGate?.let { throw it }
                return result
            }
            failAfterGate?.let { throw it }
            return queryResult
        }
        override suspend fun createOrReplacePushSubscription(
            spec: PushSubscriptionSpec,
            previous: PushSubscription?,
        ): PushSubscription {
            calls += "$name.create"
            createEntered?.complete(Unit)
            createGate?.let { return it.await() }
            return PushSubscription(spec.accountId, spec.endpoint, "remote-1")
        }
        override suspend fun updatePushAlertPolicy(
            subscription: PushSubscription,
            alerts: Set<NotificationCategory>,
        ): PushSubscription {
            calls += "$name.policy"
            return subscription
        }
        override suspend fun removePushSubscription(subscription: PushSubscription) {
            calls += "$name.remove"
        }
    }

    private class RecordingConnector : UnifiedPushConnector {
        val saved = mutableListOf<String>()
        val registered = mutableListOf<String>()
        val unregistered = mutableListOf<String>()

        override fun availableDistributors(): List<String> = listOf("com.example.distributor")
        override fun acknowledgedDistributor(): String? = null
        override fun saveDistributor(packageName: String) {
            saved += packageName
        }
        override fun register(instanceName: String, messageForDistributor: String?, vapidPublicKey: String?) {
            registered += instanceName
        }
        override fun unregister(instanceName: String) {
            unregistered += instanceName
        }
    }

    private class NoPresenter : NotificationPresenter {
        override fun present(presentation: NotificationPresentation): Boolean = false
        override fun dismiss(accountId: AccountId, notificationId: EntityId) = Unit
    }

    private object GrantedPermission : NotificationPermissionController {
        override fun isGranted(): Boolean = true
    }
}
