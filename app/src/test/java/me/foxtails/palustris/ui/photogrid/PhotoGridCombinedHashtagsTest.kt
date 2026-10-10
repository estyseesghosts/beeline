package me.foxtails.palustris.ui.photogrid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.hashtags.CatalogMember
import me.foxtails.palustris.domain.hashtags.HashtagCatalog
import me.foxtails.palustris.domain.hashtags.HashtagExpander
import me.foxtails.palustris.domain.hashtags.HashtagExpansionInput
import me.foxtails.palustris.domain.hashtags.HashtagGroup
import me.foxtails.palustris.domain.hashtags.HashtagLanguagePolicy
import me.foxtails.palustris.domain.hashtags.HashtagQuery
import me.foxtails.palustris.domain.hashtags.HashtagRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoGridCombinedHashtagsTest {
    private val account = AccountId(Connection("https://grid.example", Protocol.MISSKEY), "owner")

    private class RecordingSource : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        override val maxCombinedHashtags: Int get() = 3
        val queries = mutableListOf<Pair<HashtagQuery, String?>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?) = Page<Post>(emptyList())

        override suspend fun searchHashtags(query: HashtagQuery, cursor: String?): Page<Post> {
            queries += query to cursor
            return Page(emptyList(), if (cursor == null) "next" else null)
        }
    }

    private fun input(enabled: Boolean): HashtagExpansionInput {
        fun member(name: String, language: String, relation: HashtagRelation, weight: Double) =
            CatalogMember(name, listOf(language), relation, weight)
        val catalog = HashtagCatalog(
            listOf(
                HashtagGroup(
                    "photography",
                    listOf(
                        member("photography", "en", HashtagRelation.Head, 11.0),
                        member("foto", "es", HashtagRelation.Synonym, 8.0),
                        member("fotografia", "es", HashtagRelation.Synonym, 7.0),
                    ),
                ),
            ),
        )
        return HashtagExpansionInput(
            HashtagExpander(catalog),
            enabled,
            HashtagLanguagePolicy.forLanguage(AppLanguage.English),
        )
    }

    private fun owner(source: SocialSource, input: HashtagExpansionInput) = PhotoGridOwner(
        accountId = account,
        source = source,
        sessionRevision = 1L,
        scope = CoroutineScope(Dispatchers.Main),
        preferencesRepository = InMemoryPhotoGridPreferencesRepository(),
        applyFavouritePreference = { it },
        hashtagInput = { input },
    )

    @Test
    fun hashtagFeedSendsTheExtrasAndSavesOnlyTheTypedHashtag() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = RecordingSource()
        val owner = owner(source, input(enabled = true))
        try {
            owner.addHashtag("#foto")
            advanceUntilIdle()
            owner.loadMore()
            advanceUntilIdle()

            val expected = listOf("photography", "fotografia")
            assertEquals(
                listOf(HashtagQuery("#foto", expected) to null, HashtagQuery("#foto", expected) to "next"),
                source.queries,
            )
            assertEquals(listOf("#foto"), owner.state.value.savedHashtags)
            assertEquals(expected, owner.state.value.combinedTags)
        } finally {
            owner.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun hashtagFeedWithTheSettingOffSendsNoExtras() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = RecordingSource()
        val owner = owner(source, input(enabled = false))
        try {
            owner.addHashtag("#foto")
            advanceUntilIdle()

            assertTrue(source.queries.single().first.alsoMatching.isEmpty())
            assertTrue(owner.state.value.combinedTags.isEmpty())
        } finally {
            owner.release()
            Dispatchers.resetMain()
        }
    }
}
