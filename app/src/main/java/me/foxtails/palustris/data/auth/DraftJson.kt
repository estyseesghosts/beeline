package me.foxtails.palustris.data.auth

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftEntry
import me.foxtails.palustris.domain.PostDraftQuotePreview
import org.json.JSONArray
import org.json.JSONObject

/** Draft row JSON. A row without `media` or `followUps` is a single-post draft from before threads. */
internal fun PostDraft.toJson() = JSONObject()
    .put("id", id).put("origin", accountId?.connection?.origin).put("localId", accountId?.localId)
    .put("protocol", accountId?.connection?.protocol?.name).put("text", text).put("audience", audience.name)
    .put("contentWarning", contentWarning).put("replyTo", replyTo?.value).put("quoteOf", quoteOf?.value)
    .put("updatedAt", updatedAt)
    .put("quotePreview", quotePreview?.let {
        JSONObject()
            .put("authorDisplayName", it.authorDisplayName)
            .put("authorHandle", it.authorHandle)
            .put("text", it.text)
            .put("url", it.url)
            .put("authorEmoji", encodeEmojiMap(it.authorEmoji))
            .put("postEmoji", encodeEmojiMap(it.postEmoji))
    })
    .put("attachments", JSONArray(attachments.map { attachment ->
        JSONObject()
            .put("id", attachment.id)
            .put("url", attachment.url)
            .put("mimeType", attachment.mimeType)
            .put("kind", attachment.kind.name)
            .put("description", attachment.description)
            .put("previewUrl", attachment.previewUrl)
            .put("sensitive", attachment.sensitive)
            .put("width", attachment.width)
            .put("height", attachment.height)
            .put("previewWidth", attachment.previewWidth)
            .put("previewHeight", attachment.previewHeight)
            .put("blurhash", attachment.blurhash)
            .put("remoteOriginalUrl", attachment.remoteOriginalUrl)
    }))
    .put("media", mediaJson(media))
    .put("followUps", JSONArray(followUps.map { entry ->
        JSONObject().put("id", entry.id).put("text", entry.text).put("contentWarning", entry.contentWarning).put("media", mediaJson(entry.media))
    }))
    .put("poll", poll?.let { JSONObject().put("choices", JSONArray(it.choices)).put("multiple", it.multiple).put("expiresAt", it.expiresAt?.toEpochMilli()) })

