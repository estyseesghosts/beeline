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
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationPage
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.session.ConnectedSessionContext

/**
 * Owns the filter snapshot of the connected account for one session: the word filters in [matcher] and the
 * muted and blocked accounts in [muted] and [blocked]. Every post surface reads them through [LocalClientFilters].
 * A load failure keeps the previous snapshot; an unsupported source gives none. The settings page hands a saved
 * set to [replace], and the post menu hands each relationship change to [applyRelationship], so the feeds follow
 * without a reload. It retires with the connected entry and then holds nothing.
 */
class ContentFilterOwner(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val scope: CoroutineScope,
) {
    var matcher: ClientFilterMatcher by mutableStateOf(ClientFilterMatcher.Empty)
        private set
    var muted: Set<AccountId> by mutableStateOf(emptySet())
        private set
    var blocked: Set<AccountId> by mutableStateOf(emptySet())
        private set
    private var loadJob: Job? = null
    private var accountsJob: Job? = null
    private var retired = false

    /** Loads the word filters and both account lists. Each part keeps its last value when it fails. */
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
        loadAccounts()
    }

    /** Reads both account lists again, for example after the Blocks and mutes pages changed them. */
    fun loadAccounts() {
        if (retired) return
        accountsJob?.cancel()
        accountsJob = scope.launch {
            allAccounts { cursor -> source.mutedAccounts(cursor) }?.let { if (!retired) muted = it }
            allAccounts { cursor -> source.blockedAccounts(cursor) }?.let { if (!retired) blocked = it }
        }
    }

    /** Follows a mute or block change from the post menu at once, before the next load. */
    fun applyRelationship(relationship: ProfileRelationship) {
        if (retired || relationship.profileId.connection != accountId.connection) return
        val id = relationship.profileId
        muted = if (relationship.muting) muted + id else muted - id
        blocked = if (relationship.blocking) blocked + id else blocked - id
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
        accountsJob?.cancel()
        matcher = ClientFilterMatcher.Empty
        muted = emptySet()
        blocked = emptySet()
    }

    private fun apply(set: ContentFilterSet) {
        if (!retired) matcher = ClientFilterMatcher.forSet(accountId, set)
    }

    /** Every page of one list, up to [MAX_ACCOUNT_PAGES]. Null when the first page fails, so the old list stays. */
    private suspend fun allAccounts(page: suspend (ModerationCursor?) -> ModerationPage<ModerationAccount>): Set<AccountId>? {
        val ids = mutableSetOf<AccountId>()
        var cursor: ModerationCursor? = null
        repeat(MAX_ACCOUNT_PAGES) { index ->
            val result = try {
                page(cursor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return if (index == 0) null else ids
            }
            result.items.mapTo(ids) { it.account.id }
            cursor = result.nextCursor ?: return ids
        }
        return ids
    }

    private companion object {
        /** Large lists stop here; an account past the limit still collapses after a mute from the post menu. */
        const val MAX_ACCOUNT_PAGES = 25
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
