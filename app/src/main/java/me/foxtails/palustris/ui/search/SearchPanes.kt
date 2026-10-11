package me.foxtails.palustris.ui.search

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import me.foxtails.palustris.domain.search.SearchSuggestions
import me.foxtails.palustris.domain.search.TrendingLink
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.motion.springPress

/**
 * The panel under the field while the user types: at most three accounts, then at most six hashtags. A
 * section with no rows does not appear. A tap on an account opens its profile. A tap on a hashtag runs the
 * hashtag search.
 */
@Composable
internal fun SearchSuggestionPanel(
    suggestions: SearchSuggestions,
    onAccountClick: (Account) -> Unit,
    onHashtag: (String) -> Unit,
    clearance: SearchListClearance,
    listState: LazyListState?,
) {
    if (suggestions.isEmpty) {
        EmptyState(
            AppIcons.SearchBeeline,
            stringResource(R.string.search_ready),
            stringResource(R.string.search_ready_prompt),
            bottomClearance = clearance.end,
        )
        return
    }
    LazyColumn(
        state = listState ?: rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag("search_suggestion_results"),
        contentPadding = clearance.contentPadding(),
    ) {
        if (suggestions.accounts.isNotEmpty()) {
            item(key = "suggestion-people-heading") { DiscoveryHeading(stringResource(R.string.search_suggestions_people)) }
            items(suggestions.accounts, key = { "suggestion-account-${it.id.connection.origin}/${it.id.localId}" }) { account ->
                val interactionSource = remember(account.id) { MutableInteractionSource() }
                ListItem(
                    modifier = Modifier
                        .springPress(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onAccountClick(account) }
                        .testTag("search_account_row_${account.id.localId}"),
                    headlineContent = { AccountDisplayName(account, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(account.handle) },
                    leadingContent = { AccountAvatar(account, Modifier.size(48.dp)) },
                )
            }
        }
        if (suggestions.hashtags.isNotEmpty()) {
            item(key = "suggestion-hashtags-heading") { DiscoveryHeading(stringResource(R.string.search_suggestions_hashtags)) }
            items(suggestions.hashtags, key = { "suggestion-${it.name}" }) { suggestion ->
                val interactionSource = remember(suggestion.name) { MutableInteractionSource() }
                ListItem(
                    modifier = Modifier
                        .springPress(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                            onHashtag(hashtagLabel(suggestion.name))
                        }
                        .testTag("search_suggestion_row_${suggestion.name}"),
                    headlineContent = { Text(hashtagLabel(suggestion.name), style = MaterialTheme.typography.titleMedium) },
                )
            }
        }
    }
}

/**
 * What Search shows while the field is empty: trending hashtags as chips, trending posts as normal rows,
 * and on Mastodon a short list of link cards. A section with no rows does not appear. A server that reports
 * nothing gets one empty state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchTrendsPane(
    state: SearchTrendsState,
    context: SearchPostRowContext,
    onHashtag: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    listState: LazyListState?,
) {
    val clearance = context.clearance
    when {
        state.isEmpty && !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.isEmpty && state.error != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f)) {
                EmptyState(AppIcons.Hashtag, stringResource(R.string.search_trends_failed), state.error, bottomClearance = clearance.end)
            }
            TextButton(onClick = onRetry, modifier = Modifier.padding(bottom = clearance.end)) {
                Text(stringResource(R.string.search_trends_retry))
            }
        }
        state.isEmpty -> EmptyState(
            AppIcons.Hashtag,
            stringResource(R.string.search_trends_empty),
            stringResource(R.string.search_trends_empty_prompt),
            bottomClearance = clearance.end,
        )
        else -> LazyColumn(
            state = listState ?: rememberLazyListState(),
            modifier = Modifier.fillMaxSize().testTag("search_trends"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp + clearance.end),
        ) {
            if (state.tags.isNotEmpty()) {
                item(key = "trending-heading") { DiscoveryHeading(stringResource(R.string.search_trending_hashtags), 8.dp + clearance.left) }
                item(key = "trending-tags") {
                    FlowRow(
                        Modifier.fillMaxWidth()
                            .padding(start = 8.dp + clearance.left, end = 8.dp + clearance.right, bottom = 8.dp)
                            .testTag("search_trending_results"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.tags.forEach { tag -> TrendingTagChip(tag, onHashtag) }
                    }
                }
            }
            if (state.posts.isNotEmpty()) {
                item(key = "trending-posts-heading") { DiscoveryHeading(stringResource(R.string.search_trending_posts), 8.dp + clearance.left) }
                searchPostItems(state.posts, context, "trending")
                if (state.loadingMore || state.nextCursor != null) {
                    searchPagingFooter(state.loadingMore, state.nextCursor != null, onLoadMore, clearance)
                }
            }
            if (state.links.isNotEmpty()) {
                item(key = "trending-links-heading") { DiscoveryHeading(stringResource(R.string.search_trending_links), 8.dp + clearance.left) }
                items(state.links, key = { "trending-link-${it.url}" }) { link ->
                    TrendingLinkCard(link, onOpenUrl, Modifier.padding(start = 16.dp + clearance.left, end = 16.dp + clearance.right, bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TrendingTagChip(tag: TrendingHashtag, onHashtag: (String) -> Unit) {
    val people = tag.accounts
    val label = hashtagLabel(tag.name)
    AssistChip(
        onClick = { onHashtag(label) },
        label = { Text(label) },
        trailingIcon = people?.let { count ->
            { Text(pluralStringResource(R.plurals.search_trending_people, count, count), style = MaterialTheme.typography.labelSmall) }
        },
        modifier = Modifier.testTag("search_trending_row_${tag.name}"),
    )
}

@Composable
private fun TrendingLinkCard(link: TrendingLink, onOpenUrl: ((String) -> Unit)?, modifier: Modifier) {
    OutlinedCard(
        onClick = { onOpenUrl?.invoke(link.url) },
        enabled = onOpenUrl != null,
        modifier = modifier.fillMaxWidth().testTag("search_trending_link"),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            link.provider?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(link.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            link.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
