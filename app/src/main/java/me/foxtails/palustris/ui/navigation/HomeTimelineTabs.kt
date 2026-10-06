package me.foxtails.palustris.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.components.FilterChipEntry
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
