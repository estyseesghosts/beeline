package me.foxtails.palustris.ui.theme

import me.foxtails.palustris.domain.AppColorPalette
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColorSchemesTest {
    @Test
    fun outlineKeepsThreeToOneAgainstSurfacesInEveryPaletteAndTheme() {
        for (palette in AppColorPalette.entries) {
            for (dark in listOf(false, true)) {
                val scheme = selectedAppColorScheme(palette, dark)
                for ((name, surface) in listOf("surface" to scheme.surface, "surfaceContainer" to scheme.surfaceContainer)) {
                    val ratio = contrastRatio(scheme.outline, surface)
                    assertTrue("$palette dark=$dark outline vs $name = $ratio", ratio >= MIN_OUTLINE_CONTRAST)
                }
            }
        }
    }

    @Test
    fun outlineVariantStaysVisibleButSofterThanOutline() {
        for (palette in AppColorPalette.entries) {
            for (dark in listOf(false, true)) {
                val scheme = selectedAppColorScheme(palette, dark)
                assertTrue(
                    "$palette dark=$dark",
                    contrastRatio(scheme.outlineVariant, scheme.surfaceContainer) <
                        contrastRatio(scheme.outline, scheme.surfaceContainer),
                )
            }
        }
    }
}
