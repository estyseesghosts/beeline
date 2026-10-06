@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.foxtails.palustris.ui.feed

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ContentWarningPolicy
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.layout.LegacyFeedBottomClearance
import me.foxtails.palustris.ui.layout.compactHomeScrollEndClearance
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.PalustrisMotionScheme
import me.foxtails.palustris.ui.posts.LocalContentWarningRules
import me.foxtails.palustris.ui.posts.LocalMutedHashtags
import me.foxtails.palustris.ui.posts.PostActionBubbleHost
import me.foxtails.palustris.ui.posts.PostActionBubbleTarget
import me.foxtails.palustris.ui.posts.PostRow
import me.foxtails.palustris.ui.posts.PostRowEvents
import me.foxtails.palustris.ui.posts.PostRowPresentation
import me.foxtails.palustris.ui.posts.postHashtags
import me.foxtails.palustris.ui.shell.HomeFeedUiState

/**
 * Keeps Home's viewport full size. Wide obstruction inputs clear list interaction
 * content and the floating dock; bottom clearance also extends the list scroll range.
 * Compact Home ignores these inputs and retains its IME-aware end spacing.
 */
@Composable
fun HomeFeed(
    state: HomeFeedUiState,
    compactLayout: Boolean = true,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSignIn: () -> Unit,
    availableActions: Set<PostAction> = emptySet(),
    quoteEnabled: Boolean = false,
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, me.foxtails.palustris.domain.EmojiChoice) -> Unit = { _, _ -> },
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)? = null,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onQuote: (OwnedPost) -> Unit = {},
    onOpenProfile: (Account) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    onOpenPost: ((OwnedPost) -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    listState: LazyListState? = null,
    topContentPadding: Dp? = null,
    bottomContentClearance: Dp? = null,
    refreshIndicatorTopPadding: Dp? = null,
    bottomDock: (@Composable () -> Unit)? = null,
    cleanTrackingParameters: Boolean = false,
    contentWarningRules: ContentWarningRules = LocalContentWarningRules.current,
    rightObstructionClearance: Dp = 0.dp,
    leftObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp = 0.dp,
) {
    // Clear interaction content, not the viewport or full-width row underlay.
    // Physical left and physical right do not reverse with the layout direction.
    val wideRightClearance = if (compactLayout) 0.dp else rightObstructionClearance
    val wideLeftClearance = if (compactLayout) 0.dp else leftObstructionClearance
    val wideBottomClearance = if (compactLayout) 0.dp else bottomObstructionClearance
    val interactionModifier = Modifier.absolutePadding(left = wideLeftClearance, right = wideRightClearance)
    val scrollEndClearance = (bottomContentClearance
        ?: if (compactLayout) compactHomeScrollEndClearance() else LegacyFeedBottomClearance) + wideBottomClearance
    val list = listState ?: rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    var fallbackBubbleTarget by remember { mutableStateOf<PostActionBubbleTarget?>(null) }
    val openHashtagBubble: (OwnedPost, List<String>, Rect) -> Unit = { ownedPost, hashtags, bounds ->
        if (onOpenHashtagBubble != null) onOpenHashtagBubble(ownedPost, hashtags, bounds)
        else fallbackBubbleTarget = PostActionBubbleTarget.HashtagList(ownedPost.post.id, hashtags, bounds)
    }
    val openReactionBubble: (OwnedPost, Rect) -> Unit = { ownedPost, bounds ->
        onOpenReactionBubble?.invoke(ownedPost, bounds)
        if (onOpenReactionBubble == null) onOpenReactionPicker(ownedPost)
    }
    val currentState by rememberUpdatedState(state)
    val loadMore by rememberUpdatedState(onLoadMore)
    val scrollDirectionChanged by rememberUpdatedState(onScrollDirectionChanged)
    val scheme = LocalPalustrisMotionScheme.current
    val mutedHashtags = LocalMutedHashtags.current
    // The Home contract owns the rows. state.ownedPosts is authoritative.
    val ownedPosts = state.ownedPosts
    val hasOwnership = ownedPosts.isNotEmpty()
    val rows = if (hasOwnership) ownedPosts else state.posts.map { OwnedPost(it.author.id, it) }
    val visibleRows = rows.filterNot { ownedPost -> ContentWarningPolicy.matchesHashtagMute(postHashtags(ownedPost.post.text, ownedPost.post.emoji), mutedHashtags) }
    val currentVisibleRows by rememberUpdatedState(visibleRows)
    val pagingDemand = remember { HomePagingDemand() }
    HomePagingEffects(
        state = currentState,
        visibleRows = currentVisibleRows,
        mutedHashtags = mutedHashtags,
        list = list,
        pagingDemand = pagingDemand,
        onLoadMore = loadMore,
    )
    HomeScrollDirectionEffect(
        list = list,
        onScrollDirectionChanged = scrollDirectionChanged,
    )
    val statePaneKey = when {
        state.error != null -> "error"
        state.posts.isEmpty() && state.loading -> "loading"
        state.posts.isEmpty() -> "empty"
        else -> "feed"
    }
    AnimatedStatePane(statePaneKey, Modifier.fillMaxSize()) {
        HomeFeedContent(
            state = state,
            compactLayout = compactLayout,
            onRefresh = onRefresh,
            onSignIn = onSignIn,
            list = list,
            pullToRefreshState = pullToRefreshState,
            topContentPadding = topContentPadding,
            scrollEndClearance = scrollEndClearance,
            refreshIndicatorTopPadding = refreshIndicatorTopPadding,
            interactionModifier = interactionModifier,
            visibleRows = visibleRows,
            hasOwnership = hasOwnership,
            availableActions = availableActions,
            quoteEnabled = quoteEnabled,
            contentWarningRules = contentWarningRules,
            scheme = scheme,
            onReact = onReact,
            onReply = onReply,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenReactionBubble = openReactionBubble,
            onOpenReactionPicker = onOpenReactionPicker,
            onQuote = onQuote,
            onOpenProfile = onOpenProfile,
            onSearchHashtag = onSearchHashtag,
            onOpenHashtagBubble = openHashtagBubble,
            onOpenMedia = onOpenMedia,
            onOpenPost = onOpenPost,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
            pagingDemand = pagingDemand,
            onLoadMore = onLoadMore,
            bottomDock = bottomDock,
            wideLeftClearance = wideLeftClearance,
            wideRightClearance = wideRightClearance,
            wideBottomClearance = wideBottomClearance,
        )
    }
    if (onOpenHashtagBubble == null) PostActionBubbleHost(target = fallbackBubbleTarget, emojiCatalog = EmojiCatalogState(), emojiCapabilities = me.foxtails.palustris.domain.EmojiCapabilities(), onDismiss = { fallbackBubbleTarget = null }, onHashtagSelected = onSearchHashtag, onReactionSelected = { _, _ -> }, hashtagBottomClearance = if (compactLayout) compactHomeScrollEndClearance() else 0.dp)
}

