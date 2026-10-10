package me.foxtails.palustris.domain.hashtags

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HashtagSuggestionServiceTest {
    private fun member(name: String, language: String, weight: Double, relation: HashtagRelation = HashtagRelation.Synonym) =
        CatalogMember(name, listOf(language), relation, weight)

    private val catalog = HashtagCatalog(
        listOf(
            HashtagGroup(
                "photography",
                listOf(
                    member("photography", "en", 11.0, HashtagRelation.Head),
                    member("photo", "en", 9.0),
                    member("photos", "en", 6.0),
                    member("foto", "es", 8.0),
                    member("fotografia", "es", 7.0),
                    member("fotos", "de", 5.0),
                ),
            ),
        ),
    )

    private val english = HashtagLanguagePolicy.forLanguage(AppLanguage.English)

    private class Server(var answer: suspend (String) -> List<HashtagSuggestion> = { emptyList() }) {
        val prefixes = mutableListOf<String>()
        val limits = mutableListOf<Int>()

        suspend fun fetch(prefix: String, limit: Int): List<HashtagSuggestion> {
            prefixes += prefix
            limits += limit
            return answer(prefix)
        }
    }

    private fun service(
        server: Server,
        policy: HashtagLanguagePolicy = english,
        clock: () -> Long = { 0L },
    ) = HashtagSuggestionService(catalog, "account-a", { policy }, server::fetch, clock, debounceMillis = 250)

    @Test
    fun duplicatesCollapseByIdentityAndKeepTheEntryWithAWeight() = runTest {
        val server = Server { listOf(HashtagSuggestion("Photo", null), HashtagSuggestion("PHOTOS", 1.0)) }

        val result = service(server).suggest("#pho", 8)

        assertEquals(1, result.count { it.name.equals("photo", ignoreCase = true) })
        assertEquals(1, result.count { it.name.equals("photos", ignoreCase = true) })
        assertTrue(result.all { it.weight != null })
        assertEquals(9.0, result.single { it.name.equals("photo", ignoreCase = true) }.weight!!, 0.0)
    }

    @Test
    fun theExactMatchComesFirstThenWeightThenServerOrder() = runTest {
        val server = Server { listOf(HashtagSuggestion("photoz", null), HashtagSuggestion("photoa", null)) }

        val result = service(server).suggest("photos", 8)
        assertEquals("photos", result.first().name)

        val ordered = service(server).suggest("pho", 8).map { it.name }
        assertEquals(listOf("photography", "photo", "photos", "photoz", "photoa"), ordered)
    }

    @Test
    fun aCatalogMemberInAnotherLanguageIsHiddenUnlessItIsTheExactMatch() = runTest {
        val server = Server()
        val service = service(server)

        assertEquals(listOf("photography", "photo", "photos"), service.suggest("pho", 8).map { it.name })
        assertEquals(emptyList<String>(), service.suggest("fot", 8).map { it.name })
        assertEquals(listOf("foto"), service.suggest("foto", 8).map { it.name })
    }

    @Test
    fun spanishDisplayShowsSpanishMembers() = runTest {
        val spanish = HashtagLanguagePolicy.forLanguage(AppLanguage.Spanish)
        assertEquals(listOf("foto", "fotografia"), service(Server(), spanish).suggest("fot", 8).map { it.name })
    }

    @Test
    fun anUnknownCyrillicHashtagIsHiddenForEnglishAndPassesForRussian() = runTest {
        val server = Server { listOf(HashtagSuggestion("пример", null)) }
        val russian = HashtagLanguagePolicy.forLanguage(AppLanguage.Russian)

        assertEquals(emptyList<String>(), service(server).suggest("при", 8).map { it.name })
        assertEquals(listOf("пример"), service(server, russian).suggest("при", 8).map { it.name })
    }

    @Test
    fun aServerFailureStillReturnsCatalogMatches() = runTest {
        val server = Server { throw IOException("offline") }

        val result = service(server).suggest("pho", 8)

        assertEquals(listOf("photography", "photo", "photos"), result.map { it.name })
    }

    @Test
    fun aRepeatedPrefixInsideFiveMinutesMakesOneRequest() = runTest {
        var now = 0L
        val server = Server { listOf(HashtagSuggestion("phototype", null)) }
        val service = service(server, clock = { now })

        service.suggest("pho", 8)
        now += 4L * 60 * 1_000_000_000
        service.suggest("#PHO", 5)
        assertEquals(listOf("pho"), server.prefixes)

        now += 2L * 60 * 1_000_000_000
        service.suggest("pho", 8)
        assertEquals(listOf("pho", "pho"), server.prefixes)
    }

    @Test
    fun failedRequestsAreNotCached() = runTest {
        var fail = true
        val server = Server { if (fail) throw IOException("offline") else listOf(HashtagSuggestion("phototype", null)) }
        val service = service(server)

        service.suggest("pho", 8)
        fail = false

        assertTrue(service.suggest("pho", 8).any { it.name == "phototype" })
    }

    @Test
    fun theCacheKeepsFiftyEntries() = runTest {
        val server = Server()
        val service = service(server)

        (0 until 51).forEach { service.suggest("p$it", 8) }
        service.suggest("p50", 8)
        service.suggest("p0", 8)

        assertEquals(52, server.prefixes.size)
        assertEquals("p0", server.prefixes.last())
    }

    @Test
    fun onlyTheFragmentReachesTheServer() = runTest {
        val server = Server()
        val service = service(server)

        service.suggest("  #Pho  ", 5)
        service.suggest("two words", 5)
        service.suggest("#", 5)
        service.suggest("", 5)

        assertEquals(listOf("pho"), server.prefixes)
        assertEquals(listOf(8), server.limits)
    }

    @Test
    fun aNewPrefixCancelsThePreviousRequestAndTheDebounceSkipsFastTyping() = runTest {
        val gate = CompletableDeferred<List<HashtagSuggestion>>()
        var cancelled = 0
        val server = Server { prefix ->
            if (prefix == "pho") {
                try {
                    gate.await()
                } catch (e: CancellationException) {
                    cancelled++
                    throw e
                }
            } else {
                emptyList()
            }
        }
        val typed = MutableSharedFlow<String>()
        val results = mutableListOf<List<String>>()
        val job = service(server).suggestions(typed, 5).onEach { results += it.map { s -> s.name } }.launchIn(backgroundScope)
        runCurrent()

        typed.emit("p")
        typed.emit("ph")
        typed.emit("pho")
        advanceTimeBy(251)
        runCurrent()
        assertEquals(listOf("pho"), server.prefixes)

        typed.emit("phot")
        advanceTimeBy(251)
        runCurrent()
        assertEquals(1, cancelled)
        assertEquals(listOf("pho", "phot"), server.prefixes)
        assertEquals(listOf("photography", "photo", "photos").filter { it.startsWith("phot") }, results.last())
        job.cancel()
    }
}
