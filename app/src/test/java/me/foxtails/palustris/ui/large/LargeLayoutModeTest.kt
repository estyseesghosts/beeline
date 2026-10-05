package me.foxtails.palustris.ui.large

import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.ui.large.LargeFoldingFeature
import me.foxtails.palustris.ui.large.LargeLayoutMode
import me.foxtails.palustris.ui.large.calculateLargePaneLayout
import me.foxtails.palustris.ui.large.calculateNavigationFit
import me.foxtails.palustris.ui.large.largeContentOriginPx
import me.foxtails.palustris.ui.large.largeFeatureBoundsInContentPx
import me.foxtails.palustris.ui.large.largeLayoutMode
import me.foxtails.palustris.ui.large.primaryPaneBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeLayoutModeTest {
    @Test
    fun windowHingeCoordinatesTranslateFromPhysicalInsetsWithoutRail() {
        val origin = largeContentOriginPx(
            leftInsetPx = 24f,
            topInsetPx = 30f,
        )

        assertEquals(Rect(476f, 50f, 496f, 870f), largeFeatureBoundsInContentPx(Rect(500f, 80f, 520f, 900f), origin))
    }

    @Test
    fun widthPolicyKeepsCompactBoundaryAndExpandedBaseline() {
        assertEquals(LargeLayoutMode.Compact, largeLayoutMode(599f))
        assertEquals(LargeLayoutMode.Single, largeLayoutMode(600f))
        assertEquals(LargeLayoutMode.Single, largeLayoutMode(839f))
        assertEquals(LargeLayoutMode.Expanded, largeLayoutMode(840f))
        assertEquals(LargeLayoutMode.Expanded, largeLayoutMode(1600f))
    }

    @Test
    fun singleLayoutAlwaysUsesOnePrimaryPane() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 600f,
            contentWidthDp = 560f,
            contentHeightDp = 600f,
            density = 1f,
        )

        assertEquals(LargeLayoutMode.Single, layout.mode)
        assertNull(layout.detail)
        assertEquals(528f, layout.primary.width, 0.001f)
    }

    @Test
    fun unobstructedExpandedWindowClampsListToPaneMinimums() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 760f,
            contentHeightDp = 600f,
            density = 1f,
        )

        assertEquals(LargeLayoutMode.Expanded, layout.mode)
        assertTrue(layout.primary.width >= 320f)
        assertTrue((layout.detail?.width ?: 0f) >= 360f)
        assertEquals(727f, layout.primary.width + (layout.detail?.width ?: 0f), 0.001f)
    }

    @Test
    fun expandedWindowUsesFullSafeRegionWhenDetailIsDisabled() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 760f,
            contentHeightDp = 600f,
            density = 1f,
        )

        val primary = primaryPaneBounds(layout, twoPane = false)

        assertEquals(layout.safeRegions.single(), primary)
        assertTrue(primary.width > layout.primary.width)
    }

    @Test
    fun verticalOccludingHingeCreatesSeparateSafePanes() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 760f,
            contentHeightDp = 600f,
            density = 1f,
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(379f, 0f, 381f, 600f), isVertical = true, isSeparating = false, isOccluding = true),
            ),
        )

        assertEquals(2, layout.safeRegions.size)
        assertTrue(layout.primary.right <= 379f)
        assertTrue((layout.detail?.left ?: 0f) >= 381f)
    }

    @Test
    fun narrowHingeRegionFallsBackToSinglePane() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 700f,
            contentHeightDp = 600f,
            density = 1f,
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(340f, 0f, 360f, 600f), isVertical = true, isSeparating = true, isOccluding = false),
            ),
        )

        assertNull(layout.detail)
    }

    @Test
    fun nonSeparatingCreaseDoesNotRemoveContent() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 760f,
            contentHeightDp = 600f,
            density = 1f,
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(379f, 0f, 381f, 600f), isVertical = true, isSeparating = false, isOccluding = false),
            ),
        )

        assertEquals(1, layout.safeRegions.size)
        assertTrue(layout.detail != null)
    }

    @Test
    fun horizontalSeparatingFoldSplitsSafeRegionsWithoutDetailPane() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 760f,
            contentHeightDp = 900f,
            density = 1f,
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(0f, 449f, 760f, 451f), isVertical = false, isSeparating = true, isOccluding = false),
            ),
        )

        assertEquals(2, layout.safeRegions.size)
        assertNull(layout.detail)
        assertTrue(layout.primary.bottom <= 449f || layout.primary.top >= 451f)
    }

    @Test
    fun wideWindowWithoutObstructionsFitsVerticalNavigation() {
        val fit = calculateNavigationFit(
            windowWidthDp = 800f,
            windowHeightDp = 600f,
            systemBarInsets = Rect(24f, 30f, 24f, 30f),
        )

        assertTrue(fit.useVerticalNavigation)
        assertTrue(fit.safeRegion != null)
    }

    @Test
    fun narrowWindowFallsBackToCompactNavigation() {
        val fit = calculateNavigationFit(
            windowWidthDp = 400f,
            windowHeightDp = 800f,
            systemBarInsets = Rect(24f, 30f, 24f, 30f),
        )

        assertFalse(fit.useVerticalNavigation)
        assertNull(fit.safeRegion)
    }

    @Test
    fun verticalHingePreventsVerticalNavigationWhenItSplitsContent() {
        val fit = calculateNavigationFit(
            windowWidthDp = 800f,
            windowHeightDp = 600f,
            systemBarInsets = Rect(24f, 30f, 24f, 30f),
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(379f, 0f, 381f, 540f), isVertical = true, isSeparating = true, isOccluding = false),
            ),
        )

        assertFalse(fit.useVerticalNavigation)
        assertNull(fit.safeRegion)
    }

    @Test
    fun cutoutExclusionPreventsVerticalNavigationWhenItNarrowsContent() {
        val fit = calculateNavigationFit(
            windowWidthDp = 800f,
            windowHeightDp = 600f,
            systemBarInsets = Rect(24f, 30f, 24f, 30f),
            cutoutBounds = Rect(400f, 0f, 800f, 600f),
        )

        assertFalse(fit.useVerticalNavigation)
        assertNull(fit.safeRegion)
    }

    @Test
    fun mandatoryGestureExclusionPreventsNavigationWithInsufficientHeight() {
        val fit = calculateNavigationFit(
            windowWidthDp = 800f,
            windowHeightDp = 600f,
            systemBarInsets = Rect(24f, 30f, 24f, 30f),
            gestureInsets = Rect(0f, 0f, 0f, 220f),
        )

        assertFalse(fit.useVerticalNavigation)
        assertNull(fit.safeRegion)
    }

    @Test
    fun imeHeightDoesNotAffectNavigationFit() {
        val fit = calculateNavigationFit(
            windowWidthDp = 800f,
            windowHeightDp = 600f,
            systemBarInsets = Rect(24f, 30f, 24f, 30f),
        )

        assertTrue(fit.useVerticalNavigation)
    }

    @Test
    fun nonSeparatingCreaseDoesNotPreventVerticalNavigation() {
        val fit = calculateNavigationFit(
            windowWidthDp = 800f,
            windowHeightDp = 600f,
            systemBarInsets = Rect(24f, 30f, 24f, 30f),
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(379f, 0f, 381f, 540f), isVertical = true, isSeparating = false, isOccluding = false),
            ),
        )

        assertTrue(fit.useVerticalNavigation)
        assertTrue(fit.safeRegion != null)
    }

    @Test
    fun overlappingInsetsMergeOnEveryPhysicalEdgeInsteadOfAdding() {
        val fit = calculateNavigationFit(
            800f, 600f,
            systemBarInsets = Rect(24f, 30f, 24f, 30f),
            gestureInsets = Rect(12f, 20f, 40f, 200f),
        )
        assertTrue(fit.useVerticalNavigation)
        assertEquals(Rect(24f, 30f, 760f, 400f), fit.safeRegion)
    }

    @Test
    fun outerScreenAndSquareTabletFitWithPermanentInsets() {
        for ((width, height) in listOf(445f to 704f, 900f to 900f)) {
            for (anchorLeft in listOf(false, true)) {
                val fit = calculateNavigationFit(
                    width, height, Rect(0f, 24f, 0f, 24f),
                    gestureInsets = Rect(0f, 0f, 0f, 24f), anchorLeft = anchorLeft,
                )
                assertTrue(fit.useVerticalNavigation)
                val bounds = requireNotNull(floatingNavigationBounds(fit, anchorLeft))
                val safe = requireNotNull(fit.safeRegion)
                assertEquals(56f, bounds.width, 0.001f)
                assertEquals(360f, bounds.height, 0.001f)
                assertTrue(bounds.left >= safe.left && bounds.right <= safe.right)
                assertTrue(bounds.top >= safe.top && bounds.bottom <= safe.bottom)
                assertEquals(if (anchorLeft) 8f else width - 64f, bounds.left, 0.001f)
            }
        }
    }

    @Test
    fun structuralHingesChooseSafeRegionAtRequestedPhysicalEdge() {
        val vertical = LargeFoldingFeature(Rect(490f, 0f, 510f, 900f), true, true, true)
        val left = calculateNavigationFit(1000f, 900f, Rect.Zero, foldingFeatures = listOf(vertical), anchorLeft = true)
        val right = calculateNavigationFit(1000f, 900f, Rect.Zero, foldingFeatures = listOf(vertical), anchorLeft = false)
        assertEquals(Rect(0f, 0f, 490f, 900f), left.safeRegion)
        assertEquals(Rect(510f, 0f, 1000f, 900f), right.safeRegion)
        assertTrue(requireNotNull(floatingNavigationBounds(left, true)).right < vertical.bounds.left)
        assertTrue(requireNotNull(floatingNavigationBounds(right, false)).left > vertical.bounds.right)
        val horizontal = LargeFoldingFeature(Rect(0f, 300f, 1000f, 320f), false, true, false)
        val bottom = calculateNavigationFit(1000f, 900f, Rect.Zero, foldingFeatures = listOf(horizontal))
        assertEquals(Rect(0f, 320f, 1000f, 900f), bottom.safeRegion)
        assertTrue(requireNotNull(floatingNavigationBounds(bottom, false)).top >= 320f)
        assertFalse(calculateNavigationFit(1000f, 600f, Rect.Zero, foldingFeatures = listOf(horizontal)).useVerticalNavigation)
    }

    @Test
    fun clearanceIsPaneLocalAndAbsentForHiddenOrFailedNavigation() {
        val fit = calculateNavigationFit(900f, 900f, Rect.Zero, anchorLeft = true)
        val controls = floatingNavigationBounds(fit, true)
        assertEquals(48f, navigationPaneClearance(Rect(16f, 0f, 400f, 900f), controls, true), 0.001f)
        assertEquals(0f, navigationPaneClearance(Rect(401f, 0f, 884f, 900f), controls, true), 0.001f)
        assertEquals(0f, navigationPaneClearance(Rect(16f, 0f, 884f, 900f), null, true), 0.001f)
        assertNull(floatingNavigationBounds(calculateNavigationFit(445f, 359f, Rect.Zero), false))
    }

    @Test
    fun windowPixelsConvertToDpBeforeFit() {
        assertEquals(Rect(10f, 20f, 30f, 40f), Rect(26.25f, 52.5f, 78.75f, 105f).toDpRect(2.625f))
    }
}
