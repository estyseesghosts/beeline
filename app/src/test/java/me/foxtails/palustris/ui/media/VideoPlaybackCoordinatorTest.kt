package me.foxtails.palustris.ui.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import me.foxtails.palustris.domain.AutoplayEnvironment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPlaybackCoordinatorTest {
    private val open = AutoplayEnvironment(settingEnabled = true, networkMetered = false, reducedMotion = false, foreground = true)
    private lateinit var scope: TestScope
    private lateinit var coordinator: VideoPlaybackCoordinator

    @Before
    fun setUp() {
        scope = TestScope()
        coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Context>(), scope)
        coordinator.updateEnvironment(open)
    }

    @After
    fun tearDown() = coordinator.release()

    private fun report(key: String, order: Int, fraction: Float = 1f) =
        coordinator.report(key, order, fraction, contentVisible = true, url = "https://cdn.example/$key.mp4", maxWidthPx = 600)

    private fun settle() {
        scope.advanceTimeBy(100)
        scope.runCurrent()
    }

    @Test
    fun newPlaybackStartsMutedAndInactiveTilesHoldNoPlayer() {
        assertNull(coordinator.player)
        report("a", 0)
        assertNull(coordinator.activeKey)
        settle()
        assertEquals("a", coordinator.activeKey)
        assertEquals(0f, coordinator.player!!.volume, 0f)
        assertNull(coordinator.unmutedKey)
    }

    @Test
    fun unmuteAppliesToTheSelectedVideoOnlyAndSelectingAnotherMutesIt() {
        report("a", 0)
        report("b", 1, fraction = 0.1f)
        settle()
        coordinator.toggleSound("b")
        assertNull(coordinator.unmutedKey)
        coordinator.toggleSound("a")
        assertEquals("a", coordinator.unmutedKey)
        assertEquals(1f, coordinator.player!!.volume, 0f)

        report("a", 0, fraction = 0.1f)
        report("b", 1)
        settle()
        assertEquals("b", coordinator.activeKey)
        assertNull(coordinator.unmutedKey)
        assertEquals(0f, coordinator.player!!.volume, 0f)
    }

    @Test
    fun pauseStopsTheActiveVideoAndResetsOnTheNextSelection() {
        report("a", 0)
        settle()
        coordinator.togglePause("a")
        assertTrue(coordinator.paused)
        assertFalse(coordinator.player!!.playWhenReady)
        coordinator.togglePause("a")
        assertFalse(coordinator.paused)
        assertTrue(coordinator.player!!.playWhenReady)
    }

    @Test
    fun turningMeteredOrBackgroundingStopsPlayback() {
        report("a", 0)
        settle()
        assertEquals("a", coordinator.activeKey)
        coordinator.updateEnvironment(open.copy(networkMetered = true))
        assertNull(coordinator.activeKey)
        assertFalse(coordinator.player!!.playWhenReady)
        coordinator.updateEnvironment(open)
        assertEquals("a", coordinator.activeKey)
        coordinator.updateEnvironment(open.copy(foreground = false))
        assertNull(coordinator.activeKey)
    }

    @Test
    fun playerIsReleasedAfterTenSecondsIdle() {
        report("a", 0)
        settle()
        coordinator.remove("a")
        settle()
        assertNull(coordinator.activeKey)
        assertNotNull(coordinator.player)
        scope.advanceTimeBy(9_000)
        assertNotNull(coordinator.player)
        scope.advanceTimeBy(2_000)
        assertNull(coordinator.player)
    }

    @Test
    fun hiddenContentNeverPlays() {
        coordinator.report("a", 0, 1f, contentVisible = false, url = "https://cdn.example/a.mp4", maxWidthPx = 600)
        settle()
        assertNull(coordinator.activeKey)
        assertNull(coordinator.player)
    }
}
