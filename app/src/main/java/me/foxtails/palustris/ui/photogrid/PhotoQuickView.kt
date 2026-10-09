package me.foxtails.palustris.ui.photogrid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.withTimeoutOrNull
import me.foxtails.palustris.R
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.FavouriteArtworkStyle
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestRole
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.components.BeelineNestedSurfaceShape
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.posts.actionsForPost
import me.foxtails.palustris.ui.posts.favouriteIconFor

/**
 * The image card a press and hold opened. It names the post, the attachment, and the card bounds.
 * The post carries the owning account and session revision, so a stale target is rejected by the
 * overlay owner instead of acting for the wrong account.
 */
data class PhotoQuickViewTarget(
    val ownedPost: OwnedPost,
    val attachmentIndex: Int,
    val anchorBounds: Rect,
    /** The press that opened the quick-view while it is still down; null when opened without a press. */
    val drag: PhotoQuickViewDrag? = null,
)

/**
 * The finger that opened the quick-view, tracked in window coordinates. The card owns the pointer,
 * so it reports here and the quick-view only reads: [pointer] while the finger moves, [releasedAt]
 * when it lifts.
 */
class PhotoQuickViewDrag {
    var pointer by mutableStateOf<Offset?>(null)
        internal set
    var releasedAt by mutableStateOf<Offset?>(null)
        internal set
}

/**
 * Detects a press and hold, opens the quick-view with a [PhotoQuickViewDrag], and keeps following the
 * same finger until it lifts. Observes in the initial pass and consumes the press once it counts as
 * a long press, so neither the card tap nor grid scrolling also act on it. A move past touch slop
 * before the hold completes is left to scrolling.
 */
internal fun Modifier.photoQuickViewGesture(
    enabled: Boolean,
    origin: () -> Offset,
    onOpen: (PhotoQuickViewDrag) -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var cancelled = false
        val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (!cancelled) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                cancelled = change == null || change.changedToUp() || change.isConsumed ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
        } == null
        if (cancelled || !timedOut) return@awaitEachGesture
        val drag = PhotoQuickViewDrag()
        onOpen(drag)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            event.changes.forEach { it.consume() }
            if (change == null) break
            val at = origin() + change.position
            if (change.changedToUp() || !change.pressed) {
                drag.releasedAt = at
                break
            }
            drag.pointer = at
        }
    }
}

/** One menu entry the quick-view can offer for a post, in display order. */
internal enum class PhotoQuickViewEntry { Favourite, Heart, React, Reply, Repost, Share }

/**
 * Entries for [post] under the source's [availableActions]. A source with both favorite and
 * reactions shows "Heart" and "React"; a favorite-only source shows one "Favourite"; Share is
 * always present.
 */
internal fun photoQuickViewEntries(availableActions: Set<PostAction>): List<PhotoQuickViewEntry> = buildList {
    val reacts = PostAction.React in availableActions
    if (PostAction.Favorite in availableActions) add(if (reacts) PhotoQuickViewEntry.Heart else PhotoQuickViewEntry.Favourite)
    if (reacts) add(PhotoQuickViewEntry.React)
    if (PostAction.Reply in availableActions) add(PhotoQuickViewEntry.Reply)
    if (PostAction.Reshare in availableActions) add(PhotoQuickViewEntry.Repost)
    add(PhotoQuickViewEntry.Share)
}

/**
 * Press-and-hold preview: a scrim, the author and full image, and the action menu.
 *
 * Owns only whether the repost choice is awaiting confirmation. Every action closes the quick-view
 * through [onDismiss] first, so only one surface (this one, the reaction bubble, or the share
 * sheet) is open at a time.
 */
