package me.foxtails.palustris.ui.emoji

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import me.foxtails.palustris.R
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostMention
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.links.rememberLinkOpener
import me.foxtails.palustris.ui.motion.springClickable

/**
 * One annotated rich-text renderer for emoji-aware text. Post callers can opt into inline
 * entity bubbles; other text surfaces keep their existing plain/Markdown presentation.
 */
@Composable
fun InlineEmojiText(
    text: String,
    emoji: Map<String, CustomEmoji>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    enableInlineEntities: Boolean = false,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    onSearchHashtag: ((String) -> Unit)? = null,
    onTextTap: (() -> Unit)? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    mentions: List<PostMention> = emptyList(),
    onOpenProfile: ((Account) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resolvedStyle = style.copy(
        color = if (style.color == Color.Unspecified) LocalContentColor.current else style.color,
    )
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val model = remember(text, emoji, mentions) { EmojiTextParser.parse(text, emoji, mentions) }
    val mentionProfiles = LocalMentionProfiles.current
    val mentionsShown = remember(model, enableInlineEntities) {
        if (enableInlineEntities) model.segments.filterIsInstance<RichTextSegment.Username>().mapNotNull { it.mention } else emptyList()
    }
    // A row asks for a profile only while it shows the mention. Reading the state here recomposes the row when it resolves.
    LaunchedEffect(mentionsShown, mentionProfiles) { mentionsShown.forEach(mentionProfiles::request) }
    val mentionStates = mentionsShown.associate { it.handle to mentionProfiles.profile(it) }
    val bubbleTextStyle = MaterialTheme.typography.labelMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val linkOpener = rememberLinkOpener()
    val inlineContent = remember(model, style.fontSize, enableInlineEntities, onOpenUrl, onOpenUsername, onOpenProfile, onSearchHashtag, mentionStates, context, linkOpener) {
        buildInlineContent(
            model = model,
            emSize = style.fontSize,
            enableInlineEntities = enableInlineEntities,
            textMeasurer = textMeasurer,
            density = density,
            bubbleTextStyle = bubbleTextStyle,
            context = context,
            onOpenUrl = { url -> onOpenUrl?.invoke(url) ?: linkOpener(url) },
            onOpenUsername = onOpenUsername,
            onOpenProfile = onOpenProfile,
            mentionStates = mentionStates,
            onSearchHashtag = onSearchHashtag,
        )
    }
    val annotated = remember(model, linkStyle, enableInlineEntities) {
        androidx.compose.ui.text.buildAnnotatedString {
            model.segments.forEach { segment ->
                appendSegment(this, model.source, segment, linkStyle, enableInlineEntities)
            }
        }
    }
    val hasInteractiveEntities = remember(annotated, enableInlineEntities) {
        annotated.getStringAnnotations(URL_ANNOTATION, 0, annotated.length).isNotEmpty() ||
            (enableInlineEntities && annotated.getStringAnnotations(USERNAME_ANNOTATION, 0, annotated.length).isNotEmpty()) ||
            (enableInlineEntities && annotated.getStringAnnotations(HASHTAG_ANNOTATION, 0, annotated.length).isNotEmpty())
    }
    val currentOnTextTap by rememberUpdatedState(onTextTap)
    val textTapSemantics = if (onTextTap != null) {
        Modifier.semantics {
            onClick {
                onTextTap()
                true
            }
        }
    } else {
        Modifier
    }
    if (!hasInteractiveEntities) {
        BasicText(
            text = annotated,
            modifier = modifier
                .then(textTapSemantics)
                .then(
                    if (onTextTap != null) {
                        Modifier.pointerInput(annotated) {
                            detectTapGestures { currentOnTextTap?.invoke() }
                        }
                    } else {
                        Modifier
                    },
                ),
            style = resolvedStyle,
            maxLines = maxLines,
            overflow = overflow,
            inlineContent = inlineContent,
            onTextLayout = onTextLayout,
        )
        return
    }

    var layoutResult: TextLayoutResult? by remember { mutableStateOf(null) }
    BasicText(
        text = annotated,
        modifier = modifier.then(textTapSemantics).pointerInput(annotated, enableInlineEntities) {
            detectTapGestures { offset ->
                val position = layoutResult?.getOffsetForPosition(offset) ?: return@detectTapGestures
                annotated.getStringAnnotations(URL_ANNOTATION, position, position)
                    .firstOrNull()?.let { annotation ->
                        onOpenUrl?.invoke(annotation.item) ?: linkOpener(annotation.item)
                        return@detectTapGestures
                    }
                if (enableInlineEntities) {
                    annotated.getStringAnnotations(USERNAME_ANNOTATION, position, position)
                        .firstOrNull()?.let {
                            onOpenUsername?.invoke(it.item)
                            return@detectTapGestures
                        }
                    annotated.getStringAnnotations(HASHTAG_ANNOTATION, position, position)
                        .firstOrNull()?.let {
                            onSearchHashtag?.invoke(it.item)
                            return@detectTapGestures
                        }
                }
                currentOnTextTap?.invoke()
            }
        },
        style = resolvedStyle,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent,
        onTextLayout = {
            layoutResult = it
            onTextLayout?.invoke(it)
        },
    )
}

@Composable
fun AccountDisplayName(
    account: Account,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    InlineEmojiText(account.displayName, account.emoji, modifier, style, maxLines, overflow)
}

@Composable
fun PostText(
    post: Post,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    onSearchHashtag: ((String) -> Unit)? = null,
) {
    InlineEmojiText(
        text = post.text,
        emoji = post.emoji,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        enableInlineEntities = true,
        onOpenUrl = onOpenUrl,
        onOpenUsername = onOpenUsername,
        onSearchHashtag = onSearchHashtag,
    )
}

/**
 * Shared custom-emoji image cell for chips and notification surfaces. Renders the
 * fallback text until the image loads and whenever the request fails.
 */
@Composable
fun CustomEmojiImage(
    emoji: CustomEmoji?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val request = emoji?.let(MediaRequestPolicy::emojiImage)
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            fallbackText,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        if (emoji != null && request != null) {
            val context = LocalContext.current
            val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
            val imageRequest = remember(emoji, request, mediaImageLoader) {
                mediaImageLoader.emojiRequest(
                    context = context,
                    request = request,
                    decodeSizePx = 96,
                )
            }
            AsyncImage(
                model = imageRequest,
                imageLoader = mediaImageLoader.emojiImageLoader,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
            )
        }
    }
}

