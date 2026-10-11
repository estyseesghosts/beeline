package me.foxtails.palustris.ui.components

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal val FloatingChromeFadeDistance = 32.dp
internal val FloatingChromeBlurRadius = 12.dp

private const val SCRIM_ALPHA_LIGHT = 0.18f
private const val SCRIM_ALPHA_DARK = 0.28f

/** The scrim strength at the screen edge: dark themes need more to separate controls from dark content. */
internal fun floatingChromeScrimAlpha(darkTheme: Boolean): Float = if (darkTheme) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT

/**
 * One rule for every bottom dock: the band comes before the inset padding, so it reaches the physical screen
 * edge and the controls inside it stay clear of the navigation bar, the IME and the floating navigation.
 */
@Composable
internal fun Modifier.floatingChromeDock(insets: androidx.compose.foundation.layout.WindowInsets, backdrop: ChromeBackdrop?): Modifier =
    floatingChromeScrim(backdrop = backdrop).windowInsetsPadding(insets)

/**
 * Draws the depth band behind a stack of floating controls: a vertical gradient from transparent, [fade] above the
 * top of this node, to the theme scrim at the node's bottom edge. The band belongs to the node it decorates, so it
 * follows that node when the IME moves it and leaves with it when it hides. It draws only; it takes no pointer input
 * and adds no semantics, so rows under it stay tappable where no control covers them. Place it before any inset
 * padding that should be covered, and after any that should not. With a [backdrop], the band also draws a blurred copy
 * of the content under it, faded in by the same gradient; without one the gradient stands alone.
 */
@Composable
internal fun Modifier.floatingChromeScrim(
    fade: Dp = FloatingChromeFadeDistance,
    backdrop: ChromeBackdrop? = null,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val strength = floatingChromeScrimAlpha(darkTheme = scheme.surface.luminance() < 0.5f)
    val scrim = scheme.scrim
    val bandLayer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .then(if (backdrop != null) Modifier.onGloballyPositioned { origin = it.positionInRoot() } else Modifier)
        .drawBehind {
            val fadePx = fade.toPx()
            val top = -fadePx
            if (backdrop != null && size.width > 0f) {
                val blur = FloatingChromeBlurRadius.toPx()
                val bandSize = IntSize(size.width.roundToInt(), (size.height + fadePx).roundToInt())
                bandLayer.renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
                bandLayer.compositingStrategy = CompositingStrategy.Offscreen
                bandLayer.record(size = bandSize) {
                    translate(backdrop.sourceOrigin.x - origin.x, backdrop.sourceOrigin.y - (origin.y - fadePx)) {
                        drawLayer(backdrop.layer)
                    }
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                        blendMode = BlendMode.DstIn,
                    )
                }
                translate(top = top) { drawLayer(bandLayer) }
            }
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
