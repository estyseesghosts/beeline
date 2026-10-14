package me.foxtails.palustris.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.foxtails.palustris.domain.ContentWarningPolicy
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.domain.PostFilterPolicy
import me.foxtails.palustris.domain.filterContext
import me.foxtails.palustris.ui.filters.LocalClientFilters
import me.foxtails.palustris.ui.posts.postHashtags

/**
 * The Home rows that stay in the list: no locally muted hashtag and no removing filter for the feed's context.
 * The result is remembered for its inputs, so a recomposition does not match every row again.
 */
@Composable
internal fun rememberVisibleHomeRows(rows: List<OwnedPost>, mutedHashtags: Set<String>, feed: FeedSource): List<OwnedPost> {
    val clientFilters = LocalClientFilters.current
    return remember(rows, mutedHashtags, feed, clientFilters) {
        val context = feed.filterContext()
        rows.filterNot { ownedPost -> ContentWarningPolicy.matchesHashtagMute(postHashtags(ownedPost.post.text, ownedPost.post.emoji), mutedHashtags) }
            .filterNot { ownedPost -> PostFilterPolicy.evaluate(ownedPost.post, context, clientFilters)?.action == PostFilterAction.Remove }
    }
}
