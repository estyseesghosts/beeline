@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** How the navigation bar looks. [Transparent] lets content draw under the bar. */
internal sealed interface NavigationBarPolicy {
    data object Transparent : NavigationBarPolicy
    data object Black : NavigationBarPolicy
    data class Opaque(val color: Color) : NavigationBarPolicy
}

/**
 * Decides the navigation bar from the platform level, the navigation mode and the app theme.
 *
 * The bar is transparent everywhere, so the feed and the blur band draw to the physical edge. Android 10
 * with three buttons is the one case where a transparent bar is not confirmed on a device, so it keeps an
 * opaque bar in the app background color. The media viewer is black.
 */
internal fun navigationBarPolicy(
    sdkInt: Int,
    threeButtonNavigation: Boolean,
    background: Color,
    mediaViewerOpen: Boolean,
): NavigationBarPolicy = when {
    mediaViewerOpen -> NavigationBarPolicy.Black
    sdkInt <= Build.VERSION_CODES.Q && threeButtonNavigation -> NavigationBarPolicy.Opaque(background)
    else -> NavigationBarPolicy.Transparent
}

/**
 * The one owner of the system bar appearance for the activity window. It runs for every app state, and it
 * paints the window background in the theme background so a gap or a first frame never shows a static color.
 */
@Composable
internal fun SystemBars(mediaViewerOpen: Boolean) {
    val view = LocalView.current
    val window = (view.rootView.context.findActivity() ?: LocalContext.current.findActivity())?.window ?: return
    val background = MaterialTheme.colorScheme.background
    val policy = navigationBarPolicy(Build.VERSION.SDK_INT, rememberThreeButtonNavigation(), background, mediaViewerOpen)
    SideEffect { window.applyNavigationBar(policy, background, view) }
}

/** The tappable inset is only present with three-button navigation. Gesture navigation reports none. */
@Composable
private fun rememberThreeButtonNavigation(): Boolean = WindowInsets.tappableElement.getBottom(LocalDensity.current) > 0

/** Sets the bar color from [policy]. The icons sit on the color behind the bar: black, an opaque bar, or the theme. */
private fun Window.applyNavigationBar(policy: NavigationBarPolicy, background: Color, view: View) {
    val behind = when (policy) {
        NavigationBarPolicy.Black -> Color.Black
        is NavigationBarPolicy.Opaque -> policy.color
        NavigationBarPolicy.Transparent -> background
    }
    val barColor = if (policy == NavigationBarPolicy.Transparent) Color.Transparent else behind
    setBackgroundDrawable(ColorDrawable(background.toArgb()))
    navigationBarColor = barColor.toArgb()
    navigationBarDividerColor = barColor.toArgb()
    isNavigationBarContrastEnforced = false
    WindowCompat.getInsetsController(this, view).isAppearanceLightNavigationBars = behind.luminance() > 0.5f
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}
