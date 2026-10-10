package me.foxtails.palustris.data.misskey

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.AppMessages
import me.foxtails.palustris.data.transport.ResponseLimitExceeded
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
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
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.FavouriteArtworkStyle
import me.foxtails.palustris.domain.MediaUploadRequest
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationPage
import me.foxtails.palustris.domain.MutedHashtag
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationReadSemantics
import me.foxtails.palustris.domain.NotificationUnreadPrecision
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.ProfileCapability
import me.foxtails.palustris.domain.ProfileCapabilityQuery
import me.foxtails.palustris.domain.ProfileCapabilityResult
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushProviderInfo
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.ReportRequest
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadContext
import me.foxtails.palustris.domain.ThreadContinuation
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.Translation
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.domain.capabilityStatus
import me.foxtails.palustris.domain.hashtagBody
import me.foxtails.palustris.domain.hashtags.HashtagQuery
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import me.foxtails.palustris.domain.normalizeFavouriteEmoji
import me.foxtails.palustris.domain.unsupported
import org.json.JSONArray
import org.json.JSONObject

internal const val MISSKEY_MAX_RESPONSE_BYTES = 4L * 1024 * 1024

/** Extra inner arrays that one `notes/search-by-tag` request carries. */
private const val MAX_COMBINED_HASHTAGS = 10

