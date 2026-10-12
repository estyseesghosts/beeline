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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt

/** How far the blur fades in beyond the controls, so it never starts with a hard edge. */
internal val FloatingChromeFadeDistance = 32.dp
internal val FloatingChromeBlurRadius = 16.dp

/**
 * One rule for every bottom dock: the band comes before the inset padding, so it reaches the physical screen
 * edge and the controls inside it stay clear of the navigation bar, the IME and the floating navigation.
 */
@Composable
internal fun Modifier.floatingChromeDock(insets: WindowInsets, backdrop: ChromeBackdrop?): Modifier =
    floatingChromeBlur(backdrop = backdrop).windowInsetsPadding(insets)

/** The alpha mask of a band in recorded coordinates: transparent at both ends, full strength across the node. */
private fun chromeMaskBrush(geometry: ChromeBandGeometry, recordedTop: Float): Brush {
    val start = geometry.band.top - recordedTop
    val span = geometry.band.height
    val open = Color.Black
    val clear = Color.Transparent
    val stops = buildList {
        val rise = (geometry.opaqueTop - geometry.band.top) / span
        val fall = (geometry.opaqueBottom - geometry.band.top) / span
        if (rise > 0f) add(0f to clear)
        add(rise to open)
        add(fall to open)
        if (fall < 1f) add(1f to clear)
    }
    return Brush.verticalGradient(colorStops = stops.toTypedArray(), startY = start, endY = start + span)
}

/**
 * Draws the blur band behind a stack of floating controls: a blurred copy of the content under it, faded in by an
 * alpha mask that is transparent [fade] beyond the node and fully strong across the whole node, so every control of
 * the stack sits on a fully blurred backdrop. A band that does not rest on the screen edge also fades out past the
 * node where the backdrop continues, so it has no hard edge. The band has no tint. The controls separate
 * from the content with their own drop shadow ([floatingControlShadow]).
 *
 * The band records more than it draws: the blur reads real content beyond the band wherever the backdrop continues,
 * and mirrors the last rows at a screen edge, so no single edge row dominates ([chromeBandGeometry]).
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
    val blurPx = with(LocalDensity.current) { FloatingChromeBlurRadius.toPx() }
    // One effect for each band and density; the draw block never allocates it.
    val blurEffect = remember(blurPx) { BlurEffect(blurPx, blurPx, TileMode.Mirror) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            val geometry = chromeBandGeometry(
                nodeSize = size,
                nodeOrigin = origin,
                sourceBounds = Rect(backdrop.sourceOrigin, backdrop.sourceSize.toSize()),
                fade = fade.toPx(),
                reach = blurPx * CHROME_BLUR_REACH_FACTOR,
                edge = edge,
            ) ?: return@drawBehind
            val recorded = geometry.recorded
            if (bandLayer.renderEffect !== blurEffect) bandLayer.renderEffect = blurEffect
            bandLayer.compositingStrategy = CompositingStrategy.Offscreen
            bandLayer.record(size = IntSize(recorded.width.roundToInt(), recorded.height.roundToInt())) {
                translate(backdrop.sourceOrigin.x - origin.x - recorded.left, backdrop.sourceOrigin.y - origin.y - recorded.top) {
                    drawLayer(backdrop.layer)
                }
                drawRect(brush = chromeMaskBrush(geometry, recorded.top), blendMode = BlendMode.DstIn)
            }
            val band = geometry.band
            clipRect(band.left, band.top, band.right, band.bottom) {
                translate(recorded.left, recorded.top) { drawLayer(bandLayer) }
            }
        }
}
