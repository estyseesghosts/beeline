package me.foxtails.palustris.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.ActionIcon
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.feed.ClientReadyPostActions
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeSearchDockClearance
import me.foxtails.palustris.ui.large.LocalLargeDockEdgeInsets
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.layout.CompactSearchControlsSpacing
import me.foxtails.palustris.ui.layout.CompactSearchDockHeight
import me.foxtails.palustris.ui.layout.compactContextualControlsPositioningInsets
import me.foxtails.palustris.ui.layout.compactScrollEndClearance
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.posts.PostInteractionPresentation
import me.foxtails.palustris.ui.posts.PostRow
import me.foxtails.palustris.ui.posts.PostRowEvents
import me.foxtails.palustris.ui.posts.PostRowPresentation

/**
 * Search: one field, the suggestion panel while the user types, post or hashtag results after a submit,
 * and Trends while the field is empty. There is no category row. The pane follows [searchPresentation].
 *
 * @param rightObstructionClearance Physical-right clearance for wide result content and the wide dock.
 * @param bottomObstructionClearance Wide scroll clearance only. It never moves the dock or the field.
 * @param bottomNavigationClearance Positions the wide dock above compact fallback navigation, including its IME base.
 * Compact layout ignores both values and retains its IME-aware control placement.
 */
@Composable
fun SearchPane(
    search: SearchState = SearchState(),
    onSearch: (String) -> Unit = {},
    onSuggest: (String) -> Unit = {},
    onConsumeTarget: () -> Unit = {},
    onAccountClick: (Account) -> Unit = {},
    availableActions: Set<PostAction> = emptySet(),
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)? = null,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    onShowOnlyHashtag: (String) -> Unit = {},
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    onLoadMoreSearch: () -> Unit = {},
    trends: SearchTrendsState = SearchTrendsState(),
    trendsActions: SearchTrendsActions = SearchTrendsActions.None,
    initialQuery: String = "",
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = false,
    rightObstructionClearance: Dp = 0.dp,
    leftObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp = 0.dp,
    bottomNavigationClearance: Dp = 0.dp,
    mediaOwner: AccountId? = null,
    sessionRevision: Long = 0L,
    onOpenMedia: (me.foxtails.palustris.ui.media.MediaOpenRequest) -> Unit = {},
    onOpenPost: (OwnedPost) -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    largeLayout: Boolean = false,
    sharedQuery: String? = null,
    onSharedQueryChange: (String) -> Unit = {},
    listState: LazyListState? = null,
) {
    var localQuery by rememberSaveable { mutableStateOf("") }
    var largeDockHeightPx by remember { mutableIntStateOf(0) }
    val largeDockClearance = if (largeDockHeightPx > 0) {
        with(LocalDensity.current) { largeDockHeightPx.toDp() }
    } else {
        LargeSearchDockClearance
    }
    val query = sharedQuery ?: localQuery
    fun updateQuery(value: String) {
        if (sharedQuery == null) localQuery = value else onSharedQueryChange(value)
    }
    LaunchedEffect(initialQuery, sharedQuery == null) {
        if (sharedQuery == null && initialQuery.isNotBlank()) updateQuery(initialQuery)
    }
    // Text that no search has answered yet asks for suggestions. A submitted query asks for none.
    val typedForSuggestions = query.takeIf { it.isNotBlank() && it.trim() != search.query }.orEmpty()
    LaunchedEffect(typedForSuggestions) { onSuggest(typedForSuggestions) }
    LaunchedEffect(query.isBlank()) {
        if (query.isBlank()) trendsActions.loadTrends()
    }
    // A handle or link search opens its target once. Consuming it keeps a recomposition from opening it twice.
    LaunchedEffect(search.target) {
        when (val target = search.target) {
            is SearchTarget.Profile -> onAccountClick(target.account)
            is SearchTarget.SinglePost -> onOpenPost(OwnedPost(mediaOwner ?: target.post.author.id, target.post, sessionRevision))
            null -> return@LaunchedEffect
        }
        onConsumeTarget()
    }
    fun submitSearch() {
        if (query.isNotBlank()) onSearch(query)
    }
    val controlsPositioningInsets = compactContextualControlsPositioningInsets(
        navigationVisible = compactNavigationVisible,
        ime = WindowInsets.ime,
    )
    val searchEndClearance = if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = CompactSearchDockHeight,
            navigationVisible = compactNavigationVisible,
            ime = WindowInsets.ime,
        )
    } else {
        0.dp
    }
    // Clear interaction content, not the viewport, the outer row extent, or dividers.
    // Obstruction clearance does not move the dock. Compact fallback navigation has separate placement clearance.
    val wideRightClearance = if (compactLayout) 0.dp else rightObstructionClearance
    val wideLeftClearance = if (compactLayout) 0.dp else leftObstructionClearance
    val wideBottomClearance = if (compactLayout) 0.dp else bottomObstructionClearance
    // The keyboard covers the pane bottom, so the wide dock and the end of the results rise above the part of
    // the IME that the system-bar inset does not already clear. Compact fallback clearance already includes it.
    val density = LocalDensity.current
    val wideImeLift = if (compactLayout) {
        0.dp
    } else {
        with(density) {
            (WindowInsets.ime.getBottom(this) - WindowInsets.systemBars.getBottom(this)).coerceAtLeast(0).toDp()
        }
    }
    val endClearance = if (largeLayout) largeDockClearance + maxOf(wideBottomClearance, wideImeLift) else searchEndClearance
    val rowContext = SearchPostRowContext(
        availableActions = availableActions.intersect(ClientReadyPostActions),
        quoteEnabled = quoteEnabled,
        largeLayout = largeLayout,
        mediaOwner = mediaOwner,
        sessionRevision = sessionRevision,
        clearance = SearchListClearance(endClearance, wideRightClearance, wideLeftClearance),
        events = PostRowEvents(
            onFavourite = onReact,
            onReply = onReply,
            onRepost = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenProfile = onAccountClick,
            onOpenReactionBubble = onOpenReactionBubble ?: { _, _ -> },
            onOpenReactionPicker = onOpenReactionPicker,
            onQuote = onQuote,
            onSearchHashtag = onSearchHashtag,
            onOpenHashtagBubble = onOpenHashtagBubble,
            onOpenMedia = if (mediaOwner != null) onOpenMedia else { _: me.foxtails.palustris.ui.media.MediaOpenRequest -> },
            onOpenPost = onOpenPost,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
        ),
    )

    Box(Modifier.fillMaxSize()) {
        SearchContent(
            modifier = Modifier.fillMaxSize(),
            query = query,
            search = search,
            trends = trends,
            trendsActions = trendsActions,
            rowContext = rowContext,
            onAccountClick = onAccountClick,
            onLoadMoreSearch = onLoadMoreSearch,
            onSearchHashtag = onSearchHashtag,
            onShowOnlyHashtag = onShowOnlyHashtag,
            onOpenUrl = onOpenUrl,
            listState = listState,
        )
        if (largeLayout) {
            LargeBottomDock(
                leftClearance = wideLeftClearance,
                rightClearance = wideRightClearance,
                content = {
                    // The field keeps its bounded width and clears the floating chrome.
                    val dockInsets = LocalLargeDockEdgeInsets.current
                    Box(
                        Modifier.fillMaxWidth()
                            .absolutePadding(left = dockInsets.left, right = dockInsets.right),
                    ) {
                        Box(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                            SearchField(query, ::submitSearch, ::updateQuery)
                        }
                    }
                },
                // Physical edges narrow the dock. Only compact fallback navigation moves it upward.
                // Measurement excludes fallback placement padding so end clearance does not count it twice.
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(bottom = maxOf(bottomNavigationClearance, wideImeLift))
                    .onSizeChanged { largeDockHeightPx = it.height }
                    .testTag("search_dock"),
            )
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(controlsPositioningInsets),
                verticalArrangement = Arrangement.spacedBy(CompactSearchControlsSpacing),
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = CompactOverlayHorizontalPadding)) {
                    SearchField(query, ::submitSearch, ::updateQuery)
                }
            }
        }
    }
}

