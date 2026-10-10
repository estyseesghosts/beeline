package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PostDraftQuotePreview
import me.foxtails.palustris.domain.Protocol

/**
 * Process-recreation snapshot of [ComposerEditorState]. A snapshot from before reply and quote
 * targets were stored (nine fields) restores without targets. A snapshot from before threads were
 * stored (23 fields) restores its flat text and warning as one entry. The flat fields keep their
 * positions and always describe the first entry, so the older layouts stay readable. Entries and
 * the saved snapshot follow them. Image bytes are never part of the snapshot; only media records.
 */
private const val LEGACY_SIZE = 9
private const val TARGETS_SIZE = 23
private const val ENTRIES_INDEX = 23
private const val SAVED_ENTRIES_INDEX = 24
private const val MEDIA_DRAFT_ID_INDEX = 25

/** Restores shell-surviving fields across process recreation. */
internal val ComposerEditorStateSaver: Saver<ComposerEditorState, Any> = listSaver(
    save = {
        listOf(
            it.draftId,
            it.text,
            it.savedText,
            it.warning,
            it.savedWarning,
            it.warningEnabled,
            it.audience.name,
            it.savedAudience.name,
            it.error,
            it.replyTo?.connection,
            it.replyTo?.value,
            it.quoteOf?.connection,
            it.quoteOf?.value,
            it.savedReplyTo,
            it.savedQuoteOf,
            it.targetPreview?.authorDisplayName,
            it.targetPreview?.authorHandle,
            it.targetPreview?.text,
            it.targetPreview?.url,
            it.boundAccount?.connection?.origin,
            it.boundAccount?.connection?.protocol?.name,
            it.boundAccount?.localId,
            it.boundRevision,
            it.entries.map(::saveEntry),
            it.savedEntries.map(::saveContent),
            it.mediaDraftId,
        )
    },
    restore = {
        val firstEntry = ComposerEntryState(
            text = it[1] as String,
            warning = it[3] as String,
            warningEnabled = it[5] as Boolean,
        )
        val flatSaved = ComposerEntryContent(
            id = firstEntry.id,
            text = it[2] as String,
            warning = it[4] as String,
            media = emptyList(),
        )
        val base = ComposerEditorState(
            draftId = it[0] as String?,
            entries = listOf(firstEntry),
            savedEntries = listOf(flatSaved),
            audience = Audience.valueOf(it[6] as String),
            savedAudience = Audience.valueOf(it[7] as String),
            error = it[8] as String?,
        )
        if (it.size <= LEGACY_SIZE) return@listSaver base
        val withTargets = base.copy(
            replyTo = entity(it[9], it[10]),
            quoteOf = entity(it[11], it[12]),
            savedReplyTo = it[13] as String?,
            savedQuoteOf = it[14] as String?,
            targetPreview = (it[17] as String?)?.let { text ->
                PostDraftQuotePreview(
                    authorDisplayName = it[15] as String? ?: "",
                    authorHandle = it[16] as String? ?: "",
                    text = text,
                    url = it[18] as String?,
                )
            },
            boundAccount = account(it[19], it[20], it[21]),
            boundRevision = (it[22] as? Number)?.toLong() ?: 0L,
        )
        if (it.size <= TARGETS_SIZE) return@listSaver withTargets
        val entries = restoreEntries(it[ENTRIES_INDEX])
        val saved = restoreContents(it[SAVED_ENTRIES_INDEX])
        val mediaDraftId = it.getOrNull(MEDIA_DRAFT_ID_INDEX) as? String
        if (entries.isEmpty() || saved.isEmpty()) {
            withTargets
        } else {
            withTargets.copy(entries = entries, savedEntries = saved, mediaDraftId = mediaDraftId)
        }
    },
)

private fun saveEntry(entry: ComposerEntryState): List<Any?> =
    listOf(entry.id, entry.text, entry.warning, entry.warningEnabled, entry.media.map(::saveMedia))

private fun saveContent(content: ComposerEntryContent): List<Any?> =
    listOf(content.id, content.text, content.warning, content.media.map(::saveMedia))

private fun saveMedia(media: DraftMedia): List<Any?> =
    listOf(media.id, media.mimeType, media.width, media.height, media.byteSize, media.altText)

private fun restoreEntries(value: Any?): List<ComposerEntryState> =
    (value as? List<*>).orEmpty().mapNotNull { raw ->
        val row = raw as? List<*> ?: return@mapNotNull null
        if (row.size < 5) return@mapNotNull null
        ComposerEntryState(
            id = row[0] as? String ?: return@mapNotNull null,
            text = row[1] as? String ?: "",
            warning = row[2] as? String ?: "",
            warningEnabled = row[3] as? Boolean ?: false,
            media = restoreMedia(row[4]),
        )
    }

private fun restoreContents(value: Any?): List<ComposerEntryContent> =
    (value as? List<*>).orEmpty().mapNotNull { raw ->
        val row = raw as? List<*> ?: return@mapNotNull null
        if (row.size < 4) return@mapNotNull null
        ComposerEntryContent(
            id = row[0] as? String ?: return@mapNotNull null,
            text = row[1] as? String ?: "",
            warning = row[2] as? String ?: "",
            media = restoreMedia(row[3]),
        )
    }

private fun restoreMedia(value: Any?): List<DraftMedia> =
    (value as? List<*>).orEmpty().mapNotNull { raw ->
        val row = raw as? List<*> ?: return@mapNotNull null
        if (row.size < 6) return@mapNotNull null
        DraftMedia(
            id = row[0] as? String ?: return@mapNotNull null,
            mimeType = row[1] as? String ?: "application/octet-stream",
            width = (row[2] as? Number)?.toInt(),
            height = (row[3] as? Number)?.toInt(),
            byteSize = (row[4] as? Number)?.toLong() ?: 0L,
            altText = row[5] as? String,
        )
    }

private fun entity(connection: Any?, value: Any?): EntityId? {
    val origin = connection as? String ?: return null
    val id = value as? String ?: return null
    return EntityId(origin, id)
}

private fun account(origin: Any?, protocol: Any?, localId: Any?): AccountId? {
    val resolvedOrigin = origin as? String ?: return null
    val resolvedProtocol = (protocol as? String)?.let { name -> Protocol.entries.firstOrNull { it.name == name } }
        ?: return null
    val resolvedLocalId = localId as? String ?: return null
    return AccountId(Connection(resolvedOrigin, resolvedProtocol), resolvedLocalId)
}
