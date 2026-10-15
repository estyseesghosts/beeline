package me.foxtails.palustris.ui.photogrid

import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tile never stays an unexplained empty surface: it loads, fails with a retry, or shows its image. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PhotoGridTileStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun show(phase: TilePhase, onRetry: () -> Unit = {}) {
        compose.activity.runOnUiThread {
            compose.activity.setContent { PalustrisTheme { PhotoGridTileStateLayer(phase, onRetry = onRetry) } }
        }
        compose.waitForIdle()
    }

    @Test
    fun painterStatesMapToTilePhases() {
        val request = ImageRequest.Builder(compose.activity).data("https://example.org/a.jpg").build()
        assertEquals(TilePhase.Loading, tilePhase(AsyncImagePainter.State.Empty))
        assertEquals(TilePhase.Loading, tilePhase(AsyncImagePainter.State.Loading(null)))
        assertEquals(
            TilePhase.Failed,
            tilePhase(AsyncImagePainter.State.Error(null, ErrorResult(null, request, RuntimeException("x")))),
        )
        assertEquals(
            TilePhase.Loaded,
            tilePhase(
                AsyncImagePainter.State.Success(
                    android.graphics.drawable.ColorDrawable(0).let { androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Black) },
                    SuccessResult(
                        drawable = android.graphics.drawable.ColorDrawable(0),
                        request = request,
                        dataSource = coil.decode.DataSource.NETWORK,
                    ),
                ),
            ),
        )
    }

    @Test
    fun aLoadingTileShowsAQuietIndicatorAndNoRetry() {
        show(TilePhase.Loading)

        compose.onNodeWithTag(TILE_LOADING_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(TILE_FAILED_TAG).assertCountEquals(0)
    }

    @Test
    fun aFailedTileShowsAVisibleStateAndTheRetryControlRetries() {
        var retries = 0
        show(TilePhase.Failed) { retries++ }

        compose.onNodeWithTag(TILE_FAILED_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TILE_RETRY_TAG).assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }

    @Test
    fun aTapOutsideTheRetryControlFallsThroughToTheTile() {
        var retries = 0
        var tileTaps = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    Box(Modifier.size(200.dp).clickable { tileTaps++ }.testTag("tile")) {
                        PhotoGridTileStateLayer(TilePhase.Failed, onRetry = { retries++ })
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("tile").performTouchInput { click(topLeft) }

        assertEquals(0, retries)
        assertEquals(1, tileTaps)
    }

    @Test
    fun aLoadedTileShowsNothingOverItsImage() {
        show(TilePhase.Loaded)

        compose.onAllNodesWithTag(TILE_LOADING_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(TILE_FAILED_TAG).assertCountEquals(0)
    }

    @Test
    fun theFirstRetryAlwaysRuns() {
        assertEquals(true, mayRetryTile(0L, 1_000L))
    }

    @Test
    fun retriesInsideTheDebounceWindowAreIgnored() {
        assertEquals(false, mayRetryTile(1_000L, 1_000L + TILE_RETRY_DEBOUNCE_MILLIS - 1))
    }

    @Test
    fun retriesAfterTheDebounceWindowRun() {
        assertEquals(true, mayRetryTile(1_000L, 1_000L + TILE_RETRY_DEBOUNCE_MILLIS))
    }
}
