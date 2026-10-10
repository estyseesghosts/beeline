package me.foxtails.palustris.ui.composer

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.ui.AppIcons

@get:StringRes
internal val Audience.labelRes: Int
    get() = when (this) {
        Audience.Public -> R.string.audience_everyone
        Audience.Unlisted -> R.string.audience_unlisted
        Audience.Followers -> R.string.audience_followers
        Audience.Direct -> R.string.audience_direct
    }

@get:StringRes
internal val Audience.descriptionRes: Int
    get() = when (this) {
        Audience.Public -> R.string.audience_everyone_description
        Audience.Unlisted -> R.string.audience_unlisted_description
        Audience.Followers -> R.string.audience_followers_description
        Audience.Direct -> R.string.audience_direct_description
    }

/**
 * The audience of the whole thread. A tap opens a menu with each audience the server offers and
 * a one-line description. The body shows this row only while the first entry has focus.
 */
@Composable
internal fun ComposerAudienceRow(
    audience: Audience,
    options: List<Audience>,
    onSelect: (Audience) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val rowDescription = stringResource(R.string.composer_audience_row, stringResource(audience.labelRes))
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { open = true }
                .semantics(mergeDescendants = true) { contentDescription = rowDescription }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Globe, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(audience.labelRes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(AppIcons.CaretDown, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(option.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = { if (option == audience) Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
