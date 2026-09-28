package me.foxtails.palustris.ui.session

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.AuthGateway
import me.foxtails.palustris.data.auth.LoginSession
import me.foxtails.palustris.data.auth.PendingLogin
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.MisskeyErrorMapper
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
import me.foxtails.palustris.ui.feed.FeedViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionViewModelTest {
    private val login get() = LoginSession("https://example.org", "test-token", JSONObject("""{"id":"a","username":"alice"}"""))
    private class MemoryStore(
        initialSession: Session? = null,
        initialAccount: Account? = null,
    ) : SessionStore {
        val sessions = mutableMapOf<AccountId, Session>().apply {
            initialSession?.let { put(it.accountId, it) }
        }
        var storedSession: Session?
            get() = sessions.values.firstOrNull()
            set(value) {
                sessions.clear()
                value?.let { sessions[it.accountId] = it }
            }
        var pending: PendingLogin? = null
        val readAccountIds = mutableListOf<AccountId>()
        val writtenAccountIds = mutableListOf<AccountId>()
        var index = initialAccount?.let { account ->
            AccountIndex(accounts = listOf(AccountRef(account.id, account.handle, account.avatarUrl, account.displayName)), activeAccountId = account.id)
        } ?: AccountIndex()

        override fun read(accountId: AccountId): Session? {
            readAccountIds += accountId
            return sessions[accountId]
        }
        override fun write(accountId: AccountId, session: Session) {
            writtenAccountIds += accountId
            sessions[accountId] = session
        }
        override fun delete(accountId: AccountId) { sessions.remove(accountId) }
        override fun readIndex() = index
        override fun writeIndex(index: AccountIndex) { this.index = index }
        override fun readPending() = pending
        override fun writePending(pending: PendingLogin) { this.pending = pending }
        override fun clearPending() { pending = null }
        override fun clear() { sessions.clear(); pending = null; index = AccountIndex() }
    }
    private class Source(
        override val capabilities: ServerCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
    ) : SocialSource {
        var error: Exception? = null
        var createError: Exception? = null
        var failingTimeline: Timeline? = null
        var createGate: CompletableDeferred<Unit>? = null
        val timelineCalls = mutableListOf<Pair<Timeline, String?>>()
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
            timelineCalls += timeline to cursor
            if (timeline == failingTimeline) throw IOException("timeline unavailable")
            error?.let { throw MisskeyErrorMapper.map(it) }
            val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "a"), "Alice", "@alice")
            fun post(id: String) = Post(EntityId("example", id), account, "Text", 0, Audience.Public)
            return if (cursor == null) Page(listOf(post("2")), "2") else Page(listOf(post("2"), post("1")), null)
        }
        override suspend fun create(post: CreatePostRequest): Post {
            createError?.let { throw MisskeyErrorMapper.map(it) }
            createGate?.await()
            val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "a"), "Alice", "@alice")
            return Post(EntityId("example", "created"), account, post.text, 0, Audience.Public)
        }
    }

    private class AccountFeedSource(
        private val author: Account,
        override val capabilities: ServerCapabilities,
    ) : SocialSource {
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(
            listOf(
                Post(
                    EntityId(author.id.connection.origin, "post-${author.id.localId}"),
                    author,
                    "${author.displayName} post",
                    0,
                    Audience.Public,
                ),
            ),
        )
    }

    private class ActionSource : SocialSource {
        override val capabilities = ServerCapabilities(
            timelines = setOf(Timeline.Home),
            actions = setOf(PostAction.Favorite, PostAction.Reshare, PostAction.React),
        )
        val favoriteIds = mutableListOf<EntityId>()
        val reshareIds = mutableListOf<EntityId>()
        val reactionIds = mutableListOf<EntityId>()
        var actionError: Exception? = null
        private val author = Account(
            AccountId(Connection("https://example.org", Protocol.MISSKEY), "author"),
            "Author",
            "@author@example.org",
        )

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(
            listOf(Post(
                EntityId("https://example.org", "post"),
                author,
                "Post",
                0,
                Audience.Public,
                actionTargetId = EntityId("https://example.org", "original"),
            )),
        )

        override suspend fun favorite(id: EntityId) {
            actionError?.let { throw it }
            favoriteIds += id
        }

        override suspend fun renote(id: EntityId) {
            actionError?.let { throw it }
            reshareIds += id
        }

        override suspend fun react(id: EntityId, emoji: String) {
            actionError?.let { throw it }
            reactionIds += id
        }
    }
    private fun auth(result: LoginSession) = object : AuthGateway {
        override suspend fun prepare(input: String) = PendingLogin(input, "session-id", System.currentTimeMillis())
        override fun browserUrl(pending: PendingLogin) = "${pending.origin}/miauth/${pending.id}"
        override suspend fun complete(pending: PendingLogin) = result
    }

    private fun mastodonAuth(result: LoginSession) = object : AuthGateway {
        override suspend fun prepare(input: String) = PendingLogin(
            input,
            "session-id",
            System.currentTimeMillis(),
            protocol = Protocol.MASTODON,
        )
        override fun browserUrl(pending: PendingLogin) = "https://example.org/oauth/authorize"
        override suspend fun complete(pending: PendingLogin) = result
    }

    @Test fun restorePageDeduplicateRetryAndSignOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            val accountManager = accountManagerFixture(store, auth(login), StandardTestDispatcher(testScheduler))
            val feedModel = FeedViewModel(login.account.id, source, NotificationSyncOrchestrator(), me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(), me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository(), 0L, me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority())
            owner.put("account", accountManager)
            owner.put("feed", feedModel)
            advanceUntilIdle()
            assertEquals("@alice@example.org", accountManager.session.value.account?.handle)
            assertEquals(1, feedModel.feed.value.posts.size)
            assertEquals(login.account.id, feedModel.feed.value.ownedPosts.single().fetchedBy)
            feedModel.loadMore(); advanceUntilIdle()
            assertEquals(listOf("2", "1"), feedModel.feed.value.posts.map { it.id.value })
            source.error = IOException()
            feedModel.refresh(); advanceUntilIdle()
            assertEquals(2, feedModel.feed.value.posts.size)
            assertNotNull(feedModel.feed.value.error)
            source.error = null
            feedModel.refresh(); advanceUntilIdle()
            assertNull(feedModel.feed.value.error)
            source.error = ApiFailure(401)
            feedModel.refresh(); advanceUntilIdle()
            assertTrue(feedModel.feed.value.needsSignIn)
            accountManager.signOut(); advanceUntilIdle()
            assertNull(accountManager.session.value.account)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun pendingAuthSurvivesRestartAndInvalidCallbackDoesNotConnect() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore()
            val result = login
            val dispatcher = StandardTestDispatcher(testScheduler)
            val first = accountManagerFixture(store, auth(result), dispatcher)
            owner.put("first", first)
            advanceUntilIdle()
            first.signIn("https://example.org"); advanceUntilIdle()
            assertTrue(first.session.value.pending)
            assertNotNull(first.session.value.browserUrl)
            val restored = accountManagerFixture(store, auth(result), dispatcher)
            owner.put("restored", restored)
            advanceUntilIdle()
            assertTrue(restored.session.value.pending)
            restored.callback("palustris://auth/misskey?session=attacker")
            advanceUntilIdle()
            assertNull(restored.session.value.account)
            restored.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()
            assertNotNull(restored.session.value.account)
            assertFalse(restored.session.value.pending)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun callbackDuringRestoreIsDeferredUntilPendingStateLoads() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore().apply {
                pending = PendingLogin("https://example.org", "session-id", System.currentTimeMillis())
            }
            val manager = accountManagerFixture(store, auth(login), StandardTestDispatcher(testScheduler))
            owner.put("account", manager)

            manager.callback("palustris://auth/misskey?session=session-id")
            assertNull(manager.session.value.account)

            advanceUntilIdle()
            assertNotNull(manager.session.value.account)
            assertFalse(manager.session.value.pending)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun callbackWithoutPendingStateIsIgnored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val manager = accountManagerFixture(MemoryStore(), auth(login), StandardTestDispatcher(testScheduler))
            owner.put("account", manager)
            advanceUntilIdle()

            manager.callback("palustris://auth/misskey?session=unexpected")
            advanceUntilIdle()

            assertNull(manager.session.value.account)
            assertNull(manager.session.value.error)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun mastodonMissingCallbackShowsReturnViaBrowserError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore()
            val manager = accountManagerFixture(
                store,
                mastodonAuth(LoginSession("https://example.org", "test-token", JSONObject("{}"), Protocol.MASTODON)),
                StandardTestDispatcher(testScheduler),
                uiStrings = UiStrings.from(ApplicationProvider.getApplicationContext()),
            )
            owner.put("account", manager)
            advanceUntilIdle()
            manager.signIn("https://example.org")
            advanceUntilIdle()

            manager.callback("palustris://auth/mastodon?state=session-id")
            advanceUntilIdle()

            assertEquals(
                ApplicationProvider.getApplicationContext<android.content.Context>()
                    .getString(me.foxtails.palustris.R.string.session_callback_missing),
                manager.session.value.error,
            )
            assertTrue(manager.session.value.pending)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun duplicateMastodonCallbackExchangesOnceAndPreservesPendingCode() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val exchangeGate = CompletableDeferred<Unit>()
            var exchangeCalls = 0
            var pendingCodeAtExchange: String? = null
            val result = LoginSession(
                login.origin,
                login.token,
                login.user,
                Protocol.MASTODON,
            )
            val gateway = object : AuthGateway {
                override suspend fun prepare(input: String) = PendingLogin(
                    input,
                    "session-id",
                    System.currentTimeMillis(),
                    protocol = Protocol.MASTODON,
                )

                override fun browserUrl(pending: PendingLogin) = "https://example.org/oauth/authorize"

                override suspend fun complete(pending: PendingLogin): LoginSession {
                    exchangeCalls += 1
                    pendingCodeAtExchange = pending.authorizationCode
                    exchangeGate.await()
                    return result
                }
            }
            val store = MemoryStore()
            val manager = accountManagerFixture(store, gateway, StandardTestDispatcher(testScheduler))
            owner.put("account", manager)
            advanceUntilIdle()

            manager.signIn("https://example.org")
            advanceUntilIdle()
            val callback = "palustris://auth/mastodon?state=session-id&code=test-code"
            manager.callback(callback)
            runCurrent()
            manager.callback(callback)
            runCurrent()

            assertEquals(1, exchangeCalls)
            assertEquals("test-code", pendingCodeAtExchange)
            assertTrue(manager.session.value.pending)
            assertTrue(manager.session.value.busy)
            assertNull(manager.session.value.error)
            assertFalse(manager.session.value.error.orEmpty().contains("unsupported", ignoreCase = true))

            exchangeGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, exchangeCalls)
            assertNull(store.pending)
            assertNotNull(manager.session.value.account)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedPublishKeepsStateAndSuccessInvokesCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            val model = FeedViewModel(login.account.id, source, NotificationSyncOrchestrator(), me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(), me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository(), 0L, me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority())
            owner.put("feed", model)
            advanceUntilIdle()

            source.createError = IOException()
            var completed = false
            model.create(CreatePostRequest("Draft text")) { completed = true }
            advanceUntilIdle()
            assertFalse(completed)
            assertFalse(model.feed.value.publishing)
            assertNotNull(model.feed.value.error)

            source.createError = null
            model.create(CreatePostRequest("Draft text")) { completed = true }
            advanceUntilIdle()
            assertTrue(completed)
            assertFalse(model.feed.value.publishing)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun refreshPreservesPublishingPermissionActionsAndInFlightPublishing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val source = Source(
                ServerCapabilities(
                    timelines = setOf(Timeline.Home, Timeline.Local),
                    actions = setOf(PostAction.Favorite, PostAction.Reply),
                    canPublish = true,
                ),
            )
            val model = FeedViewModel(login.account.id, source, NotificationSyncOrchestrator(), me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(), me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository(), 0L, me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority())
            owner.put("feed", model)
            advanceUntilIdle()

            assertEquals(Timeline.Home, model.feed.value.timeline)
            assertTrue(model.feed.value.canPublish)
            assertEquals(setOf(PostAction.Favorite, PostAction.Reply), model.feed.value.actions)

            source.createGate = CompletableDeferred()
            model.create(CreatePostRequest("In-flight draft"))
            runCurrent()
            assertTrue(model.feed.value.publishing)

            model.refresh(Timeline.Local)
            advanceUntilIdle()
            assertEquals(Timeline.Local, model.feed.value.timeline)
            assertTrue(model.feed.value.canPublish)
            assertEquals(setOf(PostAction.Favorite, PostAction.Reply), model.feed.value.actions)
            assertTrue(model.feed.value.publishing)

            source.createGate?.complete(Unit)
            advanceUntilIdle()
            assertFalse(model.feed.value.publishing)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedTimelineChangeRestoresPreviousFeedAndDisplayedTimeline() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val source = Source(
                ServerCapabilities(
                    timelines = setOf(Timeline.Home, Timeline.Local),
                    actions = setOf(PostAction.Favorite),
                    canPublish = true,
                ),
            )
            val model = FeedViewModel(login.account.id, source, NotificationSyncOrchestrator(), me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(), me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository(), 0L, me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority())
            owner.put("feed", model)
            advanceUntilIdle()
            val previous = model.feed.value

            source.failingTimeline = Timeline.Local
            model.refresh(Timeline.Local)
            advanceUntilIdle()

            assertEquals(previous.posts, model.feed.value.posts)
            assertEquals(previous.ownedPosts, model.feed.value.ownedPosts)
            assertEquals(Timeline.Home, model.feed.value.timeline)
            assertEquals(previous.timelines, model.feed.value.timelines)
            assertEquals(previous.actions, model.feed.value.actions)
            assertTrue(model.feed.value.canPublish)
            assertNotNull(model.feed.value.error)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun actionsUseFetchedAccountFilterClientReadinessAndRejectDuplicates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = ActionSource()
            val model = FeedViewModel(login.account.id, source, coordinator, me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(), me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository(), 0L, me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority())
            owner.put("feed", model)
            advanceUntilIdle()

            assertEquals(setOf(PostAction.Favorite, PostAction.Reshare, PostAction.React), model.feed.value.actions)
            val ownedPost = model.feed.value.ownedPosts.single()
            model.favorite(ownedPost)
            model.favorite(ownedPost)
            advanceUntilIdle()
            val actionTarget = EntityId("https://example.org", "original")
            assertEquals(listOf(actionTarget), source.favoriteIds)

            model.react(ownedPost, me.foxtails.palustris.domain.EmojiChoice("🎉", "🎉"))
            advanceUntilIdle()
            assertEquals(listOf(actionTarget), source.reactionIds)

            val otherAccount = AccountId(Connection("https://example.org", Protocol.MISSKEY), "other")
            model.reshare(OwnedPost(otherAccount, ownedPost.post))
            advanceUntilIdle()
            assertTrue(source.reshareIds.isEmpty())

            source.actionError = IOException()
            model.reshare(ownedPost)
            advanceUntilIdle()
            assertNotNull(model.feed.value.error)
            source.actionError = null
            model.reshare(ownedPost)
            advanceUntilIdle()
            assertEquals(listOf(actionTarget), source.reshareIds)
        } finally {
            owner.clear()
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun switchingAccountsRebindsFeedTimelineActionsAndOwnership() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        val coordinator = NotificationSyncOrchestrator()
        try {
            val secondLogin = LoginSession(
                "https://other.example",
                "second-token",
                JSONObject("""{"id":"b","username":"bob"}"""),
            )
            val secondSession = Session(secondLogin.account.id, secondLogin.token, ServerCapabilities())
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            store.sessions[secondSession.accountId] = secondSession
            store.index = AccountIndex(
                accounts = listOf(
                    AccountRef(login.account.id, login.account.handle, null, login.account.displayName),
                    AccountRef(secondLogin.account.id, secondLogin.account.handle, null, secondLogin.account.displayName),
                ),
                activeAccountId = login.account.id,
            )
            val accountManager = accountManagerFixture(store, auth(login), StandardTestDispatcher(testScheduler))
            owner.put("accounts", accountManager)

            val firstSource = AccountFeedSource(
                login.account,
                ServerCapabilities(
                    timelines = setOf(Timeline.Home, Timeline.Local),
                    actions = setOf(PostAction.Favorite),
                    canPublish = true,
                ),
            )
            val firstFeed = FeedViewModel(login.account.id, firstSource, coordinator, me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(), me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository(), 0L, me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority())
            owner.put("first-feed", firstFeed)
            advanceUntilIdle()
            assertEquals(login.account.id, firstFeed.feed.value.ownedPosts.single().fetchedBy)
            assertEquals(setOf(Timeline.Home, Timeline.Local), firstFeed.feed.value.timelines)
            assertEquals(setOf(PostAction.Favorite), firstFeed.feed.value.actions)
            assertTrue(firstFeed.feed.value.canPublish)

            accountManager.switchAccount(secondLogin.account.id)
            advanceUntilIdle()
            assertEquals(secondLogin.account.id, accountManager.session.value.account?.id)

            firstFeed.stop()
            val secondSource = AccountFeedSource(
                secondLogin.account,
                ServerCapabilities(
                    timelines = setOf(Timeline.Home, Timeline.Federated),
                    actions = setOf(PostAction.Reshare),
                ),
            )
            val secondFeed = FeedViewModel(secondLogin.account.id, secondSource, coordinator, me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(), me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository(), 0L, me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority())
            owner.put("second-feed", secondFeed)
            advanceUntilIdle()

            assertEquals(secondLogin.account.id, secondFeed.feed.value.ownedPosts.single().fetchedBy)
            assertEquals(secondLogin.account.id, secondFeed.feed.value.posts.single().author.id)
            assertEquals(setOf(Timeline.Home, Timeline.Federated), secondFeed.feed.value.timelines)
            assertEquals(setOf(PostAction.Reshare), secondFeed.feed.value.actions)
            assertFalse(secondFeed.feed.value.canPublish)
        } finally {
            owner.clear()
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun stoppingFeedDoesNotStopAccountNotificationPolling() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        val coordinator = NotificationSyncOrchestrator()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            coordinator.register(login.account.id, source)
            val model = FeedViewModel(login.account.id, source, coordinator, me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(), me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository(), 0L, me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority())
            owner.put("feed", model)
            advanceUntilIdle()
            assertTrue(model.sync.value.isActive)

            model.stop()

            assertTrue(model.sync.value.isActive)
        } finally {
            owner.clear()
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun selectedTimelineOwnsPaginationAndPublishRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            val model = FeedViewModel(login.account.id, source, NotificationSyncOrchestrator(), me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(), me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository(), 0L, me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority())
            owner.put("feed", model)
            advanceUntilIdle()
            source.timelineCalls.clear()

            model.refresh(Timeline.Local)
            advanceUntilIdle()
            assertEquals(Timeline.Local, model.feed.value.timeline)
            assertEquals(listOf(Timeline.Local to null), source.timelineCalls)

            model.loadMore(Timeline.Home)
            advanceUntilIdle()
            assertEquals(listOf(Timeline.Local to null), source.timelineCalls)

            model.loadMore()
            advanceUntilIdle()
            assertEquals(listOf(Timeline.Local to null, Timeline.Local to "2"), source.timelineCalls)

            model.create(CreatePostRequest("Local post"))
            advanceUntilIdle()
            assertEquals(Timeline.Local, source.timelineCalls.last().first)
            assertEquals(Timeline.Local, model.feed.value.timeline)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun reauthenticationReplacesSameAccountTokenAndSessionGeneration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val replacement = LoginSession(
                "https://example.org",
                "replacement-token",
                JSONObject("""{"id":"a","username":"alice"}"""),
            )
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val model = accountManagerFixture(store, auth(replacement), StandardTestDispatcher(testScheduler))
            owner.put("account", model)
            advanceUntilIdle()
            val initialGeneration = model.session.value.sessionGeneration

            model.signIn("https://example.org")
            advanceUntilIdle()
            model.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()

            assertEquals(2L, model.connectedContext.value?.sessionRevision)
            assertTrue(model.session.value.sessionGeneration > initialGeneration)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun permissionUpgradeDoesNotReplaceAccountWhenReturnedIdentityDiffers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val existingSession = Session(login.account.id, login.token, ServerCapabilities())
            val mismatched = LoginSession(
                "https://example.org",
                "wrong-account-token",
                JSONObject("""{"id":"other","username":"other"}"""),
            )
            val store = MemoryStore(existingSession, login.account)
            val model = accountManagerFixture(
                store,
                auth(mismatched),
                StandardTestDispatcher(testScheduler),
                uiStrings = UiStrings.from(ApplicationProvider.getApplicationContext()),
            )
            owner.put("upgrade", model)
            advanceUntilIdle()

            model.upgradePermissions(login.account.id)
            advanceUntilIdle()
            model.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()

            assertEquals(existingSession, store.storedSession)
            assertEquals(login.account.id, model.connectedContext.value?.accountId)
            assertEquals(existingSession.sessionRevision, model.connectedContext.value?.sessionRevision)
            assertTrue(model.session.value.error.orEmpty().contains("match"))
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun addingAccountKeepsActiveSessionUntilCanceled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val existingSession = Session(login.account.id, login.token, ServerCapabilities())
            val store = MemoryStore(existingSession, login.account)
            val model = accountManagerFixture(store, auth(login), StandardTestDispatcher(testScheduler))
            owner.put("add", model)
            advanceUntilIdle()
            val initialContext = model.connectedContext.value

            model.beginAddAccount()
            assertTrue(model.session.value.addingAccount)
            assertEquals(login.account.id, model.session.value.account?.id)
            model.signIn("https://new.example")
            advanceUntilIdle()

            assertTrue(model.session.value.pending)
            assertTrue(model.session.value.addingAccount)
            assertSame(initialContext, model.connectedContext.value)
            assertEquals(existingSession, store.sessions[existingSession.accountId])

            model.cancelSignIn()
            advanceUntilIdle()
            assertFalse(model.session.value.addingAccount)
            assertFalse(model.session.value.pending)
            assertEquals(login.account.id, model.session.value.account?.id)
            assertSame(initialContext, model.connectedContext.value)
            assertNull(store.pending)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
}
