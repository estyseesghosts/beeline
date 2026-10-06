@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.components.ChipCaretPresentation
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.large.CompactWideTabCaretRegistration
import me.foxtails.palustris.ui.layout.compactContextualControlsPositioningInsets
import me.foxtails.palustris.ui.layout.compactScrollEndClearance
import me.foxtails.palustris.ui.layout.CompactFilterDockHeight
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.media.MediaOpenRequest

/**
 * Profile surface for one target account.
 *
 * The shell supplies obstruction clearances for floating navigation. Profile calculates neither value.
 * Expanded layout uses the two-column summary. Compact-wide uses the mobile content column and a wide
 * category dock. Compact layout keeps its measured end clearance and floating chip row.
 */
@Composable
fun ProfileScreen(
    account: Account? = null,
    profileState: ProfileUiState = ProfileUiState(),
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = true,
    authenticatedAccountId: AccountId? = null,
    onProfileShown: (Account) -> Unit = {},
    onCategorySelected: (ProfileCategory) -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onFollow: () -> Unit = {},
    onUnfollow: () -> Unit = {},
    onMessage: (Account) -> Unit = {},
    onEditProfile: (() -> Unit)? = null,
    onOpenDrafts: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenProfile: (Account) -> Unit = {},
    onOpenProfileImage: (String) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    availableActions: Set<PostAction> = emptySet(),
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)? = null,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    onOpenPost: (OwnedPost) -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    largeLayout: Boolean = false,
    compactWidePresentation: Boolean = false,
    useCompactWideCaret: Boolean = false,
    tabCaretHost: CompactWideTabCaretHost? = null,
    largeShowSummary: Boolean = true,
    listState: LazyListState? = null,
    rightObstructionClearance: Dp = 0.dp,
    leftObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp = 0.dp,
) {
    LaunchedEffect(account?.id) {
        account?.let(onProfileShown)
    }

    val displayedAccount = profileState.account
        ?.takeIf { account == null || it.id == account.id }
        ?: profileState.seedAccount?.takeIf { account == null || it.id == account.id }
        ?: account
    // Clear interactive content and the final list item, not the viewport. Compact ignores wide inputs.
    // Physical edges never reverse with the layout direction.
    val wideRightClearance = if (compactLayout) 0.dp else rightObstructionClearance
    val wideLeftClearance = if (compactLayout) 0.dp else leftObstructionClearance
    val wideBottomClearance = if (compactLayout) 0.dp else bottomObstructionClearance
    if (displayedAccount == null) {
        EmptyState(
            icon = AppIcons.DefaultUser,
            title = stringResource(R.string.profile_empty_title),
            subtitle = stringResource(R.string.profile_empty_subtitle),
            modifier = Modifier.absolutePadding(left = wideLeftClearance, right = wideRightClearance).fillMaxSize(),
        )
        return
    }

    val categoryOwnerKey = remember(displayedAccount.id) {
        "${displayedAccount.id.connection.origin}/${displayedAccount.id.localId}"
    }
    var categoryRowVisible by rememberSaveable(categoryOwnerKey) { mutableStateOf(true) }
    val categoryChipListState = rememberLazyListState()
    val isSelf = displayedAccount.id == authenticatedAccountId
    val expandedProfile = largeLayout && !compactWidePresentation
    val endContentClearance = if (expandedProfile || compactWidePresentation) {
        LargeBottomDockClearance + wideBottomClearance
    } else if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = CompactFilterDockHeight,
            navigationVisible = compactNavigationVisible,
            ime = WindowInsets.ime,
        )
    } else {
        wideBottomClearance
    }

    if (expandedProfile) {
        ProfileLargePresentation(
            account = displayedAccount,
            state = profileState,
            isSelf = isSelf,
            showSummary = largeShowSummary,
            onOpenProfileImage = onOpenProfileImage,
            listState = listState,
            categoryChipListState = categoryChipListState,
            categoryRowVisible = categoryRowVisible,
            onToggleCategoryRow = { categoryRowVisible = !categoryRowVisible },
            endContentClearance = endContentClearance,
            rightObstructionClearance = wideRightClearance,
            leftObstructionClearance = wideLeftClearance,
            bottomObstructionClearance = wideBottomClearance,
            onCategorySelected = onCategorySelected,
             onOpenDrafts = onOpenDrafts,
             onOpenBookmarks = onOpenBookmarks,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            onFollow = onFollow,
            onUnfollow = onUnfollow,
            onMessage = { onMessage(displayedAccount) },
            onEditProfile = onEditProfile,
            onOpenProfile = onOpenProfile,
            details = { ProfileDetails(displayedAccount) },
            availableActions = availableActions,
            onReact = onReact,
            onReply = onReply,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenReactionBubble = onOpenReactionBubble,
            onOpenReactionPicker = onOpenReactionPicker,
            onOpenHashtagBubble = onOpenHashtagBubble,
            onOpenMedia = onOpenMedia,
            onOpenPost = onOpenPost,
            onSearchHashtag = onSearchHashtag,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
        )
        return
    }

    val compactCategoryEntries = profileCategoryChipEntries(
        isSelf = isSelf,
        likedAvailable = profileState.likedAvailable,
        featuredAvailable = profileState.pinnedPosts.size > 1,
        includeShowMore = true,
        includeEditProfile = false,
        selected = profileState.selectedTab,
        onCategorySelected = onCategorySelected,
        onOpenDrafts = onOpenDrafts,
        onOpenBookmarks = onOpenBookmarks,
    )
    if (useCompactWideCaret) {
        CompactWideTabCaretRegistration(
            host = tabCaretHost,
            expanded = categoryRowVisible,
            onToggle = { categoryRowVisible = !categoryRowVisible },
        )
    }
    val profileCaretPresentation = if (useCompactWideCaret) ChipCaretPresentation.Hidden
    else ChipCaretPresentation.Inline

    Box(Modifier.fillMaxSize()) {
        ProfileTimelineList(
            account = displayedAccount,
            state = profileState,
            compactLayout = compactLayout,
            endContentClearance = endContentClearance,
            rightObstructionClearance = wideRightClearance,
            leftObstructionClearance = wideLeftClearance,
            isSelf = isSelf,
            onCategorySelected = onCategorySelected,
            onOpenDrafts = onOpenDrafts,
            onOpenBookmarks = onOpenBookmarks,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
                     onOpenProfile = onOpenProfile,
            onSearchHashtag = onSearchHashtag,
            availableActions = availableActions,
            onReact = onReact,
            onReply = onReply,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenReactionBubble = onOpenReactionBubble,
            onOpenReactionPicker = onOpenReactionPicker,
            onOpenHashtagBubble = onOpenHashtagBubble,
            onOpenMedia = onOpenMedia,
            onOpenPost = onOpenPost,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
            header = {
                Box(
                    Modifier.fillMaxWidth().absolutePadding(
                        left = wideLeftClearance,
                        right = wideRightClearance,
                    ),
                ) {
                    ProfileHeader(
                        account = displayedAccount,
                        state = profileState,
                        isSelf = isSelf,
                        onRefresh = onRefresh,
                        onFollow = onFollow,
                        onUnfollow = onUnfollow,
                        onMessage = { onMessage(displayedAccount) },
                        onOpenProfile = onOpenProfile,
                        onOpenProfileImage = onOpenProfileImage,
                    )
                }
            },
            details = { ProfileDetails(displayedAccount) },
            listState = listState,
            showInlineCategories = !compactLayout && !compactWidePresentation,
            categoryChipListState = categoryChipListState,
            categoryRowVisible = categoryRowVisible,
            onToggleCategoryRow = { categoryRowVisible = !categoryRowVisible },
        )

        if (compactLayout) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = CompactOverlayHorizontalPadding)
                    .windowInsetsPadding(
                        compactContextualControlsPositioningInsets(
                            navigationVisible = compactNavigationVisible,
                            ime = WindowInsets.ime,
                        ),
                ),
            ) {
                DestinationChipRow(
                    entries = compactCategoryEntries,
                    rowContentDescription = stringResource(R.string.a11y_profile_categories),
                    listState = categoryChipListState,
                    visible = categoryRowVisible,
                    onToggleVisibility = { categoryRowVisible = !categoryRowVisible },
                    rowTestTag = "profile_categories",
                    visibilityToggleTestTag = "profile_categories_visibility",
                )
            }
        } else if (compactWidePresentation) {
            LargeBottomDock(
                modifier = Modifier.align(Alignment.BottomStart)
                    .absolutePadding(
                        left = wideLeftClearance,
                        right = wideRightClearance,
                        bottom = wideBottomClearance,
                    )
                    .testTag("profile_categories_dock"),
                content = {
                    DestinationChipRow(
                        entries = compactCategoryEntries,
                        rowContentDescription = stringResource(R.string.a11y_profile_categories),
                        listState = categoryChipListState,
                        visible = categoryRowVisible,
                        onToggleVisibility = { categoryRowVisible = !categoryRowVisible },
                        rowTestTag = "profile_categories",
                        visibilityToggleTestTag = "profile_categories_visibility",
                        caretPresentation = profileCaretPresentation,
                    )
                },
            )
        }
    }
}

