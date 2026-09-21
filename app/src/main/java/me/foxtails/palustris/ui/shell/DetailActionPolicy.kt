package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost

/** Resolved post-action handlers for one detail surface. */
internal class DetailActions(
    val favorite: (OwnedPost) -> Unit,
    val reply: (OwnedPost) -> Unit,
    val reshare: (OwnedPost) -> Unit,
    val bookmark: (OwnedPost) -> Unit,
    val react: (OwnedPost, EmojiChoice) -> Unit,
)

/** Builds the shared fallback handlers from the shell detail callbacks. */
internal fun fallbackDetailActions(callbacks: ShellDetailCallbacks): DetailActions = DetailActions(
    favorite = callbacks.onReact,
    reply = callbacks.onReply,
    reshare = callbacks.onReshare,
    bookmark = callbacks.onBookmark,
    react = callbacks.onReaction,
)

/** Returns the shell callbacks with the five mutation handlers replaced by resolved actions. */
internal fun ShellDetailCallbacks.withResolvedActions(resolved: DetailActions): ShellDetailCallbacks = copy(
    onReact = resolved.favorite,
    onReply = resolved.reply,
    onReshare = resolved.reshare,
    onBookmark = resolved.bookmark,
    onReaction = resolved.react,
)

/**
 * Returns the thread owner for wide detail wiring.
 *
 * Wide detail routes mutations through the active thread owner. Compact detail
 * keeps fallback and origin routing and never passes the thread owner.
 */
internal fun detailThreadOwner(
    hasSelection: Boolean,
    origin: LargePostOrigin,
    thread: ThreadContract,
): ThreadContract? = thread.takeIf { hasSelection && origin.supportsComments() }

/** Selects handlers from the collection that owns the selected post. */
internal fun detailActionsFor(
    origin: LargePostOrigin,
    profile: ProfileContract,
    bookmarks: BookmarksContract,
    fallback: DetailActions,
    thread: ThreadContract? = null,
): DetailActions = DetailActions(
    favorite = thread?.let { it.actions::favorite } ?: fallback.favorite,
    reply = fallback.reply,
    reshare = thread?.let { it.actions::repost } ?: fallback.reshare,
    bookmark = thread?.let { it.actions::bookmark } ?: fallback.bookmark,
    react = thread?.let { it.actions::react } ?: when (origin) {
        LargePostOrigin.Profile -> profile.actions::react
        LargePostOrigin.Saved -> bookmarks.actions::react
        else -> fallback.react
    },
)
