package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.FeedChipCapabilities
import me.foxtails.palustris.domain.LocalOnlyRoute
import me.foxtails.palustris.domain.ModerationCapabilities
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.OwnPostCapabilities
import me.foxtails.palustris.domain.PollLimits
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostLengthRule
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.PrimaryFavouriteCapability
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.RecommendationCapability
import me.foxtails.palustris.domain.RecommendationFlavor
import me.foxtails.palustris.domain.SavedPostsCapability
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.ScheduleRoute
import me.foxtails.palustris.domain.SchedulingCapability
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.TranslationCapability
import me.foxtails.palustris.domain.search.SearchCapabilities
import me.foxtails.palustris.domain.withTimelineStatuses
import org.json.JSONArray
import org.json.JSONObject

class MisskeyCapabilityProbe(
    private val api: MisskeyApi,
    private val token: String? = null,
    private val maxResponseBytes: Long = MISSKEY_MAX_RESPONSE_BYTES,
) : CapabilityProbe {
    override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
        // Misskey's HTTP API is POST-based, including the unauthenticated meta endpoint.
        val meta = JSONObject(api.post(connection.origin, "meta", maxResponseBytes = maxResponseBytes).body)
        require(meta.optString("version").isNotBlank()) { "This server did not return Misskey-compatible information." }
        val iResponse = token?.takeIf(String::isNotBlank)?.let { credential ->
            JSONObject(api.post(connection.origin, "i", JSONObject().put("i", credential), maxResponseBytes).body)
        }
        val policies = iResponse?.optJSONObject("policies")
        val localFallback = if (meta.has("disableLocalTimeline")) {
            if (meta.optBoolean("disableLocalTimeline")) CapabilityStatus.Unsupported else CapabilityStatus.Supported
        } else {
            CapabilityStatus.Supported
        }
        val globalFallback = if (meta.has("disableGlobalTimeline")) {
            if (meta.optBoolean("disableGlobalTimeline")) CapabilityStatus.Unsupported else CapabilityStatus.Supported
        } else {
            CapabilityStatus.Supported
        }
        val statuses = linkedMapOf(
            Timeline.Home to CapabilityStatus.Supported,
            Timeline.Local to policyStatus(policies, "ltlAvailable", localFallback),
            Timeline.Social to policyStatus(policies, "ltlAvailable", localFallback),
            Timeline.Federated to policyStatus(policies, "gtlAvailable", globalFallback),
        )
        if (token?.isNotBlank() == true) {
            statuses[Timeline.Bubble] = probeBubble(connection.origin, token, policies)
        }
        // One list answers both questions: which edit route exists and which schedule routes exist.
        // The DVD recommendations gate reads the richer endpointList result beside it.
        val endpointResult = if (token.isNullOrBlank()) null else endpointList(connection)
        val endpoints = (endpointResult as? EndpointList.Available)?.names
        return ServerCapabilities(
            timelineStatuses = statuses,
            audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
            actions = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.React, PostAction.Bookmark),
            maxPostLength = meta.positiveInt("maxNoteTextLength") ?: DEFAULT_MAX_NOTE_TEXT_LENGTH,
            posting = postingCapabilities(meta, policies, hasToken = !token.isNullOrBlank(), endpoints = endpoints),
            // A token session can post. Without this, a stored session that never recorded the flag stays unable to publish.
            canPublish = !token.isNullOrBlank(),
            quotes = CapabilityStatus.Supported,
            ownPosts = OwnPostCapabilities(
                delete = if (token.isNullOrBlank()) CapabilityStatus.Denied else CapabilityStatus.Supported,
                edit = if (token.isNullOrBlank()) CapabilityStatus.Denied else editStatus(endpoints),
            ),
            translation = TranslationCapability(translationStatus(meta, policies)),
            search = SearchCapabilities(posts = policyStatus(policies, "canSearchNotes", CapabilityStatus.Unknown)),
            feedChips = feedChips(token, endpoints),
            primaryFavourite = PrimaryFavouriteCapability(CapabilityStatus.Supported, PrimaryFavouriteMode.Reaction),
            savedPosts = SavedPostsCapability(CapabilityStatus.Supported, SavedPostsKind.Favourites),
            likedPosts = CapabilityStatus.Supported,
            threads = CapabilityStatus.Supported,
            profile = ProfileCapabilities(
                editable = EditableProfileCapabilities(
                    read = CapabilityStatus.Supported,
                    update = CapabilityStatus.Supported,
                ),
            ),
            emoji = EmojiCapabilities(
                catalog = CapabilityStatus.Supported,
                reactionListing = CapabilityStatus.Supported,
                reactionMutation = CapabilityStatus.Supported,
                selectionMode = ReactionSelectionMode.Single,
            ),
            notifications = NotificationCapabilities(
                webPush = meta.optString("swPublickey").takeIf(String::isNotBlank)
                    ?.let { CapabilityStatus.Supported }
                    ?: CapabilityStatus.Unsupported,
            ),
            moderation = moderationCapabilities(token),
            recommendations = recommendationCapability(connection, iResponse, endpointResult, hasToken = !token.isNullOrBlank()),
            capabilitiesLastUpdated = System.currentTimeMillis(),
            capabilitySchemaVersion = ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
        ).withTimelineStatuses(statuses)
    }

    /**
     * Sharkey serves `notes/edit`; Misskey has no edit route. The endpoint list says which. A failed
     * or unreadable list leaves the status unknown, which hides the row instead of guessing.
     * Legacy callers keep the set-or-null view: any failure reads as null (Unknown). The DVD
     * recommendations gate uses [endpointList] directly so network and rate-limit failures can
     * read as TemporarilyUnavailable instead.
     */
    private suspend fun endpointList(connection: Connection): EndpointList = try {
        val body = api.post(connection.origin, "endpoints", maxResponseBytes = maxResponseBytes).body
        val names = if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("endpoints")
        val parsed = names?.let { list -> (0 until list.length()).map(list::optString).toSet() }
        if (parsed == null) EndpointList.Unreadable else EndpointList.Available(parsed)
    } catch (e: ApiFailure) {
        if (e.status >= 500 || e.status == 429) EndpointList.TemporarilyUnavailable else EndpointList.Unreadable
    } catch (_: java.io.IOException) {
        EndpointList.TemporarilyUnavailable
    } catch (e: Exception) {
        // A garbage body reads as unreadable, exactly like the old set-or-null fetch. Cancellation still propagates.
        if (e is CancellationException) throw e
        EndpointList.Unreadable
    }

    private sealed interface EndpointList {
        data class Available(val names: Set<String>) : EndpointList
        data object Unreadable : EndpointList
        data object TemporarilyUnavailable : EndpointList
    }

    /**
     * DVD Sharkey recommendations gate: the `dvd.chat` host plus a signed-in token plus all four
     * recommendation endpoints, with `recommendationSettings.enabled` read from the existing `i`
     * response when present. Host mismatch or a missing endpoint is Unsupported; a user who
     * disabled recommendations is Denied; an unreadable endpoint list is Unknown; a network or
     * rate-limit failure while reading the list is TemporarilyUnavailable. A single failed feed
     * call never feeds back into this status; only the probe writes it.
     */
    private fun recommendationCapability(
        connection: Connection,
        iResponse: JSONObject?,
        endpoints: EndpointList?,
        hasToken: Boolean,
    ): RecommendationCapability {
        val origin = connection.origin
        if (!isDvdSharkeyHost(origin)) {
            return RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.Unsupported, origin)
        }
        if (!hasToken) {
            return RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.Denied, origin)
        }
        return when (endpoints) {
            is EndpointList.Available -> when {
                !DVD_RECOMMENDATION_ENDPOINTS.all(endpoints.names::contains) ->
                    RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.Unsupported, origin)
                recommendationsDisabled(iResponse) ->
                    RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.Denied, origin)
                else ->
                    RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.Supported, origin)
            }
            EndpointList.TemporarilyUnavailable ->
                RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.TemporarilyUnavailable, origin)
            else ->
                RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.Unknown, origin)
        }
    }

    /** True when the `i` response carries `recommendationSettings.enabled: false`. Absent settings never disable. */
    private fun recommendationsDisabled(iResponse: JSONObject?): Boolean =
        iResponse?.optJSONObject("recommendationSettings")?.let { settings ->
            settings.has("enabled") && !settings.optBoolean("enabled", true)
        } == true

    /** Lists and antennas follow the endpoint list. Saved hashtags stay on the device, so followed tags are Unsupported. */
    private fun feedChips(token: String?, endpoints: Set<String>?) = FeedChipCapabilities(
        lists = if (token.isNullOrBlank()) CapabilityStatus.Denied else endpointStatus(endpoints, "users/lists/list"),
        followedTags = CapabilityStatus.Unsupported,
        antennas = if (token.isNullOrBlank()) CapabilityStatus.Denied else endpointStatus(endpoints, "antennas/list"),
    )

    private fun moderationCapabilities(token: String?): ModerationCapabilities {
        val status = if (token.isNullOrBlank()) CapabilityStatus.Denied else CapabilityStatus.Supported
        return ModerationCapabilities(read = status, write = status, blocked = status, muted = status, hashtags = CapabilityStatus.Unsupported)
    }

    /** Supported when the endpoint list names [endpoint]. A list that could not be read leaves it Unknown. */
    private fun endpointStatus(endpoints: Set<String>?, endpoint: String): CapabilityStatus = when {
        endpoints == null -> CapabilityStatus.Unknown
        endpoint in endpoints -> CapabilityStatus.Supported
        else -> CapabilityStatus.Unsupported
    }

    private fun editStatus(endpoints: Set<String>?): CapabilityStatus = when {
        endpoints == null -> CapabilityStatus.Unknown
        EDIT_ENDPOINT in endpoints -> CapabilityStatus.Supported
        else -> CapabilityStatus.Unsupported
    }

    /**
     * Sharkey routes scheduling through the `notes/schedule` routes and the role policy `scheduleNoteMax` (default 5).
     * Misskey routes it through the `notes/drafts` routes and `scheduledNoteLimit` (default 1). A limit of 0 turns it off.
     * A server whose endpoint list could not be read offers no scheduling.
     */
    private fun schedulingCapability(endpoints: Set<String>?, policies: JSONObject?): SchedulingCapability? {
        endpoints ?: return null
        return when {
            SHARKEY_SCHEDULE_ENDPOINT in endpoints ->
                SchedulingCapability(ScheduleRoute.Sharkey, MISSKEY_SCHEDULE_LEAD_SECONDS, policies?.optInt("scheduleNoteMax", DEFAULT_SHARKEY_SCHEDULE_LIMIT) ?: DEFAULT_SHARKEY_SCHEDULE_LIMIT)
            MISSKEY_DRAFT_ENDPOINT in endpoints ->
                SchedulingCapability(ScheduleRoute.MisskeyDrafts, MISSKEY_SCHEDULE_LEAD_SECONDS, policies?.optInt("scheduledNoteLimit", DEFAULT_MISSKEY_SCHEDULE_LIMIT) ?: DEFAULT_MISSKEY_SCHEDULE_LIMIT)
            else -> null
        }?.takeIf { it.limit == null || it.limit > 0 }
    }

    /** The server needs a configured translator, and the account's role must allow it (default true). */
    private fun translationStatus(meta: JSONObject, policies: JSONObject?): CapabilityStatus = when {
        !meta.optBoolean("translatorAvailable", false) -> CapabilityStatus.Unsupported
        policies == null -> CapabilityStatus.Denied
        policies.optBoolean("canUseTranslator", true) -> CapabilityStatus.Supported
        else -> CapabilityStatus.Denied
    }

    /**
     * The content warning has its own limit on this family, and the server never shrinks an
     * upload. The file size limit is the smaller of the instance limit and the role policy.
     */
    private fun postingCapabilities(meta: JSONObject, policies: JSONObject?, hasToken: Boolean, endpoints: Set<String>?): PostingCapabilities {
        val policyBytes = policies.positiveLong("maxFileSizeMb")?.times(BYTES_PER_MEGABYTE)
        val limits = listOfNotNull(meta.positiveLong("maxFileSize"), policyBytes)
        return PostingCapabilities(
            lengthRule = PostLengthRule.Utf16TextOnly,
            maxWarningLength = meta.positiveInt("maxCwLength") ?: DEFAULT_MAX_CW_LENGTH,
            maxAttachments = MAX_ATTACHMENTS,
            poll = MISSKEY_POLL_LIMITS,
            localOnly = LocalOnlyRoute.MisskeyFlag,
            scheduling = if (hasToken) schedulingCapability(endpoints, policies) else null,
            maxAltTextLength = meta.positiveInt("maxAltTextLength") ?: DEFAULT_MAX_ALT_TEXT_LENGTH,
            maxImageBytes = limits.minOrNull(),
            maxVideoBytes = limits.minOrNull(),
            maxImagePixels = null,
            uploadTypes = null,
            mediaUpload = if (hasToken) CapabilityStatus.Supported else CapabilityStatus.Denied,
            clientCompression = true,
        )
    }

    private fun JSONObject?.positiveInt(key: String): Int? =
        positiveLong(key)?.takeIf { it <= Int.MAX_VALUE }?.toInt()

    private fun JSONObject?.positiveLong(key: String): Long? {
        val value = this?.opt(key) as? Number ?: return null
        return value.toLong().takeIf { it > 0 }
    }

    private suspend fun probeBubble(
        origin: String,
        token: String,
        policies: JSONObject?,
    ): CapabilityStatus = try {
        api.post(origin, "notes/bubble-timeline", JSONObject().put("i", token).put("limit", 1), maxResponseBytes)
        policyStatus(policies, "btlAvailable", CapabilityStatus.Supported)
    } catch (e: ApiFailure) {
        when (e.code?.uppercase()) {
            "BTL_DISABLED" -> CapabilityStatus.Denied
            "NOT_SUPPORTED" -> CapabilityStatus.Unsupported
            else -> when {
                e.status == 404 -> CapabilityStatus.Unsupported
                e.status >= 500 || e.status == 429 -> CapabilityStatus.TemporarilyUnavailable
                else -> throw e
            }
        }
    } catch (_: java.io.IOException) {
        CapabilityStatus.TemporarilyUnavailable
    }

    private fun policyStatus(
        policies: JSONObject?,
        key: String,
        fallback: CapabilityStatus,
    ): CapabilityStatus = if (policies?.has(key) == true && !policies.isNull(key)) {
        if (policies.optBoolean(key)) CapabilityStatus.Supported else CapabilityStatus.Denied
    } else {
        fallback
    }

    private companion object {
        const val DEFAULT_MAX_NOTE_TEXT_LENGTH = 3000
        const val DEFAULT_MAX_CW_LENGTH = 100
        const val DEFAULT_MAX_ALT_TEXT_LENGTH = 512
        const val MAX_ATTACHMENTS = 16
        const val BYTES_PER_MEGABYTE = 1024L * 1024
        const val EDIT_ENDPOINT = "notes/edit"

        /** All four recommendation routes the DVD gate requires before reporting Supported. */
        val DVD_RECOMMENDATION_ENDPOINTS = setOf(
            "notes/recommendations",
            "notes/recommendations/dwell",
            "notes/recommendations/not-interested",
            "i/recommendation-interests",
        )
        const val SHARKEY_SCHEDULE_ENDPOINT = "notes/schedule/create"
        const val MISSKEY_DRAFT_ENDPOINT = "notes/drafts/create"
        const val MISSKEY_SCHEDULE_LEAD_SECONDS = 60L
        const val DEFAULT_SHARKEY_SCHEDULE_LIMIT = 5
        const val DEFAULT_MISSKEY_SCHEDULE_LIMIT = 1

        /** Neither Misskey nor Sharkey publishes poll limits in meta. 50 characters is the lower of the two. */
        val MISSKEY_POLL_LIMITS = PollLimits(maxOptions = 10, maxOptionLength = 50, minExpirySeconds = 60L, maxExpirySeconds = null)
    }
}
