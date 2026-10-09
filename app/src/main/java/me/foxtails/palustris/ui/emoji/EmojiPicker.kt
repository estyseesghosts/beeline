@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package me.foxtails.palustris.ui.emoji

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EmojiPickerPreferences
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.PillAction

enum class ComposerField { Text, Warning }

sealed interface EmojiPickerTarget {
    data class Reaction(val post: OwnedPost) : EmojiPickerTarget
    data class Composer(val field: ComposerField) : EmojiPickerTarget
}

private data class PendingEmojiPin(
    val choice: EmojiChoice,
    val bounds: Rect,
    val pinned: Boolean,
)

@Composable
private fun EmojiPickerGroup.labelText(): String = when (val label = label) {
    EmojiPickerGroupLabel.Favorite -> stringResource(R.string.emoji_group_favorite)
    EmojiPickerGroupLabel.Recent -> stringResource(R.string.emoji_group_recent)
    EmojiPickerGroupLabel.PostSpecific -> stringResource(R.string.emoji_group_post_specific)
    EmojiPickerGroupLabel.Standard -> stringResource(R.string.emoji_group_standard)
    EmojiPickerGroupLabel.Custom -> stringResource(R.string.emoji_group_custom)
    is EmojiPickerGroupLabel.ServerCategory -> label.value
}

@Composable
private fun EmojiGroupHeader(
    group: EmojiPickerGroup,
    onToggleCollapsed: (String) -> Unit,
    onTogglePinned: (String) -> Unit,
) {
    val label = group.labelText()
    val collapseDescription = stringResource(
        if (group.collapsed) R.string.emoji_expand_group else R.string.emoji_collapse_group,
        label,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group.pinnable) {
            val pinDescription = when {
                group.pinned -> stringResource(R.string.emoji_unpin_group, label)
                group.pinEnabled -> stringResource(R.string.emoji_pin_group, label)
                else -> stringResource(R.string.emoji_pin_limit_group, label)
            }
            IconButton(
                onClick = { onTogglePinned(group.id) },
                enabled = group.pinEnabled,
                modifier = Modifier.testTag("emoji_picker_pin_${group.id}"),
            ) {
                Icon(AppIcons.Pin, contentDescription = pinDescription)
            }
        }
        IconButton(
            onClick = { onToggleCollapsed(group.id) },
            modifier = Modifier.testTag("emoji_picker_collapse_${group.id}"),
        ) {
            Icon(
                if (group.collapsed) AppIcons.CaretUp else AppIcons.CaretDown,
                contentDescription = collapseDescription,
            )
        }
    }
}

/**
 * Reusable picker body and modal-sheet wrapper for reaction selection and composer
 * insertion. The grid container stays transparent; only the Material sheet and
 * individual selected/pressed cells carry a surface. The picker never alters page
 * viewport padding. A null target renders nothing.
 */
