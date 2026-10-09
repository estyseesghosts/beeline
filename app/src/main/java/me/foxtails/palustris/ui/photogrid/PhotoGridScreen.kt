@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.foxtails.palustris.ui.photogrid

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.ContentWarningDecision
import me.foxtails.palustris.domain.ContentWarningPolicy
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestRole
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.components.ChipCaretPresentation
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.components.FilterChipEntry
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.large.CompactWideTabCaretRegistration
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.layout.CompactFilterDockHeight
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.layout.compactContextualControlsPositioningInsets
import me.foxtails.palustris.ui.layout.compactScrollEndClearance
import me.foxtails.palustris.ui.posts.LocalContentWarningRules
import me.foxtails.palustris.ui.posts.LocalHiddenContentPresentation
import me.foxtails.palustris.ui.posts.LocalMutedHashtags
import me.foxtails.palustris.ui.posts.isPostContentVisible
import me.foxtails.palustris.ui.posts.postHashtags
import me.foxtails.palustris.ui.shell.timelineLabelRes

internal data class PhotoGridItem(
    val ownedPost: OwnedPost,
    val attachmentIndex: Int,
    val attachment: Attachment,
    val hiddenByRules: Boolean = false,
    /** Non-null while a collapsed content warning covers the tile; the text is the warning itself. */
    val collapsedWarning: String? = null,
)

internal fun photoGridItems(posts: List<OwnedPost>, rules: ContentWarningRules = ContentWarningRules(), hiddenPresentation: me.foxtails.palustris.domain.HiddenContentPresentation = me.foxtails.palustris.domain.HiddenContentPresentation.Placeholder, mutedHashtags: Set<String> = emptySet()): List<PhotoGridItem> = posts.mapNotNull { ownedPost ->
    val post = ownedPost.post
    val hashtags = postHashtags(post.text, post.emoji)
    if (ContentWarningPolicy.matchesHashtagMute(hashtags, mutedHashtags)) return@mapNotNull null
    val decision = ContentWarningPolicy.decide(
        post.contentWarning,
        hashtags,
        rules,
        post.contentVisibility,
        bodyText = post.text,
    )
    val hidden = decision == ContentWarningDecision.Hidden
    if (hidden && hiddenPresentation == me.foxtails.palustris.domain.HiddenContentPresentation.Remove) return@mapNotNull null
    post.attachments.withIndex()
        .firstOrNull { (_, attachment) -> attachment.isPhotoGridDisplayable() }
        ?.let { (index, attachment) ->
            val coveredByWarning = !hidden && !isPostContentVisible(decision, post.contentWarning != null, expanded = false)
            PhotoGridItem(
                ownedPost,
                index,
                attachment,
                hidden,
                collapsedWarning = if (coveredByWarning) post.contentWarning.orEmpty() else null,
            )
        }
}

private fun Attachment.isPhotoGridDisplayable(): Boolean =
    kind == MediaKind.Image &&
    MediaRequestPolicy.resolve(
        attachment = this,
        role = MediaRequestRole.Preview,
        revealed = true,
        explicitlyOpened = false,
    ) is MediaRequestDecision.Request

/** How far a card in the outermost lane may extend under the floating rail. */
internal val PhotoGridRailUnderlap = 16.dp

/** Grid padding on a wide edge: the rail clearance less the underlap, never below the outer padding. */
internal fun photoGridEdgePadding(clearance: Dp): Dp = maxOf(clearance - PhotoGridRailUnderlap, PhotoGridOuterPadding)

/** Mirrors [StaggeredGridCells.Adaptive]: as many lanes of at least [minLanePx] as fit, at least one. */
internal fun photoGridLaneCount(availablePx: Int, spacingPx: Int, minLanePx: Int): Int =
    maxOf((availablePx + spacingPx) / (minLanePx + spacingPx), 1)

/** Narrowest and widest image width-to-height ratio, so one extreme image cannot dominate a lane. A card is never wider than 16:9. */
internal const val PHOTO_GRID_MIN_TILE_ASPECT = 0.5f
internal const val PHOTO_GRID_MAX_TILE_ASPECT = 16f / 9f

internal fun photoGridAspectRatio(attachment: Attachment): Float {
    val width = attachment.width ?: attachment.previewWidth
    val height = attachment.height ?: attachment.previewHeight
    return if (width != null && height != null && width > 0 && height > 0) {
        (width.toFloat() / height.toFloat()).coerceIn(PHOTO_GRID_MIN_TILE_ASPECT, PHOTO_GRID_MAX_TILE_ASPECT)
    } else {
        4f / 3f
    }
}

