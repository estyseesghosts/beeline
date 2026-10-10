package me.foxtails.palustris.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import me.foxtails.palustris.R

private val Scrim = Color.Black.copy(alpha = 0.55f)

/**
 * Tile controls drawn on a canvas, with no Material components. An inactive tile shows a play
 * badge. The active tile shows a play or pause button and a sound toggle. The badge does not
 * intercept touches, so a tap on the tile still opens the viewer.
 */
@Composable
fun VideoTileControls(
    active: Boolean,
    unmuted: Boolean,
    paused: Boolean,
    onToggleSound: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long? = null,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        if (failed) {
            VideoRetryPill(onRetry, Modifier.align(Alignment.Center))
        } else if (!active) {
            val playDescription = videoPlayDescription(paused = true)
            ControlGlyph(
                modifier = Modifier.align(Alignment.Center).size(48.dp)
                    .semantics { contentDescription = playDescription },
                draw = { drawPlay() },
            )
            if (durationMs != null && durationMs > 0L) {
                Text(
                    formatVideoTime(durationMs),
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).testTag("video_duration")
                        .background(Scrim, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            ControlButton(
                description = videoPlayDescription(paused),
                tag = "video_play_pause",
                onClick = onTogglePause,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) { if (paused) drawPlay() else drawPause() }
            ControlButton(
                description = videoSoundDescription(unmuted),
                tag = "video_sound",
                onClick = onToggleSound,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) { drawSpeaker(unmuted) }
        }
    }
}

/** Shown on a tile whose video failed. It is a button, so a tap retries and does not open the viewer. */
@Composable
private fun VideoRetryPill(onRetry: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.video_retry)
    Text(
        label,
        modifier = modifier
            .testTag("video_retry")
            .clip(RoundedCornerShape(20.dp))
            .background(Scrim)
            .semantics { role = Role.Button }
            .clickable(onClick = onRetry)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun ControlGlyph(modifier: Modifier, draw: DrawScope.() -> Unit) {
    Box(modifier.clip(CircleShape).background(Scrim, CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) { draw() }
    }
}

@Composable
private fun ControlButton(
    description: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier,
    background: Color = Scrim,
    draw: DrawScope.() -> Unit,
) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background, CircleShape)
            .testTag(tag)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) { draw() }
    }
}

private fun DrawScope.drawPlay() {
    val path = Path().apply {
        moveTo(size.width * 0.25f, size.height * 0.1f)
        lineTo(size.width * 0.9f, size.height * 0.5f)
        lineTo(size.width * 0.25f, size.height * 0.9f)
        close()
    }
    drawPath(path, Color.White)
}

private fun DrawScope.drawPause() {
    val barWidth = size.width * 0.28f
    drawRect(Color.White, Offset(size.width * 0.12f, size.height * 0.1f), Size(barWidth, size.height * 0.8f))
    drawRect(Color.White, Offset(size.width * 0.6f, size.height * 0.1f), Size(barWidth, size.height * 0.8f))
}

private fun DrawScope.drawSpeaker(unmuted: Boolean) {
    val body = Path().apply {
        moveTo(0f, size.height * 0.36f)
        lineTo(size.width * 0.25f, size.height * 0.36f)
        lineTo(size.width * 0.5f, size.height * 0.12f)
        lineTo(size.width * 0.5f, size.height * 0.88f)
        lineTo(size.width * 0.25f, size.height * 0.64f)
        lineTo(0f, size.height * 0.64f)
        close()
    }
    drawPath(body, Color.White)
    val stroke = Stroke(width = size.width * 0.09f, cap = StrokeCap.Round)
    if (unmuted) {
        drawArc(
            Color.White, -45f, 90f, false,
            topLeft = Offset(size.width * 0.5f, size.height * 0.3f),
            size = Size(size.width * 0.3f, size.height * 0.4f), style = stroke,
        )
        drawArc(
            Color.White, -45f, 90f, false,
            topLeft = Offset(size.width * 0.5f, size.height * 0.12f),
            size = Size(size.width * 0.46f, size.height * 0.76f), style = stroke,
        )
    } else {
        drawLine(Color.White, Offset(size.width * 0.66f, size.height * 0.35f), Offset(size.width * 0.96f, size.height * 0.65f), stroke.width, StrokeCap.Round)
        drawLine(Color.White, Offset(size.width * 0.96f, size.height * 0.35f), Offset(size.width * 0.66f, size.height * 0.65f), stroke.width, StrokeCap.Round)
    }
}

