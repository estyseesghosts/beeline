package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/** Keeps hidden quote text out of composition while preserving the quote card and its action. */
@Composable
internal fun QuotePreviewCard(
    quote: Post,
    contentWarningRules: ContentWarningRules,
    labelResource: Int,
    onOpenQuote: () -> Unit,
) {
    val decision = ContentWarningPolicy.decide(
        quote.contentWarning,
        postHashtags(quote.text, quote.emoji),
        contentWarningRules,
        bodyText = quote.text,
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = onOpenQuote) {
        Column(Modifier.padding(16.dp)) {
            AccountDisplayName(quote.author, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (decision == ContentWarningDecision.Hidden) {
                Text(stringResource(R.string.content_hidden_quoted), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
