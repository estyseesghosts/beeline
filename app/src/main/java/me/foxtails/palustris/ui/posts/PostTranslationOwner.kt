package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Translation
import me.foxtails.palustris.domain.canTranslateTo
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.ui.UiStrings

/** What one post's translation currently looks like. */
sealed interface TranslationEntry {
    data object Loading : TranslationEntry

    /** [showOriginal] flips the body back without dropping the cached translation. */
    data class Ready(val translation: Translation, val showOriginal: Boolean = false) : TranslationEntry

    data class Failed(val message: String) : TranslationEntry

    /**
     * An automatic request found the post already in the reader's language. Nothing shows for it, and the
     * entry stops the owner from asking again.
     */
    data object SameLanguage : TranslationEntry
}

/** Read and drive translations from a post row without holding a source or a scope. */
interface PostTranslationPresentation {
    /** True while the user setting asks for a translation of every translatable post. */
    val automatic: Boolean
    fun entry(id: EntityId): TranslationEntry?
    fun translate(ownedPost: OwnedPost, targetLanguage: String)

    /**
     * Asks for the translation of a post that a row composed, when the owner rules allow it. It suspends while
     * the request waits for a free slot, so a row that leaves composition drops its queued request.
     */
    suspend fun requestAutomatic(ownedPost: OwnedPost, targetLanguage: String)
    fun toggleOriginal(id: EntityId)
    fun dismissFailure(id: EntityId)
}

internal val LocalPostTranslations = staticCompositionLocalOf<PostTranslationPresentation?> { null }

/** The language a post is translated into: the first locale of the current configuration. One reading for every surface. */
@Composable
internal fun rememberTranslationTargetLanguage(): String = LocalConfiguration.current.locales[0].language

/**
 * Owns the translations of one connected session and releases them with it. Every surface reads
 * the same entries, so a post translated in one list shows translated everywhere. The entries
 * live only for the session and are never persisted.
 *
 * The manual request comes from the share sheet. The automatic request comes from a post row while the
 * setting is on, and it is always fewer than the manual path would allow: only public and unlisted posts, one
 * request for each post, at most [MAX_AUTOMATIC_REQUESTS] at a time, and a stop after the server answers busy,
 * rate limited or without a translator. A failed automatic request leaves no entry, so the manual row retries it.
 */
