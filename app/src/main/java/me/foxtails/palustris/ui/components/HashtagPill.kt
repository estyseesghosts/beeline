package me.foxtails.palustris.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.ui.AppIcons

/** The one hashtag pill: the hashtag bubble and the Trending tab show a hashtag the same way. */
@Composable
internal fun HashtagPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
    maxLines: Int = 2,
) {
    PillAction(
        label = label,
        onClick = onClick,
        modifier = modifier.widthIn(max = 240.dp),
        contentDescription = contentDescription,
        maxLines = maxLines,
        leadingIcon = AppIcons.Hashtag,
    )
}
