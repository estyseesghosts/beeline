package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PostFontTest {
    @Test
    fun aPostKeepsTheApplicationFontOnlyWhenItIsTheFontOfTheScript() {
        assertEquals(AppFont.KiwiMaru, postFont(PostScript.Japanese, AppFont.KiwiMaru))
        assertEquals(AppFont.ChironHeiHk, postFont(PostScript.Cantonese, AppFont.ChironHeiHk))
        assertEquals(AppFont.ChocolateClassicalSans, postFont(PostScript.TraditionalChinese, AppFont.ChocolateClassicalSans))
        assertEquals(AppFont.ZcoolKuaiLe, postFont(PostScript.SimplifiedChinese, AppFont.ZcoolKuaiLe))
    }

    @Test
    fun everyOtherApplicationFontGivesTheSystemFont() {
        PostScript.entries.forEach { script ->
            AppFont.entries.filter { it != script.bundledFont }.forEach { appFont ->
                assertEquals("$script with $appFont", AppFont.Device, postFont(script, appFont))
            }
        }
        // The font of another script is no exception: a Kiwi Maru user reads Chinese in the system font.
        assertEquals(AppFont.Device, postFont(PostScript.TraditionalChinese, AppFont.KiwiMaru))
        assertEquals(AppFont.Device, postFont(PostScript.Japanese, AppFont.Inter))
    }

    @Test
    fun eachScriptNamesTheLocaleOfItsRegionalGlyphForms() {
        assertEquals("ja-JP", PostScript.Japanese.systemLocaleTag)
        assertEquals("zh-Hant-HK", PostScript.Cantonese.systemLocaleTag)
        assertEquals("zh-Hant-TW", PostScript.TraditionalChinese.systemLocaleTag)
        assertEquals("zh-Hans-CN", PostScript.SimplifiedChinese.systemLocaleTag)
    }
}
