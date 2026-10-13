package me.foxtails.palustris.ui.posts

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ContentWarningDecision
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.QuoteUnavailable
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.links.rememberQuoteOpener
import me.foxtails.palustris.ui.theme.PostScriptProvider

/**
 * The one decision for whether post body, media, poll, and quote show. The row and every detail presentation call it.
 * [hasContentWarning] is true when the post carries a warning that the viewer must expand.
 */
internal fun isPostContentVisible(
    decision: ContentWarningDecision,
    hasContentWarning: Boolean,
    expanded: Boolean,
): Boolean = decision != ContentWarningDecision.Hidden &&
    (!hasContentWarning || expanded || decision == ContentWarningDecision.ExpandedByDefault)

/** Warning text with its show/hide toggle. Shared by the row and Photo Grid detail. */
@Composable
internal fun PostContentWarningToggle(
    ownedPost: OwnedPost,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val post = ownedPost.post
    val warning = post.contentWarning ?: return
    InlineEmojiText(
        warning.ifBlank { stringResource(R.string.content_warning) },
        post.emoji,
        Modifier.padding(horizontal = 16.dp),
        MaterialTheme.typography.bodyLarge,
    )
    TextButton(onClick = onToggle, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(stringResource(if (expanded) R.string.content_warning_hide else R.string.content_warning_show))
    }
}

/** Quote preview bound to the owning post's session and account. Shared by the row and Photo Grid detail. */
@Composable
internal fun PostQuoteSection(
    ownedPost: OwnedPost,
    contentWarningRules: ContentWarningRules,
    @StringRes labelResource: Int,
) {
    val post = ownedPost.post
    val openQuote = rememberQuoteOpener()
    post.quote?.let { quote ->
        // A quoted post has its own script, so it does not take the font of the post around it.
        PostScriptProvider(quote) {
            QuotePreviewCard(
                quote,
                contentWarningRules,
                LocalMutedHashtags.current,
                "${ownedPost.sessionRevision}:${post.id.connection}:${post.id.value}",
                ownedPost.fetchedBy.toString(),
                labelResource,
            ) {
                // A loaded quote opens in the app. Without a shell the quote's own address opens in the browser.
                openQuote(OwnedPost(ownedPost.fetchedBy, quote, ownedPost.sessionRevision), null, null, quote.url)
            }
        }
        return
    }
    val unavailable = post.quoteUnavailable
    if (unavailable != null || post.quoteId != null || post.quoteUrl != null) {
        // The server named a quote without handing it over. Say why, and let a tap try to fetch it.
        QuotePlaceholderCard(
            unavailable = unavailable,
            onOpen = if (unavailable == null || unavailable == QuoteUnavailable.Unloaded) {
                { openQuote(null, post.quoteId, post.quoteUrl, post.quoteUrl) }
            } else {
                null
            },
        )
    }
}
