package me.foxtails.palustris.ui.composer

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerMediaTileTest {
    @Test
    fun anUnknownRatioGivesASquareTile() {
        assertEquals(160.dp, composerTileWidth(null))
    }

    @Test
    fun theTileWidthFollowsTheRatioBetweenTheBounds() {
        assertEquals(120.dp, composerTileWidth(0.75f))
        assertEquals(240.dp, composerTileWidth(3f))
        assertEquals(96.dp, composerTileWidth(0.2f))
    }
}
