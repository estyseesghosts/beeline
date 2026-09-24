package me.foxtails.palustris.data.mastodon

import org.junit.Assert.assertEquals
import org.junit.Test

class MastodonPathEncodingTest {
    @Test
    fun plainIdStaysUnchanged() {
        assertEquals("109876543210", "109876543210".encodeMastodonPathSegment())
    }

    @Test
    fun slashIsEncoded() {
        assertEquals("a%2Fb", "a/b".encodeMastodonPathSegment())
    }

    @Test
    fun questionMarkIsEncoded() {
        assertEquals("a%3Fb", "a?b".encodeMastodonPathSegment())
    }

    @Test
    fun spaceUsesPercentTwenty() {
        assertEquals("a%20b", "a b".encodeMastodonPathSegment())
    }

    @Test
    fun literalPlusUsesPercentTwoB() {
        // URLEncoder writes a space as `+`. The helper replaces `+` with `%20`.
        // A literal plus must stay `%2B` and must not become `%20`.
        assertEquals("a%2Bb", "a+b".encodeMastodonPathSegment())
    }

    @Test
    fun percentIsEncoded() {
        assertEquals("100%25", "100%".encodeMastodonPathSegment())
    }

    @Test
    fun hashIsEncoded() {
        assertEquals("a%23b", "a#b".encodeMastodonPathSegment())
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertEquals("", "".encodeMastodonPathSegment())
    }
}
