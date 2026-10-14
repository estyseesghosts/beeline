package me.foxtails.palustris.ui.filters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AccountFilter
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.ClientFilterMatcher

/**
 * The matcher that [LocalClientFilters] provides: the owner's word filters with its account lists, the user's
 * choices for muted and blocked accounts, and the localized notice texts. Empty without a session.
 */
@Composable
fun rememberClientFilters(owner: ContentFilterOwner?, preferences: AppPreferences): ClientFilterMatcher {
    owner ?: return ClientFilterMatcher.Empty
    val mutedLabel = stringResource(R.string.post_filtered_muted_account)
    val blockedLabel = stringResource(R.string.post_filtered_blocked_account)
    val matcher = owner.matcher
    val muted = owner.muted
    val blocked = owner.blocked
    val actions = preferences.accountPostActions
    return remember(matcher, muted, blocked, actions, mutedLabel, blockedLabel) {
        matcher.withAccounts(AccountFilter(muted, blocked, actions.muted, actions.blocked, mutedLabel, blockedLabel))
    }
}

/**
 * Reads the account lists again each time Settings closes, because the Blocks and mutes pages can unmute and
 * unblock. Opening Settings or the first composition does not reload.
 */
@Composable
fun ReloadAccountFiltersAfterSettings(settingsVisible: Boolean, owner: ContentFilterOwner?) {
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(settingsVisible, owner) {
        if (settingsVisible) {
            opened = true
        } else if (opened) {
            opened = false
            owner?.loadAccounts()
        }
    }
}
