package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import me.foxtails.palustris.domain.PostScript
import me.foxtails.palustris.ui.theme.LocalPostScriptScope
import me.foxtails.palustris.ui.theme.PostScriptScope

/**
 * The hashtag bubble of one post. The bubble opens in an overlay outside the post row, so it does
 * not inherit the script scope of the row. This wrapper gives it the script of its post, and each
 * hashtag pill then renders in the font of the post.
 */
@Composable
internal fun PostHashtagBubble(
    script: PostScript?,
    hashtags: List<String>,
    maxHeight: Dp,
    openingKey: Long,
    side: BubblePlacement,
    onSelected: (String) -> Unit,
) {
    CompositionLocalProvider(LocalPostScriptScope provides PostScriptScope(script)) {
        HashtagBubble(hashtags, maxHeight, openingKey, side, onSelected)
    }
}
