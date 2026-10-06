package me.foxtails.palustris.data.misskey

import java.util.UUID
import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadAcquisitionState
import me.foxtails.palustris.domain.ThreadContext
import me.foxtails.palustris.domain.ThreadContinuation
import me.foxtails.palustris.domain.ThreadLimitation
import me.foxtails.palustris.domain.ThreadSessionKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Owns Misskey thread transport and acquisition for one source lifetime.
 *
 * The service fetches the focal post, walks ancestors, and runs a bounded breadth-first
 * descendant traversal. It enforces the request, batch, time, depth, and node limits. It binds
 * each continuation to the account, session revision, and focal post, and it owns the
 * [MisskeyThreadContinuationStore] that holds paused acquisitions. The caller owns the shared
 * request error boundary and the entity-origin validator passed as [validatePostId].
 */
internal class MisskeyThreadService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId?,
    private val sessionRevision: Long,
    private val clock: () -> Long,
    monotonicClock: () -> Long,
    private val maxResponseBytes: Long = MAX_THREAD_RESPONSE_BYTES,
    private val validatePostId: (EntityId, String) -> Unit,
) {
    private val continuationStore = MisskeyThreadContinuationStore(monotonicClock)

    suspend fun threadContext(
        focalId: EntityId,
        continuation: ThreadContinuation?,
    ): ThreadContext {
        val key = ThreadSessionKey(fetchingAccount(), sessionRevision, focalId)
        val state = continuation?.let { continuationState(it, key) } ?: beginThreadAcquisition(focalId, key)
        acquireDescendants(state)
        val next = if (state.pending.isNotEmpty() && !state.hardLimitReached) {
            val tokenValue = UUID.randomUUID().toString()
            state.token = tokenValue
            continuationStore.insert(tokenValue, state)
            ThreadContinuation(key, tokenValue)
        } else {
            null
        }
        return ThreadContext(
            focal = state.focal,
            ancestors = state.ancestors,
            descendants = state.descendants,
            continuation = next,
            limitations = state.limitations.toList(),
            acquisitionState = when {
                next != null -> ThreadAcquisitionState.HasContinuation
                state.limitations.isNotEmpty() -> ThreadAcquisitionState.Limited
                else -> ThreadAcquisitionState.Finished
            },
        )
    }

    private suspend fun beginThreadAcquisition(
        focalId: EntityId,
        key: ThreadSessionKey,
    ): ThreadAcquisition {
        val state = ThreadAcquisition(
            key = key,
            focal = loadThreadPost(focalId),
            ancestors = mutableListOf(),
            descendants = mutableListOf(),
            pending = ArrayDeque(),
            visitedRequests = mutableSetOf(),
            limitations = mutableListOf(),
            requestsUsed = 1,
        )
        val visitedAncestors = mutableSetOf(focalId)
        var current = state.focal
        while (current.replyTo != null) {
            if (state.ancestors.size >= MAX_ANCESTORS) {
                state.limitations += ThreadLimitation.AncestorLimit(state.ancestors.size, MAX_ANCESTORS)
                break
            }
            val parentId = current.replyTo ?: break
            if (!visitedAncestors.add(parentId)) break
            if (!reserveRequest(state)) break
            try {
                val parent = loadThreadPost(parentId)
                state.ancestors += parent
                current = parent
            } catch (e: CancellationException) {
                throw e
            } catch (_: SourceError) {
                state.limitations += ThreadLimitation.UnavailableParent(parentId)
                break
            }
        }
        state.ancestors.reverse()
        enqueue(state, ChildWork(focalId, depth = 1, cursor = null))
        return state
    }

    private suspend fun acquireDescendants(state: ThreadAcquisition) {
        val startedAt = clock()
        var batchRequests = 0
        while (state.pending.isNotEmpty()) {
            if (batchRequests >= MAX_BATCH_REQUESTS || clock() - startedAt >= MAX_BATCH_TIME_MILLIS) {
                state.limitations += ThreadLimitation.BatchTimeLimit
                break
            }
            val work = state.pending.removeFirst()
            if (work.depth > MAX_DESCENDANT_DEPTH) {
                state.limitations += ThreadLimitation.DepthLimit(work.depth, MAX_DESCENDANT_DEPTH)
                continue
            }
            if (!reserveRequest(state)) break
            batchRequests++
            val response = try {
                api.post(
                    origin,
                    "notes/children",
                    JSONObject().put("i", token)
                        .put("noteId", work.parentId.value)
                        .put("limit", CHILDREN_PAGE_LIMIT)
                        .apply { work.cursor?.let { put("untilId", it) } },
                    maxResponseBytes,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.limitations += ThreadLimitation.BranchFailure(work.parentId, normalizeThreadError(e))
                continue
            }
            val raw = try {
                JSONArray(response.body)
            } catch (e: Exception) {
                state.limitations += ThreadLimitation.BranchFailure(work.parentId, SourceError.ServerError(null))
                continue
            }
            val lastRawId = raw.optJSONObject(raw.length() - 1)?.optString("id")
                ?.takeIf(String::isNotBlank)
            for (index in 0 until raw.length()) {
                if (state.descendants.size >= MAX_DESCENDANTS) {
                    state.limitations += ThreadLimitation.NodeLimit(state.descendants.size, MAX_DESCENDANTS)
                    state.pending.clear()
                    state.hardLimitReached = true
                    break
                }
                val child = try {
                    MisskeyMapper.post(raw.getJSONObject(index), origin)
                } catch (_: Exception) {
                    continue
                }
                if (child.id.connection != origin || child.replyTo != EntityId(origin, work.parentId.value)) continue
                if (state.descendants.none { it.id == child.id }) {
                    state.descendants += child
                    if (work.depth < MAX_DESCENDANT_DEPTH) {
                        enqueue(state, ChildWork(child.id, work.depth + 1, null))
                    }
                }
            }
            if (!state.hardLimitReached && raw.length() >= CHILDREN_PAGE_LIMIT && lastRawId != null && lastRawId != work.cursor) {
                enqueue(state, ChildWork(work.parentId, work.depth, lastRawId))
            }
        }
        if (state.pending.isNotEmpty() && state.requestsUsed >= MAX_REQUESTS) {
            state.limitations += ThreadLimitation.RequestLimit(state.requestsUsed, MAX_REQUESTS)
            state.pending.clear()
            state.hardLimitReached = true
        }
    }

    private suspend fun loadThreadPost(id: EntityId): Post {
        validatePostId(id, "thread")
        return try {
            val response = api.post(
                origin,
                "notes/show",
                JSONObject().put("i", token).put("noteId", id.value),
                maxResponseBytes,
            )
            val value = MisskeyMapper.post(JSONObject(response.body), origin)
            if (value.id.connection != origin) throw SourceError.ForeignOrigin("thread")
            value
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw normalizeThreadError(error)
        }
    }

    private fun reserveRequest(state: ThreadAcquisition): Boolean {
        if (state.requestsUsed >= MAX_REQUESTS) {
            state.limitations += ThreadLimitation.RequestLimit(state.requestsUsed, MAX_REQUESTS)
            state.pending.clear()
            state.hardLimitReached = true
            return false
        }
        state.requestsUsed++
        return true
    }

    private fun enqueue(state: ThreadAcquisition, work: ChildWork) {
        if (state.visitedRequests.add(work)) state.pending += work
    }

    private fun continuationState(
        continuation: ThreadContinuation,
        expected: ThreadSessionKey,
    ): ThreadAcquisition {
        if (continuation.sessionKey != expected) throw SourceError.Unsupported("thread.continuation")
        return continuationStore.consume(continuation.token, expected)
    }

    private fun fetchingAccount(): AccountId = accountId
        ?: AccountId(Connection(origin, Protocol.MISSKEY), "anonymous")

    private fun normalizeThreadError(error: Exception): SourceError = when (error) {
        is SourceError -> error
        is ApiFailure -> MisskeyErrorMapper.map(error)
        else -> MisskeyErrorMapper.map(error)
    }

    private companion object {
        const val MAX_DESCENDANTS = 200
        const val MAX_ANCESTORS = 20
        const val MAX_DESCENDANT_DEPTH = 10
        const val MAX_BATCH_REQUESTS = 8
        const val MAX_REQUESTS = 40
        const val MAX_BATCH_TIME_MILLIS = 15_000L
        const val CHILDREN_PAGE_LIMIT = 30
        const val MAX_THREAD_RESPONSE_BYTES = MISSKEY_MAX_RESPONSE_BYTES
    }
}
