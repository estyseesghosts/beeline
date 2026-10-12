package me.foxtails.palustris.ui.chips

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.HashtagOrigin
import me.foxtails.palustris.domain.SavedHashtagGroup
import me.foxtails.palustris.domain.SavedHashtagRepository
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.hashtagIdentity
import me.foxtails.palustris.domain.stripHashtagMarks
import me.foxtails.palustris.domain.validateExactHashtag

/**
 * Owns the shared chip catalog of one connected session: the account's lists, hashtags and antennas.
 * Home and the Photo Grid both observe [state], so a change shows on both at once. It refreshes when the
 * session starts, on a pull-to-refresh, and after every follow, unfollow, save and remove.
 *
 * Hashtags come from the server when the server offers followed tags (Mastodon) and from the device
 * otherwise (Misskey family). On the first refresh of a session a Mastodon account follows every tag it
 * saved on the device before. A tag that fails stays saved and is tried again next session.
 */
internal class FeedChipCatalog(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val saved: SavedHashtagRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(FeedChipState())
    val state: StateFlow<FeedChipState> = _state.asStateFlow()

    private var lists: List<FeedSource.UserList> = emptyList()
    private var antennas: List<FeedSource.Antenna> = emptyList()
    private var serverTags: List<String> = emptyList()
    private var deviceTags: List<String> = emptyList()
    private var deviceGroups: List<SavedHashtagGroup> = emptyList()

    /** Null until the first refresh decides. True reads followed tags from the server. */
    private var useServerTags: Boolean? = null
    private var migrated = false
    private var stopped = false
    private var refreshJob: Job? = null
    private var changeJob: Job? = null
    private var savedJob: Job? = null

    init {
        savedJob = scope.launch {
            saved.observe(accountId).collectLatest { preferences ->
                if (stopped) return@collectLatest
                deviceTags = preferences.tags
                deviceGroups = preferences.groups
                publish()
            }
        }
        refresh()
    }

    /** Reads lists, antennas and followed tags again. A kind that fails keeps what it showed. */
    fun refresh() {
        if (stopped) return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val capabilities = source.capabilities.feedChips
            coroutineScope {
                val listCall = async { if (capabilities.lists.offered()) attempt { source.userLists() } else emptyList() }
                val antennaCall = async { if (capabilities.antennas.offered()) attempt { source.antennas() } else emptyList() }
                val tagCall = async { if (capabilities.followedTags.offered()) attempt { source.followedTags() } else null }
                listCall.await()?.let { lists = it }
                antennaCall.await()?.let { antennas = it }
                val tags = tagCall.await()
                when {
                    tags != null -> {
                        useServerTags = true
                        serverTags = tags
                    }
                    // A server that says it follows tags keeps the server path through a failed read.
                    capabilities.followedTags == CapabilityStatus.Supported -> if (useServerTags == null) useServerTags = true
                    else -> useServerTags = false
                }
            }
            if (stopped) return@launch
            publish(loaded = true)
            if (useServerTags == true && !migrated) migrateSavedTags()
        }
    }

    /**
     * Adds [raw] as a hashtag chip: a follow on the server, or a save on the device. A tag that is already a
     * chip is reported without a write. [onAdded] receives the chip so the caller can select it.
     */
    fun addHashtag(raw: String, onAdded: (FeedSource.Hashtag) -> Unit = {}) {
        if (stopped || _state.value.busy) return
        // Both paths store the tag without the mark. The chip label adds the single `#` back.
        val accepted = runCatching { validateExactHashtag(stripHashtagMarks(raw)) }.getOrElse {
            publish(error = FeedChipError.InvalidHashtag)
            return
        }
        val existing = _state.value.hashtags.firstOrNull { hashtagIdentity(it.tag) == hashtagIdentity(accepted) }
        if (existing != null) {
            publish(error = null)
            onAdded(existing)
            return
        }
        change {
            if (useServerTags == true) {
                source.followTag(accepted)
                serverTags = (serverTags + accepted).distinctBy(::hashtagIdentity)
            } else {
                saved.update(accountId) { it.copy(tags = (it.tags + accepted).distinctBy(::hashtagIdentity)) }
            }
            onAdded(FeedSource.Hashtag(accepted, origin()))
        }
    }

    /**
     * Saves [raws] as a hashtag group on the device. Neither protocol stores such a list, so no server call
     * happens. Each tag gets the same normalization as a single tag. A blank [name] becomes the first tag.
     * A group with the same tags as an existing one is reported without a write.
     */
    fun addHashtagGroup(name: String, raws: List<String>, onAdded: (FeedSource.HashtagGroup) -> Unit = {}) {
        if (stopped || _state.value.busy) return
        val accepted = runCatching { raws.map { validateExactHashtag(stripHashtagMarks(it)) }.distinctBy(::hashtagIdentity) }
            .getOrNull()
            ?.takeIf { it.size >= 2 && it.size <= source.maxGroupHashtags }
        if (accepted == null) {
            publish(error = FeedChipError.InvalidHashtag)
            return
        }
        val group = FeedSource.HashtagGroup(name.trim().ifEmpty { "#" + accepted.first() }, accepted)
        val existing = _state.value.hashtagGroups.firstOrNull { it.key == group.key }
        if (existing != null) {
            publish(error = null)
            onAdded(existing)
            return
        }
        change {
            saved.update(accountId) { it.copy(groups = it.groups + SavedHashtagGroup(group.name, group.tags)) }
            onAdded(group)
        }
    }

    /** Deletes [group] from the device. */
    fun removeHashtagGroup(group: FeedSource.HashtagGroup, onRemoved: () -> Unit = {}) {
        if (stopped || _state.value.busy) return
        change {
            saved.update(accountId) { preferences ->
                preferences.copy(groups = preferences.groups.filterNot { FeedSource.HashtagGroup(it.name, it.tags).key == group.key })
            }
            onRemoved()
        }
    }

    /** Removes [tag]: an unfollow on the server, or a delete from the device. The device path never calls the server. */
    fun removeHashtag(tag: String, onRemoved: () -> Unit = {}) {
        if (stopped || _state.value.busy) return
        val identity = hashtagIdentity(tag)
        change {
            if (useServerTags == true) {
                source.unfollowTag(tag)
                serverTags = serverTags.filterNot { hashtagIdentity(it) == identity }
            } else {
                saved.update(accountId) { preferences ->
                    preferences.copy(tags = preferences.tags.filterNot { hashtagIdentity(it) == identity })
                }
            }
            onRemoved()
        }
    }

    fun clearError() {
        if (_state.value.error != null) publish(error = null)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        refreshJob?.cancel()
        changeJob?.cancel()
        savedJob?.cancel()
    }

    private fun change(block: suspend () -> Unit) {
        changeJob?.cancel()
        changeJob = scope.launch {
            publish(busy = true, error = null)
            try {
                block()
                if (stopped) return@launch
                publish(busy = false, error = null)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!stopped) publish(busy = false, error = FeedChipError.SaveFailed)
            }
        }
    }

    /** Follows each locally saved tag on the server. A failure leaves that tag saved for the next session. */
    private suspend fun migrateSavedTags() {
        migrated = true
        val local = try {
            saved.observe(accountId).first().tags
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        if (local.isEmpty()) return
        val done = mutableSetOf<String>()
        for (tag in local) {
            try {
                source.followTag(tag)
                done += hashtagIdentity(tag)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Rate limit or network. The tag stays on the device and the next session tries it again.
            }
        }
        if (done.isEmpty() || stopped) return
        try {
            saved.update(accountId) { preferences ->
                preferences.copy(tags = preferences.tags.filterNot { hashtagIdentity(it) in done })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        attempt { source.followedTags() }?.let { serverTags = it }
        publish()
    }

    private fun origin() = if (useServerTags == true) HashtagOrigin.Server else HashtagOrigin.Device

    private fun publish(
        loaded: Boolean = _state.value.loaded,
        busy: Boolean = _state.value.busy,
        error: FeedChipError? = _state.value.error,
    ) {
        val origin = origin()
        val tags = if (origin == HashtagOrigin.Server) serverTags else deviceTags
        _state.value = FeedChipState(
            lists = lists,
            hashtags = tags.map { FeedSource.Hashtag(it.removePrefix("#"), origin) },
            antennas = antennas,
            hashtagGroups = deviceGroups.map { FeedSource.HashtagGroup(it.name, it.tags) },
            maxGroupTags = source.maxGroupHashtags,
            loaded = loaded,
            busy = busy,
            error = error,
        )
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun CapabilityStatus.offered() = this != CapabilityStatus.Unsupported && this != CapabilityStatus.Denied
}
