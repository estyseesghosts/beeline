package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnPostTest {
    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val me = AccountId(connection, "me")
    private val other = AccountId(connection, "other")
    private val meAccount = Account(me, "Me", "@me@example.org")
    private val otherAccount = Account(other, "Other", "@other@example.org")

    private fun post(
        id: String,
        author: Account,
        resharedBy: Account? = null,
        wrapped: String? = null,
        replyTo: String? = null,
        audience: Audience = Audience.Public,
        language: String? = null,
        text: String = "text",
    ) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = text,
        publishedAtEpochMillis = 0,
        audience = audience,
        resharedBy = resharedBy,
        actionTargetId = wrapped?.let { EntityId(connection.origin, it) },
        replyTo = replyTo?.let { EntityId(connection.origin, it) },
        language = language,
    )

    @Test
    fun aPlainPostIsOwnedByItsAuthorOnly() {
        assertTrue(post("1", meAccount).isOwnedBy(me))
        assertFalse(post("2", otherAccount).isOwnedBy(me))
    }

    @Test
    fun aRepostOfYourOwnPostIsOwnedAndActsOnTheWrappedPost() {
        val repost = post("wrapper", meAccount, resharedBy = otherAccount, wrapped = "inner")

        assertTrue(repost.isOwnedBy(me))
        assertEquals(EntityId(connection.origin, "inner"), repost.effectiveTargetId())
    }

    @Test
    fun yourRepostOfSomeoneElsesPostIsNotOwned() {
        val repost = post("wrapper", otherAccount, resharedBy = meAccount, wrapped = "inner")

        assertFalse(repost.isOwnedBy(me))
    }

    @Test
    fun deletionRemovesTheRowAndAnyRepostWrapperAndDecrementsTheParentReplyCount() {
        val parent = post("parent", otherAccount).let {
            it.copy(interactionCounts = PostInteractionCounts(replyCount = 3))
        }
        val reply = post("reply", meAccount, replyTo = "parent")
        val wrapper = post("wrapper", meAccount, resharedBy = otherAccount, wrapped = "reply")
        val unrelated = post("unrelated", otherAccount)

        val after = listOf(parent, reply, wrapper, unrelated).afterDeletion(reply)

        assertEquals(listOf("parent", "unrelated"), after.map { it.id.value })
        assertEquals(2, after.first().interactionCounts.replyCount)
    }

    @Test
    fun deletionKeepsAnUnknownReplyCountUnknown() {
        val parent = post("parent", otherAccount)
        val reply = post("reply", meAccount, replyTo = "parent")

        assertEquals(null, listOf(parent).afterDeletion(reply).single().interactionCounts.replyCount)
    }

    @Test
    fun translationNeedsASupportedServerAndADifferentLanguage() {
        val supported = TranslationCapability(CapabilityStatus.Supported)

        assertTrue(post("1", otherAccount, language = "fr").canTranslateTo(supported, "en"))
        assertTrue(post("1", otherAccount, language = null).canTranslateTo(supported, "en"))
        assertFalse(post("1", otherAccount, language = "en").canTranslateTo(supported, "en"))
        assertFalse(post("1", otherAccount, language = "en-GB").canTranslateTo(supported, "en"))
        assertFalse(post("1", otherAccount, language = "fr").canTranslateTo(TranslationCapability(CapabilityStatus.Unknown), "en"))
        assertFalse(post("1", otherAccount, language = "fr").canTranslateTo(TranslationCapability(CapabilityStatus.Denied), "en"))
        assertFalse(post("1", otherAccount, language = "fr", text = "").canTranslateTo(supported, "en"))
    }

    @Test
    fun publicOnlyTranslationHidesPrivateAndDirectPosts() {
        val publicOnly = TranslationCapability(CapabilityStatus.Supported, publicOnly = true)

        assertTrue(post("1", otherAccount, audience = Audience.Unlisted).canTranslateTo(publicOnly, "en"))
        assertFalse(post("1", otherAccount, audience = Audience.Followers).canTranslateTo(publicOnly, "en"))
        assertFalse(post("1", otherAccount, audience = Audience.Direct).canTranslateTo(publicOnly, "en"))
    }
}
