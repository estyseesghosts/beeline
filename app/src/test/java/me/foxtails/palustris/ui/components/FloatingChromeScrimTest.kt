package me.foxtails.palustris.ui.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FloatingChromeScrimTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun darkThemesGetAStrongerBandThanLightOnes() {
        assertEquals(0.18f, floatingChromeScrimAlpha(darkTheme = false), 0.001f)
        assertEquals(0.28f, floatingChromeScrimAlpha(darkTheme = true), 0.001f)
        assertTrue(floatingChromeScrimAlpha(true) > floatingChromeScrimAlpha(false))
    }

    @Test
    fun aRowUnderTheBandStaysTappable() {
        var taps = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().testTag("row").clickable { taps++ })
                        Box(Modifier.align(Alignment.BottomCenter).height(80.dp).floatingChromeScrim().testTag("band"))
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("row").performClick()

        assertEquals(1, taps)
    }
}
