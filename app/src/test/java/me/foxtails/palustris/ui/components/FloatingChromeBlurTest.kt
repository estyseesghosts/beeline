package me.foxtails.palustris.ui.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FloatingChromeBlurTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun oneElevationTokenServesEveryFloatingControl() {
        assertEquals(6.dp, FloatingControlElevation)
        // The blur is slightly stronger than the 12 dp of infdev 02.
        assertEquals(16.dp, FloatingChromeBlurRadius)
    }

    @Test
    fun theShadowDrawsOutsideTheNodeAndChangesNoLayout() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(56.dp).floatingControlShadow(CircleShape).testTag("control"))
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("control").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
    }

    @Test
    fun aRowUnderTheBandStaysTappable() {
        var taps = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().testTag("row").clickable { taps++ })
                        // With no backdrop (API 29 and 30, battery saver) the band draws nothing and adds no node.
                        Box(Modifier.align(Alignment.BottomCenter).height(80.dp).floatingChromeBlur().testTag("band"))
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("row").performClick()

        assertEquals(1, taps)
    }
}
