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
import me.foxtails.palustris.ui.emoji.EmojiHost
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.EmojiPresentation

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

/**
 * The emoji catalog for the favourite reaction picker. It exists only while the Posting page shows an account,
 * and it is the catalog owner of that account and session (`EmojiHost`), so no second catalog is loaded.
 */
@Composable
internal fun postingEmojiPresentation(
    route: SettingsRoute,
    accountId: AccountId?,
    source: SocialSource?,
    sessionGeneration: Long,
    entryStore: ConnectedEntryStore,
): EmojiPresentation {
    if (route != SettingsRoute.Posting || accountId == null || source == null) return EmojiPresentation.Empty
    return EmojiHost(accountId, sessionGeneration, source, entryStore)
}

/** Binds the Posting page commands to [accountId]. Without an account the commands do nothing. */
internal fun postingActions(accountId: AccountId?, model: SettingsViewModel) = PostingSettingsActions(
    onDefaultAudience = { value -> accountId?.let { model.setPostDefaultAudience(it, value) } },
    onRepliesUnlisted = { value -> accountId?.let { model.setPostRepliesUnlisted(it, value) } },
    onUploadCompression = { value -> accountId?.let { model.setPostUploadCompression(it, value) } },
    onFavouriteEmoji = { value -> accountId?.let { model.setPostFavouriteEmoji(it, value) } },
)
