@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.foxtails.palustris.ui.posts

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
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
import me.foxtails.palustris.ui.UiStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostOwnActionsTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val me = AccountId(connection, "me")
    private val other = AccountId(connection, "other")

    private fun owned(author: AccountId, id: String = "p", wrapped: String? = null) = OwnedPost(
        me,
        Post(
            id = EntityId(connection.origin, id),
            author = Account(author, "A", "@a@example.org"),
            text = "hola",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            actionTargetId = wrapped?.let { EntityId(connection.origin, it) },
        ),
        sessionRevision = 1L,
    )

    private class FakeSource : SocialSource {
        override val capabilities = ServerCapabilities()
        val deleted = mutableListOf<EntityId>()
        val translated = mutableListOf<Pair<EntityId, String>>()
        var failure: Exception? = null
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun delete(id: EntityId) {
            failure?.let { throw it }
            deleted += id
        }
        override suspend fun translate(id: EntityId, targetLanguage: String): Translation {
            failure?.let { throw it }
            translated += id to targetLanguage
            return Translation("hello", sourceLanguage = "es", provider = "Test")
        }
    }

    @Test
    fun deleteActsOnTheWrappedPostAndForwardsTheConfirmedDeletion() = runTest {
        val source = FakeSource()
        val forwarded = mutableListOf<OwnedPost>()
        val popup = PostPopupOwner(me, 1L, source, this, onPostDeleted = { forwarded += it }, uiStrings = UiStrings.Default)
        val target = owned(me, id = "wrapper", wrapped = "inner")

        popup.open(target, Rect.Zero)
        popup.deleteOwnPost()
        advanceUntilIdle()

        assertEquals(listOf(EntityId(connection.origin, "inner")), source.deleted)
        assertEquals(listOf(target), forwarded)
        assertNull(popup.target)
    }

    @Test
    fun deleteRefusesSomeoneElsesPost() = runTest {
        val source = FakeSource()
        val popup = PostPopupOwner(me, 1L, source, this, uiStrings = UiStrings.Default)

        popup.open(owned(other), Rect.Zero)
        advanceUntilIdle()
        popup.deleteOwnPost()
        advanceUntilIdle()

        assertTrue(source.deleted.isEmpty())
    }

    @Test
    fun aFailedDeleteKeepsThePostAndShowsTheError() = runTest {
        val source = FakeSource().apply { failure = SourceError.NetworkUnavailable }
        val forwarded = mutableListOf<OwnedPost>()
        val popup = PostPopupOwner(me, 1L, source, this, onPostDeleted = { forwarded += it }, uiStrings = UiStrings.Default)

        popup.open(owned(me), Rect.Zero)
        popup.deleteOwnPost()
        advanceUntilIdle()

        assertTrue(forwarded.isEmpty())
        assertEquals(false, popup.ownPost.deleting)
        assertTrue(popup.ownPost.error != null)
        assertTrue(popup.target != null)
    }

    @Test
    fun retiredOwnerIgnoresDelete() = runTest {
        val source = FakeSource()
        val popup = PostPopupOwner(me, 1L, source, this, uiStrings = UiStrings.Default)
        popup.open(owned(me), Rect.Zero)
        popup.retire()

        popup.deleteOwnPost()
        advanceUntilIdle()

        assertTrue(source.deleted.isEmpty())
    }

    @Test
    fun translationIsCachedPerPostAndToggleKeepsIt() = runTest {
        val source = FakeSource()
        val translations = PostTranslationOwner(me, 1L, source, this, UiStrings.Default)
        val post = owned(other, wrapped = "inner")
        val target = EntityId(connection.origin, "inner")

        translations.translate(post, "en")
        assertEquals(TranslationEntry.Loading, translations.entry(target))
        advanceUntilIdle()
        translations.translate(post, "en")
        advanceUntilIdle()

        assertEquals(listOf(target to "en"), source.translated)
        assertEquals(false, (translations.entry(target) as TranslationEntry.Ready).showOriginal)
        translations.toggleOriginal(target)
        assertEquals(true, (translations.entry(target) as TranslationEntry.Ready).showOriginal)
    }

    @Test
    fun aFailedTranslationCanBeDismissedAndRetried() = runTest {
        val source = FakeSource().apply { failure = SourceError.Unsupported("translate") }
        val translations = PostTranslationOwner(me, 1L, source, this, UiStrings.Default)
        val post = owned(other)

        translations.translate(post, "en")
        advanceUntilIdle()
        assertTrue(translations.entry(post.post.id) is TranslationEntry.Failed)

        translations.dismissFailure(post.post.id)
        assertNull(translations.entry(post.post.id))
        source.failure = null
        translations.translate(post, "en")
        advanceUntilIdle()
        assertTrue(translations.entry(post.post.id) is TranslationEntry.Ready)
    }

    @Test
    fun retiredTranslationOwnerDropsEntriesAndIgnoresRequests() = runTest {
        val source = FakeSource()
        val translations = PostTranslationOwner(me, 1L, source, this, UiStrings.Default)
        val post = owned(other)
        translations.translate(post, "en")
        advanceUntilIdle()

        translations.retire()
        translations.translate(post, "en")
        advanceUntilIdle()

        assertNull(translations.entry(post.post.id))
        assertEquals(1, source.translated.size)
    }

    @Test
    fun translationRejectsAStaleSessionRevision() = runTest {
        val source = FakeSource()
        val translations = PostTranslationOwner(me, 2L, source, this, UiStrings.Default)

        translations.translate(owned(other), "en")
        advanceUntilIdle()

        assertTrue(source.translated.isEmpty())
    }
}