/** How long the viewer controls stay up while the video plays. */
const val VIEWER_CONTROLS_HIDE_MS = 3_000L

/** Visibility of the viewer controls: a tap shows or hides them, and they hide on their own while playing. */
@Stable
class AutoHideState {
    var visible by mutableStateOf(true)
        internal set
    internal var touches by mutableIntStateOf(0)

    fun show() {
        visible = true
        touches++
    }

    fun toggle() {
        if (visible) visible = false else show()
    }
}

@Composable
fun rememberAutoHideState(playing: Boolean, delayMs: Long = VIEWER_CONTROLS_HIDE_MS): AutoHideState {
    val state = remember { AutoHideState() }
    LaunchedEffect(state.visible, state.touches, playing) {
        if (state.visible && playing) {
            delay(delayMs)
            state.visible = false
        }
    }
    return state
}

@Composable
fun rememberIsPlaying(player: Player?): Boolean {
    var playing by remember(player) { mutableStateOf(player?.isPlaying == true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    return playing
}

/** Formats a position as m:ss, or h:mm:ss from one hour. Negative values read as zero. */
fun formatVideoTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/**
 * Viewer controls: play or pause, a seek bar with time, and the sound toggle. Presentation only;
 * every action goes to [player] or the callbacks. [onInteract] keeps the controls up while the user acts.
 */
@Composable
fun VideoViewerControls(
    player: Player,
    unmuted: Boolean,
    onToggleSound: () -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = rememberIsPlaying(player)
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(PROGRESS_TICK_MS)
        }
    }
    val paused = !playing
    Row(
        modifier.fillMaxWidth().background(Scrim, RoundedCornerShape(24.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ControlButton(
            description = videoPlayDescription(paused),
            tag = "video_play_pause",
            onClick = {
                onInteract()
                if (playing) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                    player.play()
                }
            },
            modifier = Modifier,
            background = Color.Transparent,
        ) { if (paused) drawPlay() else drawPause() }
        Text(
            stringResource(R.string.video_time, formatVideoTime(position), formatVideoTime(duration)),
            color = Color.White,
            fontSize = 12.sp,
            style = MaterialTheme.typography.labelMedium,
        )
        SeekBar(
            fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            description = stringResource(R.string.video_seek),
            onSeek = { fraction ->
                onInteract()
                if (duration > 0) player.seekTo((duration * fraction).toLong())
            },
            modifier = Modifier.weight(1f),
        )
        ControlButton(
            description = videoSoundDescription(unmuted),
            tag = "video_sound",
            onClick = {
                onInteract()
                onToggleSound()
            },
            modifier = Modifier,
            background = Color.Transparent,
        ) { drawSpeaker(unmuted) }
    }
}

@Composable
private fun SeekBar(fraction: Float, description: String, onSeek: (Float) -> Unit, modifier: Modifier) {
    var width by remember { mutableIntStateOf(1) }
    Canvas(
        modifier
            .height(40.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .semantics { contentDescription = description }
            .testTag("video_seek")
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val centerY = size.height / 2f
        val stroke = 4.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, centerY), Offset(size.width, centerY), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(0f, centerY), Offset(size.width * fraction, centerY), stroke, StrokeCap.Round)
        drawCircle(Color.White, 7.dp.toPx(), Offset(size.width * fraction, centerY))
    }
}

private const val PROGRESS_TICK_MS = 200L
