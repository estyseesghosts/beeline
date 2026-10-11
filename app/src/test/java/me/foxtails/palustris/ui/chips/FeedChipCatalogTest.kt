package me.foxtails.palustris.ui.chips

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.data.preferences.InMemorySavedHashtagRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.FeedChipCapabilities
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.HashtagOrigin
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SavedHashtags
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedChipCatalogTest {
    private val accountId = AccountId(Connection("https://chips.example", Protocol.MASTODON), "me")

    private class FakeSource(
        override val capabilities: ServerCapabilities,
    ) : SocialSource {
        var lists = listOf<FeedSource.UserList>()
        var antennaList = listOf<FeedSource.Antenna>()
        var followed = mutableListOf<String>()
        var failFollow = setOf<String>()
        var failLists = false
        val followCalls = mutableListOf<String>()
        val unfollowCalls = mutableListOf<String>()

        override suspend fun timeline(timeline: Timeline, cursor: String?) = Page<Post>(emptyList())

        override suspend fun userLists(): List<FeedSource.UserList> {
            if (failLists) throw SourceError.Unsupported("lists")
            return lists
        }

        override suspend fun antennas(): List<FeedSource.Antenna> = antennaList

        override suspend fun followedTags(): List<String> = followed.toList()

        override suspend fun followTag(tag: String) {
            followCalls += tag
            if (tag.removePrefix("#") in failFollow) throw SourceError.Unsupported("tags.follow")
            followed += tag.removePrefix("#")
        }

        override suspend fun unfollowTag(tag: String) {
            unfollowCalls += tag
            followed.remove(tag.removePrefix("#"))
        }
    }

    private fun mastodonCaps() = ServerCapabilities(
        feedChips = FeedChipCapabilities(CapabilityStatus.Supported, CapabilityStatus.Supported, CapabilityStatus.Unsupported),
    )

    private fun misskeyCaps() = ServerCapabilities(
        feedChips = FeedChipCapabilities(CapabilityStatus.Supported, CapabilityStatus.Unsupported, CapabilityStatus.Supported),
    )

    private suspend fun InMemorySavedHashtagRepository.snapshot(): List<String> = observe(accountId).first().tags

    private fun catalog(scope: TestScope, source: FakeSource, repository: InMemorySavedHashtagRepository = InMemorySavedHashtagRepository()) =
        FeedChipCatalog(accountId, source, repository, scope)

    @Test
    fun rowOrderIsTimelinesThenListsHashtagsAndAntennas() = runTest {
        val source = FakeSource(misskeyCaps()).apply {
            lists = listOf(FeedSource.UserList("l1", "Artists"))
            antennaList = listOf(FeedSource.Antenna("n1", "Cats"))
        }
        val repository = InMemorySavedHashtagRepository()
        repository.update(accountId) { SavedHashtags(listOf("#photo")) }
        val catalog = catalog(this, source, repository)
        advanceUntilIdle()

        val row = catalog.state.value.chips(setOf(Timeline.Federated, Timeline.Home, Timeline.Social))

        assertEquals(
            listOf(
                FeedSource.Timeline(Timeline.Home),
                FeedSource.Timeline(Timeline.Social),
                FeedSource.Timeline(Timeline.Federated),
                FeedSource.UserList("l1", "Artists"),
                FeedSource.Hashtag("photo", HashtagOrigin.Device),
                FeedSource.Antenna("n1", "Cats"),
            ),
            row,
        )
        catalog.stop()
    }

    @Test
    fun mastodonHashtagsComeFromTheServerAndAntennasNeverAppear() = runTest {
        val source = FakeSource(mastodonCaps()).apply {
            lists = listOf(FeedSource.UserList("1", "One"), FeedSource.UserList("2", "Two"))
            followed = mutableListOf("cats", "art", "dogs")
        }
        val catalog = catalog(this, source)
        advanceUntilIdle()

        val state = catalog.state.value
        assertEquals(listOf("1", "2"), state.lists.map { it.id })
        assertEquals(listOf("cats", "art", "dogs"), state.hashtags.map { it.tag })
        assertTrue(state.hashtags.all { it.origin == HashtagOrigin.Server })
        assertTrue(state.antennas.isEmpty())
        assertTrue(state.loaded)
        catalog.stop()
    }

    @Test
    fun aFailedListReadKeepsTheOtherKinds() = runTest {
        val source = FakeSource(mastodonCaps()).apply {
            failLists = true
            followed = mutableListOf("cats")
        }
        val catalog = catalog(this, source)
        advanceUntilIdle()

        assertTrue(catalog.state.value.lists.isEmpty())
        assertEquals(listOf("cats"), catalog.state.value.hashtags.map { it.tag })
        catalog.stop()
    }

    @Test
    fun addingOnMastodonFollowsOnTheServerAndOffersTheChip() = runTest {
        val source = FakeSource(mastodonCaps())
        val catalog = catalog(this, source)
        advanceUntilIdle()
        var added: FeedSource.Hashtag? = null

        catalog.addHashtag("#Cats") { added = it }
        advanceUntilIdle()

        assertEquals(listOf("Cats"), source.followCalls)
        assertEquals(FeedSource.Hashtag("Cats", HashtagOrigin.Server), added)
        assertEquals(listOf("Cats"), catalog.state.value.hashtags.map { it.tag })
        catalog.stop()
    }

    @Test
    fun addingOnMisskeySavesOnTheDeviceAndNeverCallsTheServer() = runTest {
        val source = FakeSource(misskeyCaps())
        val repository = InMemorySavedHashtagRepository()
        val catalog = catalog(this, source, repository)
        advanceUntilIdle()

        catalog.addHashtag("photo")
        advanceUntilIdle()

        assertTrue(source.followCalls.isEmpty())
        assertEquals(1, repository.snapshot().size)
        assertEquals(listOf("photo"), catalog.state.value.hashtags.map { it.tag })
        assertTrue(catalog.state.value.hashtags.all { it.origin == HashtagOrigin.Device })
        catalog.stop()
    }

    @Test
    fun aDuplicateTagSelectsTheExistingChipWithoutAWrite() = runTest {
        val source = FakeSource(mastodonCaps()).apply { followed = mutableListOf("cats") }
        val catalog = catalog(this, source)
        advanceUntilIdle()
        var added: FeedSource.Hashtag? = null

        catalog.addHashtag("#CATS") { added = it }
        advanceUntilIdle()

        assertTrue(source.followCalls.isEmpty())
        assertEquals("cats", added?.tag)
        catalog.stop()
    }

    @Test
    fun anInvalidTagSetsTheErrorAndClearErrorResetsIt() = runTest {
        val catalog = catalog(this, FakeSource(misskeyCaps()))
        advanceUntilIdle()

        catalog.addHashtag("not a tag!")
        assertEquals(FeedChipError.InvalidHashtag, catalog.state.value.error)
        catalog.clearError()
        assertNull(catalog.state.value.error)
        catalog.stop()
    }

    @Test
    fun removingOnMastodonUnfollowsAndOnMisskeyDeletesLocally() = runTest {
        val mastodon = FakeSource(mastodonCaps()).apply { followed = mutableListOf("cats", "art") }
        val mastodonCatalog = catalog(this, mastodon)
        advanceUntilIdle()
        var removed = false
        mastodonCatalog.removeHashtag("cats") { removed = true }
        advanceUntilIdle()
        assertTrue(removed)
        assertEquals(listOf("cats"), mastodon.unfollowCalls)
        assertEquals(listOf("art"), mastodonCatalog.state.value.hashtags.map { it.tag })
        mastodonCatalog.stop()

        val misskey = FakeSource(misskeyCaps())
        val repository = InMemorySavedHashtagRepository()
        repository.update(accountId) { SavedHashtags(listOf("#photo", "#art")) }
        val misskeyCatalog = catalog(this, misskey, repository)
        advanceUntilIdle()
        misskeyCatalog.removeHashtag("photo")
        advanceUntilIdle()
        assertTrue(misskey.unfollowCalls.isEmpty())
        assertEquals(listOf("art"), misskeyCatalog.state.value.hashtags.map { it.tag })
        misskeyCatalog.stop()
    }

    @Test
    fun mastodonFollowsLocallySavedTagsAndEmptiesTheLocalList() = runTest {
        val source = FakeSource(mastodonCaps())
        val repository = InMemorySavedHashtagRepository()
        repository.update(accountId) { SavedHashtags(listOf("#photo", "#art")) }
        val catalog = catalog(this, source, repository)
        advanceUntilIdle()

        assertEquals(listOf("photo", "art"), source.followCalls)
        assertTrue(repository.snapshot().isEmpty())
        assertEquals(listOf("photo", "art"), catalog.state.value.hashtags.map { it.tag })
        catalog.stop()
    }

    @Test
    fun aTagThatFailsToFollowStaysSavedAndIsTriedNextSession() = runTest {
        val source = FakeSource(mastodonCaps()).apply { failFollow = setOf("art") }
        val repository = InMemorySavedHashtagRepository()
        repository.update(accountId) { SavedHashtags(listOf("#photo", "#art")) }
        val first = catalog(this, source, repository)
        advanceUntilIdle()
        first.stop()

        assertEquals(listOf("art"), repository.snapshot())
        assertEquals(listOf("photo"), source.followed)

        source.failFollow = emptySet()
        val second = catalog(this, source, repository)
        advanceUntilIdle()

        assertTrue(repository.snapshot().isEmpty())
        assertEquals(listOf("photo", "art"), source.followed)
        second.stop()
    }

    @Test
    fun misskeySavedTagsSurviveAndAreNeverFollowed() = runTest {
        val source = FakeSource(misskeyCaps())
        val repository = InMemorySavedHashtagRepository()
        repository.update(accountId) { SavedHashtags(listOf("#photo")) }
        val catalog = catalog(this, source, repository)
        advanceUntilIdle()

        assertTrue(source.followCalls.isEmpty())
        // The old value with its mark loads and is held without it.
        assertEquals(listOf("photo"), repository.snapshot())
        catalog.stop()
    }

    @Test
    fun addingWithOrWithoutTheMarkGivesTheSameChipOnBothProtocols() = runTest {
        for (caps in listOf(misskeyCaps(), mastodonCaps())) {
            val source = FakeSource(caps)
            val repository = InMemorySavedHashtagRepository()
            val catalog = catalog(this, source, repository)
            advanceUntilIdle()

            var added: FeedSource.Hashtag? = null
            catalog.addHashtag("#tag") { added = it }
            advanceUntilIdle()
            catalog.addHashtag("tag")
            advanceUntilIdle()

            assertEquals("tag", added?.tag)
            assertEquals(listOf("tag"), catalog.state.value.hashtags.map { it.tag })
            assertEquals("#tag", hashtagChipLabel(catalog.state.value.hashtags.single().tag))
            assertTrue(repository.snapshot().all { '#' !in it })
            assertTrue(source.followCalls.all { '#' !in it })
            catalog.stop()
        }
    }

    @Test
    fun hashtagChipLabelShowsOneMarkForStoredTagWithOrWithoutIt() {
        assertEquals("#tag", hashtagChipLabel("tag"))
        assertEquals("#tag", hashtagChipLabel("#tag"))
    }

    @Test
    fun offersReportsWhetherASelectedChipStillExists() = runTest {
        val source = FakeSource(mastodonCaps()).apply { followed = mutableListOf("cats") }
        val catalog = catalog(this, source)
        advanceUntilIdle()
        val cats = FeedSource.Hashtag("Cats", HashtagOrigin.Server)

        assertTrue(catalog.state.value.offers(cats))
        catalog.removeHashtag("cats")
        advanceUntilIdle()

        assertTrue(!catalog.state.value.offers(cats))
        assertTrue(catalog.state.value.offers(FeedSource.Timeline(Timeline.Home)))
        catalog.stop()
    }
}
