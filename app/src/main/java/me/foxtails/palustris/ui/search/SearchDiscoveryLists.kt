package me.foxtails.palustris.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A hashtag body as the user types and reads it. */
internal fun hashtagLabel(name: String) = "#$name"

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
    state: SearchState,
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

@Composable
internal fun DiscoveryHeading(text: String, startPadding: Dp = 8.dp) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = startPadding, end = 8.dp, top = 12.dp, bottom = 12.dp).semantics { heading() },
    )
}
