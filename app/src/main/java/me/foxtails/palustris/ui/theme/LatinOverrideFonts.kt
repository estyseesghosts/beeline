package me.foxtails.palustris.ui.theme

import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily as PlatformFontFamily
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont
import me.foxtails.palustris.domain.AppFont

/**
 * Builds font families that draw Latin characters in Inter and every other character in a base
 * font. A Compose font list selects one font for a whole text, so it cannot do this. A platform
 * typeface with a fallback chain can: Inter comes first, the base font second, and the system
 * font last. Inter holds no Chinese, Japanese, Korean, Devanagari, or Arabic glyphs, so those
 * characters fall through to the base font.
 *
 * One instance belongs to one theme composition, which remembers it. It keeps each typeface it
 * builds, because a build reads the font files. Composition reads it on the main thread only.
 */
internal class LatinOverrideFonts(private val resources: Resources) {
    // One slot for each font option, so the size never grows.
    private val families = arrayOfNulls<FontFamily>(AppFont.entries.size)

    /** The family for [base]. A failed build gives the plain family of [base]. */
    fun family(base: AppFont): FontFamily = families[base.ordinal]
        ?: runCatching { FontFamily(build(base)) }.getOrElse { appFontFamily(base) }.also { families[base.ordinal] = it }

    private fun build(base: AppFont): Typeface {
        val builder = Typeface.CustomFallbackBuilder(requireNotNull(platformFamily(AppFont.Inter)))
        platformFamily(base)?.let(builder::addCustomFallback)
        return builder.setSystemFallback("sans-serif").build()
    }

    /**
     * The platform family of a bundled font, from the files of its Compose family, so the two
     * never name different files. A system font has no files and gives null.
     */
    private fun platformFamily(font: AppFont): PlatformFontFamily? {
        val files = (appFontFamily(font) as? FontListFontFamily)?.fonts.orEmpty().filterIsInstance<ResourceFont>()
        val fonts = files.distinctBy { it.weight.weight }.map { file ->
            // A variable file serves several weights. A static file ignores the setting.
            Font.Builder(resources, file.resId)
                .setWeight(file.weight.weight)
                .setFontVariationSettings("'wght' ${file.weight.weight}")
                .build()
        }
        if (fonts.isEmpty()) return null
        val family = PlatformFontFamily.Builder(fonts.first())
        fonts.drop(1).forEach(family::addFont)
        return family.build()
    }
}

/** Null while the Latin font override is off. `AppFontTheme` provides it. */
internal val LocalLatinOverrideFonts = staticCompositionLocalOf<LatinOverrideFonts?> { null }

/** Applies one family to every text style. */
internal fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
