package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.QuoteApproval
import me.foxtails.palustris.ui.Avatar
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.components.BeelineCardShape
import me.foxtails.palustris.ui.components.BeelineNestedSurfaceShape
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.emoji.PostText

/** Test tag of the read-only card of the post that a reply answers. */
internal const val COMPOSER_REPLY_TARGET_TAG = "composer_reply_target"

/** Test tag of the quoted post inside the first card. */
internal const val COMPOSER_QUOTE_CARD_TAG = "composer_quote_card"

/** Width of the column that holds an avatar beside a card. */
internal val COMPOSER_AVATAR_SIZE = 40.dp

/** Space between an avatar and its card. */
internal val COMPOSER_AVATAR_GAP = 8.dp

/**
 * Distance between a card edge and the surfaces inside it. The card radius minus this inset is the
 * radius of every inner surface, which is the nested surface radius of the shared bubble tokens.
 */
internal val COMPOSER_CARD_INSET = 8.dp

/** The most lines of a target post that the composer shows. */
private const val TARGET_BODY_LINES = 4

/**
 * The avatar of one card, hung beside it and level with its first line. A thread line runs from
 * the avatar down to the next card when [line] is set. The column takes the height of its row.
 */
@Composable
internal fun ComposerAvatarColumn(account: Account?, line: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.width(COMPOSER_AVATAR_SIZE).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(4.dp))
        if (account != null) AccountAvatar(account, Modifier.size(COMPOSER_AVATAR_SIZE)) else Avatar(Modifier.size(COMPOSER_AVATAR_SIZE))
        if (line) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.weight(1f).width(2.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

/**
 * The post that the user answers, above the reply card. It is read-only and looks like a post in
 * the feed: avatar on the left, a name, and at most four lines of text. A line joins it to the card below.
 */
@Composable
internal fun ComposerReplyTarget(target: OwnedPost, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.composer_replying_to, target.post.author.displayName)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag(COMPOSER_REPLY_TARGET_TAG)) {
        ComposerAvatarColumn(target.post.author, line = true)
        Spacer(Modifier.width(COMPOSER_AVATAR_GAP))
        Surface(
            modifier = Modifier.weight(1f).padding(bottom = 8.dp).semantics { contentDescription = description },
            shape = BeelineCardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                InlineEmojiText(
                    text = target.post.author.displayName,
                    emoji = target.post.author.emoji,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TargetBody(target)
            }
        }
    }
}

/**
 * The quoted post as a smaller card inside the first card. It holds the author avatar and name on
 * its first line and at most four lines of text below. It has no control; cancelling the post drops the quote.
 */
@Composable
internal fun ComposerQuotedCard(target: OwnedPost, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.composer_quoting, target.post.author.displayName)
    Surface(
        modifier = modifier.fillMaxWidth().testTag(COMPOSER_QUOTE_CARD_TAG).semantics { contentDescription = description },
        shape = BeelineNestedSurfaceShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountAvatar(target.post.author, Modifier.size(24.dp), exposeSemantics = false)
                InlineEmojiText(
                    text = target.post.author.displayName,
                    emoji = target.post.author.emoji,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            TargetBody(target)
        }
    }
}

@Composable
private fun TargetBody(target: OwnedPost) {
    if (target.post.text.isBlank()) {
        Text(stringResource(R.string.composer_no_post_text), maxLines = TARGET_BODY_LINES, overflow = TextOverflow.Ellipsis)
    } else {
        PostText(target.post, style = MaterialTheme.typography.bodyMedium, maxLines = TARGET_BODY_LINES, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The two quote notices as small text below the first card, on the page background. The first
 * says the server could not confirm the quote. The second says the author must approve it.
 */
@Composable
internal fun ComposerQuoteNotices(target: OwnedPost, confirmed: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (!confirmed) {
            Text(
                stringResource(R.string.quote_unconfirmed_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp).testTag("composer_quote_unconfirmed"),
            )
        }
        if (target.post.quoteApproval == QuoteApproval.Manual) {
            Text(
                stringResource(R.string.quote_manual_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp).testTag("composer_quote_manual"),
            )
        }
    }
}
