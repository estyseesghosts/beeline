package me.foxtails.palustris.ui.posts

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostRevealOwnerTest {
    private val origin = "https://example.org"

    private fun owned(id: String, account: AccountId = accountId, target: String? = null): OwnedPost {
        val author = Account(accountId, "A", "@a@example.org")
        return OwnedPost(
            account,
            Post(
                id = EntityId(origin, id),
                author = author,
                text = id,
                publishedAtEpochMillis = 0,
                audience = Audience.Public,
                actionTargetId = target?.let { EntityId(origin, it) },
            ),
        )
    }

    private val accountId = AccountId(Connection(origin, Protocol.MASTODON), "me")

    @Test
    fun expandingTheWarningAlsoRevealsTheMedia() {
        val owner = PostRevealOwner()
        val post = owned("a")
        assertFalse(owner.warningExpanded(post))
        owner.setWarningExpanded(post, true)
        assertTrue(owner.warningExpanded(post))
        assertTrue(owner.mediaRevealed(post))
    }

    @Test
    fun collapsingTheWarningCoversTheMediaAgain() {
        val owner = PostRevealOwner()
        val post = owned("a")
        owner.setWarningExpanded(post, true)
        owner.setWarningExpanded(post, false)
        assertFalse(owner.warningExpanded(post))
        assertFalse(owner.mediaRevealed(post))
    }

    @Test
    fun revealingMediaDoesNotExpandTheWarning() {
        val owner = PostRevealOwner()
        val post = owned("a")
        owner.revealMedia(post)
        assertTrue(owner.mediaRevealed(post))
        assertFalse(owner.warningExpanded(post))
    }

    @Test
    fun aRepostRowAndItsWrappedPostShareOneEntry() {
        val owner = PostRevealOwner()
        owner.setWarningExpanded(owned("boost", target = "orig"), true)
        assertTrue(owner.warningExpanded(owned("orig")))
    }

    @Test
    fun anotherAccountDoesNotSeeTheReveal() {
        val owner = PostRevealOwner()
        owner.revealMedia(owned("a"))
        assertFalse(owner.mediaRevealed(owned("a", account = AccountId(Connection(origin, Protocol.MASTODON), "other"))))
    }

    @Test
    fun theOldestPostIsDroppedAtTheBound() {
        val owner = PostRevealOwner(limit = 3)
        (1..4).forEach { owner.revealMedia(owned("p$it")) }
        assertEquals(3, owner.size)
        assertFalse(owner.mediaRevealed(owned("p1")))
        assertTrue(owner.mediaRevealed(owned("p4")))
    }

    @Test
    fun retiringClearsEveryEntry() {
        val owner = PostRevealOwner()
        owner.revealMedia(owned("a"))
        owner.retire()
        assertFalse(owner.mediaRevealed(owned("a")))
        assertEquals(0, owner.size)
    }
}
