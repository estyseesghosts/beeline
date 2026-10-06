package me.foxtails.palustris.ui.profile

import androidx.annotation.VisibleForTesting
import java.util.LinkedHashSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ProfileCapability
import me.foxtails.palustris.domain.ProfileCapabilityQuery
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.requiresSignIn

internal class ProfileTimelinePager(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val sessionRevision: Long,
    private val onPagesChanged: (Map<ProfileTimelineTab, ProfilePageState>) -> Unit,
    private val uiStrings: UiStrings = UiStrings.Default,
) {
    private companion object {
        const val MAX_REQUESTED_CURSORS_PER_TAB = 64
    }

    private val pageJobs = mutableMapOf<ProfileTimelineTab, Job>()
    private val requestedCursors = mutableMapOf<ProfileTimelineTab, LinkedHashSet<String>>()
    private val inFlightCursors = mutableMapOf<ProfileTimelineTab, MutableMap<String, Any>>()
    private var pages = emptyMap<ProfileTimelineTab, ProfilePageState>()
    private var target: AccountId? = null
    private var generation = 0L
    private var stopped = false

    fun setTarget(target: AccountId, generation: Long) {
        cancel()
        this.target = target
        this.generation = generation
        requestedCursors.clear()
        inFlightCursors.clear()
        pages = emptyMap()
        publish()
    }

    fun refresh(target: AccountId, generation: Long, tab: ProfileTimelineTab) {
        if (!isCurrent(target, generation)) return
        pageJobs[tab]?.cancel()
        requestedCursors.remove(tab)
        inFlightCursors.remove(tab)
        val previous = pages[tab] ?: ProfilePageState()
        publish(pages + (tab to previous.copy(
            initialLoading = previous.posts.isEmpty(),
            refreshing = previous.posts.isNotEmpty(),
            loadingMore = false,
            nextCursor = previous.nextCursor,
            error = null,
            needsSignIn = false,
            terminal = false,
            consecutiveEmptyPages = 0,
        )))
        loadPage(target, tab, cursor = null, generation, refreshing = true)
    }

    fun loadMore(target: AccountId, generation: Long, tab: ProfileTimelineTab) {
        if (!isCurrent(target, generation)) return
        val page = pages[tab] ?: return
        val cursor = page.nextCursor ?: return
        if (page.initialLoading || page.refreshing || page.loadingMore || page.terminal || page.needsSignIn) return
        if (pageJobs[tab]?.isActive == true) return
        publish(pages + (tab to page.copy(loadingMore = true, error = null)))
        loadPage(target, tab, cursor, generation, refreshing = false)
    }

    /** Allows tests to exercise cursor admission while the public load-more guard is active. */
    @VisibleForTesting
    internal fun requestPageForTest(
        target: AccountId,
        generation: Long,
        tab: ProfileTimelineTab,
        cursor: String,
    ) {
        loadPage(target, tab, cursor, generation, refreshing = false)
    }

    fun updatePosts(transform: (OwnedPost) -> OwnedPost) {
        publish(pages.mapValues { (_, page) -> page.copy(posts = page.posts.map(transform)) })
    }

    fun cancel() {
        pageJobs.values.forEach(Job::cancel)
        pageJobs.clear()
    }

    fun stop() {
        stopped = true
        cancel()
        generation++
    }

    private fun loadPage(
        target: AccountId,
        tab: ProfileTimelineTab,
        cursor: String?,
        requestGeneration: Long,
        refreshing: Boolean,
    ) {
        val cursorSet = requestedCursors.getOrPut(tab) { LinkedHashSet() }
        val inFlight = inFlightCursors.getOrPut(tab) { mutableMapOf() }
        if (cursor != null && (cursor in inFlight || cursor in cursorSet)) {
            val page = pages[tab] ?: return
            if (isCurrent(target, requestGeneration)) {
                publish(pages + (tab to page.copy(loadingMore = false, terminal = true, nextCursor = null)))
            }
            return
        }
        val requestToken = Any()
        if (cursor != null) {
            cursorSet.add(cursor)
            inFlight[cursor] = requestToken
            pruneRequestedCursors(tab)
        }
        pageJobs[tab]?.cancel()
        val job = scope.launch {
            try {
                if (tab == ProfileTimelineTab.Liked) {
                    when (val status = source.profileCapability(
                        ProfileCapabilityQuery(target, ProfileCapability.LikedPosts),
                    ).status) {
                        CapabilityStatus.Supported -> Unit
                        CapabilityStatus.Unsupported -> {
                            publishPageFailure(target, tab, requestGeneration, SourceError.Unsupported("profile.liked"))
                            return@launch
                        }
                        CapabilityStatus.Denied -> {
                            publishPageFailure(target, tab, requestGeneration, SourceError.AccessDenied("profile.liked"))
                            return@launch
                        }
                        CapabilityStatus.TemporarilyUnavailable -> {
                            publishPageFailure(target, tab, requestGeneration, SourceError.RateLimited)
                            return@launch
                        }
                        CapabilityStatus.Unknown -> {
                            publishCapabilityUnknown(target, tab, requestGeneration)
                            return@launch
                        }
                    }
                } else if (source.capabilities.profile.timelines == CapabilityStatus.Unsupported) {
                    publishPageFailure(target, tab, requestGeneration, SourceError.Unsupported("profile.timeline"))
                    return@launch
                }
                val page = source.profileTimeline(ProfileTimelineQuery(target, tab), cursor)
                if (!isCurrent(target, requestGeneration)) return@launch
                val current = pages[tab] ?: ProfilePageState()
                val owned = page.items.map { OwnedPost(accountId, it, sessionRevision) }
                val merged = (if (refreshing) owned else current.posts + owned).distinctBy { it.post.id }
                val repeatedCursor = page.nextCursor != null &&
                    (page.nextCursor in inFlight || page.nextCursor in cursorSet)
                val nextCursor = page.nextCursor?.takeUnless { repeatedCursor }
                publish(pages + (tab to current.copy(
                    posts = merged,
                    initialLoading = false,
                    refreshing = false,
                    loadingMore = false,
                    nextCursor = nextCursor,
                    error = null,
                    needsSignIn = false,
                    terminal = nextCursor == null,
                    consecutiveEmptyPages = if (page.items.isEmpty()) {
                        if (refreshing) 1 else current.consecutiveEmptyPages + 1
                    } else 0,
                )))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                publishPageFailure(target, tab, requestGeneration, error)
            } finally {
                if (cursor != null && inFlight[cursor] === requestToken) inFlight.remove(cursor)
                if (pageJobs[tab] === kotlinx.coroutines.currentCoroutineContext()[Job]) pageJobs.remove(tab)
                pruneRequestedCursors(tab)
            }
        }
        pageJobs[tab] = job
    }

    /** Keeps cursor history bounded without evicting a request that still owns network work. */
    private fun pruneRequestedCursors(tab: ProfileTimelineTab) {
        val requested = requestedCursors[tab] ?: return
        val inFlight = inFlightCursors[tab].orEmpty()
        val iterator = requested.iterator()
        while (requested.size > MAX_REQUESTED_CURSORS_PER_TAB && iterator.hasNext()) {
            val cursor = iterator.next()
            if (cursor !in inFlight) iterator.remove()
        }
    }

    private fun publishPageFailure(
        target: AccountId,
        tab: ProfileTimelineTab,
        requestGeneration: Long,
        error: Exception,
    ) {
        if (!isCurrent(target, requestGeneration)) return
        val current = pages[tab] ?: ProfilePageState()
        publish(pages + (tab to current.copy(
            initialLoading = false,
            refreshing = false,
            loadingMore = false,
            error = uiStrings.sourceError(error),
            needsSignIn = requiresSignIn(error),
            nextCursor = current.nextCursor,
        )))
    }

    private fun publishCapabilityUnknown(
        target: AccountId,
        tab: ProfileTimelineTab,
        requestGeneration: Long,
    ) {
        if (!isCurrent(target, requestGeneration)) return
        val current = pages[tab] ?: ProfilePageState()
        publish(pages + (tab to current.copy(
            initialLoading = false,
            refreshing = false,
            loadingMore = false,
            error = null,
        )))
    }

    private fun isCurrent(target: AccountId, requestGeneration: Long): Boolean =
        !stopped && this.target == target && generation == requestGeneration

    private fun publish(next: Map<ProfileTimelineTab, ProfilePageState> = pages) {
        pages = next
        onPagesChanged(next)
    }
}
