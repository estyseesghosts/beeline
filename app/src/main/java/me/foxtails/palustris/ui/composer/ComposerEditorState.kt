package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PostDraftQuotePreview
import me.foxtails.palustris.domain.Protocol

/**
 * Saveable composer editor fields for one connected account.
 *
 * The composer owner holds these values. The navigation shell reads them but does not own them. The
 * saved snapshot drives the dirty check. Reply and quote target identities are stored with the
 * account and session revision they were chosen under ([boundAccount], [boundRevision]), so a
 * restored editor can verify them before it is used. The draft list is not stored here. Custom
 * emoji maps in [targetPreview] are not restored; the preview shows plain text.
 */
data class ComposerEditorState(
    val draftId: String? = null,
    val text: String = "",
    val savedText: String = "",
    val warning: String = "",
    val savedWarning: String = "",
    val warningEnabled: Boolean = false,
    val audience: Audience = Audience.Public,
    val savedAudience: Audience = Audience.Public,
    val error: String? = null,
    val replyTo: EntityId? = null,
    val quoteOf: EntityId? = null,
    val savedReplyTo: String? = null,
    val savedQuoteOf: String? = null,
    val targetPreview: PostDraftQuotePreview? = null,
    val boundAccount: AccountId? = null,
    val boundRevision: Long = 0L,
) {
    val hasTargets: Boolean get() = replyTo != null || quoteOf != null

    companion object {
        private const val LEGACY_SIZE = 9

        /** Restores shell-surviving fields across process recreation. */
        val Saver: Saver<ComposerEditorState, Any> = listSaver(
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
                )
            },
            restore = {
                val base = ComposerEditorState(
                    draftId = it[0] as String?,
                    text = it[1] as String,
                    savedText = it[2] as String,
                    warning = it[3] as String,
                    savedWarning = it[4] as String,
                    warningEnabled = it[5] as Boolean,
                    audience = Audience.valueOf(it[6] as String),
                    savedAudience = Audience.valueOf(it[7] as String),
                    error = it[8] as String?,
                )
                if (it.size <= LEGACY_SIZE) return@listSaver base
                base.copy(
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
            },
        )

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
    }
}
