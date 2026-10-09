package me.foxtails.palustris.ui.photogrid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
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
)

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
                                QuickViewRow(AppIcons.Repost, label, "photo_quick_view_repost_confirm") {
                                    onDismiss()
                                    onRepost(ownedPost)
                                }
                                QuickViewRow(AppIcons.More, stringResource(R.string.dialog_cancel), "photo_quick_view_repost_cancel") {
                                    confirmingRepost = false
                                }
                            } else {
                                entries.forEach { entry ->
                                    when (entry) {
                                        PhotoQuickViewEntry.Favourite -> QuickViewRow(
                                            favouriteIconFor(ownedPost, favouriteArtworkStyle),
                                            stringResource(
                                                if (post.favourited) R.string.post_action_unfavorite else R.string.post_action_favorite,
                                            ),
                                            "photo_quick_view_favourite",
                                        ) { onDismiss(); onFavourite(ownedPost) }
                                        PhotoQuickViewEntry.Heart -> QuickViewRow(
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
                                        PhotoQuickViewEntry.React -> QuickViewRow(
                                            AppIcons.More,
                                            stringResource(R.string.photo_quick_view_react),
                                            "photo_quick_view_react",
                                        ) { onDismiss(); onReact(ownedPost, menuBounds) }
                                        PhotoQuickViewEntry.Reply -> QuickViewRow(
                                            AppIcons.Reply,
                                            stringResource(R.string.post_action_reply),
                                            "photo_quick_view_reply",
                                        ) { onDismiss(); onReply(ownedPost) }
                                        PhotoQuickViewEntry.Repost -> QuickViewRow(
                                            AppIcons.Repost,
                                            stringResource(if (post.reposted) R.string.post_action_undo_repost else R.string.post_action_repost),
                                            "photo_quick_view_repost",
                                        ) { confirmingRepost = true }
                                        PhotoQuickViewEntry.Share -> QuickViewRow(
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

@Composable
private fun QuickViewRow(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
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
