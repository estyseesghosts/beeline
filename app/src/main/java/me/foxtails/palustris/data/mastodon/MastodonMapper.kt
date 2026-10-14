package me.foxtails.palustris.data.mastodon

import java.time.Instant
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.ConversationIdentity
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileField
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.Poll
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostInteractionCounts
import me.foxtails.palustris.domain.PostMention
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.QuoteApproval
import me.foxtails.palustris.domain.QuoteUnavailable
import me.foxtails.palustris.domain.ScheduledPost
import me.foxtails.palustris.domain.Translation
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import me.foxtails.palustris.domain.isExactHashtag
import org.json.JSONObject

object MastodonMapper {
    /** A tag from `trends/tags`. Counts add up the latest two history days. A malformed tag maps to null. */
    fun trendingHashtag(json: JSONObject): TrendingHashtag? {
        val name = json.optString("name").takeIf(::isExactHashtag) ?: return null
        val days = historyDays(json).take(TRENDING_DAYS)
        return TrendingHashtag(
            name = name,
            accounts = days.takeIf { it.isNotEmpty() }?.sumOf { it.accounts }?.toIntClamped(),
            uses = days.takeIf { it.isNotEmpty() }?.sumOf { it.uses }?.toIntClamped(),
        )
    }

    /** A tag from `v2/search`. The weight is `log2(1 + accounts)` over the whole history, like the catalog. */
    fun hashtagSuggestion(json: JSONObject): HashtagSuggestion? {
        val name = json.optString("name").takeIf(::isExactHashtag) ?: return null
        val days = historyDays(json)
        val weight = days.takeIf { it.isNotEmpty() }
            ?.let { kotlin.math.ln(1.0 + it.sumOf { day -> day.accounts }) / kotlin.math.ln(2.0) }
        return HashtagSuggestion(name, weight)
    }

