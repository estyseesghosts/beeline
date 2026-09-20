package me.foxtails.palustris.ui.posts

import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.media.MediaOpenRequest

/** Callbacks for events originating from one post row. This value owns no state or lifecycle. */
internal data class PostRowEvents(
    val onFavourite: (OwnedPost) -> Unit,
    val onReply: (OwnedPost) -> Unit,
    val onRepost: (OwnedPost) -> Unit,
    val onBookmark: (OwnedPost) -> Unit,
    val onReaction: (OwnedPost, EmojiChoice) -> Unit,
    val onOpenProfile: ((Account) -> Unit)?,
    val onSearchHashtag: ((String) -> Unit)?,
    val onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    val onQuote: (OwnedPost) -> Unit = {},
    val onOpenReactionBubble: (OwnedPost, Rect) -> Unit = { _, _ -> },
    val onOpenReactionPicker: (OwnedPost) -> Unit = {},
    val onOpenMedia: (MediaOpenRequest) -> Unit = {},
    val onOpenPost: ((OwnedPost) -> Unit)? = null,
    val onOpenUrl: ((String) -> Unit)? = null,
    val onOpenUsername: ((String) -> Unit)? = null,
)

/** Presentation choices for a post row. It contains no event or ownership behavior. */
internal data class PostRowPresentation(
    val availableActions: Set<PostAction>,
    val quoteEnabled: Boolean = false,
    val truncateBody: Boolean = true,
    val largeLayout: Boolean = false,
    val contentWarningRules: ContentWarningRules = ContentWarningRules(),
    val interactionPresentation: PostInteractionPresentation = PostInteractionPresentation.Feed,
)
