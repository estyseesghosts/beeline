package me.foxtails.palustris.ui.composer

import me.foxtails.palustris.domain.COMPRESSIBLE_IMAGE_TYPES
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.UploadCompression

/**
 * Pure posting rules for the composer thread. [ComposerOwner] holds the editor; these rules decide
 * what the body shows and whether Post is enabled, so the owner does not grow.
 */

/** An entry can be posted with text or with at least one image. */
internal fun ComposerEntryState.hasPostableContent(): Boolean = text.isNotBlank() || media.isNotEmpty()

/** Characters left for this entry, or null when the server reported no limit. */
internal fun ComposerEntryState.remaining(limits: PostLimits): Int? = limits.remaining(text, effectiveWarning)

/** Characters left for this entry's content warning when the server limits it separately. */
internal fun ComposerEntryState.warningRemaining(limits: PostLimits): Int? =
    limits.warningRemaining(effectiveWarning)

internal fun ComposerEntryState.exceedsLimit(limits: PostLimits): Boolean = limits.exceeds(text, effectiveWarning)

/** True when every entry has content and none is over a limit. Empty entries block Post. */
internal fun ComposerEditorState.postable(limits: PostLimits): Boolean =
    entries.all { it.hasPostableContent() && !it.exceedsLimit(limits) }

/** True when an entry holds an image the app may re-encode. A GIF never counts. */
internal fun ComposerEditorState.hasCompressibleImage(): Boolean =
    entries.any { entry -> entry.media.any { it.mimeType.lowercase() in COMPRESSIBLE_IMAGE_TYPES } }

/** The compression choice that reaches the thread publication: the answer to the question, else the setting. */
internal fun ComposerOwner.resolveCompress(): Boolean =
    compressChoice ?: (context.contract.postPreferences.uploadCompression != UploadCompression.Never)

/** True when Post must ask first: the setting is Ask, the server leaves compression to the client, and an image qualifies. */
internal fun ComposerOwner.mustAskCompression(): Boolean =
    context.contract.postPreferences.uploadCompression == UploadCompression.Ask &&
        context.contract.limits.posting.clientCompression &&
        editor.hasCompressibleImage()

/** The editor with [prepare] applied to the text of every entry, for example to clean links. */
internal fun ComposerEditorState.withPreparedText(prepare: (String) -> String): ComposerEditorState =
    copy(entries = entries.map { it.copy(text = prepare(it.text)) })
