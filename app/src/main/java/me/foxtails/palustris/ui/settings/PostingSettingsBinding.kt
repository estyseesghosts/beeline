package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource

/**
 * The Posting page settings that the server of [source] supports. The value follows capability updates.
 * Without a source nothing is supported, so the page shows only the settings that every server has.
 */
@Composable
internal fun rememberPostingSupport(source: SocialSource?): PostingSettingsSupport {
    val capabilities by remember(source) {
        source?.observeCapabilities() ?: flowOf<ServerCapabilities?>(null)
    }.collectAsStateWithLifecycle(initialValue = source?.capabilities)
    return PostingSettingsSupport(
        uploadCompression = capabilities?.posting?.clientCompression == true,
        favouriteEmoji = capabilities?.primaryFavourite?.mode == PrimaryFavouriteMode.Reaction,
    )
}

/** Binds the Posting page commands to [accountId]. Without an account the commands do nothing. */
internal fun postingActions(accountId: AccountId?, model: SettingsViewModel) = PostingSettingsActions(
    onDefaultAudience = { value -> accountId?.let { model.setPostDefaultAudience(it, value) } },
    onRepliesUnlisted = { value -> accountId?.let { model.setPostRepliesUnlisted(it, value) } },
    onUploadCompression = { value -> accountId?.let { model.setPostUploadCompression(it, value) } },
    onFavouriteEmoji = { value -> accountId?.let { model.setPostFavouriteEmoji(it, value) } },
)
