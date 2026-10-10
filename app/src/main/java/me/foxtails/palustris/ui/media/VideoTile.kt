package me.foxtails.palustris.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaRequestPolicy

/**
 * One video in a feed or grid tile. The poster always shows. The player surface exists only while
 * the coordinator has selected this tile, so inactive tiles hold no TextureView and no player.
 * Without a coordinator or a playable URL the tile is a poster with a play badge.
 */
@Composable
fun VideoTileContent(
    tileKey: String,
    attachment: Attachment,
    poster: Painter?,
    posterDescription: String,
    contentVisible: Boolean,
    decodeWidthPx: Int,
    order: () -> Int,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit,
) {
    val coordinator = LocalVideoPlaybackCoordinator.current
    val url = remember(attachment.url) { MediaRequestPolicy.validWebUrl(attachment.url) }
    var layout by remember { mutableStateOf<LayoutCoordinates?>(null) }
    if (coordinator != null && url != null) {
        DisposableEffect(tileKey) { onDispose { coordinator.remove(tileKey) } }
    }
    val active = coordinator != null && coordinator.activeKey == tileKey
    val player = coordinator?.player
    Box(
        modifier.onGloballyPositioned { coordinates ->
            layout = coordinates
            if (coordinator != null && url != null && coordinates.isAttached) {
                coordinator.report(
                    key = tileKey,
                    order = order(),
                    fraction = coordinates.visibleFraction(),
                    contentVisible = contentVisible,
                    url = url,
                    maxWidthPx = decodeWidthPx,
                )
            }
        },
    ) {
        val posterLayer: @Composable () -> Unit = {
            if (poster != null) {
                Image(
                    painter = poster,
                    contentDescription = posterDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                placeholder()
            }
        }
        posterLayer()
        if (active && player != null) {
            VideoSurface(player = player, shutter = posterLayer)
        }
        VideoTileControls(
            active = active,
            unmuted = coordinator?.unmutedKey == tileKey,
            paused = coordinator?.paused == true,
            onToggleSound = { coordinator?.toggleSound(tileKey) },
            onTogglePause = { coordinator?.togglePause(tileKey) },
            durationMs = attachment.durationMs,
            failed = coordinator?.isFailed(tileKey) == true,
            onRetry = { coordinator?.retry(tileKey) },
        )
    }
}

private fun LayoutCoordinates.visibleFraction(): Float {
    val full = fullBoundsInRoot()
    val area = full.width * full.height
    if (area <= 0f) return 0f
    val visible = visibleBoundsInRoot(null)
    return (visible.width.coerceAtLeast(0f) * visible.height.coerceAtLeast(0f) / area).coerceIn(0f, 1f)
}

/** Shared strings for the controls, resolved here so the canvas file stays presentation-only. */
@Composable
internal fun videoSoundDescription(unmuted: Boolean): String =
    stringResource(if (unmuted) R.string.video_mute else R.string.video_unmute)

@Composable
internal fun videoPlayDescription(paused: Boolean): String =
    stringResource(if (paused) R.string.video_play else R.string.video_pause)