@Composable
internal fun PhotoQuickView(
    target: PhotoQuickViewTarget,
    availableActions: Set<PostAction>,
    favouriteArtworkStyle: FavouriteArtworkStyle,
    onDismiss: () -> Unit,
    onFavourite: (OwnedPost) -> Unit,
    onReact: (OwnedPost, Rect) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onRepost: (OwnedPost) -> Unit,
    onShare: (OwnedPost, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownedPost = target.ownedPost
    val post = ownedPost.post
    val entries = photoQuickViewEntries(actionsForPost(availableActions, post))
    var confirmingRepost by remember(target) { mutableStateOf(false) }
    var menuBounds by remember(target) { mutableStateOf(Rect.Zero) }
    val rows = remember(target) { QuickViewRows(target.drag) }
    // Lifting over an entry runs it; lifting anywhere else leaves the quick-view open.
    LaunchedEffect(target.drag?.releasedAt) { target.drag?.releasedAt?.let(rows::activateAt) }
    val title = stringResource(R.string.photo_quick_view_title)
    val reducedMotion = LocalPalustrisMotionScheme.current.reducedMotion
    val visibleState = remember(target) { MutableTransitionState(reducedMotion).apply { targetState = true } }
    BackHandler(onBack = onDismiss)
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.photo_quick_view_close),
                onClick = onDismiss,
            )
            .semantics { paneTitle = title }
            .testTag("photo_quick_view"),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = if (reducedMotion) fadeIn() else fadeIn() + scaleIn(initialScale = 0.9f),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val previewMaxHeight = maxHeight * 0.5f
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 24.dp).widthIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The card absorbs taps so only the scrim dismisses.
                    Surface(
                        shape = BeelineNestedSurfaceShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .testTag("photo_quick_view_preview"),
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AccountAvatar(post.author, Modifier.size(28.dp), exposeSemantics = false)
                                AccountDisplayName(
                                    post.author,
                                    Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            PhotoQuickViewImage(target, previewMaxHeight)
                        }
                    }
                    Surface(
                        shape = BeelineNestedSurfaceShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.widthIn(min = 240.dp)
                            .onGloballyPositioned { menuBounds = it.boundsInRoot() }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .testTag("photo_quick_view_menu"),
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            if (confirmingRepost) {
                                val label = stringResource(
                                    if (post.reposted) R.string.post_action_undo_repost else R.string.post_action_repost,
                                )
                                QuickViewRow(rows, AppIcons.Repost, label, "photo_quick_view_repost_confirm") {
                                    onDismiss()
                                    onRepost(ownedPost)
                                }
                                QuickViewRow(rows, AppIcons.More, stringResource(R.string.dialog_cancel), "photo_quick_view_repost_cancel") {
                                    confirmingRepost = false
                                }
                            } else {
                                entries.forEach { entry ->
                                    when (entry) {
                                        PhotoQuickViewEntry.Favourite -> QuickViewRow(rows,
                                            favouriteIconFor(ownedPost, favouriteArtworkStyle),
                                            stringResource(
                                                if (post.favourited) R.string.post_action_unfavorite else R.string.post_action_favorite,
                                            ),
                                            "photo_quick_view_favourite",
                                        ) { onDismiss(); onFavourite(ownedPost) }
                                        PhotoQuickViewEntry.Heart -> QuickViewRow(rows,
                                            favouriteIconFor(ownedPost, favouriteArtworkStyle),
                                            stringResource(
                                                if (favouriteArtworkStyle == FavouriteArtworkStyle.Star) {
                                                    R.string.photo_quick_view_star
                                                } else {
                                                    R.string.photo_quick_view_heart
                                                },
                                            ),
                                            "photo_quick_view_heart",
                                        ) { onDismiss(); onFavourite(ownedPost) }
                                        PhotoQuickViewEntry.React -> QuickViewRow(rows,
                                            AppIcons.More,
                                            stringResource(R.string.photo_quick_view_react),
                                            "photo_quick_view_react",
                                        ) { onDismiss(); onReact(ownedPost, menuBounds) }
                                        PhotoQuickViewEntry.Reply -> QuickViewRow(rows,
                                            AppIcons.Reply,
                                            stringResource(R.string.post_action_reply),
                                            "photo_quick_view_reply",
                                        ) { onDismiss(); onReply(ownedPost) }
                                        PhotoQuickViewEntry.Repost -> QuickViewRow(rows,
                                            AppIcons.Repost,
                                            stringResource(if (post.reposted) R.string.post_action_undo_repost else R.string.post_action_repost),
                                            "photo_quick_view_repost",
                                        ) { confirmingRepost = true }
                                        PhotoQuickViewEntry.Share -> QuickViewRow(rows,
                                            AppIcons.Share,
                                            stringResource(R.string.post_action_share),
                                            "photo_quick_view_share",
                                        ) { onDismiss(); onShare(ownedPost, menuBounds) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoQuickViewImage(target: PhotoQuickViewTarget, heightCap: androidx.compose.ui.unit.Dp) {
    val attachment = target.ownedPost.post.attachments.getOrNull(target.attachmentIndex) ?: return
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
    val width = attachment.width ?: attachment.previewWidth
    val height = attachment.height ?: attachment.previewHeight
    val ratio = if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else 4f / 3f
    val decision = remember(attachment) {
        MediaRequestPolicy.resolve(
            attachment = attachment,
            role = MediaRequestRole.Preview,
            revealed = true,
            explicitlyOpened = false,
        )
    }
    val painter = (decision as? MediaRequestDecision.Request)?.let { request ->
        val imageRequest = remember(target, request) {
            mediaImageLoader.request(
                context = context,
                decision = request,
                accountIdentity = target.ownedPost.fetchedBy.toString(),
                postIdentity = "${target.ownedPost.post.id.connection}/${target.ownedPost.post.id.value}",
                attachment = attachment,
                attachmentIndex = target.attachmentIndex,
                decodeWidthPx = 1080,
                decodeHeightPx = 1080,
            )
        }
        rememberAsyncImagePainter(imageRequest, mediaImageLoader.imageLoader)
    }
    // Width follows the height cap so a tall image shrinks instead of pushing the header off screen.
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, heightCap * ratio)
        Box(
            Modifier.width(width)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag("photo_quick_view_image"),
        ) {
            if (painter != null) {
                Image(painter, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}

/** Where each menu entry is and what it does, so a finger lifted over one can run it. */
private class QuickViewRows(private val drag: PhotoQuickViewDrag?) {
    val bounds = mutableStateMapOf<String, Rect>()
    val clicks = HashMap<String, () -> Unit>()

    fun isUnderPointer(tag: String): Boolean {
        val at = drag?.pointer ?: return false
        return drag.releasedAt == null && bounds[tag]?.contains(at) == true
    }

    fun activateAt(at: Offset) {
        val tag = bounds.entries.firstOrNull { it.value.contains(at) }?.key ?: return
        clicks[tag]?.invoke()
    }
}

@Composable
private fun QuickViewRow(rows: QuickViewRows, icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    SideEffect { rows.clicks[tag] = onClick }
    DisposableEffect(tag) {
        onDispose {
            rows.bounds.remove(tag)
            rows.clicks.remove(tag)
        }
    }
    val highlighted = rows.isUnderPointer(tag)
    Row(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { rows.bounds[tag] = it.boundsInRoot() }
            .background(if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
