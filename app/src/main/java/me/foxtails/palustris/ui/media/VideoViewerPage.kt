package me.foxtails.palustris.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.rememberAsyncImagePainter
import me.foxtails.palustris.R
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestRole

/**
 * One video page in the media viewer. Only the selected page takes the viewer slot, so neighbours
 * show their poster and nothing else. The player sits under the same zoom transform as an image, and
 * the controls sit outside it so they never scale. Playback is user initiated, so it does not depend
 * on the autoplay setting or the network state.
 */
@Composable
internal fun VideoViewerPage(
    attachment: Attachment,
    index: Int,
    tileKey: String,
    selected: Boolean,
    accountIdentity: String,
    postIdentity: String,
    contentDescription: String,
    zoomState: ZoomableMediaState,
    onReady: () -> Unit,
    onVideoSize: (Size) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coordinator = LocalVideoPlaybackCoordinator.current
    val url = remember(attachment.url) { MediaRequestPolicy.validWebUrl(attachment.url) }
    val mediaLoader = remember(context) { MediaImageLoader.get(context) }
    val posterRequest = remember(attachment, index, accountIdentity, postIdentity) {
        val decision = MediaRequestPolicy.resolve(attachment, MediaRequestRole.Preview, revealed = true, explicitlyOpened = false)
        (decision as? MediaRequestDecision.Request)?.let {
            mediaLoader.request(
                context = context,
                decision = it,
                accountIdentity = accountIdentity,
                postIdentity = postIdentity,
                attachment = attachment,
                attachmentIndex = index,
                decodeWidthPx = POSTER_DECODE_PX,
                decodeHeightPx = POSTER_DECODE_PX,
            )
        }
    }
    val poster = posterRequest?.let { rememberAsyncImagePainter(it, mediaLoader.imageLoader) }
    if (selected && coordinator != null && url != null) {
        DisposableEffect(tileKey, url) {
            coordinator.openViewer(tileKey, url)
            onDispose { coordinator.closeViewer(tileKey) }
        }
    }
    val player = coordinator?.takeIf { it.viewerKey == tileKey }?.viewerPlayer
    val failed = coordinator?.viewerFailed == true
    val playing = rememberIsPlaying(player)
    val controls = rememberAutoHideState(playing)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player?.pause() }
    // The page counts as ready at once: it shows the same poster the open transition draws.
    LaunchedEffect(selected) { if (selected) onReady() }
    val videoSize = coordinator?.viewerVideoSize
    LaunchedEffect(selected, videoSize) { if (selected && videoSize != null) onVideoSize(videoSize) }

    Box(modifier.fillMaxSize()) {
        val posterLayer: @Composable () -> Unit = {
            if (poster != null) {
                Image(painter = poster, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .zoomable(zoomState, tileKey, onTap = controls::toggle)
                .semantics { this.contentDescription = contentDescription },
        ) {
            posterLayer()
            if (player != null && !failed) {
                VideoSurface(player = player, shutter = posterLayer, contentScale = ContentScale.Fit)
            }
        }
        if (player != null && !failed) {
            AnimatedVisibility(
                visible = controls.visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                VideoViewerControls(
                    player = player,
                    unmuted = coordinator.viewerUnmuted,
                    onToggleSound = coordinator::toggleViewerSound,
                    onInteract = controls::show,
                    modifier = Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = CONTROLS_BOTTOM_CLEARANCE),
                )
            }
        }
        if (url == null) {
            Text(
                stringResource(R.string.media_unavailable),
                Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else if (failed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.video_failed), color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { coordinator?.retryViewer() }) { Text(stringResource(R.string.video_retry)) }
            }
        }
    }
}

/** Keeps the controls above the viewer's own bottom action row. */
private val CONTROLS_BOTTOM_CLEARANCE = 72.dp
private const val POSTER_DECODE_PX = 1080
