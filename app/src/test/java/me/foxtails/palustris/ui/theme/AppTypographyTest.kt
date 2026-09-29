package me.foxtails.palustris.ui.theme

import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppTextSize
import org.junit.Assert.assertEquals
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
}
