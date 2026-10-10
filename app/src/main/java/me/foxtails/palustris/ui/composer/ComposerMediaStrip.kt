package me.foxtails.palustris.ui.composer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.ui.AppIcons

/** Loads a thumbnail of a draft image no larger than the edge. The callback reports null on failure. */
internal typealias ThumbnailLoader = (media: DraftMedia, maxEdge: Int, onResult: (Bitmap?) -> Unit) -> Unit

private val THUMBNAIL_SIZE = 88.dp
private const val THUMBNAIL_EDGE_PX = 320
private const val DIALOG_EDGE_PX = 960

/**
 * The images of one entry as a row of thumbnails. Each thumbnail has a remove button and an ALT
 * badge. A tap on the thumbnail opens the alt text editor.
 */
@Composable
internal fun ComposerMediaStrip(
    media: List<DraftMedia>,
    loadThumbnail: ThumbnailLoader,
    onRemove: (mediaId: String) -> Unit,
    onEditAlt: (mediaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        media.forEach { item ->
            ComposerThumbnail(item, loadThumbnail, onRemove = { onRemove(item.id) }, onEditAlt = { onEditAlt(item.id) })
        }
    }
}

/** A play glyph on a draft video's poster. It does not take touches, so the thumbnail still opens the alt editor. */
@Composable
private fun VideoThumbnailBadge(modifier: Modifier) {
    Box(modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(16.dp)) {
            val triangle = Path().apply {
                moveTo(size.width * 0.25f, size.height * 0.1f)
                lineTo(size.width * 0.9f, size.height * 0.5f)
                lineTo(size.width * 0.25f, size.height * 0.9f)
                close()
            }
            drawPath(triangle, Color.White)
        }
    }
}

@Composable
private fun ComposerThumbnail(
    media: DraftMedia,
    loadThumbnail: ThumbnailLoader,
    onRemove: () -> Unit,
    onEditAlt: () -> Unit,
) {
    val picture by rememberThumbnail(media, loadThumbnail, THUMBNAIL_EDGE_PX)
    val described = !media.altText.isNullOrBlank()
    val isVideo = media.mimeType.startsWith("video/")
    val description = media.altText?.takeIf(String::isNotBlank)
        ?: stringResource(if (isVideo) R.string.composer_video_without_description else R.string.composer_image_without_description)
    val editLabel = stringResource(R.string.composer_alt_title)
    Box(Modifier.size(THUMBNAIL_SIZE).clip(RoundedCornerShape(12.dp))) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(THUMBNAIL_SIZE)
                .clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEditAlt),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            picture?.let {
                Image(it, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(THUMBNAIL_SIZE))
            }
        }
        if (isVideo) VideoThumbnailBadge(Modifier.align(Alignment.Center))
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            shape = RoundedCornerShape(6.dp),
            color = if (described) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (described) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                stringResource(R.string.composer_alt_badge),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                Icon(
                    AppIcons.Close,
                    contentDescription = stringResource(R.string.composer_remove_image),
                    modifier = Modifier.padding(4.dp).size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberThumbnail(media: DraftMedia, loadThumbnail: ThumbnailLoader, edge: Int): State<ImageBitmap?> {
    // The loader may be a new lambda on each recomposition; only a different image reloads.
    val latest by rememberUpdatedState(loadThumbnail)
    return produceState<ImageBitmap?>(initialValue = null, media.id, edge) {
        latest(media, edge) { bitmap -> value = bitmap?.asImageBitmap() }
    }
}

/** True when [text] is longer than the server allows for alt text. A null [maxLength] means no limit. */
internal fun altTextOverLimit(text: String, maxLength: Int?): Boolean = maxLength != null && text.length > maxLength

/**
 * Edits the alt text of one image in a dialog. [ComposerAltTextForm] and [ComposerAltTextSave] hold
 * the content so tests can exercise them without a dialog window.
 */
@Composable
internal fun ComposerAltTextDialog(
    media: DraftMedia,
    maxLength: Int?,
    loadThumbnail: ThumbnailLoader,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(media.id) { mutableStateOf(media.altText.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.composer_alt_title)) },
        text = { ComposerAltTextForm(media, text, { text = it }, maxLength, loadThumbnail) },
        confirmButton = { ComposerAltTextSave(text, maxLength, onSave) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The image, the text field, and the characters remaining. The field keeps text over the limit and marks it.
 * The image steps aside while the keyboard is up, so the field and the buttons stay in view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposerAltTextForm(
    media: DraftMedia,
    text: String,
    onTextChange: (String) -> Unit,
    maxLength: Int?,
    loadThumbnail: ThumbnailLoader,
) {
    val picture by rememberThumbnail(media, loadThumbnail, DIALOG_EDGE_PX)
    val remaining = maxLength?.let { it - text.length }
    val keyboardUp = WindowInsets.isImeVisible
    Column(Modifier.verticalScroll(rememberScrollState())) {
        picture?.takeUnless { keyboardUp }?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(stringResource(R.string.composer_alt_hint)) },
            isError = altTextOverLimit(text, maxLength),
            supportingText = remaining?.let { left ->
                { Text(stringResource(R.string.composer_characters_remaining, left)) }
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Save stays disabled while the text is over the limit, so an over-long description never reaches the draft. */
@Composable
internal fun ComposerAltTextSave(text: String, maxLength: Int?, onSave: (String?) -> Unit) {
    TextButton(enabled = !altTextOverLimit(text, maxLength), onClick = { onSave(text.takeIf(String::isNotBlank)) }) {
        Text(stringResource(R.string.composer_alt_save))
    }
}
