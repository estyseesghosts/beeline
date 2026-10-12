package me.foxtails.palustris.ui.components

import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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

/** Veil alpha at the bottom of the lowest control. The veil is app background color, never a tint of the content. */
internal const val CHROME_VEIL_CONTROL_ALPHA = 0.15f

/** Veil alpha at the physical screen edge. Kept below full opacity so it never reads as a solid bar. */
internal const val CHROME_VEIL_EDGE_ALPHA = 0.55f

/**
 * One rule for every bottom dock: the band comes before the inset padding, so it reaches the physical screen
 * edge and the controls inside it stay clear of the navigation bar, the IME and the floating navigation.
 */
@Composable
internal fun Modifier.floatingChromeDock(insets: WindowInsets, backdrop: ChromeBackdrop?): Modifier =
    floatingChromeBlur(backdrop = backdrop, edgeInsets = insets).windowInsetsPadding(insets)

/** The veil gradient of [veil] in [color], from nothing at the node top to the strongest alpha at the screen edge. */
private fun chromeVeilBrush(veil: ChromeVeil, color: Color): Brush {
    val span = veil.endY - veil.startY
    val control = (veil.controlY - veil.startY) / span
    val atControl = color.copy(alpha = CHROME_VEIL_CONTROL_ALPHA)
    val stops = buildList {
        if (control > 0f) add(0f to color.copy(alpha = 0f))
        add(control to atControl)
        add(1f to color.copy(alpha = CHROME_VEIL_EDGE_ALPHA))
    }
    return Brush.verticalGradient(colorStops = stops.toTypedArray(), startY = veil.startY, endY = veil.endY)
}

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
 * node where the backdrop continues, so it has no hard edge. A band that rests on the bottom screen edge also draws a
 * veil in the app background color over the zone below the lowest control ([edgeInsets] is that zone's height),
 * strongest at the edge, to hide the small change of a blurred copy of moving text. The veil is not a tint. The band has no tint. The controls separate
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
    edgeInsets: WindowInsets? = null,
): Modifier {
    if (backdrop == null) return this
    val veilColor = MaterialTheme.colorScheme.background
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
                edgeZone = edgeInsets?.getBottom(this)?.toFloat() ?: 0f,
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
                geometry.veil?.let { veil ->
                    drawRect(
                        brush = chromeVeilBrush(veil, veilColor),
                        topLeft = Offset(band.left, veil.startY),
                        size = Size(band.width, veil.endY - veil.startY),
                    )
                }
            }
        }
}
