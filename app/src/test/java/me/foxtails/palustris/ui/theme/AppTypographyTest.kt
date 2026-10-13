package me.foxtails.palustris.ui.theme

import androidx.compose.ui.text.font.FontFamily
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppTypographyTest {
    @Test
    fun semanticRolesKeepTheirMaterialStyleMappingAcrossFontsAndScales() {
        AppFont.entries.forEach { font ->
            AppTextSize.entries.forEach { textSize ->
                val typography = appTypography(font, textSize)

                assertEquals(typography.bodyLarge, typography.postBody)
                assertEquals(typography.titleSmall, typography.postAuthor)
                assertEquals(typography.labelSmall, typography.postMetadata)
                assertEquals(typography.bodyLarge, typography.tab)
            }
        }
    }

    @Test
    fun fontChoicesMapToTheirDeviceOrBundledFamily() {
        assertEquals(FontFamily.Default, appTypography(AppFont.Device, AppTextSize.Device).bodyLarge.fontFamily)
        assertEquals(FontFamily.Serif, appTypography(AppFont.Serif, AppTextSize.Device).bodyLarge.fontFamily)
        assertEquals(FontFamily.SansSerif, appTypography(AppFont.Sans, AppTextSize.Device).bodyLarge.fontFamily)

        val openDyslexic = appTypography(AppFont.OpenDyslexic, AppTextSize.Device).bodyLarge.fontFamily
        assertNotEquals(FontFamily.Default, openDyslexic)
        assertNotEquals(FontFamily.Serif, openDyslexic)
        assertNotEquals(FontFamily.SansSerif, openDyslexic)
    }

    @Test
    fun newBundledFontsMapToTheirOwnFamilies() {
        val bundled = listOf(
            AppFont.Inter,
            AppFont.ZcoolKuaiLe,
            AppFont.ChocolateClassicalSans,
            AppFont.ChironHeiHk,
            AppFont.KiwiMaru,
            AppFont.Sarala,
        ).map { font -> appTypography(font, AppTextSize.Device).bodyLarge.fontFamily }

        bundled.forEach { family ->
            assertNotEquals(FontFamily.Default, family)
            assertNotEquals(FontFamily.Serif, family)
            assertNotEquals(FontFamily.SansSerif, family)
        }
        assertEquals(bundled.size, bundled.toSet().size)
    }
}
