package me.foxtails.palustris.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** How far the blur fades in beyond the controls, so it never starts with a hard edge. */
internal val FloatingChromeFadeDistance = 32.dp
internal val FloatingChromeBlurRadius = 16.dp

/** Which screen edge the floating controls rest on. The blur fades in away from that edge. */
internal enum class ChromeEdge { Bottom, Top }

/**
 * One rule for every bottom dock: the band comes before the inset padding, so it reaches the physical screen
 * edge and the controls inside it stay clear of the navigation bar, the IME and the floating navigation.
 */
@Composable
internal fun Modifier.floatingChromeDock(insets: WindowInsets, backdrop: ChromeBackdrop?): Modifier =
    floatingChromeBlur(backdrop = backdrop).windowInsetsPadding(insets)

/**
 * Draws the blur band behind a stack of floating controls: a blurred copy of the content under it, faded in by an
 * alpha mask that starts [fade] beyond the node and is fully strong at the node's [edge]. The band has no tint. The
 * controls separate from the content with their own drop shadow ([floatingControlShadow]).
 *
 * The band belongs to the node it decorates, so it follows that node when the IME moves it and leaves with it when
 * it hides. It draws only; it takes no pointer input and adds no semantics, so rows under it stay tappable where no
 * control covers them. Place it before any inset padding that should be covered, and after any that should not.
 * Without a [backdrop] (API 29 and 30, battery saver) it draws nothing.
 */
@Composable
internal fun Modifier.floatingChromeBlur(
    fade: Dp = FloatingChromeFadeDistance,
    backdrop: ChromeBackdrop? = null,
    edge: ChromeEdge = ChromeEdge.Bottom,
): Modifier {
    if (backdrop == null) return this
    val bandLayer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            if (size.width <= 0f) return@drawBehind
            val fadePx = fade.toPx()
            val blur = FloatingChromeBlurRadius.toPx()
            val bottom = edge == ChromeEdge.Bottom
            // The band spans the node plus the fade, on the side that faces the content.
            val bandTop = if (bottom) -fadePx else 0f
            val bandSize = IntSize(size.width.roundToInt(), (size.height + fadePx).roundToInt())
            bandLayer.renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
            bandLayer.compositingStrategy = CompositingStrategy.Offscreen
            bandLayer.record(size = bandSize) {
                translate(backdrop.sourceOrigin.x - origin.x, backdrop.sourceOrigin.y - (origin.y + bandTop)) {
                    drawLayer(backdrop.layer)
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        if (bottom) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            translate(top = bandTop) { drawLayer(bandLayer) }
        }
}
