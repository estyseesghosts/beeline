package me.foxtails.palustris.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch

/**
 * Measured window bounds of the control that opens a surface, plus the focus return point.
 *
 * The shell holds one source per trigger. It owns geometry and focus return only, never the state
 * of the surface the trigger opens. The last measured bounds outlive the trigger, which usually
 * leaves composition while its surface is open. The shell calls [invalidate] on account, session,
 * and window changes; a stale source then reads as missing and the surface fades.
 */
@Stable
class TriggerSurfaceSource {
    var bounds by mutableStateOf<Rect?>(null)
        internal set

    internal val focusRequester = FocusRequester()
    internal var attached = false

    /** Forgets bounds that no longer match the screen. A trigger that is on screen keeps them. */
    fun invalidate() {
        if (!attached) bounds = null
    }

    /** Returns focus to the trigger after its surface closes. A missing trigger is ignored. */
    fun restoreFocus() {
        runCatching { focusRequester.requestFocus() }
    }
}

/** Measures this node as the trigger for [source]. Pass `null` for a trigger that opens nothing. */
@Composable
fun Modifier.triggerSurfaceSource(source: TriggerSurfaceSource?): Modifier {
    if (source == null) return this
    DisposableEffect(source) {
        source.attached = true
        onDispose { source.attached = false }
    }
    return this
        .focusRequester(source.focusRequester)
        .onGloballyPositioned { source.bounds = it.boundsInWindow() }
}

/** Pure placement rules shared by every trigger surface. */
object TriggerSurfaceGeometry {
    private const val MAX_PIVOT_FRACTION = 2f

    /**
     * Where the surface grows from, as a fraction of its own bounds. Returns `null` when the
     * surface should only fade: the trigger or surface is unmeasured or empty, or the trigger lies
     * outside [window].
     */
    fun pivot(trigger: Rect?, surface: Rect?, window: Rect? = null): TransformOrigin? {
        if (trigger == null || surface == null) return null
        if (trigger.width <= 0f || trigger.height <= 0f || surface.width <= 0f || surface.height <= 0f) return null
        if (window != null && !trigger.overlaps(window)) return null
        val x = ((trigger.center.x - surface.left) / surface.width).coerceIn(-MAX_PIVOT_FRACTION, MAX_PIVOT_FRACTION)
        val y = ((trigger.center.y - surface.top) / surface.height).coerceIn(-MAX_PIVOT_FRACTION, MAX_PIVOT_FRACTION)
        return TransformOrigin(x, y)
    }
}

/**
 * Open progress for one trigger surface: 0 closed, 1 open. Animation state only. The caller keeps
 * the content, its draft, and its dismissal rules.
 */
@Stable
class TriggerSurfaceState internal constructor(initial: Float) {
    internal val animatable = Animatable(initial)

    val progress: Float get() = animatable.value.coerceIn(0f, 1f)

    /** Fades a modal scrim with the surface so it never outlasts or leads its content. */
    fun scrim(base: Color): Color = base.copy(alpha = base.alpha * progress)
}

@Composable
fun rememberTriggerSurfaceState(): TriggerSurfaceState {
    val reduced = LocalPalustrisMotionScheme.current.reducedMotion
    return remember { TriggerSurfaceState(initial = if (reduced) 1f else 0f) }
}

/**
 * Presents [content] as a surface that grows from its trigger and returns to it on close.
 *
 * The component owns measured trigger geometry, open progress, the fade fallback, focus entry and
 * return, hiding the keyboard before it closes, and an accessible dismiss action. It does not
 * register a back handler: the shell owns back priority and calls the close it already guards.
 * [content] receives `requestClose`, which animates back to the trigger and then calls [onClosed].
 * If [onClosed] refuses (the surface stays composed), the surface opens again instead of staying
 * invisible.
 *
 * With reduced motion every transition snaps. A trigger that is missing, empty, or off screen
 * produces a fade without scale.
 *
 * @param trigger current bounds of the trigger in window coordinates, read in the draw phase.
 * @param surfaceOrigin window position of the surface when it is not the layout's own window, such
 * as a popup placed by a position provider.
 * @param returnFocus moves focus back to the trigger after closing.
 */
@Composable
fun TriggerSurface(
    state: TriggerSurfaceState,
    trigger: () -> Rect?,
    dismissLabel: String,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    window: () -> Rect? = { null },
    surfaceOrigin: () -> Offset? = { null },
    returnFocus: () -> Unit = {},
    content: @Composable (requestClose: () -> Unit) -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var surface by remember { mutableStateOf<Rect?>(null) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        state.animatable.animateTo(1f, scheme.spatial)
        runCatching { focusManager.moveFocus(FocusDirection.Enter) }
    }

    val requestClose: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                keyboard?.hide()
                focusManager.clearFocus()
                state.animatable.animateTo(0f, scheme.spatial)
                returnFocus()
                onClosed()
                // Reached only when the caller kept the surface (a guarded close declined).
                closing = false
                state.animatable.animateTo(1f, scheme.spatial)
            }
        }
    }

    Box(
        modifier
            .onGloballyPositioned { coordinates ->
                val size = coordinates.size.toSize()
                surface = surfaceOrigin()?.let { Rect(it, size) } ?: coordinates.boundsInWindow()
            }
            .graphicsLayer {
                val progress = state.progress
                alpha = progress
                val origin = if (scheme.reducedMotion) null else TriggerSurfaceGeometry.pivot(trigger(), surface, window())
                if (origin != null) {
                    val scale = lerp(scheme.floatingEnterScale, 1f, progress)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = origin
                }
            }
            .semantics { dismiss(dismissLabel) { requestClose(); true } },
    ) {
        content(requestClose)
    }
}
