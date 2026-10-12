package me.foxtails.palustris.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChromeBandGeometryTest {
    private val screen = Rect(0f, 0f, 1000f, 2000f)

    @Test
    fun bottomBandIsStrongFromTheNodeTopAndFadesOverTheFade() {
        val geometry = chromeBandGeometry(Size(1000f, 300f), Offset(0f, 1700f), screen, fade = 100f, reach = 40f, edge = ChromeEdge.Bottom)!!

        // The node rests on the screen edge, so the band has no fade at its bottom.
        assertEquals(Rect(0f, -100f, 1000f, 300f), geometry.band)
        // Opaque from the node top to the node bottom: the mask has no weak zone behind the controls.
        assertEquals(0f, geometry.opaqueTop, 0f)
        assertEquals(300f, geometry.opaqueBottom, 0f)
    }

    @Test
    fun topBandIsStrongToTheNodeBottomAndFadesBelowIt() {
        val geometry = chromeBandGeometry(Size(1000f, 200f), Offset.Zero, screen, fade = 100f, reach = 40f, edge = ChromeEdge.Top)!!

        assertEquals(Rect(0f, 0f, 1000f, 300f), geometry.band)
        assertEquals(0f, geometry.opaqueTop, 0f)
        assertEquals(200f, geometry.opaqueBottom, 0f)
    }

    @Test
    fun recordedAreaStopsAtTheScreenEdgeAndReachesBeyondTheBandInside() {
        val geometry = chromeBandGeometry(Size(1000f, 300f), Offset(0f, 1700f), screen, fade = 100f, reach = 40f, edge = ChromeEdge.Bottom)!!

        // Above the band the backdrop continues, so the blur reads 40 px more. At the bottom, left and right the
        // screen ends, so the recorded area ends with it.
        assertEquals(Rect(0f, -140f, 1000f, 300f), geometry.recorded)
    }

    @Test
    fun recordedAreaExtendsOnEverySideWhereTheBackdropContinues() {
        // A dock inset from the screen edges, as the large layout draws it.
        val geometry = chromeBandGeometry(Size(600f, 200f), Offset(200f, 1000f), screen, fade = 100f, reach = 40f, edge = ChromeEdge.Bottom)!!

        assertEquals(Rect(-40f, -140f, 640f, 340f), geometry.recorded)
        // Content continues below this dock, so it fades out there too instead of ending in a hard edge.
        assertEquals(Rect(0f, -100f, 600f, 300f), geometry.band)
        assertEquals(200f, geometry.opaqueBottom, 0f)
    }

    @Test
    fun topBandInsideTheScreenFadesOnBothSides() {
        val geometry = chromeBandGeometry(Size(1000f, 200f), Offset(0f, 100f), screen, fade = 100f, reach = 40f, edge = ChromeEdge.Top)!!

        // Content continues above the node too, so the band fades there by the 100 px that exist.
        assertEquals(Rect(0f, -100f, 1000f, 340f), geometry.recorded)
        assertEquals(Rect(0f, -100f, 1000f, 300f), geometry.band)
    }

    @Test
    fun anEmptyNodeHasNoBand() {
        assertNull(chromeBandGeometry(Size(0f, 300f), Offset.Zero, screen, 100f, 40f, ChromeEdge.Bottom))
        assertNull(chromeBandGeometry(Size(1000f, 0f), Offset.Zero, screen, 100f, 40f, ChromeEdge.Bottom))
    }

    @Test
    fun aBandOutsideTheBackdropHasNoBand() {
        val elsewhere = Rect(5000f, 5000f, 6000f, 6000f)

        assertNull(chromeBandGeometry(Size(1000f, 300f), Offset(0f, 1700f), elsewhere, 100f, 40f, ChromeEdge.Bottom))
        assertNull(chromeBandGeometry(Size(1000f, 300f), Offset(0f, 1700f), Rect.Zero, 100f, 40f, ChromeEdge.Bottom))
    }

    @Test
    fun aBandOnTheBottomScreenEdgeGetsAVeilOverTheZoneBelowTheControls() {
        val geometry = chromeBandGeometry(Size(1000f, 300f), Offset(0f, 1700f), screen, 100f, 40f, ChromeEdge.Bottom, edgeZone = 120f)!!

        // Nothing at the node top, a low alpha at the bottom of the lowest control, the strongest alpha at the edge.
        assertEquals(ChromeVeil(startY = 0f, controlY = 180f, endY = 300f), geometry.veil)
    }

    @Test
    fun theVeilNeverStartsAboveTheNode() {
        val geometry = chromeBandGeometry(Size(1000f, 300f), Offset(0f, 1700f), screen, 100f, 40f, ChromeEdge.Bottom, edgeZone = 900f)!!

        assertEquals(0f, geometry.veil!!.controlY, 0f)
    }

    @Test
    fun noVeilWithoutAnEdgeZoneOrAwayFromTheScreenEdge() {
        val noZone = chromeBandGeometry(Size(1000f, 300f), Offset(0f, 1700f), screen, 100f, 40f, ChromeEdge.Bottom)!!
        val lifted = chromeBandGeometry(Size(1000f, 300f), Offset(0f, 1000f), screen, 100f, 40f, ChromeEdge.Bottom, edgeZone = 120f)!!
        val top = chromeBandGeometry(Size(1000f, 300f), Offset.Zero, screen, 100f, 40f, ChromeEdge.Top, edgeZone = 120f)!!

        assertNull(noZone.veil)
        assertNull(lifted.veil)
        assertNull(top.veil)
    }
}
