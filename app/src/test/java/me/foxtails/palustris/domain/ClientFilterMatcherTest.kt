package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientFilterMatcherTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val viewer = AccountId(connection, "viewer")
    private val author = Account(AccountId(connection, "author"), "Author", "@author@example.org")

    private fun post(
        text: String,
        contentWarning: String? = null,
        by: Account = author,
        poll: Poll? = null,
        attachments: List<Attachment> = emptyList(),
        quote: Post? = null,
    ) = Post(EntityId(connection.origin, "p-$text"), by, text, 0, Audience.Public, attachments = attachments, contentWarning = contentWarning, quote = quote, poll = poll)

    private fun filter(
        vararg words: String,
        wholeWord: Boolean = false,
        action: PostFilterAction = PostFilterAction.Collapse,
        contexts: Set<FilterContext> = FilterContext.entries.toSet(),
        expiresAt: Long? = null,
        title: String = words.first(),
    ) = ContentFilter(title, title, words.map { FilterPattern.Words(listOf(it), wholeWord) }, action, contexts, expiresAt)

    private fun matcher(vararg filters: ContentFilter, now: Long = 1_000L) = ClientFilterMatcher(viewer, filters.toList()) { now }

    @Test
    fun keywordMatchesWithoutRegardToCase() {
        val result = matcher(filter("Finale")).matches(post("the FINALE was great"), FilterContext.Home)

        assertEquals(listOf("Finale" to PostFilterAction.Collapse), result)
    }

    @Test
    fun wholeWordNeedsBoundariesAtWordCharacterEnds() {
        val cat = matcher(filter("cat", wholeWord = true))

        assertTrue(cat.matches(post("concatenate this"), FilterContext.Home).isEmpty())
        assertEquals(1, cat.matches(post("my cat."), FilterContext.Home).size)
        // A leading # is not a word character, so no boundary applies before it, as in Mastodon.
        assertEquals(1, matcher(filter("#tag", wholeWord = true)).matches(post("x#tag y"), FilterContext.Home).size)
    }

    @Test
    fun wholeWordFoldsCaseOutsideAscii() {
        assertEquals(1, matcher(filter("ärger", wholeWord = true)).matches(post("So ein ÄRGER heute"), FilterContext.Home).size)
        assertTrue(matcher(filter("ärger", wholeWord = true)).matches(post("Verärgert"), FilterContext.Home).isEmpty())
    }

    @Test
    fun filterAppliesOnlyInItsContexts() {
        val homeOnly = matcher(filter("finale", contexts = setOf(FilterContext.Home)))

        assertEquals(1, homeOnly.matches(post("finale"), FilterContext.Home).size)
        assertTrue(homeOnly.matches(post("finale"), FilterContext.Thread).isEmpty())
    }

    @Test
    fun expiredFilterNeverMatches() {
        assertTrue(matcher(filter("finale", expiresAt = 1_000L), now = 1_000L).matches(post("finale"), FilterContext.Home).isEmpty())
        assertEquals(1, matcher(filter("finale", expiresAt = 1_001L), now = 1_000L).matches(post("finale"), FilterContext.Home).size)
    }

    @Test
    fun ownPostsNeverMatch() {
        val own = Account(viewer, "Me", "@viewer@example.org")

        assertTrue(matcher(filter("finale")).matches(post("finale", by = own), FilterContext.Home).isEmpty())
    }

    @Test
    fun matchingReadsWarningPollMediaAndQuote() {
        val m = matcher(filter("finale"))

        assertEquals(1, m.matches(post("body", contentWarning = "finale spoilers"), FilterContext.Home).size)
        assertEquals(1, m.matches(post("body", poll = Poll("poll", listOf(PollOption("finale", 0)))), FilterContext.Home).size)
        assertEquals(1, m.matches(post("body", attachments = listOf(Attachment("https://example.org/a.png", description = "finale still"))), FilterContext.Home).size)
        assertEquals(1, m.matches(post("body", quote = post("the finale")), FilterContext.Home).size)
        assertTrue(m.matches(post("body"), FilterContext.Home).isEmpty())
    }

    @Test
    fun misskeyWordsMatchCapitalizationAndNeedEveryWord() {
        val entry = ContentFilter("s", "spoiler season", listOf(FilterPattern.Words(listOf("spoiler", "season"), ignoreCase = false)), PostFilterAction.Collapse)
        val m = matcher(entry)

        assertEquals(1, m.matches(post("a season of spoiler talk"), FilterContext.Thread).size)
        assertTrue(m.matches(post("a Season of Spoiler talk"), FilterContext.Thread).isEmpty())
        assertTrue(m.matches(post("spoiler only"), FilterContext.Thread).isEmpty())
    }

    @Test
    fun expressionsFollowJavaScriptFlags() {
        fun expression(source: String, flags: String) = ContentFilter("e", "e", listOf(FilterPattern.Expression(source, flags)), PostFilterAction.Remove)

        assertEquals(1, matcher(expression("sp(oi)+ler", "i")).matches(post("SPOIOILER"), FilterContext.Home).size)
        assertTrue(matcher(expression("sp(oi)+ler", "")).matches(post("SPOILER"), FilterContext.Home).isEmpty())
        assertEquals(1, matcher(expression("été", "i")).matches(post("ÉTÉ"), FilterContext.Home).size)
        // A bad flag or bad syntax never matches.
        assertTrue(matcher(expression("x", "q")).matches(post("x"), FilterContext.Home).isEmpty())
        assertTrue(matcher(expression("(", "")).matches(post("("), FilterContext.Home).isEmpty())
    }

    @Test
    fun storedExpressionsRoundTripAndLiteralsEscape() {
        assertEquals(FilterPattern.Expression("a/b", "gi"), FilterExpressions.parse("/a/b/gi"))
        assertNull(FilterExpressions.parse("plain"))
        assertEquals("/a/b/gi", FilterExpressions.format(FilterPattern.Expression("a/b", "gi")))
        val escaped = FilterExpressions.escapeLiteral("1+1=2? (yes) a/b")
        assertEquals("1+1=2? (yes) a/b", FilterExpressions.literalOrNull(escaped))
        assertTrue(FilterExpressions.compile(escaped, "i")!!.containsMatchIn("so 1+1=2? (YES) a/b"))
        assertNull(FilterExpressions.literalOrNull("sp(oi)+ler"))
    }

    @Test
    fun serverMatchedSetsNeedNoClientMatcher() {
        val filters = listOf(filter("finale"))

        assertSame(ClientFilterMatcher.Empty, ClientFilterMatcher.forSet(viewer, ContentFilterSet(ContentFilterKind.Titled, filters)))
        assertSame(ClientFilterMatcher.Empty, ClientFilterMatcher.forSet(viewer, ContentFilterSet(ContentFilterKind.Phrase)))
        assertEquals(1, ClientFilterMatcher.forSet(viewer, ContentFilterSet(ContentFilterKind.Phrase, filters)).matches(post("finale"), FilterContext.Home).size)
    }

    @Test
    fun policyJoinsServerAndClientMatchesAndRemoveWins() {
        val client = matcher(filter("finale", action = PostFilterAction.Remove, title = "Finale"))
        val serverMatched = post("finale").copy(serverFilters = listOf(ServerFilterMatch("TV", PostFilterAction.Collapse)))

        assertEquals(PostFilterVerdict("Finale", PostFilterAction.Remove), PostFilterPolicy.evaluate(serverMatched, FilterContext.Home, client))
        assertEquals(PostFilterVerdict("TV", PostFilterAction.Collapse), PostFilterPolicy.evaluate(serverMatched, FilterContext.Home))
        assertNull(PostFilterPolicy.evaluate(post("nothing"), FilterContext.Home, client))
    }
}
