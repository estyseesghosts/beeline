package me.foxtails.palustris.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.foxtails.palustris.domain.hashtags.HashtagQuery
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import me.foxtails.palustris.domain.search.SearchQuery
import me.foxtails.palustris.domain.search.SearchSuggestions
import me.foxtails.palustris.domain.search.Trends

/** Transport-independent boundary implemented by individual server adapters. */
interface SocialSource {
    val capabilities: ServerCapabilities

    /** Artwork policy for the primary favourite action. This is not a feature capability. */
    val favouriteArtworkStyle: FavouriteArtworkStyle
        get() = FavouriteArtworkStyle.Heart

    /**
     * Observable capability snapshot. A feature host collects this instead of reading
     * [capabilities] once, so a refreshed probe can update capability-driven controls.
     * The default emits the current snapshot only.
     */
    fun observeCapabilities(): Flow<ServerCapabilities> = flowOf(capabilities)
    suspend fun timelines(): List<Timeline> = timelineDisplayOrder.filter { it in capabilities.timelines }
    suspend fun timeline(timeline: Timeline, cursor: String? = null): Page<Post>
    suspend fun post(id: EntityId): Post = unsupported("post")
    suspend fun profile(id: AccountId): Account = unsupported("profile")
    suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String? = null): Page<Post> =
        unsupported("profile.timeline")

    /**
     * Capability defaults must be conservative because this contract has no target ownership
     * information. Adapters that can serve a target must provide target-aware evidence.
     */
    suspend fun profileCapability(query: ProfileCapabilityQuery): ProfileCapabilityResult =
        ProfileCapabilityResult(CapabilityStatus.Unsupported)
    suspend fun profileRelationship(id: AccountId): ProfileRelationship = unsupported("profile.relationship")
    suspend fun followProfile(id: AccountId): ProfileRelationship = unsupported("profile.follow")
    suspend fun unfollowProfile(id: AccountId): ProfileRelationship = unsupported("profile.unfollow")
    suspend fun pinnedPosts(id: AccountId): List<Post> = emptyList()
    suspend fun threadContext(
        focalId: EntityId,
        continuation: ThreadContinuation? = null,
    ): ThreadContext = unsupported("thread")
    suspend fun create(post: CreatePostRequest): Post = unsupported("create")
    suspend fun loadEditableProfile(): EditableProfile = unsupported("profile.editable.load")
    suspend fun updateEditableProfile(patch: EditableProfilePatch): EditableProfile = unsupported("profile.editable.update")
    suspend fun customEmojis(): List<CustomEmoji> = emptyList()
    suspend fun delete(id: EntityId) = unsupported<Unit>("delete")

    /** The raw text and warning of one of the account's own posts, for the edit composer. */
    suspend fun editSource(id: EntityId): PostEditSource = unsupported("edit")
    suspend fun edit(id: EntityId, request: EditPostRequest): Post = unsupported("edit")

    /** Translates a post on the server into [targetLanguage], an ISO 639-1 code. */
    suspend fun translate(id: EntityId, targetLanguage: String): Translation = unsupported("translate")
    suspend fun react(id: EntityId, emoji: String) = unsupported<Unit>("react")
    suspend fun react(id: EntityId, choice: EmojiChoice) { react(id, choice.submissionValue) }

    /**
     * Sets the account's reaction to [choice] and returns the server's verdict with the status
     * as the server now holds it. The default delegates to [react] and [removeReaction], so an
     * adapter without a response body keeps its existing behavior; an adapter whose mutation
     * returns the updated status overrides this to surface it for state replacement.
     */
    suspend fun setReaction(id: EntityId, choice: EmojiChoice, selected: Boolean): PostActionResult {
        if (selected) react(id, choice) else removeReaction(id, choice)
        return PostActionResult(selected = selected)
    }

    /**
     * The accounts that reacted with [choice] on [id], oldest first where the server orders
     * them. The default knows no listing route.
     */
    suspend fun reactionAccounts(id: EntityId, choice: EmojiChoice): List<Account> = unsupported("react.accounts")
    suspend fun favorite(id: EntityId) = unsupported<Unit>("favorite")
    suspend fun favorite(id: EntityId, favouriteEmoji: String): Unit = favorite(id)
    suspend fun unfavorite(id: EntityId, favouriteEmoji: String? = null) = unsupported<Unit>("favorite")
    suspend fun renote(id: EntityId) = unsupported<Unit>("renote")
    suspend fun unrenote(id: EntityId, ownRepostId: EntityId? = null) = unsupported<Unit>("renote")
    suspend fun removeReaction(id: EntityId, emoji: String) = unsupported<Unit>("react")
    suspend fun removeReaction(id: EntityId, choice: EmojiChoice) { removeReaction(id, choice.submissionValue) }
    suspend fun save(id: EntityId) = unsupported<Unit>("save")
    suspend fun unsave(id: EntityId) = unsupported<Unit>("save")
    suspend fun savedPosts(cursor: String? = null): Page<Post> = unsupported("savedPosts")
    suspend fun setPrimaryFavourite(
        id: EntityId,
        favouriteEmoji: String,
        selected: Boolean,
    ): PostActionResult {
        if (selected) favorite(id, favouriteEmoji) else unfavorite(id, favouriteEmoji)
        return PostActionResult(selected = selected)
    }
    suspend fun setReshared(id: EntityId, selected: Boolean, ownRepostId: EntityId? = null): PostActionResult {
        if (selected) renote(id) else unrenote(id, ownRepostId)
        return PostActionResult(selected = selected, createdRepostId = ownRepostId)
    }
    suspend fun setSaved(id: EntityId, selected: Boolean): PostActionResult {
        if (selected) save(id) else unsave(id)
        return PostActionResult(selected = selected)
    }
    suspend fun quote(id: EntityId, text: String) = unsupported<Unit>("quote")

    /**
     * Votes for the options at [choices] and returns the poll as the server now holds it.
     * [pollId] is [Poll.id]; [postId] is the post that carries the poll.
     */
    suspend fun votePoll(postId: EntityId, pollId: String, choices: List<Int>): Poll = unsupported("votePoll")
    suspend fun uploadMedia(request: MediaUploadRequest): Attachment = unsupported("uploadMedia")

    /**
     * Deletes an uploaded file that no post uses. Best effort: a server without a delete route
     * reports unsupported and the caller ignores it. The default keeps nothing to delete.
     */
    suspend fun deleteUpload(attachmentId: String): Unit = Unit

    /**
     * The post that [url] addresses, found through the account's own server, or null when the server does not
     * know it. The default knows nothing. Callers bound the wait.
     */
    suspend fun resolvePostUrl(url: String): Post? = null

    /** The account that profile address [url] names, found through the account's own server, or null. */
    suspend fun resolveAccountUrl(url: String): Account? = null

    /** Hands a post to the server to publish at [CreatePostRequest.scheduledAtEpochMillis]. */
    suspend fun schedule(request: CreatePostRequest): ScheduledPost = unsupported("schedule")

    /** The posts the server holds for later, soonest first. */
    suspend fun scheduledPosts(): List<ScheduledPost> = unsupported("schedule")

    suspend fun cancelScheduled(id: String): Unit = unsupported("schedule")

    /** Moves [item] to [atEpochMillis]. A server without a route for it cancels the post and schedules it again. */
    suspend fun reschedule(item: ScheduledPost, atEpochMillis: Long): ScheduledPost = unsupported("schedule")

    /** A page of posts whose text matches [query]. [cursor] comes from the previous page. */
    suspend fun searchPosts(query: String, cursor: String? = null): Page<Post> = unsupported("search")

    /** Up to [SearchSuggestions.MAX_ACCOUNTS] accounts and [SearchSuggestions.MAX_HASHTAGS] hashtags for a typed query. */
    suspend fun searchSuggestions(query: SearchQuery): SearchSuggestions = unsupported("search.suggestions")

    /** Trending tags, the first page of trending posts and trending links. A refused section is empty. */
    suspend fun trends(): Trends = unsupported("trends")

    /** The page of trending posts after [cursor]. */
    suspend fun trendingPosts(cursor: String?): Page<Post> = unsupported("trends.posts")
    suspend fun searchHashtag(tag: String, cursor: String? = null): Page<Post> = unsupported("hashtag search")

    /** How many extra hashtags one hashtag search accepts. Zero means the source cannot combine. */
    val maxCombinedHashtags: Int get() = 0

    /** How many hashtags one local hashtag group may hold, the primary included. Zero means no groups. */
    val maxGroupHashtags: Int get() = 0

    /**
     * Searches the primary hashtag together with [HashtagQuery.alsoMatching]. A query without extras
     * is the plain hashtag search. A source that cannot combine rejects extras.
     */
    suspend fun searchHashtags(query: HashtagQuery, cursor: String? = null): Page<Post> =
        if (query.alsoMatching.isEmpty()) searchHashtag(query.primary, cursor) else unsupported("combined hashtag search")
    suspend fun searchAccounts(query: String): List<Account> = unsupported("account search")

    /**
     * A page of [feed]. A built-in timeline goes through [timeline] and a hashtag through [searchHashtags].
     * A source overrides this for the kinds it serves itself, such as lists and antennas.
     */
    suspend fun feed(feed: FeedSource, cursor: String? = null): Page<Post> = when (feed) {
        is FeedSource.Timeline -> timeline(feed.timeline, cursor)
        is FeedSource.Hashtag -> searchHashtags(HashtagQuery(feed.tag), cursor)
        is FeedSource.HashtagGroup -> searchHashtags(HashtagQuery(feed.tags.first(), feed.tags.drop(1)), cursor)
        is FeedSource.UserList -> unsupported("lists")
        is FeedSource.Antenna -> unsupported("antennas")
    }

    /**
     * Reloads the first page of [feed] as an explicit refresh. The default replays a first-page
     * [feed] load, so adapters without a refresh-sensitive endpoint ignore it. The domain carries
     * this generic refresh concept (rather than a recommendations flag) because pull-to-refresh is
     * a protocol-neutral user signal; only adapters whose endpoints distinguish a re-rank, such as
     * the DVD recommendations feed with its `refresh` flag, override it. Load-more always goes
     * through [feed] with the opaque cursor and never refreshes.
     */
    suspend fun refreshFeed(feed: FeedSource): Page<Post> = feed(feed)

    /** The account's lists, in server order. */
    suspend fun userLists(): List<FeedSource.UserList> = emptyList()

    /** The account's antennas, in server order. */
    suspend fun antennas(): List<FeedSource.Antenna> = emptyList()

    /** The hashtags the account follows on the server, without the leading `#`. Empty where the server has none. */
    suspend fun followedTags(): List<String> = emptyList()

    /** Follows [tag] on the server. */
    suspend fun followTag(tag: String): Unit = unsupported("tags.follow")

    /** Stops following [tag] on the server. */
    suspend fun unfollowTag(tag: String): Unit = unsupported("tags.unfollow")

    /** Hashtags that the server reports as trending, at most [limit]. */
    suspend fun trendingHashtags(limit: Int): List<TrendingHashtag> = unsupported("trending hashtags")

    /** Hashtag names that start with [prefix], at most [limit]. Only the fragment is sent. */
    suspend fun suggestHashtags(prefix: String, limit: Int): List<HashtagSuggestion> = unsupported("hashtag suggestions")

    /** Accounts that the server recommends or lists as popular, at most [limit]. */
    suspend fun popularAccounts(limit: Int): List<Account> = unsupported("popular accounts")

    /** Legacy page shape retained for source compatibility during the adapter migration. */
    suspend fun notifications(cursor: String? = null): Page<Notification> = unsupported("notifications")

    suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor? = null): NotificationPage =
        unsupported("notifications")
    suspend fun fetchNewerNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = unsupported("notifications.newer")
    suspend fun fetchOlderNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = unsupported("notifications.older")
    suspend fun notificationUnreadState(): NotificationUnreadState = unsupported("notifications.unread")
    suspend fun acknowledgeNotifications(): NotificationAcknowledgement = unsupported("notifications.acknowledge")
    suspend fun pushProviderInfo(): PushProviderInfo = PushProviderInfo(CapabilityStatus.Unsupported)
    suspend fun dismissNotification(id: EntityId) = unsupported<Unit>("notifications.dismiss")
    suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) =
        unsupported<Unit>("notifications.followRequest")
    suspend fun queryOwnedPushSubscription(knownEndpoint: ValidatedUrl? = null): PushSubscription? =
        unsupported("notifications.push.query")
    suspend fun createOrReplacePushSubscription(
        spec: PushSubscriptionSpec,
        previous: PushSubscription? = null,
    ): PushSubscription = unsupported("notifications.push.create-or-replace")
    suspend fun updatePushAlertPolicy(
        subscription: PushSubscription,
        alerts: Set<NotificationCategory>,
    ): PushSubscription = unsupported("notifications.push.policy")
    suspend fun removePushSubscription(subscription: PushSubscription) =
        unsupported<Unit>("notifications.push.remove")
    suspend fun setMuted(id: AccountId, muted: Boolean): ProfileRelationship =
        unsupported("profile.mute")
    suspend fun setBlocked(id: AccountId, blocked: Boolean): ProfileRelationship =
        unsupported("profile.block")
    suspend fun setBoostsHidden(id: AccountId, hidden: Boolean): ProfileRelationship =
        unsupported("profile.boosts")
    suspend fun report(request: ReportRequest) = unsupported<Unit>("moderation.report")
    suspend fun blockedAccounts(cursor: ModerationCursor? = null): ModerationPage<ModerationAccount> =
        unsupported("moderation.blocked")
    suspend fun mutedAccounts(cursor: ModerationCursor? = null): ModerationPage<ModerationAccount> =
        unsupported("moderation.muted")

    /** Accounts whose boosts stay hidden. Only Misskey-family servers list them; others throw [SourceError.Unsupported]. */
    suspend fun boostMutedAccounts(): Set<AccountId> = unsupported("moderation.boostmutes")
    suspend fun mutedHashtags(cursor: ModerationCursor? = null): ModerationPage<MutedHashtag> =
        unsupported("moderation.hashtags")
    suspend fun removeBlockedAccount(entry: ModerationAccount) = unsupported<Unit>("moderation.blocked.remove")
    suspend fun removeMutedAccount(entry: ModerationAccount) = unsupported<Unit>("moderation.muted.remove")

    /** The account's content filters. A source without filter support throws [SourceError.Unsupported]. */
    suspend fun contentFilters(): ContentFilterSet = unsupported("moderation.filters")

    /** Creates a filter when [id] is null, or replaces the filter [id]. Returns the fresh set. */
    suspend fun saveContentFilter(id: String?, draft: ContentFilterDraft): ContentFilterSet = unsupported("moderation.filters.save")
    suspend fun deleteContentFilter(id: String): ContentFilterSet = unsupported("moderation.filters.delete")
    fun streamEvents(): kotlinx.coroutines.flow.Flow<Event> = kotlinx.coroutines.flow.emptyFlow()
}

/**
 * Narrow feedback contract for recommendation feeds. Only adapters whose probe reports a
 * supported recommendations capability implement it; generic UI downcasts the session's
 * [SocialSource] instead of branching on protocol. Dwell is best-effort and silent, and a
 * denied feedback call stays [SourceError.AccessDenied] without demoting the feed itself.
 * Responsibility: dwell, not-interested, and ranking-rationale access for one session.
 * Lifetime: bound to its source session; holds no feed state.
 */
interface RecommendationFeedbackSource {
    /** Reports accumulated visible milliseconds per served note, in chunks of at most 50. */
    suspend fun reportRecommendationDwell(dwellMillis: Map<EntityId, Long>)

    /** Tells the ranker the served note holds no interest. The caller removes the row first. */
    suspend fun markRecommendationNotInterested(noteId: EntityId)

    /** The ranking rationale behind one served note, or null when the page carried none. */
    fun recommendationExplanation(noteId: EntityId): RecommendationExplanation?
}

internal suspend fun <T> unsupported(feature: String): T = throw SourceError.Unsupported(feature)
