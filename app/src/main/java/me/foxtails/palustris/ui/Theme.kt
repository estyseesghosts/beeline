package me.foxtails.palustris.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.ui.motion.LocalPalustrisHaptics
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.palustrisMotionScheme
import me.foxtails.palustris.ui.motion.rememberPalustrisHaptics
import me.foxtails.palustris.ui.theme.AppFontTheme
import me.foxtails.palustris.ui.theme.resolvedAppColorScheme

@Composable
fun PalustrisTheme(
    preferences: AppPreferences = AppPreferences(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val motionScheme = palustrisMotionScheme()
    val colorScheme = resolvedAppColorScheme(context, preferences.colorScheme, preferences.colorPalette, preferences.background)
    CompositionLocalProvider(
        LocalPalustrisMotionScheme provides motionScheme,
        LocalPalustrisHaptics provides rememberPalustrisHaptics(),
    ) {
        AppFontTheme(preferences, colorScheme, content)
    }
}

@Composable
fun PalustrisTheme(content: @Composable () -> Unit) {
    PalustrisTheme(preferences = AppPreferences(), content = content)
}
