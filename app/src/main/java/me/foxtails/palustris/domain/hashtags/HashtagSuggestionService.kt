package me.foxtails.palustris.domain.hashtags

import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withTimeoutOrNull
import me.foxtails.palustris.domain.hashtagBody
import me.foxtails.palustris.domain.hashtagIdentity
import me.foxtails.palustris.domain.isExactHashtag

/**
 * Merges catalog and server hashtag suggestions for a typed prefix. One instance serves one account
 * session: it owns the in-memory cache and the debounce, and it is dropped with the session.
 *
 * Only the hashtag fragment reaches [fetchServer]. The draft and the full query text never do. A
 * failed or slow server request returns the catalog matches without an error. The server gets
 * [SERVER_DEADLINE_MILLIS] to answer. A late answer is dropped and never cached. The policy is
 * read on each call, so a language change applies to the next request, not to cached server answers.
 */
class HashtagSuggestionService(
    private val catalog: HashtagCatalog,
    private val accountKey: String,
    private val policy: () -> HashtagLanguagePolicy,
    private val fetchServer: suspend (prefix: String, limit: Int) -> List<HashtagSuggestion>,
    private val clock: () -> Long = System::nanoTime,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
) {
    private class CacheEntry(val storedAt: Long, val suggestions: List<HashtagSuggestion>)

    // Access order makes the eldest entry the least recently used one.
    private val cache = object : LinkedHashMap<String, CacheEntry>(CACHE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > CACHE_CAPACITY
    }

    /**
     * Suggestions for every settled prefix. A new prefix cancels the request for the previous one, and
     * a prefix that changes inside the debounce window never reaches the server.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun suggestions(prefixes: Flow<String>, limit: Int): Flow<List<HashtagSuggestion>> =
        prefixes.debounce(debounceMillis).mapLatest { suggest(it, limit) }

    /** Up to [limit] suggestions for [typed]: the exact match, then by weight, then server order. */
    suspend fun suggest(typed: String, limit: Int): List<HashtagSuggestion> {
        val prefix = identityOf(typed) ?: return emptyList()
        val allowed = policy()
        // One entry for each identity: the one with the highest weight. Order of first appearance stays.
        val merged = (catalogMatches(prefix, allowed) + serverMatches(prefix, limit, allowed))
            .groupBy { HashtagCatalog.identityOf(it.name) }
            .values
            .map { duplicates -> duplicates.maxBy { it.weight ?: -1.0 } }
        return merged
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<HashtagSuggestion>> {
                    HashtagCatalog.identityOf(it.value.name) == prefix
                }.thenByDescending { it.value.weight ?: -1.0 }.thenBy { it.index },
            )
            .map { it.value }
            .take(limit)
    }

    /** Forgets cached answers. Called when the account session ends. */
    fun release() {
        synchronized(cache) { cache.clear() }
    }

    private fun identityOf(typed: String): String? {
        val fragment = typed.trim().removePrefix("#")
        if (fragment.isEmpty() || !isExactHashtag(fragment)) return null
        return hashtagIdentity(hashtagBody(fragment))
    }

    /** Members that start with the prefix and pass the language policy. An exact match always passes. */
    private fun catalogMatches(prefix: String, allowed: HashtagLanguagePolicy): List<HashtagSuggestion> =
        catalog.prefixEntries(prefix)
            .filter { it.member.name == prefix || allowed.allows(it.member) }
            .map { HashtagSuggestion(it.member.name, it.member.weight) }

    private suspend fun serverMatches(
        prefix: String,
        limit: Int,
        allowed: HashtagLanguagePolicy,
    ): List<HashtagSuggestion> =
        fetchCached(prefix, limit)
            .filter { HashtagCatalog.identityOf(it.name).startsWith(prefix) }
            .filter { suggestion ->
                val identity = HashtagCatalog.identityOf(suggestion.name)
                val known = catalog.entries(identity)
                identity == prefix ||
                    if (known.isEmpty()) allowed.allowsUnknown(suggestion.name) else known.any { allowed.allows(it.member) }
            }

    private suspend fun fetchCached(prefix: String, limit: Int): List<HashtagSuggestion> {
        val key = "$accountKey\u0000$prefix"
        val now = clock()
        synchronized(cache) {
            cache[key]?.takeIf { now - it.storedAt < CACHE_TTL_NANOS }?.let { return it.suggestions }
        }
        val fetched = try {
            withTimeoutOrNull(SERVER_DEADLINE_MILLIS) { fetchServer(prefix, max(limit, SERVER_LIMIT)) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()
        synchronized(cache) { cache[key] = CacheEntry(now, fetched) }
        return fetched
    }

    companion object {
        const val DEBOUNCE_MILLIS = 250L
        const val SEARCH_LIMIT = 8
        const val COMPOSER_LIMIT = 5
        const val SERVER_DEADLINE_MILLIS = 2_000L
        private const val SERVER_LIMIT = 8
        private const val CACHE_CAPACITY = 50
        private const val CACHE_TTL_NANOS = 5L * 60 * 1_000_000_000
    }
}
