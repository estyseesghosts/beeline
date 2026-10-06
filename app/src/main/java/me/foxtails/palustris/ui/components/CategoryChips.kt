package me.foxtails.palustris.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
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
 * Inline keeps the existing circular control at the start of the chip row. Hidden removes that
 * inline control while keeping chip expansion state and toggle plumbing intact, so compact-wide
 * can present the same state through its contextual caret without a second state machine.
 */
enum class ChipCaretPresentation {
    Inline,
    Hidden,
}

/**
 * Renders destination chips and their persistent visibility control.
 *
 * The caller owns [listState] and [visible] so adaptive placement cannot reset chip state.
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
) {
    val scheme = LocalPalustrisMotionScheme.current
    val selectedEntryIndex = entries.indexOfFirst { it.key == selectedEntryKey && it.selected }
    LaunchedEffect(selectedEntryIndex, scheme.reducedMotion) {
        if (selectedEntryIndex >= 0) {
            if (scheme.reducedMotion) {
                listState.scrollToItem(selectedEntryIndex)
            } else {
                listState.animateScrollToItem(selectedEntryIndex)
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

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = BeelineBubbleMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (caretPresentation == ChipCaretPresentation.Inline) {
            val interactionSource = remember { MutableInteractionSource() }
            // Keep the control outside LazyRow so it stays fixed and can be replaced independently.
            DestinationChipCaretButton(
                visible = visible,
                onToggleVisibility = onToggleVisibility,
                caretRotation = caretRotation,
                toggleDescription = toggleDescription,
                visibilityToggleTestTag = visibilityToggleTestTag,
                interactionSource = interactionSource,
            )
        }

        ExpandableContent(
            visible = visible,
            modifier = Modifier.weight(1f),
        ) {
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
                contentPadding = PaddingValues(start = 8.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
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
                        modifier = Modifier
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
            }
        }
    }
}

/**
 * Renders the existing inline circular chip visibility control.
 *
 * The control stays reusable for layouts that keep the caret at the start of the chip row.
 * Compact-wide hides this presentation and drives the same [visible] state from its contextual
 * caret instead.
 */
@Composable
private fun DestinationChipCaretButton(
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    caretRotation: Float,
    toggleDescription: String,
    visibilityToggleTestTag: String,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val scheme = LocalPalustrisMotionScheme.current
    // Keep the control outside LazyRow so it stays fixed and can be replaced independently.
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
