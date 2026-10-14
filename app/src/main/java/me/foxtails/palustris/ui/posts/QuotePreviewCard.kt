package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ContentWarningDecision
import me.foxtails.palustris.domain.ContentWarningPolicy
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.filters.LocalClientFilters

/** Keeps hidden quote text out of composition while preserving the quote card and its action. */
@Composable
internal fun QuotePreviewCard(
    quote: Post,
    contentWarningRules: ContentWarningRules,
    mutedHashtags: Set<String>,
    parentIdentity: String,
    accountIdentity: String,
    labelResource: Int,
    onOpenQuote: () -> Unit,
) {
    val hashtags = postHashtags(quote.text, quote.emoji)
    val decision = ContentWarningPolicy.decide(
        quote.contentWarning,
        hashtags,
        contentWarningRules,
        quote.contentVisibility,
        bodyText = quote.text,
    )
    val matchedTag = if (decision == ContentWarningDecision.Hidden) null else
        ContentWarningPolicy.firstMatchingMutedHashtag(hashtags, mutedHashtags)
    val filterVerdict = if (decision == ContentWarningDecision.Hidden) null else quoteFilterVerdict(quote, LocalClientFilters.current)
    // Reveal is card-local and must not cross quote, parent, account, session, or matched-tag changes.
    var revealed by rememberSaveable(quote.id.connection, quote.id.value, parentIdentity, accountIdentity, matchedTag) {
        mutableStateOf(false)
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = onOpenQuote) {
        Column(Modifier.padding(16.dp)) {
            AccountDisplayName(quote.author, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (decision == ContentWarningDecision.Hidden) {
                Text(stringResource(R.string.content_hidden_quoted), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (filterVerdict != null && !revealed) {
                FilteredQuoteNotice(filterVerdict) { revealed = true }
            } else if (matchedTag != null && !revealed) {
                Text(
                    stringResource(R.string.quote_muted_word_warning, "#${matchedTag.removePrefix("#")}"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { revealed = true }) { Text(stringResource(R.string.content_warning_show)) }
            } else if (matchedTag != null && quote.contentWarning != null) {
                InlineEmojiText(
                    quote.contentWarning.ifBlank { stringResource(R.string.content_warning) }, quote.emoji,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis,
                )
            } else {
                InlineEmojiText(
                    quote.contentWarning?.ifBlank { stringResource(R.string.content_warning) } ?: quote.text,
                    quote.emoji,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(stringResource(labelResource), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * A short card for a quote that arrived without its post. It says why, or offers to fetch the post when
 * the server sent only a reference. [onOpen] is null when nothing more can be done.
 */
@Composable
internal fun QuotePlaceholderCard(
    unavailable: me.foxtails.palustris.domain.QuoteUnavailable?,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val reason = when (unavailable) {
        me.foxtails.palustris.domain.QuoteUnavailable.Pending -> R.string.quote_unavailable_pending
        me.foxtails.palustris.domain.QuoteUnavailable.Rejected -> R.string.quote_unavailable_rejected
        me.foxtails.palustris.domain.QuoteUnavailable.Revoked -> R.string.quote_unavailable_revoked
        me.foxtails.palustris.domain.QuoteUnavailable.Deleted -> R.string.quote_unavailable_deleted
        me.foxtails.palustris.domain.QuoteUnavailable.Unauthorized -> R.string.quote_unavailable_unauthorized
        me.foxtails.palustris.domain.QuoteUnavailable.BlockedAccount -> R.string.quote_unavailable_blocked_account
        me.foxtails.palustris.domain.QuoteUnavailable.BlockedDomain -> R.string.quote_unavailable_blocked_domain
        me.foxtails.palustris.domain.QuoteUnavailable.MutedAccount -> R.string.quote_unavailable_muted_account
        me.foxtails.palustris.domain.QuoteUnavailable.Hidden -> R.string.quote_unavailable_hidden
        me.foxtails.palustris.domain.QuoteUnavailable.Unloaded, null -> R.string.quote_unavailable_reference
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(reason), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onOpen != null) {
                Text(
                    stringResource(R.string.quote_placeholder_open),
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    val cardModifier = modifier.fillMaxWidth().padding(16.dp).testTag("quote_placeholder")
    if (onOpen != null) {
        OutlinedCard(modifier = cardModifier, onClick = onOpen) { body() }
    } else {
        OutlinedCard(modifier = cardModifier) { body() }
    }
}
