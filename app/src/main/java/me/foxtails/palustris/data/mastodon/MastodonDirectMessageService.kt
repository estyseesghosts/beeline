package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal class MastodonDirectMessageService(
    private val origin: String,
    private val token: String,
    private val api: AuthenticatedHttpClient,
    private val accountId: AccountId,
    private val profileService: MastodonProfileService,
) {
    suspend fun conversations(cursor: String?): Page<DirectConversation> {
        val response = if (cursor == null) {
            api.getUrl(directConversationsUrl().toString(), token, MASTODON_MAX_RESPONSE_BYTES)
        } else {
            api.getUrl(validateDirectConversationsUrl(cursor).toString(), token, MASTODON_MAX_RESPONSE_BYTES)
        }
        val values = JSONArray(response.body)
        val items = (0 until values.length()).mapNotNull { index ->
            MastodonMapper.directConversation(values.getJSONObject(index), origin)
                ?.takeIf { it.lastPost.audience == Audience.Direct }
        }
        return Page(items, response.linkHeaderCursor())
    }

    suspend fun conversationThread(request: DirectThreadRequest, cursor: String? = null): DirectThreadResult {
        if (cursor != null) throw SourceError.Unsupported("direct.thread.continuation")
        validateConversationId(request.conversationId, "direct.thread")
        val anchor = loadAnchor(request.anchor)
        val context = JSONObject(api.get(
            origin,
            "api/v1/statuses/${anchor.id.value.encodeMastodonPathSegment()}/context",
            token,
            MASTODON_MAX_RESPONSE_BYTES,
        ).body)
        val ancestors = context.optJSONArray("ancestors").toPostList(origin)
        val descendants = context.optJSONArray("descendants").toPostList(origin)
        val posts = (ancestors + listOf(anchor) + descendants)
            .filter { it.audience == Audience.Direct }
            .distinctBy { it.id }
        // Mastodon context returns the full thread in two requests. It has no
        // continuation, so every call finishes with no cursor.
        return DirectThreadResult(posts = posts, acquisitionState = ThreadAcquisitionState.Finished)
    }

    /**
     * Loads the requested anchor through a supported status endpoint. The
     * account token scopes visibility, so a deleted, foreign, or filtered status
     * is a normalized unsupported result. A non-direct anchor is rejected rather
     * than substituted with an unrelated conversation.
     */
    private suspend fun loadAnchor(anchor: EntityId): Post {
        if (anchor.connection != origin || anchor.value.isBlank()) {
            throw SourceError.Unsupported("direct.thread")
        }
        val body = try {
            api.get(origin, "api/v1/statuses/${anchor.value.encodeMastodonPathSegment()}", token, MASTODON_MAX_RESPONSE_BYTES).body
        } catch (error: HttpStatusFailure) {
            if (error.status == 403 || error.status == 404 || error.status == 410) {
                throw SourceError.Unsupported("direct.thread")
            }
            throw error
        }
        val post = runCatching { MastodonMapper.post(body.toJson(), origin) }
            .getOrElse { throw SourceError.Unsupported("direct.thread") }
        if (post.audience != Audience.Direct) throw SourceError.Unsupported("direct.thread")
        return post
    }

    suspend fun sendDirectMessage(request: DirectMessageRequest): Post {
        validateDirectMessageRequest(request)
        val mentions = request.recipients.map { profileService.profile(it).handle }.distinct().joinToString(" ")
        val status = listOf(mentions, request.text.trim()).filter(String::isNotBlank).joinToString(" ")
        val fields = buildList {
            add("status" to status)
            add("visibility" to "direct")
            request.replyTo?.let { add("in_reply_to_id" to it.value) }
        }
        return MastodonMapper.post(api.postForm(origin, "api/v1/statuses", fields, token, MASTODON_MAX_RESPONSE_BYTES).body.toJson(), origin)
    }

    suspend fun markConversationRead(id: ConversationId) {
        validateConversationId(id, "direct.read")
        api.postForm(origin, "api/v1/conversations/${id.value.encodeMastodonPathSegment()}/read", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
    }

    private fun validateDirectMessageRequest(request: DirectMessageRequest) {
        if (request.recipients.isEmpty() || request.text.isBlank()) throw SourceError.Unsupported("direct.send")
        if (request.recipients.any {
                it.connection != Connection(origin, Protocol.MASTODON) || it.localId.isBlank()
            }
        ) throw SourceError.Unsupported("direct.recipient")
        request.replyTo?.let { if (it.connection != origin || it.value.isBlank()) throw SourceError.Unsupported("direct.reply") }
    }

    private fun validateConversationId(id: ConversationId, feature: String) {
        if (id.connection != origin || id.value.isBlank()) throw SourceError.Unsupported(feature)
    }

    private fun directConversationsUrl(): HttpUrl = origin.toHttpUrl().newBuilder()
        .addPathSegments("api/v1/conversations")
        .addQueryParameter("limit", DIRECT_CONVERSATION_LIMIT.toString())
        .build()

    private fun validateDirectConversationsUrl(cursor: String): HttpUrl {
        val page = cursor.toHttpUrlOrNull() ?: throw SourceError.Unsupported("direct.pagination")
        val authenticatedOrigin = origin.toHttpUrl()
        if (page.scheme != authenticatedOrigin.scheme || page.host != authenticatedOrigin.host ||
            page.port != authenticatedOrigin.port || page.username.isNotEmpty() || page.password.isNotEmpty() ||
            page.fragment != null || page.encodedPath != "/api/v1/conversations"
        ) throw SourceError.Unsupported("direct.pagination")
        return page
    }

    private companion object { const val DIRECT_CONVERSATION_LIMIT = 40 }
}

private fun String.toJson(): JSONObject = JSONObject(this)

private fun JSONArray?.toPostList(origin: String): List<Post> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        runCatching { MastodonMapper.post(getJSONObject(index), origin) }.getOrNull()
    }
}
