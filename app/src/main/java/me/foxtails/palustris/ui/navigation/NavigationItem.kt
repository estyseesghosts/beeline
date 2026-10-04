package me.foxtails.palustris.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons

/**
 * The label and icon data for a navigation target.
 *
 * Each presentation owns layout and callbacks. The model does not own selection or navigation state.
 */
internal data class NavigationItem(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

/** The four grouped compact positions. */
internal enum class CompactNavigationItem(val item: NavigationItem) {
    Home(NavigationItem(R.string.nav_home, AppIcons.HoneyHome)),
    Search(NavigationItem(R.string.nav_search, AppIcons.SearchBeeline)),
    Notifications(NavigationItem(R.string.nav_notifications, AppIcons.Mail)),
    Profile(NavigationItem(R.string.nav_profile, AppIcons.DefaultUser)),
}

/** The six direct wide targets. */
internal enum class WideNavigationItem(val item: NavigationItem) {
    Home(NavigationItem(R.string.nav_home, AppIcons.HoneyHome)),
    Search(NavigationItem(R.string.nav_search, AppIcons.SearchBeeline)),
    PhotoGrid(NavigationItem(R.string.nav_photo_grid, AppIcons.PhotoGrid)),
    Notifications(NavigationItem(R.string.nav_notifications, AppIcons.Mail)),
    DirectMessages(NavigationItem(R.string.nav_direct_messages, AppIcons.DirectMessage)),
    Profile(NavigationItem(R.string.nav_profile, AppIcons.DefaultUser)),
}

/** Applies the shared navigation-button semantic contract to any navigation surface. */
internal fun Modifier.navigationButtonSemantics(
    label: String,
    selected: Boolean,
): Modifier = semantics {
    contentDescription = label
    this.selected = selected
    role = Role.Tab
}
