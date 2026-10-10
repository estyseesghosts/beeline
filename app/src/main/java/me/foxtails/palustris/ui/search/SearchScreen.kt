package me.foxtails.palustris.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
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
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.ui.ActionIcon
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.components.ChipCaretPresentation
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.components.FilterChipEntry
import me.foxtails.palustris.ui.feed.ClientReadyPostActions
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.large.CompactWideTabCaretRegistration
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
 * Keeps the Search viewport full size while wide obstruction inputs clear interaction content.
 *
 * @param rightObstructionClearance Physical-right clearance for wide result content and the wide dock.
 * @param bottomObstructionClearance Wide scroll clearance only. It never moves the dock or the field.
 * @param bottomNavigationClearance Positions the wide dock above compact fallback navigation, including its IME base.
 * Compact layout ignores both values and retains its IME-aware control placement.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun SearchScreen(
    accountSearch: AccountSearchState = AccountSearchState(),
    onSearchAccounts: (String) -> Unit = {},
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
    explore: SearchExploreState = SearchExploreState(),
    exploreActions: SearchExploreActions = SearchExploreActions.None,
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
    sharedTab: Int? = null,
    onSharedQueryChange: (String) -> Unit = {},
    onSharedTabChange: (Int) -> Unit = {},
    listState: LazyListState? = null,
    useCompactWideCaret: Boolean = false,
    tabCaretHost: CompactWideTabCaretHost? = null,
) {
    var localQuery by rememberSaveable { mutableStateOf("") }
    var localTab by rememberSaveable { mutableIntStateOf(0) }
    var chipRowVisible by rememberSaveable { mutableStateOf(true) }
    val chipListState = rememberLazyListState()
    var largeDockHeightPx by remember { mutableIntStateOf(0) }
    val largeDockClearance = if (largeDockHeightPx > 0) {
        with(LocalDensity.current) { largeDockHeightPx.toDp() }
    } else {
        LargeSearchDockClearance
    }
    val query = sharedQuery ?: localQuery
    val tab = sharedTab ?: localTab
    fun updateQuery(value: String) {
        if (sharedQuery == null) localQuery = value else onSharedQueryChange(value)
    }
    fun updateTab(value: Int) {
        if (sharedTab == null) localTab = value else onSharedTabChange(value)
    }
    LaunchedEffect(initialQuery, sharedQuery == null) {
        if (sharedQuery == null && initialQuery.isNotBlank()) updateQuery(initialQuery)
    }
    val hashtagSearchRequested = isExactHashtag(query)
    // A typed hashtag that no search has answered yet asks for suggestions. Anything else clears them.
    val typedHashtag = query.takeIf { hashtagSearchRequested && accountSearch.query != it.trim() }.orEmpty()
    LaunchedEffect(typedHashtag) { exploreActions.suggestHashtags(typedHashtag) }
    LaunchedEffect(tab, query.isBlank()) {
        if (!query.isBlank()) return@LaunchedEffect
        when (tab) {
            0 -> exploreActions.loadPopularAccounts()
            1 -> exploreActions.loadTrending()
        }
    }
    val sections = listOf(
        stringResource(R.string.search_category_profiles),
        stringResource(R.string.search_category_hashtags),
        stringResource(R.string.search_category_news),
        stringResource(R.string.search_category_for_you),
    )
    val chipEntries = sections.mapIndexed { index, title ->
        FilterChipEntry(
            label = title,
            selected = tab == index,
            onClick = { updateTab(index) },
            testTag = "search_category_$index",
            key = "search-category:$index",
        )
    }
    fun submitSearch() {
        if (query.isNotBlank()) onSearchAccounts(query)
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
    if (useCompactWideCaret) {
        CompactWideTabCaretRegistration(
            host = tabCaretHost,
            expanded = chipRowVisible,
            onToggle = { chipRowVisible = !chipRowVisible },
        )
    }
    val searchCaretPresentation = if (useCompactWideCaret) ChipCaretPresentation.Hidden
    else ChipCaretPresentation.Inline

    Box(Modifier.fillMaxSize()) {
        SearchContent(
            modifier = Modifier.fillMaxSize(),
            query = query,
            tab = tab,
            hashtagSearchRequested = hashtagSearchRequested,
            accountSearch = accountSearch,
            onAccountClick = onAccountClick,
            availableActions = availableActions,
            onReact = onReact,
            onReply = onReply,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenReactionBubble = onOpenReactionBubble,
            onOpenReactionPicker = onOpenReactionPicker,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
            onLoadMoreSearch = onLoadMoreSearch,
            explore = explore,
            endClearance = if (largeLayout) largeDockClearance + maxOf(wideBottomClearance, wideImeLift) else searchEndClearance,
            rightClearance = wideRightClearance,
            leftClearance = wideLeftClearance,
            mediaOwner = mediaOwner,
            sessionRevision = sessionRevision,
            onOpenMedia = onOpenMedia,
            onOpenPost = onOpenPost,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
            onSearchHashtag = onSearchHashtag,
            onShowOnlyHashtag = onShowOnlyHashtag,
            onOpenHashtagBubble = onOpenHashtagBubble,
            listState = listState,
            largeLayout = largeLayout,
        )
        if (largeLayout) {
            LargeBottomDock(
                leftClearance = wideLeftClearance,
                rightClearance = wideRightClearance,
                content = {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CompactSearchControlsSpacing),
                    ) {
                        DestinationChipRow(
                            entries = chipEntries,
                            rowContentDescription = stringResource(R.string.search_categories_description),
                            listState = chipListState,
                            visible = chipRowVisible,
                            onToggleVisibility = { chipRowVisible = !chipRowVisible },
                            rowTestTag = "search_categories",
                            visibilityToggleTestTag = "search_categories_visibility",
                            caretPresentation = searchCaretPresentation,
                        )
                        // The field keeps its bounded width and clears the floating chrome. The chips do not.
                        val dockInsets = LocalLargeDockEdgeInsets.current
                        Box(
                            Modifier.fillMaxWidth()
                                .absolutePadding(left = dockInsets.left, right = dockInsets.right),
                        ) {
                            Box(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                                SearchField(query, ::submitSearch, ::updateQuery)
                            }
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
                DestinationChipRow(
                    entries = chipEntries,
                    rowContentDescription = stringResource(R.string.search_categories_description),
                    listState = chipListState,
                    visible = chipRowVisible,
                    onToggleVisibility = { chipRowVisible = !chipRowVisible },
                    rowTestTag = "search_categories",
                    visibilityToggleTestTag = "search_categories_visibility",
                    caretPresentation = searchCaretPresentation,
                    leftInset = CompactOverlayHorizontalPadding,
                    rightInset = CompactOverlayHorizontalPadding,
                )
                Box(Modifier.fillMaxWidth().padding(horizontal = CompactOverlayHorizontalPadding)) {
                    SearchField(query, ::submitSearch, ::updateQuery)
                }
            }
        }
    }
}

@Composable
private fun SearchContent(
    modifier: Modifier,
    query: String,
    tab: Int,
    hashtagSearchRequested: Boolean,
    accountSearch: AccountSearchState,
    onAccountClick: (Account) -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    onLoadMoreSearch: () -> Unit,
    explore: SearchExploreState,
    endClearance: Dp,
    rightClearance: Dp,
    leftClearance: Dp,
    mediaOwner: AccountId?,
    sessionRevision: Long,
    onOpenMedia: (me.foxtails.palustris.ui.media.MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    onSearchHashtag: (String) -> Unit,
    onShowOnlyHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    listState: LazyListState?,
    largeLayout: Boolean,
) {
    val clearance = SearchListClearance(endClearance, rightClearance, leftClearance)
    Box(modifier.testTag("search_content")) {
        val queryKind = when {
            hashtagSearchRequested -> "hashtag"
            query.isBlank() -> "blank"
            else -> "account"
        }
        val resultKind = when {
            accountSearch.loading -> "loading"
            accountSearch.error != null -> "error"
            accountSearch.accounts.isNotEmpty() || accountSearch.posts.isNotEmpty() -> "results"
            else -> "empty"
        }
        AnimatedStatePane(stateKey = "$tab:$queryKind:$resultKind", modifier = Modifier.fillMaxSize()) {
            if (tab == 0 || hashtagSearchRequested) {
                if (hashtagSearchRequested && explore.suggestions.isNotEmpty() && accountSearch.query != query.trim()) {
                    HashtagSuggestionList(explore.suggestions, onSearchHashtag, clearance, listState)
                } else if (hashtagSearchRequested) HashtagSearchResults(
                    state = accountSearch,
                    query = query,
                    onLoadMore = onLoadMoreSearch,
                    onAccountClick = onAccountClick,
                    availableActions = availableActions,
                    onReact = onReact,
                    onReply = onReply,
                    onReshare = onReshare,
                    onBookmark = onBookmark,
                    onReaction = onReaction,
                    onOpenReactionBubble = onOpenReactionBubble,
                    onOpenReactionPicker = onOpenReactionPicker,
                    quoteEnabled = quoteEnabled,
                    onQuote = onQuote,
                    endClearance = endClearance,
                    rightClearance = rightClearance,
                    leftClearance = leftClearance,
                    mediaOwner = mediaOwner,
                    sessionRevision = sessionRevision,
                    onOpenMedia = onOpenMedia,
                    onSearchHashtag = onSearchHashtag,
                    onShowOnlyHashtag = onShowOnlyHashtag,
                    onOpenHashtagBubble = onOpenHashtagBubble,
                    listState = listState,
                    largeLayout = largeLayout,
                    onOpenPost = onOpenPost,
                    onOpenUrl = onOpenUrl,
                    onOpenUsername = onOpenUsername,
                ) else AccountSearchResults(query, accountSearch, explore.popularAccounts, onAccountClick, clearance, listState)
            } else if (tab == 1 && query.isBlank() && explore.trending.isNotEmpty()) {
                TrendingHashtagList(explore.trending, onSearchHashtag, clearance, listState)
            } else {
                EmptyState(
                    AppIcons.Hashtag,
                    if (query.isNotBlank()) stringResource(R.string.search_ready_when_you_are) else when (tab) {
                        1 -> stringResource(R.string.search_explore_hashtags)
                        2 -> stringResource(R.string.search_news_from_network)
                        else -> stringResource(R.string.search_find_your_people)
                    },
                    if (query.isNotBlank()) stringResource(R.string.search_accounts_available_profiles)
                    else when (tab) {
                        1 -> stringResource(R.string.search_trending_topics)
                        2 -> stringResource(R.string.search_popular_links)
                        else -> stringResource(R.string.search_suggested_accounts)
                    },
                    bottomClearance = endClearance,
                )
            }
        }
    }
}

/** The three visual states of the one Search field. */
internal enum class SearchEntryMode {
    /** Compact bubble with the placeholder; nothing typed. */
    Idle,

    /** Expanded field with focus, where the IME is shown. */
    Entry,

    /** Compact bubble that still shows the submitted or restored query. */
    Results,
}

/** Derives the visual state from the query and focus, which stay with Search and the navigator. */
internal fun searchEntryMode(query: String, focused: Boolean): SearchEntryMode = when {
    focused -> SearchEntryMode.Entry
    query.isNotBlank() -> SearchEntryMode.Results
    else -> SearchEntryMode.Idle
}

internal val SearchEntryModeKey = SemanticsPropertyKey<SearchEntryMode>("SearchEntryMode")

private val SearchBubbleMaxWidth = 240.dp

/**
 * Search's single field. It stays composed through every state, so the query, focus, and caret
 * survive the change; only its width animates. Focus expands it for entry. Submitting or leaving
 * focus collapses it to a bubble that keeps the query visible. Tall and wide presentations share it
 * and differ only in the width their caller grants.
 */
@Composable
private fun SearchField(query: String, onSubmit: () -> Unit, onQueryChange: (String) -> Unit) {
    val scheme = LocalPalustrisMotionScheme.current
    val searchFieldDescription = stringResource(R.string.search_field)
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val mode = searchEntryMode(query, focused)
    // With the keyboard already hidden, Back first closes the entry instead of leaving Search.
    BackHandler(enabled = focused) { focusManager.clearFocus() }
    val width = if (mode == SearchEntryMode.Entry) Modifier.fillMaxWidth() else Modifier.widthIn(max = SearchBubbleMaxWidth)
    val submit = { onSubmit(); focusManager.clearFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.animateContentSize(scheme.gentleSize).then(width)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = searchFieldDescription; this[SearchEntryModeKey] = mode },
        placeholder = { Text(stringResource(R.string.search_placeholder_handle), maxLines = 1) },
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

@Composable
private fun HashtagSearchResults(
    state: AccountSearchState,
    query: String,
    onLoadMore: () -> Unit,
    onAccountClick: (Account) -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    endClearance: Dp,
    rightClearance: Dp,
    leftClearance: Dp,
    mediaOwner: AccountId?,
    sessionRevision: Long,
    onOpenMedia: (me.foxtails.palustris.ui.media.MediaOpenRequest) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onShowOnlyHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    listState: LazyListState?,
    largeLayout: Boolean,
) {
    val scheme = LocalPalustrisMotionScheme.current
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> EmptyState(AppIcons.SearchBeeline, stringResource(R.string.search_hashtag_failed), state.error)
        state.posts.isNotEmpty() && state.query == query.trim() -> LazyColumn(
            state = listState ?: rememberLazyListState(),
            modifier = Modifier.fillMaxSize().testTag("search_hashtag_results"),
            contentPadding = PaddingValues(bottom = 24.dp + endClearance),
        ) {
            hashtagResultsHeader(state, Modifier.absolutePadding(left = leftClearance, right = rightClearance), onShowOnlyHashtag, onSearchHashtag)
            items(state.posts, key = { "${it.id.connection}/${it.id.value}" }) { post ->
                Column(
                    Modifier.animateItem(
                        fadeInSpec = scheme.fastFadeIn,
                        fadeOutSpec = scheme.fastFadeOut,
                        placementSpec = scheme.gentleOffset,
                    ),
                ) {
                    PostRow(
                        ownedPost = OwnedPost(mediaOwner ?: post.author.id, post, sessionRevision),
                        // The floating edges clear interaction content only. The row extent and its
                        // divider keep full width so content passes under floating chrome.
                        modifier = Modifier.absolutePadding(left = leftClearance, right = rightClearance),
                        presentation = PostRowPresentation(
                            availableActions = availableActions.intersect(ClientReadyPostActions),
                            quoteEnabled = quoteEnabled,
                            largeLayout = largeLayout,
                            interactionPresentation = PostInteractionPresentation.Detailed,
                        ),
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
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.testTag("search_divider_${post.id.value}"),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth().absolutePadding(left = leftClearance, right = rightClearance).padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                    else if (state.nextCursor != null) androidx.compose.material3.TextButton(onClick = onLoadMore) { Text(stringResource(R.string.search_load_older)) }
                    else Text(stringResource(R.string.search_up_to_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        query.isBlank() -> EmptyState(AppIcons.Hashtag, stringResource(R.string.search_find_hashtag), stringResource(R.string.search_hashtag_prompt))
        state.query == query.trim() -> EmptyState(AppIcons.Hashtag, stringResource(R.string.search_no_posts), stringResource(R.string.search_no_posts_for, state.query))
        else -> EmptyState(AppIcons.Hashtag, stringResource(R.string.search_hashtag_ready), stringResource(R.string.search_hashtag_ready_prompt))
    }
}

@Composable
private fun AccountSearchResults(
    query: String,
    state: AccountSearchState,
    popularAccounts: List<Account>,
    onAccountClick: (Account) -> Unit,
    clearance: SearchListClearance,
    listState: LazyListState?,
) {
    val endClearance = clearance.end
    when {
        query.isBlank() && popularAccounts.isNotEmpty() -> AccountList(
            popularAccounts,
            stringResource(R.string.search_popular_accounts),
            onAccountClick,
            clearance,
            listState,
            "search_popular_accounts",
        )
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> EmptyState(AppIcons.SearchBeeline, stringResource(R.string.search_account_failed), state.error, bottomClearance = endClearance)
        // Both physical edges belong to the scroll content, so account rows and their
        // click bounds keep clear of floating chrome. The list viewport keeps its width.
        state.accounts.isNotEmpty() -> AccountList(state.accounts, null, onAccountClick, clearance, listState, "search_account_results")
        query.isBlank() -> EmptyState(AppIcons.SearchBeeline, stringResource(R.string.search_find_account), stringResource(R.string.search_account_prompt), bottomClearance = endClearance)
        state.query == query.trim() -> EmptyState(AppIcons.SearchBeeline, stringResource(R.string.search_no_account), stringResource(R.string.search_no_account_prompt), bottomClearance = endClearance)
        else -> EmptyState(AppIcons.SearchBeeline, stringResource(R.string.search_ready), stringResource(R.string.search_ready_prompt), bottomClearance = endClearance)
    }
}
