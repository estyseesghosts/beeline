package me.foxtails.palustris.ui.shell

import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.media.MediaOpenRequest

/** Actions shared by compact and wide selected-post surfaces. */
internal data class ShellDetailCallbacks(
    val onClose: () -> Unit,
    val onReact: (OwnedPost) -> Unit,
    val onReply: (OwnedPost) -> Unit,
    val onReshare: (OwnedPost) -> Unit,
    val onBookmark: (OwnedPost) -> Unit,
    val onReaction: (OwnedPost, EmojiChoice) -> Unit,
    val onOpenProfile: (Account) -> Unit,
    val onSearchHashtag: (String) -> Unit,
    val onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    val onOpenReactionBubble: (OwnedPost, Rect, (OwnedPost, EmojiChoice) -> Unit) -> Unit,
    val onOpenReactionPicker: (OwnedPost) -> Unit,
    val onOpenMedia: (MediaOpenRequest) -> Unit,
    val onOpenUsername: ((String) -> Unit)? = null,
    val onThreadRefresh: () -> Unit,
    val onThreadContinue: () -> Unit,
    val quoteEnabled: Boolean,
    val onQuote: (OwnedPost) -> Unit,
)
