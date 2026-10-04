package me.foxtails.palustris.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.rememberSelectedScale
import me.foxtails.palustris.ui.motion.springPress
import kotlin.math.roundToInt

/** Renders one stateless navigation target; the caller owns selection and navigation callbacks. */
@Composable
internal fun NavigationButton(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: (@Composable (Modifier) -> Unit)? = null,
) {
    val scheme = LocalPalustrisMotionScheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val pressColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val selectedTint = rememberSelectedColor(
        selected,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val selectedScale = rememberSelectedScale(selected)
    val targetModifier = modifier
        .size(48.dp)
        .springPress(interactionSource, pressedScale = scheme.compactPressedScale)
        .bubblePressLayer(pressed, pressColor, CircleShape)
        .then(
            if (onLongClick == null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
            } else {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            },
        )
        .navigationButtonSemantics(label, selected)

    Box(targetModifier, contentAlignment = Alignment.Center) {
        val scaledContent = Modifier.graphicsLayer {
            scaleX = selectedScale
            scaleY = selectedScale
        }
        if (content != null) {
            content(scaledContent)
        } else if (icon != null) {
            Icon(icon, null, scaledContent, tint = selectedTint)
        }
    }
}

/**
 * Draws a shared capsule and its single traveling indicator without owning selection state.
 * The caller supplies bounds and lays out [itemCount] 48 dp targets with [Arrangement.SpaceEvenly],
 * centered on the cross-axis.
 */
@Composable
internal fun NavigationCapsule(
    selectedIndex: Int,
    itemCount: Int,
    orientation: Orientation,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    require(itemCount > 0) { "A navigation capsule requires at least one item." }
    require(selectedIndex in 0 until itemCount) { "The selected navigation index must be in range." }

    val scheme = LocalPalustrisMotionScheme.current
    val animatedPosition = if (scheme.reducedMotion) {
        null
    } else {
        animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = scheme.spatial,
            label = "navigationSelectionPosition",
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        shadowElevation = 6.dp,
    ) {
        val capsulePadding = if (orientation == Orientation.Horizontal) {
            Modifier.padding(horizontal = 4.dp)
        } else {
            Modifier.padding(vertical = 4.dp)
        }
        BoxWithConstraints(Modifier.fillMaxSize().then(capsulePadding)) {
            Surface(
                modifier = Modifier
                    .align(if (orientation == Orientation.Horizontal) Alignment.CenterStart else Alignment.TopCenter)
                    .offset {
                        val mainAxisLengthPx =
                            (if (orientation == Orientation.Horizontal) maxWidth else maxHeight).roundToPx()
                        val targetSize = 48.dp.roundToPx()
                        val indicatorSize = 40.dp.roundToPx()
                        // SpaceEvenly's outer and inter-item gaps use this same division.
                        val gap = (mainAxisLengthPx - targetSize * itemCount) / (itemCount + 1f)
                        // Reduced motion uses the selected slot directly instead of waiting for an animation frame.
                        val selectedPosition = animatedPosition?.value ?: selectedIndex.toFloat()
                        val selectedOffset = (gap + (targetSize + gap) * selectedPosition).roundToInt() +
                            (targetSize - indicatorSize) / 2
                        if (orientation == Orientation.Horizontal) IntOffset(selectedOffset, 0)
                        else IntOffset(0, selectedOffset)
                    }
                    .size(40.dp)
                    .testTag("selected_navigation_indicator"),
                shape = BeelineBubbleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {}
            content()
        }
    }
}
