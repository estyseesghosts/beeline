package me.foxtails.palustris.ui.layout

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import me.foxtails.palustris.domain.AppLayoutDirection

/**
 * The single owner of the layout direction policy.
 *
 * Android has no public per-application API that sets layout direction independently of the locale.
 * `Configuration.setLayoutDirection` is not public API, and `LocaleManager.applicationLocales` also
 * changes every string. The application therefore resolves the stored override at the composition
 * root and provides `LocalLayoutDirection`.
 */

/**
 * The device layout direction.
 *
 * Callers must resolve the stored override against this value and never against
 * `LocalLayoutDirection.current`. A provided override replaces that composition local, so reading it
 * below the composition root returns the forced direction. `LocalConfiguration` is the platform
 * authority for the device direction, and a Compose override never changes it.
 */
@Composable
fun deviceLayoutDirection(): LayoutDirection {
    // The platform value is an Int constant. Compose does not accept it directly.
    val platform = LocalConfiguration.current.layoutDirection
    return if (platform == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
}

/** The direction the composition root publishes for this override against [base]. */
fun AppLayoutDirection.resolveAgainst(base: LayoutDirection): LayoutDirection = when (this) {
    AppLayoutDirection.System -> base
    AppLayoutDirection.ForceRtl -> LayoutDirection.Rtl
    AppLayoutDirection.ForceLtr -> LayoutDirection.Ltr
}

/**
 * The override that produces the direction opposite [base]. The switch stores this when the user
 * turns it on. Turning it off stores `System`, which returns to the device direction.
 */
fun AppLayoutDirection.forcingOppositeOf(base: LayoutDirection): AppLayoutDirection =
    if (base == LayoutDirection.Rtl) AppLayoutDirection.ForceLtr else AppLayoutDirection.ForceRtl
