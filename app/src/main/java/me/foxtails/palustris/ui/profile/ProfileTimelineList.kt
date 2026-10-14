@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.feed.ClientReadyPostActions
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.filters.LocalClientFilters
import me.foxtails.palustris.ui.photogrid.PhotoGridItemRules
import me.foxtails.palustris.ui.posts.PostRow
import me.foxtails.palustris.ui.posts.PostRowEvents
import me.foxtails.palustris.ui.posts.PostRowPresentation
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.components.FilterChipEntry
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.photogrid.photoGridItems
import me.foxtails.palustris.ui.posts.LocalContentWarningRules
import me.foxtails.palustris.ui.posts.LocalHiddenContentPresentation
import me.foxtails.palustris.ui.posts.LocalMutedHashtags

@Composable
internal fun ProfileTimelineList(
    account: Account,
    state: ProfileUiState,
    compactLayout: Boolean,
    endContentClearance: Dp,
    rightObstructionClearance: Dp = 0.dp,
    leftObstructionClearance: Dp = 0.dp,
    isSelf: Boolean,
    onCategorySelected: (ProfileCategory) -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    header: @Composable () -> Unit,
    details: @Composable () -> Unit,
    listState: LazyListState? = null,
    categoryChipListState: LazyListState? = null,
    categoryRowVisible: Boolean = true,
    onToggleCategoryRow: () -> Unit = {},
    showHeader: Boolean = true,
    showInlineCategories: Boolean = !compactLayout,
    largeLayout: Boolean = false,
    mediaLayout: ProfileMediaLayout = ProfileMediaLayout.Grid,
    onMediaLayoutSelected: (ProfileMediaLayout) -> Unit = {},
) {
    // A profile row is a transparent column with separate state and footer items, so only the
    // interactive content clears physical edges. Item dividers keep the full width for underlay.
    val interactionModifier = Modifier.absolutePadding(left = leftObstructionClearance, right = rightObstructionClearance)
    val list = listState ?: rememberLazyListState()
    val currentState by rememberUpdatedState(state)
    val loadMore by rememberUpdatedState(onLoadMore)
    val selectedTab = state.selectedTab.timelineTab
    val page = selectedTab?.let { state.pages[it] }
    val pullToRefreshState = rememberPullToRefreshState()
    val firstPostId = page?.posts?.firstOrNull()?.post?.id
    var previousTab by remember { mutableStateOf(selectedTab) }
    var pendingTabReset by remember { mutableStateOf<ProfileTimelineTab?>(null) }
    // Only the Media tab has a layout choice. Every other tab renders the list.
    val mediaSelected = selectedTab == ProfileTimelineTab.Media
    var listWidthPx by remember { mutableIntStateOf(0) }
    val mediaGrid = if (mediaSelected && mediaLayout == ProfileMediaLayout.Grid) {
        val listWidth = with(LocalDensity.current) { listWidthPx.toDp() }
        rememberProfileMediaGrid(
            posts = page?.posts.orEmpty(),
            columns = profileMediaGridColumns(listWidth - leftObstructionClearance - rightObstructionClearance),
        )
    } else {
        null
    }
    var previousMediaLayout by remember { mutableStateOf(mediaLayout) }

    LaunchedEffect(mediaLayout) {
        // A grid row holds several posts, so a list position has no meaning in the other layout.
        val firstItem = firstTimelineItemIndex(showHeader, showInlineCategories, pinnedRowCount = 0, pinnedError = false, hasFirstPost = true)
        if (previousMediaLayout != mediaLayout && mediaSelected && list.firstVisibleItemIndex > firstItem) {
            list.scrollToItem(firstItem)
        }
        previousMediaLayout = mediaLayout
    }

    LaunchedEffect(selectedTab) {
        if (previousTab != selectedTab) {
            pendingTabReset = selectedTab
            list.scrollToItem(0)
        }
        previousTab = selectedTab
    }

    LaunchedEffect(selectedTab, firstPostId, state.pinnedPosts.size, showHeader, showInlineCategories, pendingTabReset) {
        if (pendingTabReset == selectedTab && firstPostId != null) {
            // Only the Posts tab leads with a pinned row, and only when exactly one is pinned.
            val pinnedInPosts = selectedTab == ProfileTimelineTab.Posts && state.pinnedPosts.size == 1
            val pinnedError = selectedTab == ProfileTimelineTab.Posts && state.pinnedError != null
            list.scrollToItem(
                firstTimelineItemIndex(
                    showHeader = showHeader,
                    showInlineCategories = showInlineCategories,
                    pinnedRowCount = if (pinnedInPosts) 1 else 0,
                    pinnedError = pinnedError,
                    hasFirstPost = true,
                ),
            )
            pendingTabReset = null
        }
    }

    LaunchedEffect(list, state.targetId, selectedTab) {
        snapshotFlow {
            val currentPage = currentState.selectedTab.timelineTab?.let { currentState.pages[it] }
            val nearEnd = (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >=
                (list.layoutInfo.totalItemsCount - 5).coerceAtLeast(0)
            currentPage?.nextCursor != null &&
                currentPage.error == null &&
                !currentPage.initialLoading &&
                !currentPage.refreshing &&
                !currentPage.loadingMore &&
                currentPage.consecutiveEmptyPages < MAX_AUTOMATIC_EMPTY_PAGES &&
                nearEnd
        }.distinctUntilChanged().collect { if (it) loadMore() }
    }

    PullToRefreshBox(
        isRefreshing = page?.refreshing == true,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize().onSizeChanged { listWidthPx = it.width }.testTag("profile_content"),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = page?.refreshing == true,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().testTag("profile_timeline_list"),
            // The Media tab keeps its final rows above the floating layout toggle.
            contentPadding = PaddingValues(
                bottom = endContentClearance + 24.dp +
                    if (mediaSelected) ProfileMediaLayoutToggleHeight + ProfileMediaLayoutToggleGap else 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (showHeader) item(key = "profile-header") { header() }
            if (showInlineCategories) {
                item(key = "profile-categories") {
                    DestinationChipRow(
                        entries = profileCategoryChipEntries(
                            selected = state.selectedTab,
                            isSelf = isSelf,
                            likedAvailable = state.likedAvailable,
                            featuredAvailable = state.pinnedPosts.size > 1,
                            onCategorySelected = onCategorySelected,
                            onOpenDrafts = onOpenDrafts,
                            onOpenBookmarks = onOpenBookmarks,
                            includeShowMore = true,
                            includeEditProfile = false,
                        ),
                        rowContentDescription = stringResource(R.string.a11y_profile_categories),
                        listState = categoryChipListState ?: rememberLazyListState(),
                        visible = categoryRowVisible,
                        onToggleVisibility = onToggleCategoryRow,
                        modifier = interactionModifier,
                        rowTestTag = "profile_categories",
                        visibilityToggleTestTag = "profile_categories_visibility",
                    )
                }
            }
            val featuredSelected = state.selectedTab == ProfileCategory.Featured
            val singlePinnedInPosts = state.selectedTab == ProfileCategory.Posts && state.pinnedPosts.size == 1
            val pinnedErrorInPosts = state.selectedTab == ProfileCategory.Posts && state.pinnedError != null
            when {
                featuredSelected -> profilePinnedItems(
                    state = state,
                    posts = state.pinnedPosts,
                    showTitle = true,
                    showLoading = false,
                    showError = false,
                    quoteEnabled = quoteEnabled,
                    onQuote = onQuote,
                    onRefresh = onRefresh,
                    availableActions = availableActions,
                    onReact = onReact,
                    onReply = onReply,
                    onReshare = onReshare,
                    onBookmark = onBookmark,
                    onReaction = onReaction,
                    onOpenReactionBubble = { ownedPost, bounds -> onOpenReactionBubble?.invoke(ownedPost, bounds) },
                    onOpenReactionPicker = onOpenReactionPicker,
                    onOpenProfile = onOpenProfile,
                    onSearchHashtag = onSearchHashtag,
                    onOpenHashtagBubble = onOpenHashtagBubble,
                    onOpenMedia = onOpenMedia,
                    onOpenPost = onOpenPost,
                    onOpenUrl = onOpenUrl,
                    onOpenUsername = onOpenUsername,
                    largeLayout = largeLayout,
                    interactionModifier = interactionModifier,
                )
                state.selectedTab == ProfileCategory.ShowMore -> item(key = "profile-details") {
                    Box(interactionModifier) { details() }
                }
                else -> {
                    if (singlePinnedInPosts || pinnedErrorInPosts) {
                        profilePinnedItems(
                            state = state,
                            posts = if (singlePinnedInPosts) state.pinnedPosts else emptyList(),
                            showTitle = false,
                            showLoading = false,
                            showError = pinnedErrorInPosts,
                            quoteEnabled = quoteEnabled,
                            onQuote = onQuote,
                            onRefresh = onRefresh,
                            availableActions = availableActions,
                            onReact = onReact,
                            onReply = onReply,
                            onReshare = onReshare,
                            onBookmark = onBookmark,
                            onReaction = onReaction,
                            onOpenReactionBubble = { ownedPost, bounds -> onOpenReactionBubble?.invoke(ownedPost, bounds) },
                            onOpenReactionPicker = onOpenReactionPicker,
                            onOpenProfile = onOpenProfile,
                            onSearchHashtag = onSearchHashtag,
                            onOpenHashtagBubble = onOpenHashtagBubble,
                            onOpenMedia = onOpenMedia,
                            onOpenPost = onOpenPost,
                            onOpenUrl = onOpenUrl,
                            onOpenUsername = onOpenUsername,
                            largeLayout = largeLayout,
                            interactionModifier = interactionModifier,
                        )
                    }
                    profilePageItems(
                        page = page,
                        mediaGrid = mediaGrid,
                        quoteEnabled = quoteEnabled,
                        onQuote = onQuote,
                        onRefresh = onRefresh,
                        onLoadMore = onLoadMore,
                        availableActions = availableActions.intersect(ClientReadyPostActions),
                        onReact = onReact,
                        onReply = onReply,
                        onReshare = onReshare,
                        onBookmark = onBookmark,
                        onReaction = onReaction,
                        onOpenReactionBubble = { ownedPost, bounds -> onOpenReactionBubble?.invoke(ownedPost, bounds) },
                        onOpenReactionPicker = onOpenReactionPicker,
                        onOpenProfile = onOpenProfile,
                        onSearchHashtag = onSearchHashtag,
                        onOpenHashtagBubble = onOpenHashtagBubble,
                        onOpenMedia = onOpenMedia,
                        onOpenPost = onOpenPost,
                        onOpenUrl = onOpenUrl,
                        onOpenUsername = onOpenUsername,
                        largeLayout = largeLayout,
                        interactionModifier = interactionModifier,
                    )
                }
            }
        }
        if (mediaSelected) {
            // The toggle floats over the list, above the category row and clear of the physical edges.
            ProfileMediaLayoutToggle(
                layout = mediaLayout,
                onLayoutSelected = onMediaLayoutSelected,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .then(interactionModifier)
                    .padding(end = 16.dp, bottom = endContentClearance + ProfileMediaLayoutToggleGap),
            )
        }
    }
}

/** Builds the media grid of [posts] under the content warning and mute rules that Photo Grid applies. */
@Composable
private fun rememberProfileMediaGrid(posts: List<OwnedPost>, columns: Int): ProfileMediaGrid {
    val rules = LocalContentWarningRules.current
    val hiddenPresentation = LocalHiddenContentPresentation.current
    val mutedHashtags = LocalMutedHashtags.current
    val clientFilters = LocalClientFilters.current
    return remember(posts, columns, rules, hiddenPresentation, mutedHashtags, clientFilters) {
        profileMediaGrid(photoGridItems(posts, PhotoGridItemRules(rules, hiddenPresentation, mutedHashtags, clientFilters)), columns)
    }
}

@Composable
internal fun profileCategoryChipEntries(
    selected: ProfileCategory,
    isSelf: Boolean,
    likedAvailable: Boolean = false,
    featuredAvailable: Boolean = false,
    onCategorySelected: (ProfileCategory) -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onEditProfile: () -> Unit = {},
    includeShowMore: Boolean = true,
    includeEditProfile: Boolean = false,
): List<FilterChipEntry> = profileChipEntries(
            isSelf = isSelf,
            likedAvailable = likedAvailable,
            featuredAvailable = featuredAvailable,
            includeShowMore = includeShowMore,
            includeEditProfile = includeEditProfile,
        ).map { entry ->
            when (entry) {
                is ProfileChipEntry.Timeline -> FilterChipEntry(
                    label = stringResource(entry.category.labelRes),
                    selected = entry.category == selected,
                    onClick = { onCategorySelected(entry.category) },
                    key = "profile-category:${entry.category.name}",
                )
                ProfileChipEntry.Drafts -> FilterChipEntry(
                    label = stringResource(R.string.profile_action_drafts),
                    onClick = onOpenDrafts,
                    contentDescription = stringResource(R.string.profile_action_drafts_description),
                    role = Role.Button,
                    testTag = "profile_drafts_chip",
                    key = "profile-action:drafts",
                )
                ProfileChipEntry.Bookmarks -> FilterChipEntry(
                    label = stringResource(R.string.profile_action_bookmarks),
                    onClick = onOpenBookmarks,
                    contentDescription = stringResource(R.string.profile_action_bookmarks_description),
                    role = Role.Button,
                    testTag = "profile_bookmarks_chip",
                    key = "profile-action:bookmarks",
                )
                ProfileChipEntry.EditProfile -> FilterChipEntry(
                    label = stringResource(R.string.profile_edit),
                    onClick = onEditProfile,
                    contentDescription = stringResource(R.string.profile_edit_description),
                    role = Role.Button,
                    testTag = "profile_edit_profile_chip",
                    key = "profile-action:edit",
                )
            }
        }

private fun LazyListScope.profilePinnedItems(
    state: ProfileUiState,
    posts: List<OwnedPost>,
    showTitle: Boolean,
    showLoading: Boolean,
    showError: Boolean,
    onRefresh: () -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    largeLayout: Boolean,
    interactionModifier: Modifier,
) {
    if (showLoading && posts.isEmpty()) item(key = "profile-pinned-loading") {
        Box(Modifier.fillMaxWidth().then(interactionModifier).padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
    if (showError && state.pinnedError != null) item(key = "profile-pinned-error") {
        ProfileMessage(
            title = stringResource(R.string.profile_featured_posts_unavailable),
            message = state.pinnedError,
            action = stringResource(R.string.notifications_retry),
            onAction = onRefresh,
            modifier = interactionModifier,
        )
    }
    if (posts.isNotEmpty()) {
        if (showTitle) item(key = "profile-pinned-title") {
            Text(stringResource(R.string.profile_featured_posts), interactionModifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
        items(posts, key = { "pinned/${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
            PostRow(
                ownedPost = ownedPost,
                presentation = PostRowPresentation(availableActions = availableActions, quoteEnabled = quoteEnabled, largeLayout = largeLayout, filterContext = FilterContext.Account),
                events = PostRowEvents(
                    onFavourite = onReact,
                    onReply = onReply,
                    onRepost = onReshare,
                    onBookmark = onBookmark,
                    onReaction = onReaction,
                    onOpenReactionBubble = { target, bounds -> onOpenReactionBubble?.invoke(target, bounds) },
                    onOpenReactionPicker = onOpenReactionPicker,
                    onOpenProfile = onOpenProfile,
                    onSearchHashtag = onSearchHashtag,
                    onOpenHashtagBubble = onOpenHashtagBubble,
                    onOpenMedia = onOpenMedia,
                    onOpenPost = onOpenPost,
                    onOpenUrl = onOpenUrl,
                    onOpenUsername = onOpenUsername,
                    onQuote = onQuote,
                ),
                 modifier = interactionModifier.animateItem(
                    fadeInSpec = LocalPalustrisMotionScheme.current.fastFadeIn,
                    fadeOutSpec = LocalPalustrisMotionScheme.current.fastFadeOut,
                    placementSpec = LocalPalustrisMotionScheme.current.gentleOffset,
                ),
            )
            HorizontalDivider(
                modifier = Modifier.testTag("profile_pinned_divider_${ownedPost.post.id.value}"),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
            )
        }
    }
}

private fun LazyListScope.profilePageItems(
    page: ProfilePageState?,
    mediaGrid: ProfileMediaGrid?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    largeLayout: Boolean,
    interactionModifier: Modifier,
) {
    if (page == null || page.initialLoading && page.posts.isEmpty()) {
        item(key = "profile-timeline-loading") {
            AnimatedStatePane(stateKey = "loading", modifier = Modifier.fillMaxWidth().then(interactionModifier)) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        return
    }
    if (page.error != null && page.posts.isEmpty()) {
        item(key = "profile-timeline-error") {
            AnimatedStatePane(stateKey = "error", modifier = Modifier.fillMaxWidth().then(interactionModifier)) {
                ProfileMessage(
                    title = if (page.needsSignIn) stringResource(R.string.sign_in_required) else stringResource(R.string.profile_posts_unavailable),
                    message = page.error,
                    action = if (page.needsSignIn) stringResource(R.string.action_try_again) else stringResource(R.string.notifications_retry),
                    onAction = onRefresh,
                )
            }
        }
    }
    // The grid can be empty while the page holds posts: a post without an image or video has no tile.
    val nothingToShow = if (mediaGrid != null) mediaGrid.rows.isEmpty() else page.posts.isEmpty()
    if (nothingToShow && page.error == null && !page.refreshing) {
        item(key = "profile-timeline-empty") {
            AnimatedStatePane(stateKey = "empty", modifier = Modifier.fillMaxWidth().then(interactionModifier)) {
                EmptyState(AppIcons.DefaultUser, stringResource(R.string.profile_no_posts_title), stringResource(R.string.profile_no_posts_subtitle))
            }
        }
    }
    // The grid replaces the post rows. The states and footers around them stay the same.
    if (mediaGrid != null) profileMediaGridItems(mediaGrid, onOpenPost, interactionModifier)
    val rowPosts = if (mediaGrid == null) page.posts else emptyList()
    items(rowPosts, key = { "timeline/${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
        PostRow(
            ownedPost = ownedPost,
            presentation = PostRowPresentation(availableActions = availableActions, quoteEnabled = quoteEnabled, largeLayout = largeLayout, filterContext = FilterContext.Account),
            events = PostRowEvents(
                onFavourite = onReact,
                onReply = onReply,
                onRepost = onReshare,
                onBookmark = onBookmark,
                onReaction = onReaction,
                onOpenReactionBubble = { target, bounds -> onOpenReactionBubble?.invoke(target, bounds) },
                onOpenReactionPicker = onOpenReactionPicker,
                onOpenProfile = onOpenProfile,
                onSearchHashtag = onSearchHashtag,
                onOpenHashtagBubble = onOpenHashtagBubble,
                onOpenMedia = onOpenMedia,
                onOpenPost = onOpenPost,
                onOpenUrl = onOpenUrl,
                onOpenUsername = onOpenUsername,
                onQuote = onQuote,
            ),
             modifier = interactionModifier.animateItem(
                fadeInSpec = LocalPalustrisMotionScheme.current.fastFadeIn,
                fadeOutSpec = LocalPalustrisMotionScheme.current.fastFadeOut,
                placementSpec = LocalPalustrisMotionScheme.current.gentleOffset,
            ),
        )
        HorizontalDivider(
            modifier = Modifier.testTag("profile_timeline_divider_${ownedPost.post.id.value}"),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
        )
    }
    if (page.loadingMore) item(key = "profile-timeline-loading-more") {
        Box(Modifier.fillMaxWidth().then(interactionModifier).padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
    if (page.posts.isNotEmpty() && page.error != null && !page.loadingMore) item(key = "profile-timeline-inline-error") {
        ProfileMessage(
            title = stringResource(R.string.profile_timeline_load_error),
            message = page.error,
            action = if (page.needsSignIn) stringResource(R.string.profile_timeline_try_again) else stringResource(R.string.notifications_retry),
            onAction = if (page.needsSignIn) onRefresh else onLoadMore,
            modifier = interactionModifier,
        )
    }
    if (!page.loadingMore && page.error == null) item(key = "profile-timeline-footer") {
        Box(Modifier.fillMaxWidth().then(interactionModifier).padding(16.dp), contentAlignment = Alignment.Center) {
            if (page.nextCursor != null) {
                TextButton(onClick = onLoadMore) {
                    Text(if (page.consecutiveEmptyPages > 0) stringResource(R.string.profile_timeline_continue_browsing) else stringResource(R.string.feed_load_older))
                }
            } else {
                Text(stringResource(R.string.feed_up_to_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileMessage(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAction, Modifier.padding(top = 4.dp)) { Text(action) }
    }
}

private const val MAX_AUTOMATIC_EMPTY_PAGES = 3

private fun firstTimelineItemIndex(
    showHeader: Boolean,
    showInlineCategories: Boolean,
    pinnedRowCount: Int,
    pinnedError: Boolean,
    hasFirstPost: Boolean,
): Int {
    if (!hasFirstPost) return 0
    var index = 0
    if (showHeader) index++
    if (showInlineCategories) index++
    if (pinnedError) index++
    index += pinnedRowCount
    return index
}
