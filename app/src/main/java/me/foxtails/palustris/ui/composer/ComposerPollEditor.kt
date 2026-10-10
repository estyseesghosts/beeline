package me.foxtails.palustris.ui.composer

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.PollLimits
import me.foxtails.palustris.domain.PollProblem
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.ui.AppIcons

/** How long a new poll runs when the author picks nothing: one day, held inside the server's bounds. */
private const val DEFAULT_EXPIRY_SECONDS = 86_400L

/** Expiry presets in seconds. Only those inside the server's bounds are offered. */
private val EXPIRY_PRESETS: List<Pair<Long, Int>> = listOf(
    300L to R.string.composer_poll_duration_5m,
    1_800L to R.string.composer_poll_duration_30m,
    3_600L to R.string.composer_poll_duration_1h,
    21_600L to R.string.composer_poll_duration_6h,
    86_400L to R.string.composer_poll_duration_1d,
    259_200L to R.string.composer_poll_duration_3d,
    604_800L to R.string.composer_poll_duration_7d,
)

/** The presets that fit [limits]. A server with a shorter range than every preset still gets its bounds. */
internal fun expiryChoices(limits: PollLimits): List<Pair<Long, Int>> {
    val within = EXPIRY_PRESETS.filter { (seconds, _) ->
        seconds >= limits.minExpirySeconds && (limits.maxExpirySeconds == null || seconds <= limits.maxExpirySeconds)
    }
    return within.ifEmpty { EXPIRY_PRESETS.take(1) }
}

/** A fresh poll: two empty choices, single choice, one day (or the nearest length the server allows). */
internal fun newPoll(limits: PollLimits): PollRequest {
    val choices = expiryChoices(limits).map { it.first }
    val expiry = choices.minByOrNull { kotlin.math.abs(it - DEFAULT_EXPIRY_SECONDS) } ?: DEFAULT_EXPIRY_SECONDS
    return PollRequest(choices = listOf("", ""), multiple = false, expiresInSeconds = expiry)
}

/**
 * The poll fields of one entry: the choices, single or multiple choice, how long it runs, and
 * whether totals stay hidden. It holds no state; the entry owns the poll and [onChange] replaces it.
 */
@Composable
internal fun ComposerPollEditor(
    poll: PollRequest,
    limits: PollLimits,
    onChange: (PollRequest) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("composer_poll"), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            poll.choices.forEachIndexed { index, choice ->
                val remaining = limits.maxOptionLength - choice.length
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = choice,
                        onValueChange = { changed ->
                            onChange(poll.copy(choices = poll.choices.toMutableList().also { it[index] = changed }))
                        },
                        modifier = Modifier.weight(1f).testTag("composer_poll_choice_$index"),
                        label = { Text(stringResource(R.string.composer_poll_choice, index + 1)) },
                        singleLine = true,
                        isError = remaining < 0,
                        supportingText = if (remaining <= CHOICE_COUNTER_THRESHOLD) {
                            { Text(stringResource(R.string.composer_warning_remaining, remaining)) }
                        } else {
                            null
                        },
                    )
                    if (poll.choices.size > PollLimits.MIN_OPTIONS) {
                        IconButton(
                            onClick = { onChange(poll.copy(choices = poll.choices.filterIndexed { i, _ -> i != index })) },
                            modifier = Modifier.testTag("composer_poll_remove_choice_$index"),
                        ) { Icon(AppIcons.Close, stringResource(R.string.composer_poll_remove_choice, index + 1)) }
                    }
                }
            }
            if (poll.choices.size < limits.maxOptions) {
                TextButton(
                    onClick = { onChange(poll.copy(choices = poll.choices + "")) },
                    modifier = Modifier.testTag("composer_poll_add_choice"),
                ) { Text(stringResource(R.string.composer_poll_add_choice)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(R.string.composer_poll_single, selected = !poll.multiple, tag = "composer_poll_single") { onChange(poll.copy(multiple = false)) }
                ModeChip(R.string.composer_poll_multiple, selected = poll.multiple, tag = "composer_poll_multiple") { onChange(poll.copy(multiple = true)) }
            }
            Text(
                stringResource(R.string.composer_poll_duration),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                expiryChoices(limits).forEach { (seconds, label) ->
                    ModeChip(label, selected = poll.expiresInSeconds == seconds, tag = "composer_poll_duration_$seconds") {
                        onChange(poll.copy(expiresInSeconds = seconds))
                    }
                }
            }
            if (limits.hideTotals) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = poll.hideTotals, onCheckedChange = { onChange(poll.copy(hideTotals = it)) })
                    Text(stringResource(R.string.composer_poll_hide_totals), style = MaterialTheme.typography.bodyMedium)
                }
            }
            poll.problem(limits)?.takeIf { it != PollProblem.TooFewOptions || poll.choices.any(String::isNotBlank) }?.let { problem ->
                Text(
                    stringResource(problem.message()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("composer_poll_problem"),
                )
            }
            TextButton(onClick = onRemove, modifier = Modifier.testTag("composer_poll_remove")) {
                Text(stringResource(R.string.composer_poll_remove))
            }
        }
    }
}

@Composable
private fun ModeChip(@StringRes label: Int, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) }, modifier = Modifier.testTag(tag))
}

@StringRes
private fun PollProblem.message(): Int = when (this) {
    PollProblem.Unsupported -> R.string.composer_poll_problem_unsupported
    PollProblem.TooFewOptions -> R.string.composer_poll_problem_few
    PollProblem.TooManyOptions -> R.string.composer_poll_problem_many
    PollProblem.OptionTooLong -> R.string.composer_poll_problem_long
    PollProblem.DuplicateOptions -> R.string.composer_poll_problem_duplicate
    PollProblem.ExpiryOutOfRange -> R.string.composer_poll_problem_expiry
}

/** The counter appears only when a choice is close to the limit. */
private const val CHOICE_COUNTER_THRESHOLD = 10