    private fun Long.toIntClamped(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private class HistoryDay(val accounts: Long, val uses: Long)

    /** History values are strings. A day without a readable count is skipped. */
    private fun historyDays(json: JSONObject): List<HistoryDay> {
        val history = json.optJSONArray("history") ?: return emptyList()
        return (0 until history.length()).mapNotNull { index ->
            val day = history.optJSONObject(index) ?: return@mapNotNull null
            val accounts = day.opt("accounts")?.toString()?.toLongOrNull() ?: return@mapNotNull null
            HistoryDay(accounts.coerceAtLeast(0), day.opt("uses")?.toString()?.toLongOrNull()?.coerceAtLeast(0) ?: 0)
        }
    }

    private const val TRENDING_DAYS = 2

    fun directConversation(json: JSONObject, origin: String): DirectConversation? {
        val id = json.nullableString("id") ?: return null
        val lastStatus = json.optJSONObject("last_status") ?: return null
        val participants = json.optJSONArray("accounts")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                runCatching { account(values.getJSONObject(index), origin) }.getOrNull()
            }
        }.orEmpty()
        return DirectConversation(
            id = ConversationId(origin, id),
            participants = participants,
            lastPost = post(lastStatus, origin),
            unread = json.optBoolean("unread"),
            rootPostId = null,
            identity = ConversationIdentity.Verified,
        )
    }

    fun account(json: JSONObject, origin: String, includeMovedTo: Boolean = true): Account {
        val username = json.optString("username")
        val host = json.optString("acct").substringAfter('@', "").ifBlank {
            java.net.URI(origin).host.orEmpty()
        }
        val emoji = MastodonEmojiMapper.parseEmojis(json.optJSONArray("emojis"), origin)
        val fields = json.optJSONArray("fields")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { field ->
                    ProfileField(field.optString("name"), field.optString("value").htmlToText(emoji))
                }?.takeIf { it.name.isNotBlank() || it.value.isNotBlank() }
            }.take(4)
        }.orEmpty()
        val account = Account(
            id = AccountId(Connection(origin, Protocol.MASTODON), json.getString("id")),
            displayName = json.optString("display_name").ifBlank { username },
            handle = "@$username@$host",
            avatarUrl = json.nullableString("avatar"),
            biography = json.optString("note").stripHtml(emoji),
            profileFields = fields,
            bannerUrl = json.nullableString("header"),
            followersCount = json.optionalNonNegativeLong("followers_count"),
            followingCount = json.optionalNonNegativeLong("following_count"),
            postsCount = json.optionalNonNegativeLong("statuses_count"),
            locked = json.optBoolean("locked"),
            bot = json.optBoolean("bot"),
            emoji = emoji,
        )
        if (!includeMovedTo) return account
        val destination = json.optJSONObject("moved")?.let { moved ->
            runCatching { account(moved, origin, includeMovedTo = false) }.getOrNull()
        }?.takeIf { it.hasUsableProfileIdentity() }
        return account.copy(movedTo = destination)
    }

    /** API 8 self-profile responses carry raw values; no HTML or Markdown conversion. */
    fun editableProfile(json: JSONObject): EditableProfile = EditableProfile(
        id = json.optString("id"),
        displayName = json.optString("display_name"),
        biography = json.optString("note"),
        fields = json.optJSONArray("fields")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { field ->
                    EditableProfileField(field.optString("name"), field.optString("value"))
                }
            }
        }.orEmpty(),
        avatarUrl = json.nullableString("avatar"),
        avatarDescription = json.nullableString("avatar_description"),
        headerUrl = json.nullableString("header"),
        headerDescription = json.nullableString("header_description"),
        locked = json.optBoolean("locked"),
        bot = json.optBoolean("bot"),
        hideCollections = json.optBoolean("hide_collections"),
        discoverable = json.optBoolean("discoverable"),
        indexable = json.optBoolean("indexable"),
        showMedia = json.optBoolean("show_media"),
        showMediaReplies = json.optBoolean("show_media_replies"),
        showFeatured = json.optBoolean("show_featured"),
        attributionDomains = json.optJSONArray("attribution_domains")?.let { values ->
            (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty(),
    )

    /** Legacy verify-credentials responses prefer plaintext source values over rendered ones. */
    fun legacyEditableProfile(json: JSONObject): EditableProfile {
        val source = json.optJSONObject("source")
        val renderedFields = json.optJSONArray("fields")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { field ->
                    EditableProfileField(field.optString("name"), field.optString("value"))
                }
            }
        }.orEmpty()
        val sourceFields = source?.optJSONArray("fields")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { field ->
                    EditableProfileField(field.optString("name"), field.optString("value"))
                }
            }
        }
        return EditableProfile(
            id = json.optString("id"),
            displayName = source?.optString("display_name")?.ifBlank { null }
                ?: json.optString("display_name"),
            biography = source?.optString("note")?.ifBlank { null } ?: json.optString("note"),
            fields = sourceFields ?: renderedFields,
            avatarUrl = json.nullableString("avatar"),
            avatarDescription = json.nullableString("avatar_description"),
            headerUrl = json.nullableString("header"),
            headerDescription = json.nullableString("header_description"),
            locked = json.optBoolean("locked"),
            bot = json.optBoolean("bot"),
        )
    }

    fun relationship(json: JSONObject, profileId: AccountId): ProfileRelationship = ProfileRelationship(
        profileId = profileId,
        following = json.optBoolean("following"),
        followedBy = json.optBoolean("followed_by"),
        requested = json.optBoolean("requested"),
        muting = json.optBoolean("muting"),
        blocking = json.optBoolean("blocking"),
        // Hiding boosts needs a follow; without one the flag carries no meaning.
        boostsHidden = if (json.optBoolean("following") && json.has("showing_reblogs")) {
            !json.optBoolean("showing_reblogs", true)
        } else {
            null
        },
    )

    /** Maps a `POST /statuses/:id/translate` body. The content is HTML and renders as the post does. */
    fun translation(json: JSONObject, emoji: Map<String, CustomEmoji> = emptyMap()): Translation = Translation(
        text = json.optString("content").htmlToMarkdown(emoji),
        contentWarning = json.nullableString("spoiler_text"),
        sourceLanguage = json.nullableString("detected_source_language"),
        provider = json.nullableString("provider"),
        pollOptions = json.optJSONObject("poll")?.optJSONArray("options")?.let { options ->
            (0 until options.length()).map { options.getJSONObject(it).optString("title") }
        }.orEmpty(),
        attachmentDescriptions = json.optJSONArray("media_attachments")?.let { media ->
            (0 until media.length()).mapNotNull { index ->
                val item = media.getJSONObject(index)
                item.nullableString("description")?.let { item.optString("id") to it }
            }.toMap()
        }.orEmpty(),
    )

    /** Maps a Mastodon poll. `votes_count` is null when the author hides totals, which reads as zero. */
    fun poll(json: JSONObject): Poll {
        val ownVotes = json.optJSONArray("own_votes")?.let { votes -> (0 until votes.length()).map(votes::optInt) }.orEmpty().toSet()
        val options = json.optJSONArray("options")
        return Poll(
            id = json.optString("id"),
            options = (0 until (options?.length() ?: 0)).map { index ->
                val option = options!!.getJSONObject(index)
                PollOption(option.optString("title"), option.optInt("votes_count"), picked = index in ownVotes)
            },
            multiple = json.optBoolean("multiple"),
            expiresAtEpochMillis = json.nullableString("expires_at")?.let(::parseInstant),
            expired = json.optBoolean("expired"),
            votersCount = json.optionalNonNegativeInt("voters_count"),
            voted = json.optBoolean("voted"),
        )
    }

    /**
     * Maps a status's `mentions` array. `acct` has no host for an account on the home server, so the connection's host
     * completes it. An item with no `acct` is skipped.
     */
    fun mentions(items: org.json.JSONArray?, origin: String): List<PostMention> {
        if (items == null) return emptyList()
        val homeHost = java.net.URI(origin).host.orEmpty()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val acct = item.optString("acct").removePrefix("@").ifBlank { return@mapNotNull null }
            val handle = if ('@' in acct) "@$acct" else "@$acct@$homeHost"
            PostMention(
                handle = handle,
                accountId = item.nullableString("id")?.let { AccountId(Connection(origin, Protocol.MASTODON), it) },
                url = item.nullableString("url"),
            )
        }.distinctBy { it.handle.lowercase() }
    }

    fun post(json: JSONObject, origin: String, depth: Int = 0): Post {
        val id = EntityId(origin, json.getString("id"))
        val reblog = json.optJSONObject("reblog")
        if (reblog != null && depth < MAX_NESTING_DEPTH) {
            val resharedPost = post(reblog, origin, depth + 1)
            return resharedPost.copy(
                id = id,
                resharedBy = account(json.getJSONObject("account"), origin),
                url = json.nullableString("url") ?: resharedPost.url,
                reposted = json.optBoolean("reblogged", resharedPost.reposted),
                favourited = json.optBoolean("favourited", resharedPost.favourited),
                saved = json.optBoolean("bookmarked", resharedPost.saved),
                actionTargetId = resharedPost.actionTargetId ?: resharedPost.id,
            )
        }

        val poll = json.optJSONObject("poll")
        val quoted = quoteReference(json)
        val statusSensitive = json.optBoolean("sensitive")
        val emoji = MastodonEmojiMapper.parseEmojis(json.optJSONArray("emojis"), origin)
        val extensionReactions = MastodonReactionExtensionMapper.reactions(json, emoji)
        val interactionCounts = PostInteractionCounts(
            favouriteCount = json.optionalNonNegativeInt("favourites_count"),
            reactionCount = MastodonReactionExtensionMapper.aggregateCount(json, emoji),
            repostCount = checkedSum(
                json.optionalNonNegativeInt("reblogs_count"),
                json.optionalNonNegativeInt("quotes_count"),
            ),
            quoteRepostCount = json.optionalNonNegativeInt("quotes_count"),
            replyCount = json.optionalNonNegativeInt("replies_count"),
        )
        val selectedReactions = MastodonReactionExtensionMapper.selectedChoices(
            json, emoji, independentSelection = true,
        ).orEmpty()
        val mentions = mentions(json.optJSONArray("mentions"), origin)
        return Post(
            id = id,
            author = account(json.getJSONObject("account"), origin),
            mentions = mentions,
            text = quoted.contentWithoutFallback(json.optString("content")).htmlToMarkdown(emoji, mentions),
            publishedAtEpochMillis = parseInstant(json.optString("created_at")),
            audience = when (json.optString("visibility")) {
                "unlisted" -> Audience.Unlisted
                "private" -> Audience.Followers
                "direct" -> Audience.Direct
                else -> Audience.Public
            },
            attachments = json.optJSONArray("media_attachments")?.let { media ->
                (0 until media.length()).map { attachment(media.getJSONObject(it), statusSensitive) }
            }.orEmpty(),
            contentWarning = json.nullableString("spoiler_text"),
            language = json.nullableString("language"),
            localOnly = json.optBoolean("local_only") || (json.optString("visibility") == "local"),
            editedAtEpochMillis = json.nullableString("edited_at")?.let(::parseInstant),
            replyTo = json.nullableString("in_reply_to_id")?.let { EntityId(origin, it) },
            replyToAuthorId = json.nullableString("in_reply_to_account_id")
                ?.let { AccountId(Connection(origin, Protocol.MASTODON), it) },
            availableActions = MASTODON_ACTIONS,
            url = json.nullableString("url") ?: json.nullableString("uri"),
            interactionCounts = interactionCounts,
            quote = quoted.status?.takeIf { depth < MAX_NESTING_DEPTH }?.let { post(it, origin, depth + 1) },
            quoteUnavailable = quoted.unavailable
                ?: QuoteUnavailable.Unloaded.takeIf { quoted.status != null && depth >= MAX_NESTING_DEPTH },
            quoteId = if (quoted.status != null) null else quoted.id?.let { EntityId(origin, it) },
            quoteUrl = if (quoted.status != null) null else quoted.url,
            quoteApproval = quoteApproval(json),
            poll = poll?.let(::poll),
            reposted = json.optBoolean("reblogged"),
            favourited = json.optBoolean("favourited"),
            saved = json.optBoolean("bookmarked"),
            reactions = extensionReactions.orEmpty(),
            selectedReactions = selectedReactions,
            myReaction = selectedReactions.singleOrNull()?.submissionValue,
            emoji = emoji,
            serverFilters = MastodonFilterMapper.matches(json.optJSONArray("filtered")),
        )
    }

    fun attachment(json: JSONObject, statusSensitive: Boolean = false): Attachment {
        val type = json.optString("type")
        val originalMeta = json.optJSONObject("meta")?.optJSONObject("original")
        val smallMeta = json.optJSONObject("meta")?.optJSONObject("small")
        val mimeType = originalMeta?.nullableString("mime_type") ?: type.toMastodonMimeType()
        return Attachment(
            id = json.nullableString("id"),
            url = json.nullableString("url"),
            mimeType = mimeType,
            kind = type.toMastodonMediaKind(mimeType),
            description = json.nullableString("description"),
            previewUrl = json.nullableString("preview_url"),
            sensitive = statusSensitive || json.optBoolean("sensitive"),
            width = originalMeta?.positiveInt("width"),
            height = originalMeta?.positiveInt("height"),
            previewWidth = smallMeta?.positiveInt("width"),
            previewHeight = smallMeta?.positiveInt("height"),
            blurhash = json.nullableString("blurhash"),
            remoteOriginalUrl = json.nullableString("remote_url"),
            durationMs = originalMeta?.optDouble("duration")?.takeIf { it.isFinite() && it > 0.0 }?.let { (it * 1000).toLong() },
        )
    }

    /** Maps a `ScheduledStatus`: an ID, the time, and the parameters the post will be created with. */
    fun scheduled(json: JSONObject): ScheduledPost {
        val params = json.optJSONObject("params") ?: JSONObject()
        val media = json.optJSONArray("media_attachments")
        return ScheduledPost(
            id = json.getString("id"),
            scheduledAtEpochMillis = parseInstant(json.optString("scheduled_at")),
            text = params.optString("text"),
            audience = when (params.optString("visibility")) {
                "unlisted" -> Audience.Unlisted
                "private" -> Audience.Followers
                "direct" -> Audience.Direct
                else -> Audience.Public
            },
            contentWarning = params.nullableString("spoiler_text"),
            attachmentCount = media?.length() ?: 0,
            hasPoll = params.optJSONObject("poll") != null,
        )
    }

    /** What one status says about the post it quotes, in whichever of the known shapes it arrived. */
    internal class QuoteReference(
        val status: JSONObject? = null,
        val unavailable: QuoteUnavailable? = null,
        val id: String? = null,
        val url: String? = null,
        /** True when the server marked a quote at all, so its `RE:` fallback paragraph is redundant. */
        val present: Boolean = false,
    ) {
        /** The status HTML without the `<p class="quote-inline">` paragraph that older clients show in place of a quote. */
        fun contentWithoutFallback(content: String): String =
            if (present) QUOTE_INLINE_PARAGRAPH.replace(content, "") else content
    }

    /**
     * Reads the quote of a Mastodon-API status in this order: `quote` with a state and a status, `quote`
     * with a state and an ID, `quote` as a plain status (Fedibird, older Akkoma), `pleroma.quote`, the
     * legacy `quoted_status`, then a bare `quote_id`, `pleroma.quote_id`, `pleroma.quote_url` or a
     * trailing `RE:` link. A state other than `accepted` is kept as a reason for a placeholder.
     */
    internal fun quoteReference(json: JSONObject): QuoteReference {
        val pleroma = json.optJSONObject("pleroma")
        val quote = json.optJSONObject("quote")
        if (quote != null) {
            val state = quote.nullableString("state")
            val embedded = quote.optJSONObject("quoted_status") ?: quote.takeIf { it.has("content") && it.has("account") }
            val shallowId = quote.nullableString("quoted_status_id")
            return when {
                state != null && state != "accepted" -> QuoteReference(unavailable = quoteState(state), present = true)
                json.optBoolean("quote_muted") -> QuoteReference(unavailable = QuoteUnavailable.Hidden, present = true)
                embedded != null -> QuoteReference(status = embedded, present = true)
                shallowId != null -> QuoteReference(id = shallowId, present = true)
                else -> QuoteReference(present = true)
            }
        }
        pleroma?.optJSONObject("quote")?.let { plain ->
            return if (pleroma.has("quote_visible") && !pleroma.optBoolean("quote_visible", true)) {
                QuoteReference(unavailable = QuoteUnavailable.Hidden, present = true)
            } else {
                QuoteReference(status = plain, present = true)
            }
        }
        json.optJSONObject("quoted_status")?.let { return QuoteReference(status = it, present = true) }
        val id = json.nullableString("quote_id") ?: pleroma?.nullableString("quote_id")
        val url = pleroma?.nullableString("quote_url") ?: QUOTE_INLINE_LINK.find(json.optString("content"))?.groupValues?.get(1)
        if (json.optBoolean("quote_muted")) return QuoteReference(unavailable = QuoteUnavailable.Hidden, present = true)
        if (pleroma != null && pleroma.has("quote_visible") && !pleroma.optBoolean("quote_visible", true) && (id != null || url != null)) {
            return QuoteReference(unavailable = QuoteUnavailable.Hidden, present = true)
        }
        return QuoteReference(id = id, url = url, present = id != null || url != null)
    }

    private fun quoteState(state: String): QuoteUnavailable = when (state) {
        "pending" -> QuoteUnavailable.Pending
        "rejected" -> QuoteUnavailable.Rejected
        "revoked" -> QuoteUnavailable.Revoked
        "deleted" -> QuoteUnavailable.Deleted
        "unauthorized" -> QuoteUnavailable.Unauthorized
        "blocked_account" -> QuoteUnavailable.BlockedAccount
        "blocked_domain" -> QuoteUnavailable.BlockedDomain
        "muted_account" -> QuoteUnavailable.MutedAccount
        else -> QuoteUnavailable.Hidden
    }

    private fun quoteApproval(json: JSONObject): QuoteApproval =
        when (json.optJSONObject("quote_approval")?.nullableString("current_user")) {
            "automatic" -> QuoteApproval.Automatic
            "manual" -> QuoteApproval.Manual
            "denied" -> QuoteApproval.Denied
            else -> QuoteApproval.Unknown
        }

    private val QUOTE_INLINE_PARAGRAPH = Regex("<p\\s+class=\"quote-inline\">.*?</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val QUOTE_INLINE_LINK = Regex("<p\\s+class=\"quote-inline\">\\s*RE:\\s*<a\\s[^>]*href=\"([^\"]+)\"", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    private fun parseInstant(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private const val MAX_NESTING_DEPTH = 3
    private val MASTODON_ACTIONS = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.Bookmark)
}

