@file:Suppress("UnsafeOptInUsageError")

package me.foxtails.palustris.ui.media

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.media.MeteredNetworkMonitor
import me.foxtails.palustris.data.media.isReducedMotion
import me.foxtails.palustris.domain.AutoplayCandidate
import me.foxtails.palustris.domain.AutoplayEnvironment
import me.foxtails.palustris.domain.AutoplayPolicy

/**
 * Owns the feed and grid playback slot: at most one [ExoPlayer], created on first need and released
 * after [IDLE_RELEASE_MS] without an active video. Tiles report their visibility; [AutoplayPolicy]
 * picks the one that plays. Every new playback starts muted, and unmuting applies to the selected
 * video only. All calls happen on the main thread.
 *
 * This file is the only place that uses Media3's `@UnstableApi` surface. Lint does not accept Kotlin's
 * `@OptIn` for that Java-style marker, so the file suppresses `UnsafeOptInUsageError` instead.
 */
class VideoPlaybackCoordinator(private val context: Context, private val scope: CoroutineScope) {
    private class Tile(
        val order: Int,
        val fraction: Float,
        val contentVisible: Boolean,
        val url: String,
        val maxWidthPx: Int,
    )

    private val tiles = LinkedHashMap<String, Tile>()
    private val knownSizes = HashMap<String, Size>()

    /** Tiles whose video failed to play. Observable, so a tile can offer a retry. */
    private val failed = mutableStateSetOf<String>()
    private var environment = AutoplayEnvironment(
        settingEnabled = false,
        networkMetered = null,
        reducedMotion = false,
        foreground = false,
    )
    private var loadedKey: String? = null
    private var evaluateJob: Job? = null
    private var releaseJob: Job? = null

    /** The tile that currently plays, or null. */
    var activeKey by mutableStateOf<String?>(null)
        private set

    /** The player of the feed slot while a video is active or the idle timer runs. */
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    /** The one video with sound on. Null means everything is muted. */
    var unmutedKey by mutableStateOf<String?>(null)
        private set

    /** True when the user paused the active video. */
    var paused by mutableStateOf(false)
        private set

    /** The tile key of the video open in the viewer, or null. While set, the feed slot stays stopped. */
    var viewerKey by mutableStateOf<String?>(null)
        private set

    /** The viewer slot's player. Separate from the feed slot so the feed player can stay warm. */
    var viewerPlayer by mutableStateOf<ExoPlayer?>(null)
        private set

    var viewerUnmuted by mutableStateOf(false)
        private set

    /** True after the viewer player failed. [retryViewer] clears it. */
    var viewerFailed by mutableStateOf(false)
        private set

    /** Decoded size of the viewer video once known, so the open transition can land on the real frame. */
    var viewerVideoSize by mutableStateOf<Size?>(null)
        private set

    fun report(key: String, order: Int, fraction: Float, contentVisible: Boolean, url: String, maxWidthPx: Int) {
        tiles[key] = Tile(order, fraction, contentVisible, url, maxWidthPx)
        scheduleEvaluate()
    }

    /** The decoded size of the video at [url] once any player has seen it. The open transition uses it when the server sent none. */
    fun knownVideoSize(url: String?): Size? = url?.let { knownSizes[it] }

    fun isFailed(key: String): Boolean = key in failed

    /** Clears a tile's failure so autoplay or a tap can try the video again. */
    fun retry(key: String) {
        if (failed.remove(key)) scheduleEvaluate()
    }

    fun remove(key: String) {
        if (tiles.remove(key) != null) scheduleEvaluate()
    }

    fun updateEnvironment(next: AutoplayEnvironment) {
        if (next == environment) return
        environment = next
        evaluate()
    }

    fun toggleSound(key: String) {
        if (key != activeKey) return
        unmutedKey = if (unmutedKey == key) null else key
        player?.volume = if (unmutedKey == key) 1f else 0f
    }

    fun togglePause(key: String) {
        if (key != activeKey) return
        val current = player ?: return
        paused = !paused
        current.playWhenReady = !paused
    }

    /**
     * Takes the viewer slot for [key]. The video starts where the feed tile was and keeps its sound
     * state when it is the same video; any other video starts at zero and muted. The feed slot stops.
     */
    fun openViewer(key: String, url: String) {
        val handoff = viewerHandoff(
            loadedKey = loadedKey,
            activeKey = activeKey,
            feedPositionMs = player?.currentPosition ?: 0L,
            unmutedKey = unmutedKey,
            key = key,
        )
        closeViewerPlayer()
        viewerKey = key
        viewerUnmuted = handoff.unmuted
        viewerFailed = false
        viewerVideoSize = null
        evaluate()
        val created = buildPlayer(
            onError = { viewerFailed = true },
            onVideoSize = { size ->
                viewerVideoSize = size
                knownSizes[url] = size
            },
        )
        created.volume = if (handoff.unmuted) 1f else 0f
        created.setMediaItem(MediaItem.fromUri(url), handoff.startPositionMs)
        created.prepare()
        created.playWhenReady = true
        viewerPlayer = created
    }

    /** Releases the viewer slot if [key] holds it, then lets the feed pick a video again. */
    fun closeViewer(key: String) {
        if (viewerKey != key) return
        closeViewerPlayer()
        evaluate()
    }

    fun toggleViewerSound() {
        viewerUnmuted = !viewerUnmuted
        viewerPlayer?.volume = if (viewerUnmuted) 1f else 0f
    }

    fun retryViewer() {
        val current = viewerPlayer ?: return
        viewerFailed = false
        current.prepare()
        current.playWhenReady = true
    }

