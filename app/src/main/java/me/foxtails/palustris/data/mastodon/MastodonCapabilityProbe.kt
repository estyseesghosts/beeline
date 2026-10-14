package me.foxtails.palustris.data.mastodon

import java.net.URI
import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.BubbleRoute
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
import me.foxtails.palustris.domain.ReactionRoute
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ReportRoute
import me.foxtails.palustris.domain.SavedPostsCapability
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.ScheduleRoute
import me.foxtails.palustris.domain.SchedulingCapability
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.ServerFlavor
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.TranslationCapability
import me.foxtails.palustris.domain.withTimelineStatuses
import org.json.JSONObject

/**
 * Probes Mastodon instance metadata without making protocol details visible to the UI.
 *
 * Flavor detection reads the version compatibility marker once, then a per-flavor table
 * supplies the defaults; the leading compatibility number never proves a fork feature.
 *
 * Reaction capability comes from a recognized extension advertisement, never from a
 * mutation request. An arbitrary probe request cannot prove mutation support: the
 * advertised extension is the only positive evidence this owner accepts.
 */
class MastodonCapabilityProbe(private val api: AuthenticatedHttpClient) : CapabilityProbe {
    data class VersionTriple(val major: Int, val minor: Int, val patch: Int) {
        fun atLeast(major: Int, minor: Int, patch: Int): Boolean = when {
            this.major != major -> this.major > major
            this.minor != minor -> this.minor > minor
            else -> this.patch >= patch
        }
    }

