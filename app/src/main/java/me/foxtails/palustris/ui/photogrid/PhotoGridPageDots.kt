package me.foxtails.palustris.ui.photogrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** A tile shows at most this many dots. A post with more pages still shows this many. */
internal const val PHOTO_GRID_MAX_PAGE_DOTS = 3

/** Two dots for two pages, three dots for three or more. A tile with one page has no dots. */
internal fun photoGridDotCount(pageCount: Int): Int = pageCount.coerceIn(0, PHOTO_GRID_MAX_PAGE_DOTS)

/**
 * The dot that marks [page]. With more than three pages the first dot is the first page, the last
 * dot is the last page, and the middle dot is every page between them.
 */
internal fun photoGridActiveDot(page: Int, pageCount: Int): Int = when {
    pageCount <= PHOTO_GRID_MAX_PAGE_DOTS -> page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    page <= 0 -> 0
    page >= pageCount - 1 -> PHOTO_GRID_MAX_PAGE_DOTS - 1
    else -> 1
}

/**
 * The page indicator of a tile with several images. The dots sit directly on the photo with no
 * container. A faint dark ring keeps a dot visible on a bright photo. The dots take no input.
 */
@Composable
internal fun PhotoGridPageDots(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    val active = photoGridActiveDot(currentPage, pageCount)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(photoGridDotCount(pageCount)) { dot ->
            Box(
                Modifier
                    .size(6.dp)
                    .background(Color.White.copy(alpha = if (dot == active) 1f else 0.45f), CircleShape)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
                    .testTag(if (dot == active) PHOTO_GRID_ACTIVE_DOT_TAG else PHOTO_GRID_DOT_TAG),
            )
        }
    }
}

internal const val PHOTO_GRID_DOT_TAG = "photo_grid_page_dot"
internal const val PHOTO_GRID_ACTIVE_DOT_TAG = "photo_grid_page_dot_active"