@Composable
fun EmojiPickerHost(
    target: EmojiPickerTarget?,
    catalog: EmojiCatalogState,
    selectionMode: ReactionSelectionMode,
    mutationSupported: Boolean,
    onLoadCatalog: () -> Unit,
    onRetryCatalog: () -> Unit,
    onDismiss: () -> Unit,
    onEmojiSelected: (EmojiChoice) -> Unit,
    onToggleGroupCollapsed: (String) -> Unit = {},
    onToggleGroupPinned: (String) -> Unit = {},
    onTogglePinnedEmoji: (String) -> Unit = {},
    onCloseRequest: () -> Unit = {},
 ) {
    if (target == null) return
    LaunchedEffect(target) { onLoadCatalog() }
    val readOnlyReactions = target is EmojiPickerTarget.Reaction && !mutationSupported
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("emoji_picker_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(
                        if (target is EmojiPickerTarget.Reaction) R.string.emoji_add_reaction
                        else R.string.emoji_picker_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                IconButton(
                    onClick = onRetryCatalog,
                    enabled = !catalog.initialLoading && !catalog.refreshing,
                    modifier = Modifier.testTag("emoji_picker_refresh"),
                ) {
                    Text(stringResource(R.string.common_refresh), style = MaterialTheme.typography.labelLarge)
                }
            }
            when {
                readOnlyReactions -> ReadOnlyReactions(target as EmojiPickerTarget.Reaction)
                else -> Column(Modifier.fillMaxWidth()) {
                    if (catalog.initialLoading || catalog.refreshing) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(
                                    if (catalog.initialLoading) R.string.emoji_picker_loading
                                    else R.string.emoji_picker_refreshing,
                                ),
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (catalog.unsupported) {
                        PickerMessage(stringResource(R.string.emoji_picker_unsupported))
                    }
                    catalog.error?.let { error ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            TextButton(onClick = onRetryCatalog) { Text(stringResource(R.string.emoji_picker_retry)) }
                        }
                    }
                    EmojiChoiceGrid(
                        catalogItems = catalog.items,
                        preferences = catalog.preferences,
                        additionalChoices = (target as? EmojiPickerTarget.Reaction)?.post?.post?.reactions?.map { reaction ->
                            EmojiChoice(reaction.emoji, reaction.emoji, reaction.emojiMetadata)
                        }.orEmpty(),
                        selectedIdentities = (target as? EmojiPickerTarget.Reaction)?.post?.post?.let { post ->
                            buildSet {
                                post.selectedReactions.forEach { add(it.submissionValue) }
                                post.reactions.filter { it.selected }.forEach { add(it.emoji) }
                                post.myReaction?.let(::add)
                            }
                        }.orEmpty(),
                        pins = catalog.pinState(),
                        actions = EmojiPreferenceActions(onToggleGroupCollapsed, onToggleGroupPinned, onTogglePinnedEmoji),
                         onCloseRequest = onDismiss,
                        onEmojiSelected = { choice ->
                            onEmojiSelected(choice)
                            onDismiss()
                        },
                    )
                }
            }
            Box(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PickerMessage(message: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReadOnlyReactions(target: EmojiPickerTarget.Reaction) {
    val post = target.post.post
    LazyVerticalGrid(
        columns = GridCells.Adaptive(48.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(post.reactions, key = { it.emoji }) { reaction ->
            val reactionDescription = stringResource(R.string.post_reaction_accessibility, reaction.emoji, reaction.count)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = reactionDescription
                        this.selected = reaction.selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomEmojiImage(
                        emoji = reaction.emojiMetadata,
                        fallbackText = reaction.emoji,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(reaction.count.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun EmojiChoiceGrid(
    catalogItems: List<CustomEmoji>,
    additionalChoices: List<EmojiChoice> = emptyList(),
    selectedIdentities: Set<String> = emptySet(),
    preferences: EmojiPickerPreferences = EmojiPickerPreferences(),
    compact: Boolean = false,
    pins: EmojiPinState = EmojiPinState(),
    recents: EmojiRecents = rememberEmojiRecents(),
    modifier: Modifier = Modifier,
    testTag: String = "emoji_picker_grid",
    actions: EmojiPreferenceActions = EmojiPreferenceActions(),
    onCloseRequest: () -> Unit = {},
    onEmojiSelected: (EmojiChoice) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val gridState = rememberLazyGridState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val recentIdentities = recents.identities
    // The confirmation belongs to one account scope; a new scope starts without it.
    var pendingPin by remember(pins.scopeKey) { mutableStateOf<PendingEmojiPin?>(null) }
    val useChoice: (EmojiChoice) -> Unit = { choice ->
        recents.use(choice.submissionValue)
        onEmojiSelected(choice)
    }
    val requestPin: (EmojiChoice, Rect) -> Unit = { choice, bounds ->
        pendingPin = PendingEmojiPin(choice, bounds, choice.submissionValue in preferences.pinnedEmoji)
    }
    val compactChoices = remember(catalogItems, additionalChoices, recentIdentities, selectedIdentities, preferences) {
        buildCompactEmojiChoices(catalogItems, additionalChoices, recentIdentities, selectedIdentities, preferences)
    }
    if (compact) {
        Column(modifier.fillMaxWidth()) {
            if (pins.failed) PinFailureMessage()
            Box(Modifier.fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(48.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).testTag(testTag),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(compactChoices, key = { "compact-${it.submissionValue}" }) { choice ->
                        EmojiPickerTile(
                            choice = choice,
                            selected = choice.submissionValue in selectedIdentities,
                            pinned = choice.submissionValue in preferences.pinnedEmoji,
                            pending = choice.submissionValue in pins.pendingPins,
                            onClick = { useChoice(choice) },
                            onLongClick = { bounds -> requestPin(choice, bounds) },
                        )
                    }
                }
                EmojiPinConfirmationPopup(
                    pending = pendingPin,
                    onConfirm = { pending ->
                        actions.togglePinnedEmoji(pending.choice.submissionValue)
                        pendingPin = null
                    },
                    onDismiss = { pendingPin = null },
                )
            }
        }
        return
    }
    val groups = remember(catalogItems, additionalChoices, recentIdentities, selectedIdentities, query, preferences) {
        buildEmojiPickerGroups(
            catalogItems = catalogItems,
            additionalChoices = additionalChoices,
            recentIdentities = recentIdentities,
            selectedIdentities = selectedIdentities,
            searchQuery = query,
            preferences = preferences,
        )
    }
    val layoutInfo by remember { derivedStateOf { gridState.layoutInfo } }
    val density = LocalDensity.current
    val viewportHeightPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    val scrollbarThumb = remember(layoutInfo) {
        calculateEmojiGridScrollbarThumb(
            firstVisibleItemIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0,
            visibleItemCount = layoutInfo.visibleItemsInfo.size,
            totalItemCount = layoutInfo.totalItemsCount,
            viewportHeightPx = viewportHeightPx,
            minimumThumbHeightPx = with(density) { 48.dp.toPx() },
        )
    }
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = if (gridState.isScrollInProgress) 0.85f else 0.45f,
    )
    Column(Modifier.fillMaxWidth()) {
        if (pins.failed) PinFailureMessage()
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("emoji_picker_search"),
            placeholder = { Text(stringResource(R.string.emoji_picker_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
            }),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (query.isNotEmpty()) {
                            query = ""
                        } else {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onCloseRequest()
                        }
                    },
                    modifier = Modifier.testTag("emoji_picker_hide_keyboard"),
                ) {
                    Icon(AppIcons.Close, stringResource(R.string.emoji_picker_hide_keyboard))
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Box(modifier.fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(48.dp),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    item(key = "header-${group.id}", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        EmojiGroupHeader(
                            group = group,
                            onToggleCollapsed = actions.toggleGroupCollapsed,
                            onTogglePinned = actions.toggleGroupPinned,
                        )
                    }
                    group.choices.forEach { choice ->
                        item(key = "${group.id}-${choice.submissionValue}") {
                            EmojiPickerTile(
                                choice = choice,
                                selected = choice.submissionValue in selectedIdentities,
                                pinned = choice.submissionValue in preferences.pinnedEmoji,
                                pending = choice.submissionValue in pins.pendingPins,
                                onClick = { useChoice(choice) },
                                onLongClick = { bounds -> requestPin(choice, bounds) },
                            )
                        }
                    }
                }
            }
            EmojiPinConfirmationPopup(
                pending = pendingPin,
                onConfirm = { pending ->
                    actions.togglePinnedEmoji(pending.choice.submissionValue)
                    pendingPin = null
                },
                onDismiss = { pendingPin = null },
            )
            scrollbarThumb?.let { thumb ->
                Canvas(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(4.dp)
                        .testTag("emoji_picker_scrollbar"),
                ) {
                    val thumbHeight = thumb.heightPx.coerceAtMost(size.height)
                    val top = thumb.topPx
                        .coerceIn(0f, (size.height - thumbHeight).coerceAtLeast(0f))
                    drawRoundRect(
                        color = scrollbarColor,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(size.width, thumbHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PinFailureMessage() {
    Text(
        stringResource(R.string.emoji_pin_failed),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("emoji_pin_failure"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun EmojiPinConfirmationPopup(
    pending: PendingEmojiPin?,
    onConfirm: (PendingEmojiPin) -> Unit,
    onDismiss: () -> Unit,
) {
    if (pending == null) return
    val question = stringResource(
        if (pending.pinned) R.string.emoji_remove_question else R.string.emoji_pin_question,
    )
    Popup(
        popupPositionProvider = me.foxtails.palustris.ui.posts.WindowAnchorPositionProvider(
            pending.bounds,
            me.foxtails.palustris.ui.posts.BubblePlacement.Above,
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        PillAction(
            label = question,
            onClick = { onConfirm(pending) },
            modifier = Modifier
                .widthIn(min = 176.dp, max = 280.dp)
                .testTag("emoji_pin_confirmation")
                .semantics {
                    contentDescription = question
                },
            fillContent = true,
        )
    }
}
