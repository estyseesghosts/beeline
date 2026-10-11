@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.foxtails.palustris.ui.posts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.Translation
import me.foxtails.palustris.domain.TranslationCapability
import me.foxtails.palustris.ui.UiStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The automatic translation rules of [PostTranslationOwner]: what it sends, how often and when it stops. */
class PostTranslationOwnerTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val me = AccountId(connection, "me")
    private val author = AccountId(connection, "author")

    private fun owned(
        id: String,
        audience: Audience = Audience.Public,
        language: String? = null,
        revision: Long = 1L,
    ) = OwnedPost(
        me,
        Post(
            id = EntityId(connection.origin, id),
            author = Account(author, "A", "@a@example.org"),
            text = "hola",
            publishedAtEpochMillis = 0,
            audience = audience,
            language = language,
        ),
        sessionRevision = revision,
    )

    private fun id(value: String) = EntityId(connection.origin, value)

    private class FakeSource(supported: Boolean = true) : SocialSource {
        override val capabilities = ServerCapabilities(
            translation = TranslationCapability(if (supported) CapabilityStatus.Supported else CapabilityStatus.Unsupported),
        )
        val translated = mutableListOf<EntityId>()
        var failure: Exception? = null
        var detected: String? = "es"
        var gate: CompletableDeferred<Unit>? = null
        var running = 0
        var peak = 0

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun translate(id: EntityId, targetLanguage: String): Translation {
            running++
            peak = maxOf(peak, running)
            try {
                gate?.await()
                failure?.let { throw it }
                translated += id
                return Translation("hello", sourceLanguage = detected, provider = "Test")
            } finally {
                running--
            }
        }
    }

    private fun TestScope.owner(source: FakeSource, now: () -> Long = { 0L }, automatic: Boolean = true) =
        PostTranslationOwner(me, 1L, source, this, UiStrings.Default, now).also { it.applyAutomaticSetting(automatic) }

    @Test
    fun settingOffRequestsNothing() = runTest {
        val source = FakeSource()
        val owner = owner(source, automatic = false)

        owner.requestAutomatic(owned("a"), "en")
        advanceUntilIdle()

        assertTrue(source.translated.isEmpty())
        assertNull(owner.entry(id("a")))
    }

    @Test
    fun aPostInTheTargetLanguageRequestsNothing() = runTest {
        val source = FakeSource()
        val owner = owner(source)

        owner.requestAutomatic(owned("a", language = "en"), "en")
        advanceUntilIdle()

        assertTrue(source.translated.isEmpty())
    }

    @Test
    fun aServerWithoutTranslationRequestsNothing() = runTest {
        val source = FakeSource(supported = false)
        val owner = owner(source)

        owner.requestAutomatic(owned("a"), "en")
        advanceUntilIdle()

        assertTrue(source.translated.isEmpty())
    }

    @Test
    fun aPostOnlySomePeopleCanReadIsNeverSent() = runTest {
        val source = FakeSource()
        val owner = owner(source)

        owner.requestAutomatic(owned("followers", audience = Audience.Followers), "en")
        owner.requestAutomatic(owned("direct", audience = Audience.Direct), "en")
        owner.requestAutomatic(owned("unlisted", audience = Audience.Unlisted), "en")
        advanceUntilIdle()

        assertEquals(listOf(id("unlisted")), source.translated)
    }

    @Test
    fun oneRequestForEachPostAndTheTranslationShowsWithoutATap() = runTest {
        val source = FakeSource()
        val owner = owner(source)
        val post = owned("a")

        owner.requestAutomatic(post, "en")
        advanceUntilIdle()
        owner.requestAutomatic(post, "en")
        owner.requestAutomatic(post, "en")
        advanceUntilIdle()

        assertEquals(listOf(id("a")), source.translated)
        assertTrue(owner.entry(id("a")) is TranslationEntry.Ready)
    }

    @Test
    fun aFailedAutomaticRequestIsQuietAndNotRepeatedButTheManualRowRetries() = runTest {
        val source = FakeSource().apply { failure = SourceError.NetworkUnavailable }
        val owner = owner(source)
        val post = owned("a")

        owner.requestAutomatic(post, "en")
        advanceUntilIdle()
        assertNull(owner.entry(id("a")))
        source.failure = null
        owner.requestAutomatic(post, "en")
        advanceUntilIdle()
        assertTrue(source.translated.isEmpty())

        owner.translate(post, "en")
        advanceUntilIdle()
        assertTrue(owner.entry(id("a")) is TranslationEntry.Ready)
    }

    @Test
    fun anAnswerInTheReadersLanguageShowsNothingAndIsNotAskedAgain() = runTest {
        val source = FakeSource().apply { detected = "en" }
        val owner = owner(source)
        val post = owned("a")

        owner.requestAutomatic(post, "en")
        advanceUntilIdle()
        owner.requestAutomatic(post, "en")
        advanceUntilIdle()

        assertEquals(TranslationEntry.SameLanguage, owner.entry(id("a")))
        assertEquals(1, source.translated.size)
    }

    @Test
    fun atMostTwoRequestsRunAtOnceAndTheRestWaitInOrder() = runTest {
        val source = FakeSource().apply { gate = CompletableDeferred() }
        val owner = owner(source)

        (1..5).forEach { number -> launch { owner.requestAutomatic(owned("p$number"), "en") } }
        runCurrent()
        assertEquals(2, source.running)

        source.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, source.peak)
        assertEquals((1..5).map { id("p$it") }, source.translated)
    }

    @Test
    fun aRowThatLeavesDropsItsQueuedRequestAndMayAskAgainLater() = runTest {
        val source = FakeSource().apply { gate = CompletableDeferred() }
        val owner = owner(source)
        launch { owner.requestAutomatic(owned("a"), "en") }
        launch { owner.requestAutomatic(owned("b"), "en") }
        val queued = launch { owner.requestAutomatic(owned("c"), "en") }
        runCurrent()

        queued.cancel()
        source.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(id("a"), id("b")), source.translated)

        owner.requestAutomatic(owned("c"), "en")
        advanceUntilIdle()
        assertEquals(listOf(id("a"), id("b"), id("c")), source.translated)
    }

    @Test
    fun aBusyAnswerPausesAutomaticRequestsUntilTheTimerEnds() = runTest {
        var now = 0L
        val source = FakeSource().apply { failure = SourceError.ResourceLimit("translate.busy") }
        val owner = owner(source, now = { now })

        owner.requestAutomatic(owned("a"), "en")
        advanceUntilIdle()
        source.failure = null
        owner.requestAutomatic(owned("b"), "en")
        advanceUntilIdle()
        assertTrue(source.translated.isEmpty())

        now = PostTranslationOwner.PAUSE_MILLIS
        owner.requestAutomatic(owned("b"), "en")
        advanceUntilIdle()
        assertEquals(listOf(id("b")), source.translated)
    }

    @Test
    fun aRateLimitedAnswerPausesToo() = runTest {
        val source = FakeSource().apply { failure = SourceError.RateLimited }
        val owner = owner(source)

        owner.requestAutomatic(owned("a"), "en")
        advanceUntilIdle()
        source.failure = null
        owner.requestAutomatic(owned("b"), "en")
        advanceUntilIdle()

        assertTrue(source.translated.isEmpty())
    }

    @Test
    fun aServerThatCannotTranslateStopsAutomaticRequestsForTheSession() = runTest {
        val source = FakeSource().apply { failure = SourceError.Unsupported("translate") }
        val owner = owner(source)

        owner.requestAutomatic(owned("a"), "en")
        advanceUntilIdle()
        source.failure = null
        owner.requestAutomatic(owned("b"), "en")
        advanceUntilIdle()

        assertTrue(source.translated.isEmpty())
        assertNull(owner.entry(id("a")))
    }

    @Test
    fun turningTheSettingOffStopsNewRequestsAndKeepsStoredTranslations() = runTest {
        val source = FakeSource()
        val owner = owner(source)
        owner.requestAutomatic(owned("a"), "en")
        advanceUntilIdle()

        owner.applyAutomaticSetting(false)
        owner.requestAutomatic(owned("b"), "en")
        advanceUntilIdle()

        assertEquals(listOf(id("a")), source.translated)
        assertNotNull(owner.entry(id("a")))
    }

    @Test
    fun entriesAreBoundedAndTheOldestGoFirst() = runTest {
        val source = FakeSource()
        val owner = owner(source)
        val total = PostTranslationOwner.MAX_ENTRIES + 20

        (1..total).forEach { number -> owner.translate(owned("p$number"), "en") }
        advanceUntilIdle()

        assertNull(owner.entry(id("p1")))
        assertNotNull(owner.entry(id("p$total")))
    }

    @Test
    fun retiringTheOwnerReleasesEveryEntryAndIgnoresLaterRequests() = runTest {
        val source = FakeSource()
        val owner = owner(source)
        owner.requestAutomatic(owned("a"), "en")
        advanceUntilIdle()

        owner.retire()
        owner.requestAutomatic(owned("b"), "en")
        advanceUntilIdle()

        assertNull(owner.entry(id("a")))
        assertEquals(listOf(id("a")), source.translated)
    }

    @Test
    fun aPostFromAnotherSessionRevisionIsIgnored() = runTest {
        val source = FakeSource()
        val owner = owner(source)

        owner.requestAutomatic(owned("a", revision = 2L), "en")
        advanceUntilIdle()

        assertTrue(source.translated.isEmpty())
    }
}
