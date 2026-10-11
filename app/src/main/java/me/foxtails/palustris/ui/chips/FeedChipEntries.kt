package me.foxtails.palustris.ui.chips

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.HashtagOrigin
import me.foxtails.palustris.ui.components.FilterChipEntry

/** The label a hashtag chip shows: the tag with one leading `#`. */
internal fun hashtagChipLabel(tag: String): String = "#" + tag.removePrefix("#")

/**
 * Builds the chips that follow the built-in timelines: lists, hashtags and antennas, then the add-hashtag
 * control. Home and the Photo Grid both call this, so the two rows look and behave the same. A hashtag
 * chip reports a long press and a TalkBack action through [onHashtagMenu]. List and antenna chips do not.
 * [selectedKey] is the [FeedSource.key] of the selected chip, or null when a built-in timeline is selected.
 */
@Composable
internal fun feedExtraChipEntries(
    extras: List<FeedSource>,
    selectedKey: String?,
    keyPrefix: String,
    addTestTag: String,
    onSelect: (FeedSource) -> Unit,
    onHashtagMenu: (FeedSource.Hashtag, Rect) -> Unit,
    onAddHashtag: () -> Unit,
): List<FilterChipEntry> = buildList {
    extras.forEach { feed ->
        when (feed) {
            is FeedSource.UserList -> add(
                FilterChipEntry(
                    label = feed.title,
                    selected = selectedKey == feed.key,
                    onClick = { onSelect(feed) },
                    contentDescription = stringResource(R.string.feed_chip_list, feed.title),
                    testTag = "${keyPrefix}_list_${feed.id}",
                    key = "$keyPrefix-${feed.key}",
                ),
            )
            is FeedSource.Antenna -> add(
                FilterChipEntry(
                    label = feed.name,
                    selected = selectedKey == feed.key,
                    onClick = { onSelect(feed) },
                    contentDescription = stringResource(R.string.feed_chip_antenna, feed.name),
                    testTag = "${keyPrefix}_antenna_${feed.id}",
                    key = "$keyPrefix-${feed.key}",
                ),
            )
            is FeedSource.Hashtag -> {
                val label = hashtagChipLabel(feed.tag)
                add(
                    FilterChipEntry(
                        label = label,
                        selected = selectedKey == feed.key,
                        onClick = { onSelect(feed) },
                        contentDescription = stringResource(R.string.feed_chip_hashtag, label),
                        testTag = "${keyPrefix}_hashtag_${feed.tag.removePrefix("#")}",
                        key = "$keyPrefix-${feed.key}",
                        onLongClick = { bounds -> onHashtagMenu(feed, bounds) },
                        longClickLabel = stringResource(hashtagMenuLabel(feed.origin), label),
                    ),
                )
            }
            is FeedSource.Timeline -> Unit
        }
    }
    add(
        FilterChipEntry(
            label = stringResource(R.string.photo_grid_add_hashtag),
            onClick = onAddHashtag,
            role = Role.Button,
            contentDescription = stringResource(R.string.photo_grid_add_hashtag),
            testTag = addTestTag,
            key = "$keyPrefix-action:add-hashtag",
        ),
    )
}

/** The accessibility label of the long-press action: Unfollow on the server, Remove on the device. */
internal fun hashtagMenuLabel(origin: HashtagOrigin): Int = when (origin) {
    HashtagOrigin.Server -> R.string.feed_chip_unfollow_tag
    HashtagOrigin.Device -> R.string.feed_chip_remove_tag
}
