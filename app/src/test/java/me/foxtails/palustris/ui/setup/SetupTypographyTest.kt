package me.foxtails.palustris.ui.setup

import androidx.compose.ui.text.font.FontFamily
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.ui.theme.appFontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupTypographyTest {
    @Test
    fun mappedLanguagesUseTheirBundledFamily() {
        assertEquals(appFontFamily(AppFont.Inter), setupFontFamily(AppLanguage.English))
        assertEquals(appFontFamily(AppFont.ZcoolKuaiLe), setupFontFamily(AppLanguage.ChineseMainland))
        assertEquals(appFontFamily(AppFont.ChocolateClassicalSans), setupFontFamily(AppLanguage.ChineseTaiwan))
        assertEquals(appFontFamily(AppFont.ChironHeiHk), setupFontFamily(AppLanguage.CantoneseHongKong))
        assertEquals(appFontFamily(AppFont.KiwiMaru), setupFontFamily(AppLanguage.Japanese))
        assertEquals(appFontFamily(AppFont.Sarala), setupFontFamily(AppLanguage.Hindi))
    }

    @Test
    fun systemCoveredLanguagesUseTheSystemFont() {
        listOf(
            AppLanguage.SystemDefault,
            AppLanguage.Chinese,
            AppLanguage.Korean,
            AppLanguage.Russian,
        ).forEach { language ->
            assertEquals(FontFamily.Default, setupFontFamily(language))
        }
    }
}