/**
 * Observes Home paging state and requests the next page when the visible rows approach
 * the end of the list. Owns no persistent state; the [HomePagingDemand] is created and
 * owned by [HomeFeed].
 */
@Composable
private fun HomePagingEffects(
    state: HomeFeedUiState,
    visibleRows: List<OwnedPost>,
    mutedHashtags: Set<String>,
    list: LazyListState,
    pagingDemand: HomePagingDemand,
    onLoadMore: () -> Unit,
) {
    val currentState by rememberUpdatedState(state)
    val currentVisibleRows by rememberUpdatedState(visibleRows)
    val loadMore by rememberUpdatedState(onLoadMore)
    // New visible rows, a changed filter, or a new request epoch reset the no-progress budget.
    // The row count alone never identifies the filter, so the identity travels with the count.
    LaunchedEffect(visibleRows.size, mutedHashtags, state.requestEpoch) {
        pagingDemand.onVisiblePostsChanged(visibleRows.size, mutedHashtags, state.requestEpoch)
    }
    // A refresh, timeline change, epoch change, or filter change starts a new demand budget and
    // reevaluates demand even when the visible rows are unchanged.
    LaunchedEffect(state.loading, state.selectedTimeline, state.requestEpoch, mutedHashtags) {
        pagingDemand.reset()
    }
    LaunchedEffect(list) {
        snapshotFlow {
            val s = currentState
            val postCount = currentVisibleRows.size
            HomePagingInput(
                nextCursor = s.nextCursor,
                loading = s.loading,
                loadingMore = s.loadingMore,
                errorPresent = s.error != null,
                needsSignIn = s.needsSignIn,
                visiblePostCount = postCount,
                lastVisiblePostIndex = lastVisiblePostIndex(
                    visibleItemIndices = list.layoutInfo.visibleItemsInfo.map { it.index },
                    leadingItemCount = s.leadingItemCount(),
                    postCount = postCount,
                ),
                requestEpoch = s.requestEpoch,
                filterIdentity = mutedHashtags,
                demandGeneration = pagingDemand.demandGeneration,
            )
        }.distinctUntilChanged().collect { input ->
            if (pagingDemand.shouldRequestNextPage(input)) {
                // The input excludes the rejection cases the feed owner checks (cursor,
                // loading, error, sign-in), and the shell timeline only diverges from the
                // feed timeline while the feed loads. The owner reserves the page slot
                // synchronously, so this request counts as accepted. A refresh that lands
                // between evaluation and the call can still reject it. That costs one
                // bounded budget unit and the next reset recovers it.
                loadMore()
                pagingDemand.onPageAccepted()
            }
        }
    }
}

