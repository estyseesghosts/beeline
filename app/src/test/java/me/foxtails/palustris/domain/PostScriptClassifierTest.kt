package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostScriptClassifierTest {
    private fun classify(text: String): PostScript? = PostScriptClassifier.classify(text)

    @Test
    fun kanaProvesJapaneseAlsoBesideChineseCharacters() {
        assertEquals(PostScript.Japanese, classify("今日はいい天気ですね"))
        assertEquals(PostScript.Japanese, classify("カメラ"))
        assertEquals(PostScript.Japanese, classify("ｶﾒﾗ"))
        // One kana decides, also beside characters that only traditional Chinese uses.
        assertEquals(PostScript.Japanese, classify("學習の時間"))
        assertEquals(PostScript.Japanese, classify("Photo walk in 東京 でした"))
    }

    @Test
    fun japanOnlyPunctuationProvesJapanese() {
        assertEquals(PostScript.Japanese, classify("東京〜大阪"))
        assertEquals(PostScript.Japanese, classify("〒100"))
        assertEquals(PostScript.Japanese, classify("締切〆"))
    }

    @Test
    fun punctuationThatChineseSharesDoesNotProveJapanese() {
        assertNull(classify("你好。"))
        assertNull(classify("你好、大家"))
        assertNull(classify("「你好」"))
        assertEquals(PostScript.TraditionalChinese, classify("「這裡」。"))
    }

    @Test
    fun aCantoneseOnlyCharacterProvesCantonese() {
        assertEquals(PostScript.Cantonese, classify("我哋今日去邊度食飯"))
        assertEquals(PostScript.Cantonese, classify("佢喺屋企"))
        assertEquals(PostScript.Cantonese, classify("冇問題"))
        // Cantonese wins over the traditional and the simplified evidence in the same text.
        assertEquals(PostScript.Cantonese, classify("這個係乜嘢"))
        assertEquals(PostScript.Cantonese, classify("这个唔好"))
    }

    @Test
    fun aTraditionalOnlyCharacterProvesTraditionalChinese() {
        assertEquals(PostScript.TraditionalChinese, classify("這是我們的時間"))
        assertEquals(PostScript.TraditionalChinese, classify("臺灣的風景很美麗"))
        assertEquals(PostScript.TraditionalChinese, classify("Hello 學習"))
    }

    @Test
    fun aSimplifiedOnlyCharacterProvesSimplifiedChinese() {
        assertEquals(PostScript.SimplifiedChinese, classify("这是我们的时间"))
        assertEquals(PostScript.SimplifiedChinese, classify("今天的风景很美丽"))
    }

    @Test
    fun traditionalEvidenceWinsOverSimplifiedEvidence() {
        assertEquals(PostScript.TraditionalChinese, classify("這 and 这"))
    }

    @Test
    fun textWithoutEvidenceHasNoClass() {
        assertNull(classify(""))
        assertNull(classify("Hello world #photo"))
        assertNull(classify("Привет"))
        assertNull(classify("नमस्ते"))
        assertNull(classify("مرحبا"))
        assertNull(classify("안녕하세요"))
        // These characters are the same in every Chinese variant and in Japanese.
        assertNull(classify("你好"))
        assertNull(classify("中文 大小 山水"))
        // A simplified form that is also a traditional or a Japanese character proves nothing.
        assertNull(classify("国 会 学 来 后 里 机 气"))
        assertNull(classify("😀🎉"))
    }
}