internal fun JSONObject.toDraft(): PostDraft {
    val origin = optString("origin").takeIf { it.isNotBlank() }
    val accountId = if (origin == null || isNull("localId") || isNull("protocol")) null else AccountId(me.foxtails.palustris.domain.Connection(origin, me.foxtails.palustris.domain.Protocol.valueOf(getString("protocol"))), getString("localId"))
    val attachments = optJSONArray("attachments")?.let { array ->
        (0 until array.length()).map { index ->
            array.getJSONObject(index).let { json ->
                val mimeType = json.nullableString("mimeType") ?: "application/octet-stream"
                Attachment(
                    id = json.nullableString("id"),
                    url = json.nullableString("url"),
                    mimeType = mimeType,
                    kind = json.optString("kind").takeIf { it.isNotBlank() }?.let { value ->
                        runCatching { MediaKind.valueOf(value) }.getOrNull()
                    } ?: me.foxtails.palustris.domain.mediaKindForMimeType(mimeType),
                    description = json.nullableString("description"),
                    previewUrl = json.nullableString("previewUrl"),
                    sensitive = json.optBoolean("sensitive"),
                    width = json.positiveInt("width"),
                    height = json.positiveInt("height"),
                    previewWidth = json.positiveInt("previewWidth"),
                    previewHeight = json.positiveInt("previewHeight"),
                    blurhash = json.nullableString("blurhash"),
                    remoteOriginalUrl = json.nullableString("remoteOriginalUrl"),
                )
            }
        }
    }.orEmpty()
    val pollJson = optJSONObject("poll")
    val poll = pollJson?.let { PollRequest((0 until it.getJSONArray("choices").length()).map(it.getJSONArray("choices")::getString), it.optBoolean("multiple"), it.optLong("expiresAt").takeIf { value -> value > 0 }?.let(java.time.Instant::ofEpochMilli)) }
    val quotePreview = optJSONObject("quotePreview")?.let {
        PostDraftQuotePreview(
            authorDisplayName = it.optString("authorDisplayName"),
            authorHandle = it.optString("authorHandle"),
            text = it.optString("text"),
            url = it.optString("url").takeIf(String::isNotBlank),
            authorEmoji = decodeEmojiMap(it.optJSONObject("authorEmoji")),
            postEmoji = decodeEmojiMap(it.optJSONObject("postEmoji")),
        )
    }?.takeIf { it.authorDisplayName.isNotBlank() || it.authorHandle.isNotBlank() || it.text.isNotBlank() }
    return PostDraft(getString("id"), accountId, optString("text"), Audience.valueOf(optString("audience", Audience.Public.name)), optString("contentWarning").takeIf(String::isNotBlank), optString("replyTo").takeIf(String::isNotBlank)?.let { EntityId(origin.orEmpty(), it) }, optString("quoteOf").takeIf(String::isNotBlank)?.let { EntityId(origin.orEmpty(), it) }, attachments, poll, optLong("updatedAt"), quotePreview, media = optJSONArray("media").toDraftMedia(), followUps = optJSONArray("followUps").toFollowUps())
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

private fun encodeEmojiMap(emoji: Map<String, me.foxtails.palustris.domain.CustomEmoji>): JSONObject = JSONObject().apply {
    emoji.forEach { (key, value) ->
        put(key, JSONObject()
            .put("shortcode", value.shortcode)
            .put("animatedUrl", value.animatedUrl?.value)
            .put("staticUrl", value.staticUrl?.value)
            .put("category", value.category)
            .put("aliases", JSONArray(value.aliases))
            .put("visibleInPicker", value.visibleInPicker)
            .put("submissionValue", value.submissionValue))
    }
}

private fun decodeEmojiMap(json: JSONObject?): Map<String, me.foxtails.palustris.domain.CustomEmoji> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, me.foxtails.palustris.domain.CustomEmoji>()
    json.keys().asSequence().forEach { key ->
        runCatching {
            val entry = json.getJSONObject(key)
            entry.optString("shortcode").takeIf { it.isNotBlank() }?.let { shortcode ->
                val submissionValue = entry.optString("submissionValue").takeIf { it.isNotBlank() } ?: ":$shortcode:"
                me.foxtails.palustris.domain.CustomEmoji(
                    shortcode = shortcode,
                    animatedUrl = entry.optString("animatedUrl").takeIf { it.isNotBlank() }
                        ?.let(me.foxtails.palustris.domain.ValidatedUrl::https),
                    staticUrl = entry.optString("staticUrl").takeIf { it.isNotBlank() }
                        ?.let(me.foxtails.palustris.domain.ValidatedUrl::https),
                    category = entry.optString("category").takeIf { it.isNotBlank() },
                    aliases = entry.optJSONArray("aliases")?.let { values ->
                        (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty(),
                    visibleInPicker = entry.optBoolean("visibleInPicker", true),
                    submissionValue = submissionValue,
                )
            }
        }.getOrNull()?.let { result[key] = it }
    }
    return result
}

private fun JSONObject.positiveInt(key: String): Int? = when (val value = opt(key)) {
    is Number -> value.toInt().takeIf { it > 0 }
    is String -> value.toIntOrNull()?.takeIf { it > 0 }
    else -> null
}

private fun mediaJson(media: List<DraftMedia>) = JSONArray(media.map {
    JSONObject()
        .put("id", it.id)
        .put("mimeType", it.mimeType)
        .put("width", it.width)
        .put("height", it.height)
        .put("byteSize", it.byteSize)
        .put("altText", it.altText)
})

private fun JSONArray?.toDraftMedia(): List<DraftMedia> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val json = optJSONObject(index) ?: return@mapNotNull null
        val id = json.nullableString("id") ?: return@mapNotNull null
        DraftMedia(
            id = id,
            mimeType = json.nullableString("mimeType") ?: "application/octet-stream",
            width = json.positiveInt("width"),
            height = json.positiveInt("height"),
            byteSize = json.optLong("byteSize"),
            altText = json.nullableString("altText"),
        )
    }
}

private fun JSONArray?.toFollowUps(): List<PostDraftEntry> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val json = optJSONObject(index) ?: return@mapNotNull null
        val id = json.nullableString("id") ?: return@mapNotNull null
        PostDraftEntry(
            id = id,
            text = json.optString("text"),
            contentWarning = json.nullableString("contentWarning"),
            media = json.optJSONArray("media").toDraftMedia(),
        )
    }
}
