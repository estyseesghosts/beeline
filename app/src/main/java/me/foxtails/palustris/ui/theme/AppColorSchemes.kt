package me.foxtails.palustris.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorPalette
import me.foxtails.palustris.domain.AppColorScheme

@Composable
fun resolvedAppColorScheme(
    context: Context,
    colorScheme: AppColorScheme,
    palette: AppColorPalette,
    background: AppBackground,
    darkTheme: Boolean = isSystemInDarkTheme(),
): ColorScheme {
    val system = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    val base = when (colorScheme) {
        AppColorScheme.System -> system
        AppColorScheme.SystemMonochrome -> grayscale(system)
        AppColorScheme.Palette -> selectedAppColorScheme(palette, darkTheme)
    }
    return when (background) {
        AppBackground.Default -> base
        AppBackground.Dark -> base.withNeutralBackground(Color(0xFF090909), dark = true)
        AppBackground.PureBlack -> base.withNeutralBackground(Color.Black, dark = true)
    }
}

fun appPaletteColor(palette: AppColorPalette): Color = paletteColors(palette).primary

/** The four hand-picked colours of one palette, lightest to darkest by luminance. */
internal data class PaletteColors(
    val lighter: Color,
    val primary: Color,
    val darker: Color,
    val darkest: Color,
)

internal fun paletteColors(palette: AppColorPalette): PaletteColors = when (palette) {
    AppColorPalette.PastelGold -> PaletteColors(Color(0xFFFFC768), Color(0xFFF88200), Color(0xFFD37329), Color(0xFF594834))
    AppColorPalette.PastelRed -> PaletteColors(Color(0xFFFFAA92), Color(0xFFFF594F), Color(0xFFE85762), Color(0xFF5C3F3C))
    AppColorPalette.PastelPink -> PaletteColors(Color(0xFFFFB4EB), Color(0xFFFA6AAC), Color(0xFFFA93C0), Color(0xFF711544))
    AppColorPalette.PastelIndigo -> PaletteColors(Color(0xFFEDD2FF), Color(0xFFB189F2), Color(0xFF745EAE), Color(0xFF493E76))
    AppColorPalette.PastelBlue -> PaletteColors(Color(0xFFC7D1FF), Color(0xFF627BE9), Color(0xFF425091), Color(0xFF2B3873))
    AppColorPalette.PastelGreen -> PaletteColors(Color(0xFFB4FBA6), Color(0xFF88DA78), Color(0xFF6FC15E), Color(0xFF2D5126))
    AppColorPalette.VibrantGold -> PaletteColors(Color(0xFFFFC711), Color(0xFFEE8711), Color(0xFFD16F10), Color(0xFF6C380B))
    AppColorPalette.VibrantRed -> PaletteColors(Color(0xFFFF706A), Color(0xFFF11E16), Color(0xFFB63832), Color(0xFF270807))
    AppColorPalette.VibrantPink -> PaletteColors(Color(0xFFFF80C3), Color(0xFFF62B8F), Color(0xFFC24883), Color(0xFF3B1D2C))
    AppColorPalette.VibrantIndigo -> PaletteColors(Color(0xFFCEA2FF), Color(0xFF9453FF), Color(0xFF664FA4), Color(0xFF3F3661))
    AppColorPalette.VibrantBlue -> PaletteColors(Color(0xFF92A1FB), Color(0xFF3364FE), Color(0xFF2B5094), Color(0xFF182E4E))
    AppColorPalette.VibrantGreen -> PaletteColors(Color(0xFF53CF45), Color(0xFF00AA00), Color(0xFF1E8C12), Color(0xFF021C00))
}

