package me.foxtails.palustris.ui.components

import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The one drop shadow of every control that floats over scrolling content: the navigation pill, the contextual
 * buttons, the chips, the search field, the composer bubbles and the post action bubbles. No control sets its own
 * elevation number. A control built on `Surface` passes this token as `shadowElevation`; any other control uses
 * [floatingControlShadow].
 */
internal val FloatingControlElevation = 6.dp

/** Draws [FloatingControlElevation] around a control of [shape]. It draws outside the node and changes no layout. */
internal fun Modifier.floatingControlShadow(shape: Shape): Modifier = shadow(FloatingControlElevation, shape, clip = false)

/** Material chip elevation for a floating filter chip: the same shadow in every state. */
@Composable
internal fun floatingFilterChipElevation(): SelectableChipElevation = FilterChipDefaults.filterChipElevation(
    elevation = FloatingControlElevation,
    pressedElevation = FloatingControlElevation,
    focusedElevation = FloatingControlElevation,
    hoveredElevation = FloatingControlElevation,
    draggedElevation = FloatingControlElevation,
    disabledElevation = FloatingControlElevation,
)

/** Material chip elevation for a floating assist chip: the same shadow in every state. */
@Composable
internal fun floatingAssistChipElevation(): ChipElevation = AssistChipDefaults.assistChipElevation(
    elevation = FloatingControlElevation,
    pressedElevation = FloatingControlElevation,
    focusedElevation = FloatingControlElevation,
    hoveredElevation = FloatingControlElevation,
    draggedElevation = FloatingControlElevation,
    disabledElevation = FloatingControlElevation,
)
