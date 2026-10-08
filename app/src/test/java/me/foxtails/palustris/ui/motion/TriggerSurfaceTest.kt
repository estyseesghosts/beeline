package me.foxtails.palustris.ui.motion

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TriggerSurfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val window = Rect(0f, 0f, 1000f, 2000f)
    private val surface = Rect(0f, 1000f, 1000f, 2000f)

    @Test
    fun pivotLocatesTheTriggerInsideTheSurface() {
        val pivot = TriggerSurfaceGeometry.pivot(Rect(900f, 1900f, 1000f, 2000f), surface, window)

        assertEquals(TransformOrigin(0.95f, 0.95f), pivot)
    }

    @Test
    fun pivotFadesWhenTheTriggerIsMissingEmptyOrOffScreen() {
        assertNull(TriggerSurfaceGeometry.pivot(null, surface, window))
        assertNull(TriggerSurfaceGeometry.pivot(Rect(10f, 10f, 10f, 10f), surface, window))
        assertNull(TriggerSurfaceGeometry.pivot(Rect(2000f, 10f, 2100f, 110f), surface, window))
        assertNull(TriggerSurfaceGeometry.pivot(Rect(0f, 0f, 10f, 10f), null, window))
        assertNull(TriggerSurfaceGeometry.pivot(Rect(0f, 0f, 10f, 10f), Rect(0f, 0f, 0f, 0f), window))
    }

    @Test
    fun pivotStaysBoundedWhenTheTriggerLiesFarFromTheSurface() {
        val pivot = TriggerSurfaceGeometry.pivot(Rect(0f, 0f, 10f, 10f), Rect(0f, 1990f, 10f, 2000f), null)

        assertNotNull(pivot)
        assertEquals(-2f, pivot!!.pivotFractionY, 0f)
    }

    @Test
    fun sourceKeepsBoundsOnlyWhileItsTriggerIsAttachedOrUntilInvalidated() {
        val source = TriggerSurfaceSource()
        source.bounds = Rect(0f, 0f, 10f, 10f)

        source.attached = true
        source.invalidate()
        assertNotNull(source.bounds)

        source.attached = false
        source.invalidate()
        assertNull(source.bounds)
    }

    @Test
    fun scrimFollowsOpenProgress() {
        val state = TriggerSurfaceState(initial = 0.5f)

        assertEquals(0.2f, state.scrim(Color.Black.copy(alpha = 0.4f)).alpha, 0.001f)
    }

    @Test
    fun surfaceOpensAndRequestCloseReachesTheCallerAfterTheReverseAnimation() {
        var closed = 0
        var focusReturned = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val state = rememberTriggerSurfaceState()
                TriggerSurface(
                    state = state,
                    trigger = { Rect(0f, 0f, 48f, 48f) },
                    dismissLabel = "Close",
                    onClosed = { closed++ },
                    returnFocus = { focusReturned++ },
                ) { requestClose ->
                    Box(Modifier.size(100.dp).testTag("surface").clickable { requestClose() }) { Text("Surface body") }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Surface body").assertExists()
        assertEquals(0, closed)

        compose.onNodeWithTag("surface").performClick()
        compose.waitForIdle()

        assertEquals(1, closed)
        assertEquals(1, focusReturned)
    }

    @Test
    fun accessibleDismissActionClosesTheSurface() {
        var closed = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val state = rememberTriggerSurfaceState()
                TriggerSurface(
                    state = state,
                    trigger = { null },
                    dismissLabel = "Close",
                    onClosed = { closed++ },
                    modifier = Modifier.testTag("host"),
                ) { Text("Body") }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("host").performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()

        assertEquals(1, closed)
    }

    @Test
    fun declinedCloseOpensTheSurfaceAgainAndAllowsAnotherClose() {
        var attempts = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val state = rememberTriggerSurfaceState()
                TriggerSurface(
                    state = state,
                    trigger = { Rect(0f, 0f, 48f, 48f) },
                    dismissLabel = "Close",
                    // A guarded close declines the first time and leaves the surface composed.
                    onClosed = { attempts++ },
                    modifier = Modifier.testTag("host"),
                ) { Text("Body") }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("host").performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
        compose.onNodeWithTag("host").performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()

        assertEquals(2, attempts)
    }

    @Test
    fun reducedMotionStartsOpenAndClosesInTheNextFrames() {
        var closed = 0
        var opened = -1f
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalPalustrisMotionScheme provides PalustrisMotionScheme.standard(true)) {
                    val state = rememberTriggerSurfaceState()
                    opened = state.progress
                    TriggerSurface(
                        state = state,
                        trigger = { Rect(0f, 0f, 48f, 48f) },
                        dismissLabel = "Close",
                        onClosed = { closed++ },
                        modifier = Modifier.testTag("host"),
                    ) { Text("Body") }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(1f, opened, 0f)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("host").performSemanticsAction(SemanticsActions.Dismiss)
        repeat(4) { compose.mainClock.advanceTimeByFrame() }

        assertEquals(1, closed)
    }
}
