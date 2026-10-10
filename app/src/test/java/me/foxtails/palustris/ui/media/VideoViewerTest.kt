package me.foxtails.palustris.ui.media

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import me.foxtails.palustris.domain.AutoplayEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoViewerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val open = AutoplayEnvironment(settingEnabled = true, networkMetered = false, reducedMotion = false, foreground = true)

    @Test
    fun handoffContinuesTheSameVideoAndKeepsItsSound() {
        val same = viewerHandoff("a", "a", 4_200L, "a", "a")
        assertEquals(4_200L, same.startPositionMs)
        assertTrue(same.unmuted)
    }

    @Test
    fun handoffForAnotherVideoStartsAtZeroAndMuted() {
        val other = viewerHandoff("a", "a", 4_200L, "a", "b")
        assertEquals(0L, other.startPositionMs)
        assertFalse(other.unmuted)
        val none = viewerHandoff(null, null, 0L, null, "a")
        assertEquals(0L, none.startPositionMs)
        assertFalse(none.unmuted)
    }

    @Test
    fun handoffKeepsPositionButNotSoundWhenTheTileWasMuted() {
        val muted = viewerHandoff("a", "a", 1_000L, null, "a")
        assertEquals(1_000L, muted.startPositionMs)
        assertFalse(muted.unmuted)
    }

    @Test
    fun timeFormatsAsMinutesAndHours() {
        assertEquals("0:00", formatVideoTime(-5))
        assertEquals("0:04", formatVideoTime(4_900))
        assertEquals("1:05", formatVideoTime(65_000))
        assertEquals("1:01:01", formatVideoTime(3_661_000))
    }

    @Test
    fun viewerSlotStopsTheFeedAndTheFeedResumesOnClose() {
        val scope = TestScope()
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Context>(), scope)
        try {
            coordinator.updateEnvironment(open)
            coordinator.report("a", 0, 1f, contentVisible = true, url = "https://cdn.example/a.mp4", maxWidthPx = 600)
            scope.advanceTimeBy(100)
            scope.runCurrent()
            assertEquals("a", coordinator.activeKey)

            coordinator.openViewer("a", "https://cdn.example/a.mp4")
            assertEquals("a", coordinator.viewerKey)
            assertNotNull(coordinator.viewerPlayer)
            assertNull(coordinator.activeKey)
            assertFalse(coordinator.player!!.playWhenReady)

            coordinator.toggleViewerSound()
            assertTrue(coordinator.viewerUnmuted)
            assertEquals(1f, coordinator.viewerPlayer!!.volume, 0f)

            coordinator.closeViewer("other")
            assertNotNull(coordinator.viewerPlayer)
            coordinator.closeViewer("a")
            assertNull(coordinator.viewerPlayer)
            assertNull(coordinator.viewerKey)
            assertEquals("a", coordinator.activeKey)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun anotherVideoOpensMutedEvenWhenTheFeedTileHadSound() {
        val scope = TestScope()
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Context>(), scope)
        try {
            coordinator.updateEnvironment(open)
            coordinator.report("a", 0, 1f, contentVisible = true, url = "https://cdn.example/a.mp4", maxWidthPx = 600)
            scope.advanceTimeBy(100)
            scope.runCurrent()
            coordinator.toggleSound("a")

            coordinator.openViewer("b", "https://cdn.example/b.mp4")
            assertFalse(coordinator.viewerUnmuted)
            assertEquals(0f, coordinator.viewerPlayer!!.volume, 0f)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun controlsHideAfterThreeSecondsWhilePlaying() {
        compose.mainClock.autoAdvance = false
        lateinit var state: AutoHideState
        compose.setContent { state = rememberAutoHideState(playing = true) }
        compose.mainClock.advanceTimeBy(VIEWER_CONTROLS_HIDE_MS - 100)
        assertTrue(state.visible)
        compose.mainClock.advanceTimeBy(300)
        assertFalse(state.visible)
    }

    @Test
    fun aTouchRestartsTheHideTimer() {
        compose.mainClock.autoAdvance = false
        lateinit var state: AutoHideState
        compose.setContent { state = rememberAutoHideState(playing = true) }
        compose.mainClock.advanceTimeBy(VIEWER_CONTROLS_HIDE_MS - 500)
        compose.runOnUiThread {
            state.show()
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(VIEWER_CONTROLS_HIDE_MS - 500)
        assertTrue(state.visible)
        compose.mainClock.advanceTimeBy(1_000)
        assertFalse(state.visible)
    }

    @Test
    fun aTapTogglesTheControls() {
        val state = AutoHideState()
        state.toggle()
        assertFalse(state.visible)
        state.toggle()
        assertTrue(state.visible)
    }

    @Test
    fun controlsStayWhilePaused() {
        compose.mainClock.autoAdvance = false
        lateinit var state: AutoHideState
        compose.setContent { state = rememberAutoHideState(playing = false) }
        compose.mainClock.advanceTimeBy(VIEWER_CONTROLS_HIDE_MS * 3)
        assertTrue(state.visible)
    }
}