@Composable
internal fun ProfileRedirectBanner(
    account: Account,
    destination: Account,
    onOpenProfile: () -> Unit,
) {
    val destinationName = destination.displayName.ifBlank { destination.handle }
    val destinationDescription = stringResource(
        R.string.profile_redirect_destination_description,
        destinationName,
        destination.handle,
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("profile_redirect"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.profile_redirect_notice,
                    account.displayName.ifBlank { account.handle },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_redirect_destination")
                    .semantics {
                        contentDescription = destinationDescription
                        role = Role.Button
                    }
                    .clickable(role = Role.Button, onClick = onOpenProfile),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccountAvatar(destination, Modifier.size(56.dp), exposeSemantics = false)
                    Column(Modifier.weight(1f)) {
                        InlineEmojiText(
                            text = destinationName,
                            emoji = destination.emoji,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            destination.handle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth().testTag("profile_redirect_go_to_profile"),
            ) {
                Text(stringResource(R.string.profile_redirect_go_to_profile))
            }
        }
    }
}

@Composable
internal fun ProfileStats(account: Account) {
    val stats = listOfNotNull(
        account.postsCount?.let { stringResource(R.string.profile_count_posts, formatProfileCount(it)) },
        account.followersCount?.let { stringResource(R.string.profile_count_followers, formatProfileCount(it)) },
        account.followingCount?.let { stringResource(R.string.profile_count_following, formatProfileCount(it)) },
    )
    if (stats.isNotEmpty()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).testTag("profile_stats"),
        ) {
            if (maxWidth < 480.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stats.forEach { stat ->
                        Text(stat, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    stats.forEach { stat ->
                        Text(stat, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun LinearProfileProgress(description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("profile_detail_loading")) {
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
internal fun ProfileStatus(
    message: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

internal fun formatProfileCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

internal fun Account.hasUsableProfileIdentity(): Boolean = id.localId.isNotBlank() &&
    (displayName.isNotBlank() || handle.removePrefix("@").substringBefore("@").isNotBlank() || avatarUrl != null)

private fun String.isWebAddress(): Boolean =
    startsWith("https://") || startsWith("http://")
