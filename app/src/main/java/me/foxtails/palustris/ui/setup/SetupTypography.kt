package me.foxtails.palustris.ui.setup

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.defaultFontForLanguage
import me.foxtails.palustris.ui.localization.AppLocaleController
import me.foxtails.palustris.ui.theme.appFontFamily

/**
 * The first-launch typeface. It follows the per-language display default, so setup text renders
 * in the same font the language uses after sign-in. Languages without a bundled default use the
 * system font, whose Noto family covers every script.
 */
internal fun setupFontFamily(language: AppLanguage): FontFamily =
    when (val default = defaultFontForLanguage(language)) {
        AppFont.Device -> FontFamily.Default
        else -> appFontFamily(default)
    }

/**
 * The effective application language for first-launch screens, read from the current
 * configuration locale. The activity already applies the stored or platform language to its base
 * context, so this matches the language the application actually renders. A region-qualified
 * device locale (en-US, ja-JP) falls back to its bare language.
 */
internal fun setupLanguage(context: Context): AppLanguage {
    val tag = context.resources.configuration.getLocales().get(0)?.toLanguageTag()
    val exact = AppLocaleController.languageForTag(tag)
    if (exact != AppLanguage.SystemDefault || tag.isNullOrBlank()) return exact
    return AppLanguage.entries.firstOrNull { it.tag == tag.substringBefore('-') }
        ?: AppLanguage.SystemDefault
}

/** Applies one family to every first-launch text style. */
internal fun withSetupFamily(base: Typography, family: FontFamily): Typography = base.copy(
    displaySmall = base.displaySmall.copy(
        fontFamily = family,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = family,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = family,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
    ),
    titleLarge = base.titleLarge.copy(
        fontFamily = family,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
    ),
    labelLarge = base.labelLarge.copy(
        fontFamily = family,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
    ),
    bodyLarge = base.bodyLarge.copy(fontFamily = family, letterSpacing = (-0.05).em),
    bodyMedium = base.bodyMedium.copy(fontFamily = family, letterSpacing = (-0.05).em),
)

@Composable
internal fun setupTypography(base: Typography): Typography =
    withSetupFamily(base, setupFontFamily(setupLanguage(LocalContext.current)))
