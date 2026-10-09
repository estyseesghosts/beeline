package me.foxtails.palustris.ui.posts

import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.shell.ShellOverlayPresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionBubbleOwnershipTest {
    private val middle = Rect(400f, 1200f, 460f, 1260f)

    // Account-bound target: the overlay presenter owns who may open and select.
    private val owner = AccountId(Connection("https://example.org", Protocol.MASTODON), "owner")
    private val other = AccountId(Connection("https://example.org", Protocol.MASTODON), "other")

    private fun post(by: AccountId, revision: Long) = OwnedPost(
        fetchedBy = by,
        post = Post(
            id = EntityId("https://example.org", "p"),
            author = Account(by, "Author", "@author@example.org"),
            text = "Text",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
        ),
        sessionRevision = revision,
    )

    private fun presenter() = ShellOverlayPresenter().apply {
        account = Account(owner, "Owner", "@owner@example.org")
        sessionRevision = 4L
        reactionMutation = CapabilityStatus.Supported
    }

    @Test
    fun presenterOpensOnlyForTheCurrentAccountAndRevision() {
        val overlay = presenter()
        val handler: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> }

        overlay.openReactionBubble(post(other, 4L), middle, handler)
        assertNull(overlay.postActionBubbleTarget)
        overlay.openReactionBubble(post(owner, 3L), middle, handler)
        assertNull(overlay.postActionBubbleTarget)
        overlay.reactionMutation = CapabilityStatus.Unsupported
        overlay.openReactionBubble(post(owner, 4L), middle, handler)
        assertNull(overlay.postActionBubbleTarget)

        overlay.reactionMutation = CapabilityStatus.Supported
        overlay.openReactionBubble(post(owner, 4L), middle, handler)
        assertTrue(overlay.postActionBubbleTarget is PostActionBubbleTarget.Reaction)
    }

    @Test
    fun accountAndSessionChangesClearTheTargetAndHandler() {
        val overlay = presenter()
        overlay.openReactionBubble(post(owner, 4L), middle) { _, _ -> }

        overlay.clearForAccountChange()
        assertNull(overlay.postActionBubbleTarget)
        assertNull(overlay.postReactionHandler)

        overlay.openReactionBubble(post(owner, 4L), middle) { _, _ -> }
        overlay.expandReactionPicker(post(owner, 4L))
        overlay.clearForSessionChange()
        assertNull(overlay.postActionBubbleTarget)
        assertNull(overlay.pendingExpandedReactionTarget)
        assertNull(overlay.postReactionHandler)
    }

    @Test
    fun expandingPromotesTheSameTargetWithoutChangingItsPost() {
        val overlay = presenter()
        val owned = post(owner, 4L)
        overlay.openReactionBubble(owned, middle) { _, _ -> }

        overlay.expandReactionPicker(owned)

        val target = overlay.postActionBubbleTarget as PostActionBubbleTarget.Reaction
        assertEquals(ReactionBubbleMode.Expanded, target.mode)
        assertEquals(owned, target.ownedPost)
        assertEquals(middle, target.anchorBounds)
    }
}
