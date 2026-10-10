package me.foxtails.palustris.ui.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
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
class SearchCombinedHashtagsTest {
    private val connection = Connection("https://search.example", Protocol.MISSKEY)
    private val account = AccountId(connection, "owner")
    private val author = Account(account, "Owner", "@owner@search.example")

    private fun post(id: String) = Post(EntityId(connection.origin, id), author, id, 0L, Audience.Public)

    private class RecordingSource(private val limit: Int) : SocialSource {
        override val capabilities = ServerCapabilities()
        override val maxCombinedHashtags: Int get() = limit
        val queries = mutableListOf<Pair<HashtagQuery, String?>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?) = Page<Post>(emptyList())

        override suspend fun searchHashtags(query: HashtagQuery, cursor: String?): Page<Post> {
            queries += query to cursor
            return Page(emptyList(), if (cursor == null) "next" else null)
        }
    }

    private fun member(name: String, language: String, relation: HashtagRelation, weight: Double) =
        CatalogMember(name, listOf(language), relation, weight)

    private val catalog = HashtagCatalog(
        listOf(
            HashtagGroup(
                "photography",
                listOf(
                    member("photography", "en", HashtagRelation.Head, 11.0),
                    member("foto", "es", HashtagRelation.Synonym, 8.0),
                    member("fotografia", "es", HashtagRelation.Synonym, 7.0),
                    member("写真", "ja", HashtagRelation.Synonym, 6.0),
                    member("fotografie", "de", HashtagRelation.Synonym, 5.0),
                ),
            ),
        ),
    )

    private fun input(enabled: Boolean = true) = HashtagExpansionInput(
        HashtagExpander(catalog),
        enabled,
        HashtagLanguagePolicy.forLanguage(AppLanguage.English),
    )

    private fun owner(scope: kotlinx.coroutines.CoroutineScope, source: SocialSource, input: HashtagExpansionInput) =
        SearchOwner(account, source, 1L, scope, { it }, { input })

    @Test
    fun stateKeepsTheAppliedExtrasAndLoadMoreSendsThem() = runTest {
        val source = RecordingSource(limit = 3)
        val owner = owner(this, source, input())

        owner.search("#foto")
        advanceUntilIdle()
        owner.loadMore()
        advanceUntilIdle()

        // The head leads, then the best member of each other language. The limit trims the rest.
        val expected = listOf("photography", "fotografia", "写真")
        assertEquals(expected, owner.state.value.combinedTags)
        assertEquals(listOf(HashtagQuery("#foto", expected) to null, HashtagQuery("foto", expected) to "next"), source.queries)
    }

    @Test
    fun showOnlySendsNoExtras() = runTest {
        val source = RecordingSource(limit = 10)
        val owner = owner(this, source, input())

        owner.searchWithoutRelated("#foto")
        advanceUntilIdle()

        assertEquals(listOf(HashtagQuery("#foto", emptyList()) to null), source.queries)
        assertTrue(owner.state.value.combinedTags.isEmpty())
    }

    @Test
    fun aSearchWithTheSettingOffSendsNoExtras() = runTest {
        val source = RecordingSource(limit = 10)
        val owner = owner(this, source, input(enabled = false))

        owner.search("#foto")
        advanceUntilIdle()

        assertTrue(source.queries.single().first.alsoMatching.isEmpty())
    }

    @Test
    fun aSourceThatCannotCombineGetsNoExtras() = runTest {
        val source = RecordingSource(limit = 0)
        val owner = owner(this, source, input())

        owner.search("#foto")
        advanceUntilIdle()

        assertTrue(source.queries.single().first.alsoMatching.isEmpty())
    }

    @Test
    fun anAccountSearchNeverExpands() = runTest {
        val source = object : SocialSource {
            override val capabilities = ServerCapabilities()
            override suspend fun timeline(timeline: Timeline, cursor: String?) = Page<Post>(emptyList())
            override suspend fun searchAccounts(query: String) = listOf(author)
        }
        val owner = owner(this, source, input())

        owner.search("@alice")
        advanceUntilIdle()

        assertTrue(owner.state.value.combinedTags.isEmpty())
        assertEquals(listOf(author), owner.state.value.accounts)
    }

    @Test
    fun theNextSearchReadsTheSettingAgain() = runTest {
        val source = RecordingSource(limit = 10)
        var current = input(enabled = false)
        val owner = SearchOwner(account, source, 1L, this, { it }, { current })

        owner.search("#foto")
        advanceUntilIdle()
        assertTrue(owner.state.value.combinedTags.isEmpty())
        current = input(enabled = true)
        owner.search("#foto")
        advanceUntilIdle()

        assertTrue(owner.state.value.combinedTags.isNotEmpty())
    }
}
