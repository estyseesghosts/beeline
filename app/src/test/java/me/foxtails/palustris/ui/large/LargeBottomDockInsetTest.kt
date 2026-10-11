package me.foxtails.palustris.ui.large

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The dock band reaches the screen edge, so it holds the bottom inset inside itself. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LargeBottomDockInsetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dockGrowsByTheBottomInsetAndKeepsItsContentAbove() {
        compose.setContent {
            CompositionLocalProvider(LocalLargeBottomInset provides 30.dp) {
                LargeBottomDock(
                    modifier = Modifier.testTag("dock"),
                    content = { Box(Modifier.size(100.dp, 40.dp)) },
                )
            }
        }

        compose.onNodeWithTag("dock").assertHeightIsEqualTo(40.dp + LargeBottomDockEdgeGap * 2f + 30.dp)
    }

    @Test
    fun dockHasNoExtraHeightWhenAnotherBarOwnsTheInset() {
        compose.setContent {
            LargeBottomDock(
                modifier = Modifier.testTag("dock"),
                content = { Box(Modifier.height(40.dp)) },
            )
        }

        compose.onNodeWithTag("dock").assertHeightIsEqualTo(40.dp + LargeBottomDockEdgeGap * 2f)
    }
}