/** What a post row in Search or Trends needs from its screen. */
internal class SearchPostRowContext(
    val availableActions: Set<PostAction>,
    val quoteEnabled: Boolean,
    val largeLayout: Boolean,
    val mediaOwner: AccountId?,
    val sessionRevision: Long,
    val clearance: SearchListClearance,
    val events: PostRowEvents,
)

/** Post rows with their dividers. The floating edges clear interaction content only, so rows keep full width. */
internal fun LazyListScope.searchPostItems(posts: List<Post>, context: SearchPostRowContext, keyPrefix: String) {
    items(posts, key = { "$keyPrefix-${it.id.connection}/${it.id.value}" }) { post ->
        val scheme = LocalPalustrisMotionScheme.current
        Column(
            Modifier.animateItem(
                fadeInSpec = scheme.fastFadeIn,
                fadeOutSpec = scheme.fastFadeOut,
                placementSpec = scheme.gentleOffset,
            ),
        ) {
            PostRow(
                ownedPost = OwnedPost(context.mediaOwner ?: post.author.id, post, context.sessionRevision),
                modifier = Modifier.absolutePadding(left = context.clearance.left, right = context.clearance.right),
                presentation = PostRowPresentation(
                    availableActions = context.availableActions,
                    quoteEnabled = context.quoteEnabled,
                    largeLayout = context.largeLayout,
                    interactionPresentation = PostInteractionPresentation.Detailed,
                ),
                events = context.events,
            )
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.testTag("search_divider_${post.id.value}"),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
            )
        }
    }
}

