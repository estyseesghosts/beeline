package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId

/** One action family that shares an execution slot per effective target. */
enum class PostActionFamily {
    Favorite,
    FavoriteReaction,
    Reaction,
    Reshare,
    Bookmark,
    Reply,
}

/**
 * Session-bound execution authority for post interaction families.
 *
 * Per-feature job maps cannot serialize the same target's reaction family across Home,
 * Profile, collections, and Thread. This gate is shared across surfaces: a second caller for
 * one family and effective target is rejected while the first owns the slot. The caller does
 * not wait. Slots are keyed by account and session revision, so replacement sessions never
 * block on stale owners. Feature owners keep their own rows, overlays, and membership.
 *
 * Each accepted acquisition returns the token that owns the slot. Only that token releases
 * the slot. A foreign or repeated release changes nothing.
 */
class PostInteractionExecutionAuthority {
    /** Opaque ownership of one reserved family slot. Only this token releases the slot. */
    class OperationToken internal constructor(
        internal val accountId: AccountId,
        internal val sessionRevision: Long,
        internal val family: PostActionFamily,
        internal val target: EntityId,
    )

    private data class Slot(
        val accountId: AccountId,
        val sessionRevision: Long,
        val family: PostActionFamily,
        val target: EntityId,
    )

    // Observable so every surface recomposes when an action starts or settles. Writes stay synchronized.
    private var slots by mutableStateOf(emptySet<Slot>())

    /** Reserves the family slot. Returns null when another surface owns it. */
    @Synchronized
    fun acquire(
        accountId: AccountId,
        sessionRevision: Long,
        family: PostActionFamily,
        target: EntityId,
    ): OperationToken? {
        val slot = Slot(accountId, sessionRevision, family, target)
        if (slot in slots) return null
        slots = slots + slot
        return OperationToken(accountId, sessionRevision, family, target)
    }

    /** Releases only the slot owned by [token]. A foreign or repeated release changes nothing. */
    @Synchronized
    fun release(token: OperationToken) {
        slots = slots - Slot(token.accountId, token.sessionRevision, token.family, token.target)
    }

    /**
     * True while any of [families] is in flight for [target] in this session. The answer is the same on every surface
     * because all of them share this authority, and it is observable from Compose.
     */
    fun isPending(
        accountId: AccountId,
        sessionRevision: Long,
        target: EntityId,
        families: Set<PostActionFamily>,
    ): Boolean = slots.any {
        it.accountId == accountId && it.sessionRevision == sessionRevision && it.target == target && it.family in families
    }
}