private const val URL_ANNOTATION = "URL"
private const val USERNAME_ANNOTATION = "USERNAME"
private const val HASHTAG_ANNOTATION = "HASHTAG"

private fun appendSegment(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    source: String,
    segment: RichTextSegment,
    linkStyle: SpanStyle,
    enableInlineEntities: Boolean,
) {
    when (segment) {
        is RichTextSegment.Text -> builder.append(segment.text)
        is RichTextSegment.Emoji -> builder.appendInlineContent(segment.token, alternateText = segment.token)
        is RichTextSegment.Link -> {
            if (enableInlineEntities) {
                builder.pushStringAnnotation(URL_ANNOTATION, segment.target)
                builder.appendInlineContent(inlineContentId(segment), alternateText = segment.displayLabel)
                builder.pop()
            } else if (segment.plainUrl) {
                builder.append(source.substring(segment.range))
            } else {
                builder.pushStringAnnotation(URL_ANNOTATION, segment.target)
                builder.pushStyle(linkStyle)
                segment.label.forEach { inner -> appendSegment(builder, source, inner, linkStyle, false) }
                builder.pop()
                builder.pop()
            }
        }
        is RichTextSegment.Username -> {
            if (enableInlineEntities) {
                builder.pushStringAnnotation(USERNAME_ANNOTATION, segment.target)
                // A mention copies as its complete handle, whatever the bubble shows.
                builder.appendInlineContent(
                    inlineContentId(segment),
                    alternateText = if (segment.mention != null) segment.target else segment.displayLabel,
                )
                builder.pop()
            } else {
                builder.append(source.substring(segment.range))
            }
        }
        is RichTextSegment.Hashtag -> {
            if (enableInlineEntities) {
                builder.pushStringAnnotation(HASHTAG_ANNOTATION, segment.target)
                builder.appendInlineContent(inlineContentId(segment), alternateText = segment.displayLabel)
                builder.pop()
            } else {
                builder.append(source.substring(segment.range))
            }
        }
    }
}

