package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.BeelineBubbleMinHeight
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.components.FloatingControlElevation
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.photogrid.PhotoGridItem
import me.foxtails.palustris.ui.photogrid.PhotoGridTileMedia
import me.foxtails.palustris.ui.photogrid.photoGridItemKey

/** How the profile Media tab renders its posts. The other tabs always render the list. */
enum class ProfileMediaLayout { Grid, List }

/** The rows of the profile media grid. Each row holds at most [columns] tiles. */
internal data class ProfileMediaGrid(val rows: List<List<PhotoGridItem>>, val columns: Int)

internal fun profileMediaGrid(items: List<PhotoGridItem>, columns: Int): ProfileMediaGrid =
    ProfileMediaGrid(items.chunked(columns), columns)

private val ProfileMediaGridGap = 4.dp
private val ProfileMediaGridMinTile = 128.dp
private const val PROFILE_MEDIA_GRID_MIN_COLUMNS = 3
private val ProfileMediaTileShape = RoundedCornerShape(12.dp)

/** Height of [ProfileMediaLayoutToggle], so the list can keep its final rows clear of it. */
internal val ProfileMediaLayoutToggleHeight = BeelineBubbleMinHeight

/** Space between [ProfileMediaLayoutToggle] and the category row below it. */
internal val ProfileMediaLayoutToggleGap = 8.dp

/** Three tiles in a row on a phone. A wider list adds a tile for each further [ProfileMediaGridMinTile]. */
internal fun profileMediaGridColumns(availableWidth: Dp): Int =
    maxOf(PROFILE_MEDIA_GRID_MIN_COLUMNS, (availableWidth / ProfileMediaGridMinTile).toInt())

/**
 * Emits the media grid as rows of square tiles into the profile list, so the grid shares the scroll
 * position, the header, and the paging of the other profile feeds. One post gives one tile. A tap
 * opens the post.
 */
internal fun LazyListScope.profileMediaGridItems(
    grid: ProfileMediaGrid,
    onOpenPost: (OwnedPost) -> Unit,
    interactionModifier: Modifier,
) {
    items(grid.rows, key = { row -> "media-grid/${photoGridItemKey(row.first())}" }) { row ->
        Row(
            interactionModifier
                .fillMaxWidth()
                .padding(start = ProfileMediaGridGap, end = ProfileMediaGridGap, bottom = ProfileMediaGridGap),
            horizontalArrangement = Arrangement.spacedBy(ProfileMediaGridGap),
        ) {
            row.forEach { item ->
                PhotoGridTileMedia(
                    item = item,
                    onOpenPost = onOpenPost,
                    modifier = Modifier.weight(1f),
                    aspectRatio = 1f,
                    shape = ProfileMediaTileShape,
                )
            }
            // A short final row keeps the tile size of the full rows.
            repeat(grid.columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** The floating two-segment control that switches the Media tab between the grid and the list. */
@Composable
internal fun ProfileMediaLayoutToggle(
    layout: ProfileMediaLayout,
    onLayoutSelected: (ProfileMediaLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.profile_media_layout_description)
    Surface(
        modifier = modifier.testTag("profile_media_layout_toggle"),
        shape = BeelineBubbleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = FloatingControlElevation,
    ) {
        Row(Modifier.selectableGroup().semantics { contentDescription = description }) {
            ProfileMediaLayoutSegment(
                icon = AppIcons.GridLayout,
                label = stringResource(R.string.profile_media_layout_grid),
                selected = layout == ProfileMediaLayout.Grid,
                onClick = { onLayoutSelected(ProfileMediaLayout.Grid) },
                testTag = "profile_media_layout_grid",
            )
            ProfileMediaLayoutSegment(
                icon = AppIcons.ListLayout,
                label = stringResource(R.string.profile_media_layout_list),
                selected = layout == ProfileMediaLayout.List,
                onClick = { onLayoutSelected(ProfileMediaLayout.List) },
                testTag = "profile_media_layout_list",
            )
        }
    }
}

@Composable
private fun ProfileMediaLayoutSegment(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val container = rememberSelectedColor(
        selected,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.surfaceContainer,
    )
    val content = rememberSelectedColor(
        selected,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
        Modifier
            .size(ProfileMediaLayoutToggleHeight)
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = content)
    }
}
