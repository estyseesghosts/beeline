package me.foxtails.palustris.ui.media

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import me.foxtails.palustris.ui.media.MediaTransitionKey
import me.foxtails.palustris.ui.media.MediaTransitionFrame
import me.foxtails.palustris.ui.media.MediaTransitionRegistry
import me.foxtails.palustris.ui.media.MediaTransitionSource
import me.foxtails.palustris.ui.media.MediaViewerPhase
import me.foxtails.palustris.ui.media.MediaViewerTransitionState
import me.foxtails.palustris.ui.media.ZoomableMediaState
import me.foxtails.palustris.ui.media.dismissProgress
import me.foxtails.palustris.ui.media.cropRect
import me.foxtails.palustris.ui.media.fitRect
import me.foxtails.palustris.ui.media.lerpRect
import me.foxtails.palustris.ui.media.shouldDismiss
import me.foxtails.palustris.ui.media.updateIfVisible
import me.foxtails.palustris.ui.media.updateWhileRevealed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MediaTransitionStateTest {
    @Test
    fun fitRectCentersPortraitAndLandscapeImages() {
        assertEquals(Rect(250f, 0f, 750f, 1000f), fitRect(Rect(0f, 0f, 1000f, 1000f), 1f, 2f))
        assertEquals(Rect(0f, 250f, 1000f, 750f), fitRect(Rect(0f, 0f, 1000f, 1000f), 2f, 1f))
    }

    @Test
    fun deterministicEdgeMarkerFixtureVariantsKeepCropGeometry() {
        val boundedFeed = Rect(0f, 0f, 240f, 240f)
        val landscape = cropRect(boundedFeed, 16f, 9f)
        val portrait = cropRect(boundedFeed, 9f, 16f)
        val sameAspect = cropRect(Rect(0f, 0f, 320f, 180f), 16f, 9f)
        val differentAspect = cropRect(Rect(0f, 0f, 320f, 180f), 4f, 3f)
        val missingMetadata = cropRect(boundedFeed, 4f, 3f)

        assertTrue(landscape.width > boundedFeed.width)
        assertEquals(boundedFeed.height, landscape.height, 0.001f)
        assertTrue(portrait.height > boundedFeed.height)
        assertEquals(boundedFeed.width, portrait.width, 0.001f)
        assertEquals(Rect(0f, 0f, 320f, 180f), sameAspect)
        assertTrue(differentAspect.height > sameAspect.height)
        assertEquals(4f / 3f, missingMetadata.width / missingMetadata.height, 0.001f)
    }

    @Test
    fun geometryClampsInteractiveProgressAndInterpolation() {
        assertEquals(0.5f, dismissProgress(-100f, 200f), 0.001f)
        assertEquals(1f, dismissProgress(500f, 200f), 0.001f)
        assertEquals(Rect(25f, 25f, 75f, 75f), lerpRect(Rect(0f, 0f, 50f, 50f), Rect(50f, 50f, 100f, 100f), 0.5f))
    }

    @Test
    fun frameInterpolationHasExactEndpointsForEveryGeometryComponent() {
        val start = MediaTransitionFrame(
            imageBounds = Rect(-40f, 20f, 280f, 260f),
            clipBounds = Rect(0f, 40f, 240f, 240f),
            visibleBounds = Rect(0f, 80f, 180f, 240f),
            cornerRadiusPx = 18f,
        )
        val end = MediaTransitionFrame(
            imageBounds = Rect(12f, 34f, 412f, 334f),
            clipBounds = Rect(12f, 34f, 412f, 334f),
            visibleBounds = Rect(12f, 34f, 412f, 334f),
            cornerRadiusPx = 0f,
        )

        assertEquals(start, me.foxtails.palustris.ui.media.lerpFrame(start, end, 0f))
        assertEquals(end, me.foxtails.palustris.ui.media.lerpFrame(start, end, 1f))
    }

    @Test
    fun distanceAndVelocityShareDismissThresholds() {
        assertFalse(shouldDismiss(100f, 0f, 1_000f))
        assertTrue(shouldDismiss(200f, 0f, 1_000f))
        assertTrue(shouldDismiss(10f, -1_400f, 1_000f))
    }

    @Test
    fun registryTracksActiveKeyAndCurrentBounds() {
        val registry = MediaTransitionRegistry()
        val key = MediaTransitionKey("account", "post", "attachment")
        val bounds = Rect(1f, 2f, 101f, 202f)

        registry.update(key, bounds)
        assertEquals(bounds, registry.boundsFor(key))
        assertFalse(registry.isActive(key))
        val owner = registry.begin(key)
        assertTrue(registry.isActive(key))
        registry.end(owner)
        assertNull(registry.currentActiveKey)
        registry.remove(key)
        assertNull(registry.boundsFor(key))
    }

    @Test
    fun registryKeepsCompleteBoundsForPartialClipsAndInvalidatesInvisibleSources() {
        val registry = MediaTransitionRegistry()
        val key = MediaTransitionKey("account", "post", "attachment", "occurrence")
        val full = Rect(10f, 20f, 210f, 220f)
        val visible = Rect(10f, 40f, 210f, 220f)

        registry.updateIfVisible(key, MediaTransitionSource(fullBounds = full, visibleBounds = visible))
        assertEquals(full, registry.sourceFor(key)?.fullBounds)
        assertEquals(visible, registry.sourceFor(key)?.visibleBounds)

        registry.updateIfVisible(key, MediaTransitionSource(fullBounds = full, visibleBounds = Rect(0f, 0f, 0f, 0f)))
        assertNull(registry.sourceFor(key))
    }

    @Test
    fun coveredTileIsRemovedAndRevealedTileIsPublished() {
        val registry = MediaTransitionRegistry()
        val key = MediaTransitionKey("account", "post", "attachment")
        val source = MediaTransitionSource(fullBounds = Rect(0f, 0f, 100f, 100f))

        registry.updateWhileRevealed(key, revealed = true, source = source)
        assertEquals(source, registry.sourceFor(key))
        registry.updateWhileRevealed(key, revealed = false, source = source)
        assertNull(registry.sourceFor(key))
    }

    @Test
    fun releasingOtherAccountsKeepsOnlyTheCurrentAccountAndItsViewer() {
        val registry = MediaTransitionRegistry()
        val mine = MediaTransitionKey("mine", "post", "a")
        val theirs = MediaTransitionKey("theirs", "post", "a")
        val bounds = Rect(0f, 0f, 10f, 10f)
        registry.update(mine, bounds)
        registry.update(theirs, bounds)
        val ownerOfMine = registry.begin(mine)

        registry.releaseOtherAccounts("mine")
        assertEquals(bounds, registry.boundsFor(mine))
        assertNull(registry.boundsFor(theirs))
        assertTrue(registry.isOwnerActive(ownerOfMine))

        val ownerOfTheirs = registry.begin(theirs)
        registry.releaseOtherAccounts("mine")
        assertFalse(registry.isOwnerActive(ownerOfTheirs))
        assertNull(registry.currentActiveKey)
        assertFalse(registry.isSourceHidden(theirs))

        registry.releaseOtherAccounts(null)
        assertNull(registry.boundsFor(mine))
    }

    @Test
    fun oldViewerOwnerCannotClearAReplacementOrItsHandoff() {
        val registry = MediaTransitionRegistry()
        val key = MediaTransitionKey("account", "post", "attachment")
        val first = registry.begin(key)
        registry.prepareHandoff(first)
        assertFalse(registry.isSourceHidden(key))

        val replacement = registry.begin(key)
        registry.end(first)
        assertTrue(registry.isOwnerActive(replacement))
        assertTrue(registry.isSourceHidden(key))

        registry.end(replacement)
        assertNull(registry.currentActiveKey)
    }

    @Test
    fun replacementRetainsOnlyTheCurrentHiddenSource() {
        val registry = MediaTransitionRegistry()
        val firstKey = MediaTransitionKey("account", "post", "first")
        val secondKey = MediaTransitionKey("account", "post", "second")

        registry.begin(firstKey)
        assertTrue(registry.isSourceHidden(firstKey))

        registry.begin(secondKey)
        assertFalse(registry.isSourceHidden(firstKey))
        assertTrue(registry.isSourceHidden(secondKey))

        registry.begin(firstKey)
        assertTrue(registry.isSourceHidden(firstKey))
        assertFalse(registry.isSourceHidden(secondKey))
    }

    @Test
    fun staleOwnerCannotClearReplacementOrPrepareItsHandoff() {
        val registry = MediaTransitionRegistry()
        val firstKey = MediaTransitionKey("account", "post", "first")
        val secondKey = MediaTransitionKey("account", "post", "second")
        val first = registry.begin(firstKey)
        val second = registry.begin(secondKey)

        registry.end(first)
        registry.prepareHandoff(first)

        assertTrue(registry.isOwnerActive(second))
        assertTrue(registry.isSourceHidden(secondKey))
        assertFalse(registry.isSourceHidden(firstKey))
    }

    @Test
    fun staleSameKeyMarkSourceReadyCannotRehideActiveReplacement() {
        val registry = MediaTransitionRegistry()
        val key = MediaTransitionKey("account", "post", "attachment")
        val first = registry.begin(key)
        val second = registry.begin(key)

        assertTrue(registry.isOwnerActive(second))
        assertTrue(registry.isSourceHidden(key))

        registry.prepareHandoff(second)
        assertFalse(registry.isSourceHidden(key))

        registry.markSourceReady(first)
        assertFalse(registry.isSourceHidden(key))
        assertTrue(registry.isOwnerActive(second))
        assertEquals(key, registry.currentActiveKey)

        registry.markSourceReady(second)
        assertTrue(registry.isSourceHidden(key))
        assertTrue(registry.isOwnerActive(second))
    }

    @Test
    fun currentOwnerHandoffAndEndReleaseTheHiddenSource() {
        val registry = MediaTransitionRegistry()
        val key = MediaTransitionKey("account", "post", "attachment")
        val owner = registry.begin(key)

        registry.prepareHandoff(owner)
        assertFalse(registry.isSourceHidden(key))
        registry.markSourceReady(owner)
        assertTrue(registry.isSourceHidden(key))

        registry.end(owner)
        assertFalse(registry.isSourceHidden(key))
        assertNull(registry.currentActiveKey)
        registry.end(owner)
        registry.prepareHandoff(owner)
        assertFalse(registry.isSourceHidden(key))
    }

    @Test
    fun manyRepeatedTransitionsRetainOnlyTheLatestHiddenKey() {
        val registry = MediaTransitionRegistry()
        val keys = List(6) { index -> MediaTransitionKey("account", "post", "attachment-$index") }
        val owners = keys.map(registry::begin)

        keys.forEachIndexed { index, key ->
            assertEquals(index == keys.lastIndex, registry.isSourceHidden(key))
        }
        assertTrue(registry.isOwnerActive(owners.last()))
        assertEquals(keys.last(), registry.currentActiveKey)

        owners.dropLast(1).forEach { stale ->
            registry.end(stale)
            registry.prepareHandoff(stale)
        }

        assertTrue(registry.isOwnerActive(owners.last()))
        assertTrue(registry.isSourceHidden(keys.last()))
        keys.dropLast(1).forEach { staleKey ->
            assertFalse(registry.isSourceHidden(staleKey))
        }

        registry.end(owners.last())
        assertNull(registry.currentActiveKey)
        keys.forEach { key ->
            assertFalse(registry.isSourceHidden(key))
        }
    }

    @Test
    fun replacementAfterHandoffKeepsOnlyTheLatestHiddenKey() {
        val registry = MediaTransitionRegistry()
        val firstKey = MediaTransitionKey("account", "post", "first")
        val secondKey = MediaTransitionKey("account", "post", "second")
        val thirdKey = MediaTransitionKey("account", "post", "third")
        val first = registry.begin(firstKey)

        registry.prepareHandoff(first)
        assertFalse(registry.isSourceHidden(firstKey))

        val second = registry.begin(secondKey)
        assertTrue(registry.isSourceHidden(secondKey))
        assertFalse(registry.isSourceHidden(firstKey))

        val third = registry.begin(thirdKey)
        assertTrue(registry.isSourceHidden(thirdKey))
        assertFalse(registry.isSourceHidden(firstKey))
        assertFalse(registry.isSourceHidden(secondKey))

        registry.end(first)
        registry.prepareHandoff(first)
        registry.end(second)
        registry.prepareHandoff(second)
        registry.markSourceReady(first)
        registry.markSourceReady(second)

        assertTrue(registry.isOwnerActive(third))
        assertFalse(registry.isOwnerActive(first))
        assertFalse(registry.isOwnerActive(second))
        assertTrue(registry.isSourceHidden(thirdKey))
        assertFalse(registry.isSourceHidden(firstKey))
        assertFalse(registry.isSourceHidden(secondKey))
        assertEquals(thirdKey, registry.currentActiveKey)

        registry.prepareHandoff(third)
        assertFalse(registry.isSourceHidden(thirdKey))
        assertEquals(thirdKey, registry.currentActiveKey)

        registry.markSourceReady(third)
        assertTrue(registry.isSourceHidden(thirdKey))

        registry.end(third)
        assertNull(registry.currentActiveKey)
        assertFalse(registry.isSourceHidden(firstKey))
        assertFalse(registry.isSourceHidden(secondKey))
        assertFalse(registry.isSourceHidden(thirdKey))

        registry.end(third)
        registry.prepareHandoff(third)
        assertFalse(registry.isSourceHidden(thirdKey))
    }

    @Test
    fun reducedMotionTransitionSnapsThroughReturnAndClosePhases() = runBlocking {
        val source = Rect(20f, 40f, 220f, 240f)
        val destination = Rect(0f, 0f, 1_000f, 1_000f)
        val state = MediaViewerTransitionState(source, destination, me.foxtails.palustris.ui.motion.PalustrisMotionScheme.standard(true))

        assertEquals(MediaViewerPhase.Open, state.phase)
        state.beginDrag()
        state.dragBy(Offset(0f, 240f))
        assertEquals(MediaViewerPhase.Dragging, state.phase)
        state.returnToOpen()
        assertEquals(MediaViewerPhase.Open, state.phase)
        state.close(source, destination)
        assertEquals(MediaViewerPhase.Closing, state.phase)
        assertEquals(source, state.visualBounds)
    }

    @Test
    fun longDragUsesTheSameControlledClosePath() = runBlocking {
        val source = Rect(20f, 40f, 220f, 240f)
        val destination = Rect(0f, 0f, 1_000f, 1_000f)
        val state = MediaViewerTransitionState(source, destination, me.foxtails.palustris.ui.motion.PalustrisMotionScheme.standard(true))

        state.beginDrag()
        state.dragBy(Offset(0f, 250f))
        assertTrue(state.shouldDismiss(0f))
        state.close(source, destination)
        assertEquals(MediaViewerPhase.Closing, state.phase)
        assertEquals(source, state.visualBounds)
    }

    @Test
    fun zoomStateExposesWhetherDismissIsAllowed() {
        val state = ZoomableMediaState()

        assertFalse(state.isZoomed)
        state.applyTransform(2f, Offset(20f, 10f))
        assertTrue(state.isZoomed)
        state.setDoubleTapZoom()
        assertFalse(state.isZoomed)
        assertEquals(Offset.Zero, state.offset)
    }
}