/**
 * Keeps the Photo Grid viewport full size while wide obstruction inputs clear grid content.
 *
 * @param rightObstructionClearance Physical-right clearance for wide grid content and the wide dock.
 * @param bottomObstructionClearance Wide grid end clearance and the wide dock offset.
 * Compact layout ignores both values and keeps its current clearance and control placement.
 */
@Composable
fun PhotoGridScreen(
    state: PhotoGridFeedState = PhotoGridFeedState(),
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onSelectFeed: (PhotoGridFeed) -> Unit = {},
    onAddHashtag: (String, () -> Unit) -> Unit = { _, onSuccess -> onSuccess() },
    onClearPreferenceError: () -> Unit = {},
    onOpenPost: (OwnedPost) -> Unit = {},
    cardActions: PhotoGridCardActions = PhotoGridCardActions(),
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = false,
    rightObstructionClearance: Dp = 0.dp,
    leftObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp = 0.dp,
    gridState: LazyStaggeredGridState? = null,
    contentWarningRules: ContentWarningRules = LocalContentWarningRules.current,
    useCompactWideCaret: Boolean = false,
    tabCaretHost: CompactWideTabCaretHost? = null,
) {
    var addHashtagDialog by rememberSaveable { mutableStateOf(false) }
    var hashtagInput by rememberSaveable { mutableStateOf("") }
    var chipRowVisible by rememberSaveable { mutableStateOf(true) }
    val chipListState = rememberLazyListState()
    val rows = state.posts
    val hiddenPresentation = LocalHiddenContentPresentation.current
    val mutedHashtags = LocalMutedHashtags.current
    val mediaItems = remember(rows, contentWarningRules, hiddenPresentation, mutedHashtags) {
        photoGridItems(rows, contentWarningRules, hiddenPresentation, mutedHashtags)
    }
    val list = gridState ?: rememberLazyStaggeredGridState()
    val loadMore by rememberUpdatedState(onLoadMore)
    var requestedCursor by remember { mutableStateOf<String?>(null) }
    val bottomClearance = if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = CompactFilterDockHeight,
            navigationVisible = compactNavigationVisible,
        )
    } else {
        LargeBottomDockClearance
    }
    // A tile is opaque media and one click target, so the grid content area clears the floating
    // navigation on both physical edges. The viewport keeps its full size and the lanes stay
    // adaptive. Physical left and physical right do not reverse with the layout direction.
    val wideRightClearance = if (compactLayout) 0.dp else rightObstructionClearance
    val wideLeftClearance = if (compactLayout) 0.dp else leftObstructionClearance
    // Cards underlap the floating rail by up to PhotoGridRailUnderlap. Their footer controls do not:
    // the lane next to the rail insets its footer by the underlap actually reached.
    val gridLeftPadding = if (compactLayout) PhotoGridOuterPadding else photoGridEdgePadding(wideLeftClearance)
    val gridRightPadding = if (compactLayout) PhotoGridOuterPadding else photoGridEdgePadding(wideRightClearance)
    val leftUnderlap = (wideLeftClearance - gridLeftPadding).coerceAtLeast(0.dp)
    val rightUnderlap = (wideRightClearance - gridRightPadding).coerceAtLeast(0.dp)
    // Full-line rows (loading, retry, continuation, empty) are controls or text, so they stay clear of the rail.
    val footerRowInset = Modifier.absolutePadding(left = leftUnderlap, right = rightUnderlap)
    val favourite = remember(cardActions) {
        if (PostAction.Favorite in cardActions.availableActions) {
            PhotoGridFavourite(cardActions.favouriteArtworkStyle, cardActions.onFavourite)
        } else {
            null
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    // Lane of each visible card by key, and the lane count the adaptive cells produce. Lanes are
    // laid out start to end, so the physical edges swap with the layout direction.
    val laneByKey by remember(list) {
        derivedStateOf { list.layoutInfo.visibleItemsInfo.associate { it.key to it.lane } }
    }
    val laneCount by remember(list, gridLeftPadding, gridRightPadding, density) {
        derivedStateOf {
            with(density) {
                val available = list.layoutInfo.viewportSize.width - (gridLeftPadding + gridRightPadding).roundToPx()
                if (available <= 0) 0 else photoGridLaneCount(available, PhotoGridGap.roundToPx(), 150.dp.roundToPx())
            }
        }
    }
    val wideBottomClearance = if (compactLayout) 0.dp else bottomObstructionClearance
    if (useCompactWideCaret) {
        CompactWideTabCaretRegistration(
            host = tabCaretHost,
            expanded = chipRowVisible,
            onToggle = { chipRowVisible = !chipRowVisible },
        )
    }
    val photoGridCaretPresentation = if (useCompactWideCaret) ChipCaretPresentation.Hidden
    else ChipCaretPresentation.Inline

    LaunchedEffect(state.selectedFeed, state.initialLoadComplete) {
        requestedCursor = null
    }
    LaunchedEffect(list, mediaItems.size, state.nextCursor, state.loading, state.loadingMore, state.error) {
        snapshotFlow {
            val lastVisible = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = if (mediaItems.isEmpty()) {
                list.layoutInfo.totalItemsCount > 0
            } else {
                lastVisible >= (mediaItems.size - 3).coerceAtLeast(0)
            }
            nearEnd && state.nextCursor != null && state.error == null &&
                !state.loading && !state.loadingMore
        }.distinctUntilChanged().collectLatest { shouldLoadMore ->
            val cursor = state.nextCursor
            if (shouldLoadMore && cursor != null && requestedCursor != cursor) {
                requestedCursor = cursor
                loadMore()
            }
        }
    }

    val selectedTimeline = (state.selectedFeed as? PhotoGridFeed.TimelineFeed)?.timeline
    val chipEntries = buildList {
        timelineDisplayOrder.filter { it in state.availableTimelines }.forEach { timeline ->
            add(
                FilterChipEntry(
                    label = stringResource(timelineLabelRes(timeline)),
                    selected = selectedTimeline == timeline,
                    onClick = { onSelectFeed(PhotoGridFeed.TimelineFeed(timeline)) },
                    contentDescription = stringResource(timelineLabelRes(timeline)),
                    key = "photo-grid-timeline:${timeline.name}",
                ),
            )
        }
        state.savedHashtags.forEach { tag ->
            add(
                FilterChipEntry(
                    label = "#$tag".removePrefix("##"),
                    selected = (state.selectedFeed as? PhotoGridFeed.Hashtag)?.tag == tag,
                    onClick = { onSelectFeed(PhotoGridFeed.Hashtag(tag)) },
                    contentDescription = tag,
                    key = "photo-grid-hashtag:$tag",
                ),
            )
        }
        add(
            FilterChipEntry(
                label = stringResource(R.string.photo_grid_add_hashtag),
                onClick = {
                    hashtagInput = ""
                    onClearPreferenceError()
                    addHashtagDialog = true
                },
                role = Role.Button,
                contentDescription = stringResource(R.string.photo_grid_add_hashtag),
                testTag = "photo_grid_add_hashtag",
                key = "photo-grid-action:add-hashtag",
            ),
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading && mediaItems.isEmpty() -> PhotoGridLoading(Modifier.fillMaxSize().testTag("photo_grid_content"))
                state.error != null && mediaItems.isEmpty() -> PhotoGridError(
                    message = state.error,
                    onRetry = if (state.nextCursor != null) onLoadMore else onRefresh,
                    modifier = Modifier.fillMaxSize().testTag("photo_grid_content"),
                    rightClearance = wideRightClearance,
                    leftClearance = wideLeftClearance,
                )
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(150.dp),
                    state = list,
                    modifier = Modifier.fillMaxSize().testTag("photo_grid_content"),
                    verticalItemSpacing = PhotoGridGap,
                    horizontalArrangement = Arrangement.spacedBy(PhotoGridGap),
                    contentPadding = PaddingValues.Absolute(
                        left = gridLeftPadding,
                        right = gridRightPadding,
                        bottom = bottomClearance + wideBottomClearance + PhotoGridGap,
                    ),
                ) {
                    items(
                        items = mediaItems,
                        key = { item -> photoGridItemKey(item) },
                    ) { item ->
                        val lane = laneByKey[photoGridItemKey(item)]
                        val startLane = lane == 0
                        val endLane = lane != null && laneCount > 0 && lane == laneCount - 1
                        val rtl = layoutDirection == LayoutDirection.Rtl
                        PhotoGridTile(
                            item = item,
                            onOpenPost = onOpenPost,
                            chrome = PhotoGridCardChrome(
                                favourite = favourite,
                                leftRailInset = if (if (rtl) endLane else startLane) leftUnderlap else 0.dp,
                                rightRailInset = if (if (rtl) startLane else endLane) rightUnderlap else 0.dp,
                                onQuickView = cardActions.onQuickView,
                            ),
                        )
                    }
                    if (state.loadingMore) {
                        item(key = "photo-grid-loading-more", span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                footerRowInset.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        }
                    } else if (state.error != null && mediaItems.isNotEmpty()) {
                        item(key = "photo-grid-paging-error", span = StaggeredGridItemSpan.FullLine) {
                            Box(footerRowInset) { PhotoGridPagingError(state.error, onLoadMore) }
                        }
                    } else if (state.nextCursor != null) {
                        item(key = "photo-grid-load-more", span = StaggeredGridItemSpan.FullLine) {
                            TextButton(onClick = onLoadMore, modifier = footerRowInset.fillMaxWidth()) {
                                Text(stringResource(R.string.photo_grid_load_older))
                            }
                        }
                    } else if (mediaItems.isNotEmpty()) {
                        item(key = "photo-grid-up-to-date", span = StaggeredGridItemSpan.FullLine) {
                            Text(
                                stringResource(R.string.photo_grid_up_to_date),
                                modifier = footerRowInset.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        item(key = "photo-grid-empty", span = StaggeredGridItemSpan.FullLine) {
                            EmptyState(
                                AppIcons.PhotoGrid,
                                stringResource(R.string.photo_grid_empty_title),
                                stringResource(R.string.photo_grid_empty_subtitle),
                                modifier = footerRowInset.fillMaxWidth().height(300.dp),
                            )
                        }
                    }
                }
            }
        }
        if (compactLayout) {
            DestinationChipRow(
                entries = chipEntries,
                rowContentDescription = stringResource(R.string.photo_grid_filter_description),
                listState = chipListState,
                visible = chipRowVisible,
                onToggleVisibility = { chipRowVisible = !chipRowVisible },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        compactContextualControlsPositioningInsets(compactNavigationVisible),
                    ),
                rowTestTag = "photo_grid_filters",
                visibilityToggleTestTag = "photo_grid_filters_visibility",
                caretPresentation = photoGridCaretPresentation,
                leftInset = CompactOverlayHorizontalPadding,
                rightInset = CompactOverlayHorizontalPadding,
            )
        } else {
            LargeBottomDock(
                // The wide dock clears the floating chrome the same way the Home timeline dock does.
                modifier = Modifier.align(Alignment.BottomStart)
                    .absolutePadding(bottom = wideBottomClearance)
                    .testTag("photo_grid_dock"),
                leftClearance = wideLeftClearance,
                rightClearance = wideRightClearance,
                content = {
                    DestinationChipRow(
                        entries = chipEntries,
                        rowContentDescription = stringResource(R.string.photo_grid_filter_description),
                        listState = chipListState,
                        visible = chipRowVisible,
                        onToggleVisibility = { chipRowVisible = !chipRowVisible },
                        rowTestTag = "photo_grid_filters",
                        visibilityToggleTestTag = "photo_grid_filters_visibility",
                        caretPresentation = photoGridCaretPresentation,
                    )
                },
            )
        }
    }

    if (addHashtagDialog) {
        val validInput = isExactHashtag(hashtagInput)
        AlertDialog(
            onDismissRequest = {
                if (!state.preferenceSaving) {
                    addHashtagDialog = false
                    onClearPreferenceError()
                }
            },
            title = { Text(stringResource(R.string.photo_grid_add_hashtag_title)) },
            text = {
                OutlinedTextField(
                    value = hashtagInput,
                    onValueChange = { hashtagInput = it; onClearPreferenceError() },
                    enabled = !state.preferenceSaving,
                    label = { Text(stringResource(R.string.photo_grid_hashtag_hint)) },
                    singleLine = true,
                    isError = hashtagInput.isNotEmpty() && !validInput,
                    supportingText = {
                        when {
                            hashtagInput.isNotEmpty() && !validInput -> Text(stringResource(R.string.photo_grid_invalid_hashtag))
                            state.preferenceError == "save" -> Text(stringResource(R.string.photo_grid_preference_save_failed))
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = validInput && !state.preferenceSaving,
                    onClick = { onAddHashtag(hashtagInput) { addHashtagDialog = false; hashtagInput = "" } },
                ) { Text(stringResource(R.string.dialog_add)) }
            },
            dismissButton = {
                TextButton(onClick = { addHashtagDialog = false; onClearPreferenceError() }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

internal fun photoGridItemKey(item: PhotoGridItem): String =
    "${item.ownedPost.fetchedBy.connection.origin}/${item.ownedPost.post.id.connection}/${item.ownedPost.post.id.value}/${item.attachment.id ?: item.attachmentIndex}"

@Composable
private fun PhotoGridLoading(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun PhotoGridError(message: String, onRetry: () -> Unit, rightClearance: Dp, leftClearance: Dp, modifier: Modifier) {
    // The error viewport keeps its full size. Only the retry content clears the floating edges.
    Column(
        modifier.absolutePadding(left = leftClearance, right = rightClearance).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.notifications_retry)) }
    }
}

@Composable
private fun PhotoGridPagingError(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.photo_grid_paging_error), color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.notifications_retry)) }
        }
    }
}
