package me.foxtails.palustris.ui.photogrid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.PillAction

/** What a Photo Grid tile shows over its neutral surface while its image request runs. */
internal enum class TilePhase { Loaded, Loading, Failed }

/** Maps the painter state of a tile request. A request that has not started counts as loading. */
internal fun tilePhase(state: AsyncImagePainter.State): TilePhase = when (state) {
    is AsyncImagePainter.State.Success -> TilePhase.Loaded
    is AsyncImagePainter.State.Error -> TilePhase.Failed
    else -> TilePhase.Loading
}

/**
 * The loading and failed states of one tile. A loading tile shows a quiet indicator. A failed tile shows an icon, a
 * line of text and a Retry control that runs [onRetry]. A loaded tile shows nothing, so an empty tile that looks
 * black cannot exist. The layer never takes a tap outside its Retry control, so the tile still opens its post.
 * The layer holds no state; the tile owns the attempt counter.
 */
@Composable
internal fun PhotoGridTileStateLayer(phase: TilePhase, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (phase) {
        TilePhase.Loaded -> Unit
        TilePhase.Loading -> {
            val loading = stringResource(R.string.photo_grid_tile_loading)
            Box(
                modifier.fillMaxSize().testTag(TILE_LOADING_TAG).semantics { contentDescription = loading },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TilePhase.Failed -> {
            Box(modifier.fillMaxSize().testTag(TILE_FAILED_TAG)) {
                // Only the Retry control is interactive. A tap anywhere else on the tile still opens the post.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 36.dp),
                ) {
                    Icon(AppIcons.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.photo_grid_tile_failed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
                PillAction(
                    label = stringResource(R.string.photo_grid_tile_retry),
                    onClick = onRetry,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).testTag(TILE_RETRY_TAG),
                )
            }
        }
    }
}

internal const val TILE_LOADING_TAG = "photo_grid_tile_loading"
internal const val TILE_FAILED_TAG = "photo_grid_tile_failed"
internal const val TILE_RETRY_TAG = "photo_grid_tile_retry"
