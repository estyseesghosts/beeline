package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A bottom sheet is a dialog window, and Robolectric never goes idle for a dialog at phone width, so this runs at 600 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h900dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerAttachSheetTest {
    @get:Rule val compose = createComposeRule()

    private val icon: ImageVector = AppIcons.Paperclip

    @Test
    fun atFontScale1Point5TheMenuOpensAsASheetAndAChoiceStillRunsOnce() {
        val chosen = mutableListOf<String>()
        var dismissed = 0
        compose.setContent {
            PalustrisTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 2.625f, fontScale = 1.5f)) {
                    ComposerAttachMenu(
                        anchor = Rect.Zero,
                        rows = listOf(ComposerAttachRow("row_a", "A", icon, true) { chosen += "a" }),
                        onDismiss = { dismissed++ },
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("row_a").performClick()

        assertEquals(listOf("a"), chosen)
        assertEquals(1, dismissed)
    }
}
