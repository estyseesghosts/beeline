package me.foxtails.palustris.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import me.foxtails.palustris.ui.PalustrisApp
import me.foxtails.palustris.ui.shell.AppShellFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchPanelRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun restoredPhotoGridModeRestoresItsNavigationIcon() {
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent { AppShellFixtures.app() }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithContentDescription("Photo grid").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("Photo grid").assertIsSelected()
    }

    @Test
    fun matchingSessionRestoresSearchQueryAfterFirstEffects() {
        val account = AppShellFixtures.account("search-owner")
        val restorationTester = StateRestorationTester(compose)
        var current = account
        var revision = 7L
        restorationTester.setContent {
            AppShellFixtures.app(account = current, sessionRevision = revision)
        }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("photography")

        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("photography").assertIsDisplayed()
    }

    @Test
    fun differentAccountClearsRestoredSearchQuery() {
        val account = AppShellFixtures.account("search-owner")
        val other = AppShellFixtures.account("search-other")
        val restorationTester = StateRestorationTester(compose)
        var current = account
        restorationTester.setContent {
            AppShellFixtures.app(account = current, sessionRevision = 7L)
        }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("photography")

        current = other
        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("photography").assertDoesNotExist()
    }
}
