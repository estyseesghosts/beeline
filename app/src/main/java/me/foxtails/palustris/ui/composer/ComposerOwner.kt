package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.EditAttachment
import me.foxtails.palustris.domain.EditPostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostingVisibilityPolicy
import me.foxtails.palustris.domain.QuoteApprovalPolicy
import me.foxtails.palustris.domain.ScheduledPost
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.ThreadPublishFailure
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.domain.carriesQuote
import me.foxtails.palustris.domain.toPollRequest
import me.foxtails.palustris.domain.toPostDraft
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftsContract

/** Something the user should hear after a publish that went through but not as asked. */
enum class PublishNotice {
    /** The server holds the post and will publish it at the chosen time. */
    Scheduled,

    /** The server posted without the quote, so it does not seem to take quotes. */
    QuoteDropped,
}

/** A composer transition that the shell applies by placing the composer overlay. */
sealed interface ComposerNavigation {
    data object New : ComposerNavigation
    data class Reply(val target: OwnedPost) : ComposerNavigation
    data class Quote(val target: OwnedPost) : ComposerNavigation
    data class Draft(val draftId: String) : ComposerNavigation

    /** Opens the editor on one of the account's own posts. */
    data class Edit(val target: OwnedPost) : ComposerNavigation
}

/** Inputs the composer owner reads from the connected shell on each recomposition. */
data class ComposerOwnerContext(
    val account: Account? = null,
    val contract: ComposerContract = ComposerContract.Empty,
    val canReply: Boolean = false,
    val canQuote: Boolean = false,
    val composerOpen: Boolean = false,
    val overlayOpen: Boolean = false,
) {
    val anyOverlayOpen: Boolean get() = composerOpen || overlayOpen
}

/**
 * Owns the composer editor for one connected account.
 *
 * The shell requests transitions and renders the editor. It does not hold editor fields, draft
 * identity, or reply and quote restoration. The owner validates a target against the current
 * account before it publishes a navigation request. A session replacement invalidates restored
 * targets.
 */