fun selectedAppColorScheme(
    palette: AppColorPalette,
    darkTheme: Boolean,
): ColorScheme {
    val picked = paletteColors(palette)
    val seed = seedFromPrimary(picked.primary)
    val primary = if (darkTheme) picked.lighter else picked.primary
    val onPrimary = if (darkTheme) picked.darkest else contrasting(picked.primary)
    val primaryContainer = if (darkTheme) picked.darkest else picked.lighter
    val onPrimaryContainer = if (darkTheme) picked.lighter else picked.darkest
    val secondary = if (darkTheme) picked.primary else picked.darker
    val onSecondary = contrasting(secondary)
    val secondaryContainer = if (darkTheme) picked.darker else picked.lighter
    val onSecondaryContainer = if (darkTheme) contrasting(secondaryContainer) else picked.darkest
    val tertiary = secondary
    val onTertiary = onSecondary
    val tertiaryContainer = secondaryContainer
    val onTertiaryContainer = onSecondaryContainer
    val inversePrimary = if (darkTheme) picked.primary else picked.lighter
    val neutralSurface = tone(seed, if (darkTheme) 0.16f else 0.98f, if (darkTheme) 0.07f else 0.10f)
    val neutralVariant = tone(seed, if (darkTheme) 0.24f else 0.91f, if (darkTheme) 0.18f else 0.14f)
    val onSurface = tone(seed, if (darkTheme) 0.94f else 0.18f, if (darkTheme) 0.10f else 0.12f)
    val onSurfaceVariant = tone(seed, if (darkTheme) 0.78f else 0.34f, if (darkTheme) 0.12f else 0.14f)
    val error = if (darkTheme) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
    val errorContainer = if (darkTheme) Color(0xFF93000A) else Color(0xFFFFDAD6)
    val surfaceContainer = tone(seed, if (darkTheme) 0.16f else 0.94f, if (darkTheme) 0.12f else 0.06f)
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = neutralSurface,
        onBackground = onSurface,
        surface = neutralSurface,
        onSurface = onSurface,
        surfaceVariant = neutralVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = onSurface,
        inverseOnSurface = neutralSurface,
        error = error,
        onError = contrasting(error),
        errorContainer = errorContainer,
        onErrorContainer = contrasting(errorContainer),
        outline = accessibleOutline(seed, darkTheme, listOf(neutralSurface, surfaceContainer)),
        outlineVariant = tone(seed, if (darkTheme) 0.30f else 0.80f, if (darkTheme) 0.50f else 0.60f),
        scrim = Color.Black,
        surfaceBright = tone(seed, if (darkTheme) 0.24f else 0.98f, if (darkTheme) 0.16f else 0.04f),
        surfaceDim = tone(seed, if (darkTheme) 0.10f else 0.90f, if (darkTheme) 0.04f else 0.10f),
        surfaceContainerLowest = tone(seed, if (darkTheme) 0.11f else 1f, if (darkTheme) 0.03f else 0.02f),
        surfaceContainerLow = tone(seed, if (darkTheme) 0.13f else 0.97f, if (darkTheme) 0.09f else 0.04f),
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = tone(seed, if (darkTheme) 0.20f else 0.90f, if (darkTheme) 0.17f else 0.08f),
        surfaceContainerHighest = tone(seed, if (darkTheme) 0.24f else 0.86f, if (darkTheme) 0.18f else 0.10f),
    )
}

private data class PaletteSeed(val hue: Float, val saturation: Float)

/**
 * The neutral seed for surfaces and outlines, taken from the palette's Primary.
 * Neutral roles are not part of the picked colours, so they stay derived.
 */
private fun seedFromPrimary(primary: Color): PaletteSeed {
    val r = primary.red
    val g = primary.green
    val b = primary.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return PaletteSeed(hue = 0f, saturation = 0f)
    val delta = max - min
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val hue = when (max) {
        r -> ((g - b) / delta + (if (g < b) 6f else 0f)) * 60f
        g -> ((b - r) / delta + 2f) * 60f
        else -> ((r - g) / delta + 4f) * 60f
    }
    return PaletteSeed(hue = hue, saturation = saturation.coerceIn(0f, 1f))
}

private fun tone(seed: PaletteSeed, lightness: Float, saturationScale: Float): Color = Color.hsl(
    hue = seed.hue,
    saturation = (seed.saturation * saturationScale).coerceIn(0f, 1f),
    lightness = lightness.coerceIn(0f, 1f),
)

/**
 * The accent-tinted outline tone, moved away from the surfaces until it keeps [MIN_OUTLINE_CONTRAST]
 * against every one of them. Bright hues such as yellow need a darker light-theme outline than blue.
 */
