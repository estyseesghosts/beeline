package me.foxtails.palustris.data.mastodon

import java.io.InputStream
import java.net.URLEncoder
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.transport.HttpResponse
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.ResponseLimitExceeded
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectMessageSource
import me.foxtails.palustris.domain.DirectThreadRequest
import me.foxtails.palustris.domain.DirectThreadResult
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationPage
import me.foxtails.palustris.domain.MutedHashtag
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.PushProviderInfo
import me.foxtails.palustris.domain.ReportRequest
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadAcquisitionState
import me.foxtails.palustris.domain.ThreadContext
import me.foxtails.palustris.domain.ThreadContinuation
import me.foxtails.palustris.domain.ThreadRefreshHint
import me.foxtails.palustris.domain.ThreadSessionKey
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.hashtagBody
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONObject

internal const val MASTODON_MAX_RESPONSE_BYTES = 4L * 1024 * 1024

class MastodonSource(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
    initialCapabilities: ServerCapabilities = DEFAULT_CAPABILITIES,
    private val capabilityProbe: CapabilityProbe? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sessionRevision: Long = 0L,
    private val onCapabilitiesUpdated: ((ServerCapabilities) -> Unit)? = null,
) : SocialSource, DirectMessageSource {
    private val _capabilities = kotlinx.coroutines.flow.MutableStateFlow(
        if (initialCapabilities.timelines.isEmpty() && initialCapabilities.actions.isEmpty() &&
            initialCapabilities.audiences.isEmpty() && initialCapabilities.notifications == NotificationCapabilities()
        ) {
            DEFAULT_CAPABILITIES.copy(canPublish = initialCapabilities.canPublish)
        } else {
            initialCapabilities
        },
    )
    private val profileService = MastodonProfileService(origin, token, api, accountId)
    private val selfProfileService = MastodonSelfProfileService(origin, token, api, accountId)
    private val directMessageService = MastodonDirectMessageService(origin, token, api, accountId, profileService)
    private val notificationService = MastodonNotificationService(origin, token, api, accountId, clock)
    private val moderationService = MastodonModerationService(origin, token, api, accountId)
    private val pushService = MastodonPushService(origin, token, api, accountId)
    private val streamService = MastodonStreamService(origin, token, api, accountId)
    private val threadService = MastodonThreadService(origin, token, api, accountId, sessionRevision)
    private val sourceInstance = UUID.randomUUID().toString()
    private val pageClient = MastodonPageClient(origin, token, api, accountId.localId, sessionRevision, sourceInstance)
    private val timelineService = MastodonTimelineService(pageClient, origin)
    override val capabilities: ServerCapabilities get() = _capabilities.value
    override fun observeCapabilities(): Flow<ServerCapabilities> = _capabilities

    /**
     * Bounds capability refresh retries after a metadata failure. Without it, a failed probe
     * clears [ServerCapabilities.capabilitiesLastUpdated] and every later request re-probes
     * during an outage. Reads and writes happen from IO dispatcher threads.
     */
    @Volatile
    private var capabilitiesRetryNotBefore = 0L

    override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = request("timeline") {
        // Reject invalid continuations before capability refresh can issue its own probe.
        timelineService.validateCursor(timeline, cursor)
        refreshCapabilities()
        timelineService.timeline(timeline, cursor, capabilities)
    }

    override suspend fun post(id: EntityId): Post = request("post") {
        validatePostId(id, "post")
        MastodonMapper.post(api.get(origin, "v1/statuses/${id.value.encodeMastodonPathSegment()}", token, MASTODON_MAX_RESPONSE_BYTES).body.toJson(), origin)
    }

    override suspend fun threadContext(
        focalId: EntityId,
        continuation: ThreadContinuation?,
    ): ThreadContext = request("thread") {
        validatePostId(focalId, "thread")
        if (capabilities.threads == CapabilityStatus.Unsupported || capabilities.threads == CapabilityStatus.Denied) {
            throw SourceError.Unsupported("thread")
        }
        val key = ThreadSessionKey(accountId, sessionRevision, focalId)
        if (continuation != null && continuation.sessionKey != key) {
            throw SourceError.Unsupported("thread.continuation")
        }
        threadService.context(focalId, continuation)
    }

    override suspend fun profile(id: AccountId): Account = request("profile.details") { profileService.profile(id) }

    override suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String?): Page<Post> = request("profile.timeline") {
        profileService.timeline(query, cursor)
    }

    override suspend fun profileRelationship(id: AccountId): ProfileRelationship = request("profile.relationship") {
        profileService.relationship(id)
    }

    override suspend fun followProfile(id: AccountId): ProfileRelationship = request("profile.follow") {
        profileService.follow(id)
    }

    override suspend fun unfollowProfile(id: AccountId): ProfileRelationship = request("profile.unfollow") {
        profileService.unfollow(id)
    }

    override suspend fun setBlocked(id: AccountId, blocked: Boolean): ProfileRelationship = request("profile.block") {
        moderationService.setBlocked(id, blocked)
    }

    override suspend fun setMuted(id: AccountId, muted: Boolean): ProfileRelationship = request("profile.mute") {
        moderationService.setMuted(id, muted)
    }

    override suspend fun pinnedPosts(id: AccountId): List<Post> = request("profile.pinned") { profileService.pinnedPosts(id) }

    override suspend fun create(post: CreatePostRequest): Post = request("create") {
        if (post.replyTo != null && post.quoteOf != null) throw SourceError.Unsupported("create.reply.quote")
        post.replyTo?.let { validatePostId(it, "create.reply-origin") }
        post.quoteOf?.let { validatePostId(it, "create.quote-origin") }
        if (post.quoteOf != null && capabilities.quotes != CapabilityStatus.Supported) {
            throw SourceError.Unsupported("quote")
        }
        if (post.quoteOf != null && (post.attachments.isNotEmpty() || post.poll != null)) {
            throw SourceError.Unsupported("quote.attachments-or-poll")
        }
        if (post.attachments.isNotEmpty()) throw SourceError.Unsupported("create.attachments")
        if (post.poll != null) throw SourceError.Unsupported("create.poll")
        val fields = buildList {
            add("status" to post.text)
            add("visibility" to post.audience.toMastodonVisibility())
            post.contentWarning?.let { add("spoiler_text" to it) }
            post.replyTo?.let { add("in_reply_to_id" to it.value) }
            post.quoteOf?.let { add("quoted_status_id" to it.value) }
        }
        MastodonMapper.post(api.postForm(origin, "api/v1/statuses", fields, token, MASTODON_MAX_RESPONSE_BYTES).body.toJson(), origin)
    }

    override suspend fun conversations(cursor: String?): Page<DirectConversation> = request("direct.conversations") { directMessageService.conversations(cursor) }

    override suspend fun conversationThread(request: DirectThreadRequest, cursor: String?): DirectThreadResult =
        this.request("direct.thread") { directMessageService.conversationThread(request, cursor) }

    override suspend fun sendDirectMessage(request: DirectMessageRequest): Post = request("direct.send") { directMessageService.sendDirectMessage(request) }

    override suspend fun markConversationRead(id: ConversationId) = request("direct.read") { directMessageService.markConversationRead(id) }

    override suspend fun loadEditableProfile(): EditableProfile = request("profile.editable.load") {
        refreshCapabilities()
        selfProfileService.load(capabilities.profile.editable)
    }

    override suspend fun updateEditableProfile(patch: EditableProfilePatch): EditableProfile = request("profile.editable.update") {
        refreshCapabilities()
        selfProfileService.update(patch, capabilities.profile.editable)
    }

    override suspend fun customEmojis(): List<CustomEmoji> = request("emoji.catalog") {
        MastodonEmojiMapper.parseCatalog(api.get(origin, "v1/custom_emojis", token, MASTODON_MAX_RESPONSE_BYTES).body, origin)
    }

    override suspend fun react(id: EntityId, choice: EmojiChoice) = request("react") {
        validatePostId(id, "react")
        requireReactionMutation()
        // A failed mutation returns its normalized error. A resource 404 is not proof that
        // the extension is unsupported, so support stays until explicit server evidence says
        // otherwise. Only the advertisement establishes reaction support.
        mutateEmojiReaction(id, choice.submissionValue, selected = true)
        Unit
    }

    override suspend fun removeReaction(id: EntityId, choice: EmojiChoice) = request("react") {
        validatePostId(id, "react")
        requireReactionMutation()
        mutateEmojiReaction(id, choice.submissionValue, selected = false)
        Unit
    }

    private fun requireReactionMutation() {
        if (capabilities.emoji.reactionMutation != CapabilityStatus.Supported) {
            throw SourceError.Unsupported("react")
        }
    }

    private suspend fun mutateEmojiReaction(
        id: EntityId,
        submission: String,
        selected: Boolean,
    ): PostActionResult {
        val response = if (selected) {
            api.putForm(origin, emojiReactionEndpoint(id, submission), emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        } else {
            api.delete(origin, emojiReactionEndpoint(id, submission), token, MASTODON_MAX_RESPONSE_BYTES)
        }
        return PostActionResult(
            post = response.optionalPost(origin),
            selected = selected,
        )
    }

    private fun emojiReactionEndpoint(id: EntityId, submission: String): String =
        origin.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("pleroma")
            .addPathSegment("statuses")
            .addPathSegment(id.value)
            .addPathSegment("reactions")
            .addPathSegment(submission)
            .build()
            .encodedPath
            .removePrefix("/")

    override suspend fun favorite(id: EntityId) = request("favorite") {
        validatePostId(id, "favorite")
        api.postForm(origin, "api/v1/statuses/${id.value.encodeMastodonPathSegment()}/favourite", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun favorite(id: EntityId, favouriteEmoji: String) = favorite(id)

    override suspend fun unfavorite(id: EntityId, favouriteEmoji: String?) = request("favorite") {
        validatePostId(id, "favorite")
        api.postForm(origin, "api/v1/statuses/${id.value.encodeMastodonPathSegment()}/unfavourite", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun setPrimaryFavourite(
        id: EntityId,
        favouriteEmoji: String,
        selected: Boolean,
    ): PostActionResult = request("favorite") {
        validatePostId(id, "favorite")
        val endpoint = if (selected) "favourite" else "unfavourite"
        val response = api.postForm(origin, "api/v1/statuses/${id.value.encodeMastodonPathSegment()}/$endpoint", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        PostActionResult(
            post = response.optionalPost(origin),
            selected = selected,
        )
    }

    override suspend fun renote(id: EntityId) = request("renote") {
        validatePostId(id, "renote")
        api.postForm(origin, "api/v1/statuses/${id.value.encodeMastodonPathSegment()}/reblog", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun unrenote(id: EntityId, ownRepostId: EntityId?) = request("renote") {
        validatePostId(id, "renote")
        api.postForm(origin, "api/v1/statuses/${id.value.encodeMastodonPathSegment()}/unreblog", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun setReshared(id: EntityId, selected: Boolean, ownRepostId: EntityId?): PostActionResult = request("renote") {
        validatePostId(id, "renote")
        val endpoint = if (selected) "reblog" else "unreblog"
        val response = api.postForm(origin, "api/v1/statuses/${id.value.encodeMastodonPathSegment()}/$endpoint", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        val mapped = response.optionalPost(origin)
        PostActionResult(
            post = mapped,
            selected = selected,
            createdRepostId = if (selected) mapped?.id else null,
        )
    }

    override suspend fun save(id: EntityId) = request("save") {
        validatePostId(id, "save")
        api.postForm(origin, "api/v1/statuses/${id.value.encodeMastodonPathSegment()}/bookmark", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun unsave(id: EntityId) = request("save") {
        validatePostId(id, "save")
        api.postForm(origin, "api/v1/statuses/${id.value.encodeMastodonPathSegment()}/unbookmark", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun setSaved(id: EntityId, selected: Boolean): PostActionResult = request("save") {
        validatePostId(id, "save")
        val endpoint = if (selected) "bookmark" else "unbookmark"
        val response = api.postForm(origin, "api/v1/statuses/${id.value.encodeMastodonPathSegment()}/$endpoint", emptyList(), token, MASTODON_MAX_RESPONSE_BYTES)
        val mapped = response.optionalPost(origin)
        PostActionResult(post = mapped, selected = selected)
    }

    @Deprecated("Use create(CreatePostRequest(quoteOf = ...))")
    override suspend fun quote(id: EntityId, text: String) {
        create(CreatePostRequest(text = text, quoteOf = id))
    }

    override suspend fun savedPosts(cursor: String?): Page<Post> = request("bookmarks") {
        val route = MastodonPageRoute("bookmarks", "v1/bookmarks?limit=40", "/api/v1/bookmarks", "", "bookmarks")
        val currentUrl = pageClient.currentUrl(route, cursor)
        val response = pageClient.getPage(route, cursor)
        val statuses = JSONArray(response.body)
        Page(
            items = (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) },
            nextCursor = pageClient.nextCursor(response, route, currentUrl),
        )
    }

    override suspend fun notifications(cursor: String?): Page<me.foxtails.palustris.domain.Notification> = request("notifications") { notificationService.notifications(cursor) }

    override suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor?): NotificationPage = request("notifications") {
        notificationService.notifications(query, cursor)
    }

    override suspend fun fetchNewerNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage = request("notifications") {
        notificationService.fetchNewerNotifications(query, checkpoint)
    }

    override suspend fun fetchOlderNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage = request("notifications") {
        notificationService.fetchOlderNotifications(query, checkpoint)
    }

    override suspend fun notificationUnreadState(): NotificationUnreadState = request("notifications.unread") { notificationService.unreadState() }

    override suspend fun pushProviderInfo(): PushProviderInfo = request("notifications.push.info") { pushService.providerInfo() }

    override suspend fun acknowledgeNotifications(): NotificationAcknowledgement = request("notifications.acknowledge") { notificationService.acknowledge() }

    override suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) = request("notifications.followRequest") {
        notificationService.respondToFollowRequest(targetAccountId, accept)
    }

    private fun validatePostId(id: EntityId, feature: String) {
        if (id.connection != origin) throw SourceError.ForeignOrigin(feature)
        if (id.value.isBlank()) throw SourceError.Unsupported(feature)
    }

    override suspend fun dismissNotification(id: EntityId) = request("notifications.dismiss") { notificationService.dismiss(id) }

    override suspend fun blockedAccounts(cursor: ModerationCursor?): ModerationPage<ModerationAccount> = request("moderation.blocked") {
        moderationService.blocked(cursor)
    }

    override suspend fun mutedAccounts(cursor: ModerationCursor?): ModerationPage<ModerationAccount> = request("moderation.muted") {
        moderationService.muted(cursor)
    }

    override suspend fun mutedHashtags(cursor: ModerationCursor?): ModerationPage<MutedHashtag> = request("moderation.hashtags") {
        moderationService.hashtags(cursor)
    }

    override suspend fun removeBlockedAccount(entry: ModerationAccount) = request("moderation.removeBlocked") { moderationService.removeBlocked(entry) }

    override suspend fun removeMutedAccount(entry: ModerationAccount) = request("moderation.removeMuted") { moderationService.removeMuted(entry) }

    override suspend fun report(request: ReportRequest) = request("moderation.report") { moderationService.report(request) }

    override suspend fun queryOwnedPushSubscription(knownEndpoint: ValidatedUrl?): PushSubscription? = request("notifications.push.query") {
        pushService.query(knownEndpoint)
    }

    override suspend fun createOrReplacePushSubscription(
        spec: PushSubscriptionSpec,
        previous: PushSubscription?,
    ): PushSubscription = request("notifications.push.create") { pushService.createOrReplace(spec, previous) }

    override suspend fun updatePushAlertPolicy(
        subscription: PushSubscription,
        alerts: Set<NotificationCategory>,
    ): PushSubscription = request("notifications.push.update") { pushService.updatePolicy(subscription, alerts) }

    override suspend fun removePushSubscription(subscription: PushSubscription) = request("notifications.push.remove") { pushService.remove(subscription) }

    override fun streamEvents(): Flow<Event> = streamService.events()

    override suspend fun uploadMedia(file: InputStream, mimeType: String) = request("media.upload") {
        MastodonMapper.attachment(api.postMultipart(origin, "api/v1/media", file, mimeType, bearerToken = token, maxResponseBytes = MASTODON_MAX_RESPONSE_BYTES).body.toJson())
    }

    override suspend fun search(query: String): List<Post> = request("search") {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val statuses = JSONObject(api.get(origin, "v2/search?q=$encodedQuery", token, MASTODON_MAX_RESPONSE_BYTES).body)
            .optJSONArray("statuses") ?: JSONArray()
        (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) }
    }

    override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = request("search.hashtag") {
        val normalized = hashtagBody(tag)
        val encodedTag = URLEncoder.encode(normalized, Charsets.UTF_8.name())
        val route = MastodonPageRoute(
            "hashtag", "v1/timelines/tag/$encodedTag?limit=40", "/api/v1/timelines/tag/$encodedTag", normalized, "tag",
        )
        val currentUrl = pageClient.currentUrl(route, cursor)
        val response = pageClient.getPage(route, cursor)
        val statuses = JSONArray(response.body)
        Page(
            items = (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) },
            nextCursor = pageClient.nextCursor(response, route, currentUrl),
        )
    }

    override suspend fun searchAccounts(query: String): List<Account> = request("search.accounts") {
        val handle = query.trim().removePrefix("@").takeIf { it.isNotBlank() }
            ?: throw SourceError.Unsupported("account search")
        listOf(MastodonMapper.account(
            api.get(origin, "v1/accounts/lookup?acct=${URLEncoder.encode(handle, Charsets.UTF_8.name())}", token, MASTODON_MAX_RESPONSE_BYTES)
                .body.toJson(), origin,
        ))
    }

    private suspend fun refreshCapabilities() {
        val probe = capabilityProbe ?: return
        val now = clock()
        if (now < capabilitiesRetryNotBefore) return
        val schemaCurrent = capabilities.capabilitySchemaVersion == ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION
        if (schemaCurrent && now - capabilities.capabilitiesLastUpdated < CAPABILITIES_TTL_MILLIS) return
        try {
            val probed = probe.probeCapabilities(Connection(origin, Protocol.MASTODON))
            val updated = probed.copy(
                canPublish = probed.canPublish || capabilities.canPublish,
                notifications = probed.notifications.takeVerifiedOr(capabilities.notifications),
                profile = probed.profile.takeVerifiedOr(capabilities.profile),
                emoji = probed.emoji.takeVerifiedOr(capabilities.emoji),
            )
            _capabilities.value = updated
            capabilitiesRetryNotBefore = 0L
            // Publish the refreshed snapshot through the account/session owner. The owner
            // compares the session revision before it persists.
            onCapabilitiesUpdated?.invoke(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _capabilities.value = capabilities.copy(capabilitiesLastUpdated = 0)
            capabilitiesRetryNotBefore = now + CAPABILITIES_RETRY_MILLIS
        }
    }

    private suspend fun <T> request(operation: String, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: ResponseLimitExceeded) {
            throw SourceError.ResourceLimit(operation)
        } catch (e: SourceError) {
            throw e
        } catch (e: ApiFailure) {
            throw MastodonErrorMapper.map(e)
        } catch (e: Exception) {
            throw MastodonErrorMapper.map(e)
        }
    }

    private companion object {
        const val CAPABILITIES_TTL_MILLIS = 5 * 60 * 1000L

        /** Minimum delay before a failed capability probe retries. Bounds outage traffic. */
        const val CAPABILITIES_RETRY_MILLIS = 30 * 1000L
        val DEFAULT_CAPABILITIES = ServerCapabilities(
            timelines = setOf(Timeline.Home, Timeline.Local, Timeline.Federated),
            audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
            actions = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.Bookmark),
            emoji = EmojiCapabilities(catalog = CapabilityStatus.Supported),
            primaryFavourite = me.foxtails.palustris.domain.PrimaryFavouriteCapability(
                me.foxtails.palustris.domain.CapabilityStatus.Supported,
                me.foxtails.palustris.domain.PrimaryFavouriteMode.Native,
            ),
             savedPosts = me.foxtails.palustris.domain.SavedPostsCapability(
                 me.foxtails.palustris.domain.CapabilityStatus.Supported,
                 me.foxtails.palustris.domain.SavedPostsKind.Bookmarks,
             ),
             likedPosts = me.foxtails.palustris.domain.CapabilityStatus.Supported,
             threads = CapabilityStatus.Supported,
         )
    }
}

private fun HttpResponse.optionalPost(origin: String): Post? = runCatching {
    JSONObject(body).takeIf { it.optString("id").isNotBlank() }?.let { MastodonMapper.post(it, origin) }
}.getOrNull()

private fun ProfileCapabilities.takeVerifiedOr(previous: ProfileCapabilities): ProfileCapabilities =
    if (this == ProfileCapabilities()) previous else this

private fun EmojiCapabilities.takeVerifiedOr(previous: EmojiCapabilities): EmojiCapabilities =
    if (this == EmojiCapabilities()) previous else this

private fun NotificationCapabilities.takeVerifiedOr(previous: NotificationCapabilities): NotificationCapabilities =
    if (this == NotificationCapabilities()) previous else this

private fun Audience.toMastodonVisibility(): String = when (this) {
    Audience.Public -> "public"
    Audience.Unlisted -> "unlisted"
    Audience.Followers -> "private"
    Audience.Direct -> "direct"
}

private fun String.toJson(): JSONObject = JSONObject(this)
