@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui.posts

import android.text.format.DateUtils
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.theme.postAuthor
import me.foxtails.palustris.ui.theme.postBody
import me.foxtails.palustris.ui.theme.postMetadata

private val PostMetadataVerticalPadding = 2.dp * 1.06f
private val PostChromeHeight = 44.dp + (PostMetadataVerticalPadding * 2f)

internal fun isPostBodyTruncated(post: Post, presentation: PostTextPresentation, truncateBody: Boolean): Boolean =
    truncateBody && postBodyCharacterCount(presentation.visibleText, post.emoji) > POST_BODY_CHARACTER_LIMIT

@Composable
internal fun PostBodyContent(
    post: Post,
    presentation: PostTextPresentation,
    truncateBody: Boolean,
    onOpenPost: (() -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    onSearchHashtag: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bodyTruncated = isPostBodyTruncated(post, presentation, truncateBody)
    val bodyText = if (bodyTruncated) {
        truncatedPostBody(presentation.visibleText, post.emoji)
    } else {
        presentation.visibleText
    }
    val timestamp = postTimestamp(post)
    if (presentation.visibleText.isNotBlank() || timestamp != null) {
        SelectionContainer {
            Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                if (presentation.visibleText.isNotBlank()) {
                    if (bodyTruncated) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            PostBodyText(
                                text = bodyText,
                                emoji = post.emoji,
                                onTextTap = onOpenPost,
                                onOpenUrl = onOpenUrl,
                                onOpenUsername = onOpenUsername,
                                onSearchHashtag = onSearchHashtag,
                                style = MaterialTheme.typography.postBody.copy(color = MaterialTheme.colorScheme.onSurface),
                            )
                            onOpenPost?.let { ViewFullPostBubble(it) }
                        }
                    } else {
                        PostBodyText(
                            text = bodyText,
                            emoji = post.emoji,
                            onTextTap = onOpenPost,
                            onOpenUrl = onOpenUrl,
                            onOpenUsername = onOpenUsername,
                            onSearchHashtag = onSearchHashtag,
                            style = MaterialTheme.typography.postBody.copy(color = MaterialTheme.colorScheme.onSurface),
                        )
                    }
                }
                timestamp?.let {
                    if (presentation.visibleText.isNotBlank()) Spacer(Modifier.height(2.dp))
                    val timeDescription = stringResource(R.string.post_time)
                    Text(
                        it,
                        modifier = Modifier.semantics { contentDescription = timeDescription },
                        style = MaterialTheme.typography.postMetadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PostBodyText(
    text: String,
    emoji: Map<String, CustomEmoji>,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.postBody,
    onTextTap: (() -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    onSearchHashtag: ((String) -> Unit)? = null,
) {
    InlineEmojiText(
        text = text,
        emoji = emoji,
        modifier = modifier,
        style = style,
        onTextTap = onTextTap,
        enableInlineEntities = true,
        onOpenUrl = onOpenUrl,
        onOpenUsername = onOpenUsername,
        onSearchHashtag = onSearchHashtag,
    )
}

@Composable
internal fun PostMetadataRow(
    post: Post,
    filteredHashtags: List<String>,
    onOpenProfile: (() -> Unit)?,
    onSearchHashtag: ((String) -> Unit)?,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    postOwned: OwnedPost? = null,
) {
    val profileInteractionSource = remember { MutableInteractionSource() }
    val metadataDescription = stringResource(R.string.post_metadata)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = PostMetadataVerticalPadding)
            .heightIn(min = PostChromeHeight).semantics { contentDescription = metadataDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).then(onOpenProfile?.let { callback ->
                Modifier.springPress(profileInteractionSource).clickable(
                    interactionSource = profileInteractionSource,
                    indication = LocalIndication.current,
                    onClick = callback,
                )
            } ?: Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(post.author, Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            AccountDisplayName(post.author, style = MaterialTheme.typography.postAuthor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (filteredHashtags.isNotEmpty() && (onSearchHashtag != null || onOpenHashtagBubble != null)) {
            Spacer(Modifier.width(4.dp))
            FilteredHashtagSummary(filteredHashtags) { bounds ->
                if (onOpenHashtagBubble != null && postOwned != null) {
                    onOpenHashtagBubble(postOwned, filteredHashtags, bounds)
                } else {
                    onSearchHashtag?.invoke(filteredHashtags.first())
                }
            }
        }
    }
}

@Composable
private fun ViewFullPostBubble(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val description = stringResource(R.string.post_view_full)
    Surface(
        modifier = Modifier.height(32.dp).widthIn(max = 124.dp)
            .springPress(interactionSource, pressedScale = LocalPalustrisMotionScheme.current.compactPressedScale)
            .clickable(interactionSource, LocalIndication.current, onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(stringResource(R.string.post_view_full), Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

internal fun postTimestamp(post: Post): String? = if (post.publishedAtEpochMillis > 0) {
    DateUtils.getRelativeTimeSpanString(post.publishedAtEpochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
} else null

@Composable
private fun FilteredHashtagSummary(hashtags: List<String>, onOpen: (Rect) -> Unit) {
    val label = if (hashtags.size == 1) hashtags.first() else "${hashtags.first()} +${hashtags.size - 1}"
    val collapsedDescription = stringResource(R.string.post_action_bubble_collapsed)
    val hashtagDescription = hashtagSummaryDescription(hashtags)
    val summaryMinHeight = 32.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    // This local state anchors the popup only; it owns no session, network, or post authority.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Surface(
            modifier = Modifier.widthIn(max = 124.dp).heightIn(min = summaryMinHeight).onGloballyPositioned { bounds = it.boundsInWindow() }
                .springPress(interactionSource, pressedScale = LocalPalustrisMotionScheme.current.compactPressedScale)
                .clickable(interactionSource, LocalIndication.current) { onOpen(bounds) }
                .semantics { contentDescription = hashtagDescription; role = Role.Button; stateDescription = collapsedDescription },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(label, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun hashtagSummaryDescription(hashtags: List<String>): String {
    if (hashtags.size == 1) return stringResource(R.string.a11y_hashtag_summary_one, hashtags.first())
    val joined = hashtags.dropLast(1).joinToString(", ") + " and " + hashtags.last()
    return stringResource(R.string.a11y_hashtag_summary_many, hashtags.size, joined)
}
