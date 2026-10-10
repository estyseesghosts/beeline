package me.foxtails.palustris.domain.hashtags

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HashtagQuerySourceTest {
    private class PlainSource : SocialSource {
        override val capabilities = ServerCapabilities()
        val plainSearches = mutableListOf<String>()

        override suspend fun timeline(timeline: Timeline, cursor: String?) = Page<Post>(emptyList())

        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> {
            plainSearches += tag
            return Page(emptyList())
        }
    }

    @Test
    fun aSourceWithoutCombiningRejectsExtras() {
        val source = PlainSource()
        assertEquals(0, source.maxCombinedHashtags)
        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.searchHashtags(HashtagQuery("foto", listOf("fotografia"))) }
        }
        assertEquals("combined hashtag search", error.feature)
    }

    @Test
    fun aQueryWithoutExtrasIsThePlainSearch() = runBlocking {
        val source = PlainSource()
        source.searchHashtags(HashtagQuery("cats"), "cursor")
        assertEquals(listOf("cats"), source.plainSearches)
    }

    @Test
    fun extrasAreValidDistinctAndBounded() {
        val query = HashtagQuery("#Foto", listOf("fotografia", "FOTOGRAFIA", "foto", "#Foto", "bad tag", "", "写真", "x"))
        assertEquals(listOf("fotografia", "写真", "x"), query.extras(10))
        assertEquals(listOf("fotografia", "写真"), query.extras(2))
        assertEquals(emptyList<String>(), query.extras(0))
        assertEquals(emptyList<String>(), HashtagQuery("foto").extras(3))
    }
}