private fun Account.hasUsableProfileIdentity(): Boolean = id.localId.isNotBlank() &&
    (displayName.isNotBlank() || handle.removePrefix("@").substringBefore("@").isNotBlank() || avatarUrl != null)

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.optionalNonNegativeLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key) ?: return null
    val parsed = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
    return parsed?.takeIf { it >= 0L }
}

private fun JSONObject.optionalNonNegativeInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key) ?: return null
    val raw = when (value) {
        is Number, is String -> value.toString()
        else -> return null
    }
    return runCatching { java.math.BigDecimal(raw).toBigIntegerExact().intValueExact() }
        .getOrNull()
        ?.takeIf { it >= 0 }
}

private fun checkedSum(first: Int?, second: Int?): Int? = when {
    first == null -> null
    second == null -> first
    else -> runCatching { Math.addExact(first, second) }.getOrNull()
}

private fun String.toMastodonMimeType(): String = when (this) {
    "image" -> "image/*"
    "video" -> "video/*"
    "gifv" -> "video/*"
    "audio" -> "audio/*"
    else -> if (isBlank()) "application/octet-stream" else this
}

private fun String.toMastodonMediaKind(mimeType: String): MediaKind = when (this) {
    "image" -> MediaKind.Image
    "gifv" -> MediaKind.Video
    "video" -> MediaKind.Video
    "audio" -> MediaKind.Audio
    else -> me.foxtails.palustris.domain.mediaKindForMimeType(mimeType)
}

