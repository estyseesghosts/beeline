package me.foxtails.palustris.ui.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HashtagTokenTest {
    @Test
    fun aCursorAfterTheFragmentFindsIt() {
        assertEquals(HashtagToken(0, 4, "pho"), hashtagTokenAt("#pho", 4))
    }

    @Test
    fun aBareHashFindsNothing() {
        assertNull(hashtagTokenAt("#", 1))
        assertNull(hashtagTokenAt("hello #", 7))
        assertNull(hashtagTokenAt("#pho", 1))
    }

    @Test
    fun aHashInsideAWordIsNotAToken() {
        assertNull(hashtagTokenAt("word#pho", 8))
    }

    @Test
    fun aCursorInsideATokenTakesTheFragmentBeforeItAndCoversTheWholeWord() {
        assertEquals(HashtagToken(0, 6, "pho"), hashtagTokenAt("#photo ", 4))
        assertEquals(HashtagToken(6, 12, "pho"), hashtagTokenAt("hello #photo", 10))
    }

    @Test
    fun theCursorMustSitInsideTheTokenNotAfterASpace() {
        assertNull(hashtagTokenAt("#pho ", 5))
        assertNull(hashtagTokenAt("#pho and", 8))
    }

    @Test
    fun aTokenAfterANewlineIsFound() {
        assertEquals(HashtagToken(6, 10, "pho"), hashtagTokenAt("hello\n#pho", 10))
    }

    @Test
    fun aTokenNextToAnEmojiIsFound() {
        assertEquals(HashtagToken(6, 10, "pho"), hashtagTokenAt(":blob:#pho", 10))
        assertEquals(HashtagToken(2, 6, "pho"), hashtagTokenAt("😀#pho", 6))
        assertEquals(HashtagToken(0, 4, "pho"), hashtagTokenAt("#pho😀", 4))
    }

    @Test
    fun letterRulesMatchPosts() {
        assertEquals(HashtagToken(0, 3, "写真"), hashtagTokenAt("#写真 ", 3))
        assertEquals("tecnología", hashtagTokenAt("#tecnología", 11)?.fragment)
    }

    @Test
    fun aTokenMustEndAtABoundary() {
        assertNull(hashtagTokenAt("#pho.nope", 4))
        assertEquals(HashtagToken(0, 4, "pho"), hashtagTokenAt("#pho,nope", 4))
    }

    @Test
    fun outOfRangeCursorsFindNothing() {
        assertNull(hashtagTokenAt("#pho", -1))
        assertNull(hashtagTokenAt("#pho", 9))
        assertNull(hashtagTokenAt("", 0))
    }
}