class MisskeySource(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val initialCapabilities: ServerCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
    private val accountId: AccountId? = null,
    private val capabilityProbe: CapabilityProbe = MisskeyCapabilityProbe(api, token.takeIf { accountId != null }, MISSKEY_MAX_RESPONSE_BYTES),
    private val capabilityCache: CapabilityCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sessionRevision: Long = 0L,
    /** Opaque identity from [CapabilityCache.activate] for this session. Null only for anonymous or test sources. */
    private val sessionIdentity: Long? = null,
    /** Rejects probe results after the session store replaces or removes this source's session. */
    private val isCurrentSession: () -> Boolean = { true },
    private val onCapabilitiesUpdated: ((ServerCapabilities) -> Unit)? = null,
    private val appMessages: AppMessages = AppMessages.Default,
    private val monotonicClock: () -> Long = System::nanoTime,
) : SocialSource, DirectMessageSource {
    override val favouriteArtworkStyle: FavouriteArtworkStyle = FavouriteArtworkStyle.Heart
    private val sourceInstance = UUID.randomUUID().toString()
    private val cacheKey = CapabilityCacheKey(
        origin,
        accountId ?: AccountId(Connection(origin, Protocol.MISSKEY), "anonymous"),
        sessionRevision,
    )
    private val _capabilities = MutableStateFlow(initialCapabilities)
    val capabilitiesFlow: StateFlow<ServerCapabilities> = _capabilities
    private val profileService = MisskeyProfileService(origin, token, api, accountId, MISSKEY_MAX_RESPONSE_BYTES)
    private val directMessageService = MisskeyDirectMessageService(origin, token, api, accountId, sessionRevision, sourceInstance, MISSKEY_MAX_RESPONSE_BYTES) { id -> post(id) }
    private val notificationService = MisskeyNotificationService(origin, token, api, accountId, clock, MISSKEY_MAX_RESPONSE_BYTES)
    private val moderationService = accountId?.let { MisskeyModerationService(origin, token, api, it, MISSKEY_MAX_RESPONSE_BYTES) }
    private val pushService = MisskeyPushService(origin, token, api, accountId, MISSKEY_MAX_RESPONSE_BYTES)
    private val streamService = MisskeyStreamService(origin, token, api, accountId)
    private val mediaService = MisskeyMediaService(origin, token, api)
    private val discoveryService = MisskeyDiscoveryService(origin, token, api, accountId)
    private val timelineService = MisskeyTimelineService(origin, token, api, MISSKEY_MAX_RESPONSE_BYTES)
    private val threadService = MisskeyThreadService(
        origin = origin,
        token = token,
        api = api,
        accountId = accountId,
        sessionRevision = sessionRevision,
        clock = clock,
        monotonicClock = monotonicClock,
        maxResponseBytes = MISSKEY_MAX_RESPONSE_BYTES,
        validatePostId = ::validatePostId,
    )
    override val capabilities: ServerCapabilities get() = _capabilities.value
    override fun observeCapabilities(): Flow<ServerCapabilities> = capabilitiesFlow

    override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = request("timeline", invalidateCapabilitiesOnNotFound = true) {
            refreshCapabilities()
            timelineService.timeline(timeline, cursor, capabilities)
    }

    override suspend fun post(id: EntityId): Post = request("post") {
        validatePostId(id, "post")
        val response = api.post(origin, "notes/show", JSONObject().put("i", token).put("noteId", id.value), MISSKEY_MAX_RESPONSE_BYTES)
        MisskeyMapper.post(JSONObject(response.body), origin)
    }

    override suspend fun profile(id: AccountId): Account = request("profile.details") { profileService.profile(id) }

    override suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String?): Page<Post> = request("profile.timeline") {
        profileService.timeline(query, cursor)
    }

    override suspend fun profileCapability(query: ProfileCapabilityQuery): ProfileCapabilityResult = try {
        request("profile.capability") {
            if (query.target.connection != Connection(origin, Protocol.MISSKEY)) {
                return@request ProfileCapabilityResult(CapabilityStatus.Unsupported)
            }
            refreshCapabilities()
            when (query.capability) {
                ProfileCapability.LikedPosts -> ProfileCapabilityResult(capabilities.likedPosts)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: SourceError) {
        ProfileCapabilityResult(capabilityStatus(error))
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
        moderationService?.setBlocked(id, blocked) ?: unsupported("profile.block")
    }

    override suspend fun setMuted(id: AccountId, muted: Boolean): ProfileRelationship = request("profile.mute") {
        moderationService?.setMuted(id, muted) ?: unsupported("profile.mute")
    }

    override suspend fun pinnedPosts(id: AccountId): List<Post> = request("profile.pinned") { profileService.pinnedPosts(id) }

    override suspend fun trendingHashtags(limit: Int): List<TrendingHashtag> =
        request("trends.hashtags") { discoveryService.trendingHashtags(limit) }

    override suspend fun suggestHashtags(prefix: String, limit: Int): List<HashtagSuggestion> =
        request("search.hashtag.suggest") { discoveryService.suggestHashtags(prefix, limit) }

    override suspend fun popularAccounts(limit: Int): List<Account> =
        request("accounts.popular") { discoveryService.popularAccounts(limit) }

    override suspend fun searchAccounts(query: String): List<Account> = request("search.accounts") {
        val parts = query.trim().removePrefix("@").split('@')
        require(parts.size in 1..2 && parts[0].isNotBlank()) { appMessages.webfingerHandleInvalid() }
        val body = JSONObject().put("i", token).put("username", parts[0])
        parts.getOrNull(1)?.takeIf { it.isNotBlank() && !it.equals(java.net.URI(origin).host, ignoreCase = true) }
            ?.let { body.put("host", it) }
        listOf(MisskeyMapper.account(JSONObject(api.post(origin, "users/show", body, MISSKEY_MAX_RESPONSE_BYTES).body), origin))
    }

    override val maxCombinedHashtags: Int get() = MAX_COMBINED_HASHTAGS

    override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> =
        searchHashtags(HashtagQuery(tag), cursor)

    /**
     * Without extras the request keeps `tag`. With extras it sends `query`, one inner array for each
     * hashtag: the outer array is OR, so each hashtag matches alone.
     */
    override suspend fun searchHashtags(query: HashtagQuery, cursor: String?): Page<Post> = request("search.hashtag") {
        val normalized = hashtagBody(query.primary)
        val extras = query.extras(MAX_COMBINED_HASHTAGS)
        val body = JSONObject().put("i", token).put("limit", 30)
        if (extras.isEmpty()) {
            body.put("tag", normalized)
        } else {
            body.put("query", JSONArray((listOf(normalized) + extras).map { JSONArray().put(it) }))
        }
        cursor?.let { body.put("untilId", it) }
        val notes = JSONArray(api.post(origin, "notes/search-by-tag", body, MISSKEY_MAX_RESPONSE_BYTES).body)
        Page(
            items = (0 until notes.length()).map { MisskeyMapper.post(notes.getJSONObject(it), origin) },
            nextCursor = notes.optJSONObject(notes.length() - 1)?.optString("id")?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun threadContext(
        focalId: EntityId,
        continuation: ThreadContinuation?,
    ): ThreadContext = request("thread") {
        validatePostId(focalId, "thread")
        threadService.threadContext(focalId, continuation)
    }

    override suspend fun uploadMedia(request: MediaUploadRequest): Attachment = this.request("media.upload") {
        mediaService.upload(request)
    }

    override suspend fun deleteUpload(attachmentId: String): Unit = this.request("media.delete") {
        mediaService.delete(attachmentId)
    }

    override suspend fun create(post: CreatePostRequest): Post = request("create") {
        post.replyTo?.let { validatePostId(it, "create.reply-origin") }
        val fileIds = post.attachments.map { it.id ?: throw SourceError.Unsupported("create.attachment-id") }
        val body = JSONObject()
            .put("i", token)
            .put("visibility", post.audience.toMisskeyVisibility())
        // A note with files may have no text. A blank text field is rejected, so leave it out.
        if (post.text.isNotBlank() || fileIds.isEmpty()) body.put("text", post.text)
        if (fileIds.isNotEmpty()) body.put("fileIds", JSONArray(fileIds))
        post.contentWarning?.let { body.put("cw", it) }
        post.replyTo?.let { body.put("replyId", it.value) }
        post.quoteOf?.let {
            // The shared validator rejects a foreign origin and a blank value together.
            validatePostId(it, "create.quote-origin")
            body.put("renoteId", it.value)
        }
        post.poll?.let { poll ->
            body.put("poll", JSONObject()
                .put("choices", JSONArray(poll.choices))
                .put("multiple", poll.multiple)
                .apply { poll.expiresAt?.let { put("expiresAt", it.toEpochMilli()) } })
        }
        val response = JSONObject(api.post(origin, "notes/create", body, MISSKEY_MAX_RESPONSE_BYTES).body)
        MisskeyMapper.post(response.getJSONObject("createdNote"), origin)
    }

    override suspend fun conversations(cursor: String?): Page<DirectConversation> = request("direct.conversations") { directMessageService.conversations(cursor) }

    override suspend fun conversationThread(request: DirectThreadRequest, cursor: String?): DirectThreadResult =
        this.request("direct.thread") { directMessageService.conversationThread(request, cursor) }

    override suspend fun sendDirectMessage(request: DirectMessageRequest): Post = request("direct.send") { directMessageService.send(request) }

    override suspend fun markConversationRead(id: ConversationId) = request("direct.read") { directMessageService.markConversationRead(id) }

    override suspend fun react(id: EntityId, emoji: String) = request("react") {
        validatePostId(id, "react")
        val reaction = emoji.trim().takeIf { it.isNotBlank() }
            ?: throw SourceError.Unsupported("react.emoji")
        api.post(origin, "notes/reactions/create", JSONObject()
            .put("i", token)
            .put("noteId", id.value)
            .put("reaction", reaction), MISSKEY_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun react(id: EntityId, choice: EmojiChoice) = request("react") {
        validatePostId(id, "react")
        val reaction = choice.submissionValue.takeIf { it.isNotBlank() }
            ?: throw SourceError.Unsupported("react.emoji")
        api.post(origin, "notes/reactions/create", JSONObject()
            .put("i", token)
            .put("noteId", id.value)
            .put("reaction", reaction), MISSKEY_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun removeReaction(id: EntityId, emoji: String) = request("react") {
        validatePostId(id, "react")
        api.post(origin, "notes/reactions/delete", JSONObject().put("i", token).put("noteId", id.value), MISSKEY_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun removeReaction(id: EntityId, choice: EmojiChoice) = request("react") {
        validatePostId(id, "react")
        api.post(origin, "notes/reactions/delete", JSONObject().put("i", token).put("noteId", id.value), MISSKEY_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun favorite(id: EntityId) = favorite(id, me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI)

    override suspend fun favorite(id: EntityId, favouriteEmoji: String) = request("favorite") {
        validatePostId(id, "favorite")
        react(id, normalizeFavouriteEmoji(favouriteEmoji))
    }

    override suspend fun unfavorite(id: EntityId, favouriteEmoji: String?) = request("favorite") {
        validatePostId(id, "favorite")
        removeReaction(id, favouriteEmoji.orEmpty())
    }

    override suspend fun setPrimaryFavourite(
        id: EntityId,
        favouriteEmoji: String,
        selected: Boolean,
    ): PostActionResult {
        if (selected) favorite(id, favouriteEmoji) else unfavorite(id, favouriteEmoji)
        return PostActionResult(selected = selected)
    }

    override suspend fun renote(id: EntityId) = request("renote") {
        validatePostId(id, "renote")
        api.post(origin, "notes/create", JSONObject().put("i", token).put("renoteId", id.value), MISSKEY_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun unrenote(id: EntityId, ownRepostId: EntityId?) = request("renote.undo") {
        val repostId = ownRepostId ?: throw SourceError.Unsupported("renote.undo")
        validatePostId(repostId, "renote.undo")
        api.post(origin, "notes/delete", JSONObject().put("i", token).put("noteId", repostId.value), MISSKEY_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun setReshared(id: EntityId, selected: Boolean, ownRepostId: EntityId?): PostActionResult = request("renote") {
        validatePostId(id, "renote")
        if (!selected) {
            unrenote(id, ownRepostId)
            return@request PostActionResult(selected = false)
        }
        val response = JSONObject(api.post(origin, "notes/create", JSONObject()
            .put("i", token)
            .put("renoteId", id.value), MISSKEY_MAX_RESPONSE_BYTES).body)
        val created = response.optJSONObject("createdNote")
            ?: throw SourceError.ServerError("Misskey did not return the created renote")
        val createdId = created.optString("id").takeIf { it.isNotBlank() }?.let { EntityId(origin, it) }
        PostActionResult(selected = true, createdRepostId = createdId)
    }

    override suspend fun save(id: EntityId) = request("save") {
        validatePostId(id, "save")
        api.post(origin, "notes/favorites/create", JSONObject().put("i", token).put("noteId", id.value), MISSKEY_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun unsave(id: EntityId) = request("save") {
        validatePostId(id, "save")
        api.post(origin, "notes/favorites/delete", JSONObject().put("i", token).put("noteId", id.value), MISSKEY_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun setSaved(id: EntityId, selected: Boolean): PostActionResult {
        if (selected) save(id) else unsave(id)
        return PostActionResult(selected = selected)
    }

    override suspend fun savedPosts(cursor: String?): Page<Post> = request("saved.posts") {
        val body = JSONObject().put("i", token).put("limit", 30)
        cursor?.takeIf(String::isNotBlank)?.let { body.put("untilId", it) }
        val values = JSONArray(api.post(origin, "i/favorites", body, MISSKEY_MAX_RESPONSE_BYTES).body)
        val items = (0 until values.length()).mapNotNull { index ->
            val wrapper = values.optJSONObject(index) ?: return@mapNotNull null
            val note = wrapper.optJSONObject("note") ?: wrapper
            runCatching { MisskeyMapper.post(note, origin).copy(saved = true) }.getOrNull()
        }
        val nextCursor = values.optJSONObject(values.length() - 1)?.optString("id")
            ?.takeIf { it.isNotBlank() }
        Page(items, nextCursor)
    }

    private fun validatePostId(id: EntityId, feature: String) {
        if (id.connection != origin) throw SourceError.ForeignOrigin(feature)
        if (id.value.isBlank()) throw SourceError.Unsupported(feature)
    }

    override suspend fun loadEditableProfile(): EditableProfile = request("profile.editable") {
        val localId = requireAccountId().localId
        val json = JSONObject(api.post(origin, "i", JSONObject().put("i", token), MISSKEY_MAX_RESPONSE_BYTES).body)
        EditableProfile(
            id = localId,
            displayName = json.optString("name"),
            biography = json.optString("description"),
        )
    }

    override suspend fun updateEditableProfile(patch: EditableProfilePatch): EditableProfile = request("profile.editable.update") {
        val unsupported = patch != EditableProfilePatch(displayName = patch.displayName, biography = patch.biography)
        if (unsupported) throw SourceError.Unsupported("profile.editable.update")
        val body = JSONObject().put("i", token)
        patch.displayName?.let { body.put("name", it) }
        patch.biography?.let { body.put("description", it) }
        val json = JSONObject(api.post(origin, "i/update", body, MISSKEY_MAX_RESPONSE_BYTES).body)
        val returnedId = json.optString("id").takeIf { it.isNotBlank() }
        val expectedId = accountId?.localId
        if (expectedId != null && returnedId != null && returnedId != expectedId) {
            throw SourceError.AccountMismatch
        }
        EditableProfile(
            id = expectedId ?: returnedId ?: throw SourceError.AccountMismatch,
            displayName = json.optString("name"),
            biography = json.optString("description"),
        )
    }

    override suspend fun customEmojis(): List<CustomEmoji> = request("emoji.catalog") {
        val response = api.post(origin, "emojis", JSONObject().put("i", token), MISSKEY_MAX_RESPONSE_BYTES)
        MisskeyEmojiMapper.parseCatalog(response.body, origin)
    }

    override suspend fun delete(id: EntityId) = request("delete") {
        validatePostId(id, "delete")
        api.post(origin, "notes/delete", JSONObject().put("i", token).put("noteId", id.value), MISSKEY_MAX_RESPONSE_BYTES)
        Unit
    }

    override suspend fun translate(id: EntityId, targetLanguage: String): Translation = request("translate") {
        validatePostId(id, "translate")
        val body = JSONObject().put("i", token).put("noteId", id.value).put("targetLang", targetLanguage)
        try {
            MisskeyMapper.translation(JSONObject(api.post(origin, "notes/translate", body, MISSKEY_MAX_RESPONSE_BYTES).body))
        } catch (e: ApiFailure) {
            // The server answers UNAVAILABLE when no translator is configured or the call failed.
            if (e.code.equals("UNAVAILABLE", ignoreCase = true)) throw SourceError.Unsupported("translate")
            throw e
        }
    }

    override fun streamEvents(): Flow<Event> = streamService.events()

    override suspend fun notifications(cursor: String?): Page<Notification> = request("notifications") { notificationService.notifications(cursor) }

    override suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor?): NotificationPage = request("notifications") { notificationService.notifications(query, cursor) }

    override suspend fun fetchNewerNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage = request("notifications.newer") {
        notificationService.fetchNewer(query, checkpoint)
    }

    override suspend fun fetchOlderNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage = request("notifications.older") {
        notificationService.fetchOlder(query, checkpoint)
    }

    override suspend fun notificationUnreadState(): NotificationUnreadState = request("notifications.unread") { notificationService.unreadState() }

    override suspend fun pushProviderInfo(): PushProviderInfo = request("notifications.push.provider") { pushService.providerInfo() }

    override suspend fun acknowledgeNotifications(): NotificationAcknowledgement = request("notifications.acknowledge") { notificationService.acknowledge() }

    override suspend fun queryOwnedPushSubscription(knownEndpoint: ValidatedUrl?): PushSubscription? = request("notifications.push.query") { pushService.query(knownEndpoint) }

    override suspend fun createOrReplacePushSubscription(
        spec: PushSubscriptionSpec,
        previous: PushSubscription?,
    ): PushSubscription = request("notifications.push.create") { pushService.createOrReplace(spec, previous) }

    override suspend fun updatePushAlertPolicy(
        subscription: PushSubscription,
        alerts: Set<NotificationCategory>,
    ): PushSubscription = request("notifications.push.update") { pushService.updatePolicy(subscription, alerts) }

    override suspend fun removePushSubscription(subscription: PushSubscription) = request("notifications.push.remove") { pushService.remove(subscription) }

    override suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) = request("notifications.followRequest") {
        notificationService.respondToFollowRequest(targetAccountId, accept)
    }

    override suspend fun blockedAccounts(cursor: ModerationCursor?): ModerationPage<ModerationAccount> = request("moderation.blocked") {
        moderationService?.blocked(cursor) ?: unsupported("moderation.blocked")
    }

    override suspend fun mutedAccounts(cursor: ModerationCursor?): ModerationPage<ModerationAccount> = request("moderation.muted") {
        moderationService?.muted(cursor) ?: unsupported("moderation.muted")
    }

    override suspend fun mutedHashtags(cursor: ModerationCursor?): ModerationPage<MutedHashtag> = request("moderation.hashtags") {
        moderationService?.hashtags(cursor) ?: unsupported("moderation.hashtags")
    }

    override suspend fun removeBlockedAccount(entry: ModerationAccount) = request("moderation.blocked.remove") {
        moderationService?.removeBlocked(entry) ?: unsupported<Unit>("moderation.blocked.remove")
    }

    override suspend fun removeMutedAccount(entry: ModerationAccount) = request("moderation.muted.remove") {
        moderationService?.removeMuted(entry) ?: unsupported<Unit>("moderation.muted.remove")
    }

    override suspend fun report(request: ReportRequest) = request("moderation.report") {
        moderationService?.report(request) ?: unsupported<Unit>("moderation.report")
    }

    private fun requireAccountId(): AccountId = accountId ?: throw SourceError.Unsupported("notifications.account")

    private suspend fun <T> request(
        operation: String,
        invalidateCapabilitiesOnNotFound: Boolean = false,
        block: suspend () -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceError) {
            throw e
        } catch (e: ApiFailure) {
            if (invalidateCapabilitiesOnNotFound && (e.status == 404 || e.code.equals("NOT_SUPPORTED", ignoreCase = true))) {
                capabilityCache.remove(cacheKey)
                _capabilities.value = capabilities.copy(capabilitiesLastUpdated = 0)
            }
            throw MisskeyErrorMapper.map(e)
        } catch (_: ResponseLimitExceeded) {
            throw SourceError.ResourceLimit(operation)
        } catch (e: Exception) {
            throw MisskeyErrorMapper.map(e)
        }
    }

    private suspend fun refreshCapabilities() {
        val now = clock()
        val schemaCurrent = capabilities.capabilitySchemaVersion == ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION
        if (schemaCurrent && now - capabilities.capabilitiesLastUpdated < CAPABILITIES_TTL_MILLIS) return
        // A session source must use the fenced lookup. The revision restarts at one
        // after removal and re-add, so an unfenced hit could return replacement
        // evidence to the old session. Anonymous sources keep the direct lookup.
        val cached = if (sessionIdentity != null && accountId != null) {
            capabilityCache.get(cacheKey, sessionIdentity)
        } else {
            capabilityCache.get(cacheKey)
        }
        cached?.takeIf {
            it.capabilitySchemaVersion == ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION
        }?.let {
            _capabilities.value = it.copy(
                canPublish = it.canPublish || capabilities.canPublish,
                notifications = it.notifications.takeVerifiedOr(capabilities.notifications),
                profile = it.profile.takeVerifiedOr(capabilities.profile),
            )
            return
        }
        try {
            capabilityProbe.probeCapabilities(Connection(origin, Protocol.MISSKEY)).also {
                val updated = it.copy(
                    canPublish = it.canPublish || capabilities.canPublish,
                    notifications = it.notifications.mergeNotificationCapabilities(capabilities.notifications),
                    profile = it.profile.takeVerifiedOr(capabilities.profile),
                )
                // A suspended probe can finish after account removal or session replacement.
                // Check before every publication boundary so old evidence cannot become current.
                // The fenced put also fails after removal, which covers durable revision reuse.
                if (!isCurrentSession()) return@also
                if (sessionIdentity != null && accountId != null) {
                    if (!capabilityCache.put(cacheKey, updated, sessionIdentity)) return@also
                } else {
                    capabilityCache.put(cacheKey, updated)
                }
                _capabilities.value = updated
                onCapabilitiesUpdated?.invoke(updated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            capabilityCache.remove(cacheKey)
            _capabilities.value = capabilities.copy(capabilitiesLastUpdated = 0)
            throw if (e is SourceError) e else MisskeyErrorMapper.map(e)
        }
    }

    private companion object {
        const val CAPABILITIES_TTL_MILLIS = 5 * 60 * 1000L
    }
}
private fun NotificationCapabilities.takeVerifiedOr(previous: NotificationCapabilities): NotificationCapabilities =
    if (this == NotificationCapabilities()) previous else this

private fun NotificationCapabilities.mergeNotificationCapabilities(
    previous: NotificationCapabilities,
): NotificationCapabilities = copy(
    listing = listing.takeKnown(previous.listing),
    supportedCategories = supportedCategories.ifEmpty { previous.supportedCategories },
    readSemantics = readSemantics.takeKnown(previous.readSemantics),
    unreadCountPrecision = unreadCountPrecision.takeKnown(previous.unreadCountPrecision),
    grouping = grouping.takeKnown(previous.grouping),
    dismissal = dismissal.takeKnown(previous.dismissal),
    policyManagement = policyManagement.takeKnown(previous.policyManagement),
    followRequestActions = followRequestActions.takeKnown(previous.followRequestActions),
    streaming = streaming.takeKnown(previous.streaming),
)

private fun CapabilityStatus.takeKnown(previous: CapabilityStatus): CapabilityStatus =
    if (this == CapabilityStatus.Unknown) previous else this

private fun NotificationReadSemantics.takeKnown(
    previous: NotificationReadSemantics,
) = if (this == NotificationReadSemantics.Unknown) previous else this

private fun NotificationUnreadPrecision.takeKnown(
    previous: NotificationUnreadPrecision,
) = if (this == NotificationUnreadPrecision.Unknown) previous else this

private fun ProfileCapabilities.takeVerifiedOr(previous: ProfileCapabilities): ProfileCapabilities =
    if (this == ProfileCapabilities()) previous else this

private fun Audience.toMisskeyVisibility(): String = when (this) {
    Audience.Public -> "public"
    Audience.Unlisted -> "home"
    Audience.Followers -> "followers"
    Audience.Direct -> "specified"
}