    override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
        val instance = fetchInstanceMetadata(connection.origin)
        // NodeInfo is supplemental evidence. Skip discovery when instance metadata already
        // advertises the extension, so a supported server never pays the extra requests.
        val discovered = if (hasVerifiedEmojiReactionMetadata(instance)) {
            false
        } else {
            fetchNodeInfoReactionAdvertisement(connection.origin)
        }
        return parseCapabilities(instance, nodeInfoAdvertisesReactions = discovered)
    }

    /**
     * Fetches instance metadata with a bounded response size.
     *
     * The v2 endpoint is preferred. The v1 endpoint is a documented fallback only when the
     * v2 endpoint is absent (404). Any other failure propagates, so the caller keeps its
     * existing evidence instead of replacing verified state with a default.
     */
    suspend fun fetchInstanceMetadata(origin: String): JSONObject {
        val v2 = try {
            JSONObject(api.get(origin, "api/v2/instance", maxResponseBytes = MAX_INSTANCE_BYTES).body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpStatusFailure) {
            if (e.status != 404) throw e
            null
        }
        if (v2 != null) return v2
        return JSONObject(api.get(origin, "api/v1/instance", maxResponseBytes = MAX_INSTANCE_BYTES).body)
    }

    /**
     * Discovers the reaction advertisement through NodeInfo when instance metadata lacks it.
     *
     * The well-known document and the chosen NodeInfo document stay on the validated
     * connection origin, carry no credential, follow no redirect, and are bounded in bytes.
     * At most one NodeInfo document is fetched. Every discovery failure returns false: an
     * inaccessible supplemental document is not proof that the extension is unsupported.
     */
    suspend fun fetchNodeInfoReactionAdvertisement(origin: String): Boolean {
        val wellKnown = try {
            JSONObject(api.getUrl("$origin/.well-known/nodeinfo", maxResponseBytes = MAX_NODEINFO_BYTES).body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        val documentUrl = supportedNodeInfoUrl(origin, wellKnown) ?: return false
        val document = try {
            JSONObject(api.getUrl(documentUrl, maxResponseBytes = MAX_NODEINFO_BYTES).body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        return hasNodeInfoReactionFeature(document)
    }

    companion object {
        /** Bounds the instance metadata read. The document is small on supported servers. */
        private const val MAX_INSTANCE_BYTES = 512 * 1024L

        /** Bounds each NodeInfo discovery read. */
        private const val MAX_NODEINFO_BYTES = 256 * 1024L

        /** Parses only a leading major.minor.patch value; fork suffixes are ignored. */
        fun parseLeadingVersion(value: String): VersionTriple? {
            val match = LEADING_VERSION.find(value.trim()) ?: return null
            if (match.range.first != 0) return null
            return VersionTriple(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0,
            )
        }

        /**
         * True when instance metadata advertises the recognized reaction extension.
         * The shape is fixture-confirmed. A malformed or partial shape returns false.
         */
        fun hasVerifiedEmojiReactionMetadata(instance: JSONObject): Boolean =
            instance.optJSONObject("pleroma")
                ?.optJSONObject("metadata")
                ?.optJSONArray("features")
                ?.let { features ->
                    (0 until features.length()).any { features.optString(it) == EMOJI_REACTION_FEATURE }
                }
                ?: false

        /**
         * True when a NodeInfo document advertises the recognized reaction extension.
         * Pleroma and Akkoma publish the feature list under the top-level `metadata` object.
         * Software names are not evidence.
         */
        fun hasNodeInfoReactionFeature(nodeInfo: JSONObject): Boolean =
            nodeInfo.optJSONObject("metadata")
                ?.optJSONArray("features")
                ?.let { features ->
                    (0 until features.length()).any { features.optString(it) == EMOJI_REACTION_FEATURE }
                }
                ?: false

        /**
         * Identifies the server family from the instance document. Forks announce themselves in
         * the version compatibility marker, so that marker is read before anything else; the
         * leading number is only the compatibility version and never a feature signal.
         * Glitch-soc reports `api_versions.glitch` and Hometown puts `+hometown-` in its
         * version. A document with no version and no machine signal proves nothing, so the
         * flavor stays Unknown instead of guessing Mastodon.
         */
        fun parseFlavor(instance: JSONObject): ServerFlavor {
            val version = instance.optString("version")
            if (version.contains("(compatible; Pleroma")) return ServerFlavor.Pleroma
            if (version.contains("(compatible; Akkoma")) return ServerFlavor.Akkoma
            if (version.contains("(compatible; Pixelfed")) return ServerFlavor.Pixelfed
            if (instance.optJSONObject("api_versions")?.has("glitch") == true) return ServerFlavor.Glitch
            if (version.contains("+hometown-")) return ServerFlavor.Hometown
            val machineVersion = instance.optJSONObject("api_versions")?.opt("mastodon")
            if (version.isBlank() && machineVersion == null) return ServerFlavor.Unknown
            return ServerFlavor.Mastodon
        }

        fun parseCapabilities(instance: JSONObject, nodeInfoAdvertisesReactions: Boolean = false): ServerCapabilities {
            val flavor = parseFlavor(instance)
            val machineVersion = instance.optJSONObject("api_versions")?.opt("mastodon")?.let { value ->
                when (value) {
                    is Number -> value.toInt().takeIf { it >= 0 }
                    is String -> value.toIntOrNull()?.takeIf { it >= 0 }
                    else -> null
                }
            }
            val releaseVersion = parseLeadingVersion(instance.optString("version"))
            val editable = when {
                machineVersion != null -> editableForMachineApi(machineVersion)
                releaseVersion != null -> editableForReleaseVersion(releaseVersion)
                else -> EditableProfileCapabilities()
            }
            val imageDeletion = when {
                machineVersion != null -> CapabilityStatus.Supported
                releaseVersion != null -> if (releaseVersion.atLeast(4, 2, 0)) {
                    CapabilityStatus.Supported
                } else {
                    CapabilityStatus.Unsupported
                }
                else -> CapabilityStatus.Unknown
            }
            val quoteEvidence = quoteEvidence(instance)
            val quotes = when {
                quoteEvidence -> CapabilityStatus.Supported
                machineVersion != null && machineVersion >= 7 -> CapabilityStatus.Supported
                releaseVersion != null && releaseVersion.atLeast(4, 5, 0) -> CapabilityStatus.Supported
                // An older version number proves nothing for a fork, so the choice stays open and the first
                // quote is checked. Only a failed check marks quotes unsupported.
                else -> CapabilityStatus.Unknown
            }
            val emoji = emojiCapabilities(instance, nodeInfoAdvertisesReactions)
            val posting = postingLimits(instance, quotes, quotePolicy = (machineVersion != null && machineVersion >= 7) ||
                (machineVersion == null && releaseVersion != null && releaseVersion.atLeast(4, 5, 0)), flavor)
            val actions = setOf(
                PostAction.Reply,
                PostAction.Reshare,
                PostAction.Favorite,
                PostAction.Bookmark,
            ) + if (emoji.reactionMutation == CapabilityStatus.Supported) setOf(PostAction.React) else emptySet()
            return ServerCapabilities(
                audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
                actions = actions,
                maxPostLength = posting.maxPostLength,
                posting = posting.capabilities.copy(postFormats = postFormats(instance)),
                canPublish = true,
                profile = ProfileCapabilities(editable = editable.copy(imageDeletion = imageDeletion)),
                emoji = emoji,
                quotes = quotes,
                ownPosts = OwnPostCapabilities(delete = CapabilityStatus.Supported, edit = editCapability(instance, releaseVersion, flavor)),
                translation = translationCapability(instance),
                feedChips = feedChipCapabilities(machineVersion, releaseVersion, flavor),
                primaryFavourite = PrimaryFavouriteCapability(CapabilityStatus.Supported, PrimaryFavouriteMode.Native),
                savedPosts = SavedPostsCapability(CapabilityStatus.Supported, SavedPostsKind.Bookmarks),
                likedPosts = CapabilityStatus.Supported,
                threads = CapabilityStatus.Supported,
                moderation = ModerationCapabilities(
                    read = CapabilityStatus.Supported,
                    write = CapabilityStatus.Supported,
                    blocked = CapabilityStatus.Supported,
                    muted = CapabilityStatus.Supported,
                    hashtags = CapabilityStatus.Unsupported,
                ),
                notifications = notificationCapabilities(flavor),
                capabilitiesLastUpdated = System.currentTimeMillis(),
                capabilitySchemaVersion = ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
                flavor = flavor,
                supportsReactions = emoji.reactionMutation,
                supportsCustomReactions = customReactionCapability(instance),
                reportRoute = if (flavor == ServerFlavor.Pixelfed) ReportRoute.V11Report else ReportRoute.V1Reports,
                reactionRoute = reactionRoute(flavor, emoji),
                bubbleRoute = if (flavor == ServerFlavor.Akkoma) BubbleRoute.Akkoma else BubbleRoute.None,
            ).withTimelineStatuses(timelineStatusesFor(flavor))
        }

        /**
         * Per-flavor timeline table. Only Akkoma serves a bubble timeline; Social stays
         * unsupported on every Mastodon-family server until a probe proves otherwise.
         */
        private fun timelineStatusesFor(flavor: ServerFlavor): Map<Timeline, CapabilityStatus> = mapOf(
            Timeline.Home to CapabilityStatus.Supported,
            Timeline.Local to CapabilityStatus.Supported,
            Timeline.Social to CapabilityStatus.Unsupported,
            Timeline.Bubble to if (flavor == ServerFlavor.Akkoma) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            Timeline.Federated to CapabilityStatus.Supported,
        )

        /**
         * Per-flavor feed-chip table. Pixelfed has lists but no list timeline, so the chip
         * stays hidden. The leading number on a fork is the compatibility version, never a
         * followed-tags signal, so forks stay Unknown pending a runtime probe.
         */
        private fun feedChipCapabilities(
            machineVersion: Int?,
            releaseVersion: VersionTriple?,
            flavor: ServerFlavor,
        ): FeedChipCapabilities = FeedChipCapabilities(
            lists = if (flavor == ServerFlavor.Pixelfed) CapabilityStatus.Unsupported else CapabilityStatus.Supported,
            followedTags = if (flavor == ServerFlavor.Pleroma || flavor == ServerFlavor.Akkoma || flavor == ServerFlavor.Pixelfed) {
                CapabilityStatus.Unknown
            } else {
                when {
                    machineVersion != null -> CapabilityStatus.Supported
                    releaseVersion != null -> if (releaseVersion.atLeast(4, 0, 0)) CapabilityStatus.Supported else CapabilityStatus.Unsupported
                    else -> CapabilityStatus.Unknown
                }
            },
            antennas = CapabilityStatus.Unsupported,
        )

        /**
         * Per-flavor notification table. Pixelfed has no `unread_count`, no notification
         * dismissal, no push subscription and no streaming endpoint, so those stay
         * Unsupported instead of failing at runtime. Trends, suggestions and directory stay
         * Unknown on every flavor pending a runtime probe.
         */
        private fun notificationCapabilities(flavor: ServerFlavor): NotificationCapabilities =
            if (flavor == ServerFlavor.Pixelfed) {
                NotificationCapabilities(
                    dismissal = CapabilityStatus.Unsupported,
                    streaming = CapabilityStatus.Unsupported,
                    webPush = CapabilityStatus.Unsupported,
                    unread = CapabilityStatus.Unsupported,
                )
            } else {
                NotificationCapabilities()
            }

        /**
         * Which reaction mutation path the probe identified. Pleroma and Akkoma mutate
         * through the `pleroma` path once they advertise reactions; other families have no
         * such path, and an unprobed server stays Unknown. The source reuses this after it
         * merges emoji state, so the route can never drift from the merged advertisement.
         */
        fun reactionRoute(flavor: ServerFlavor, emoji: EmojiCapabilities): ReactionRoute = when {
            emoji.reactionMutation == CapabilityStatus.Supported &&
                (flavor == ServerFlavor.Pleroma || flavor == ServerFlavor.Akkoma) -> ReactionRoute.Pleroma
            flavor == ServerFlavor.Unknown -> ReactionRoute.Unknown
            else -> ReactionRoute.None
        }

        /**
         * Content types the server accepts at creation. Pleroma-family servers list them in
         * `pleroma.metadata.post_formats`. Null when the server lists none.
         */
        private fun postFormats(instance: JSONObject): List<String>? {
            val array = instance.optJSONObject("pleroma")?.optJSONObject("metadata")?.optJSONArray("post_formats")
                ?: instance.optJSONArray("post_formats") ?: return null
            return (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank).takeIf { it.isNotEmpty() }
        }

        /**
         * Mastodon and Pleroma report `configuration.translation.enabled`. Akkoma lists
         * `akkoma:machine_translation` in its feature list. A document with neither is a server that
         * offers no translation, so the row stays hidden.
         */
        fun translationCapability(instance: JSONObject): TranslationCapability {
            val enabled = instance.optJSONObject("configuration")?.optJSONObject("translation")
                ?.optBoolean("enabled", false) == true
            val akkoma = instance.optJSONObject("pleroma")?.optJSONObject("metadata")?.optJSONArray("features")
                ?.let { features -> (0 until features.length()).any { features.optString(it) == AKKOMA_TRANSLATION_FEATURE } }
                ?: false
            // The Mastodon route answers public and unlisted posts only.
            return if (enabled || akkoma) {
                TranslationCapability(CapabilityStatus.Supported, publicOnly = true)
            } else {
                TranslationCapability(CapabilityStatus.Unsupported)
            }
        }

        /**
         * Mastodon 3.5 and later edit by `PUT /statuses/:id`. Pleroma-family servers advertise
         * `editing` in their feature list, and their version string says nothing about it, so a
         * server with a `pleroma` block is judged by that list alone. Pixelfed has no source
         * route, so editing stays Unsupported instead of trusting the compatibility number.
         * A Pleroma-family server without metadata stays Unknown: the number proves nothing.
         */
        private fun editCapability(instance: JSONObject, releaseVersion: VersionTriple?, flavor: ServerFlavor): CapabilityStatus {
            if (flavor == ServerFlavor.Pixelfed) return CapabilityStatus.Unsupported
            val pleroma = instance.optJSONObject("pleroma")
            if (pleroma != null) {
                val features = pleroma.optJSONObject("metadata")?.optJSONArray("features")
                val editing = features != null && (0 until features.length()).any { features.optString(it) == PLEROMA_EDITING_FEATURE }
                return if (editing) CapabilityStatus.Supported else CapabilityStatus.Unsupported
            }
            if (flavor == ServerFlavor.Pleroma || flavor == ServerFlavor.Akkoma) return CapabilityStatus.Unknown
            return when {
                releaseVersion == null -> CapabilityStatus.Unknown
                releaseVersion.atLeast(3, 5, 0) -> CapabilityStatus.Supported
                else -> CapabilityStatus.Unsupported
            }
        }

        private const val PLEROMA_EDITING_FEATURE = "editing"
        private const val PLEROMA_CUSTOM_REACTION_FEATURE = "pleroma_custom_emoji_reactions"
        private const val AKKOMA_TRANSLATION_FEATURE = "akkoma:machine_translation"

        private class PostingLimits(val maxPostLength: Int, val capabilities: PostingCapabilities)

        /**
         * Reads the posting and media limits from `configuration`. A server without the
         * block (Mastodon before 4.0, or a v1 fallback) gets the documented defaults.
         * `max_toot_chars` is the pre-configuration limit that Pleroma-family servers report.
         */
        /**
         * Positive evidence that a server takes quotes: `quote_posting` in the Pleroma-family feature list, or
         * `feature_quote` in a Fedibird instance document. Absence proves nothing, so it never lowers the status.
         */
        private fun quoteEvidence(instance: JSONObject): Boolean {
            if (instance.optBoolean("feature_quote")) return true
            val features = instance.optJSONObject("pleroma")?.optJSONObject("metadata")?.optJSONArray("features") ?: return false
            return (0 until features.length()).any { features.optString(it) == QUOTE_POSTING_FEATURE }
        }

        private const val QUOTE_POSTING_FEATURE = "quote_posting"
        private const val MASTODON_SCHEDULE_LEAD_SECONDS = 300L

        /**
         * Local-only route from the detected flavor, so there is one derivation path. Glitch-soc
         * and Hometown take the flag; Pleroma-family servers take `visibility=local` when they
         * send a `pleroma` block. Other families have no route. The block is only evidence where
         * the flavor allows it (Pleroma, Akkoma, or still Unknown).
         */
        fun localOnlyRoute(flavor: ServerFlavor, instance: JSONObject): LocalOnlyRoute = when (flavor) {
            ServerFlavor.Glitch, ServerFlavor.Hometown -> LocalOnlyRoute.MastodonFlag
            ServerFlavor.Pleroma, ServerFlavor.Akkoma, ServerFlavor.Unknown ->
                if (instance.optJSONObject("pleroma") != null) LocalOnlyRoute.PleromaVisibility else LocalOnlyRoute.None
            ServerFlavor.Mastodon, ServerFlavor.Pixelfed -> LocalOnlyRoute.None
        }

        private fun postingLimits(instance: JSONObject, quotes: CapabilityStatus, quotePolicy: Boolean, flavor: ServerFlavor): PostingLimits {
            val configuration = instance.optJSONObject("configuration")
            val statuses = configuration?.optJSONObject("statuses")
            val media = configuration?.optJSONObject("media_attachments")
            val maxCharacters = statuses.positiveInt("max_characters")
                ?: instance.positiveInt("max_toot_chars")
                ?: DEFAULT_MAX_CHARACTERS
            val types = media?.optJSONArray("supported_mime_types")?.let { array ->
                (0 until array.length()).map { array.optString(it).lowercase() }.filter(String::isNotBlank).toSet()
            }?.takeIf { it.isNotEmpty() } ?: DEFAULT_UPLOAD_TYPES
            return PostingLimits(
                maxPostLength = maxCharacters,
                capabilities = PostingCapabilities(
                    lengthRule = PostLengthRule.MastodonCombined,
                    maxWarningLength = null,
                    // Pleroma-family servers report the attachment cap at the top level, and that block
                    // wins only when `configuration.statuses` says nothing: the nested value stays first.
                    maxAttachments = statuses.positiveInt("max_media_attachments")
                        ?: instance.positiveInt("max_media_attachments")
                        ?: DEFAULT_MAX_ATTACHMENTS,
                    // Limits audit: the fixture-confirmed Pleroma-family top-level keys are
                    // `max_toot_chars`, `max_media_attachments`, and `poll_limits`. No documented
                    // top-level key exists for the alt-text, image-byte, image-pixel, or video-byte
                    // limits in that shape, so `configuration.media_attachments` stays their only
                    // source. No top-level fallback is added here; inventing one would guess.
                    maxAltTextLength = media.positiveInt("description_limit") ?: DEFAULT_ALT_TEXT_LENGTH,
                    maxImageBytes = media.positiveLong("image_size_limit") ?: DEFAULT_IMAGE_BYTES,
                    maxImagePixels = media.positiveLong("image_matrix_limit") ?: DEFAULT_IMAGE_PIXELS,
                    maxVideoBytes = media.positiveLong("video_size_limit") ?: DEFAULT_VIDEO_BYTES,
                    uploadTypes = types,
                    charactersReservedPerUrl = statuses.positiveInt("characters_reserved_per_url")
                        ?: PostingCapabilities.DEFAULT_CHARACTERS_PER_URL,
                    mediaUpload = CapabilityStatus.Supported,
                    clientCompression = false,
                    // Mastodon limits a poll with media, not a quote with media.
                    quoteWithMedia = quotes == CapabilityStatus.Supported,
                    quoteApprovalPolicy = quotePolicy,
                    localOnly = localOnlyRoute(flavor, instance),
                    // Pixelfed takes neither `scheduled_at` nor `poll[...]`, so both stay absent
                    // and the composer never sends them. Every other family takes `scheduled_at`.
                    scheduling = if (flavor == ServerFlavor.Pixelfed) {
                        null
                    } else {
                        SchedulingCapability(ScheduleRoute.Mastodon, MASTODON_SCHEDULE_LEAD_SECONDS)
                    },
                    poll = if (flavor == ServerFlavor.Pixelfed) null else pollLimits(instance, configuration),
                ),
            )
        }

        /**
         * Mastodon reports `configuration.polls`. Pleroma-family servers report `poll_limits` at the
         * top level with different key names. A server with neither gets Mastodon's documented defaults.
         */
        private fun pollLimits(instance: JSONObject, configuration: JSONObject?): PollLimits {
            val polls = configuration?.optJSONObject("polls")
            val pleroma = instance.optJSONObject("poll_limits")
            return PollLimits(
                maxOptions = polls.positiveInt("max_options") ?: pleroma.positiveInt("max_options") ?: DEFAULT_POLL_OPTIONS,
                maxOptionLength = polls.positiveInt("max_characters_per_option")
                    ?: pleroma.positiveInt("max_option_chars") ?: DEFAULT_POLL_OPTION_LENGTH,
                minExpirySeconds = polls.positiveLong("min_expiration") ?: pleroma.positiveLong("min_expiration") ?: DEFAULT_POLL_MIN_EXPIRY,
                maxExpirySeconds = polls.positiveLong("max_expiration") ?: pleroma.positiveLong("max_expiration") ?: DEFAULT_POLL_MAX_EXPIRY,
                hideTotals = true,
            )
        }

        private const val DEFAULT_POLL_OPTIONS = 4
        private const val DEFAULT_POLL_OPTION_LENGTH = 50
        private const val DEFAULT_POLL_MIN_EXPIRY = 300L
        private const val DEFAULT_POLL_MAX_EXPIRY = 2_629_746L

        private fun JSONObject?.positiveInt(key: String): Int? =
            positiveLong(key)?.takeIf { it <= Int.MAX_VALUE }?.toInt()

        private fun JSONObject?.positiveLong(key: String): Long? {
            val value = this?.opt(key) as? Number ?: return null
            return value.toLong().takeIf { it > 0 }
        }

        private const val DEFAULT_MAX_CHARACTERS = 500
        private const val DEFAULT_MAX_ATTACHMENTS = 4
        private const val DEFAULT_ALT_TEXT_LENGTH = 1500
        private const val DEFAULT_IMAGE_BYTES = 16L * 1024 * 1024
        private const val DEFAULT_IMAGE_PIXELS = 33_177_600L
        private const val DEFAULT_VIDEO_BYTES = 99L * 1024 * 1024
        private val DEFAULT_UPLOAD_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

        private fun editableForMachineApi(api: Int): EditableProfileCapabilities = EditableProfileCapabilities(
            read = if (api >= 8) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            update = if (api >= 8) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            advancedSettings = if (api >= 8) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            imageDescriptions = if (api >= 9) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            imageUpload = if (api >= 8) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            imageDeletion = CapabilityStatus.Supported,
        )

        private fun editableForReleaseVersion(version: VersionTriple): EditableProfileCapabilities {
            val api8 = if (version.atLeast(4, 6, 0)) CapabilityStatus.Supported else CapabilityStatus.Unsupported
            return EditableProfileCapabilities(
                read = api8,
                update = api8,
                advancedSettings = api8,
                imageUpload = api8,
                imageDeletion = if (version.atLeast(4, 2, 0)) {
                    CapabilityStatus.Supported
                } else {
                    CapabilityStatus.Unsupported
                },
            )
        }

        /**
         * Reaction capabilities come from the advertisement alone.
         *
         * A recognized instance advertisement or a recognized NodeInfo advertisement proves
         * listing and mutation support at the server level with independent selection. A
         * missing or malformed advertisement stays Unknown: absence is not a verified
         * endpoint rejection, and it must never masquerade as proof of support.
         */
        private fun emojiCapabilities(instance: JSONObject, nodeInfoAdvertised: Boolean): EmojiCapabilities {
            val advertised = hasVerifiedEmojiReactionMetadata(instance) || nodeInfoAdvertised
            return EmojiCapabilities(
                catalog = CapabilityStatus.Supported,
                reactionListing = if (advertised) CapabilityStatus.Supported else CapabilityStatus.Unknown,
                reactionMutation = if (advertised) CapabilityStatus.Supported else CapabilityStatus.Unknown,
                selectionMode = if (advertised) ReactionSelectionMode.Independent else ReactionSelectionMode.Unknown,
            )
        }

        /**
         * Custom-emoji reactions need their own advertisement: `pleroma_custom_emoji_reactions`
         * in the instance feature list. A missing advertisement stays Unknown, never a guess
         * from the server version — and never Unsupported. ServerCapabilities
         * .allowsCustomReactions() grants the Akkoma optimistic allowance separately and the
         * source demotes it on a custom PUT rejection, so do not regress this to Unsupported.
         */
        private fun customReactionCapability(instance: JSONObject): CapabilityStatus {
            val features = instance.optJSONObject("pleroma")?.optJSONObject("metadata")?.optJSONArray("features") ?: return CapabilityStatus.Unknown
            return if ((0 until features.length()).any { features.optString(it) == PLEROMA_CUSTOM_REACTION_FEATURE }) {
                CapabilityStatus.Supported
            } else {
                CapabilityStatus.Unknown
            }
        }

        /**
         * Chooses at most one same-origin NodeInfo document from the well-known links.
         * NodeInfo 2.1 is preferred. A foreign, credentialed, or fragmented URL is rejected
         * before any request.
         */
        private fun supportedNodeInfoUrl(origin: String, wellKnown: JSONObject): String? {
            val links = wellKnown.optJSONArray("links") ?: return null
            val candidates = (0 until links.length()).mapNotNull { index ->
                val link = links.optJSONObject(index) ?: return@mapNotNull null
                val href = link.optString("href")
                if (href.isBlank()) null else link.optString("rel") to href
            }
            val chosen = candidates.firstOrNull { it.first.endsWith("/2.1") }?.second
                ?: candidates.firstOrNull { it.first.endsWith("/2.0") }?.second
                ?: return null
            return sameOriginUrl(origin, chosen)
        }

        /**
         * Returns the normalized URL only when it is same-origin with the connection origin.
         *
         * The connection origin is a validated HTTPS origin without credentials, a path, a
         * query, or a fragment. Matching the candidate scheme to the origin scheme therefore
         * keeps discovery HTTPS-only in production. This function still rejects foreign
         * hosts, credentialed URLs, and fragments explicitly.
         */
        private fun sameOriginUrl(origin: String, candidate: String): String? {
            val base = runCatching { URI(origin) }.getOrNull() ?: return null
            val target = runCatching { URI(candidate) }.getOrNull() ?: return null
            if (!target.scheme.equals(base.scheme, ignoreCase = true)) return null
            if (target.host.isNullOrBlank()) return null
            if (target.userInfo != null) return null
            if (target.fragment != null) return null
            if (!target.host.equals(base.host, ignoreCase = true)) return null
            if (effectivePort(target) != effectivePort(base)) return null
            return target.toASCIIString()
        }

        private fun effectivePort(uri: URI): Int = when {
            uri.port != -1 -> uri.port
            uri.scheme.equals("https", ignoreCase = true) -> 443
            uri.scheme.equals("http", ignoreCase = true) -> 80
            else -> -1
        }

        private val EMOJI_REACTION_FEATURE = "pleroma_emoji_reactions"

        private val LEADING_VERSION = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
    }
}
