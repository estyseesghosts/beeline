package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.emoji.InlineEmojiText

/** Each step multiplies the font size by this factor before the text wraps or splits. */
private const val SHRINK_STEP = 0.92f
private const val NAME_MAX_SHRINK_STEPS = 4
private const val HANDLE_SHRINK_FACTOR = 0.85f

/**
 * Splits `@user@domain` into `@user` and `@domain` so a handle that must wrap breaks between its parts.
 * Returns null for a handle with no domain part, which has nowhere better to break.
 */
internal fun splitHandle(handle: String): List<String>? {
    val domainStart = handle.indexOf('@', startIndex = 1)
    if (domainStart <= 0) return null
    return listOf(handle.substring(0, domainStart), handle.substring(domainStart))
}

private fun TextStyle.scaledBy(factor: Float): TextStyle = copy(
    fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
)

/**
 * The profile display name. It stays on one line and shrinks a few steps when it does not fit,
 * and wraps only after the smallest step still overflows.
 */
@Composable
internal fun ProfileDisplayName(account: Account, style: TextStyle, modifier: Modifier = Modifier) {
    val text = account.displayName.ifBlank { account.handle }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.testTag("profile_display_name")) {
        var steps by remember(text, maxWidth, fontScale, style) { mutableIntStateOf(0) }
        var scale = 1f
        repeat(steps) { scale *= SHRINK_STEP }
        InlineEmojiText(
            text = text,
            emoji = account.emoji,
            style = style.scaledBy(scale),
            maxLines = if (steps >= NAME_MAX_SHRINK_STEPS) Int.MAX_VALUE else 1,
            onTextLayout = { layout ->
                if (layout.hasVisualOverflow && steps < NAME_MAX_SHRINK_STEPS) steps++
            },
        )
    }
}

/**
 * The profile handle. It stays on one line, shrinks once, then splits into the name and domain lines.
 * A split line that still does not fit shrinks once more and then ellipsizes.
 */
@Composable
internal fun ProfileHandle(handle: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val parts = remember(handle) { splitHandle(handle) }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        // 0 single line, 1 shrunk single line, 2 split lines, 3 shrunk split lines.
        var stage by remember(handle, maxWidth, fontScale, style) { mutableIntStateOf(0) }
        val split = parts != null && stage >= 2
        val shrunk = stage == 1 || stage == 3
        Text(
            text = if (split) parts.joinToString("\n") else handle,
            color = color,
            style = if (shrunk) style.scaledBy(HANDLE_SHRINK_FACTOR) else style,
            maxLines = if (split) 2 else 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (layout.hasVisualOverflow) {
                    stage = when {
                        stage == 0 -> 1
                        stage == 1 && parts != null -> 2
                        stage == 2 -> 3
                        else -> stage
                    }
                }
            },
        )
    }
}
