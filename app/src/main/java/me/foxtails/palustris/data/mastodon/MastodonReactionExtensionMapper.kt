package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps fixture-confirmed Pleroma/Akkoma `emoji_reactions` summaries. The `pleroma` copy
 * wins: both servers write it, while the top-level copy exists only on newer Akkoma and
 * can lag it. The raw `name` is the opaque submission identity and is never rewritten.
 * Colons are stripped and `@host` is split into a canonical shortcode plus a remote host,
 * with the host marking picker-excluded remote emoji. `url` and `account_ids` are
 * optional; unknown fields are ignored. An image resolves only from the entry's own URL
 * or the status's local emoji metadata; unresolved custom identities stay visible as
 * text. Standard `emojis` arrays are never converted into reactions here.
 */
object MastodonReactionExtensionMapper {
    fun hasReactionExtension(json: JSONObject): Boolean = reactionsArray(json) != null

    fun reactions(json: JSONObject, statusEmojis: Map<String, CustomEmoji>): List<Reaction>? {
        val array = reactionsArray(json) ?: return null
        return (0 until array.length()).mapNotNull { index ->
            entry(array.optJSONObject(index), statusEmojis)?.let { parsed ->
                Reaction(parsed.identity, parsed.count, parsed.me, parsed.metadata, parsed.host, parsed.accountIds)
            }
        }
    }

    fun selectedChoices(
        json: JSONObject,
        statusEmojis: Map<String, CustomEmoji>,
        independentSelection: Boolean,
    ): List<EmojiChoice>? {
        val array = reactionsArray(json) ?: return null
        val selected = (0 until array.length()).mapNotNull { index ->
            val parsed = entry(array.optJSONObject(index), statusEmojis) ?: return@mapNotNull null
            if (!parsed.me) return@mapNotNull null
            EmojiChoice(parsed.identity, parsed.identity, parsed.metadata)
        }
        return if (selected.isEmpty()) emptyList() else if (independentSelection) selected else selected.takeLast(1)
    }

    fun aggregateCount(json: JSONObject, statusEmojis: Map<String, CustomEmoji>): Int? {
        val array = reactionsArray(json) ?: return null
        var total = 0
        for (index in 0 until array.length()) {
            val mapped = entry(array.optJSONObject(index), statusEmojis) ?: return null
            total = runCatching { Math.addExact(total, mapped.count) }.getOrNull() ?: return null
        }
        return total
    }

    private fun reactionsArray(json: JSONObject): JSONArray? = json.optJSONObject("pleroma")?.optJSONArray("emoji_reactions")
        ?: json.optJSONArray("emoji_reactions")
        ?: json.optJSONObject("akkoma")?.optJSONArray("emoji_reactions")

    private data class Entry(
        val identity: String,
        val count: Int,
        val me: Boolean,
        val metadata: CustomEmoji?,
        val host: String?,
        val accountIds: List<String>,
    )

    private fun entry(json: JSONObject?, statusEmojis: Map<String, CustomEmoji>): Entry? {
        if (json == null) return null
        val identity = json.optString("name").takeIf { it.isNotBlank() } ?: return null
        val count = json.optInt("count", 0).coerceAtLeast(0)
        val me = json.optBoolean("me", false)
        val canonical = identity.trim().trim(':')
        val shortcode = canonical.substringBefore('@')
        val host = canonical.substringAfter('@', "").takeIf { '@' in canonical }?.takeIf { it.isNotBlank() }
        val accountIds = json.optJSONArray("account_ids")?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
        }.orEmpty()
        val image = reactionImage(json, identity, shortcode, statusEmojis)
        val metadata = if (image != null) {
            CustomEmoji(
                shortcode = shortcode,
                animatedUrl = image,
                staticUrl = image,
                visibleInPicker = false,
                submissionValue = identity,
            )
        } else {
            null
        }
        return Entry(identity, count, me, metadata, host, accountIds)
    }

    private fun reactionImage(
        json: JSONObject,
        identity: String,
        shortcode: String,
        statusEmojis: Map<String, CustomEmoji>,
    ): ValidatedUrl? {
        val direct = MediaRequestPolicy.validatedWebUrl(json.optString("url"))
        if (direct != null) return direct
        return statusEmojis[identity]?.let(MediaRequestPolicy::emojiImage)?.url
            ?: statusEmojis[identity.trim(':')]?.let(MediaRequestPolicy::emojiImage)?.url
            ?: statusEmojis[shortcode]?.let(MediaRequestPolicy::emojiImage)?.url
            ?: statusEmojis[":$shortcode:"]?.let(MediaRequestPolicy::emojiImage)?.url
    }
}
