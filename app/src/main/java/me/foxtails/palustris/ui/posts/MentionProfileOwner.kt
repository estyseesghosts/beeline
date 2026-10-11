package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.PostMention
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.emoji.MentionProfile
import me.foxtails.palustris.ui.emoji.MentionProfilePresentation

/**
 * Resolves the accounts that post text mentions, for one connected session. Neither server sends a mentioned
 * account's display name or avatar with the post, so a row that shows a mention asks here. The map from a handle
 * to its state is the only cache. It keeps at most [limit] handles and drops the oldest first. A handle is
 * requested once per session, at most [concurrency] requests run together, and a failed handle is not retried.
 * The session host creates the owner and calls [retire] with the session, which cancels the requests and clears
 * the map. Requests go to the session's own source only.
 */
class MentionProfileOwner(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val limit: Int = DEFAULT_LIMIT,
    concurrency: Int = DEFAULT_CONCURRENCY,
) : MentionProfilePresentation {
    private var entries by mutableStateOf<Map<String, MentionProfile>>(emptyMap())
    private val permits = Semaphore(concurrency)
    private val jobs = mutableMapOf<String, Job>()
    private var retired = false

    /** Number of handles held. */
    internal val size: Int get() = entries.size

    override fun profile(mention: PostMention): MentionProfile? = entries[keyOf(mention.handle)]

    override fun request(mention: PostMention) {
        if (retired) return
        val key = keyOf(mention.handle)
        if (key in entries) return
        put(key, MentionProfile.Pending)
        jobs[key] = scope.launch {
            val result = try {
                permits.withPermit { MentionProfile.Resolved(lookup(mention)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                MentionProfile.Failed
            }
            if (!retired) put(key, result)
            jobs.remove(key)
        }
    }

    override fun seed(accounts: List<Account>) {
        if (retired) return
        accounts.forEach { account ->
            val key = keyOf(account.handle)
            if (entries[key] !is MentionProfile.Resolved) put(key, MentionProfile.Resolved(account))
        }
    }

    /** Drops every entry and cancels every request. The session host calls it when the session is replaced. */
    fun retire() {
        retired = true
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        entries = emptyMap()
    }

    private suspend fun lookup(mention: PostMention): Account {
        val id = mention.accountId
        if (id != null && id.connection == accountId.connection) return source.profile(id)
        return source.searchAccounts(mention.handle.removePrefix("@"))
            .firstOrNull { keyOf(it.handle) == keyOf(mention.handle) }
            ?: error("mention not found")
    }

    private fun put(key: String, profile: MentionProfile) {
        val next = LinkedHashMap(entries)
        next.remove(key)
        next[key] = profile
        while (next.size > limit) next.remove(next.keys.first())
        entries = next
    }

    private fun keyOf(handle: String) = handle.lowercase()

    private companion object {
        const val DEFAULT_LIMIT = 500
        const val DEFAULT_CONCURRENCY = 2
    }
}
