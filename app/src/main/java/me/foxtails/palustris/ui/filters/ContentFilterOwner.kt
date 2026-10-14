package me.foxtails.palustris.ui.filters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ClientFilterMatcher
import me.foxtails.palustris.domain.ContentFilterSet
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.session.ConnectedSessionContext

/**
 * Owns the filter snapshot of the connected account for one session. Every post surface reads [matcher]
 * through [LocalClientFilters]. A load failure keeps the previous snapshot; an unsupported source gives none.
 * The settings page hands a saved set to [replace], so the feed follows an edit without a reload.
 * It retires with the connected entry and then holds no filters.
 */
class ContentFilterOwner(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val scope: CoroutineScope,
) {
    var matcher: ClientFilterMatcher by mutableStateOf(ClientFilterMatcher.Empty)
        private set
    private var loadJob: Job? = null
    private var retired = false

    fun load() {
        if (retired) return
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                apply(source.contentFilters())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the last snapshot. Unsupported sources keep the empty one.
            }
        }
    }

    /** Applies [set] when it belongs to this session's account. A set of another account is ignored. */
    fun replace(owner: AccountId, set: ContentFilterSet) {
        if (owner == accountId && !retired) {
            loadJob?.cancel()
            apply(set)
        }
    }

    fun retire() {
        retired = true
        loadJob?.cancel()
        matcher = ClientFilterMatcher.Empty
    }

    private fun apply(set: ContentFilterSet) {
        if (!retired) matcher = ClientFilterMatcher.forSet(accountId, set)
    }
}

/** The client filters of the connected account. Empty outside a session and for server-matched filters. */
val LocalClientFilters = staticCompositionLocalOf { ClientFilterMatcher.Empty }

/** The session owner, so the settings page can publish a saved set. Null outside a session. */
val LocalContentFilterOwner = staticCompositionLocalOf<ContentFilterOwner?> { null }

/**
 * Creates the owner for the accepted [context], loads it once, and retires it with the connected entry.
 * Null while no session is connected.
 */
@Composable
fun rememberContentFilterOwner(context: ConnectedSessionContext?, entryStore: ConnectedEntryStore): ContentFilterOwner? {
    val scope = rememberCoroutineScope()
    // A new generation retires the old owner through the entry store, so each generation gets its own owner.
    val owner = remember(context?.accountId, context?.sessionRevision, context?.source, context?.presentationGeneration) {
        context?.let { ContentFilterOwner(it.accountId, it.source, scope) }
    }
    LaunchedEffect(entryStore, context?.presentationGeneration, owner) {
        val generation = context?.presentationGeneration ?: return@LaunchedEffect
        owner ?: return@LaunchedEffect
        owner.load()
        entryStore.register(generation, "content-filters-${context.accountId}-$generation") { owner.retire() }
    }
    return owner
}
