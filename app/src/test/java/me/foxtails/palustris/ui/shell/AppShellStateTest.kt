package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.photogrid.PhotoGridFeedState
import me.foxtails.palustris.ui.profile.ProfileUiState
import me.foxtails.palustris.ui.saved.SavedPostsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppShellStateTest {
    private val account = AppShellFixtures.account("owner")
    private val otherAccount = AppShellFixtures.account("other")

    private fun selected(
        id: String = "post",
        owner: Account = account,
        revision: Long = 4L,
        actionTargetId: EntityId? = null,
    ): OwnedPost = AppShellFixtures.owned(
        account = owner,
        post = AppShellFixtures.post(id, owner, text = "selected", actionTargetId = actionTargetId),
        sessionRevision = revision,
    )

    private fun candidate(
        id: String = "post",
        owner: Account = account,
        revision: Long = 4L,
    ): OwnedPost = AppShellFixtures.owned(
        account = owner,
        post = AppShellFixtures.post(id, owner, text = "candidate"),
        sessionRevision = revision,
    )

    private fun resolve(
        selected: OwnedPost?,
        origin: LargePostOrigin,
        home: List<OwnedPost> = emptyList(),
        photoGrid: List<OwnedPost> = emptyList(),
        saved: List<OwnedPost> = emptyList(),
        profile: List<OwnedPost> = emptyList(),
    ): OwnedPost? = resolveSelectedPost(
        selected = selected,
        origin = origin,
        home = HomeContract(HomeFeedUiState(ownedPosts = home), HomeContract.Empty.actions),
        photoGrid = PhotoGridContract(PhotoGridFeedState(posts = photoGrid), PhotoGridContract.Empty.actions),
        bookmarks = BookmarksContract(
            state = SavedPostsUiState(posts = saved),
            actions = BookmarksContract.Empty.actions,
        ),
        profile = ProfileContract(ProfileUiState(pinnedPosts = profile), ProfileContract.Empty.actions),
    )

    @Test
    fun noSelectedPostReturnsNull() {
        assertNull(resolve(null, LargePostOrigin.Home, home = listOf(candidate())))
    }

    @Test
    fun matchingHomeCandidateIsPreferredForHomeOrigin() {
        val preferred = candidate()
        val fallback = candidate()

        assertSame(preferred, resolve(selected(), LargePostOrigin.Home, home = listOf(preferred), photoGrid = listOf(fallback)))
    }

    @Test
    fun matchingPhotoGridCandidateIsPreferredForPhotoGridOrigin() {
        val preferred = candidate()
        val fallback = candidate()

        assertSame(preferred, resolve(selected(), LargePostOrigin.PhotoGrid, home = listOf(fallback), photoGrid = listOf(preferred)))
    }

    @Test
    fun matchingSavedCandidateIsPreferredForSavedOrigin() {
        val preferred = candidate()
        val fallback = candidate()

        assertSame(preferred, resolve(selected(), LargePostOrigin.Saved, home = listOf(fallback), saved = listOf(preferred)))
    }

    @Test
    fun matchingProfileCandidateIsPreferredForProfileOrigin() {
        val preferred = candidate()
        val fallback = candidate()

        assertSame(preferred, resolve(selected(), LargePostOrigin.Profile, home = listOf(fallback), profile = listOf(preferred)))
    }

    @Test
    fun unrelatedCollectionsAreFallbackSources() {
        val fallback = candidate()

        assertSame(fallback, resolve(selected(), LargePostOrigin.Home, profile = listOf(fallback)))
    }

    @Test
    fun samePostIdWithDifferentAccountIsRejected() {
        val foreign = candidate(owner = otherAccount)
        val selected = selected()

        assertSame(selected, resolve(selected, LargePostOrigin.Home, home = listOf(foreign)))
    }

    @Test
    fun samePostIdWithDifferentSessionRevisionIsRejected() {
        val stale = candidate(revision = 3L)
        val selected = selected()

        assertSame(selected, resolve(selected, LargePostOrigin.Home, home = listOf(stale)))
    }

    @Test
    fun originalSelectedSnapshotIsReturnedWhenNoCurrentCandidateMatches() {
        val selected = selected()

        assertSame(selected, resolve(selected, LargePostOrigin.Home, home = listOf(candidate(id = "other"))))
    }

    @Test
    fun effectiveTargetIdsDoNotReplacePostIdOwnershipOrRevisionMatching() {
        val targetId = EntityId(AppShellFixtures.connection.origin, "target")
        val selected = selected(actionTargetId = targetId)
        val target = candidate(id = "target")
        val matchingWrapper = candidate()

        assertSame(selected, resolve(selected, LargePostOrigin.Home, home = listOf(target)))
        assertSame(matchingWrapper, resolve(selected, LargePostOrigin.Home, home = listOf(matchingWrapper)))
        assertEquals(selected.post.id, matchingWrapper.post.id)
    }
}