private fun accessibleOutline(seed: PaletteSeed, darkTheme: Boolean, surfaces: List<Color>): Color {
    var lightness = if (darkTheme) 0.60f else 0.50f
    val step = if (darkTheme) 0.02f else -0.02f
    var outline = tone(seed, lightness, 0.70f)
    while (lightness in 0.05f..0.95f && surfaces.any { contrastRatio(outline, it) < MIN_OUTLINE_CONTRAST }) {
        lightness += step
        outline = tone(seed, lightness, 0.70f)
    }
    return outline
}

internal const val MIN_OUTLINE_CONTRAST = 3f

/** WCAG contrast ratio between two opaque colors. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

internal fun contrasting(color: Color): Color {
    val dark = Color(0xFF151515)
    return if (contrastRatio(color, dark) >= contrastRatio(color, Color.White)) dark else Color.White
}

private fun grayscale(scheme: ColorScheme): ColorScheme = scheme.copy(
    primary = scheme.primary.gray(),
    onPrimary = scheme.onPrimary.gray(),
    primaryContainer = scheme.primaryContainer.gray(),
    onPrimaryContainer = scheme.onPrimaryContainer.gray(),
    inversePrimary = scheme.inversePrimary.gray(),
    secondary = scheme.secondary.gray(),
    onSecondary = scheme.onSecondary.gray(),
    secondaryContainer = scheme.secondaryContainer.gray(),
    onSecondaryContainer = scheme.onSecondaryContainer.gray(),
    tertiary = scheme.tertiary.gray(),
    onTertiary = scheme.onTertiary.gray(),
    tertiaryContainer = scheme.tertiaryContainer.gray(),
    onTertiaryContainer = scheme.onTertiaryContainer.gray(),
    background = scheme.background.gray(),
    onBackground = scheme.onBackground.gray(),
    surface = scheme.surface.gray(),
    onSurface = scheme.onSurface.gray(),
    surfaceVariant = scheme.surfaceVariant.gray(),
    onSurfaceVariant = scheme.onSurfaceVariant.gray(),
    surfaceTint = scheme.surfaceTint.gray(),
    inverseSurface = scheme.inverseSurface.gray(),
    inverseOnSurface = scheme.inverseOnSurface.gray(),
    error = scheme.error.gray(),
    onError = scheme.onError.gray(),
    errorContainer = scheme.errorContainer.gray(),
    onErrorContainer = scheme.onErrorContainer.gray(),
    outline = scheme.outline.gray(),
    outlineVariant = scheme.outlineVariant.gray(),
    scrim = scheme.scrim.gray(),
    surfaceBright = scheme.surfaceBright.gray(),
    surfaceDim = scheme.surfaceDim.gray(),
    surfaceContainerLowest = scheme.surfaceContainerLowest.gray(),
    surfaceContainerLow = scheme.surfaceContainerLow.gray(),
    surfaceContainer = scheme.surfaceContainer.gray(),
    surfaceContainerHigh = scheme.surfaceContainerHigh.gray(),
    surfaceContainerHighest = scheme.surfaceContainerHighest.gray(),
)

private fun Color.gray(): Color {
    val value = red * 0.2126f + green * 0.7152f + blue * 0.0722f
    return Color(value, value, value, alpha)
}

private fun ColorScheme.withNeutralBackground(background: Color, dark: Boolean): ColorScheme = copy(
    background = background,
    surface = background,
    surfaceDim = background,
    surfaceBright = background,
    surfaceContainerLowest = background,
    surfaceContainerLow = background,
    surfaceContainer = if (dark) background.copy(alpha = 0.96f) else background,
    surfaceContainerHigh = if (dark) Color(0xFF1D1D1D) else Color(0xFFF2F2F2),
    surfaceContainerHighest = if (dark) Color(0xFF282828) else Color(0xFFE8E8E8),
    onBackground = if (dark) Color.White else Color.Black,
    onSurface = if (dark) Color.White else Color.Black,
    onSurfaceVariant = if (dark) Color(0xFFD0D0D0) else Color(0xFF454545),
)