/** The footer under a paged list: a spinner, the load button, or the end marker. */
internal fun LazyListScope.searchPagingFooter(
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    clearance: SearchListClearance,
) {
    item(key = "paging-footer") {
        Box(
            Modifier.fillMaxWidth().absolutePadding(left = clearance.left, right = clearance.right).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loadingMore) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else if (hasMore) {
                androidx.compose.material3.TextButton(onClick = onLoadMore) { Text(stringResource(R.string.search_load_older)) }
            } else {
                Text(
                    stringResource(R.string.search_up_to_date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchContent(
    modifier: Modifier,
    query: String,
    search: SearchState,
    trends: SearchTrendsState,
    trendsActions: SearchTrendsActions,
    rowContext: SearchPostRowContext,
    onAccountClick: (Account) -> Unit,
    onLoadMoreSearch: () -> Unit,
    onSearchHashtag: (String) -> Unit,
    onShowOnlyHashtag: (String) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    listState: LazyListState?,
) {
    val presentation = searchPresentation(query, search)
    val clearance = rowContext.clearance
    Box(modifier.testTag("search_content")) {
        val resultKind = when {
            search.loading -> "loading"
            search.error != null || search.postSearchDenied -> "error"
            search.posts.isNotEmpty() -> "results"
            else -> "empty"
        }
        AnimatedStatePane(stateKey = "$presentation:$resultKind", modifier = Modifier.fillMaxSize()) {
            when (presentation) {
                SearchPresentation.Trends -> SearchTrendsPane(
                    state = trends,
                    context = rowContext,
                    onHashtag = onSearchHashtag,
                    onLoadMore = trendsActions::loadMoreTrends,
                    onRetry = trendsActions::refreshTrends,
                    onOpenUrl = onOpenUrl,
                    listState = listState,
                )
                SearchPresentation.Suggestions -> SearchSuggestionPanel(
                    suggestions = search.suggestions,
                    onAccountClick = onAccountClick,
                    onHashtag = onSearchHashtag,
                    clearance = clearance,
                    listState = listState,
                )
                SearchPresentation.Posts, SearchPresentation.Hashtag -> SearchPostResults(
                    state = search,
                    context = rowContext,
                    onLoadMore = onLoadMoreSearch,
                    onHashtag = onSearchHashtag,
                    onShowOnlyHashtag = onShowOnlyHashtag,
                    listState = listState,
                )
                SearchPresentation.Lookup -> SearchLookupPane(search, clearance.end)
            }
        }
    }
}

/** A handle or link lookup in flight shows a spinner. A lookup that failed says so. */
@Composable
private fun SearchLookupPane(search: SearchState, bottomClearance: Dp) {
    if (search.error != null) {
        EmptyState(AppIcons.SearchBeeline, stringResource(R.string.search_lookup_failed), search.error, bottomClearance = bottomClearance)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

/** The three visual states of the one Search field. */
internal enum class SearchEntryMode {
    /** Placeholder shown; nothing typed. */
    Idle,

    /** Field with focus, where the IME is shown. */
    Entry,

    /** Field that still shows the submitted or restored query. */
    Results,
}

/** Derives the visual state from the query and focus, which stay with Search and the navigator. */
internal fun searchEntryMode(query: String, focused: Boolean): SearchEntryMode = when {
    focused -> SearchEntryMode.Entry
    query.isNotBlank() -> SearchEntryMode.Results
    else -> SearchEntryMode.Idle
}

internal val SearchEntryModeKey = SemanticsPropertyKey<SearchEntryMode>("SearchEntryMode")

/**
 * Search's single field. It stays composed through every state, so the query, focus, and caret
 * survive the change. It always fills the width its caller grants, so focus and submit do not
 * resize it. Tall and wide presentations share it and differ only in that width.
 */
@Composable
private fun SearchField(query: String, onSubmit: () -> Unit, onQueryChange: (String) -> Unit) {
    val searchFieldDescription = stringResource(R.string.search_field)
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val mode = searchEntryMode(query, focused)
    // With the keyboard already hidden, Back first closes the entry instead of leaving Search.
    BackHandler(enabled = focused) { focusManager.clearFocus() }
    val submit = { onSubmit(); focusManager.clearFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = searchFieldDescription; this[SearchEntryModeKey] = mode },
        placeholder = { Text(stringResource(R.string.search_placeholder), maxLines = 1) },
        leadingIcon = { Icon(AppIcons.SearchBeeline, null) },
        trailingIcon = { SearchClearButton(query.isNotEmpty()) { onQueryChange("") } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        shape = CircleShape,
        colors = searchFieldColors(),
    )
}

@Composable
private fun searchFieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
)

@Composable
private fun SearchClearButton(visible: Boolean, onClear: () -> Unit) {
    val scheme = LocalPalustrisMotionScheme.current
    AnimatedContent(
        targetState = visible,
        transitionSpec = {
            if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive)) togetherWith
                (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive))
        },
        label = "searchClearVisibility",
    ) { shown -> if (shown) ActionIcon(AppIcons.Close, stringResource(R.string.search_clear), onClear) }
}

/**
 * Post results for a text query or a hashtag. A hashtag adds its header with the combined and related tags.
 * An empty answer names the likely cause: the role policy on a Misskey-family server, or the missing search
 * backend on a Mastodon-API server.
 */
@Composable
private fun SearchPostResults(
    state: SearchState,
    context: SearchPostRowContext,
    onLoadMore: () -> Unit,
    onHashtag: (String) -> Unit,
    onShowOnlyHashtag: (String) -> Unit,
    listState: LazyListState?,
) {
    val clearance = context.clearance
    val hashtag = state.mode == SearchMode.Hashtag
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.postSearchDenied -> EmptyState(
            AppIcons.SearchBeeline,
            stringResource(R.string.search_posts_denied),
            stringResource(R.string.search_posts_denied_prompt),
            bottomClearance = clearance.end,
        )
        state.error != null -> EmptyState(
            AppIcons.SearchBeeline,
            stringResource(if (hashtag) R.string.search_hashtag_failed else R.string.search_posts_failed),
            state.error,
            bottomClearance = clearance.end,
        )
        state.posts.isNotEmpty() -> LazyColumn(
            state = listState ?: rememberLazyListState(),
            modifier = Modifier.fillMaxSize().testTag(if (hashtag) "search_hashtag_results" else "search_post_results"),
            contentPadding = PaddingValues(bottom = 24.dp + clearance.end),
        ) {
            if (hashtag) {
                hashtagResultsHeader(
                    state,
                    Modifier.absolutePadding(left = clearance.left, right = clearance.right),
                    onShowOnlyHashtag,
                    onHashtag,
                )
            }
            searchPostItems(state.posts, context, "result")
            searchPagingFooter(state.loadingMore, state.nextCursor != null, onLoadMore, clearance)
        }
        hashtag -> EmptyState(
            AppIcons.Hashtag,
            stringResource(R.string.search_no_posts),
            stringResource(R.string.search_no_posts_for, state.query),
            bottomClearance = clearance.end,
        )
        else -> EmptyState(
            AppIcons.SearchBeeline,
            stringResource(R.string.search_no_posts),
            stringResource(R.string.search_no_posts_server_hint),
            bottomClearance = clearance.end,
        )
    }
}
