package me.foxtails.palustris.ui.theme

import androidx.compose.ui.graphics.Color
import me.foxtails.palustris.domain.AppColorPalette
import org.junit.Assert.assertEquals
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

    @Test
    fun accentRolesUseTheExactPickedColours() {
        for ((palette, colours) in expectedColours) {
            val light = selectedAppColorScheme(palette, darkTheme = false)
            assertEquals("$palette light primary", colours.primary, light.primary)
            assertEquals("$palette light primaryContainer", colours.lighter, light.primaryContainer)
            assertEquals("$palette light secondaryContainer", colours.lighter, light.secondaryContainer)
            assertEquals("$palette light secondary", colours.darker, light.secondary)
            assertEquals("$palette light tertiary", colours.darker, light.tertiary)
            assertEquals("$palette light tertiaryContainer", colours.lighter, light.tertiaryContainer)
            assertEquals("$palette light inversePrimary", colours.lighter, light.inversePrimary)
            assertEquals("$palette light surfaceTint", colours.primary, light.surfaceTint)

            val dark = selectedAppColorScheme(palette, darkTheme = true)
            assertEquals("$palette dark primary", colours.lighter, dark.primary)
            assertEquals("$palette dark primaryContainer", colours.darkest, dark.primaryContainer)
            assertEquals("$palette dark secondaryContainer", colours.darker, dark.secondaryContainer)
            assertEquals("$palette dark secondary", colours.primary, dark.secondary)
            assertEquals("$palette dark tertiary", colours.primary, dark.tertiary)
            assertEquals("$palette dark tertiaryContainer", colours.darker, dark.tertiaryContainer)
            assertEquals("$palette dark inversePrimary", colours.primary, dark.inversePrimary)
            assertEquals("$palette dark surfaceTint", colours.lighter, dark.surfaceTint)
        }
    }

    // The 4.5:1 accent-text gate from docs/palettes.md is removed for this test build
    // on explicit user order (2026-10-13). Measured with contrastRatio():
    // VibrantRed light onPrimary/primary = 4.29, VibrantIndigo light onPrimary/primary = 4.30,
    // VibrantGreen dark onSecondaryContainer/secondaryContainer = 4.36.
    // The hex values are unchanged. Restore the gate before the design decision lands.

    @Test
    fun paletteSwatchShowsPrimary() {
        for ((palette, colours) in expectedColours) {
            assertEquals("$palette swatch", colours.primary, appPaletteColor(palette))
        }
    }

    private data class Expected(val lighter: Color, val primary: Color, val darker: Color, val darkest: Color)

    private val expectedColours: Map<AppColorPalette, Expected> = mapOf(
        AppColorPalette.PastelGold to Expected(Color(0xFFFFC768), Color(0xFFF88200), Color(0xFFD37329), Color(0xFF594834)),
        AppColorPalette.PastelRed to Expected(Color(0xFFFFAA92), Color(0xFFFF594F), Color(0xFFE85762), Color(0xFF5C3F3C)),
        AppColorPalette.PastelPink to Expected(Color(0xFFFFB4EB), Color(0xFFFA6AAC), Color(0xFFFA93C0), Color(0xFF711544)),
        AppColorPalette.PastelIndigo to Expected(Color(0xFFEDD2FF), Color(0xFFB189F2), Color(0xFF745EAE), Color(0xFF493E76)),
        AppColorPalette.PastelBlue to Expected(Color(0xFFC7D1FF), Color(0xFF627BE9), Color(0xFF425091), Color(0xFF2B3873)),
        AppColorPalette.PastelGreen to Expected(Color(0xFFB4FBA6), Color(0xFF88DA78), Color(0xFF6FC15E), Color(0xFF2D5126)),
        AppColorPalette.VibrantGold to Expected(Color(0xFFFFC711), Color(0xFFEE8711), Color(0xFFD16F10), Color(0xFF6C380B)),
        AppColorPalette.VibrantRed to Expected(Color(0xFFFF706A), Color(0xFFF11E16), Color(0xFFB63832), Color(0xFF270807)),
        AppColorPalette.VibrantPink to Expected(Color(0xFFFF80C3), Color(0xFFF62B8F), Color(0xFFC24883), Color(0xFF3B1D2C)),
        AppColorPalette.VibrantIndigo to Expected(Color(0xFFCEA2FF), Color(0xFF9453FF), Color(0xFF664FA4), Color(0xFF3F3661)),
        AppColorPalette.VibrantBlue to Expected(Color(0xFF92A1FB), Color(0xFF3364FE), Color(0xFF2B5094), Color(0xFF182E4E)),
        AppColorPalette.VibrantGreen to Expected(Color(0xFF53CF45), Color(0xFF00AA00), Color(0xFF1E8C12), Color(0xFF021C00)),
    )
}
