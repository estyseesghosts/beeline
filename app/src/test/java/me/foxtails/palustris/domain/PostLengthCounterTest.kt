package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostLengthCounterTest {
    private val mastodon = PostingCapabilities(lengthRule = PostLengthRule.MastodonCombined)
    private val misskey = PostingCapabilities(lengthRule = PostLengthRule.Utf16TextOnly, maxWarningLength = 100)
    private val family = "👨‍👩‍👧"
    private val flag = "🇩🇪"

    @Test
    fun mastodonCombinesWarningAndText() {
        assertEquals(9, PostLengthCounter.count(mastodon, text = "hello", warning = "spam"))
        assertNull(PostLengthCounter.warningCount(mastodon, "spam"))
    }

    @Test
    fun mastodonCountsAnEmojiSequenceAsOne() {
        assertEquals(1, PostLengthCounter.count(mastodon, family))
        assertEquals(1, PostLengthCounter.count(mastodon, flag))
        assertEquals(3, PostLengthCounter.count(mastodon, "a${flag}b"))
    }

    @Test
    fun mastodonCountsEveryUrlAsTheReservedLength() {
        val url = "https://example.com/" + "a".repeat(200)

        assertEquals(23, PostLengthCounter.count(mastodon, url))
        assertEquals(23 + 1 + 3, PostLengthCounter.count(mastodon, "$url see"))
        assertEquals(33, PostLengthCounter.count(mastodon.copy(charactersReservedPerUrl = 30), "$url hi"))
    }

    @Test
    fun mastodonKeepsTrailingPunctuationOutOfTheUrl() {
        assertEquals(23 + 2, PostLengthCounter.count(mastodon, "https://example.com/path)."))
    }

    @Test
    fun mastodonDropsTheDomainOfARemoteMentionOnly() {
        assertEquals("@alice hi".length, PostLengthCounter.count(mastodon, "@alice@remote.example hi"))
        assertEquals("@alice hi".length, PostLengthCounter.count(mastodon, "@alice hi"))
    }

    @Test
    fun misskeyCountsTextOnlyInUtf16Units() {
        assertEquals(5, PostLengthCounter.count(misskey, text = "hello", warning = "a long warning"))
        assertEquals(8, PostLengthCounter.count(misskey, family))
        assertEquals(14, PostLengthCounter.warningCount(misskey, "a long warning"))
        assertEquals(60, PostLengthCounter.count(misskey, "https://example.com/" + "a".repeat(40)))
    }

    @Test
    fun emptyTextCountsZero() {
        assertEquals(0, PostLengthCounter.count(mastodon, ""))
        assertEquals(0, PostLengthCounter.count(misskey, ""))
    }
}
