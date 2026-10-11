package me.foxtails.palustris.ui.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.components.ChipCaretPresentation
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.components.FilterChipEntry

/**
 * The Trending and News chips above the Search field. They exist only while the server supplies links, and
 * they stay in every Search state. A chip tap changes the tab and keeps the query and its results.
 */
@Composable
internal fun SearchTabChips(
    selected: SearchTab,
    onSelect: (SearchTab) -> Unit,
    listState: LazyListState,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    caretPresentation: ChipCaretPresentation,
    modifier: Modifier = Modifier,
    leftInset: Dp? = null,
    rightInset: Dp? = null,
) {
    val entries = SearchTab.entries.map { tab ->
        val label = stringResource(if (tab == SearchTab.Trending) R.string.search_tab_trending else R.string.search_tab_news)
        FilterChipEntry(
            label = label,
            selected = tab == selected,
            onClick = { onSelect(tab) },
            testTag = "search_tab_${tab.name}",
            key = "search-tab:${tab.name}",
        )
    }
    if (leftInset != null && rightInset != null) {
        DestinationChipRow(
            entries = entries,
            rowContentDescription = stringResource(R.string.search_tabs_description),
            listState = listState,
            visible = visible,
            onToggleVisibility = onToggleVisibility,
            selectedEntryKey = "search-tab:${selected.name}",
            modifier = modifier,
            rowTestTag = "search_tabs",
            visibilityToggleTestTag = "search_tabs_visibility",
            caretPresentation = caretPresentation,
            leftInset = leftInset,
            rightInset = rightInset,
        )
    } else {
        DestinationChipRow(
            entries = entries,
            rowContentDescription = stringResource(R.string.search_tabs_description),
            listState = listState,
            visible = visible,
            onToggleVisibility = onToggleVisibility,
            selectedEntryKey = "search-tab:${selected.name}",
            modifier = modifier,
            rowTestTag = "search_tabs",
            visibilityToggleTestTag = "search_tabs_visibility",
            caretPresentation = caretPresentation,
        )
    }
}

/** Whether the chip row takes height above the field: not when it is absent or hidden by the compact-wide caret. */
internal fun searchTabRowOccupiesSpace(tabsAvailable: Boolean, useCompactWideCaret: Boolean, rowVisible: Boolean): Boolean =
    tabsAvailable && (!useCompactWideCaret || rowVisible)
