package me.foxtails.palustris.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.rememberSelectedScale
import me.foxtails.palustris.ui.motion.springPress

/**
 * Renders one destination chip: its selected colors, press animation, selection scale, test tag,
 * and accessibility semantics. The caller owns the entry and its callback.
 */
@Composable
internal fun DestinationFilterChip(entry: FilterChipEntry, modifier: Modifier = Modifier) {
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
    // A long press that opens a menu must not also select the chip when the finger lifts.
    val windowBounds = remember { arrayOf(Rect.Zero) }
    val longPressed = remember { booleanArrayOf(false) }
    val longClick = entry.onLongClick
    FilterChip(
        selected = entry.selected,
        onClick = {
            if (longPressed[0]) longPressed[0] = false else entry.onClick()
        },
        enabled = entry.enabled,
        interactionSource = interactionSource,
        label = { Text(entry.label) },
        trailingIcon = entry.onDismiss?.let { dismiss ->
            {
                Icon(
                    AppIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).clickable(onClick = dismiss),
                )
            }
        },
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
            .onGloballyPositioned { windowBounds[0] = it.boundsInWindow() }
            .then(
                if (longClick == null) {
                    Modifier
                } else {
                    // The chip's own click handler consumes the press, so watch it in the Initial pass.
                    Modifier.pointerInput(longClick) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            longPressed[0] = false
                            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                    // A release is a tap and a drag is a scroll. Neither is a long press.
                                    if (!change.pressed ||
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        return@withTimeoutOrNull true
                                    }
                                }
                                @Suppress("UNREACHABLE_CODE")
                                false
                            }
                            if (ended == null) {
                                longPressed[0] = true
                                longClick(windowBounds[0])
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.id == down.id && it.pressed })
                            }
                        }
                    }
                },
            )
            .then(entry.testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .semantics {
                contentDescription = entry.contentDescription
                role = entry.role
                this.selected = entry.selected
                entry.stateDescription?.let { stateDescription = it }
                val dismiss = entry.onDismiss
                val actions = buildList {
                    if (dismiss != null) {
                        add(
                            CustomAccessibilityAction(entry.dismissDescription ?: entry.label) {
                                dismiss()
                                true
                            },
                        )
                    }
                    if (longClick != null) {
                        add(
                            CustomAccessibilityAction(entry.longClickLabel ?: entry.label) {
                                longClick(windowBounds[0])
                                true
                            },
                        )
                    }
                }
                if (actions.isNotEmpty()) customActions = actions
            },
        shape = BeelineBubbleShape,
    )
}
