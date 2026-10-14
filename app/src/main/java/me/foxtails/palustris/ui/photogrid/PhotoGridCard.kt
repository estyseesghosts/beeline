@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.foxtails.palustris.ui.photogrid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import me.foxtails.palustris.R
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.FavouriteArtworkStyle
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestRole
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.components.BeelineNestedSurfaceShape
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.media.SensitiveMediaTile
import me.foxtails.palustris.ui.media.VideoTileContent
import me.foxtails.palustris.ui.posts.currentPostReveals
import me.foxtails.palustris.ui.posts.favouriteIconFor

@Composable
internal fun PhotoGridTile(
    item: PhotoGridItem,
    onOpenPost: (OwnedPost) -> Unit,
    modifier: Modifier = Modifier,
    chrome: PhotoGridCardChrome = PhotoGridCardChrome(),
) {
    // A card hidden by the rules shows no author and no favorite button. A warned card has no caption.
    PhotoGridCard(
        item = item,
        modifier = modifier,
        favourite = chrome.favourite.takeUnless { item.hiddenByRules },
        leftRailInset = chrome.leftRailInset,
        rightRailInset = chrome.rightRailInset,
        caption = photoGridCaption(item),
        showMeta = !item.hiddenByRules,
    ) {
        PhotoGridTileMedia(item = item, onOpenPost = onOpenPost, onQuickView = chrome.onQuickView)
    }
}

/**
 * The media area of one grid entry: the hidden placeholder, the collapsed warning, the sensitive cover,
 * or the image or video with its loading and failed states. Photo Grid cards and the profile media grid
 * share it. The caller sets the [aspectRatio] and the [shape]; a tap opens the post.
 *
 * A post with several images or videos pages horizontally inside the one frame, under [PhotoGridPageDots].
 * The frame never changes size, so a later page crops to the frame of the first. A post with one image or
 * one video has no pager. Only the page in view is composed, so a video page leaves playback when it leaves.
 */
@Composable
internal fun PhotoGridTileMedia(
    item: PhotoGridItem,
    onOpenPost: (OwnedPost) -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = photoGridAspectRatio(item.attachment),
    shape: Shape = PhotoGridImageShape,
    onQuickView: ((PhotoQuickViewTarget) -> Unit)? = null,
) {
    if (item.hiddenByRules) {
        val hiddenMessage = stringResource(R.string.content_hidden_settings)
        Box(
            modifier.fillMaxWidth().aspectRatio(aspectRatio)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .semantics { contentDescription = hiddenMessage },
            contentAlignment = Alignment.Center,
        ) { Text(hiddenMessage, Modifier.padding(12.dp)) }
        return
    }
    val reveals = currentPostReveals()
    // One tap on the warning tile reveals the post in place. The tile then shows the image.
    if (item.collapsedWarning != null && !reveals.warningExpanded(item.ownedPost)) {
        PhotoGridWarningTile(item, item.collapsedWarning, modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(shape)) {
            reveals.setWarningExpanded(item.ownedPost, true)
        }
        return
    }
    val tileKey = photoGridItemKey(item)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val pages = item.pages
    // Every page crops to this one frame, which takes its ratio from the first image.
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .onGloballyPositioned { bounds = it.boundsInRoot() },
    ) {
        if (pages.size == 1) {
            PhotoGridTilePage(item, pages.first(), tileKey, { bounds }, onOpenPost, onQuickView)
        } else {
            val pagerState = rememberPagerState { pages.size }
            HorizontalPager(pagerState, Modifier.fillMaxSize(), key = { pages[it].index }) { page ->
                PhotoGridTilePage(item, pages[page], photoGridPageKey(tileKey, page), { bounds }, onOpenPost, onQuickView)
            }
            PhotoGridPageDots(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).testTag("photo_grid_dots_$tileKey"),
            )
        }
    }
}

/** The first page keeps the key of its tile. A later page adds its position. */
internal fun photoGridPageKey(tileKey: String, page: Int): String = if (page == 0) tileKey else "$tileKey#$page"

