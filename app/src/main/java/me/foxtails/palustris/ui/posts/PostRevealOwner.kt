package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.effectiveTargetId

/**
 * What the viewer asked to see of one post. The owner records intent only. Callers still apply
 * `isPostContentVisible` and `MediaRequestPolicy`, so a post that a rule hides stays hidden.
 */
interface PostRevealPresentation {
    /** True when the viewer expanded the content warning of [ownedPost]. */
    fun warningExpanded(ownedPost: OwnedPost): Boolean

    /** True when the viewer revealed the sensitive media of [ownedPost], every image at once. */
    fun mediaRevealed(ownedPost: OwnedPost): Boolean

    /** Expanding the warning also reveals the media. Collapsing it covers the media again. */
    fun setWarningExpanded(ownedPost: OwnedPost, expanded: Boolean)

    /** Reveals every sensitive image of [ownedPost]. */
    fun revealMedia(ownedPost: OwnedPost)
}

internal val LocalPostReveals = staticCompositionLocalOf<PostRevealPresentation?> { null }

/** The session owner, or a private one for a preview or test that provides none. */
@Composable
internal fun currentPostReveals(): PostRevealPresentation = LocalPostReveals.current ?: remember { PostRevealOwner() }

private data class RevealKey(val account: AccountId, val post: EntityId)

private data class Reveal(val expanded: Boolean, val mediaRevealed: Boolean)

/**
 * Owns the reveal state of the posts of one connected session and releases it with the session.
 * Every surface (feed row, detail, media viewer, Photo Grid) reads the same entry, so one reveal
 * shows everywhere. The key is the fetching account plus the post a repost row wraps. The map keeps
 * at most [limit] posts and drops the oldest first. State lives only for the session, so a process
 * death covers posts again.
 */
class PostRevealOwner(private val limit: Int = DEFAULT_LIMIT) : PostRevealPresentation {
    private var entries by mutableStateOf<Map<RevealKey, Reveal>>(emptyMap())

    /** Number of posts held. */
    internal val size: Int get() = entries.size

    override fun warningExpanded(ownedPost: OwnedPost): Boolean = entries[key(ownedPost)]?.expanded == true

    override fun mediaRevealed(ownedPost: OwnedPost): Boolean = entries[key(ownedPost)]?.mediaRevealed == true

    override fun setWarningExpanded(ownedPost: OwnedPost, expanded: Boolean) {
        put(key(ownedPost), Reveal(expanded = expanded, mediaRevealed = expanded))
    }

    override fun revealMedia(ownedPost: OwnedPost) {
        val key = key(ownedPost)
        put(key, (entries[key] ?: Reveal(expanded = false, mediaRevealed = false)).copy(mediaRevealed = true))
    }

    /** Drops every entry. The session host calls it when the session is replaced or released. */
    fun retire() {
        entries = emptyMap()
    }

    private fun key(ownedPost: OwnedPost) = RevealKey(ownedPost.fetchedBy, ownedPost.effectiveTargetId())

    private fun put(key: RevealKey, reveal: Reveal) {
        val next = LinkedHashMap(entries)
        next.remove(key)
        next[key] = reveal
        while (next.size > limit) next.remove(next.keys.first())
        entries = next
    }

    private companion object {
        const val DEFAULT_LIMIT = 500
    }
}
