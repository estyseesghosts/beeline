package me.foxtails.palustris.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppTextSize

private val OpenDyslexicFont = FontFamily(
    Font(R.font.open_dyslexic3_regular, weight = FontWeight.Normal),
    Font(R.font.open_dyslexic3_bold, weight = FontWeight.Bold),
)

fun appTypography(font: AppFont, textSize: AppTextSize): Typography {
    val family = when (font) {
        AppFont.Device -> FontFamily.Default
        AppFont.Serif -> FontFamily.Serif
        AppFont.Sans -> FontFamily.SansSerif
        AppFont.OpenDyslexic -> OpenDyslexicFont
    }
    val scale = when (textSize) {
        AppTextSize.Device -> 1f
        AppTextSize.Smaller -> 0.9f
        AppTextSize.Larger -> 1.1f
    }
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.with(family, scale),
        displayMedium = base.displayMedium.with(family, scale),
        displaySmall = base.displaySmall.with(family, scale),
        headlineLarge = base.headlineLarge.with(family, scale),
        headlineMedium = base.headlineMedium.with(family, scale),
        headlineSmall = base.headlineSmall.with(family, scale),
        titleLarge = base.titleLarge.with(family, scale),
        titleMedium = base.titleMedium.with(family, scale),
        titleSmall = base.titleSmall.with(family, scale),
        bodyLarge = base.bodyLarge.with(family, scale),
        bodyMedium = base.bodyMedium.with(family, scale),
        bodySmall = base.bodySmall.with(family, scale),
        labelLarge = base.labelLarge.with(family, scale),
        labelMedium = base.labelMedium.with(family, scale),
        labelSmall = base.labelSmall.with(family, scale),
    )
}

val Typography.postBody: TextStyle
    get() = bodyLarge

val Typography.postAuthor: TextStyle
    get() = titleSmall

val Typography.postMetadata: TextStyle
    get() = labelSmall

val Typography.tab: TextStyle
    get() = bodyLarge

private fun TextStyle.with(family: FontFamily, scale: Float): TextStyle = copy(
    fontFamily = family,
    fontSize = fontSize * scale,
    lineHeight = lineHeight * scale,
)
