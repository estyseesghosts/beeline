package me.foxtails.palustris.ui.posts

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Poll
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.emoji.InlineEmojiText

/**
 * A poll on a post. While the account may vote it shows choices and a Vote button; once the account
 * has voted or the poll has ended it shows the results. Shared by the row and the single-post view.
 * It reads the poll through [LocalPostPolls], so a vote shows the same in every surface.
 */
@Composable
internal fun PostPollOptions(ownedPost: OwnedPost) {
    val presentation = LocalPostPolls.current
    val poll = presentation?.poll(ownedPost) ?: ownedPost.post.poll ?: return
    if (poll.options.isEmpty()) return
    val target = ownedPost.effectiveTargetId()
    val status = presentation?.status(target) ?: PollVoteStatus.Idle
    val canVote = presentation != null && poll.open && poll.id.isNotBlank()
    var picked by remember(poll.id) { mutableStateOf(emptyList<Int>()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("post_poll"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        poll.options.forEachIndexed { index, option ->
            if (poll.showsResults || !canVote) {
                PollResultRow(ownedPost, poll, index)
            } else {
                PollChoiceRow(
                    text = option.text,
                    emoji = ownedPost.post.emoji,
                    multiple = poll.multiple,
                    selected = index in picked,
                    enabled = status != PollVoteStatus.Sending,
                    tag = "post_poll_option_$index",
                ) {
                    picked = when {
                        poll.multiple && index in picked -> picked - index
                        poll.multiple -> picked + index
                        else -> listOf(index)
                    }
                }
            }
        }
        if (canVote && !poll.showsResults) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { presentation?.vote(ownedPost, picked) },
                    enabled = picked.isNotEmpty() && status != PollVoteStatus.Sending,
                    modifier = Modifier.testTag("post_poll_vote"),
                ) { Text(stringResource(if (status == PollVoteStatus.Sending) R.string.post_poll_voting else R.string.post_poll_vote)) }
                if (poll.multiple) {
                    Text(
                        stringResource(R.string.post_poll_pick_multiple),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        (status as? PollVoteStatus.Failed)?.let { failed ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    failed.message.ifBlank { stringResource(R.string.post_poll_vote_failed) },
                    modifier = Modifier.weight(1f).testTag("post_poll_error"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { presentation?.dismissFailure(target) }) { Text(stringResource(R.string.post_translate_dismiss)) }
            }
        }
        Text(
            pollSummary(poll),
            modifier = Modifier.testTag("post_poll_summary"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PollChoiceRow(
    text: String,
    emoji: Map<String, me.foxtails.palustris.domain.CustomEmoji>,
    multiple: Boolean,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val rowModifier = Modifier.fillMaxWidth().testTag(tag)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            if (multiple) {
                rowModifier.toggleable(selected, enabled, Role.Checkbox) { onClick() }
            } else {
                rowModifier.selectable(selected, enabled, Role.RadioButton) { onClick() }
            }.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multiple) {
                Checkbox(checked = selected, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(8.dp))
            } else {
                RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(8.dp))
            }
            InlineEmojiText(text, emoji, Modifier.weight(1f).padding(end = 12.dp), MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One option as a results bar: its share of the votes behind the text, a check on the choices the account made. */
@Composable
private fun PollResultRow(ownedPost: OwnedPost, poll: Poll, index: Int) {
    val option = poll.options[index]
    val total = poll.totalVotes
    val share = if (total > 0) option.votes.toFloat() / total else 0f
    val percent = (share * 100f).let { Math.round(it) }
    val barColor = if (option.picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("post_poll_result_$index")) {
        Row(
            Modifier.fillMaxWidth()
                .drawBehind { drawRect(barColor, size = Size(size.width * share.coerceIn(0f, 1f), size.height)) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (option.picked) {
                Icon(AppIcons.Check, stringResource(R.string.post_poll_your_vote), Modifier.size(18.dp).padding(end = 4.dp))
            }
            InlineEmojiText(option.text, ownedPost.post.emoji, Modifier.weight(1f), MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.post_poll_percent, percent), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun pollSummary(poll: Poll): String {
    val voters = poll.votersCount
    val count = if (voters != null) {
        pluralStringResource(R.plurals.post_poll_voters, voters, voters)
    } else {
        pluralStringResource(R.plurals.post_poll_votes, poll.totalVotes, poll.totalVotes)
    }
    val timing = when {
        poll.expired -> stringResource(R.string.post_poll_ended)
        poll.expiresAtEpochMillis != null -> stringResource(
            R.string.post_poll_ends_in,
            DateUtils.getRelativeTimeSpanString(poll.expiresAtEpochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                .toString().replaceFirstChar { it.lowercase() },
        )
        else -> null
    }
    return if (timing == null) count else stringResource(R.string.post_poll_summary, count, timing)
}
