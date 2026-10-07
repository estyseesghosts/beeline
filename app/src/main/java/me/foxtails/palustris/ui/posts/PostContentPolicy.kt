package me.foxtails.palustris.ui.posts

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ContentWarningDecision
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.links.ExternalLinkHandler

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

/** Poll options with vote counts. Shared by the row and Photo Grid detail. */
@Composable
internal fun PostPollOptions(ownedPost: OwnedPost) {
    val post = ownedPost.post
    post.pollOptions.forEach { option ->
        Surface(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(Modifier.padding(12.dp)) {
                InlineEmojiText(option.text, post.emoji, Modifier.weight(1f), MaterialTheme.typography.bodyMedium)
                Text(pluralStringResource(R.plurals.post_poll_votes, option.votes, option.votes), style = MaterialTheme.typography.labelLarge)
            }
        }
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
    val context = LocalContext.current
    post.quote?.let { quote ->
        QuotePreviewCard(
            quote,
            contentWarningRules,
            LocalMutedHashtags.current,
            "${ownedPost.sessionRevision}:${post.id.connection}:${post.id.value}",
            ownedPost.fetchedBy.toString(),
            labelResource,
        ) {
            ExternalLinkHandler.open(context, quote.url)
        }
    }
}
