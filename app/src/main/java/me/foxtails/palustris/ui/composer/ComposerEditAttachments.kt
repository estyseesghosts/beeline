package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.foxtails.palustris.R
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.ui.AppIcons

/**
 * The images that stay on a post being edited. Each one can lose its image or change its alt text.
 * New images cannot be added in an edit. It holds no state beyond the open alt text dialog; the
 * edit editor owns the list.
 */
@Composable
internal fun ComposerEditAttachments(
    attachments: List<EditAttachmentState>,
    maxAltLength: Int?,
    onAlt: (id: String, text: String?) -> Unit,
    onRemove: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var altTarget by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val loader = remember(context) { MediaImageLoader.get(context).imageLoader }
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEachIndexed { index, attachment ->
            Row(Modifier.fillMaxWidth().testTag("composer_edit_attachment_$index"), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(attachment.previewUrl).build(),
                    imageLoader = loader,
                    contentDescription = attachment.description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                )
                TextButton(
                    onClick = { altTarget = attachment.id },
                    modifier = Modifier.weight(1f).testTag("composer_edit_alt_$index"),
                ) {
                    Text(
                        attachment.description ?: stringResource(R.string.composer_edit_image_no_alt),
                        maxLines = 2,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = { onRemove(attachment.id) }, modifier = Modifier.testTag("composer_edit_remove_$index")) {
                    Icon(AppIcons.Close, stringResource(R.string.composer_edit_image_remove, index + 1))
                }
            }
        }
    }
    attachments.firstOrNull { it.id == altTarget }?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.description.orEmpty()) }
        val over = maxAltLength != null && text.length > maxAltLength
        AlertDialog(
            onDismissRequest = { altTarget = null },
            title = { Text(stringResource(R.string.composer_alt_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    isError = over,
                    modifier = Modifier.fillMaxWidth().testTag("composer_edit_alt_field"),
                    supportingText = maxAltLength?.let { limit -> { Text(stringResource(R.string.composer_warning_remaining, limit - text.length)) } },
                )
            },
            confirmButton = {
                TextButton(enabled = !over, onClick = { onAlt(target.id, text.takeIf(String::isNotBlank)); altTarget = null }) {
                    Text(stringResource(R.string.composer_save))
                }
            },
            dismissButton = { TextButton(onClick = { altTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
