package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayPolicyTest {
    private val open = AutoplayEnvironment(settingEnabled = true, networkMetered = false, reducedMotion = false, foreground = true)

    private fun tile(key: String, order: Int, fraction: Float, visible: Boolean = true) =
        AutoplayCandidate(key, order, fraction, visible)

    @Test
    fun startsOnlyAtSixtyPercent() {
        assertNull(AutoplayPolicy.select(listOf(tile("a", 0, 0.59f)), open, null))
        assertEquals("a", AutoplayPolicy.select(listOf(tile("a", 0, 0.6f)), open, null))
    }

    @Test
    fun keepsPlayingDownToFortyPercent() {
        assertEquals("a", AutoplayPolicy.select(listOf(tile("a", 0, 0.4f)), open, "a"))
        assertNull(AutoplayPolicy.select(listOf(tile("a", 0, 0.39f)), open, "a"))
    }

    @Test
    fun hysteresisDoesNotApplyToAnotherTile() {
        assertNull(AutoplayPolicy.select(listOf(tile("a", 0, 0.5f)), open, "b"))
    }

    @Test
    fun highestFractionWinsAndTiesGoToTheEarlierItem() {
        val tiles = listOf(tile("a", 0, 0.7f), tile("b", 1, 0.9f), tile("c", 2, 0.9f))
        assertEquals("b", AutoplayPolicy.select(tiles, open, null))
        assertEquals("a", AutoplayPolicy.select(listOf(tile("b", 1, 0.8f), tile("a", 0, 0.8f)), open, null))
    }

    @Test
    fun hiddenOrSensitiveContentIsExcluded() {
        val tiles = listOf(tile("a", 0, 1f, visible = false), tile("b", 1, 0.7f))
        assertEquals("b", AutoplayPolicy.select(tiles, open, null))
        assertNull(AutoplayPolicy.select(listOf(tile("a", 0, 1f, visible = false)), open, "a"))
    }

    @Test
    fun settingOffReducedMotionBackgroundMeteredAndUnknownNetworkBlockAutoplay() {
        val tiles = listOf(tile("a", 0, 1f))
        assertNull(AutoplayPolicy.select(tiles, open.copy(settingEnabled = false), null))
        assertNull(AutoplayPolicy.select(tiles, open.copy(reducedMotion = true), null))
        assertNull(AutoplayPolicy.select(tiles, open.copy(foreground = false), null))
        assertNull(AutoplayPolicy.select(tiles, open.copy(networkMetered = true), null))
        assertNull(AutoplayPolicy.select(tiles, open.copy(networkMetered = null), null))
        assertTrue(open.allowsAutoplay)
        assertFalse(open.copy(networkMetered = null).allowsAutoplay)
    }

    @Test
    fun clipsUnderThirtySecondsLoop() {
        assertTrue(AutoplayPolicy.shouldLoop(29_999))
        assertFalse(AutoplayPolicy.shouldLoop(30_000))
        assertFalse(AutoplayPolicy.shouldLoop(null))
    }
}
