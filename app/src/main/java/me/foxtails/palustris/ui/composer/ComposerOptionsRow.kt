package me.foxtails.palustris.ui.composer

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.LocalOnlyRoute
import me.foxtails.palustris.domain.PostOptions
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.QuoteApprovalPolicy
import me.foxtails.palustris.ui.AppIcons

/**
 * Row 2 of the composer bubbles: the choices that belong to the whole thread. They are its audience, keeping it
 * on the author's server, who may quote it, and when to send it. Each pill shows only when the server offers it.
 * The row holds no state beyond its open menus and picker; the editor owns [options] and [audience].
 */
@Composable
internal fun ComposerOptionsRow(
    options: PostOptions,
    audience: Audience,
    posting: PostingCapabilities,
    entryCount: Int,
    onLocalOnly: (Boolean) -> Unit,
    onQuotePolicy: (QuoteApprovalPolicy?) -> Unit,
    onScheduledAt: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    audienceOptions: List<Audience> = emptyList(),
    onAudience: (Audience) -> Unit = {},
    nowMillis: () -> Long = System::currentTimeMillis,
) {
    val localOnlyShown = posting.localOnly != LocalOnlyRoute.None
    val scheduling = posting.scheduling
    if (audienceOptions.isEmpty() && !localOnlyShown && !posting.quoteApprovalPolicy && scheduling == null) return
    var audienceOpen by remember { mutableStateOf(false) }
    var policyOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (audienceOptions.isNotEmpty()) {
            Box {
                val label = stringResource(audience.labelRes)
                ComposerPillBubble(
                    label = label,
                    description = stringResource(R.string.composer_audience_row, label),
                    leadingIcon = AppIcons.Globe,
                    enabled = audienceOptions.size > 1,
                    onClick = { audienceOpen = true },
                    modifier = Modifier.testTag("composer_option_audience"),
                )
                DropdownMenu(expanded = audienceOpen, onDismissRequest = { audienceOpen = false }) {
                    audienceOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        stringResource(option.descriptionRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingIcon = { if (option == audience) Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                audienceOpen = false
                                onAudience(option)
                            },
                        )
                    }
                }
            }
        }
        if (localOnlyShown) {
            // Pleroma keeps a post on the server through the `local` visibility, which only a public post has.
            val enabled = posting.localOnly != LocalOnlyRoute.PleromaVisibility || audience == Audience.Public
            ComposerPillBubble(
                label = stringResource(R.string.composer_option_local_only),
                leadingIcon = AppIcons.Person,
                tone = if (options.localOnly && enabled) ComposerBubbleTone.Selected else ComposerBubbleTone.Plain,
                enabled = enabled,
                onClick = { onLocalOnly(!options.localOnly) },
                modifier = Modifier.testTag("composer_option_local_only"),
            )
        }
        if (posting.quoteApprovalPolicy) {
            Box {
                ComposerPillBubble(
                    label = stringResource(R.string.composer_option_quote_policy, stringResource(options.quotePolicy.label())),
                    tone = if (options.quotePolicy != null) ComposerBubbleTone.Selected else ComposerBubbleTone.Plain,
                    onClick = { policyOpen = true },
                    modifier = Modifier.testTag("composer_option_quote_policy"),
                )
                DropdownMenu(expanded = policyOpen, onDismissRequest = { policyOpen = false }) {
                    listOf<QuoteApprovalPolicy?>(null).plus(QuoteApprovalPolicy.entries).forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(stringResource(choice.label())) },
                            onClick = {
                                policyOpen = false
                                onQuotePolicy(choice)
                            },
                        )
                    }
                }
            }
        }
        if (scheduling != null) {
            val scheduled = options.scheduledAtEpochMillis
            val context = LocalContext.current
            ComposerPillBubble(
                label = if (scheduled == null) {
                    stringResource(R.string.composer_option_schedule)
                } else {
                    DateUtils.formatDateTime(context, scheduled, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL)
                },
                leadingIcon = ComposerIcons.Calendar,
                tone = if (scheduled != null) ComposerBubbleTone.Selected else ComposerBubbleTone.Plain,
                // A thread cannot be scheduled, because its later posts reply to a post that does not exist yet.
                enabled = entryCount == 1,
                onClick = { pickerOpen = true },
                modifier = Modifier.testTag("composer_option_schedule"),
            )
            if (pickerOpen) {
                ComposerSchedulePicker(
                    initial = scheduled,
                    minLeadSeconds = scheduling.minLeadSeconds,
                    nowMillis = nowMillis,
                    onDismiss = { pickerOpen = false },
                    onConfirm = { at ->
                        pickerOpen = false
                        onScheduledAt(at)
                    },
                )
            }
        }
    }
}

private fun QuoteApprovalPolicy?.label(): Int = when (this) {
    null -> R.string.quote_policy_default
    QuoteApprovalPolicy.Public -> R.string.quote_policy_public
    QuoteApprovalPolicy.Followers -> R.string.quote_policy_followers
    QuoteApprovalPolicy.Nobody -> R.string.quote_policy_nobody
}

/**
 * A date, then a time. The time must lie at least [minLeadSeconds] ahead. A schedule that is already set can be
 * cleared, which publishes at once again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerSchedulePicker(
    initial: Long?,
    minLeadSeconds: Long,
    nowMillis: () -> Long,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val start = remember(initial) { Instant.ofEpochMilli(initial ?: (nowMillis() + (minLeadSeconds + 600L) * 1000L)).atZone(zone) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val context = LocalContext.current
    val timeState = rememberTimePickerState(start.hour, start.minute, DateFormat.is24HourFormat(context))
    var step by remember { mutableStateOf(0) }
    val chosen = dateState.selectedDateMillis?.let { utc ->
        Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate().atTime(LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant().toEpochMilli()
    }
    val tooSoon = chosen != null && chosen < nowMillis() + minLeadSeconds * 1000L
    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(enabled = dateState.selectedDateMillis != null, onClick = { step = 1 }) { Text(stringResource(R.string.composer_schedule_next)) }
            },
            dismissButton = {
                if (initial != null) TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.composer_schedule_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = dateState, showModeToggle = false)
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.composer_schedule_pick_time)) },
        text = {
            Column(Modifier.testTag("composer_schedule_picker")) {
                TimePicker(state = timeState)
                if (tooSoon) {
                    Text(
                        stringResource(R.string.composer_schedule_too_soon, ((minLeadSeconds + 59L) / 60L).toInt()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("composer_schedule_too_soon"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen != null && !tooSoon, onClick = { onConfirm(chosen) }, modifier = Modifier.testTag("composer_schedule_confirm")) {
                Text(stringResource(R.string.composer_schedule_set))
            }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.composer_schedule_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