/**
 * One image or video of a tile, at the full size of the tile frame: the sensitive cover, the media,
 * and its loading and failed states. [tileBounds] is the frame in the root, for quick view and video order.
 */
@Composable
private fun PhotoGridTilePage(
    item: PhotoGridItem,
    page: IndexedValue<Attachment>,
    pageKey: String,
    tileBounds: () -> Rect,
    onOpenPost: (OwnedPost) -> Unit,
    onQuickView: ((PhotoQuickViewTarget) -> Unit)?,
) {
    val attachment = page.value
    val reveals = currentPostReveals()
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
    val revealed = !attachment.sensitive || reveals.mediaRevealed(item.ownedPost)
    val decision = remember(attachment, revealed) {
        MediaRequestPolicy.resolve(
            attachment = attachment,
            role = MediaRequestRole.Preview,
            revealed = revealed,
            explicitlyOpened = false,
            videoFrame = true,
        )
    }
    val tileDescription = if (revealed) {
        stringResource(R.string.post_open)
    } else {
        stringResource(R.string.a11y_sensitive_media, page.index + 1)
    }
    // A tap on a failed tile raises this counter, which makes the tile ask for its image again.
    var attempt by remember(attachment) { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val quickViewLabel = stringResource(R.string.photo_quick_view_open)
    val isVideo = attachment.kind == MediaKind.Video
    // Quick view is an image pager, so a video page keeps tap-to-open only.
    val openQuickView: ((PhotoQuickViewDrag?) -> Unit)? = onQuickView?.takeUnless { isVideo }?.let { open ->
        { drag ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            open(PhotoQuickViewTarget(item.ownedPost, page.index, tileBounds(), drag))
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (revealed) {
                    Modifier
                        .photoQuickViewGesture(enabled = openQuickView != null, origin = { tileBounds().topLeft }) {
                            openQuickView?.invoke(it)
                        }
                        .clickable { onOpenPost(item.ownedPost) }
                } else {
                    Modifier
                },
            )
            .testTag("photo_grid_tile_$pageKey")
            .semantics {
                contentDescription = tileDescription
                role = Role.Button
                if (revealed && openQuickView != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(quickViewLabel) { openQuickView(null); true },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The decode size follows the width the tile actually received.
        val decodeWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val decodeHeightPx = constraints.maxHeight.coerceAtLeast(1)
        val imageRequest = remember(item, attachment, decision, decodeWidthPx, decodeHeightPx, attempt) {
            (decision as? MediaRequestDecision.Request)?.let { request ->
                mediaImageLoader.request(
                    context = context,
                    decision = request,
                    accountIdentity = item.ownedPost.fetchedBy.toString(),
                    postIdentity = "${item.ownedPost.post.id.connection}/${item.ownedPost.post.id.value}",
                    attachment = attachment,
                    attachmentIndex = page.index,
                    decodeWidthPx = decodeWidthPx,
                    decodeHeightPx = decodeHeightPx,
                ).newBuilder().setParameter("retry", attempt, memoryCacheKey = null).build()
            }
        }
        val painter = imageRequest?.let { rememberAsyncImagePainter(it, mediaImageLoader.imageLoader) }
        if (!revealed) {
            SensitiveMediaTile(onReveal = { reveals.revealMedia(item.ownedPost) })
        } else if (isVideo) {
            VideoTileContent(
                tileKey = pageKey,
                attachment = attachment,
                poster = painter,
                posterDescription = attachment.description.orEmpty(),
                contentVisible = revealed,
                decodeWidthPx = decodeWidthPx,
                order = { tileBounds().top.toInt() },
                modifier = Modifier.fillMaxSize(),
                placeholder = {},
            )
        } else if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // Over the neutral surface: a quiet indicator while loading, a retry control after a failure.
        if (revealed && painter != null) {
            PhotoGridTileStateLayer(phase = tilePhase(painter.state), onRetry = { attempt++ })
        }
    }
}

/**
 * Post actions the grid offers on its cards. Favorite shows the card button when the source allows it;
 * [onQuickView] enables press and hold. The defaults offer neither.
 */
data class PhotoGridCardActions(
    val availableActions: Set<PostAction> = emptySet(),
    val favouriteArtworkStyle: FavouriteArtworkStyle = FavouriteArtworkStyle.Heart,
    val onFavourite: (OwnedPost) -> Unit = {},
    val onQuickView: ((PhotoQuickViewTarget) -> Unit)? = null,
)

/** Per-card presentation: the favorite button, the footer insets next to a rail, and the quick-view opener. */
internal data class PhotoGridCardChrome(
    val favourite: PhotoGridFavourite? = null,
    val leftRailInset: Dp = 0.dp,
    val rightRailInset: Dp = 0.dp,
    val onQuickView: ((PhotoQuickViewTarget) -> Unit)? = null,
)

/** How the card renders and invokes the favorite button; null means the source offers none. */
internal data class PhotoGridFavourite(
    val style: FavouriteArtworkStyle,
    val onClick: (OwnedPost) -> Unit,
)

internal val PhotoGridGap = 8.dp
internal val PhotoGridOuterPadding = 8.dp
private val PhotoGridCardInset = 4.dp
private val PhotoGridImageShape = RoundedCornerShape(16.dp)
private val PhotoGridFooterHeight = 64.dp
private val PhotoGridCaptionHeight = 20.dp
private val PhotoGridFavouriteTarget = 40.dp

/** First non-blank line of the post text, else the image alt text, else null. Covered cards have none. */
internal fun photoGridCaption(item: PhotoGridItem): String? {
    if (item.hiddenByRules || item.collapsedWarning != null) return null
    val firstLine = item.ownedPost.post.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    return firstLine ?: item.attachment.description?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * One grid entry: inset image area above a fixed-height footer. The fixed footer keeps lane height
 * a function of the image ratio alone. The rail insets push the footer controls away from a floating
 * rail that the card surface underlaps on that physical side.
 */
@Composable
private fun PhotoGridCard(
    item: PhotoGridItem,
    modifier: Modifier,
    favourite: PhotoGridFavourite?,
    leftRailInset: Dp,
    rightRailInset: Dp,
    caption: String?,
    showMeta: Boolean,
    imageArea: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(BeelineNestedSurfaceShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.padding(PhotoGridCardInset)) { imageArea() }
        Column(
            Modifier
                .fillMaxWidth()
                .height(PhotoGridFooterHeight)
                .absolutePadding(left = 8.dp + leftRailInset, right = 4.dp + rightRailInset),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.fillMaxWidth().height(PhotoGridCaptionHeight), contentAlignment = Alignment.CenterStart) {
                if (caption != null) {
                    InlineEmojiText(
                        text = caption,
                        emoji = item.ownedPost.post.emoji,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("photo_grid_caption_${photoGridItemKey(item)}"),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().height(PhotoGridFavouriteTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showMeta) {
                    val author = item.ownedPost.post.author
                    AccountAvatar(author, Modifier.size(20.dp), exposeSemantics = false)
                    AccountDisplayName(
                        author,
                        Modifier.weight(1f).padding(start = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (favourite != null) {
                        val label = stringResource(
                            if (item.ownedPost.post.favourited) R.string.post_action_unfavorite else R.string.post_action_favorite,
                        )
                        IconButton(
                            onClick = { favourite.onClick(item.ownedPost) },
                            modifier = Modifier.size(PhotoGridFavouriteTarget)
                                .testTag("photo_grid_favourite_${photoGridItemKey(item)}"),
                        ) {
                            Icon(
                                favouriteIconFor(item.ownedPost, favourite.style),
                                contentDescription = label,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Stands in for a tile whose content warning is collapsed. A tap reveals the post in place. */
@Composable
private fun PhotoGridWarningTile(
    item: PhotoGridItem,
    warningText: String,
    modifier: Modifier,
    onReveal: () -> Unit,
) {
    val warning = warningText.ifBlank { stringResource(R.string.content_warning) }
    val description = stringResource(R.string.a11y_content_warning_tile, warning)
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onReveal)
            .testTag("photo_grid_tile_${photoGridItemKey(item)}")
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            warning,
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
