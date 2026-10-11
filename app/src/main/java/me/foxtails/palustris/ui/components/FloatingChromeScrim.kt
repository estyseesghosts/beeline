package me.foxtails.palustris.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val FloatingChromeFadeDistance = 32.dp

private const val SCRIM_ALPHA_LIGHT = 0.30f
private const val SCRIM_ALPHA_DARK = 0.45f

/** The scrim strength at the screen edge: dark themes need more to separate controls from dark content. */
internal fun floatingChromeScrimAlpha(darkTheme: Boolean): Float = if (darkTheme) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT

/**
 * Draws the depth band behind a stack of floating controls: a vertical gradient from transparent, [fade] above the
 * top of this node, to the theme scrim at the node's bottom edge. The band belongs to the node it decorates, so it
 * follows that node when the IME moves it and leaves with it when it hides. It draws only; it takes no pointer input
 * and adds no semantics, so rows under it stay tappable where no control covers them. Place it before any inset
 * padding that should be covered, and after any that should not.
 */
@Composable
internal fun Modifier.floatingChromeScrim(fade: Dp = FloatingChromeFadeDistance): Modifier {
    val scheme = MaterialTheme.colorScheme
    val strength = floatingChromeScrimAlpha(darkTheme = scheme.surface.luminance() < 0.5f)
    val scrim = scheme.scrim
    return drawBehind {
        val fadePx = fade.toPx()
        val top = -fadePx
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, scrim.copy(alpha = strength)),
                startY = top,
                endY = size.height,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, size.height + fadePx),
        )
    }
}
