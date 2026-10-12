package me.foxtails.palustris.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** Which screen edge the floating controls rest on. The blur fades in away from that edge. */
internal enum class ChromeEdge { Bottom, Top }

/** How many blur radii of real content the band records beyond its own edge, so the blur has something to read. */
internal const val CHROME_BLUR_REACH_FACTOR = 2f

/**
 * Where one blur band records, draws and fades, all in the node's own coordinates (pixels).
 *
 * @property band the area the band draws: the node plus a fade on each side that faces content
 * @property recorded the area that is recorded and blurred. It is the band plus the blur reach on every side where
 * the backdrop still has content. At a screen edge the backdrop ends, so the recorded area ends there too.
 * @property opaqueTop where the mask reaches full strength coming from the top of the band
 * @property opaqueBottom where the mask starts to fade again toward the bottom of the band
 * @property veil the edge veil over the node, or null when the band does not rest on the bottom screen edge
 */
internal data class ChromeBandGeometry(
    val band: Rect,
    val recorded: Rect,
    val opaqueTop: Float,
    val opaqueBottom: Float,
    val veil: ChromeVeil? = null,
)

/**
 * The edge veil in node coordinates: alpha 0 at [startY], a low alpha at [controlY] (the bottom of the lowest
 * control), and the highest alpha at [endY] (the screen edge). It hides the small frame-to-frame change of a
 * blurred copy of moving text in the zone that holds no control.
 */
internal data class ChromeVeil(val startY: Float, val controlY: Float, val endY: Float)

/**
 * The pure geometry of one blur band. The mask is transparent at the far end of the fade, fully strong across the
 * whole node, so every control in the node sits on a fully blurred backdrop, and fades out again past the node on
 * the side where the backdrop continues. The side that faces the content always fades over [fade]. The side at
 * the [edge] fades only as far as the backdrop reaches past the node, which is not at all at the
 * screen edge itself. Returns null when there is nothing to draw: an empty node, or no backdrop content under the
 * band.
 *
 * @param nodeSize size of the node that carries the band
 * @param nodeOrigin where the node starts, in root coordinates
 * @param sourceBounds the recorded backdrop in root coordinates
 * @param fade length of the fade beyond the node
 * @param reach how far the blur reads beyond the band
 * @param edgeZone height of the zone below the lowest control, down to the screen edge. A band that rests on the
 * bottom screen edge gets a veil when this is above zero; any other band gets none.
 */
internal fun chromeBandGeometry(
    nodeSize: Size,
    nodeOrigin: Offset,
    sourceBounds: Rect,
    fade: Float,
    reach: Float,
    edge: ChromeEdge,
    edgeZone: Float = 0f,
): ChromeBandGeometry? {
    if (nodeSize.width <= 0f || nodeSize.height <= 0f) return null
    val source = sourceBounds.translate(-nodeOrigin.x, -nodeOrigin.y)
    val bottom = edge == ChromeEdge.Bottom
    // Content continues past the node on its edge side only when the dock is lifted off the screen edge.
    val trailing = (if (bottom) source.bottom - nodeSize.height else -source.top).coerceIn(0f, fade)
    val band = Rect(
        left = 0f,
        top = -(if (bottom) fade else trailing),
        right = nodeSize.width,
        bottom = nodeSize.height + (if (bottom) trailing else fade),
    )
    val recorded = band.inflate(reach).intersect(source)
    if (recorded.width <= 0f || recorded.height <= 0f) return null
    val veil = if (bottom && trailing <= 0f && edgeZone > 0f) {
        ChromeVeil(startY = 0f, controlY = (nodeSize.height - edgeZone).coerceAtLeast(0f), endY = nodeSize.height)
    } else {
        null
    }
    return ChromeBandGeometry(band = band, recorded = recorded, opaqueTop = 0f, opaqueBottom = nodeSize.height, veil = veil)
}
