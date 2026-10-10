package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.hashtags.HashtagExpansionInput
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.HashtagSuggestionService
import me.foxtails.palustris.ui.session.ConnectedEntryStore

/** The hashtag under the cursor of one entry. */
internal data class ActiveHashtag(val entryId: String, val token: HashtagToken)

/** A chip tap that waits for the entry row, which owns the cursor, to apply it. */
internal data class HashtagInsertion(val entryId: String, val name: String)

/**
 * The hashtag the user is typing in the composer, and the chip tap that completes it. One instance
 * serves one composer body. The entry rows report tokens, the body shows the chips, and the row
 * that owns the cursor applies the insertion, as it does for an emoji.
 */
@Stable
internal class ComposerHashtags {
    var active by mutableStateOf<ActiveHashtag?>(null)
        private set
    var pending by mutableStateOf<HashtagInsertion?>(null)
        private set

    /** A row reports its token after each text, cursor, or focus change. A null token clears only that row's token. */
    fun report(entryId: String, token: HashtagToken?) {
        active = when {
            token != null -> ActiveHashtag(entryId, token)
            active?.entryId == entryId -> null
            else -> active
        }
    }

    fun insert(name: String) {
        val entryId = active?.entryId ?: return
        pending = HashtagInsertion(entryId, name)
    }

    fun applied() {
        pending = null
    }
}

/** The text a chip tap writes: the hashtag and a space, so typing continues after it. */
internal fun hashtagInsertionText(name: String): String = "#$name "

/**
 * Up to five chips directly above the toolbar for the fragment being typed. The list stays empty
 * for a blank fragment. A failed server request leaves the catalog matches.
 */
@Composable
internal fun ComposerHashtagChips(
    fragment: String?,
    suggestions: (Flow<String>) -> Flow<List<HashtagSuggestion>>,
    onInsert: (String) -> Unit,
) {
    val fragments = remember { MutableStateFlow("") }
    var results by remember { mutableStateOf(emptyList<HashtagSuggestion>()) }
    val latest by rememberUpdatedState(suggestions)
    LaunchedEffect(fragments) { latest(fragments).collect { results = it } }
    LaunchedEffect(fragment) {
        fragments.value = fragment.orEmpty()
        if (fragment == null) results = emptyList()
    }
    if (fragment == null || results.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("composer_hashtag_suggestions"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        results.forEach { suggestion ->
            val description = stringResource(R.string.composer_insert_hashtag, "#${suggestion.name}")
            AssistChip(
                onClick = { onInsert(suggestion.name) },
                label = { Text(hashtagLabel(suggestion.name)) },
                modifier = Modifier.testTag("composer_hashtag_chip_${suggestion.name}")
                    .semantics { contentDescription = description; role = Role.Button },
            )
        }
    }
}

private fun hashtagLabel(name: String) = "#$name"

/**
 * Builds the suggestion service of the connected session, and releases it with the session. The
 * service sends only the typed fragment to the account server.
 */
@Composable
fun rememberComposerHashtagSuggestions(
    accountKey: String,
    sessionGeneration: Long,
    source: SocialSource,
    hashtagInput: HashtagExpansionInput,
    entryStore: ConnectedEntryStore,
): (Flow<String>) -> Flow<List<HashtagSuggestion>> {
    val latestInput by rememberUpdatedState(hashtagInput)
    val service = remember(accountKey, source) {
        HashtagSuggestionService(
            catalog = latestInput.catalog,
            accountKey = accountKey,
            policy = { latestInput.policy },
            fetchServer = source::suggestHashtags,
        )
    }
    LaunchedEffect(entryStore, sessionGeneration, service) {
        entryStore.register(sessionGeneration, "composer-hashtags-$accountKey-$sessionGeneration") { service.release() }
    }
    return remember(service) {
        { fragments -> service.suggestions(fragments, HashtagSuggestionService.COMPOSER_LIMIT) }
    }
}
