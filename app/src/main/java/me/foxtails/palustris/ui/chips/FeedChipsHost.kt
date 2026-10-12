package me.foxtails.palustris.ui.chips

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.SavedHashtagRepository
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.FeedChipsContract

/** Connects one [FeedChipCatalog] to the connected lifetime and exposes it as the shared chips contract. */
@Composable
fun FeedChipsHost(
    accountId: AccountId,
    sessionGeneration: Long,
    sessionRevision: Long,
    source: SocialSource,
    savedHashtags: SavedHashtagRepository,
    entryStore: ConnectedEntryStore,
): FeedChipsContract {
    val scope = rememberCoroutineScope()
    val catalog = remember(accountId, sessionRevision, source) {
        FeedChipCatalog(accountId, source, savedHashtags, scope)
    }
    LaunchedEffect(entryStore, sessionGeneration, catalog) {
        entryStore.register(sessionGeneration, "feed-chips-$accountId-$sessionGeneration") { catalog.stop() }
    }
    val state by catalog.state.collectAsStateWithLifecycle()
    val actions = remember(catalog) {
        object : FeedChipsContract.Actions {
            override fun refresh() = catalog.refresh()
            override fun addHashtag(value: String, onAdded: (FeedSource.Hashtag) -> Unit) = catalog.addHashtag(value, onAdded)
            override fun removeHashtag(tag: String, onRemoved: () -> Unit) = catalog.removeHashtag(tag, onRemoved)
            override fun addHashtagGroup(name: String, values: List<String>, onAdded: (FeedSource.HashtagGroup) -> Unit) =
                catalog.addHashtagGroup(name, values, onAdded)
            override fun removeHashtagGroup(group: FeedSource.HashtagGroup, onRemoved: () -> Unit) =
                catalog.removeHashtagGroup(group, onRemoved)
            override fun clearError() = catalog.clearError()
        }
    }
    return remember(state, actions) { FeedChipsContract(state, actions) }
}