@Stable
class ComposerOwner internal constructor(
    private val editorState: MutableState<ComposerEditorState>,
    internal val uiStrings: UiStrings = UiStrings.Default,
) {
    internal var context: ComposerOwnerContext = ComposerOwnerContext()
    internal var draftsContract: DraftsContract = DraftsContract.Empty

    var drafts by mutableStateOf<List<PostDraft>>(emptyList())
        private set
    var navigation by mutableStateOf<ComposerNavigation?>(null)
        private set

    /** The posts the server holds for later, as of the last load. */
    var scheduled by mutableStateOf<List<ScheduledPost>>(emptyList())
        private set
    var scheduledError by mutableStateOf<String?>(null)
        private set
    var scheduledLoading by mutableStateOf(false)
        private set
    private var scheduledEpoch = 0L
    var closing by mutableStateOf(false)
        private set

    private val quoteOf: EntityId? get() = editor.quoteOf
    private val replyTo: EntityId? get() = editor.replyTo

    /** The full target post chosen in this process. A restored editor falls back to its saved preview. */
    private var target by mutableStateOf<OwnedPost?>(null)

    /** The account and revision this owner last verified restored targets against. */
    private var boundSession: Pair<AccountId?, Long>? = null

    /** Advances on each editor change. An obsolete save callback cannot act on a newer version. */
    private var editorRevision = 0L

    /** Durable session revision captured from the host. */
    internal var sessionRevision = 0L
    private var submission: ComposerSubmission? = null

    /** True while an edit is on its way to the server. */
    private var editSaving by mutableStateOf(false)

    val submitting: Boolean get() = submission != null || editSaving

    val editor: ComposerEditorState get() = editorState.value
    val quoteTarget: OwnedPost? get() = target ?: previewTarget(context.account, editor.quoteOf ?: editor.replyTo, editor.targetPreview, uiStrings.composerQuotedPost())
    val isReply: Boolean get() = replyTo != null

    val hasChanges: Boolean
        get() = editor.entriesChanged || editor.editing?.attachmentsChanged == true ||
            editor.quoteOf?.value != editor.savedQuoteOf ||
            editor.replyTo?.value != editor.savedReplyTo ||
            editor.audience != editor.savedAudience ||
            editor.options != editor.savedOptions

    val canPublish: Boolean
        get() = context.contract.canPublish &&
            (editor.draftId == null || drafts.firstOrNull { it.id == editor.draftId }?.accountId == context.account?.id)

    /** The id of the first entry. Single-post callers address it by default. */
    val firstEntryId: String get() = editor.first.id

    internal var compressChoice: Boolean? = null // The answer to the compression question; null follows the setting.

    /** The id that names this draft's media files. A new editor takes one before its first save. */
    fun mediaDraftId(): String = editor.effectiveDraftId ?: UUID.randomUUID().toString().also { id -> mutateEditor { it.copy(mediaDraftId = id) } }

    fun setText(text: String) { setEntryText(firstEntryId, text) }
    fun setWarning(text: String) { setEntryWarning(firstEntryId, text) }
    fun setWarningEnabled(enabled: Boolean) { setEntryWarningEnabled(firstEntryId, enabled) }
    fun setAudience(audience: Audience) { mutateEditor { it.copy(audience = audience) } }
    fun setLocalOnly(localOnly: Boolean) { mutateEditor { it.copy(options = it.options.copy(localOnly = localOnly)) } }
    fun setQuotePolicy(policy: QuoteApprovalPolicy?) { mutateEditor { it.copy(options = it.options.copy(quotePolicy = policy)) } }

    /** Sets the time to publish, or null to publish now. A thread cannot be scheduled, because its later posts reply to a post that does not exist yet. */
    fun setScheduledAt(epochMillis: Long?) {
        if (epochMillis != null && editor.entries.size > 1) return
        mutateEditor { it.copy(options = it.options.copy(scheduledAtEpochMillis = epochMillis)) }
    }

    fun setEntryText(entryId: String, text: String) = editEntry(entryId) { it.copy(text = text) }
    fun setEntryWarning(entryId: String, text: String) = editEntry(entryId) { it.copy(warning = text) }
    fun setEntryWarningEnabled(entryId: String, enabled: Boolean) = editEntry(entryId) { it.copy(warningEnabled = enabled) }

    /** Sets or clears the poll of an entry. A poll and images exclude each other, so a poll never lands on an entry with images. */
    fun setPoll(entryId: String, poll: PollRequest?) = editEntry(entryId) {
        if (poll != null && it.media.isNotEmpty()) it else it.copy(poll = poll)
    }
    fun addMedia(entryId: String, media: DraftMedia) = editEntry(entryId) { it.copy(media = it.media + media) }
    fun removeMedia(entryId: String, mediaId: String) = editEntry(entryId) { it.withoutMedia(mediaId) }
    fun setAltText(entryId: String, mediaId: String, altText: String?) = editEntry(entryId) { it.withAltText(mediaId, altText) }

    /**
     * Inserts an empty entry after [entryId] and returns its id, or null when [entryId] is unknown.
     * The thread audience, reply target, and quote target stay with the first entry.
     */
    fun addEntryAfter(entryId: String): String? {
        if (editor.entry(entryId) == null) return null
        val added = ComposerEntryState()
        mutateEditor { it.withEntryAfter(entryId, added) }
        return added.id
    }

    /** Removes [entryId] with its media. The first entry stays because it carries the thread's targets. */
    fun removeEntry(entryId: String) {
        if (entryId != firstEntryId) mutateEditor { it.withoutEntry(entryId) }
    }

    private fun editEntry(entryId: String, transform: (ComposerEntryState) -> ComposerEntryState) {
        if (editor.entry(entryId) != null) mutateEditor { it.withEntry(entryId, transform) }
    }

    fun removeTargets() {
        target = null
        mutateEditor { it.copy(quoteOf = null, replyTo = null, targetPreview = null) }
    }

    fun consumeNavigation() { navigation = null }

    private var draftLoadEpoch = 0L

    /** Reloads the draft list. A stale load result cannot replace a newer one. */
    fun refreshDrafts() {
        val epoch = ++draftLoadEpoch
        draftsContract.actions.load(
            onResult = { result -> if (epoch == draftLoadEpoch) drafts = result },
            onError = { message -> if (epoch == draftLoadEpoch) editorState.value = editor.copy(error = message) },
        )
    }

    /** Reloads the scheduled posts. A stale answer cannot replace a newer one. */
    fun refreshScheduled() {
        val epoch = ++scheduledEpoch
        scheduledLoading = true
        context.contract.actions.loadScheduled(
            onResult = { items ->
                if (epoch == scheduledEpoch) {
                    scheduled = items
                    scheduledError = null
                    scheduledLoading = false
                }
            },
            onError = { message ->
                if (epoch == scheduledEpoch) {
                    scheduledError = message.ifBlank { uiStrings.scheduledLoadFailed() }
                    scheduledLoading = false
                }
            },
        )
    }

    fun cancelScheduled(item: ScheduledPost) {
        scheduled = scheduled.filterNot { it.id == item.id }
        context.contract.actions.cancelScheduled(
            item.id,
            onDone = { refreshScheduled() },
            // The list is reloaded either way, so it shows what the server really holds.
            onError = { message -> scheduledError = message.ifBlank { uiStrings.scheduledActionFailed() }; refreshScheduled() },
        )
    }

    fun reschedule(item: ScheduledPost, atEpochMillis: Long) {
        context.contract.actions.rescheduleScheduled(
            item,
            atEpochMillis,
            onDone = { refreshScheduled() },
            onError = { message -> scheduledError = message.ifBlank { uiStrings.scheduledActionFailed() }; refreshScheduled() },
        )
    }

    fun deleteDraft(item: PostDraft) {
        draftsContract.actions.delete(
            draftId = item.id,
            onDone = { refreshDrafts() },
            onError = { message -> editorState.value = editor.copy(error = message) },
        )
    }

    /**
     * Retires reply and quote targets that no longer belong to the connected session. The editor
     * text goes with them: keeping it would silently turn a reply into a new post.
     */
    fun resetTargets() {
        if (editor.hasTargets) mutateEditor { ComposerEditorState() }
        target = null
        submission = null
    }

    /**
     * Verifies restored targets against the connected account and session revision.
     *
     * Targets chosen under the same account and revision stay. The same account under a new
     * revision saves the editor as a draft, which keeps its reply or quote link, then clears it;
     * a failed save keeps the editor on the current revision. Any other account clears the editor.
     * A change after the first bind also fences a pending submission.
     */
    fun bindSession() {
        val accountId = context.account?.id
        val current = accountId to sessionRevision
        val previous = boundSession
        boundSession = current
        if (previous != null && previous != current) submission = null
        val restored = editor
        if (!restored.hasTargets) return
        if (restored.boundAccount == accountId && restored.boundRevision == sessionRevision) return
        if (accountId == null || restored.boundAccount != accountId) {
            resetTargets()
            return
        }
        val submittedRevision = editorRevision
        draftsContract.actions.save(
            draftValue(),
            onResult = {
                if (editorRevision == submittedRevision) {
                    target = null
                    mutateEditor { ComposerEditorState() }
                }
                refreshDrafts()
            },
            onError = {
                editorState.value = editor.copy(boundRevision = sessionRevision, error = uiStrings.composerDraftSaveFailed())
            },
        )
    }

    fun requestNew() {
        if (context.composerOpen) return
        // A closed edit editor never becomes the next post.
        if (editor.editing != null) mutateEditor { ComposerEditorState() }
        val first = drafts.firstOrNull()
        if (!editor.hasContent && editor.savedText.isBlank() && first != null) {
            requestDraft(first)
            return
        }
        // A restored reply or an edited post keeps its audience; only a blank editor takes the default.
        if (!hasChanges && !editor.hasTargets) applyNewPostAudience()
        navigation = ComposerNavigation.New
    }

    fun requestReply(post: OwnedPost) {
        val account = context.account ?: return
        if (post.fetchedBy != account.id || !context.canReply || context.anyOverlayOpen || hasChanges) return
        bindTarget(post, account.id, reply = post.post.actionTargetId ?: post.post.id, quote = null)
        navigation = ComposerNavigation.Reply(post)
    }

    fun requestQuote(post: OwnedPost) {
        val account = context.account ?: return
        if (post.fetchedBy != account.id || !context.canQuote || context.anyOverlayOpen || hasChanges) return
        bindTarget(post, account.id, reply = null, quote = post.post.id)
        navigation = ComposerNavigation.Quote(post)
    }

    /**
     * Opens the editor on [post], which must be the connected account's own post. The editor opens at
     * once and fills in when the raw text arrives, so the surface is not held back by the request.
     */
    fun requestEdit(post: OwnedPost) {
        val account = context.account ?: return
        if (post.fetchedBy != account.id || post.post.author.id != account.id || context.anyOverlayOpen || hasChanges) return
        val target = post.post.actionTargetId ?: post.post.id
        resetForTarget()
        val shown = post.post
        val poll = shown.poll?.toPollRequest()
        val attachments = shown.attachments.mapNotNull { attachment ->
            attachment.id?.let { EditAttachmentState(it, attachment.previewUrl ?: attachment.url, attachment.description) }
        }
        mutateEditor {
            ComposerEditorState(
                audience = shown.audience,
                savedAudience = shown.audience,
                boundAccount = account.id,
                boundRevision = sessionRevision,
                editing = ComposerEdit(
                    target = target,
                    originalAttachments = attachments,
                    attachments = attachments,
                    originalPoll = poll,
                ),
            )
        }
        val epoch = editorRevision
        context.contract.actions.loadEditSource(
            target,
            onResult = { source ->
                if (editorRevision != epoch || editor.editing?.target != target) return@loadEditSource
                val entry = ComposerEntryState(
                    id = editor.first.id,
                    text = source.text,
                    warning = source.contentWarning.orEmpty(),
                    warningEnabled = !source.contentWarning.isNullOrBlank(),
                    poll = poll,
                )
                editorState.value = editor.copy(
                    entries = listOf(entry),
                    savedEntries = listOf(entry.content),
                    editing = editor.editing?.copy(
                        sourceText = source.text,
                        sourceWarning = source.contentWarning.orEmpty(),
                        loading = false,
                    ),
                )
            },
            onError = { message ->
                if (editor.editing?.target == target) {
                    editorState.value = editor.copy(error = message.ifBlank { uiStrings.composerEditLoadFailed() })
                }
            },
        )
        navigation = ComposerNavigation.Edit(post)
    }

    /** Changes the alt text of an image that stays on the post being edited. */
    fun setEditAlt(attachmentId: String, text: String?) {
        val current = editor.editing ?: return
        mutateEditor {
            it.copy(editing = current.copy(attachments = current.attachments.map { a ->
                if (a.id == attachmentId) a.copy(description = text?.takeIf(String::isNotBlank)) else a
            }))
        }
    }

    /** Takes an image off the post being edited. */
    fun removeEditAttachment(attachmentId: String) {
        val current = editor.editing ?: return
        mutateEditor { it.copy(editing = current.copy(attachments = current.attachments.filterNot { a -> a.id == attachmentId })) }
    }

    /** Sends the edit and calls [onSent] once the server has accepted it. */
    private fun publishEdit(onSent: () -> Unit) {
        val current = editor
        val edit = current.editing ?: return
        if (edit.loading || editSaving) return
        val entry = current.first
        val text = context.contract.prepareText(entry.text)
        val warning = entry.effectiveWarning
        val request = EditPostRequest(
            text = text,
            contentWarning = warning.takeIf { it != edit.sourceWarning },
            attachments = edit.attachments.takeIf { edit.attachmentsChanged }?.map { EditAttachment(it.id, it.description) },
            poll = entry.poll?.takeIf { it != edit.originalPoll }?.cleaned(),
        )
        val epoch = editorRevision
        editSaving = true
        context.contract.actions.editPost(
            edit.target,
            request,
            onResult = {
                editSaving = false
                if (editorRevision == epoch) mutateEditor { ComposerEditorState() }
                onSent()
            },
            onError = { message ->
                editSaving = false
                editorState.value = editor.copy(error = message.ifBlank { uiStrings.composerEditSaveFailed() })
            },
        )
    }

    fun requestDraft(item: PostDraft) {
        resetForTarget()
        mutateEditor { draftEditorState(item) }
        target = draftTarget(item)
        navigation = ComposerNavigation.Draft(item.id)
    }

    /** Starts an editor for [post] and binds its reply or quote id to the current account and revision. */
    private fun bindTarget(post: OwnedPost, accountId: AccountId, reply: EntityId?, quote: EntityId?) {
        val audience = replyAudience(post)
        resetForTarget()
        mutateEditor {
            it.copy(
                audience = audience,
                savedAudience = audience,
                error = null,
                replyTo = reply,
                quoteOf = quote,
                targetPreview = post.toQuotePreview(),
                boundAccount = accountId,
                boundRevision = sessionRevision,
            )
        }
        target = post
    }

    private fun applyNewPostAudience() {
        val audience = newPostAudience()
        mutateEditor { it.copy(audience = audience, savedAudience = audience) }
    }

    private fun newPostAudience(): Audience = runCatching {
        PostingVisibilityPolicy.forNewPost(
            context.contract.postPreferences,
            ServerCapabilities(audiences = context.contract.availableAudiences),
        )
    }.getOrDefault(context.contract.postPreferences.defaultAudience)

    /** The editor for a saved draft. Its reply and quote ids stay only for the current server. */
    private fun draftEditorState(item: PostDraft): ComposerEditorState {
        val account = context.account?.id
        val quote = item.quoteOf?.takeIf { it.connection == account?.connection?.origin }
        val reply = item.replyTo?.takeIf { it.connection == account?.connection?.origin }
        val entries = item.toEditorEntries()
        return ComposerEditorState(
            draftId = item.id,
            entries = entries,
            savedEntries = entries.map(ComposerEntryState::content),
            audience = item.audience,
            savedAudience = item.audience,
            options = item.options,
            savedOptions = item.options,
            quoteOf = quote,
            replyTo = reply,
            savedQuoteOf = quote?.value,
            savedReplyTo = reply?.value,
            targetPreview = item.quotePreview,
            boundAccount = account,
            boundRevision = sessionRevision,
        )
    }

    /** Saves the current editor as a draft. Clears the dirty baseline only after success. */
    fun save(onSaved: () -> Unit = {}) {
        // An edit never becomes a draft. Closing it drops the unsaved changes.
        if (editor.editing != null) {
            mutateEditor { ComposerEditorState() }
            onSaved()
            return
        }
        val current = editor
        if (!current.hasContent && current.quoteOf == null && current.replyTo == null) {
            onSaved()
            return
        }
        val submittedRevision = editorRevision
        val submittedContents = current.entries.map(ComposerEntryState::content)
        val submittedAccountId = context.account?.id
        closing = true
        draftsContract.actions.save(
            draftValue(),
            onResult = { item ->
                // A save that finishes after a newer edit must not reset the newer baseline.
                if (editorRevision == submittedRevision && context.account?.id == submittedAccountId) {
                    editorState.value = editor.copy(
                        draftId = item.id,
                        savedEntries = submittedContents,
                        savedAudience = item.audience,
                        savedOptions = item.options,
                        savedQuoteOf = item.quoteOf?.value,
                        savedReplyTo = item.replyTo?.value,
                        error = null,
                    )
                }
                refreshDrafts()
                closing = false
                onSaved()
            },
            onError = {
                editorState.value = editor.copy(error = uiStrings.composerDraftSaveFailed())
                closing = false
            },
        )
    }

    /**
     * Saves the submitted draft, then publishes its thread. A submission reserves its identity
     * before the asynchronous save starts. An obsolete save callback cannot publish. Success
     * clears and deletes only the submitted version. Failure keeps the entries that remain.
     */
    fun publish(onNotice: (PublishNotice) -> Unit = {}, onSent: (replySent: Boolean, quoteSent: Boolean) -> Unit) {
        val account = context.account ?: return
        if (editor.editing != null) {
            publishEdit { onSent(false, false) }
            return
        }
        if (submission != null) return
        val submittedAudience = editor.validatedAudience(context.contract.availableAudiences)
        if (submittedAudience == null) {
            editorState.value = editor.copy(error = uiStrings.composerAudienceUnavailable())
            return
        }
        val submittedReply = replyTo?.takeIf { it.connection == account.id.connection.origin }
        val submittedQuote = quoteOf?.takeIf { it.connection == account.id.connection.origin }
        val publication = editor.withPreparedText(context.contract.prepareText).toThreadPublication(
            draftId = editor.effectiveDraftId ?: UUID.randomUUID().toString(),
            audience = submittedAudience,
            replyTo = submittedReply,
            quoteOf = submittedQuote,
            compress = resolveCompress(),
            accountId = account.id,
            options = editor.options,
        )
        val reserved = ComposerSubmission(
            revision = editorRevision,
            draftId = editor.draftId,
            accountId = account.id,
            sessionRevision = sessionRevision,
        )
        submission = reserved
        val submittedContents = editor.entries.map(ComposerEntryState::content)
        draftsContract.actions.save(
            publication.toPostDraft(account.id).copy(quotePreview = target?.toQuotePreview() ?: editor.targetPreview),
            onResult = { saved ->
                val current = submission
                // Reject an obsolete save callback before it triggers publication.
                if (current == null ||
                    context.account?.id != current.accountId ||
                    sessionRevision != current.sessionRevision
                ) {
                    submission = null
                    return@save
                }
                editorState.value = editor.copy(
                    draftId = saved.id,
                    savedEntries = submittedContents,
                )
                context.contract.actions.publish(
                    publication.copy(draftId = saved.id),
                    publishListener(saved.id, current, submittedReply, submittedQuote, onSent, onNotice),
                )
            },
            onError = {
                submission = null
                editorState.value = editor.copy(error = uiStrings.composerDraftSaveFailedOpen())
            },
        )
    }

    /**
     * Keeps the entries a failed publish did not post and names the failed entry. The reply and
     * quote targets follow the remaining publication: after posted entries they point at the last
     * created post with no quote, so a retry threads only what remains.
     */
    internal fun retainRemaining(failure: ThreadPublishFailure) {
        editorRevision += 1
        editorState.value = retainStates(failure)
    }

    private fun publishListener(
        savedId: String,
        submitted: ComposerSubmission,
        submittedReply: EntityId?,
        submittedQuote: EntityId?,
        onSent: (Boolean, Boolean) -> Unit,
        onNotice: (PublishNotice) -> Unit,
    ): ThreadPublishListener = object : ThreadPublishListener {
        override fun onScheduled(items: List<ScheduledPost>) = onNotice(PublishNotice.Scheduled)

        override fun onAccepted(created: List<OwnedPost>, requests: List<CreatePostRequest>) {
            draftsContract.actions.delete(
                draftId = savedId,
                onDone = { refreshDrafts() },
                onError = { message -> editorState.value = editor.copy(error = message) },
            )
            // A server that took the post and dropped its quote gives no error, so the answer is checked here.
            val quoteKept = submittedQuote == null || created.firstOrNull()?.post?.carriesQuote() != false
            if (!quoteKept) onNotice(PublishNotice.QuoteDropped)
            onSent(submittedReply != null, submittedQuote != null && quoteKept)
            clearAfterPublish(submitted)
        }

        override fun onError(failure: ThreadPublishFailure) {
            submission = null
            retainRemaining(failure)
        }
    }

    private fun mutateEditor(transform: (ComposerEditorState) -> ComposerEditorState) {
        editorRevision += 1
        editorState.value = transform(editorState.value)
    }

    private fun resetForTarget() {
        submission = null
        mutateEditor { ComposerEditorState() }
        target = null
    }

    private fun clearAfterPublish(submitted: ComposerSubmission) {
        // Clear only the submitted editor version. Newer edits typed during publication stay.
        if (editorRevision == submitted.revision && context.account?.id == submitted.accountId) {
            mutateEditor { ComposerEditorState() }
            target = null
        }
        submission = null
    }

    private fun replyAudience(post: OwnedPost): Audience = runCatching {
        PostingVisibilityPolicy.forReply(
            context.contract.postPreferences,
            ServerCapabilities(audiences = context.contract.availableAudiences),
            context = post.post.audience,
        )
    }.getOrDefault(post.post.audience)

    private fun draftValue(): PostDraft {
        val account = context.account
        return PostDraft(
            id = editor.effectiveDraftId ?: UUID.randomUUID().toString(),
            accountId = account?.id,
            text = editor.text,
            audience = editor.audience,
            contentWarning = editor.first.effectiveWarning.takeIf(String::isNotBlank),
            quoteOf = quoteOf?.takeIf { it.connection == account?.id?.connection?.origin },
            replyTo = replyTo?.takeIf { it.connection == account?.id?.connection?.origin },
            quotePreview = target?.toQuotePreview() ?: editor.targetPreview,
            media = editor.first.media,
            poll = editor.first.poll,
            followUps = editor.toFollowUps(),
            options = editor.options,
        )
    }

    private fun draftTarget(item: PostDraft): OwnedPost? =
        item.takeIf { it.accountId == context.account?.id }?.let { previewTarget(context.account, it.quoteOf, it.quotePreview, uiStrings.composerQuotedPost()) }
}

/** The editor identity reserved for one publish. A newer submission supersedes it. */
private data class ComposerSubmission(
    val revision: Long,
    val draftId: String?,
    val accountId: AccountId,
    val sessionRevision: Long,
)