private fun buildInlineContent(
    model: RichTextModel,
    emSize: TextUnit,
    enableInlineEntities: Boolean,
    textMeasurer: TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    bubbleTextStyle: TextStyle,
    context: android.content.Context,
    onOpenUrl: (String) -> Unit,
    onOpenUsername: ((String) -> Unit)?,
    onOpenProfile: ((Account) -> Unit)?,
    mentionStates: Map<String, MentionProfile?>,
    onSearchHashtag: ((String) -> Unit)?,
): Map<String, androidx.compose.foundation.text.InlineTextContent> = buildMap {
    fun addSegments(segments: List<RichTextSegment>) {
        segments.forEach { segment ->
            when (segment) {
                is RichTextSegment.Emoji -> put(
                    segment.token,
                    androidx.compose.foundation.text.InlineTextContent(
                        placeholder = androidx.compose.ui.text.Placeholder(
                            width = emSize,
                            height = emSize,
                            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
                        ),
                    ) { EmojiInlineContent(segment.token, segment.emoji, emSize) },
                )
                is RichTextSegment.Link -> {
                    if (enableInlineEntities) {
                        put(
                            inlineContentId(segment),
                            entityContent(
                                label = segment.displayLabel,
                                description = context.getString(R.string.a11y_link_description, segment.displayLabel),
                                emSize = emSize,
                                width = entityWidthEm(segment.displayLabel, true, DisplayIsolation.LeftToRight, emSize, textMeasurer, density, bubbleTextStyle),
                                onClick = { onOpenUrl(segment.target) },
                                leadingIcon = AppIcons.LinkBeeline,
                                isolation = DisplayIsolation.LeftToRight,
                            ),
                        )
                    } else {
                        addSegments(segment.label)
                    }
                }
                is RichTextSegment.Username -> if (enableInlineEntities) {
                    val mention = segment.mention
                    val account = mention?.let { (mentionStates[it.handle] as? MentionProfile.Resolved)?.account }
                    // Before the profile resolves, or after a failure, the bubble shows the handle and a person icon.
                    val label = account?.let(::mentionDisplayName) ?: segment.displayLabel
                    val openAccount = account?.let { resolved -> onOpenProfile?.let { open -> { open(resolved) } } }
                    val labelWidth = entityWidthEm(label, mention != null, DisplayIsolation.FirstStrong, emSize, textMeasurer, density, bubbleTextStyle)
                    put(
                        inlineContentId(segment),
                        entityContent(
                            label = label,
                            description = if (mention != null) {
                                context.getString(R.string.a11y_mention_description, label, mention.handle)
                            } else {
                                context.getString(R.string.a11y_username_description, segment.displayLabel)
                            },
                            emSize = emSize,
                            width = if (mention != null && labelWidth.value > MENTION_MAX_WIDTH_EM) MENTION_MAX_WIDTH_EM.em else labelWidth,
                            onClick = openAccount ?: onOpenUsername?.let { callback -> { callback(segment.target) } },
                            leadingIcon = null,
                            isolation = DisplayIsolation.FirstStrong,
                            leading = if (mention != null) { size ->
                                if (account != null) {
                                    AccountAvatar(account, Modifier.size(size).testTag("entity_avatar"), exposeSemantics = false)
                                } else {
                                    Icon(AppIcons.Person, contentDescription = null, modifier = Modifier.size(size).testTag("entity_icon"))
                                }
                            } else {
                                null
                            },
                        ),
                    )
                }
                is RichTextSegment.Hashtag -> if (enableInlineEntities) {
                    val hashtagLabel = segment.displayLabel.removePrefix("#")
                    put(
                        inlineContentId(segment),
                        entityContent(
                            label = hashtagLabel,
                            description = context.getString(R.string.a11y_hashtag_description, segment.displayLabel),
                            emSize = emSize,
                            width = entityWidthEm(hashtagLabel, true, DisplayIsolation.FirstStrong, emSize, textMeasurer, density, bubbleTextStyle),
                            onClick = onSearchHashtag?.let { callback -> { callback(segment.target) } },
                            leadingIcon = AppIcons.Hashtag,
                            isolation = DisplayIsolation.FirstStrong,
                        ),
                    )
                }
                is RichTextSegment.Text -> Unit
            }
        }
    }
    addSegments(model.segments)
}

