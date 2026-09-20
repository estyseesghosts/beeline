package me.foxtails.palustris.ui.posts

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.session.ConnectedSessionPostInteractionAuthority
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies that connected surfaces share slots without sharing replacement sessions. */
class PostInteractionExecutionAuthorityCrossSurfaceTest {
    private val account = AccountId(Connection("https://cross-surface.example", Protocol.MISSKEY), "owner")
    private val target = EntityId("https://cross-surface.example", "post")

    @Test
    fun sameSessionRejectsConflictingSurfaceUntilTheFirstOperationReleases() {
        val authority = PostInteractionExecutionAuthority()
        val first = authority.acquire(account, 1L, PostActionFamily.Reaction, target)

        assertNotNull(first)
        assertNull(authority.acquire(account, 1L, PostActionFamily.Reaction, target))
        authority.release(first!!)
        assertNotNull(authority.acquire(account, 1L, PostActionFamily.Reaction, target))
    }

    @Test
    fun replacementSessionHasIndependentSlotsAndOldCompletionCannotReleaseIt() {
        val oldAuthority = PostInteractionExecutionAuthority()
        val newAuthority = PostInteractionExecutionAuthority()
        val oldToken = oldAuthority.acquire(account, 1L, PostActionFamily.Reshare, target)
        val newToken = newAuthority.acquire(account, 2L, PostActionFamily.Reshare, target)

        assertNotNull(oldToken)
        assertNotNull(newToken)
        oldAuthority.release(oldToken!!)
        assertNull(newAuthority.acquire(account, 2L, PostActionFamily.Reshare, target))
    }

    @Test
    fun connectedSessionHostSharesOneAuthorityAcrossFeatureOwnersAndReplacesItByRevision() {
        val connected = ConnectedSessionPostInteractionAuthority(account, 1L, PostInteractionExecutionAuthority())
        val feed = connected.authority
        val profile = connected.authority
        val thread = connected.authority
        val saved = connected.authority

        assertSame(feed, profile)
        assertSame(feed, thread)
        assertSame(feed, saved)

        val replacement = ConnectedSessionPostInteractionAuthority(account, 2L, PostInteractionExecutionAuthority())
        assertNotSame(feed, replacement.authority)
    }
}
