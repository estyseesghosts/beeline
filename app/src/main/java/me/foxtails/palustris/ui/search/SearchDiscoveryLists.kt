package me.foxtails.palustris.ui.search

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress

/** A hashtag body as the user types and reads it. */
private fun hashtagLabel(name: String) = "#$name"

/** Chips under the results header. A long list wraps instead of scrolling past the floating chrome. */
private const val MAX_RELATED_CHIPS = 8

/**
 * The physical clearance that every Search list applies inside its scroll content. Both edges
 * belong to the content, so rows and their click bounds keep clear of floating chrome while the
 * list viewport keeps its width.
 */
internal data class SearchListClearance(val end: Dp, val right: Dp, val left: Dp) {
    fun contentPadding(): PaddingValues =
        PaddingValues.Absolute(left = 8.dp + left, right = 8.dp + right, bottom = end)
}

/** The list that the Hashtags tab shows before the user types. Rows run the existing hashtag search. */
@Composable
internal fun TrendingHashtagList(
    trending: List<TrendingHashtag>,
    onHashtag: (String) -> Unit,
    clearance: SearchListClearance,
    listState: LazyListState?,
) {
    val scheme = LocalPalustrisMotionScheme.current
    LazyColumn(
        state = listState ?: rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag("search_trending_results"),
        contentPadding = clearance.contentPadding(),
    ) {
        item(key = "trending-heading") { DiscoveryHeading(stringResource(R.string.search_trending_hashtags)) }
        items(trending, key = { "trending-${it.name}" }) { tag ->
            val interactionSource = remember(tag.name) { MutableInteractionSource() }
            ListItem(
                modifier = Modifier
                    .springPress(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                        onHashtag(hashtagLabel(tag.name))
                    }
                    .animateItem(
                        fadeInSpec = scheme.fastFadeIn,
                        fadeOutSpec = scheme.fastFadeOut,
                        placementSpec = scheme.gentleOffset,
                    )
                    .testTag("search_trending_row_${tag.name}"),
                headlineContent = { Text(hashtagLabel(tag.name), style = MaterialTheme.typography.titleMedium) },
                supportingContent = tag.accounts?.let { people ->
                    { Text(pluralStringResource(R.plurals.search_trending_people, people, people)) }
                },
            )
        }
    }
}

/** Suggestions for the hashtag that the user is typing. A row runs the search. */
@Composable
internal fun HashtagSuggestionList(
    suggestions: List<HashtagSuggestion>,
    onHashtag: (String) -> Unit,
    clearance: SearchListClearance,
    listState: LazyListState?,
) {
    LazyColumn(
        state = listState ?: rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag("search_suggestion_results"),
        contentPadding = clearance.contentPadding(),
    ) {
        items(suggestions, key = { "suggestion-${it.name}" }) { suggestion ->
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

/** Related hashtags under the results header. A chip runs that search. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RelatedHashtagChips(
    relatedTags: List<String>,
    onHashtag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("search_hashtag_related"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        relatedTags.take(MAX_RELATED_CHIPS).forEach { tag ->
            AssistChip(
                onClick = { onHashtag(hashtagLabel(tag)) },
                label = { Text(hashtagLabel(tag)) },
                modifier = Modifier.testTag("search_related_chip_$tag"),
            )
        }
    }
}

/**
 * The items above hashtag results: the "Includes" line when the search merged hashtags, and the
 * related chips when the catalog suggests any. [modifier] carries the physical clearance.
 */
internal fun LazyListScope.hashtagResultsHeader(
    state: AccountSearchState,
    modifier: Modifier,
    onShowOnly: (String) -> Unit,
    onHashtag: (String) -> Unit,
) {
    if (state.combinedTags.isNotEmpty()) {
        item(key = "combined-hashtags") {
            CombinedHashtagHeader(
                primaryTag = state.tagQuery.orEmpty(),
                combinedTags = state.combinedTags,
                onShowOnly = { onShowOnly(state.query) },
                modifier = modifier,
            )
        }
    }
    if (state.relatedTags.isNotEmpty()) {
        item(key = "related-hashtags") { RelatedHashtagChips(state.relatedTags, onHashtag, modifier) }
    }
}

/**
 * Account rows for search results and for the popular accounts list. A non-null [heading] becomes
 * the first item, so the heading scrolls with the rows.
 */
@Composable
internal fun AccountList(
    accounts: List<Account>,
    heading: String?,
    onAccountClick: (Account) -> Unit,
    clearance: SearchListClearance,
    listState: LazyListState?,
    listTag: String,
) {
    val scheme = LocalPalustrisMotionScheme.current
    LazyColumn(
        state = listState ?: rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag(listTag),
        contentPadding = clearance.contentPadding(),
    ) {
        heading?.let { item(key = "accounts-heading") { DiscoveryHeading(it) } }
        items(accounts, key = { "${it.id.connection.origin}/${it.id.localId}" }) { account ->
            val interactionSource = remember(account.id) { MutableInteractionSource() }
            ListItem(
                modifier = Modifier
                    .springPress(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onAccountClick(account) }
                    .animateItem(
                        fadeInSpec = scheme.fastFadeIn,
                        fadeOutSpec = scheme.fastFadeOut,
                        placementSpec = scheme.gentleOffset,
                    )
                    .testTag("search_account_row_${account.id.localId}"),
                headlineContent = { AccountDisplayName(account, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(account.handle) },
                leadingContent = { AccountAvatar(account, Modifier.size(48.dp)) },
            )
        }
    }
}

@Composable
private fun DiscoveryHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp).semantics { heading() },
    )
}