private fun entityContent(
    label: String,
    description: String,
    emSize: TextUnit,
    width: TextUnit,
    onClick: (() -> Unit)?,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    isolation: DisplayIsolation,
    /** Replaces [leadingIcon] with other content of the given square size, such as an avatar. */
    leading: (@Composable (androidx.compose.ui.unit.Dp) -> Unit)? = null,
): androidx.compose.foundation.text.InlineTextContent = androidx.compose.foundation.text.InlineTextContent(
    placeholder = androidx.compose.ui.text.Placeholder(
        width = width,
        height = 1.5.em,
        placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
    ),
) {
    val iconSize = with(LocalDensity.current) {
        if (emSize == TextUnit.Unspecified) 14.dp else emSize.toDp() * .78f
    }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        // The placeholder keeps the 1.5 em line metrics. The bubble is 1 dp shorter inside it, so bubbles
        // on adjacent lines leave a gap.
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = ENTITY_VERTICAL_INSET)
            .then(
                onClick?.let { callback ->
                    Modifier.springClickable(interactionSource, role = Role.Button, onClick = callback)
                } ?: Modifier,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = ENTITY_EDGE_PADDING),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading(iconSize)
                Spacer(Modifier.width(ENTITY_ICON_GAP))
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(iconSize).testTag("entity_icon"))
                Spacer(Modifier.width(ENTITY_ICON_GAP))
            }
            androidx.compose.material3.Text(
                text = isolateDisplayDirection(label, isolation),
                modifier = Modifier.testTag("entity_label"),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val ENTITY_EDGE_PADDING = 6.dp
private val ENTITY_VERTICAL_INSET = 0.5.dp
private val ENTITY_ICON_GAP = 4.dp

/** A long display name does not fill the line: the mention bubble stops growing here and ellipsizes. */
private const val MENTION_MAX_WIDTH_EM = 14f

private enum class DisplayIsolation(val opener: Char) {
    /** Domains and addresses read left to right even inside right-to-left text. */
    LeftToRight('⁦'),

    /** Names and tags take the direction of their own first strong character. */
    FirstStrong('⁨'),
}

private const val DIRECTION_ISOLATE_CLOSE = '⁩'

/** Adds invisible isolates for display only. The tap target and copied text keep the exact original string. */
private fun isolateDisplayDirection(label: String, isolation: DisplayIsolation): String =
    "${isolation.opener}$label$DIRECTION_ISOLATE_CLOSE"

private fun entityWidthEm(
    label: String,
    hasIcon: Boolean,
    isolation: DisplayIsolation,
    emSize: TextUnit,
    textMeasurer: TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    bubbleTextStyle: TextStyle,
): TextUnit {
    val labelWidthPx = textMeasurer.measure(
        text = AnnotatedString(isolateDisplayDirection(label, isolation)),
        style = bubbleTextStyle,
        maxLines = 1,
    ).size.width.toFloat()
    val parentFontSizePx = with(density) {
        (if (emSize == TextUnit.Unspecified) 16.sp else emSize).toPx()
    }
    val horizontalPaddingPx = with(density) { (ENTITY_EDGE_PADDING * 2).toPx() }
    val iconWidthPx = if (hasIcon) {
        with(density) { (if (emSize == TextUnit.Unspecified) 14.dp else emSize.toDp() * .78f).toPx() } +
            with(density) { ENTITY_ICON_GAP.toPx() }
    } else {
        0f
    }
    return ((labelWidthPx + horizontalPaddingPx + iconWidthPx) / parentFontSizePx).em
}

private fun inlineContentId(segment: RichTextSegment): String =
    "entity-${segment.range.first}-${segment.range.last}"

@Composable
private fun EmojiInlineContent(token: String, emoji: CustomEmoji?, emSize: TextUnit) {
    val density = LocalDensity.current
    val size = with(density) { emSize.toDp() }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CustomEmojiImage(
            emoji = emoji,
            fallbackText = token,
            modifier = Modifier.size(size),
            textStyle = TextStyle(fontSize = emSize),
        )
    }
}
