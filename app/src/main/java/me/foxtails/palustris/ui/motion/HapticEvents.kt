package me.foxtails.palustris.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Semantic haptic events shared by every control. Callers name the event; the map below owns the platform constant.
 * Fire an event once per discrete moment (never per drag frame).
 */
enum class HapticEvent {
    /** A selected state changed, for example a tab or reaction choice. */
    Selection,

    /** An action was committed, for example a send or confirm. */
    Commit,

    /** A gesture crossed a threshold, for example drag-to-dismiss or pull-to-refresh arming. */
    Threshold,

    /** A press was held long enough to open a secondary surface. */
    LongPress,

    /** A destructive action was confirmed. */
    DestructiveConfirm,
}

/** Maps an event to the platform constant for [sdk]. Only constants that exist on API 29 are used below API 30. */
fun HapticEvent.platformConstant(sdk: Int): Int = when (this) {
    HapticEvent.Selection -> HapticFeedbackConstants.CLOCK_TICK
    HapticEvent.Commit -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
    HapticEvent.Threshold -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
    HapticEvent.LongPress -> HapticFeedbackConstants.LONG_PRESS
    HapticEvent.DestructiveConfirm -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
}

/**
 * Performs haptic events through [sink]. The platform sink honors the system touch-feedback setting and the view's
 * haptic flag, so this type adds no preference of its own.
 */
@Immutable
class PalustrisHaptics(
    private val sdk: Int = Build.VERSION.SDK_INT,
    private val sink: (Int) -> Unit,
) {
    fun perform(event: HapticEvent) {
        sink(event.platformConstant(sdk))
    }

    companion object {
        val None = PalustrisHaptics(sink = {})
    }
}

val LocalPalustrisHaptics = compositionLocalOf { PalustrisHaptics.None }

@Composable
fun rememberPalustrisHaptics(): PalustrisHaptics {
    val view = LocalView.current
    return remember(view) { PalustrisHaptics(sink = { constant -> view.performHapticFeedback(constant) }) }
}
