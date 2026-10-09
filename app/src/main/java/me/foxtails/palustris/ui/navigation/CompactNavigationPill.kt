package me.foxtails.palustris.ui.navigation

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.Avatar
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.layout.CompactNavigationCapsuleWidth
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.NotificationsPanel
import me.foxtails.palustris.ui.shell.SearchPanel

/** The compact destination pill: one stateless capsule of destination buttons. */
@Composable
internal fun CompactNavigationPill(
    destination: Destination,
    searchPanel: SearchPanel,
    notificationsPanel: NotificationsPanel,
    account: Account?,
    onOpenAccounts: () -> Unit,
    onDestinationSelected: (Destination) -> Unit,
) {
    NavigationCapsule(
        selectedIndex = destination.ordinal,
        itemCount = Destination.entries.size,
        orientation = Orientation.Horizontal,
        modifier = Modifier.width(CompactNavigationCapsuleWidth).height(56.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { item ->
                val selected = destination == item
                val showsPhotoGrid = item == Destination.Search && searchPanel == SearchPanel.PhotoGrid
                val showsDirectMessages = item == Destination.Notifications &&
                    notificationsPanel == NotificationsPanel.DirectMessages
                val label = when {
                    showsPhotoGrid -> stringResource(R.string.nav_photo_grid)
                    showsDirectMessages -> stringResource(R.string.nav_direct_messages)
                    else -> stringResource(item.labelRes)
                }
                val icon = when {
                    showsPhotoGrid -> AppIcons.PhotoGrid
                    showsDirectMessages -> AppIcons.DirectMessage
                    else -> item.icon
                }
                if (item == Destination.Profile) {
                    NavigationButton(
                        label = label,
                        icon = null,
                        selected = selected,
                        onClick = { onDestinationSelected(item) },
                        onLongClick = onOpenAccounts,
                        content = { avatarModifier ->
                            val sizedAvatar = avatarModifier.size(30.dp)
                            if (account != null) AccountAvatar(account, sizedAvatar, exposeSemantics = false)
                            else Avatar(sizedAvatar, description = null)
                        },
                    )
                } else {
                    NavigationButton(
                        label = label,
                        icon = icon,
                        selected = selected,
                        onClick = { onDestinationSelected(item) },
                    )
                }
            }
        }
    }
}
