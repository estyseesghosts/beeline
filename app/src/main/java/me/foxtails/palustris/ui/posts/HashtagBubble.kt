@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.components.FloatingControlElevation
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.theme.forPostLabel

private const val COMPACT_HASHTAG_LIMIT = 5

@Composable
internal fun HashtagBubble(
    hashtags: List<String>,
    maxHeight: Dp,
    openingKey: Long,
    side: BubblePlacement,
    onSelected: (String) -> Unit,
) {
    var expanded by remember(openingKey) { mutableStateOf(false) }
    val listDescription = stringResource(
        if (expanded) R.string.post_action_hashtags_expanded else R.string.post_action_hashtags,
    )
    val stateDescription = stringResource(
        if (expanded) R.string.post_action_bubble_expanded else R.string.post_action_bubble_collapsed,
    )
    Surface(
        modifier = Modifier
            .widthIn(max = HASHTAG_BUBBLE_MAX_WIDTH)
            .heightIn(max = maxHeight)
            .testTag(if (expanded) "hashtag_bubble_expanded" else "hashtag_bubble_compact")
            .semantics {
                contentDescription = listDescription
                this.stateDescription = stateDescription
            },
        shape = BeelineBubbleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = FloatingControlElevation,
    ) {
        if (expanded) {
            ExpandedHashtags(hashtags, side, onSelected) { expanded = false }
        } else {
            CompactHashtags(
                hashtags = hashtags,
                maxHeight = maxHeight,
                side = side,
                density = LocalDensity.current,
                onExpand = { expanded = true },
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun CompactHashtags(
    hashtags: List<String>,
    maxHeight: Dp,
    side: BubblePlacement,
    density: androidx.compose.ui.unit.Density,
    onExpand: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge.forPostLabel()
    val visibleHashtags = remember(hashtags, maxHeight, density, textMeasurer, textStyle) {
        measuredCompactPrefix(
            hashtags = hashtags,
            maxWidth = HASHTAG_BUBBLE_CONTENT_WIDTH,
            maxHeight = maxHeight,
            density = density,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
        )
    }
    val entries = hashtagBubbleEntries(visibleHashtags, showSeeAll = visibleHashtags.size < hashtags.size, side = side)
    // The column is as wide as its widest pill. Traversal reads the hashtags before the action on both sides.
    Column(
        modifier = Modifier
            .padding(8.dp)
            .width(IntrinsicSize.Max)
            .semantics { isTraversalGroup = true },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { entry ->
            when (entry) {
                is HashtagBubbleEntry.Tag -> HashtagPill(
                    entry.hashtag,
                    onSelected,
                    Modifier.semantics { traversalIndex = 0f },
                )
                HashtagBubbleEntry.SeeAll -> PillAction(
                    label = stringResource(R.string.post_action_hashtags_see_all),
                    onClick = onExpand,
                    modifier = Modifier
                        .testTag("hashtag_bubble_expand")
                        .semantics { traversalIndex = 1f },
                    contentDescription = stringResource(R.string.post_action_hashtags_see_all),
                )
            }
        }
    }
}

/** One row of the compact bubble. */
internal sealed interface HashtagBubbleEntry {
    data class Tag(val hashtag: String) : HashtagBubbleEntry
    data object SeeAll : HashtagBubbleEntry
}

/**
 * Orders the compact bubble so "see all?" is the entry farthest from the post anchor: last below
 * the anchor, first above it. It appears only when the compact list hides hashtags.
 */
internal fun hashtagBubbleEntries(
    visible: List<String>,
    showSeeAll: Boolean,
    side: BubblePlacement,
): List<HashtagBubbleEntry> {
    val tags = visible.map { HashtagBubbleEntry.Tag(it) }
    return when {
        !showSeeAll -> tags
        side == BubblePlacement.Below -> tags + HashtagBubbleEntry.SeeAll
        else -> listOf(HashtagBubbleEntry.SeeAll) + tags
    }
}

/**
 * Picks the side of the anchor with more room, below when they tie. The size limit, the popup
 * position and the entry order all use this one result.
 */
internal fun hashtagBubbleSide(anchor: Rect, windowHeight: Float, bottomClearance: Float, margin: Float): BubblePlacement {
    val below = windowHeight - bottomClearance - anchor.bottom - margin
    val above = anchor.top - margin
    return if (below >= above) BubblePlacement.Below else BubblePlacement.Above
}

private val HASHTAG_BUBBLE_MAX_WIDTH = 280.dp
private val HASHTAG_BUBBLE_CONTENT_WIDTH = HASHTAG_BUBBLE_MAX_WIDTH - 16.dp

@Composable
private fun ExpandedHashtags(
    hashtags: List<String>,
    side: BubblePlacement,
    onSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
    val closePill = @Composable {
        PillAction(
            label = stringResource(R.string.post_action_hashtags_close),
            onClick = onClose,
            modifier = Modifier.testTag("hashtag_bubble_close"),
            contentDescription = stringResource(R.string.post_action_hashtags_close),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(8.dp),
    ) {
        // "Close" sits farthest from the post anchor, the same place "see all?" takes.
        if (side == BubblePlacement.Above) closePill()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(hashtags.chunked(2), key = { row -> row.joinToString(" ") }) { row ->
                Box(Modifier.fillMaxWidth()) {
                    FlowRow(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        row.forEach { hashtag -> HashtagPill(hashtag, onSelected) }
                    }
                }
            }
        }
        if (side == BubblePlacement.Below) closePill()
    }
}

@Composable
private fun HashtagPill(hashtag: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    me.foxtails.palustris.ui.components.HashtagPill(
        label = hashtag.removePrefix("#"),
        onClick = { onSelected(hashtag) },
        modifier = modifier.testTag("hashtag_bubble_$hashtag"),
        contentDescription = stringResource(R.string.post_action_hashtag_description, hashtag),
    )
}

private fun measuredCompactPrefix(
    hashtags: List<String>,
    maxWidth: Dp,
    maxHeight: Dp,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
): List<String> {
    val width = maxWidth.coerceAtLeast(1.dp)
    val availableHeight = maxHeight - 72.dp
    if (availableHeight < 48.dp) return emptyList()

    var usedHeight = 0.dp
    val result = mutableListOf<String>()
    for (hashtag in hashtags.take(COMPACT_HASHTAG_LIMIT)) {
        val measured = textMeasurer.measure(
            AnnotatedString(hashtag),
            textStyle,
            constraints = Constraints(maxWidth = with(density) { minOf(width, 240.dp).roundToPx() }),
        )
        val pillHeight = maxOf(48.dp, with(density) { measured.size.height.toDp() } + 20.dp)
        val nextHeight = usedHeight + if (usedHeight == 0.dp) pillHeight else 8.dp + pillHeight
        if (nextHeight > availableHeight) break
        result += hashtag
        usedHeight = nextHeight
    }
    return result
}
