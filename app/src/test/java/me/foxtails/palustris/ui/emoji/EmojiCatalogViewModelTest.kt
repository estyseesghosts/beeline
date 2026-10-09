package me.foxtails.palustris.ui.emoji

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiCatalogRepository
import me.foxtails.palustris.domain.EmojiCatalogSnapshot
import me.foxtails.palustris.domain.EmojiPickerGroupIds
import me.foxtails.palustris.domain.EmojiPickerPreferences
import me.foxtails.palustris.domain.EmojiPickerPreferencesRepository
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.ValidatedUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmojiCatalogViewModelTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "account")
    private val cancellationAccount =
        AccountId(Connection("https://example.org", Protocol.MASTODON), "owner")
    private val oldEmoji = emoji("old")
    private val newEmoji = emoji("new")
    private val now = 2 * DAY

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun freshSnapshotAvoidsNetworkRequest() = runTest {
        val repository = FakeRepository(EmojiCatalogSnapshot(listOf(oldEmoji), now - HOUR))
        val model = model(repository)

        model.loadIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf(oldEmoji), model.state.value.items)
        assertEquals(0, repository.refreshCalls)
        assertFalse(model.state.value.initialLoading)
        assertFalse(model.state.value.refreshing)
    }

    @Test
    fun staleSnapshotIsPublishedBeforeRefreshCompletes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(EmojiCatalogSnapshot(listOf(oldEmoji), now - DAY - HOUR), gate = gate)
        val model = model(repository)

        model.loadIfNeeded()
        runCurrent()

        assertEquals(listOf(oldEmoji), model.state.value.items)
        assertTrue(model.state.value.refreshing)
        assertEquals(1, repository.refreshCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(newEmoji), model.state.value.items)
        assertNull(model.state.value.error)
    }

    @Test
    fun staleSnapshotSurvivesOrdinaryRefreshFailure() = runTest {
        val repository = FakeRepository(
            EmojiCatalogSnapshot(listOf(oldEmoji), now - DAY - HOUR),
            failure = IllegalStateException("offline"),
        )
        val model = model(repository)

        model.loadIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf(oldEmoji), model.state.value.items)
        assertFalse(model.state.value.refreshing)
        assertNotNull(model.state.value.error)
    }

    @Test
    fun unsupportedRefreshHidesCatalogButKeepsStateUsable() = runTest {
        val repository = FakeRepository(
            EmojiCatalogSnapshot(listOf(oldEmoji), now - DAY - HOUR),
            failure = SourceError.Unsupported("emoji"),
        )
        val model = model(repository)

        model.loadIfNeeded()
        advanceUntilIdle()

        assertTrue(model.state.value.unsupported)
        assertTrue(model.state.value.items.isEmpty())
        assertFalse(model.state.value.initialLoading)
    }

    @Test
    fun successfulRefreshPrunesOnlyMissingServerGroups() = runTest {
        val preferences = FakePreferencesRepository(
            EmojiPickerPreferences(
                collapsedGroups = setOf("server:missing", EmojiPickerGroupIds.server(null)),
                pinnedGroups = listOf("server:missing", EmojiPickerGroupIds.server(null)),
                pinnedEmoji = listOf(":old:", ":post-only:", "🎉"),
            ),
        )
        val model = model(
            FakeRepository(EmojiCatalogSnapshot(listOf(oldEmoji), now - DAY - HOUR)),
            preferences,
        )

        model.loadIfNeeded()
        advanceUntilIdle()

        assertEquals(setOf(EmojiPickerGroupIds.server(null)), preferences.preferences.collapsedGroups)
        assertEquals(listOf(EmojiPickerGroupIds.server(null)), preferences.preferences.pinnedGroups)
        assertEquals(listOf(":post-only:", "🎉"), preferences.preferences.pinnedEmoji)
    }

    @Test
    fun failedRefreshDoesNotPruneServerGroups() = runTest {
        val initial = EmojiPickerPreferences(
            collapsedGroups = setOf("server:missing"),
            pinnedGroups = listOf("server:missing"),
        )
        val preferences = FakePreferencesRepository(initial)
        val model = model(
            FakeRepository(
                EmojiCatalogSnapshot(listOf(oldEmoji), now - DAY - HOUR),
                failure = IllegalStateException("offline"),
            ),
            preferences,
        )

        model.loadIfNeeded()
        advanceUntilIdle()

        assertEquals(initial, preferences.preferences)
    }

    @Test
    fun cancelledReadNeverRefreshes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = GatedCatalogRepository()
            repository.readGate = CompletableDeferred()
            val model = cancellationModel(repository)

            model.loadIfNeeded()
            advanceUntilIdle()
            // The cached read is in flight. Stopping cancels it. Cancellation stays
            // cancellation: no refresh follows and no error is reported.
            model.stop()
            advanceUntilIdle()

            assertTrue(repository.refreshCalls.isEmpty())
            assertFalse(model.state.value.initialLoading)
            assertFalse(model.state.value.refreshing)
            assertNull(model.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedReadFallsBackToRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = GatedCatalogRepository()
            repository.readGate = CompletableDeferred<EmojiCatalogSnapshot?>().apply {
                completeExceptionally(java.io.IOException("cache down"))
            }
            val model = cancellationModel(repository)

            model.loadIfNeeded()
            advanceUntilIdle()

            assertEquals(listOf(cancellationAccount), repository.refreshCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedPinWriteClearsPendingKeepsPreferencesAndReportsFailure() = runTest {
        val preferences = FakePreferencesRepository(failure = java.io.IOException("disk"))
        val model = model(FakeRepository(EmojiCatalogSnapshot(listOf(oldEmoji), now - HOUR)), preferences)
        runCurrent()

        model.togglePinnedEmoji(":old:")
        assertEquals(setOf(":old:"), model.state.value.pendingPins)
        advanceUntilIdle()

        assertTrue(model.state.value.pendingPins.isEmpty())
        assertTrue(model.state.value.pinFailed)
        assertTrue(model.state.value.preferences.pinnedEmoji.isEmpty())
        model.loadIfNeeded()
        assertFalse(model.state.value.pinFailed)
    }

    @Test
    fun pinWriteStaysPendingUntilTheWriteEndsAndIgnoresRepeats() = runTest {
        val gate = CompletableDeferred<Unit>()
        val preferences = FakePreferencesRepository(gate = gate)
        val model = model(FakeRepository(EmojiCatalogSnapshot(listOf(oldEmoji), now - HOUR)), preferences)
        runCurrent()

        model.togglePinnedEmoji(":old:")
        model.togglePinnedEmoji(":old:")
        runCurrent()
        assertEquals(setOf(":old:"), model.state.value.pendingPins)
        assertEquals(0, preferences.updates)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, preferences.updates)
        assertTrue(model.state.value.pendingPins.isEmpty())
        assertFalse(model.state.value.pinFailed)
        assertEquals(listOf(":old:"), preferences.preferences.pinnedEmoji)
    }

    @Test
    fun stoppingTheModelClearsTransientPinState() = runTest {
        val gate = CompletableDeferred<Unit>()
        val model = model(FakeRepository(null), FakePreferencesRepository(gate = gate))
        runCurrent()
        model.togglePinnedEmoji(":old:")
        runCurrent()

        model.stop()

        assertTrue(model.state.value.pendingPins.isEmpty())
        assertFalse(model.state.value.pinFailed)
        assertEquals(account, model.state.value.accountId)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun emptyCatalogIsEmptyWithoutAnError() = runTest {
        val model = model(FakeRepository(EmojiCatalogSnapshot(emptyList(), now - HOUR)))

        model.loadIfNeeded()
        advanceUntilIdle()

        assertTrue(model.state.value.empty)
        assertTrue(model.state.value.items.isEmpty())
        assertNull(model.state.value.error)
        assertTrue(model.state.value.hasSnapshot)
    }

    @Test
    fun failingFirstLoadReportsErrorAndRetryRecovers() = runTest {
        val repository = FakeRepository(null, failure = IllegalStateException("offline"))
        val model = model(repository)

        model.loadIfNeeded()
        advanceUntilIdle()
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.hasSnapshot)
        assertTrue(model.state.value.items.isEmpty())

        repository.failure = null
        model.retry()
        advanceUntilIdle()

        assertNull(model.state.value.error)
        assertTrue(model.state.value.hasSnapshot)
        assertEquals(listOf(":new:"), model.state.value.items.map { it.submissionValue })
    }

    @Test
    fun groupCollapseTogglesAndSurvivesAFailedWrite() = runTest {
        val preferences = FakePreferencesRepository()
        val model = model(FakeRepository(null), preferences)
        runCurrent()

        model.toggleGroupCollapsed(EmojiPickerGroupIds.Unicode)
        advanceUntilIdle()
        assertEquals(setOf(EmojiPickerGroupIds.Unicode), preferences.preferences.collapsedGroups)
        model.toggleGroupCollapsed(EmojiPickerGroupIds.Unicode)
        advanceUntilIdle()
        assertTrue(preferences.preferences.collapsedGroups.isEmpty())

        preferences.failure = java.io.IOException("disk")
        model.toggleGroupCollapsed(EmojiPickerGroupIds.Unicode)
        advanceUntilIdle()
        assertTrue(preferences.preferences.collapsedGroups.isEmpty())
    }

    @Test
    fun pinnedEmojiKeepPinOrderAcrossUnpinAndRepin() = runTest {
        val preferences = FakePreferencesRepository()
        val model = model(FakeRepository(null), preferences)
        runCurrent()

        listOf(":a:", ":b:", ":c:", ":a:", ":a:").forEach {
            model.togglePinnedEmoji(it)
            advanceUntilIdle()
        }

        assertEquals(listOf(":b:", ":c:", ":a:"), preferences.preferences.pinnedEmoji)
    }

    @Test
    fun replacedAccountHasItsOwnScopeAndWritesOnlyItsOwnPreferences() = runTest {
        val store = AccountPreferencesRepository()
        val first = accountModel(account, store)
        val second = accountModel(cancellationAccount, store)
        runCurrent()

        first.togglePinnedEmoji(":a:")
        advanceUntilIdle()
        first.stop()
        first.togglePinnedEmoji(":ignored:")
        second.togglePinnedEmoji(":b:")
        advanceUntilIdle()

        assertEquals(listOf(":a:"), store.values.getValue(account).pinnedEmoji)
        assertEquals(listOf(":b:"), store.values.getValue(cancellationAccount).pinnedEmoji)
        assertEquals(account, first.state.value.accountId)
        assertEquals(cancellationAccount, second.state.value.accountId)
        assertTrue(first.state.value.scope !== second.state.value.scope)
    }

    private fun accountModel(accountId: AccountId, preferences: EmojiPickerPreferencesRepository) =
        EmojiCatalogViewModel(
            accountId = accountId,
            source = CatalogSource(),
            repository = FakeRepository(null),
            clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
            preferencesRepository = preferences,
        )

    private fun model(
        repository: EmojiCatalogRepository,
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
    ) = EmojiCatalogViewModel(
        accountId = account,
        source = CatalogSource(),
        repository = repository,
        clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
        preferencesRepository = preferences,
    )

    private fun cancellationModel(repository: GatedCatalogRepository) = EmojiCatalogViewModel(
        accountId = cancellationAccount,
        source = EmptySource,
        repository = repository,
        clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        preferencesRepository = InertPreferencesRepository(),
    )

    private fun emoji(shortcode: String) = CustomEmoji(
        shortcode = shortcode,
        animatedUrl = ValidatedUrl.https("https://cdn.example/$shortcode.gif"),
        staticUrl = ValidatedUrl.https("https://cdn.example/$shortcode.png"),
        submissionValue = ":$shortcode:",
    )

    private class FakeRepository(
        private var snapshot: EmojiCatalogSnapshot?,
        private val gate: CompletableDeferred<Unit>? = null,
        var failure: Exception? = null,
    ) : EmojiCatalogRepository {
        var refreshCalls = 0
            private set

        override suspend fun read(accountId: AccountId): EmojiCatalogSnapshot? = snapshot

        override suspend fun refresh(accountId: AccountId, source: SocialSource): EmojiCatalogSnapshot {
            refreshCalls += 1
            gate?.await()
            failure?.let { throw it }
            snapshot = EmojiCatalogSnapshot(listOf(emoji("new")), 2 * DAY)
            return snapshot!!
        }

        override suspend fun remove(accountId: AccountId) {
            snapshot = null
        }

        private fun emoji(shortcode: String) = CustomEmoji(
            shortcode = shortcode,
            animatedUrl = ValidatedUrl.https("https://cdn.example/$shortcode.gif"),
            staticUrl = ValidatedUrl.https("https://cdn.example/$shortcode.png"),
            submissionValue = ":$shortcode:",
        )
    }

    private class GatedCatalogRepository : EmojiCatalogRepository {
        var readGate: CompletableDeferred<EmojiCatalogSnapshot?>? = null
        var readResult: EmojiCatalogSnapshot? = null
        val refreshCalls = mutableListOf<AccountId>()

        override suspend fun read(accountId: AccountId): EmojiCatalogSnapshot? {
            val gate = readGate
            if (gate != null) return gate.await()
            return readResult
        }

        override suspend fun refresh(accountId: AccountId, source: SocialSource): EmojiCatalogSnapshot {
            refreshCalls += accountId
            return EmojiCatalogSnapshot(emptyList(), 0L)
        }

        override suspend fun remove(accountId: AccountId) = Unit
    }

    private class CatalogSource : SocialSource {
        override val capabilities = ServerCapabilities()
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
    }

    private object EmptySource : SocialSource {
        override val capabilities = ServerCapabilities()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
    }

    private class FakePreferencesRepository(
        initial: EmojiPickerPreferences = EmojiPickerPreferences(),
        var failure: Exception? = null,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : EmojiPickerPreferencesRepository {
        var preferences = initial
        var updates = 0
            private set

        override fun observe(accountId: AccountId) = kotlinx.coroutines.flow.flowOf(preferences)

        override suspend fun update(
            accountId: AccountId,
            transform: (EmojiPickerPreferences) -> EmojiPickerPreferences,
        ) {
            gate?.await()
            failure?.let { throw it }
            updates += 1
            preferences = transform(preferences)
        }

        override suspend fun remove(accountId: AccountId) = Unit
    }

    private class AccountPreferencesRepository : EmojiPickerPreferencesRepository {
        val values = mutableMapOf<AccountId, EmojiPickerPreferences>()

        override fun observe(accountId: AccountId): Flow<EmojiPickerPreferences> =
            kotlinx.coroutines.flow.flowOf(values[accountId] ?: EmojiPickerPreferences())

        override suspend fun update(
            accountId: AccountId,
            transform: (EmojiPickerPreferences) -> EmojiPickerPreferences,
        ) {
            values[accountId] = transform(values[accountId] ?: EmojiPickerPreferences())
        }

        override suspend fun remove(accountId: AccountId) {
            values.remove(accountId)
        }
    }

    private class InertPreferencesRepository : EmojiPickerPreferencesRepository {
        private val state = MutableStateFlow(EmojiPickerPreferences())

        override fun observe(accountId: AccountId): Flow<EmojiPickerPreferences> = state

        override suspend fun update(
            accountId: AccountId,
            transform: (EmojiPickerPreferences) -> EmojiPickerPreferences,
        ) {
            state.value = transform(state.value)
        }

        override suspend fun remove(accountId: AccountId) = Unit
    }

    private companion object {
        const val HOUR = 60L * 60L * 1000L
        const val DAY = 24L * HOUR
    }
}
