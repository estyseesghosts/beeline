package me.foxtails.palustris.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
) {
    Box(modifier.fillMaxSize()) {
        if (!active) {
            val playDescription = videoPlayDescription(paused = true)
            ControlGlyph(
                modifier = Modifier.align(Alignment.Center).size(48.dp)
                    .semantics { contentDescription = playDescription },
                draw = { drawPlay() },
            )
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
    draw: DrawScope.() -> Unit,
) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Scrim, CircleShape)
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
