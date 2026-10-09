package me.foxtails.palustris.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.large.LocalLargeDockEdgeInsets
import me.foxtails.palustris.ui.motion.ExpandableContent
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.rememberSelectedScale
import me.foxtails.palustris.ui.motion.springPress

internal data class FilterChipEntry(
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val contentDescription: String = label,
    val role: Role = Role.Tab,
    val testTag: String? = null,
    val stateDescription: String? = null,
    val key: String = "$role:$label",
)

/**
 * Selects where the destination chip visibility control appears.
 *
 * Inline puts the circular control first in the scrolling chip row. Hidden removes that inline
 * control while keeping chip expansion state and toggle plumbing intact, so a contextual caret can
 * present the same state without a second state machine.
 */
enum class ChipCaretPresentation {
    Inline,
    Hidden,
}

/**
 * Renders destination chips and their persistent visibility control.
 *
 * The caller owns [listState] and [visible] so adaptive placement cannot reset chip state.
 * The row fills its container. The chip scroll path reaches the far container edge and passes beneath
 * floating chrome there. [leftInset] and [rightInset] are physical resting insets: the first and last
 * chips rest clear of them. An inline caret is the first item of the scrolling row, so it scrolls with the
 * chips and stays in the first logical position; collapsing the chips leaves only the caret. With the
 * caret hidden, collapsing removes the whole row. Physical insets never follow layout direction.
 */
@Composable
internal fun DestinationChipRow(
    entries: List<FilterChipEntry>,
    rowContentDescription: String,
    listState: LazyListState,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    selectedEntryKey: String? = null,
    modifier: Modifier = Modifier,
    rowTestTag: String? = null,
    visibilityToggleTestTag: String = "destination_chip_visibility",
    caretPresentation: ChipCaretPresentation = ChipCaretPresentation.Inline,
    leftInset: Dp = LocalLargeDockEdgeInsets.current.left,
    rightInset: Dp = LocalLargeDockEdgeInsets.current.right,
) {
    val scheme = LocalPalustrisMotionScheme.current
    val selectedEntryIndex = entries.indexOfFirst { it.key == selectedEntryKey && it.selected }
    val inlineCaret = caretPresentation == ChipCaretPresentation.Inline
    // The inline caret is item 0, so chip indices shift by one while it is present.
    val firstChipIndex = if (inlineCaret) 1 else 0
    LaunchedEffect(selectedEntryIndex, scheme.reducedMotion, visible) {
        if (selectedEntryIndex >= 0 && (visible || !inlineCaret)) {
            // Re-expanding adds the chips back this frame, so wait until they are laid out.
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > selectedEntryIndex + firstChipIndex }
            withFrameNanos { }
            if (scheme.reducedMotion) {
                listState.scrollToItem(selectedEntryIndex + firstChipIndex)
            } else {
                listState.animateScrollToItem(selectedEntryIndex + firstChipIndex)
            }
        }
    }
    val caretRotation by animateFloatAsState(
        targetValue = if (visible) 0f else 180f,
        animationSpec = scheme.gentle,
        label = "destinationChipCaretRotation",
    )
    val toggleDescription = stringResource(
        if (visible) R.string.hide_destination_chips else R.string.show_destination_chips,
    )
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val startInset = if (ltr) leftInset else rightInset
    val endInset = if (ltr) rightInset else leftInset

    val chipLazyRow: @Composable () -> Unit = {
        val interactionSource = remember { MutableInteractionSource() }
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(
                lazyListState = listState,
                snapPosition = SnapPosition.Start,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BeelineBubbleMinHeight)
                .semantics {
                    contentDescription = rowContentDescription
                    if (entries.any { it.role == Role.Tab }) selectableGroup()
                }
                .then(rowTestTag?.let { Modifier.testTag(it) } ?: Modifier),
            // The path reaches the far display edge. Content padding only sets where the first and
            // last items rest. The caret rests at the inset; chips follow it after one gap.
            contentPadding = PaddingValues(
                start = startInset + if (inlineCaret) 0.dp else 8.dp,
                end = endInset + 2.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inlineCaret) {
                item(key = INLINE_CARET_KEY) {
                    DestinationChipVisibilityButton(
                        visible = visible,
                        onToggleVisibility = onToggleVisibility,
                        caretRotation = caretRotation,
                        toggleDescription = toggleDescription,
                        visibilityToggleTestTag = visibilityToggleTestTag,
                        interactionSource = interactionSource,
                    )
                }
            }
            // A collapsed inline row keeps only the caret, so removed chips leave no gaps behind.
            if (visible || !inlineCaret) {
                items(entries, key = { it.key }) { entry ->
                    DestinationFilterChip(
                        entry = entry,
                        modifier = if (inlineCaret && !scheme.reducedMotion) Modifier.animateItem() else Modifier,
                    )
                }
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = BeelineBubbleMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inlineCaret) {
            Box(Modifier.weight(1f)) { chipLazyRow() }
        } else {
            ExpandableContent(
                visible = visible,
                modifier = Modifier.weight(1f),
            ) { chipLazyRow() }
        }
    }
}

private const val INLINE_CARET_KEY = "inline-destination-chip-caret"

/**
 * Renders one destination chip: its selected colors, press animation, selection scale, test tag,
 * and accessibility semantics. The caller owns the entry and its callback.
 */
@Composable
private fun DestinationFilterChip(entry: FilterChipEntry, modifier: Modifier = Modifier) {
    val scheme = LocalPalustrisMotionScheme.current
    val interactionSource = remember(entry.key) { MutableInteractionSource() }
    val selectedContainerColor = rememberSelectedColor(
        entry.selected,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.surfaceContainer,
    )
    val selectedContentColor = rememberSelectedColor(
        entry.selected,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val selectedScale = rememberSelectedScale(entry.selected)
    FilterChip(
        selected = entry.selected,
        onClick = entry.onClick,
        enabled = entry.enabled,
        interactionSource = interactionSource,
        label = { Text(entry.label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = selectedContainerColor,
            labelColor = selectedContentColor,
            selectedContainerColor = selectedContainerColor,
            selectedLabelColor = selectedContentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = modifier
            .heightIn(min = BeelineBubbleMinHeight)
            .springPress(interactionSource, pressedScale = scheme.pressedScale)
            .graphicsLayer {
                scaleX = selectedScale
                scaleY = selectedScale
            }
            .then(entry.testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .semantics {
                contentDescription = entry.contentDescription
                role = entry.role
                this.selected = entry.selected
                entry.stateDescription?.let { stateDescription = it }
            },
        shape = BeelineBubbleShape,
    )
}

/**
 * Renders the inline circular chip visibility control as the first item of the chip row.
 *
 * Layouts with a contextual caret hide this presentation and drive the same [visible] state from
 * that caret instead.
 */
@Composable
private fun DestinationChipVisibilityButton(
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    caretRotation: Float,
    toggleDescription: String,
    visibilityToggleTestTag: String,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val scheme = LocalPalustrisMotionScheme.current
    Surface(
        modifier = modifier
            .size(BeelineBubbleMinHeight)
            .springPress(interactionSource, pressedScale = scheme.compactPressedScale),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
    ) {
        IconButton(
            onClick = onToggleVisibility,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = toggleDescription }
                .testTag(visibilityToggleTestTag),
        ) {
            Icon(
                imageVector = AppIcons.CaretUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = caretRotation },
            )
        }
    }
}
