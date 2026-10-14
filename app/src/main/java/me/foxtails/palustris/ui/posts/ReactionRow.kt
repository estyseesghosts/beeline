@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.foxtails.palustris.ui.posts

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.ui.emoji.CustomEmojiImage
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.springPress

private val CircleShapeForReaction = RoundedCornerShape(50)
private val ReactionChipHeight = 32.dp
private val ReactionEmojiSlotSize = 20.dp
private val ReactionChipMinWidth = 56.dp

/** A selected control that the server has not confirmed yet is dimmed so it does not look settled. */
private const val REACTION_PENDING_ALPHA = 0.6f

/**
 * One chip per reaction with its count, highlighted when the account selected it.
 *
 * Tap toggles the reaction. Long-press loads the account list through [reactorsLoader]
 * when provided and shows it in a sheet; otherwise [onLongPressReaction] observes the
 * press. The add chip opens the picker through [onAddReaction] when provided. Transport
 * stays in the injected loader and callbacks; this row owns only the sheet UI state,
 * released with the composition.
 */
@Composable
internal fun ReactionRow(
    reactions: List<Reaction>,
    ownedPost: OwnedPost,
    enabled: Boolean,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    showReactionNumbers: Boolean = false,
    onLongPressReaction: ((OwnedPost, Reaction) -> Unit)? = null,
    reactorsLoader: (suspend (OwnedPost, Reaction) -> List<Account>)? = null,
    onAddReaction: ((OwnedPost) -> Unit)? = null,
) {
    val scheme = LocalPalustrisMotionScheme.current
    val reactionPending = LocalPostPendingLookup.current.isPending(ownedPost, PostControlFamilies.Reaction)
    val pendingDescription = stringResource(R.string.post_action_pending)
    var reactorsTarget by remember(ownedPost) { mutableStateOf<Reaction?>(null) }
    var reactorsAccounts by remember(ownedPost) { mutableStateOf<List<Account>?>(null) }
    val scope = rememberCoroutineScope()
    val longPress: ((Reaction) -> Unit)? = when {
        reactorsLoader != null -> { reaction ->
            reactorsTarget = reaction
            reactorsAccounts = null
            scope.launch {
                reactorsAccounts = try {
                    reactorsLoader(ownedPost, reaction)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        onLongPressReaction != null -> { reaction -> onLongPressReaction(ownedPost, reaction) }
        else -> null
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.filter { it.count > 0 }.forEach { reaction ->
            val interactionSource = remember(reaction.emoji) { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val countText = pluralStringResource(R.plurals.reaction_count, reaction.count, reaction.count)
            val reactionDescription = stringResource(R.string.post_reaction_accessibility, reaction.emoji, countText)
            val chipPending = reactionPending && reaction.selected
            val selectedStateDescription = if (chipPending) {
                pendingDescription
            } else if (reaction.selected) {
                if (enabled) stringResource(R.string.emoji_reaction_remove, reaction.emoji)
                else stringResource(R.string.emoji_reaction_selected, reaction.emoji)
            } else null
            val chipColor = rememberSelectedColor(
                selected = reaction.selected,
                selectedColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Surface(
                modifier = Modifier
                    .heightIn(min = ReactionChipHeight)
                    .widthIn(min = ReactionChipMinWidth)
                    .alpha(if (chipPending) REACTION_PENDING_ALPHA else 1f)
                    .springPress(interactionSource, pressedScale = scheme.compactPressedScale)
                    .combinedClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = { onReaction(ownedPost, EmojiChoice(reaction.emoji, reaction.emoji, reaction.emojiMetadata)) },
                        onLongClick = longPress?.let { action -> { action(reaction) } },
                    )
                    .testTag("reaction_chip_${reaction.emoji}")
                    .semantics {
                        contentDescription = reactionDescription
                        role = Role.Button
                        this.selected = reaction.selected
                        selectedStateDescription?.let { stateDescription = it }
                    },
                shape = CircleShapeForReaction,
                color = chipColor,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(Modifier.fillMaxHeight().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(ReactionEmojiSlotSize).testTag("reaction_emoji_slot_${reaction.emoji}"), contentAlignment = Alignment.Center) {
                        CustomEmojiImage(
                            emoji = reaction.emojiMetadata,
                            fallbackText = reaction.emoji,
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                        )
                    }
                    if (showReactionNumbers && reaction.count > 1) {
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.widthIn(min = 16.dp).testTag("reaction_count_${reaction.emoji}"), contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = reaction.count,
                                transitionSpec = { countTransform(scheme) },
                                label = "reactionCount",
                            ) { count -> Text(count.toString(), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
        if (onAddReaction != null && enabled) {
            val addDescription = stringResource(R.string.emoji_add_reaction)
            val addSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Surface(
                modifier = Modifier
                    .heightIn(min = ReactionChipHeight)
                    .widthIn(min = ReactionChipMinWidth)
                    .springPress(addSource, pressedScale = scheme.compactPressedScale)
                    .combinedClickable(
                        interactionSource = addSource,
                        indication = LocalIndication.current,
                        onClick = { onAddReaction(ownedPost) },
                    )
                    .testTag("reaction_add")
                    .semantics {
                        contentDescription = addDescription
                        role = Role.Button
                    },
                shape = CircleShapeForReaction,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(Modifier.fillMaxHeight().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.reaction_add_symbol), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    reactorsTarget?.let { target ->
        ReactionReactorsSheet(
            reaction = target,
            accounts = reactorsAccounts,
            onDismiss = {
                reactorsTarget = null
                reactorsAccounts = null
            },
        )
    }
}

/**
 * The accounts behind one reaction, loaded through the row's [reactorsLoader]. A null
 * [accounts] list is still loading; an empty list loaded but found nobody.
 */
@Composable
private fun ReactionReactorsSheet(
    reaction: Reaction,
    accounts: List<Account>?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("reaction_reactors_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.reaction_reactors_title, reaction.emoji),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 4.dp).testTag("reaction_reactors_title"),
            )
            if (accounts == null) {
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp).testTag("reaction_reactors_loading"), strokeWidth = 2.dp)
                }
            } else {
                accounts.forEachIndexed { index, account ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("reaction_reactor_$index")) {
                        Text(
                            account.displayName.ifBlank { account.handle },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            account.handle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(Modifier.size(24.dp))
        }
    }
}