    fun release() {
        evaluateJob?.cancel()
        releaseJob?.cancel()
        closeViewerPlayer()
        releasePlayer()
        activeKey = null
    }

    private fun closeViewerPlayer() {
        viewerPlayer?.release()
        viewerPlayer = null
        viewerKey = null
        viewerUnmuted = false
        viewerFailed = false
        viewerVideoSize = null
    }

    private fun scheduleEvaluate() {
        evaluateJob?.cancel()
        evaluateJob = scope.launch {
            delay(EVALUATE_DEBOUNCE_MS)
            evaluate()
        }
    }

    private fun evaluate() {
        val candidates = tiles.filterKeys { it !in failed }.map { (key, tile) ->
            AutoplayCandidate(key, tile.order, tile.fraction, tile.contentVisible)
        }
        activate(if (viewerKey != null) null else AutoplayPolicy.select(candidates, environment, activeKey))
    }

    private fun activate(next: String?) {
        if (next == activeKey) return
        activeKey = next
        unmutedKey = null
        paused = false
        val tile = next?.let { tiles[it] }
        if (next == null || tile == null) {
            player?.pause()
            scheduleRelease()
            return
        }
        releaseJob?.cancel()
        val current = player ?: createFeedPlayer().also { player = it }
        current.volume = 0f
        if (loadedKey != next) {
            current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
                .setMaxVideoSize((tile.maxWidthPx * 2).coerceAtLeast(1), Int.MAX_VALUE)
                .build()
            current.repeatMode = Player.REPEAT_MODE_OFF
            current.setMediaItem(MediaItem.fromUri(tile.url))
            current.prepare()
            loadedKey = next
        }
        current.playWhenReady = true
    }

    private fun createFeedPlayer(): ExoPlayer = buildPlayer(
        onVideoSize = { size -> loadedKey?.let { tiles[it]?.url }?.let { knownSizes[it] = size } },
        onError = {
            loadedKey?.let { failed.add(it) }
            loadedKey = null
            player?.stop()
            activeKey = null
            evaluate()
            scheduleRelease()
        },
    )

    /** Builds a player with the shared buffering and loop rules. Clips under 30 s loop once the duration is known. */
    private fun buildPlayer(onError: () -> Unit, onVideoSize: (Size) -> Unit = {}): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5_000, 15_000, BUFFER_FOR_PLAYBACK_MS, 3_000)
            .build()
        return ExoPlayer.Builder(context).setLoadControl(loadControl).build().also { created ->
            created.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_READY) return
                        val duration = created.duration.takeIf { it != C.TIME_UNSET }
                        created.repeatMode =
                            if (AutoplayPolicy.shouldLoop(duration)) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    }

                    override fun onPlayerError(error: PlaybackException) = onError()

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            onVideoSize(Size(videoSize.width * videoSize.pixelWidthHeightRatio, videoSize.height.toFloat()))
                        }
                    }
                },
            )
        }
    }

    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            if (activeKey == null) releasePlayer()
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        loadedKey = null
    }

    private companion object {
        const val EVALUATE_DEBOUNCE_MS = 50L
        const val IDLE_RELEASE_MS = 10_000L
        const val BUFFER_FOR_PLAYBACK_MS = 1_500
    }
}

/** Where a viewer video starts and whether it has sound. */
data class ViewerHandoff(val startPositionMs: Long, val unmuted: Boolean)

/**
 * The viewer continues the feed tile only when it is the same video that the feed slot holds.
 * Sound carries over only for the selected video, so any other video starts at zero and muted.
 */
fun viewerHandoff(
    loadedKey: String?,
    activeKey: String?,
    feedPositionMs: Long,
    unmutedKey: String?,
    key: String,
): ViewerHandoff {
    val sameVideo = loadedKey == key
    return ViewerHandoff(
        startPositionMs = if (sameVideo) feedPositionMs.coerceAtLeast(0L) else 0L,
        unmuted = sameVideo && activeKey == key && unmutedKey == key,
    )
}

/** Null where no host provides one, such as previews and isolated tests. Tiles then show posters only. */
val LocalVideoPlaybackCoordinator = staticCompositionLocalOf<VideoPlaybackCoordinator?> { null }

/**
 * Creates the coordinator for one app lifetime and feeds it the conditions that gate autoplay:
 * the setting, the metered state, reduced motion, and the foreground. Releases the player on leave.
 */
@Composable
fun rememberVideoPlaybackCoordinator(
    autoplayEnabled: Boolean,
    monitor: MeteredNetworkMonitor?,
): VideoPlaybackCoordinator {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { VideoPlaybackCoordinator(context.applicationContext, scope) }
    DisposableEffect(coordinator) { onDispose { coordinator.release() } }
    val metered by remember(monitor) { monitor?.metered ?: flowOf(true) }.collectAsStateWithLifecycle(null)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var reducedMotion by remember { mutableStateOf(isReducedMotion(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        reducedMotion = isReducedMotion(context)
        foreground = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { foreground = false }
    LaunchedEffect(autoplayEnabled, metered, reducedMotion, foreground) {
        coordinator.updateEnvironment(AutoplayEnvironment(autoplayEnabled, metered, reducedMotion, foreground))
    }
    return coordinator
}

/** Renders [player] on a TextureView so clipping and transforms apply. [shutter] covers until the first frame. */
@Composable
fun VideoSurface(
    player: Player,
    shutter: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier.fillMaxSize()) {
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = contentScale,
            shutter = shutter,
        )
    }
}
