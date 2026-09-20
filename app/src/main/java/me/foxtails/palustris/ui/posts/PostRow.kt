@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ContentWarningDecision
import me.foxtails.palustris.domain.ContentWarningPolicy
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.media.PostMediaCarousel
import me.foxtails.palustris.ui.motion.ExpandableContent
import me.foxtails.palustris.ui.links.ExternalLinkHandler
import me.foxtails.palustris.ui.posts.LocalPostActionOwner
import me.foxtails.palustris.ui.posts.LocalPostRepostConfirmationOwner
import me.foxtails.palustris.ui.AppIcons

private val PostMetadataVerticalPadding = 2.dp * 1.06f
private val PostChromeHeight = 44.dp + (PostMetadataVerticalPadding * 2f)

@Composable
internal fun PostRow(
    ownedPost: OwnedPost,
    presentation: PostRowPresentation,
    events: PostRowEvents,
    modifier: Modifier = Modifier,
) {
    val availableActions = presentation.availableActions
    val onReact = events.onFavourite
    val onReply = events.onReply
    val onReshare = events.onRepost
    val onBookmark = events.onBookmark
    val onReaction = events.onReaction
    val onOpenProfile = events.onOpenProfile
    val onSearchHashtag = events.onSearchHashtag
    val onOpenHashtagBubble = events.onOpenHashtagBubble
    val quoteEnabled = presentation.quoteEnabled
    val onQuote = events.onQuote
    val onOpenReactionBubble = events.onOpenReactionBubble
    val onOpenReactionPicker = events.onOpenReactionPicker
    val onOpenMedia = events.onOpenMedia
    val onOpenPost = events.onOpenPost
    val truncateBody = presentation.truncateBody
    val largeLayout = presentation.largeLayout
    val onOpenUrl = events.onOpenUrl
    val onOpenUsername = events.onOpenUsername
    val contentWarningRules = presentation.contentWarningRules
    val interactionPresentation = presentation.interactionPresentation
    val post = ownedPost.post
    val context = LocalContext.current
    val repostConfirmationOwner = LocalPostRepostConfirmationOwner.current
    var expanded by rememberSaveable(post.id.connection, post.id.value) { mutableStateOf(false) }
    val displayPresentation = remember(post.text, post.emoji) { parseHashtagBlocks(post.text, post.emoji) }
    val hashtags = remember(post.text, post.emoji) { postHashtags(post.text, post.emoji) }
    val warningDecision = remember(post.contentWarning, hashtags, contentWarningRules) {
        ContentWarningPolicy.decide(post.contentWarning, hashtags, contentWarningRules, bodyText = post.text)
    }
    val postActionOwner = LocalPostActionOwner.current
    LaunchedEffect(ownedPost.fetchedBy, post.id, ownedPost.sessionRevision, post.reposted) {
        repostConfirmationOwner.reconcile(ownedPost)
    }
    val contentVisible = warningDecision != ContentWarningDecision.Hidden &&
        (post.contentWarning == null || expanded || warningDecision == ContentWarningDecision.ExpandedByDefault)
    if (warningDecision == ContentWarningDecision.Hidden && LocalHiddenContentPresentation.current == me.foxtails.palustris.domain.HiddenContentPresentation.Remove) return
    val bodyTruncated = isPostBodyTruncated(post, displayPresentation, truncateBody)
    Column(modifier.fillMaxWidth().testTag("post_row_${post.id.value}")) {
        post.resharedBy?.let {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)) {
                AccountDisplayName(
                    it,
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Text(stringResource(R.string.post_reshared), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PostMetadataRow(
            post = post,
            filteredHashtags = displayPresentation.filteredHashtags.takeIf { contentVisible }.orEmpty(),
            onOpenProfile = onOpenProfile?.let { callback -> { callback(post.author) } },
            onSearchHashtag = onSearchHashtag,
            onOpenHashtagBubble = onOpenHashtagBubble,
            postOwned = ownedPost,
        )
        if (warningDecision == ContentWarningDecision.Hidden) {
             Text(stringResource(R.string.content_hidden_local_rule), Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (post.contentVisibility != me.foxtails.palustris.domain.PostContentVisibility.Visible) {
            Text(
                stringResource(
                    if (post.contentVisibility == me.foxtails.palustris.domain.PostContentVisibility.Hidden) {
                        R.string.post_content_unavailable
                    } else {
                        R.string.post_content_filtered
                    },
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (interactionPresentation.showInteractionSummary) {
                InteractionSummaryRow(post.interactionCounts)
            }
            return@Column
        }
        if (post.replyTo != null) Text(stringResource(R.string.post_reply), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (post.contentWarning != null) {
            InlineEmojiText(post.contentWarning.ifBlank { stringResource(R.string.content_warning) }, post.emoji, Modifier.padding(horizontal = 16.dp), MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(if (expanded) R.string.content_warning_hide else R.string.content_warning_show)) }
        }
        ExpandableContent(visible = contentVisible, modifier = Modifier.fillMaxWidth()) {
            PostBodyContent(
                post = post,
                presentation = displayPresentation,
                truncateBody = truncateBody,
                onOpenPost = onOpenPost?.let { callback -> { callback(ownedPost) } },
                onOpenUrl = onOpenUrl,
                onOpenUsername = onOpenUsername,
                onSearchHashtag = onSearchHashtag,
            )
            PostMediaCarousel(ownedPost = ownedPost, onOpenMedia = onOpenMedia)
            post.pollOptions.forEach { option ->
                Surface(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(12.dp)) {
                        InlineEmojiText(option.text, post.emoji, Modifier.weight(1f), MaterialTheme.typography.bodyMedium)
                        Text(pluralStringResource(R.plurals.post_poll_votes, option.votes, option.votes), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            post.quote?.let { quote ->
                val quoteDecision = ContentWarningPolicy.decide(
                    quote.contentWarning,
                     postHashtags(quote.text, quote.emoji),
                    contentWarningRules,
                    bodyText = quote.text,
                )
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = { ExternalLinkHandler.open(context, quote.url) }) {
                    Column(Modifier.padding(16.dp)) {
                        AccountDisplayName(quote.author, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        if (quoteDecision != ContentWarningDecision.Hidden) {
                            InlineEmojiText(
                                quote.contentWarning?.ifBlank { stringResource(R.string.content_warning) } ?: quote.text,
                                quote.emoji,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                         } else Text(stringResource(R.string.content_hidden_quoted), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.post_view_quoted), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (largeLayout && !bodyTruncated && onOpenPost != null) {
            TextButton(
                onClick = { onOpenPost(ownedPost) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.post_open))
            }
        }
        if (interactionPresentation.showInteractionSummary) {
            InteractionSummaryRow(post.interactionCounts)
        }
        if (post.reactions.any { it.count > 0 }) {
            ReactionRow(
                reactions = post.reactions,
                ownedPost = ownedPost,
                enabled = PostAction.React in availableActions,
                onReaction = onReaction,
                showReactionNumbers = interactionPresentation.showReactionNumbers,
            )
        }
        InteractionRow(
            ownedPost = ownedPost,
            availableActions = availableActions,
            onReply = onReply,
            onReact = onReact,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
            onOpenReactionBubble = { target, bounds ->
                onOpenReactionBubble(target, bounds)
            },
            onOpenReactionPicker = onOpenReactionPicker,
            pendingRepost = repostConfirmationOwner.pending,
            onRepostConfirmationRequest = repostConfirmationOwner::request,
            onRepostConfirmationDismiss = repostConfirmationOwner::dismiss,
            onRepostConfirmationConfirm = { target -> repostConfirmationOwner.confirm(target, onReshare) },
            onShare = { target, bounds -> postActionOwner?.open(target, bounds) },
           )
      }
 }

internal fun actionsForPost(availableActions: Set<PostAction>, post: Post): Set<PostAction> =
    if (post.availableActions.isEmpty()) availableActions else availableActions.intersect(post.availableActions)

internal fun Post.hasVisibleInteractionSelection(): Boolean =
    favourited || myReaction != null || selectedReactions.isNotEmpty()

/** Stars show Mastodon favourite state. Hearts show like and reaction state on all other services. */
internal fun favouriteIconFor(ownedPost: OwnedPost): ImageVector {
    val selected = ownedPost.post.hasVisibleInteractionSelection()
    return if (ownedPost.fetchedBy.connection.protocol == Protocol.MASTODON) {
        if (selected) AppIcons.FilledStar else AppIcons.HollowStar
    } else {
        if (selected) AppIcons.FilledHeart else AppIcons.HollowHeart
    }
}
