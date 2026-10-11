package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.ui.components.BeelineBubbleMinHeight
import me.foxtails.palustris.ui.components.BeelineBubbleShape

/** How a composer bubble reads: plain, picked by the user, or the one emphasized action. */
internal enum class ComposerBubbleTone { Plain, Selected, Emphasized }

private const val DISABLED_CONTENT_ALPHA = 0.38f

/**
 * One opaque bubble of the composer. [onClick] null makes a read-only bubble. The bubble holds no
 * state; the composer owner decides tone and availability.
 */
@Composable
private fun ComposerBubble(
    shape: Shape,
    tone: ComposerBubbleTone,
    enabled: Boolean,
    description: String,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = when (tone) {
        ComposerBubbleTone.Plain -> colors.surfaceContainerHigh
        ComposerBubbleTone.Selected -> colors.primaryContainer
        ComposerBubbleTone.Emphasized -> colors.primary
    }
    val content0 = when (tone) {
        ComposerBubbleTone.Plain -> colors.onSurface
        ComposerBubbleTone.Selected -> colors.onPrimaryContainer
        ComposerBubbleTone.Emphasized -> colors.onPrimary
    }
    val semantic = modifier.requiredSizeIn(minWidth = BeelineBubbleMinHeight, minHeight = BeelineBubbleMinHeight)
        .semantics { contentDescription = description; selected = tone == ComposerBubbleTone.Selected }
    val contentColor = if (enabled) content0 else content0.copy(alpha = DISABLED_CONTENT_ALPHA)
    val body: @Composable () -> Unit = {
        Row(
            Modifier.requiredSizeIn(minWidth = BeelineBubbleMinHeight, minHeight = BeelineBubbleMinHeight).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
    if (onClick == null) {
        Surface(semantic, shape = shape, color = container, contentColor = contentColor, content = body)
    } else {
        Surface(onClick, semantic, enabled = enabled, shape = shape, color = container, contentColor = contentColor, content = body)
    }
}

/** An icon in a circle. */
@Composable
internal fun ComposerCircleBubble(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ComposerBubbleTone = ComposerBubbleTone.Plain,
) {
    ComposerBubble(CircleShape, tone, enabled, description, onClick, modifier.size(BeelineBubbleMinHeight)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
    }
}

/** A label in a pill, with an optional leading icon. [onClick] null gives a read-only pill. */
@Composable
internal fun ComposerPillBubble(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    description: String = label,
    enabled: Boolean = true,
    tone: ComposerBubbleTone = ComposerBubbleTone.Plain,
    leadingIcon: ImageVector? = null,
    leading: @Composable (() -> Unit)? = null,
    labelColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    ComposerBubble(BeelineBubbleShape, tone, enabled, description, onClick, modifier) {
        leading?.invoke()
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
