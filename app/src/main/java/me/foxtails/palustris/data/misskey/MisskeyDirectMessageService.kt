package me.foxtails.palustris.data.misskey

import java.util.Base64
import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.data.transport.ResponseLimitExceeded
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.ConversationIdentity
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectThreadRequest
import me.foxtails.palustris.domain.DirectThreadResult
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadAcquisitionState
import me.foxtails.palustris.domain.ThreadLimitation
import org.json.JSONArray
import org.json.JSONObject

internal class MisskeyDirectMessageService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId?,
    private val sessionRevision: Long,
    private val sourceInstance: String,
    private val maxResponseBytes: Long = MISSKEY_MAX_RESPONSE_BYTES,
    private val postLoader: suspend (EntityId) -> Post,
) {
    suspend fun conversations(cursor: String?): Page<DirectConversation> {
        val account = requireAccountId()
        val continuations = cursor?.let { decodeCursor(it, account) }
        val mentionsResult = if (continuations?.mentionsExhausted == true) null else
            fetchMentionedNotes(continuations)
        val sentResult = if (continuations?.sentExhausted == true) null else {
            val sentBody = requestBody().apply {
                continuations?.sentUntilId?.let { put("untilId", it) }
                put("userId", account.localId).put("includeReplies", true)
            }
            val raw = JSONArray(api.post(origin, "users/notes", sentBody, maxResponseBytes).body)
            val sentUntilId = raw.lastOrNullJson()?.optString("id")?.takeIf(String::isNotBlank)
            StreamPage(
                posts = directPosts(raw),
                untilId = sentUntilId,
                exhausted = raw.length() == 0 || sentUntilId == null,
            )
        }
        if (mentionsResult == null && sentResult == null) return Page(emptyList())
        val mentionedNotes = mentionsResult?.posts.orEmpty()
        val sentPosts = sentResult?.posts.orEmpty()
        // Mentions order wins duplicates; conversation grouping keeps first-seen roots, while each
        // conversation still selects its latest post for display. Cursor progress stays per endpoint.
        val posts = (mentionedNotes + sentPosts).distinctBy { it.id }
        val byId = posts.associateBy { it.id }
        val items = posts.groupBy { rootFor(it, byId) }.map { (root, thread) ->
            val latest = thread.maxByOrNull(Post::publishedAtEpochMillis) ?: thread.first()
            val rootPost = byId[root]
            DirectConversation(
                id = ConversationId(origin, root.value),
                participants = (thread.map(Post::author) + localAccount(account)).distinctBy { it.id },
                lastPost = latest,
                unread = latest.author.id != account,
                rootPostId = root,
                // A reply at the edge of this page is an anchor, not proof of its missing root.
                identity = if (rootPost != null && rootPost.replyTo == null) {
                    ConversationIdentity.Verified
                } else {
                    ConversationIdentity.Provisional
                },
            )
        }
        val mentionsUntil = mentionsResult?.untilId ?: continuations?.let { if (it.mentionsExhausted) it.mentionsUntilId else null }
        val sentUntil = sentResult?.untilId ?: continuations?.let { if (it.sentExhausted) it.sentUntilId else null }
        val mentionsExhausted = mentionsResult?.exhausted ?: continuations?.mentionsExhausted ?: false
        val sentExhausted = sentResult?.exhausted ?: continuations?.sentExhausted ?: false
        val mentionsSource = mentionsResult?.source ?: continuations?.mentionsSource ?: "feed"
        val fallbackUntil = mentionsResult?.fallbackUntilId ?: continuations?.mentionsFallbackUntilId
        val fallbackExhausted = mentionsResult?.fallbackExhausted ?: continuations?.mentionsFallbackExhausted ?: false
        val next = if (mentionsExhausted && sentExhausted) null else encodeCursor(
            account, mentionsSource, mentionsUntil, mentionsExhausted,
            fallbackUntil, fallbackExhausted, sentUntil, sentExhausted,
        )
        return Page(items, next)
    }

    suspend fun conversationThread(request: DirectThreadRequest, cursor: String? = null): DirectThreadResult {
        val account = accountId ?: throw SourceError.Unsupported("direct.thread")
        // Validate the continuation before any network request. A malformed,
        // foreign, or stale cursor fails here with zero requests. Cursor values
        // are never logged.
        val pendingWork = cursor?.let { decodeThreadCursor(it, account, request.conversationId) }
        validateConversationId(request.conversationId, "direct.thread")
        // Misskey conversation identity is reply-rooted: the conversation value is
        // the root post id. The anchor stays unused so Misskey keeps its own
        // semantics instead of adopting Mastodon conversation identity rules.
        if (pendingWork != null) {
            // Seed with the conversation root as well as the carried accepted
            // IDs. A cyclic or hostile root row must never be reaccepted.
            val seedSeen = setOf(EntityId(origin, request.conversationId.value)) +
                pendingWork.acceptedIds.map { EntityId(origin, it) }.toSet()
            val batch = acquireDescendants(
                pendingWork.pending,
                seedSeen,
                pendingWork.acceptedIds,
                pendingWork.loaded,
                pendingWork.limitations,
                pendingWork.requestsUsed,
            )
            val posts = batch.posts.distinctBy { it.id }
            val nextCursor = batch.pending.takeIf(List<ThreadWorkItem>::isNotEmpty)
                ?.let {
                    encodeThreadCursor(
                        account,
                        request.conversationId,
                        it,
                        batch.acceptedIds,
                        batch.limitations,
                        batch.requestsUsed,
                    )
                }
            return DirectThreadResult(
                posts = posts,
                nextCursor = nextCursor,
                limitations = batch.limitations,
                acquisitionState = when {
                    nextCursor != null -> ThreadAcquisitionState.HasContinuation
                    batch.limitations.isNotEmpty() -> ThreadAcquisitionState.Limited
                    else -> ThreadAcquisitionState.Finished
                },
            )
        }
        val root = postLoader(EntityId(origin, request.conversationId.value))
        val ancestors = mutableListOf<Post>()
        val limitations = mutableListOf<ThreadLimitation>()
        val visited = mutableSetOf(root.id)
        var current = root
        // The chain budget counts every authenticated thread request. The
        // root read counts as one, each ancestor attempt counts as one, and
        // each successful children request counts as one, up to 40 total.
        var chainRequests = 1
        // At most 20 ancestors. The fixed budget is 1 root plus 20 ancestors
        // plus 3 descendant requests, for 24 requests per call at most.
        while (current.replyTo != null && ancestors.size < MAX_THREAD_ANCESTORS &&
            (1 + ancestors.size) < MAX_THREAD_REQUESTS
        ) {
            val parentId = current.replyTo ?: break
            if (!visited.add(parentId)) break
            chainRequests++
            val parent = try {
                postLoader(parentId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ResponseLimitExceeded) {
                throw e
            } catch (e: SourceError.Unsupported) {
                limitations += ThreadLimitation.UnavailableParent(parentId)
                break
            } catch (e: SourceError.AccessDenied) {
                limitations += ThreadLimitation.UnavailableParent(parentId)
                break
            } catch (e: SourceError.UnsupportedCredential) {
                limitations += ThreadLimitation.UnavailableParent(parentId)
                break
            } catch (e: SourceError.ServerUnsupported) {
                limitations += ThreadLimitation.UnavailableParent(parentId)
                break
            }
            ancestors += parent
            current = parent
        }
        if (current.replyTo != null && ancestors.size >= MAX_THREAD_ANCESTORS) {
            limitations += ThreadLimitation.AncestorLimit(ancestors.size, MAX_THREAD_ANCESTORS)
        }
        ancestors.reverse()
        // Bounded breadth-first descent. The fresh call queues the reply root at
        // depth 1. Each mapped direct child is enqueued at depth + 1. Pending
        // work travels in the cursor so a continuation resumes the same BFS.
        // Accepted descendant IDs travel in the cursor so overlapping pages
        // never count or enqueue the same descendant twice. Cumulative
        // limitations travel too, so a terminal call stays Limited after any
        // earlier truncation. Loaded always equals the accepted ID count.
        val seedSeen = (ancestors + root).map { it.id }.toSet()
        val batch = acquireDescendants(
            listOf(ThreadWorkItem(root.id.value, 1, null)),
            seedSeen,
            emptyList(),
            0,
            limitations,
            chainRequests,
        )
        val combined = (ancestors + root + batch.posts).distinctBy { it.id }.filter { it.audience == Audience.Direct }
        val nextCursor = batch.pending.takeIf(List<ThreadWorkItem>::isNotEmpty)
            ?.let {
                encodeThreadCursor(
                    account,
                    request.conversationId,
                    it,
                    batch.acceptedIds,
                    batch.limitations,
                    batch.requestsUsed,
                )
            }
        val allLimitations = batch.limitations
        return DirectThreadResult(
            posts = combined,
            nextCursor = nextCursor,
            limitations = allLimitations,
            acquisitionState = when {
                nextCursor != null -> ThreadAcquisitionState.HasContinuation
                allLimitations.isNotEmpty() -> ThreadAcquisitionState.Limited
                else -> ThreadAcquisitionState.Finished
            },
        )
    }

    private data class ThreadWorkItem(
        val parentId: String,
        val depth: Int,
        val untilId: String?,
    )

    private data class ThreadContinuation(
        val pending: List<ThreadWorkItem>,
        val loaded: Int,
        val acceptedIds: List<String>,
        val limitations: List<ThreadLimitation>,
        val requestsUsed: Int,
    )

    private data class DescendantBatch(
        val posts: List<Post>,
        val pending: List<ThreadWorkItem> = emptyList(),
        val limitations: List<ThreadLimitation> = emptyList(),
        val totalLoaded: Int = 0,
        val acceptedIds: List<String> = emptyList(),
        val requestsUsed: Int = 0,
    )

    /**
     * Bounded breadth-first descent over `notes/children`. At most 3 requests
     * run per call across all queued parents, and at most 40 authenticated
     * thread requests run across the whole continuation chain. Beeline pages
     * with the established Misskey older-than `untilId` direction. Live
     * Misskey/Sharkey ordering is unverified. Only identity equality guards
     * no-progress within one parent pagination chain. Never compare IDs from
     * different spaces and never infer order or time from opaque IDs.
     *
     * Direct-thread bounds are explicit here: depth 10, 3 requests per call,
     * 40 requests per chain, 200 accepted descendants, and a 200-item pending
     * frontier. A full frontier records PendingLimit with the actual frontier
     * size, clears pending work, and ends the batch with no continuation and
     * no parent re-queue. The 200-descendant cap still records NodeLimit.
     * Accepted IDs and cumulative limitations travel in the cursor.
     * This service does not reuse the general post-thread engine.
     */
    private suspend fun acquireDescendants(
        initial: List<ThreadWorkItem>,
        seedSeen: Set<EntityId>,
        seedAcceptedIds: List<String>,
        initialLoaded: Int,
        initialLimitations: List<ThreadLimitation>,
        requestsUsedAtEntry: Int,
    ): DescendantBatch {
        val collected = mutableListOf<Post>()
        val seen = seedSeen.toMutableSet()
        val accepted = seedAcceptedIds.toMutableList()
        val pending = ArrayDeque(initial)
        val visitedRequests = mutableSetOf<Pair<String, String?>>()
        val limitations = initialLimitations.distinct().toMutableList()
        fun recordLimitation(limitation: ThreadLimitation) {
            if (!limitations.contains(limitation)) {
                if (limitations.size < MAX_CURSOR_LIMITATIONS) limitations += limitation
            }
        }

        // Terminal limitations stay bounded but are never dropped. When the
        // list is full the oldest non-terminal entry leaves first so the
        // terminal state survives.
        fun recordTerminal(limitation: ThreadLimitation) {
            if (limitations.contains(limitation)) return
            if (limitations.size < MAX_CURSOR_LIMITATIONS) {
                limitations += limitation
                return
            }
            val evict = limitations.indexOfFirst {
                it !is ThreadLimitation.NodeLimit &&
                    it !is ThreadLimitation.PendingLimit &&
                    it !is ThreadLimitation.RequestLimit
            }
            if (evict >= 0) limitations.removeAt(evict) else limitations.removeAt(0)
            limitations += limitation
        }
        var requestsThisCall = 0
        var requestsUsed = requestsUsedAtEntry
        var totalLoaded = initialLoaded
        fun finishWithRequestLimit(): DescendantBatch {
            val capped = ThreadLimitation.RequestLimit(requestsUsed, MAX_THREAD_REQUESTS_TOTAL)
            recordTerminal(capped)
            return DescendantBatch(
                posts = collected.distinctBy { it.id },
                pending = emptyList(),
                limitations = limitations.distinct(),
                totalLoaded = totalLoaded,
                acceptedIds = accepted.toList(),
                requestsUsed = requestsUsed,
            )
        }
        while (pending.isNotEmpty() && requestsThisCall < THREAD_CHILD_REQUESTS_PER_CALL) {
            if (totalLoaded >= MAX_DESCENDANT_NODES) {
                recordTerminal(ThreadLimitation.NodeLimit(totalLoaded, MAX_DESCENDANT_NODES))
                pending.clear()
                break
            }
            if (requestsUsed >= MAX_THREAD_REQUESTS_TOTAL) {
                return finishWithRequestLimit()
            }
            val work = pending.removeFirst()
            if (work.depth < 1 || work.depth > MAX_DESCENDANT_DEPTH) {
                recordLimitation(ThreadLimitation.DepthLimit(work.depth, MAX_DESCENDANT_DEPTH))
                continue
            }
            if (!visitedRequests.add(work.parentId to work.untilId)) continue
            val body = JSONObject().put("i", token).put("noteId", work.parentId).put("limit", THREAD_CHILDREN_LIMIT)
            work.untilId?.let { body.put("untilId", it) }
            val raw = try {
                JSONArray(api.post(origin, "notes/children", body, maxResponseBytes).body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ResponseLimitExceeded) {
                throw e
            } catch (e: SourceError) {
                throw e
            } catch (e: Exception) {
                throw MisskeyErrorMapper.map(e)
            }
            // Only a successful children request advances the chain budget.
            requestsUsed++
            requestsThisCall++
            if (raw.length() == 0) {
                // An empty page still consumes budget. The chain stops with
                // RequestLimit only when work remains; a drained chain keeps
                // its carried limitations instead of gaining a spurious cap.
                if (requestsUsed >= MAX_THREAD_REQUESTS_TOTAL && pending.isNotEmpty()) {
                    return finishWithRequestLimit()
                }
                continue
            }
            val lastRawId = raw.optJSONObject(raw.length() - 1)?.optString("id")?.takeIf(String::isNotBlank)
            var newInPage = 0
            var frontierExhausted = false
            for (index in 0 until raw.length()) {
                if (totalLoaded >= MAX_DESCENDANT_NODES) break
                val item = raw.optJSONObject(index) ?: continue
                val post = runCatching { MisskeyMapper.post(item, origin) }.getOrNull() ?: continue
                if (post.audience != Audience.Direct) continue
                // Only a direct reply to the queried parent is a child. An
                // unrelated or forked direct row is ignored and never enqueued.
                if (post.replyTo != EntityId(origin, work.parentId)) continue
                if (!seen.add(post.id)) continue
                collected += post
                accepted += post.id.value
                totalLoaded++
                newInPage++
                if (totalLoaded >= MAX_DESCENDANT_NODES) break
                val childDepth = work.depth + 1
                if (childDepth > MAX_DESCENDANT_DEPTH) {
                    recordLimitation(ThreadLimitation.DepthLimit(childDepth, MAX_DESCENDANT_DEPTH))
                } else if (pending.size >= MAX_PENDING_WORK) {
                    // The frontier cannot accept another branch. Record the
                    // actual frontier size, drop all pending work, and stop
                    // the whole batch without re-queuing this parent page.
                    recordTerminal(ThreadLimitation.PendingLimit(pending.size, MAX_PENDING_WORK))
                    pending.clear()
                    frontierExhausted = true
                    break
                } else {
                    pending.addLast(ThreadWorkItem(post.id.value, childDepth, null))
                }
            }
            if (frontierExhausted) break
            if (totalLoaded >= MAX_DESCENDANT_NODES) {
                recordTerminal(ThreadLimitation.NodeLimit(totalLoaded, MAX_DESCENDANT_NODES))
                pending.clear()
                break
            }
            if (requestsUsed >= MAX_THREAD_REQUESTS_TOTAL && pending.isNotEmpty()) {
                return finishWithRequestLimit()
            }
            if (lastRawId != null && lastRawId == work.untilId) {
                recordLimitation(ThreadLimitation.UncertainServerTruncation)
                continue
            }
            // A page with no newly accepted children is uncertain truncation,
            // but a full page with an advancing cursor still paginates so older
            // direct replies are not dropped behind public, malformed, forked,
            // or duplicate rows.
            val noProgress = newInPage == 0 && raw.length() > 0
            if (noProgress) {
                recordLimitation(ThreadLimitation.UncertainServerTruncation)
            }
            val fullPage = raw.length() >= THREAD_CHILDREN_LIMIT
            if (!fullPage) continue
            if (lastRawId == null) {
                if (!noProgress) {
                    recordLimitation(ThreadLimitation.UncertainServerTruncation)
                }
                continue
            }
            if (pending.size >= MAX_PENDING_WORK) {
                recordTerminal(ThreadLimitation.PendingLimit(pending.size, MAX_PENDING_WORK))
                pending.clear()
                break
            }
            pending.addFirst(ThreadWorkItem(work.parentId, work.depth, lastRawId))
        }
        if (requestsUsed >= MAX_THREAD_REQUESTS_TOTAL && pending.isNotEmpty()) {
            return finishWithRequestLimit()
        }
        if (totalLoaded >= MAX_DESCENDANT_NODES && limitations.none { it is ThreadLimitation.NodeLimit }) {
            recordTerminal(ThreadLimitation.NodeLimit(totalLoaded, MAX_DESCENDANT_NODES))
            return DescendantBatch(posts = collected.distinctBy { it.id }, pending = emptyList(), limitations = limitations.distinct(), totalLoaded = totalLoaded, acceptedIds = accepted.toList(), requestsUsed = requestsUsed)
        }
        return DescendantBatch(posts = collected.distinctBy { it.id }, pending = pending.toList(), limitations = limitations.distinct(), totalLoaded = totalLoaded, acceptedIds = accepted.toList(), requestsUsed = requestsUsed)
    }

    private fun encodeThreadCursor(
        account: AccountId,
        conversationId: ConversationId,
        pending: List<ThreadWorkItem>,
        acceptedIds: List<String>,
        limitations: List<ThreadLimitation>,
        requestsUsed: Int,
    ): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            JSONObject().put("version", THREAD_CURSOR_VERSION).put("variant", THREAD_CURSOR_VARIANT)
                .put("origin", origin).put("account", account.localId)
                .put("sessionRevision", sessionRevision).put("sourceInstance", sourceInstance)
                .put("conversationId", conversationId.value)
                .put("loaded", acceptedIds.size)
                .put("pending", org.json.JSONArray(pending.map { work ->
                    JSONObject().put("parentId", work.parentId).put("depth", work.depth)
                        .put("untilId", work.untilId ?: JSONObject.NULL)
                }))
                .put("accepted", org.json.JSONArray(acceptedIds))
                .put("limitations", org.json.JSONArray(limitations.distinct().map(::encodeLimitation)))
                .put("requestsUsed", requestsUsed)
                .toString().toByteArray(Charsets.UTF_8),
        )

    private fun encodeLimitation(limitation: ThreadLimitation): JSONObject = when (limitation) {
        ThreadLimitation.UncertainServerTruncation ->
            JSONObject().put("type", LIMITATION_UNCERTAIN)
        is ThreadLimitation.NodeLimit ->
            JSONObject().put("type", LIMITATION_NODE).put("loaded", limitation.loaded).put("maximum", limitation.maximum)
        is ThreadLimitation.PendingLimit ->
            JSONObject().put("type", LIMITATION_PENDING).put("pending", limitation.pending).put("maximum", limitation.maximum)
        is ThreadLimitation.DepthLimit ->
            JSONObject().put("type", LIMITATION_DEPTH).put("depth", limitation.depth).put("maximum", limitation.maximum)
        is ThreadLimitation.AncestorLimit ->
            JSONObject().put("type", LIMITATION_ANCESTOR).put("loaded", limitation.loaded).put("maximum", limitation.maximum)
        is ThreadLimitation.UnavailableParent ->
            JSONObject().put("type", LIMITATION_UNAVAILABLE_PARENT).put("parentId", limitation.parentId.value)
        is ThreadLimitation.RequestLimit ->
            JSONObject().put("type", LIMITATION_REQUEST).put("used", limitation.used).put("maximum", limitation.maximum)
        // The direct service never emits other shared types. Reject them
        // instead of persisting an unknown cursor shape.
        else -> throw SourceError.Unsupported("direct.thread.continuation")
    }

    private fun decodeLimitation(item: JSONObject?): ThreadLimitation {
        if (item == null) throw SourceError.Unsupported("direct.thread.continuation")
        val type = item.opt("type")
        if (type !is String) throw SourceError.Unsupported("direct.thread.continuation")
        return when (type) {
            LIMITATION_UNCERTAIN -> {
                if (item.keys().asSequence().toSet() != setOf("type")) {
                    throw SourceError.Unsupported("direct.thread.continuation")
                }
                ThreadLimitation.UncertainServerTruncation
            }
            LIMITATION_NODE -> {
                if (item.keys().asSequence().toSet() != setOf("type", "loaded", "maximum") ||
                    item.get("loaded") !is Int || item.get("maximum") !is Int ||
                    item.getInt("loaded") < 0 || item.getInt("loaded") > MAX_DESCENDANT_NODES ||
                    item.getInt("maximum") != MAX_DESCENDANT_NODES
                ) throw SourceError.Unsupported("direct.thread.continuation")
                ThreadLimitation.NodeLimit(item.getInt("loaded"), item.getInt("maximum"))
            }
            LIMITATION_PENDING -> {
                if (item.keys().asSequence().toSet() != setOf("type", "pending", "maximum") ||
                    item.get("pending") !is Int || item.get("maximum") !is Int ||
                    item.getInt("pending") < 0 || item.getInt("pending") > MAX_PENDING_WORK ||
                    item.getInt("maximum") != MAX_PENDING_WORK
                ) throw SourceError.Unsupported("direct.thread.continuation")
                ThreadLimitation.PendingLimit(item.getInt("pending"), item.getInt("maximum"))
            }
            LIMITATION_DEPTH -> {
                if (item.keys().asSequence().toSet() != setOf("type", "depth", "maximum") ||
                    item.get("depth") !is Int || item.get("maximum") !is Int ||
                    item.getInt("depth") < 0 || item.getInt("depth") > MAX_CURSOR_LIMITATION_DEPTH ||
                    item.getInt("maximum") != MAX_DESCENDANT_DEPTH
                ) throw SourceError.Unsupported("direct.thread.continuation")
                ThreadLimitation.DepthLimit(item.getInt("depth"), item.getInt("maximum"))
            }
            LIMITATION_ANCESTOR -> {
                if (item.keys().asSequence().toSet() != setOf("type", "loaded", "maximum") ||
                    item.get("loaded") !is Int || item.get("maximum") !is Int ||
                    item.getInt("loaded") < 0 || item.getInt("loaded") > MAX_THREAD_ANCESTORS ||
                    item.getInt("maximum") != MAX_THREAD_ANCESTORS
                ) throw SourceError.Unsupported("direct.thread.continuation")
                ThreadLimitation.AncestorLimit(item.getInt("loaded"), item.getInt("maximum"))
            }
            LIMITATION_UNAVAILABLE_PARENT -> {
                if (item.keys().asSequence().toSet() != setOf("type", "parentId") ||
                    item.get("parentId") !is String || item.getString("parentId").isBlank()
                ) throw SourceError.Unsupported("direct.thread.continuation")
                ThreadLimitation.UnavailableParent(EntityId(origin, item.getString("parentId")))
            }
            LIMITATION_REQUEST -> {
                if (item.keys().asSequence().toSet() != setOf("type", "used", "maximum") ||
                    item.get("used") !is Int || item.get("maximum") !is Int ||
                    item.getInt("used") < 0 || item.getInt("used") > MAX_THREAD_REQUESTS_TOTAL ||
                    item.getInt("maximum") != MAX_THREAD_REQUESTS_TOTAL
                ) throw SourceError.Unsupported("direct.thread.continuation")
                ThreadLimitation.RequestLimit(item.getInt("used"), item.getInt("maximum"))
            }
            else -> throw SourceError.Unsupported("direct.thread.continuation")
        }
    }

    private fun decodeThreadCursor(cursor: String, account: AccountId, conversationId: ConversationId): ThreadContinuation {
        try {
            val json = JSONObject(String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8))
            val keys = setOf("version", "variant", "origin", "account", "sessionRevision", "sourceInstance", "conversationId", "loaded", "pending", "accepted", "limitations", "requestsUsed")
            if (json.keys().asSequence().toSet() != keys || json.get("version") !is Int ||
                json.getInt("version") != THREAD_CURSOR_VERSION ||
                json.get("sessionRevision") !is Long && json.get("sessionRevision") !is Int ||
                json.getLong("sessionRevision") != sessionRevision ||
                json.get("variant") !is String || json.getString("variant") != THREAD_CURSOR_VARIANT ||
                json.get("origin") !is String || json.getString("origin") != origin ||
                json.get("account") !is String || json.getString("account") != account.localId ||
                json.get("sourceInstance") !is String || json.getString("sourceInstance") != sourceInstance ||
                json.get("conversationId") !is String || json.getString("conversationId") != conversationId.value ||
                json.get("loaded") !is Int || json.getInt("loaded") < 0 ||
                json.getInt("loaded") > MAX_DESCENDANT_NODES ||
                json.get("pending") !is org.json.JSONArray ||
                json.get("accepted") !is org.json.JSONArray ||
                json.get("limitations") !is org.json.JSONArray ||
                json.get("requestsUsed") !is Int || json.getInt("requestsUsed") < 0 ||
                json.getInt("requestsUsed") > MAX_THREAD_REQUESTS_TOTAL
            ) throw SourceError.Unsupported("direct.thread.continuation")
            val acceptedJson = json.getJSONArray("accepted")
            if (acceptedJson.length() > MAX_DESCENDANT_NODES) throw SourceError.Unsupported("direct.thread.continuation")
            val accepted = (0 until acceptedJson.length()).map { index ->
                val value = acceptedJson.opt(index)
                if (value !is String || value.isBlank()) throw SourceError.Unsupported("direct.thread.continuation")
                value
            }
            // Loaded stays strictly consistent with the accepted ID count so
            // overlapping pages cannot inflate the count.
            if (accepted.size != json.getInt("loaded") || accepted.size != accepted.distinct().size) {
                throw SourceError.Unsupported("direct.thread.continuation")
            }
            val limitationsJson = json.getJSONArray("limitations")
            if (limitationsJson.length() > MAX_CURSOR_LIMITATIONS) throw SourceError.Unsupported("direct.thread.continuation")
            val limitations = (0 until limitationsJson.length()).map { index ->
                decodeLimitation(limitationsJson.optJSONObject(index))
            }.distinct()
            val pendingJson = json.getJSONArray("pending")
            if (pendingJson.length() == 0 || pendingJson.length() > MAX_PENDING_WORK) throw SourceError.Unsupported("direct.thread.continuation")
            val pending = (0 until pendingJson.length()).map { index ->
                val item = pendingJson.optJSONObject(index)
                    ?: throw SourceError.Unsupported("direct.thread.continuation")
                if (item.keys().asSequence().toSet() != setOf("parentId", "depth", "untilId") ||
                    item.get("parentId") !is String || item.getString("parentId").isBlank() ||
                    item.get("depth") !is Int || item.getInt("depth") < 1 ||
                    item.getInt("depth") > MAX_DESCENDANT_DEPTH
                ) throw SourceError.Unsupported("direct.thread.continuation")
                val untilId = when (val value = item.opt("untilId")) {
                    JSONObject.NULL -> null
                    is String -> value.takeIf(String::isNotBlank)
                        ?: throw SourceError.Unsupported("direct.thread.continuation")
                    else -> throw SourceError.Unsupported("direct.thread.continuation")
                }
                ThreadWorkItem(item.getString("parentId"), item.getInt("depth"), untilId)
            }
            return ThreadContinuation(pending, json.getInt("loaded"), accepted, limitations, json.getInt("requestsUsed"))
        } catch (error: SourceError) {
            throw error
        } catch (_: Exception) {
            throw SourceError.Unsupported("direct.thread.continuation")
        }
    }

    suspend fun send(message: DirectMessageRequest): Post {
        validateDirectMessageRequest(message)
        val body = JSONObject().put("i", token).put("text", message.text.trim()).put("visibility", "specified")
            .put("visibleUserIds", JSONArray(message.recipients.map(AccountId::localId)))
        message.replyTo?.let { body.put("replyId", it.value) }
        return MisskeyMapper.post(JSONObject(api.post(origin, "notes/create", body, maxResponseBytes).body).getJSONObject("createdNote"), origin)
    }

    suspend fun markConversationRead(id: ConversationId) {
        // Keep this validation-only no-op until a verified server endpoint exists; reads stay local.
        validateConversationId(id, "direct.read")
    }

    private suspend fun fetchMentionedNotes(cursor: Continuations?): StreamPage {
        val fallback = cursor?.mentionsSource == "notifications"
        val body = requestBody().apply {
            (if (fallback) cursor?.mentionsFallbackUntilId else cursor?.mentionsUntilId)?.let { put("untilId", it) }
            if (fallback) put("includeTypes", JSONArray(listOf("mention", "reply")))
        }
        val response = if (fallback) {
            api.post(origin, "i/notifications", body, maxResponseBytes)
        } else try {
            api.post(origin, "notes/mentions", body, maxResponseBytes)
        } catch (error: ApiFailure) {
            if (error.status != 404) throw error
            // The documented degradation for a missing notes/mentions route filters notifications;
            // notification IDs remain separate from note IDs in this latched cursor mode.
            return fetchMentionFallback(cursor)
        }
        val values = JSONArray(response.body)
        val posts = (0 until values.length()).mapNotNull { index ->
            val item = values.optJSONObject(index) ?: return@mapNotNull null
            val note = if (fallback) item.optJSONObject("note") ?: return@mapNotNull null else item
            runCatching { MisskeyMapper.post(note, origin) }.getOrNull()?.takeIf { it.audience == Audience.Direct }
        }
        val lastId = values.lastOrNullJson()?.optString("id")?.takeIf(String::isNotBlank)
        // No usable continuation ID ends this stream; replaying page one would repeat items forever.
        return StreamPage(
            posts, if (fallback) null else lastId,
            values.length() == 0 || (!fallback && lastId == null),
            if (fallback) "notifications" else "feed",
            if (fallback) lastId else null,
            fallback && (values.length() == 0 || lastId == null),
        )
    }
    private suspend fun fetchMentionFallback(cursor: Continuations?): StreamPage {
        val body = requestBody().apply {
            cursor?.mentionsFallbackUntilId?.let { put("untilId", it) }
            put("includeTypes", JSONArray(listOf("mention", "reply")))
        }
        val values = JSONArray(api.post(origin, "i/notifications", body, maxResponseBytes).body)
        val lastId = values.lastOrNullJson()?.optString("id")?.takeIf(String::isNotBlank)
        // No usable continuation ID ends this stream; replaying page one would repeat items forever.
        val posts = (0 until values.length()).mapNotNull { index ->
            val note = values.optJSONObject(index)?.optJSONObject("note") ?: return@mapNotNull null
            runCatching { MisskeyMapper.post(note, origin) }.getOrNull()?.takeIf { it.audience == Audience.Direct }
        }
        return StreamPage(posts, null, values.length() == 0 || lastId == null, "notifications",
            lastId, values.length() == 0 || lastId == null)
    }
    private fun directPosts(values: JSONArray) = (0 until values.length()).mapNotNull {
        val note = values.optJSONObject(it) ?: return@mapNotNull null
        runCatching { MisskeyMapper.post(note, origin) }.getOrNull()?.takeIf { post -> post.audience == Audience.Direct }
    }
    private fun rootFor(post: Post, posts: Map<EntityId, Post>): EntityId {
        var current = post; val visited = mutableSetOf<EntityId>()
        while (current.replyTo != null && visited.add(current.id)) current = posts[current.replyTo] ?: break
        return current.id
    }
    private fun localAccount(account: AccountId): Account {
        val host = java.net.URI(origin).host.orEmpty(); return Account(account, account.localId, "@${account.localId}@$host")
    }
    private fun validateDirectMessageRequest(message: DirectMessageRequest) {
        if (message.recipients.isEmpty() || message.text.isBlank()) throw SourceError.Unsupported("direct.send")
        if (message.recipients.any { it.connection != Connection(origin, Protocol.MISSKEY) || it.localId.isBlank() }) throw SourceError.Unsupported("direct.recipient")
        message.replyTo?.let { if (it.connection != origin || it.value.isBlank()) throw SourceError.Unsupported("direct.reply") }
    }
    private fun validateConversationId(id: ConversationId, feature: String) { if (id.connection != origin || id.value.isBlank()) throw SourceError.Unsupported(feature) }
    private fun requireAccountId() = accountId ?: throw SourceError.Unsupported("notifications.account")
    private data class Continuations(
        val mentionsSource: String, val mentionsUntilId: String?, val mentionsExhausted: Boolean,
        val mentionsFallbackUntilId: String?, val mentionsFallbackExhausted: Boolean,
        val sentUntilId: String?, val sentExhausted: Boolean,
    )
    private data class StreamPage(
        val posts: List<Post>, val untilId: String?, val exhausted: Boolean,
        val source: String = "feed", val fallbackUntilId: String? = null, val fallbackExhausted: Boolean = false,
    )
    private fun JSONArray.lastOrNullJson() = if (length() == 0) null else optJSONObject(length() - 1)

    private fun requestBody() = JSONObject().put("i", token).put("limit", DIRECT_PAGE_LIMIT).put("markAsRead", false)

    // Each endpoint advances independently; the cursor also binds the existing source session and instance identities.
    private fun encodeCursor(account: AccountId, mentionsSource: String, mentionsUntilId: String?, mentionsExhausted: Boolean,
                             mentionsFallbackUntilId: String?, mentionsFallbackExhausted: Boolean,
                             sentUntilId: String?, sentExhausted: Boolean): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            JSONObject().put("version", CURSOR_VERSION).put("variant", CURSOR_VARIANT)
                .put("origin", origin).put("account", account.localId)
                .put("sessionRevision", sessionRevision).put("sourceInstance", sourceInstance)
                .put("mentionsSource", mentionsSource).put("mentionsUntilId", mentionsUntilId ?: JSONObject.NULL)
                .put("mentionsExhausted", mentionsExhausted)
                .put("mentionsFallbackUntilId", mentionsFallbackUntilId ?: JSONObject.NULL)
                .put("mentionsFallbackExhausted", mentionsFallbackExhausted)
                .put("sentUntilId", sentUntilId ?: JSONObject.NULL).put("sentExhausted", sentExhausted)
                .toString().toByteArray(Charsets.UTF_8),
        )

    private fun decodeCursor(cursor: String, account: AccountId): Continuations {
        try {
            val json = JSONObject(String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8))
            val keys = setOf("version", "variant", "origin", "account", "sessionRevision", "sourceInstance", "mentionsSource", "mentionsUntilId", "mentionsExhausted", "mentionsFallbackUntilId", "mentionsFallbackExhausted", "sentUntilId", "sentExhausted")
            if (json.keys().asSequence().toSet() != keys || json.get("version") !is Int || json.getInt("version") != CURSOR_VERSION ||
                json.get("sessionRevision") !is Long && json.get("sessionRevision") !is Int || json.getLong("sessionRevision") != sessionRevision ||
                json.get("variant") !is String || json.getString("variant") != CURSOR_VARIANT || json.get("origin") !is String || json.getString("origin") != origin ||
                json.get("account") !is String || json.getString("account") != account.localId || json.get("sourceInstance") !is String || json.getString("sourceInstance") != sourceInstance ||
                json.get("mentionsSource") !is String || json.getString("mentionsSource") !in setOf("feed", "notifications") ||
                json.get("mentionsExhausted") !is Boolean || json.get("mentionsFallbackExhausted") !is Boolean || json.get("sentExhausted") !is Boolean
            ) throw SourceError.Unsupported("direct.pagination")
            fun optionalId(key: String): String? = when (val value = json.opt(key)) {
                JSONObject.NULL -> null
                is String -> value.takeIf(String::isNotBlank) ?: throw SourceError.Unsupported("direct.pagination")
                else -> throw SourceError.Unsupported("direct.pagination")
            }
            val mentions = optionalId("mentionsUntilId")
            val sent = optionalId("sentUntilId")
            val fallbackUntil = optionalId("mentionsFallbackUntilId")
            if (fallbackUntil != null && json.getString("mentionsSource") != "notifications") throw SourceError.Unsupported("direct.pagination")
            return Continuations(json.getString("mentionsSource"), mentions, json.getBoolean("mentionsExhausted"), fallbackUntil,
                json.getBoolean("mentionsFallbackExhausted"), sent, json.getBoolean("sentExhausted"))
        } catch (error: SourceError) {
            throw error
        } catch (_: Exception) {
            throw SourceError.Unsupported("direct.pagination")
        }
    }
    private companion object {
        const val DIRECT_PAGE_LIMIT = 30
        const val CURSOR_VERSION = 2
        const val CURSOR_VARIANT = "misskey-inbox-v2"
        const val THREAD_CURSOR_VERSION = 4
        const val THREAD_CURSOR_VARIANT = "misskey-direct-thread-v2"
        const val MAX_THREAD_ANCESTORS = 20
        const val MAX_DESCENDANT_DEPTH = 10
        const val THREAD_CHILDREN_LIMIT = 30
        const val THREAD_CHILD_REQUESTS_PER_CALL = 3
        const val MAX_DESCENDANT_NODES = 200
        const val MAX_PENDING_WORK = 200

        // Fixed thread budget per call: 1 reply root plus 20 ancestors plus
        // 3 descendant requests. The loops above enforce each part, so the total
        // stays bounded without comparing opaque identifiers.
        const val MAX_THREAD_REQUESTS = 24

        // Aggregate chain budget across all continuation calls, including the
        // root and ancestor reads. A chain that reaches this total stops with
        // RequestLimit and no pending continuation, even when pages keep
        // advancing. Forty requests bound an endless chain of pages that
        // contain only public, malformed, duplicate, or forked rows.
        const val MAX_THREAD_REQUESTS_TOTAL = 40

        // Strict bound for the cumulative limitation payload in the cursor.
        // The chain records at most a few distinct limitations; 64 leaves
        // headroom while keeping the opaque cursor bounded.
        const val MAX_CURSOR_LIMITATIONS = 64

        // Depth values in stored limitations stay small. The bound only keeps
        // malformed payloads out; live depths never approach it.
        const val MAX_CURSOR_LIMITATION_DEPTH = 1_000
        const val LIMITATION_UNCERTAIN = "uncertain"
        const val LIMITATION_NODE = "node"
        const val LIMITATION_PENDING = "pending"
        const val LIMITATION_DEPTH = "depth"
        const val LIMITATION_ANCESTOR = "ancestor"
        const val LIMITATION_UNAVAILABLE_PARENT = "unavailableParent"
        const val LIMITATION_REQUEST = "request"
    }
}
