package me.foxtails.palustris.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileCapability
import me.foxtails.palustris.domain.ProfileCapabilityQuery
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.domain.mergeInto
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import me.foxtails.palustris.ui.posts.PostInteractionMutationOwner
import me.foxtails.palustris.ui.requiresSignIn

@HiltViewModel(assistedFactory = ProfileViewModel.Factory::class)
class ProfileViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    @Assisted private val sessionRevision: Long = 0L,
    @Assisted private val executionAuthority: PostInteractionExecutionAuthority,
    private val uiStrings: UiStrings = UiStrings.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()
    private val timelinePager = ProfileTimelinePager(
        accountId = accountId,
        source = source,
        scope = viewModelScope,
        sessionRevision = sessionRevision,
        onPagesChanged = { pages -> if (!stopped) _state.value = _state.value.copy(pages = pages) },
        uiStrings = uiStrings,
    )

    private var generation = 0L
    private var editorGeneration = 0L
    private var stopped = false
    private var detailJob: Job? = null
    private var relationshipJob: Job? = null
    private var pinnedJob: Job? = null
    private var editJob: Job? = null
    private var capabilityJob: Job? = null
    private val interactionMutations = PostInteractionMutationOwner(
        accountId = accountId,
        source = source,
        sessionRevision = sessionRevision,
        scope = viewModelScope,
        isActionAvailable = { action ->
            when (action) {
                PostAction.Favorite -> source.capabilities.primaryFavourite.status == CapabilityStatus.Supported
                PostAction.Bookmark -> source.capabilities.savedPosts?.status == CapabilityStatus.Supported
                PostAction.React -> source.capabilities.emoji.reactionMutation == CapabilityStatus.Supported
                else -> action in source.capabilities.actions
            }
        },
        favouriteEmoji = { me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI },
        updatePost = { _, target, transform -> updateOwnedPost(target, transform) },
        onFailure = {},
        executionAuthority = executionAuthority,
    )

    init {
        capabilityJob = viewModelScope.launch {
            source.observeCapabilities().collect {
                val target = _state.value.targetId ?: return@collect
                refreshLikedAvailability(target, generation)
            }
        }
    }

    fun open(seed: Account) {
        if (stopped) return
        val current = _state.value
        if (current.targetId == seed.id) {
            _state.value = current.copy(
                seedAccount = seed,
                account = current.account ?: seed,
                likedAvailable = false,
            )
            cancelProfileRequests()
            loadDetails(seed.id, generation)
            loadRelationshipIfNeeded(seed.id, generation)
            loadPinned(seed.id, generation)
            refreshSelected()
            refreshLikedAvailability(seed.id, generation)
            return
        }

        generation += 1
        cancelProfileRequests()
        val targetGeneration = generation
        _state.value = ProfileUiState(
            targetId = seed.id,
            seedAccount = seed,
            account = seed,
            editableSupported = editableSupported(source.capabilities.profile.editable),
            likedAvailable = false,
        )
        timelinePager.setTarget(seed.id, targetGeneration)
        loadDetails(seed.id, targetGeneration)
        loadRelationshipIfNeeded(seed.id, targetGeneration)
        loadPinned(seed.id, targetGeneration)
        refreshSelected()
        refreshLikedAvailability(seed.id, targetGeneration)
    }

    fun refreshDetails() {
        val target = _state.value.targetId ?: return
        loadDetails(target, generation)
    }

    fun refresh() {
        if (stopped) return
        val target = _state.value.targetId ?: return
        val targetGeneration = generation
        loadDetails(target, targetGeneration)
        loadRelationshipIfNeeded(target, targetGeneration)
        loadPinned(target, targetGeneration)
        refreshSelected()
        refreshLikedAvailability(target, targetGeneration)
    }

    fun selectCategory(category: ProfileCategory) {
        if (stopped || _state.value.selectedTab == category) return
        _state.value = _state.value.copy(selectedTab = category)
        if (category.timelineTab != null && _state.value.pages[category.timelineTab] == null) {
            refreshSelected()
        }
    }

    fun refreshSelected() {
        val target = _state.value.targetId ?: return
        val tab = _state.value.selectedTab.timelineTab ?: return
        timelinePager.refresh(target, generation, tab)
    }

    fun loadMoreSelected() {
        val target = _state.value.targetId ?: return
        val tab = _state.value.selectedTab.timelineTab ?: return
        timelinePager.loadMore(target, generation, tab)
    }

    fun openEditor() {
        if (stopped || _state.value.targetId != accountId) return
        editorGeneration += 1
        val start = _state.value.copy(editorDraftBase = null).editorBase
        _state.value = _state.value.copy(
            editorOpen = true,
            editorDraft = start,
            editorDraftBase = start,
            editableLoading = true,
            editableError = null,
            editError = null,
            editorCapabilities = source.capabilities.profile.editable,
        )
        loadEditor(editorGeneration)
    }

    fun updateEditor(draft: EditableProfile) {
        if (stopped || !_state.value.editorOpen) return
        _state.value = _state.value.copy(editorDraft = draft)
    }

    fun refreshEditor() {
        if (stopped || !_state.value.editorOpen) return
        editorGeneration += 1
        loadEditor(editorGeneration)
    }

    fun closeEditor() {
        editorGeneration += 1
        editJob?.cancel()
        _state.value = _state.value.copy(
            editorOpen = false,
            editorDraft = null,
            editorDraftBase = null,
            editableLoading = false,
            savingProfile = false,
            editError = null,
            editableError = null,
        )
    }

    fun saveEditor(patch: EditableProfilePatch, onSuccess: (Account) -> Unit = {}) {
        if (stopped || _state.value.savingProfile || _state.value.targetId != accountId) return
        if (patch.isEmpty) {
            _state.value = _state.value.copy(editorOpen = false, editorDraft = null, editorDraftBase = null, editError = null)
            _state.value.account?.let(onSuccess)
            return
        }
        val targetEditorGeneration = editorGeneration
        _state.value = _state.value.copy(savingProfile = true, editError = null)
        editJob?.cancel()
        editJob = viewModelScope.launch {
            try {
                val updated = source.updateEditableProfile(patch)
                if (isEditorCurrent(targetEditorGeneration, accountId)) {
                    val current = _state.value.account ?: _state.value.seedAccount
                    val merged = current?.let { updated.mergeInto(it) } ?: current
                    _state.value = _state.value.copy(
                        account = merged,
                        seedAccount = merged,
                        editable = updated,
                        savingProfile = false,
                        editError = null,
                        editorOpen = false,
                        editorDraft = null,
                        editorDraftBase = null,
                    )
                    merged?.let(onSuccess)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isEditorCurrent(targetEditorGeneration, accountId)) {
                    _state.value = _state.value.copy(
                        savingProfile = false,
                        editError = uiStrings.sourceError(error),
                    )
                }
            }
        }
    }

    fun react(ownedPost: OwnedPost, choice: EmojiChoice) {
        interactionMutations.react(ownedPost, choice)
    }

    fun applyExternalPost(updated: OwnedPost) {
        if (stopped || updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        val target = updated.effectiveTargetId()
        updateOwnedPost(target) { existing -> existing.mergeExternalActionFields(updated.post) }
    }

    fun applyPublishedPost(request: me.foxtails.palustris.domain.CreatePostRequest) {
        request.replyTo?.let { parent -> incrementKnownReplyCount(parent) }
        request.quoteOf?.let { target -> incrementKnownQuoteCount(target) }
    }

    fun follow() {
        mutateRelationship { source.followProfile(it) }
    }

    fun unfollow() {
        mutateRelationship { source.unfollowProfile(it) }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        timelinePager.stop()
        generation += 1
        cancelProfileRequests()
        interactionMutations.stop()
    }

    private fun loadEditor(targetEditorGeneration: Long) {
        editJob?.cancel()
        editJob = viewModelScope.launch {
            try {
                val editable = source.loadEditableProfile()
                if (isEditorCurrent(targetEditorGeneration, accountId)) {
                    val current = _state.value
                    val untouched = current.editorDraft == current.editorDraftBase
                    _state.value = current.copy(
                        editable = editable,
                        editorDraft = if (untouched) editable else current.editorDraft,
                        editorDraftBase = if (untouched) editable else current.editorDraftBase,
                        editableLoading = false,
                        editableError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isEditorCurrent(targetEditorGeneration, accountId)) {
                    _state.value = _state.value.copy(
                        editableLoading = false,
                        editableError = uiStrings.sourceError(error),
                    )
                }
            }
        }
    }

    private fun updateOwnedPost(postId: EntityId, transform: (me.foxtails.palustris.domain.Post) -> me.foxtails.palustris.domain.Post) {
        _state.value = _state.value.copy(
            pinnedPosts = _state.value.pinnedPosts.map { owned ->
                if (owned.post.id == postId || owned.effectiveTargetId() == postId) owned.copy(post = transform(owned.post)) else owned
            },
            pages = _state.value.pages.mapValues { (_, page) ->
                page.copy(posts = page.posts.map { owned ->
                    if (owned.post.id == postId || owned.effectiveTargetId() == postId) owned.copy(post = transform(owned.post)) else owned
                })
            },
        )
        timelinePager.updatePosts { owned ->
            if (owned.post.id == postId || owned.effectiveTargetId() == postId) {
                owned.copy(post = transform(owned.post))
            } else owned
        }
    }

    private fun incrementKnownReplyCount(target: EntityId) = updateOwnedPost(target) { post ->
        post.copy(interactionCounts = post.interactionCounts.copy(
            replyCount = post.interactionCounts.replyCount.adjustedBy(1),
        ))
    }

    private fun incrementKnownQuoteCount(target: EntityId) = updateOwnedPost(target) { post ->
        post.copy(interactionCounts = post.interactionCounts.copy(
            quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
        ))
    }

    private fun loadDetails(target: AccountId, targetGeneration: Long) {
        detailJob?.cancel()
        if (!profileDetailsAvailable(source.capabilities)) {
            if (isCurrent(targetGeneration, target)) {
                _state.value = _state.value.copy(
                    detailLoading = false,
                    detailError = uiStrings.profileDetailsUnsupported(),
                    staleDetails = true,
                )
            }
            return
        }
        _state.value = _state.value.copy(detailLoading = true, detailError = null)
        detailJob = viewModelScope.launch {
            try {
                val account = source.profile(target)
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        account = account,
                        detailLoading = false,
                        detailError = null,
                        detailNeedsSignIn = false,
                        staleDetails = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        detailLoading = false,
                        detailError = uiStrings.sourceError(error),
                        detailNeedsSignIn = requiresSignIn(error),
                        staleDetails = true,
                    )
                }
            }
        }
    }

    private fun loadRelationshipIfNeeded(target: AccountId, targetGeneration: Long) {
        relationshipJob?.cancel()
        if (target == accountId) {
            _state.value = _state.value.copy(
                relationship = null,
                relationshipSupported = null,
                relationshipLoading = false,
                relationshipMutation = false,
                relationshipError = null,
            )
            return
        }
        if (source.capabilities.profile.relationships == me.foxtails.palustris.domain.CapabilityStatus.Unsupported) {
            _state.value = _state.value.copy(relationshipSupported = false, relationshipLoading = false)
            return
        }
        _state.value = _state.value.copy(
            relationshipLoading = true,
            relationshipError = null,
            relationshipSupported = null,
        )
        relationshipJob = viewModelScope.launch {
            try {
                val relationship = source.profileRelationship(target)
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        relationship = relationship,
                        relationshipSupported = true,
                        relationshipLoading = false,
                        relationshipError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        relationshipLoading = false,
                        relationshipSupported = if (error is SourceError.Unsupported) false else null,
                        relationshipError = uiStrings.sourceError(error),
                    )
                }
            }
        }
    }

    private fun loadPinned(target: AccountId, targetGeneration: Long) {
        pinnedJob?.cancel()
        if (source.capabilities.profile.pinnedPosts == me.foxtails.palustris.domain.CapabilityStatus.Unsupported) {
            _state.value = _state.value.copy(pinnedLoading = false)
            return
        }
        _state.value = _state.value.copy(pinnedLoading = true, pinnedError = null)
        pinnedJob = viewModelScope.launch {
            try {
                val posts = source.pinnedPosts(target)
                    .distinctBy { it.id }
                    .map { OwnedPost(accountId, it, sessionRevision) }
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        pinnedPosts = posts,
                        pinnedLoading = false,
                        pinnedError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        pinnedLoading = false,
                        pinnedError = uiStrings.sourceError(error),
                    )
                }
            }
        }
    }

    private fun mutateRelationship(operation: suspend (AccountId) -> me.foxtails.palustris.domain.ProfileRelationship) {
        if (stopped) return
        val current = _state.value
        val target = current.targetId ?: return
        val relationship = current.relationship ?: return
        if (target == accountId || current.relationshipSupported != true || current.relationshipMutation) return
        val targetGeneration = generation
        _state.value = current.copy(relationshipMutation = true, relationshipError = null)
        relationshipJob?.cancel()
        relationshipJob = viewModelScope.launch {
            try {
                val updated = operation(target)
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        relationship = updated,
                        relationshipMutation = false,
                        relationshipError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        relationship = relationship,
                        relationshipMutation = false,
                        relationshipError = uiStrings.sourceError(error),
                        relationshipSupported = if (error is SourceError.Unsupported) false else current.relationshipSupported,
                    )
                }
            }
        }
    }

    private fun cancelProfileRequests() {
        detailJob?.cancel()
        relationshipJob?.cancel()
        pinnedJob?.cancel()
        editJob?.cancel()
        detailJob = null
        relationshipJob = null
        pinnedJob = null
        editJob = null
        timelinePager.cancel()
    }

    private fun refreshLikedAvailability(target: AccountId, targetGeneration: Long) {
        viewModelScope.launch {
            try {
                val result = source.profileCapability(ProfileCapabilityQuery(target, ProfileCapability.LikedPosts))
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(likedAvailable = result.status == CapabilityStatus.Supported)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A failed capability query cannot prove that the tab is unsupported.
            }
        }
    }

    private fun isCurrent(targetGeneration: Long, target: AccountId): Boolean =
        !stopped && generation == targetGeneration && _state.value.targetId == target

    private fun isEditorCurrent(targetEditorGeneration: Long, target: AccountId): Boolean =
        !stopped && editorGeneration == targetEditorGeneration && _state.value.targetId == target

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource, sessionRevision: Long, executionAuthority: PostInteractionExecutionAuthority): ProfileViewModel
    }
}

private fun editableSupported(capabilities: EditableProfileCapabilities): Boolean =
    capabilities.read != CapabilityStatus.Unsupported || capabilities.update != CapabilityStatus.Unsupported

private fun profileDetailsAvailable(capabilities: ServerCapabilities): Boolean =
    capabilities.profile.details != me.foxtails.palustris.domain.CapabilityStatus.Unsupported
