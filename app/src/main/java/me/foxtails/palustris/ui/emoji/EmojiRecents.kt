package me.foxtails.palustris.ui.emoji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Recent emoji identities for one picker presentation, newest first.
 *
 * The holder lives only in composition and saved instance state. It is never written to
 * preferences, so a new presentation starts empty. The compact pop-out and the full picker of
 * one presentation share one holder, so expanding keeps the recents.
 */
@Stable
class EmojiRecents(initial: List<String> = emptyList()) {
    var identities by mutableStateOf(initial)
        private set

    fun use(identity: String) {
        identities = (listOf(identity) + identities.filterNot { it == identity }).take(LIMIT)
    }

    companion object {
        const val LIMIT = 16

        val Saver = listSaver<EmojiRecents, String>(
            save = { it.identities },
            restore = { EmojiRecents(it) },
        )
    }
}

@Composable
fun rememberEmojiRecents(): EmojiRecents = rememberSaveable(saver = EmojiRecents.Saver) { EmojiRecents() }
