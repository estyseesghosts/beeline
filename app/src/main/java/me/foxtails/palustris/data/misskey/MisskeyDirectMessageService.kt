package me.foxtails.palustris.data.misskey

import java.util.Base64
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.ConversationIdentity
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectThreadRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
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

    suspend fun conversationThread(request: DirectThreadRequest): List<Post> {
        validateConversationId(request.conversationId, "direct.thread")
        // Misskey conversation identity is reply-rooted: the conversation value is
        // the root post id. The anchor stays unused so Misskey keeps its own
        // semantics instead of adopting Mastodon conversation identity rules.
        return postLoader(EntityId(origin, request.conversationId.value)).let { root ->
            val ancestors = mutableListOf<Post>(); val visited = mutableSetOf(root.id); var current = root
            while (current.replyTo != null && visited.add(current.replyTo!!)) {
                val parent = postLoader(current.replyTo!!); ancestors += parent; current = parent
            }
            ancestors.reverse()
            val children = JSONArray(api.post(origin, "notes/children", JSONObject().put("i", token).put("noteId", root.id.value).put("limit", 30), maxResponseBytes).body)
            val descendants = (0 until children.length()).map { MisskeyMapper.post(children.getJSONObject(it), origin) }
            (ancestors + root + descendants).distinctBy { it.id }.filter { it.audience == Audience.Direct }
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
    }
}