class PostTranslationOwner(
    private val accountId: AccountId,
    private val sessionRevision: Long,
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val uiStrings: UiStrings,
    private val clock: () -> Long = System::currentTimeMillis,
) : PostTranslationPresentation {
    private val entries = mutableStateMapOf<EntityId, TranslationEntry>()
    private val storedOrder = ArrayDeque<EntityId>()
    private val jobs = mutableMapOf<EntityId, Job>()
    private val inFlight = mutableSetOf<EntityId>()
    private val automaticRequests = LinkedHashSet<EntityId>()
    private val slots = Semaphore(MAX_AUTOMATIC_REQUESTS)
    private var pausedUntilMillis = 0L
    private var noTranslator = false
    private var retired = false

    override var automatic by mutableStateOf(false)
        private set

    /** The host passes the user setting in. Turning it off stops new requests; stored translations stay. */
    fun applyAutomaticSetting(enabled: Boolean) {
        automatic = enabled
    }

    override fun entry(id: EntityId): TranslationEntry? = entries[id]

    override fun translate(ownedPost: OwnedPost, targetLanguage: String) {
        if (retired || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        val target = ownedPost.effectiveTargetId()
        val current = entries[target]
        if (current is TranslationEntry.Loading || current is TranslationEntry.Ready || target in inFlight) return
        store(target, TranslationEntry.Loading)
        launchRequest(target, targetLanguage, automatic = false) {}
    }

    override suspend fun requestAutomatic(ownedPost: OwnedPost, targetLanguage: String) {
        if (!mayRequestAutomatically(ownedPost, targetLanguage)) return
        val target = ownedPost.effectiveTargetId()
        if (target in automaticRequests || entries.containsKey(target) || target in inFlight) return
        // Mark the post first, so composition cannot queue it twice. A request that never started is forgotten.
        rememberAutomatic(target)
        try {
            slots.acquire()
        } catch (error: CancellationException) {
            automaticRequests.remove(target)
            throw error
        }
        // The state can change while the request waits in the queue.
        if (!mayRequestAutomatically(ownedPost, targetLanguage)) {
            automaticRequests.remove(target)
            slots.release()
            return
        }
        launchRequest(target, targetLanguage, automatic = true) { slots.release() }
    }

    private fun mayRequestAutomatically(ownedPost: OwnedPost, targetLanguage: String): Boolean {
        if (!automatic || retired || noTranslator || clock() < pausedUntilMillis) return false
        if (ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return false
        val post = ownedPost.post
        // Automatic mode never sends a post that only some people can read, on any protocol.
        if (post.audience != Audience.Public && post.audience != Audience.Unlisted) return false
        return post.canTranslateTo(source.capabilities.translation, targetLanguage)
    }

    /** The one request path of both triggers. [release] runs when the request ends, whatever its outcome. */
    private fun launchRequest(target: EntityId, targetLanguage: String, automatic: Boolean, release: () -> Unit) {
        inFlight += target
        jobs[target] = scope.launch {
            try {
                val result = source.translate(target, targetLanguage)
                if (!retired) store(target, outcome(result, targetLanguage, automatic))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!retired) failed(target, error, automatic)
            } finally {
                inFlight -= target
                jobs.remove(target)
                trim()
                release()
            }
        }
    }

    private fun outcome(result: Translation, targetLanguage: String, automatic: Boolean): TranslationEntry {
        val detected = result.sourceLanguage?.substringBefore('-')?.lowercase()
        val same = detected != null && detected == targetLanguage.substringBefore('-').lowercase()
        return if (automatic && same) TranslationEntry.SameLanguage else TranslationEntry.Ready(result)
    }

    private fun failed(target: EntityId, error: Exception, automatic: Boolean) {
        if (!automatic) {
            store(target, TranslationEntry.Failed(uiStrings.translationError(error)))
            return
        }
        // An automatic failure is quiet. Busy and rate limited answers pause the account for a while.
        // A server without a translator stops automatic requests for the rest of the session.
        when (error) {
            is SourceError.RateLimited, is SourceError.ResourceLimit -> pausedUntilMillis = clock() + PAUSE_MILLIS
            is SourceError.Unsupported -> noTranslator = true
            else -> Unit
        }
    }

    override fun toggleOriginal(id: EntityId) {
        val current = entries[id] as? TranslationEntry.Ready ?: return
        entries[id] = current.copy(showOriginal = !current.showOriginal)
    }

    override fun dismissFailure(id: EntityId) {
        if (entries[id] is TranslationEntry.Failed) entries.remove(id)
    }

    /** Stores an entry and drops the oldest ones past the bound. A post that lost its entry shows its original. */
    private fun store(id: EntityId, entry: TranslationEntry) {
        if (entries.put(id, entry) == null) storedOrder.addLast(id)
        trim()
    }

    /** A request in flight keeps its entry, so the bound is met again when that request ends. */
    private fun trim() {
        while (storedOrder.size > MAX_ENTRIES) {
            val victim = storedOrder.firstOrNull { it !in inFlight } ?: return
            storedOrder.remove(victim)
            entries.remove(victim)
        }
    }

    private fun rememberAutomatic(id: EntityId) {
        automaticRequests.add(id)
        while (automaticRequests.size > MAX_AUTOMATIC_MEMORY) automaticRequests.remove(automaticRequests.first())
    }

    /** Retires the owner with its connected entry. Later requests are ignored and results dropped. */
    fun retire() {
        retired = true
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        entries.clear()
        storedOrder.clear()
        inFlight.clear()
        automaticRequests.clear()
    }

    companion object {
        const val MAX_AUTOMATIC_REQUESTS = 2
        const val MAX_ENTRIES = 500
        const val MAX_AUTOMATIC_MEMORY = 2000
        const val PAUSE_MILLIS = 5 * 60 * 1000L
    }
}
