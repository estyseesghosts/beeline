package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.ModerationCapabilities
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostLengthRule
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.PrimaryFavouriteCapability
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.SavedPostsCapability
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.withTimelineStatuses
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
        val policies = token?.takeIf(String::isNotBlank)?.let { credential ->
            JSONObject(api.post(connection.origin, "i", JSONObject().put("i", credential), maxResponseBytes).body)
                .optJSONObject("policies")
        }
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
        return ServerCapabilities(
            timelineStatuses = statuses,
            audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
            actions = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.React, PostAction.Bookmark),
            maxPostLength = meta.positiveInt("maxNoteTextLength") ?: DEFAULT_MAX_NOTE_TEXT_LENGTH,
            posting = postingCapabilities(meta, policies, hasToken = !token.isNullOrBlank()),
            // A token session can post. Without this, a stored session that never recorded the flag stays unable to publish.
            canPublish = !token.isNullOrBlank(),
            quotes = CapabilityStatus.Supported,
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
            moderation = ModerationCapabilities(
                read = if (token.isNullOrBlank()) CapabilityStatus.Denied else CapabilityStatus.Supported,
                write = if (token.isNullOrBlank()) CapabilityStatus.Denied else CapabilityStatus.Supported,
                blocked = if (token.isNullOrBlank()) CapabilityStatus.Denied else CapabilityStatus.Supported,
                muted = if (token.isNullOrBlank()) CapabilityStatus.Denied else CapabilityStatus.Supported,
                hashtags = CapabilityStatus.Unsupported,
            ),
            capabilitiesLastUpdated = System.currentTimeMillis(),
            capabilitySchemaVersion = ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
        ).withTimelineStatuses(statuses)
    }

    /**
     * The content warning has its own limit on this family, and the server never shrinks an
     * upload. The file size limit is the smaller of the instance limit and the role policy.
     */
    private fun postingCapabilities(meta: JSONObject, policies: JSONObject?, hasToken: Boolean): PostingCapabilities {
        val policyBytes = policies.positiveLong("maxFileSizeMb")?.times(BYTES_PER_MEGABYTE)
        val limits = listOfNotNull(meta.positiveLong("maxFileSize"), policyBytes)
        return PostingCapabilities(
            lengthRule = PostLengthRule.Utf16TextOnly,
            maxWarningLength = meta.positiveInt("maxCwLength") ?: DEFAULT_MAX_CW_LENGTH,
            maxAttachments = MAX_ATTACHMENTS,
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
    }
}
