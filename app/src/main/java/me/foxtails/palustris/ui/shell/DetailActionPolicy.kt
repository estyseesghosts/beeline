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
