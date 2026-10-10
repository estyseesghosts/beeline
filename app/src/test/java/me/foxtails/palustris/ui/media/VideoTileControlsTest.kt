package me.foxtails.palustris.ui.media

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.domain.Attachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoTileControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun inactiveTileShowsItsDuration() {
        compose.setContent {
            VideoTileControls(active = false, unmuted = false, paused = false, onToggleSound = {}, onTogglePause = {}, durationMs = 75_000L)
        }
        compose.onNodeWithTag("video_duration").assertIsDisplayed()
        compose.onNodeWithTag("video_duration").assertTextEquals("1:15")
    }

    @Test
    fun tileWithoutADurationShowsNoBadge() {
        compose.setContent {
            VideoTileControls(active = false, unmuted = false, paused = false, onToggleSound = {}, onTogglePause = {})
        }
        assertEquals(0, compose.onAllNodes(hasTestTag("video_duration")).fetchSemanticsNodes().size)
    }

    @Test
    fun activeTileHidesTheDurationBadge() {
        compose.setContent {
            VideoTileControls(active = true, unmuted = false, paused = false, onToggleSound = {}, onTogglePause = {}, durationMs = 75_000L)
        }
        assertEquals(0, compose.onAllNodes(hasTestTag("video_duration")).fetchSemanticsNodes().size)
    }

    @Test
    fun aFailedTileOffersARetryThatCallsBack() {
        var retried = 0
        compose.setContent {
            VideoTileControls(
                active = false,
                unmuted = false,
                paused = false,
                onToggleSound = {},
                onTogglePause = {},
                durationMs = 10_000L,
                failed = true,
                onRetry = { retried++ },
            )
        }
        compose.onNodeWithTag("video_retry").assertIsDisplayed().performClick()
        assertEquals(1, retried)
        assertEquals(0, compose.onAllNodes(hasTestTag("video_duration")).fetchSemanticsNodes().size)
    }

    @Test
    fun theViewerKeepsTheTransitionSizeWhenTheVideoHasTheSameAspect() {
        val attachment = Attachment(url = "https://x/v.mp4", width = 1920, height = 1080)
        assertTrue(sameAspect(androidx.compose.ui.geometry.Size(1280f, 720f), attachment))
        assertFalse(sameAspect(androidx.compose.ui.geometry.Size(1080f, 1920f), attachment))
        assertFalse(sameAspect(androidx.compose.ui.geometry.Size(1280f, 720f), Attachment(url = "https://x/v.mp4")))
    }
}
