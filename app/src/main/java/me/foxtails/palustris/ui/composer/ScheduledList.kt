package me.foxtails.palustris.ui.composer

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ScheduledPost
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState

/**
 * What the Scheduled tab shows and does. The composer owner holds the list; the tab only reads it and reports
 * taps. [minLeadSeconds] bounds the time a reschedule may pick.
 */
class ScheduledTab(
    val items: List<ScheduledPost>,
    val loading: Boolean,
    val error: String?,
    val minLeadSeconds: Long,
    val onRefresh: () -> Unit,
    val onCancel: (ScheduledPost) -> Unit,
    val onReschedule: (ScheduledPost, Long) -> Unit,
)

@Composable
internal fun ScheduledList(tab: ScheduledTab) {
    LaunchedEffect(Unit) { tab.onRefresh() }
    var rescheduling by remember { mutableStateOf<ScheduledPost?>(null) }
    var cancelling by remember { mutableStateOf<ScheduledPost?>(null) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        tab.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp).testTag("scheduled_error"),
            )
        }
        if (tab.items.isEmpty() && !tab.loading) {
            EmptyState(AppIcons.Folder, stringResource(R.string.scheduled_empty), "")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                items(tab.items, key = { it.id.ifBlank { "${it.scheduledAtEpochMillis}${it.text}" } }) { item ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("scheduled_item")) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                DateUtils.formatDateTime(context, item.scheduledAtEpochMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                item.text.ifBlank { item.contentWarning.orEmpty() },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.attachmentCount > 0) {
                                Text(
                                    stringResource(R.string.scheduled_attachments, item.attachmentCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { rescheduling = item }, modifier = Modifier.testTag("scheduled_reschedule")) {
                                    Text(stringResource(R.string.scheduled_reschedule))
                                }
                                TextButton(onClick = { cancelling = item }, modifier = Modifier.testTag("scheduled_cancel")) {
                                    Text(stringResource(R.string.scheduled_cancel))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    rescheduling?.let { item ->
        ComposerSchedulePicker(
            initial = item.scheduledAtEpochMillis,
            minLeadSeconds = tab.minLeadSeconds,
            nowMillis = System::currentTimeMillis,
            onDismiss = { rescheduling = null },
            onConfirm = { at ->
                rescheduling = null
                // A null time means "post now", which a reschedule does not offer.
                if (at != null) tab.onReschedule(item, at)
            },
        )
    }
    cancelling?.let { item ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text(stringResource(R.string.scheduled_cancel)) },
            text = { Text(item.text.ifBlank { item.contentWarning.orEmpty() }, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { cancelling = null; tab.onCancel(item) }, modifier = Modifier.testTag("scheduled_cancel_confirm")) {
                    Text(stringResource(R.string.scheduled_cancel))
                }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}