/**
 * Observes scroll direction and reports whether the user is scrolling up.
 * Owns no persistent state.
 */
@Composable
private fun HomeScrollDirectionEffect(
    list: LazyListState,
    onScrollDirectionChanged: (Boolean) -> Unit,
) {
    val scrollDirectionChanged by rememberUpdatedState(onScrollDirectionChanged)
    LaunchedEffect(list) {
        var previousIndex = list.firstVisibleItemIndex
        var previousOffset = list.firstVisibleItemScrollOffset
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.distinctUntilChanged().collect { (index, offset) ->
            when {
                index > previousIndex || (index == previousIndex && offset > previousOffset) -> scrollDirectionChanged(false)
                index < previousIndex || (index == previousIndex && offset < previousOffset) -> scrollDirectionChanged(true)
            }
            previousIndex = index
            previousOffset = offset
        }
    }
}

/**
 * Renders the Home feed: pull-to-refresh, the lazy list with error, empty, filtered-empty,
 * post, and footer states, and the wide bottom dock.
 */
@Composable
private fun HomeFeedContent(
    state: HomeFeedUiState,
    compactLayout: Boolean,
    onRefresh: () -> Unit,
    onSignIn: () -> Unit,
    list: LazyListState,
    pullToRefreshState: PullToRefreshState,
    topContentPadding: Dp?,
    scrollEndClearance: Dp,
    refreshIndicatorTopPadding: Dp?,
    interactionModifier: Modifier,
    visibleRows: List<OwnedPost>,
    hasOwnership: Boolean,
    availableActions: Set<PostAction>,
    quoteEnabled: Boolean,
    contentWarningRules: ContentWarningRules,
    scheme: PalustrisMotionScheme,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, me.foxtails.palustris.domain.EmojiChoice) -> Unit,
    onOpenReactionBubble: (OwnedPost, Rect) -> Unit,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onQuote: (OwnedPost) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: (OwnedPost, List<String>, Rect) -> Unit,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: ((OwnedPost) -> Unit)?,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    pagingDemand: HomePagingDemand,
    onLoadMore: () -> Unit,
    bottomDock: (@Composable () -> Unit)?,
    wideLeftClearance: Dp,
    wideRightClearance: Dp,
    wideBottomClearance: Dp,
) {
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize().testTag("home_feed_content"),
            indicator = { PullToRefreshDefaults.Indicator(state = pullToRefreshState, isRefreshing = state.loading, modifier = Modifier.align(Alignment.TopCenter).padding(top = refreshIndicatorTopPadding ?: 96.dp)) },
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().testTag("home_feed_list"),
                contentPadding = PaddingValues(top = topContentPadding ?: 96.dp, bottom = scrollEndClearance),
            ) {
                if (state.error != null) item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("home_feed_error"), shape = MaterialTheme.shapes.large) {
                        Column(interactionModifier.padding(16.dp)) {
                            Text(state.error, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = if (state.needsSignIn) onSignIn else onRefresh) { Text(if (state.needsSignIn) stringResource(R.string.feed_sign_in_again) else stringResource(R.string.notifications_retry)) }
                        }
                    }
                }
                if (state.posts.isEmpty() && !state.loading && state.error == null) item {
                    Box(Modifier.fillParentMaxSize()) {
                        Box(interactionModifier.fillMaxSize()) {
                            EmptyState(AppIcons.HoneyHome, stringResource(R.string.feed_empty_title), stringResource(R.string.feed_empty_subtitle))
                        }
                    }
                }
                val enabledActions = if (hasOwnership) availableActions.intersect(ClientReadyPostActions) else emptySet()
                items(visibleRows, key = { "${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
                    Column(Modifier.animateItem(fadeInSpec = scheme.fastFadeIn, fadeOutSpec = scheme.fastFadeOut, placementSpec = scheme.gentleOffset).testTag("home_post_underlay_${ownedPost.post.id.value}")) {
                        PostRow(
                            ownedPost = ownedPost,
                            presentation = PostRowPresentation(
                                availableActions = enabledActions,
                                favouriteArtworkStyle = state.favouriteArtworkStyle,
                                quoteEnabled = quoteEnabled,
                                largeLayout = !compactLayout,
                                contentWarningRules = contentWarningRules,
                            ),
                            events = PostRowEvents(
                                onFavourite = onReact,
                                onReply = onReply,
                                onRepost = onReshare,
                                onBookmark = onBookmark,
                                onReaction = onReaction,
                                onOpenProfile = onOpenProfile,
                                onSearchHashtag = onSearchHashtag,
                                onOpenHashtagBubble = onOpenHashtagBubble,
                                onQuote = onQuote,
                                onOpenReactionBubble = onOpenReactionBubble,
                                onOpenReactionPicker = onOpenReactionPicker,
                                onOpenMedia = onOpenMedia,
                                onOpenPost = onOpenPost,
                                onOpenUrl = onOpenUrl,
                                onOpenUsername = onOpenUsername,
                            ),
                            modifier = interactionModifier,
                        )
                        HorizontalDivider(modifier = Modifier.testTag("home_divider_${ownedPost.post.id.value}"), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    }
                }
                if (state.posts.isNotEmpty() && visibleRows.isEmpty() && !state.loading && state.error == null) item {
                    Box(Modifier.fillMaxWidth().then(interactionModifier).padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.feed_filtered_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.loadingMore) item { Box(Modifier.fillMaxWidth().then(interactionModifier).padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                if (state.posts.isNotEmpty() && !state.loadingMore && state.error == null) item {
                    Box(Modifier.fillMaxWidth().then(interactionModifier).padding(16.dp), contentAlignment = Alignment.Center) {
                        if (state.nextCursor != null) TextButton(onClick = { pagingDemand.reset(); onLoadMore() }) { Text(stringResource(R.string.feed_load_older)) }
                        else Text(stringResource(R.string.feed_up_to_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        bottomDock?.let {
            LargeBottomDock(
                content = it,
                modifier = Modifier.align(Alignment.BottomStart)
                    .absolutePadding(left = wideLeftClearance, right = wideRightClearance, bottom = wideBottomClearance),
            )
        }
    }
}
