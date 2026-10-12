package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.PollLimits
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class ComposerAttachMenuTest {
    @get:Rule val compose = createComposeRule()

    private val poll = PollLimits(4, 50, 300L, 2_629_746L)
    private val withPolls = PostLimits(500, PostingCapabilities(maxAttachments = 4, poll = poll))
    private val calls = mutableListOf<String>()

    private fun show(
        entry: ComposerEntryState = ComposerEntryState(text = "hi"),
        limits: PostLimits = withPolls,
        editing: Boolean = false,
        photo: PhotoButtonState = PhotoButtonState.Enabled,
        pollEnabled: Boolean = true,
        width: Int = 411,
        density: Density? = null,
    ) {
        compose.setContent {
            PalustrisTheme {
                val content = @androidx.compose.runtime.Composable {
                    Box(Modifier.width(width.dp)) {
                        ComposerToolbar(
                            entry = entry,
                            limits = limits,
                            editing = editing,
                            attachRows = composerAttachRows(
                                entry = entry,
                                editing = editing,
                                photo = photo,
                                pollSupported = limits.posting.poll != null,
                                pollEnabled = pollEnabled,
                                onPhoto = { calls += "photo" },
                                onPoll = { calls += "poll" },
                                onToggleWarning = { calls += "warning" },
                            ),
                            onEmoji = { calls += "emoji" },
                            addEnabled = true,
                            onAddEntry = { calls += "add" },
                        )
                    }
                }
                if (density == null) content() else CompositionLocalProvider(LocalDensity provides density) { content() }
            }
        }
    }

    private fun open() {
        compose.onNodeWithTag("composer_attach").performClick()
        compose.waitForIdle()
    }

    @Test
    fun theRowHoldsFourControlsAndNoneOfTheOldPhotoPollOrWarningControls() {
        show()

        compose.onNodeWithContentDescription("Add attachment").assertExists()
        compose.onNodeWithContentDescription("Open the emoji picker").assertExists()
        compose.onNodeWithContentDescription("498 characters remaining").assertExists()
        compose.onNodeWithContentDescription("Add post").assertExists()
        compose.onNodeWithContentDescription("Add photo").assertDoesNotExist()
        compose.onNodeWithTag("composer_poll_toggle").assertDoesNotExist()
        compose.onNodeWithContentDescription("Content warning").assertDoesNotExist()
    }

    @Test
    fun theMenuHoldsPhotoWarningAndPollInThatOrder() {
        show()
        open()

        compose.onNodeWithTag("composer_attach_photo").assertIsEnabled()
        compose.onNodeWithTag("composer_attach_warning").assertIsEnabled()
        compose.onNodeWithTag("composer_poll_toggle").assertIsEnabled()
        val photo = compose.onNodeWithTag("composer_attach_photo").fetchSemanticsNode().boundsInRoot
        val warning = compose.onNodeWithTag("composer_attach_warning").fetchSemanticsNode().boundsInRoot
        val pollRow = compose.onNodeWithTag("composer_poll_toggle").fetchSemanticsNode().boundsInRoot
        assertTrue(photo.top < warning.top && warning.top < pollRow.top)
    }

    @Test
    fun aServerWithoutPollsShowsNoPollRow() {
        show(limits = PostLimits(500, PostingCapabilities(maxAttachments = 4)))
        open()

        compose.onNodeWithTag("composer_attach_photo").assertExists()
        compose.onNodeWithTag("composer_poll_toggle").assertDoesNotExist()
    }

    @Test
    fun photoFollowsItsStateAndSignInAgainStaysEnabled() {
        show(photo = PhotoButtonState.Disabled)
        open()
        compose.onNodeWithTag("composer_attach_photo").assertIsNotEnabled()
    }

    @Test
    fun anEntryWithMediaShowsADisabledPollRowButAnEntryWithAPollKeepsItEnabled() {
        show(pollEnabled = false)
        open()
        compose.onNodeWithTag("composer_poll_toggle").assertIsNotEnabled()
    }

    @Test
    fun anEntryWithAPollCanRemoveItEvenWhenNewPollsAreOff() {
        show(entry = ComposerEntryState(text = "hi", poll = newPoll(poll)), pollEnabled = false)
        open()

        compose.onNodeWithTag("composer_poll_toggle").assertIsEnabled().performClick()

        assertEquals(listOf("poll"), calls)
    }

    @Test
    fun aChoiceClosesTheMenuAndRunsOnce() {
        show()
        open()

        compose.onNodeWithTag("composer_attach_warning").performClick()
        compose.waitForIdle()

        assertEquals(listOf("warning"), calls)
        compose.onNodeWithTag("composer_attach_menu").assertDoesNotExist()
    }

    @Test
    fun theWarningRowNamesTheActionItWillTake() {
        show(entry = ComposerEntryState(text = "hi", warningEnabled = true))
        open()

        compose.onNodeWithContentDescription("Remove content warning").assertExists()
    }

    @Test
    fun whileEditingTheMenuHoldsTheContentWarningRowOnly() {
        show(editing = true)
        open()

        compose.onNodeWithTag("composer_attach_warning").assertExists()
        compose.onNodeWithTag("composer_attach_photo").assertDoesNotExist()
        compose.onNodeWithTag("composer_poll_toggle").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add post").assertDoesNotExist()
    }

    @Test
    fun theCircleShowsTheSelectedToneAndAStateDescriptionWhileAWarningOrPollIsSet() {
        show(entry = ComposerEntryState(text = "hi", warningEnabled = true))

        compose.onNodeWithTag("composer_attach").assertIsSelected()
        val state = compose.onNodeWithTag("composer_attach").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertNotNull(state)
    }

    @Test
    fun theCircleIsPlainWithoutAWarningOrPoll() {
        show()

        val node = compose.onNodeWithTag("composer_attach").fetchSemanticsNode()
        assertFalse(node.config.getOrNull(SemanticsProperties.Selected) == true)
        assertEquals(null, node.config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun fourControlsDoNotOverlapAt320DpWithAFourDigitCounter() {
        show(limits = PostLimits(9999, PostingCapabilities(maxAttachments = 4, poll = poll)), width = 320)

        assertNoOverlap()
    }

    @Test
    fun fourControlsDoNotOverlapAt360DpWithFontScale2() {
        show(
            limits = PostLimits(9999, PostingCapabilities(maxAttachments = 4, poll = poll)),
            width = 360,
            density = Density(density = 2.625f, fontScale = 2f),
        )

        assertNoOverlap()
    }

    private fun assertNoOverlap() {
        val bounds: List<Rect> = listOf("Add attachment", "Open the emoji picker", "9997 characters remaining", "Add post").map {
            compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                assertFalse("control $i overlaps control $j", bounds[i].overlaps(bounds[j]))
            }
        }
    }
}
