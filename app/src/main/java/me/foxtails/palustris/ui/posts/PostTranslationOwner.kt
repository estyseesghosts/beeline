package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Translation
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.ui.UiStrings

/** What one post's translation currently looks like. */
sealed interface TranslationEntry {
    data object Loading : TranslationEntry

    /** [showOriginal] flips the body back without dropping the cached translation. */
    data class Ready(val translation: Translation, val showOriginal: Boolean = false) : TranslationEntry

    data class Failed(val message: String) : TranslationEntry
}

/** Read and drive translations from a post row without holding a source or a scope. */
interface PostTranslationPresentation {
    fun entry(id: EntityId): TranslationEntry?
    fun translate(ownedPost: OwnedPost, targetLanguage: String)
    fun toggleOriginal(id: EntityId)
    fun dismissFailure(id: EntityId)
}

internal val LocalPostTranslations = staticCompositionLocalOf<PostTranslationPresentation?> { null }

/**
 * Owns the translations of one connected session and releases them with it. Every surface reads
 * the same entries, so a post translated in one list shows translated everywhere. The entries
 * live only for the session and are never persisted.
 */
class PostTranslationOwner(
    private val accountId: AccountId,
    private val sessionRevision: Long,
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val uiStrings: UiStrings,
) : PostTranslationPresentation {
    private val entries = mutableStateMapOf<EntityId, TranslationEntry>()
    private val jobs = mutableMapOf<EntityId, Job>()
    private var retired = false

    override fun entry(id: EntityId): TranslationEntry? = entries[id]

    override fun translate(ownedPost: OwnedPost, targetLanguage: String) {
        if (retired || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        val target = ownedPost.effectiveTargetId()
        if (entries[target] is TranslationEntry.Loading || entries[target] is TranslationEntry.Ready) return
        entries[target] = TranslationEntry.Loading
        jobs[target] = scope.launch {
            try {
                val result = source.translate(target, targetLanguage)
                if (!retired) entries[target] = TranslationEntry.Ready(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!retired) entries[target] = TranslationEntry.Failed(uiStrings.translationError(error))
            } finally {
                jobs.remove(target)
            }
        }
    }

    override fun toggleOriginal(id: EntityId) {
        val current = entries[id] as? TranslationEntry.Ready ?: return
        entries[id] = current.copy(showOriginal = !current.showOriginal)
    }

    override fun dismissFailure(id: EntityId) {
        if (entries[id] is TranslationEntry.Failed) entries.remove(id)
    }

    /** Retires the owner with its connected entry. Later requests are ignored and results dropped. */
    fun retire() {
        retired = true
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        entries.clear()
    }
}
