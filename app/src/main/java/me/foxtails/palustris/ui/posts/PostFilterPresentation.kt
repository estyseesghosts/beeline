package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostFilterPolicy
import me.foxtails.palustris.domain.PostFilterVerdict

/** The filter decision for [post] on the surface [context], or null when no filter matched. */
@Composable
internal fun rememberPostFilterVerdict(post: Post, context: FilterContext): PostFilterVerdict? =
    remember(post, context) { PostFilterPolicy.evaluate(post, context) }

/** The text that names why a post is collapsed. Shared by the row, notifications, quotes, and Photo Grid detail. */
@Composable
internal fun filteredPostLabel(verdict: PostFilterVerdict): String =
    if (verdict.reason.isBlank()) stringResource(R.string.post_filtered_untitled)
    else stringResource(R.string.post_filtered_named, verdict.reason)

/** The notice a collapsed post shows in place of its body, with a button that shows the post. */
@Composable
internal fun FilteredPostNotice(verdict: PostFilterVerdict, onShow: () -> Unit) {
    Text(
        filteredPostLabel(verdict),
        Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onShow, modifier = Modifier.padding(horizontal = 4.dp).testTag("post_filter_show")) {
        Text(stringResource(R.string.post_filtered_show))
    }
}

/** A quote has no surface of its own, so Home filters decide. A quote never leaves its post; a removing filter collapses it. */
internal fun quoteFilterVerdict(quote: Post): PostFilterVerdict? = PostFilterPolicy.evaluate(quote, FilterContext.Home)

/** The notice inside a quote card. The card owns its reveal state. */
@Composable
internal fun FilteredQuoteNotice(verdict: PostFilterVerdict, onShow: () -> Unit) {
    Text(filteredPostLabel(verdict), color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = onShow) { Text(stringResource(R.string.post_filtered_show)) }
}
