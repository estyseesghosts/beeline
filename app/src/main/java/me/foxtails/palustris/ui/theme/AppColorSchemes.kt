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

fun appPaletteColor(palette: AppColorPalette): Color = when (palette) {
    AppColorPalette.PastelRed -> Color(0xFFF28B82)
    AppColorPalette.PastelOrange -> Color(0xFFF6B26B)
    AppColorPalette.PastelYellow -> Color(0xFFF9E27D)
    AppColorPalette.PastelGreen -> Color(0xFFA8D5BA)
    AppColorPalette.PastelBlue -> Color(0xFF9FC5E8)
    AppColorPalette.PastelIndigo -> Color(0xFFA9A7E8)
    AppColorPalette.PastelViolet -> Color(0xFFD7A7E8)
    AppColorPalette.VibrantRed -> Color(0xFFE53935)
    AppColorPalette.VibrantOrange -> Color(0xFFF57C00)
    AppColorPalette.VibrantYellow -> Color(0xFFFBC02D)
    AppColorPalette.VibrantGreen -> Color(0xFF43A047)
    AppColorPalette.VibrantBlue -> Color(0xFF1E88E5)
    AppColorPalette.VibrantIndigo -> Color(0xFF3949AB)
    AppColorPalette.VibrantViolet -> Color(0xFF8E24AA)
}

fun selectedAppColorScheme(
    palette: AppColorPalette,
    darkTheme: Boolean,
): ColorScheme {
    val seed = paletteSeed(palette)
    val primary = tone(seed, if (darkTheme) 0.78f else 0.42f, 1f)
    val primaryContainer = tone(seed, if (darkTheme) 0.32f else 0.86f, 1f)
    val secondarySeed = seed.copy(hue = (seed.hue + 36f) % 360f)
    val tertiarySeed = seed.copy(hue = (seed.hue + 72f) % 360f)
    val secondary = tone(secondarySeed, if (darkTheme) 0.68f else 0.42f, 0.8f)
    val secondaryContainer = tone(secondarySeed, if (darkTheme) 0.28f else 0.84f, 0.8f)
    val tertiary = tone(tertiarySeed, if (darkTheme) 0.68f else 0.42f, 0.8f)
    val tertiaryContainer = tone(tertiarySeed, if (darkTheme) 0.28f else 0.84f, 0.8f)
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
        onPrimary = contrasting(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = contrasting(primaryContainer),
        inversePrimary = tone(seed, if (darkTheme) 0.42f else 0.78f, 1f),
        secondary = secondary,
        onSecondary = contrasting(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = contrasting(secondaryContainer),
        tertiary = tertiary,
        onTertiary = contrasting(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = contrasting(tertiaryContainer),
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

private fun paletteSeed(palette: AppColorPalette): PaletteSeed = PaletteSeed(
    hue = when (palette) {
        AppColorPalette.PastelRed, AppColorPalette.VibrantRed -> 0f
        AppColorPalette.PastelOrange, AppColorPalette.VibrantOrange -> 30f
        AppColorPalette.PastelYellow, AppColorPalette.VibrantYellow -> 60f
        AppColorPalette.PastelGreen, AppColorPalette.VibrantGreen -> 120f
        AppColorPalette.PastelBlue, AppColorPalette.VibrantBlue -> 210f
        AppColorPalette.PastelIndigo, AppColorPalette.VibrantIndigo -> 240f
        AppColorPalette.PastelViolet, AppColorPalette.VibrantViolet -> 280f
    },
    saturation = if (palette.name.startsWith("Pastel")) 0.55f else 0.85f,
)

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

private fun contrasting(color: Color): Color {
    val brightness = color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
    return if (brightness > 0.58f) Color(0xFF151515) else Color.White
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
