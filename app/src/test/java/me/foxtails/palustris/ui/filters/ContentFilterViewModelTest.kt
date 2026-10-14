package me.foxtails.palustris.ui.filters

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ClientFilterMatcher
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ContentFilter
import me.foxtails.palustris.domain.ContentFilterDraft
import me.foxtails.palustris.domain.ContentFilterInput
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.ContentFilterSet
import me.foxtails.palustris.domain.FilterPattern
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.ModerationListQuery
import me.foxtails.palustris.domain.ModerationPage
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContentFilterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val accountId = AccountId(Connection("https://example.org", Protocol.MASTODON), "owner")
    private val filter = ContentFilter("f1", "TV", listOf(FilterPattern.Words(listOf("finale"))), PostFilterAction.Collapse)
    private val titled = ContentFilterSet(ContentFilterKind.Titled, listOf(filter))

    private class FilterSource(var set: ContentFilterSet, var failure: Exception? = null) : SocialSource {
        override val capabilities = ServerCapabilities()
        val saves = mutableListOf<Pair<String?, ContentFilterDraft>>()
        val deletes = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun contentFilters(): ContentFilterSet {
            failure?.let { throw it }
            return set
        }

        override suspend fun saveContentFilter(id: String?, draft: ContentFilterDraft): ContentFilterSet {
            gate?.await()
            failure?.let { throw it }
            saves += id to draft
            return set
        }

        override suspend fun deleteContentFilter(id: String): ContentFilterSet {
            failure?.let { throw it }
            deletes += id
            return set.copy(filters = set.filters.filterNot { it.id == id })
        }
    }

    private val registry = AccountSourceRegistry()

    private fun model(source: SocialSource?): ContentFilterViewModel {
        source?.let { registry.register(NotificationSyncToken(accountId, 1L), it) }
        return ContentFilterViewModel(accountId, registry)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadPublishesTheSet() = runTest(dispatcher) {
        val model = model(FilterSource(titled))

        model.load()
        advanceUntilIdle()

        assertEquals(titled, model.state.value.set)
        assertFalse(model.state.value.loading)
    }

    @Test
    fun unsupportedSourceShowsTheUnsupportedState() = runTest(dispatcher) {
        val model = model(FilterSource(titled, failure = SourceError.Unsupported("moderation.filters")))

        model.load()
        advanceUntilIdle()

        assertNull(model.state.value.set)
        assertTrue(model.state.value.unsupported)
        assertFalse(model.state.value.loading)
    }

    @Test
    fun missingSessionReportsAnErrorWithoutASet() = runTest(dispatcher) {
        val model = model(null)

        model.load()
        advanceUntilIdle()

        assertNull(model.state.value.set)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.unsupported)
    }

    @Test
    fun invalidEditorDoesNotCallTheServer() = runTest(dispatcher) {
        val source = FilterSource(titled)
        val model = model(source)
        model.load()
        advanceUntilIdle()

        model.startNew()
        model.save()
        advanceUntilIdle()

        assertEquals(FilterEditorError.MissingTitle, model.state.value.editorError)
        assertTrue(source.saves.isEmpty())
    }

    @Test
    fun savingClosesTheEditorAndKeepsItOpenOnFailure() = runTest(dispatcher) {
        val source = FilterSource(titled)
        val model = model(source)
        model.load()
        advanceUntilIdle()

        model.startEdit(filter)
        model.updateEditor { it.copy(title = "Television") }
        source.failure = SourceError.ServerError("bad")
        model.save()
        advanceUntilIdle()

        assertNotNull(model.state.value.editor)
        assertFalse(model.state.value.saving)
        assertNotNull(model.state.value.error)

        source.failure = null
        model.save()
        advanceUntilIdle()

        assertNull(model.state.value.editor)
        assertEquals("f1" to "Television", source.saves.single().let { it.first to it.second.title })
    }

    @Test
    fun editorIgnoresChangesAndDismissWhileSaving() = runTest(dispatcher) {
        val source = FilterSource(titled).apply { gate = CompletableDeferred() }
        val model = model(source)
        model.load()
        advanceUntilIdle()
        model.startEdit(filter)

        model.save()
        advanceUntilIdle()
        model.updateEditor { it.copy(title = "Changed") }
        model.dismissEditor()

        assertTrue(model.state.value.saving)
        assertEquals("TV", model.state.value.editor?.title)
        source.gate?.complete(Unit)
        advanceUntilIdle()
        assertNull(model.state.value.editor)
    }

    @Test
    fun resultFromAReplacedSourceIsDropped() = runTest(dispatcher) {
        val first = FilterSource(titled).apply { gate = CompletableDeferred() }
        val model = model(first)
        model.load()
        advanceUntilIdle()
        model.startEdit(filter)

        model.save()
        advanceUntilIdle()
        registry.register(NotificationSyncToken(accountId, 2L), FilterSource(ContentFilterSet(ContentFilterKind.Titled)))
        first.gate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(model.state.value.saving)
        assertNotNull(model.state.value.editor)
        assertEquals(titled, model.state.value.set)
    }

    @Test
    fun refusedExpressionShowsNextToTheField() = runTest(dispatcher) {
        val source = FilterSource(ContentFilterSet(ContentFilterKind.WordMute))
        val model = model(source)
        model.load()
        advanceUntilIdle()
        model.startNew()
        model.updateEditor { it.copy(mode = WordMuteMode.Advanced, expression = "/(?=x)/") }

        source.failure = SourceError.InvalidInput(ContentFilterInput.EXPRESSION)
        model.save()
        advanceUntilIdle()

        assertEquals(FilterEditorError.ExpressionRejected, model.state.value.editorError)
        assertNull(model.state.value.error)
        assertNotNull(model.state.value.editor)
    }

    @Test
    fun deleteReplacesTheSet() = runTest(dispatcher) {
        val source = FilterSource(titled)
        val model = model(source)
        model.load()
        advanceUntilIdle()

        model.delete(filter)
        advanceUntilIdle()

        assertEquals(listOf("f1"), source.deletes)
        assertTrue(model.state.value.set!!.filters.isEmpty())
        assertTrue(model.state.value.deleting.isEmpty())
    }

    @Test
    fun ownerAppliesOnlyItsAccountAndEmptiesWhenRetired() = runTest(dispatcher) {
        val phrase = ContentFilterSet(ContentFilterKind.Phrase, listOf(filter))
        val owner = ContentFilterOwner(accountId, FilterSource(titled), this)

        owner.replace(AccountId(accountId.connection, "other"), phrase)
        assertSame(ClientFilterMatcher.Empty, owner.matcher)

        owner.replace(accountId, phrase)
        assertFalse(owner.matcher === ClientFilterMatcher.Empty)

        owner.retire()
        assertSame(ClientFilterMatcher.Empty, owner.matcher)
        owner.replace(accountId, phrase)
        assertSame(ClientFilterMatcher.Empty, owner.matcher)
    }

    @Test
    fun ownerLoadsEveryAccountPageAndFollowsRelationships() = runTest(dispatcher) {
        val first = AccountId(accountId.connection, "first")
        val second = AccountId(accountId.connection, "second")
        val third = AccountId(accountId.connection, "third")
        fun entry(id: AccountId) = ModerationAccount(Account(id, id.localId, "@${id.localId}"))
        val next = ModerationCursor(accountId, ModerationListQuery(ModerationListKind.Muted), "test", "page2")
        val source = object : SocialSource by FilterSource(titled) {
            override suspend fun mutedAccounts(cursor: ModerationCursor?) =
                if (cursor == null) ModerationPage(listOf(entry(first)), next) else ModerationPage(listOf(entry(second)))
            override suspend fun blockedAccounts(cursor: ModerationCursor?): ModerationPage<ModerationAccount> = throw SourceError.NetworkUnavailable
        }
        val owner = ContentFilterOwner(accountId, source, this)

        owner.load()
        advanceUntilIdle()
        assertEquals(setOf(first, second), owner.muted)
        assertTrue(owner.blocked.isEmpty())

        owner.applyRelationship(ProfileRelationship(third, blocking = true))
        owner.applyRelationship(ProfileRelationship(first, muting = false))
        assertEquals(setOf(second), owner.muted)
        assertEquals(setOf(third), owner.blocked)

        owner.retire()
        assertTrue(owner.muted.isEmpty() && owner.blocked.isEmpty())
    }

    @Test
    fun ownerKeepsItsSnapshotWhenALoadFails() = runTest(dispatcher) {
        val source = FilterSource(ContentFilterSet(ContentFilterKind.Phrase, listOf(filter)))
        val owner = ContentFilterOwner(accountId, source, this)
        owner.load()
        advanceUntilIdle()
        val loaded = owner.matcher

        source.failure = SourceError.NetworkUnavailable
        owner.load()
        advanceUntilIdle()

        assertFalse(loaded === ClientFilterMatcher.Empty)
        assertSame(loaded, owner.matcher)
    }
}
