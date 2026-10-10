package me.foxtails.palustris.data.media

import android.content.Context
import android.provider.Settings

/** Reduced motion means the system animator duration scale is zero. */
fun isReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
