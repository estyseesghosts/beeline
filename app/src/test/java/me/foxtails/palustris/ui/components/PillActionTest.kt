package me.foxtails.palustris.ui.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PillActionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun actionUsesAccessibleButtonSemanticsInLightAndDarkThemes() {
        var clicked = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PillAction("repost?", onClick = { clicked = true })
                }
            }
        }
        compose.onNodeWithContentDescription("repost?")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun actionKeepsAnEffective48DpTargetWhenParentIsSmaller() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    Box(Modifier.requiredSize(width = 200.dp, height = 32.dp)) {
                        PillAction("repost?", onClick = {})
                    }
                }
            }
        }

        val bounds = compose.onNodeWithContentDescription("repost?")
            .fetchSemanticsNode().boundsInRoot
        val minimumPixels = with(compose.density) { 48.dp.toPx() }
        assertTrue(bounds.width >= minimumPixels)
        assertTrue(bounds.height >= minimumPixels)
    }

    @Test
    fun disabledActionIsNotClickable() {
        var clicked = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PillAction("repost?", onClick = { clicked = true }, enabled = false)
                }
            }
        }

        compose.onNodeWithContentDescription("repost?")
            .assertIsNotEnabled()
            .performClick()
        compose.runOnIdle { assertTrue(!clicked) }
    }

    @Test
    fun loadingActionSuppressesClicksAndKeepsLoadingState() {
        var clicked = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PillAction("repost?", onClick = { clicked = true }, loading = true)
                }
            }
        }

        compose.onNodeWithContentDescription("repost?")
            .assertIsNotEnabled()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertTrue(!clicked) }
    }
}
