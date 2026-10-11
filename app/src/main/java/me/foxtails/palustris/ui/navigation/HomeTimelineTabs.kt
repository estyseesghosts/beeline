package me.foxtails.palustris.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.chips.AddHashtagDialog
import me.foxtails.palustris.ui.chips.HashtagChipBubble
import me.foxtails.palustris.ui.chips.HashtagChipMenu
import me.foxtails.palustris.ui.chips.feedExtraChipEntries
import me.foxtails.palustris.ui.components.FilterChipEntry
import me.foxtails.palustris.ui.shell.FeedChipsContract
import me.foxtails.palustris.ui.shell.timelineDescriptionRes
import me.foxtails.palustris.ui.shell.timelineLabelRes

/** Builds Home's shared chip entries without owning or rendering chip presentation. */
@Composable
internal fun homeTimelineChipEntries(
    timelines: Set<Timeline>,
    selected: Timeline,
    onSelect: (Timeline) -> Unit,
): List<FilterChipEntry> = buildList {
    timelineDisplayOrder.filter { it in timelines }.forEach { timeline ->
        val label = stringResource(timelineLabelRes(timeline))
        add(
            FilterChipEntry(
                label = label,
                selected = timeline == selected,
                onClick = { onSelect(timeline) },
                contentDescription = stringResource(R.string.large_timeline, label),
                role = Role.Tab,
                stateDescription = stringResource(timelineDescriptionRes(timeline)),
                testTag = "home_timeline_tab_${timeline.name}",
                key = "home-timeline:${timeline.name}",
            ),
        )
    }
}

/** The key of the Home chip for [selected], so the row can scroll it into view. */
internal fun homeSelectedEntryKey(selected: FeedSource): String = when (selected) {
    is FeedSource.Timeline -> "home-timeline:${selected.timeline.name}"
    else -> "home-${selected.key}"
}

/**
 * Builds Home's whole chip row: the built-in timelines, then the shared catalog's lists, hashtags and
 * antennas, then the add-hashtag control. It also hosts the add dialog and the long-press menu, so every
 * row that shows these chips gets them. A long press on a hashtag chip asks before it unfollows or removes.
 */
@Composable
internal fun homeFeedChipEntries(
    timelines: Set<Timeline>,
    chips: FeedChipsContract,
    selected: FeedSource,
    onSelect: (FeedSource) -> Unit,
): List<FilterChipEntry> {
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<HashtagChipMenu?>(null) }
    val timelineEntries = homeTimelineChipEntries(
        timelines = timelines,
        selected = (selected as? FeedSource.Timeline)?.timeline ?: Timeline.Home,
        onSelect = { onSelect(FeedSource.Timeline(it)) },
    ).map { entry ->
        if (selected is FeedSource.Timeline) entry else entry.copy(selected = false)
    }
    val extraEntries = feedExtraChipEntries(
        extras = chips.state.extras,
        selectedKey = selected.takeUnless { it is FeedSource.Timeline }?.key,
        keyPrefix = "home",
        addTestTag = "home_add_hashtag",
        onSelect = onSelect,
        onHashtagMenu = { hashtag, bounds -> menu = HashtagChipMenu(hashtag, bounds) },
        onAddHashtag = {
            chips.actions.clearError()
            addDialog = true
        },
    )
    if (addDialog) {
        AddHashtagDialog(
            busy = chips.state.busy,
            error = chips.state.error,
            onAdd = { value, onDone ->
                chips.actions.addHashtag(value) { added ->
                    onSelect(added)
                    onDone()
                }
            },
            onClearError = chips.actions::clearError,
            onDismiss = {
                addDialog = false
                chips.actions.clearError()
            },
        )
    }
    menu?.let { open ->
        HashtagChipBubble(
            menu = open,
            onDismiss = { menu = null },
            onConfirm = {
                menu = null
                chips.actions.removeHashtag(open.hashtag.tag) {}
            },
        )
    }
    return timelineEntries + extraEntries
}
