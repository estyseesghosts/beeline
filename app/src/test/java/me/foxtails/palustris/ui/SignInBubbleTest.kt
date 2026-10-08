package me.foxtails.palustris.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.session.SessionUi
import me.foxtails.palustris.ui.setup.SetupDestination
import me.foxtails.palustris.ui.setup.SetupServerScreen
import me.foxtails.palustris.ui.setup.setupTransitionAnimates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignInBubbleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val submitted = mutableListOf<String>()
    private var completed = 0
    private var reopened = 0

    private fun show(state: SessionUi = SessionUi(starting = false), fontScale: Float = 1f) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        SetupServerScreen(
                            state = state,
                            onNext = { submitted += it },
                            onComplete = { completed++ },
                            onReopen = { reopened++ },
                            onCancel = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private val density get() = compose.activity.resources.displayMetrics.density

    @Test
    fun handoffToAndFromThePendingScreenDoesNotAnimate() {
        assertTrue(setupTransitionAnimates(SetupDestination.Initial, SetupDestination.Server, reducedMotion = false))
        assertFalse(setupTransitionAnimates(SetupDestination.Server, SetupDestination.Pending, reducedMotion = false))
        assertFalse(setupTransitionAnimates(SetupDestination.Pending, SetupDestination.Initial, reducedMotion = false))
        assertFalse(setupTransitionAnimates(SetupDestination.Initial, SetupDestination.Server, reducedMotion = true))
    }

    @Test
    fun pastedLongOriginIsKeptAndSubmittedThroughAuthenticationValidation() {
        val origin = "https://" + "abcdefgh.".repeat(25) + "example.org"
        show()

        compose.onNodeWithText("next").assertIsNotEnabled()
        compose.onNodeWithTag("setup_server_field").performTextInput(origin)
        compose.onNodeWithText("next").assertIsEnabled().performClick()

        assertEquals(1, submitted.size)
        assertTrue(submitted.single().startsWith("https://abcdefgh.abcdefgh."))
    }

    @Test
    fun pastedAddressWithSpacesIsRejectedWithAMessageAndNeverSubmitted() {
        show()

        compose.onNodeWithTag("setup_server_field").performTextInput("exam ple.org")

        compose.onNodeWithText("A server address cannot contain spaces.").assertIsDisplayed()
        compose.onNodeWithText("next").assertIsNotEnabled()
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun invalidOriginShowsTheValidationMessageAndAllowsRetry() {
        show()
        compose.onNodeWithTag("setup_server_field").performTextInput("http://")
        compose.onNodeWithText("next").performClick()

        assertTrue(submitted.isEmpty())
        compose.onNode(hasSetTextAction()).assertTextContains("http://")

        compose.onNodeWithTag("setup_server_field").performTextReplacement("sharkey.world")
        compose.onNodeWithText("next").performClick()
        assertEquals(listOf("https://sharkey.world"), submitted)
    }

    @Test
    fun fieldAndNextStayReachableAndUnclippedAtLargeFont() {
        show(fontScale = 2f)

        compose.onNodeWithTag("setup_server_field").assertIsDisplayed()
        compose.onNodeWithTag("setup_server_field").performTextInput("sharkey.world")
        val field = compose.onNodeWithTag("setup_server_field").fetchSemanticsNode().boundsInRoot
        assertTrue("field keeps at least its 64 dp bubble height", field.height >= 64f * density - 1f)
        compose.onNodeWithText("next").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun pendingScreenReportsFailureAndRetriesThroughTheSameCallbacks() {
        show(SessionUi(starting = false, pending = true, origin = "https://example.org", error = "Could not reach the server."))

        compose.onNodeWithText("Could not reach the server.").assertIsDisplayed()
        compose.onNodeWithText("Open browser again").performClick()
        compose.onNodeWithText("I've authorized access").performClick()

        assertEquals(1, reopened)
        assertEquals(1, completed)
    }

    @Test
    fun pendingActionsAreDisabledWhileBusy() {
        show(SessionUi(starting = false, pending = true, origin = "https://example.org", busy = true))

        compose.onNodeWithText("Open browser again").assertIsNotEnabled()
        compose.onNodeWithText("I've authorized access").assertIsNotEnabled()
    }
}