private fun JSONObject.positiveInt(key: String): Int? = when (val value = opt(key)) {
    is Number -> value.toInt().takeIf { it > 0 }
    is String -> value.toIntOrNull()?.takeIf { it > 0 }
    else -> null
}

private fun String.stripHtml(emoji: Map<String, CustomEmoji> = emptyMap()): String = htmlToText(emoji)

private val htmlAnchor = Regex("<a\\b[^>]*\\bhref\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private val emojiImage = Regex("<img\\b[^>]*\\balt\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>", setOf(RegexOption.IGNORE_CASE))

/** Replaces known emoji images with their `:shortcode:` alternate token before tag removal. */
private fun String.preserveEmojiAlts(emoji: Map<String, CustomEmoji>): String {
    if (emoji.isEmpty()) return replace(emojiImage, "")
    val known = buildSet {
        emoji.keys.forEach { key ->
            add(key)
            add(":$key:")
        }
    }
    return replace(emojiImage) { match ->
        val alt = match.groupValues[1].trim()
        if (alt in known) alt else ""
    }
}

private fun String.htmlToMarkdown(
    emoji: Map<String, CustomEmoji> = emptyMap(),
    mentions: List<PostMention> = emptyList(),
): String {
    val linked = preserveEmojiAlts(emoji).replace(htmlAnchor) { match ->
        val label = match.groupValues[2].htmlToText()
        // A mention link names its account by URL. The text then carries the complete handle, not the short one.
        val mention = mentions.firstOrNull { it.url != null && it.url == match.groupValues[1] }
        if (mention != null && label.trimStart().startsWith("@")) mention.handle
        else if (label.trimStart().startsWith("@")) label
        else "[$label](${match.groupValues[1]})"
    }
    return linked.htmlToText()
}

private fun String.htmlToText(emoji: Map<String, CustomEmoji>): String =
    preserveEmojiAlts(emoji).htmlToText()

private fun String.htmlToText(): String = replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace(Regex("&(#x[0-9a-f]+|#\\d+|amp|lt|gt|quot|apos|nbsp);", RegexOption.IGNORE_CASE)) { match ->
        when (val entity = match.value.lowercase()) {
            "&amp;" -> "&"
            "&lt;" -> "<"
            "&gt;" -> ">"
            "&quot;" -> "\""
            "&apos;" -> "'"
            "&nbsp;" -> " "
            else -> entity.removePrefix("&#").removeSuffix(";").let {
                runCatching {
                    val value = if (it.startsWith("x")) it.removePrefix("x").toInt(16) else it.toInt()
                    String(Character.toChars(value))
                }.getOrDefault(match.value)
            }
        }
    }
    .trim()
