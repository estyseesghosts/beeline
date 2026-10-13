package me.foxtails.palustris.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize

/**
 * The content that floating controls sit over, recorded once so a band can draw a blurred copy of the part under
 * it. It lives as long as the host that remembers it and owns no other state. The source and the band must be
 * siblings, never ancestor and descendant, or the band would record itself.
 */
internal class ChromeBackdrop(val layer: GraphicsLayer) {
    /** Where the recorded content starts, in root coordinates. */
    var sourceOrigin by mutableStateOf(Offset.Zero)

    /** The size of the recorded content, so a band can tell where the backdrop ends. */
    var sourceSize by mutableStateOf(IntSize.Zero)
}

/** Blur needs the platform render effect (API 31) and is off while battery saver is on. */
internal fun chromeBlurAvailable(sdkInt: Int, batterySaver: Boolean): Boolean = sdkInt >= Build.VERSION_CODES.S && !batterySaver

/**
 * Whether the user allows blur effects. The composition root provides it from the "Disable blur effects"
 * setting. Every blur site reads it, so one setting turns all blur off. The default is true for previews and tests.
 */
internal val LocalBlurEffectsEnabled = staticCompositionLocalOf { true }

/** The backdrop for one host, or null where the band should draw its gradient alone. */
@Composable
internal fun rememberChromeBackdrop(): ChromeBackdrop? {
    val saver = rememberBatterySaverOn()
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { ChromeBackdrop(layer) }
    val allowed = LocalBlurEffectsEnabled.current
    return backdrop.takeIf { allowed && chromeBlurAvailable(Build.VERSION.SDK_INT, saver) }
}

@Composable
private fun rememberBatterySaverOn(): Boolean {
    val context = LocalContext.current
    var on by remember { mutableStateOf(context.powerManager()?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                on = c.powerManager()?.isPowerSaveMode == true
            }
        }
        val app = context.applicationContext
        app.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { app.unregisterReceiver(receiver) }
    }
    return on
}

private fun Context.powerManager(): PowerManager? = getSystemService(Context.POWER_SERVICE) as? PowerManager

/** Records this node's content into [backdrop] and draws it as usual. Without a backdrop it changes nothing. */
internal fun Modifier.chromeBackdropSource(backdrop: ChromeBackdrop?): Modifier {
    if (backdrop == null) return this
    return this
        .onGloballyPositioned {
            backdrop.sourceOrigin = it.positionInRoot()
            backdrop.sourceSize = it.size
        }
        .drawWithContent {
            val content: ContentDrawScope = this
            backdrop.layer.record { content.drawContent() }
            drawLayer(backdrop.layer)
        }
}

/** A full-size host for content that a band draws over, so the band can blur it. Content and band stay siblings. */
@Composable
internal fun ChromeBackdropSource(backdrop: ChromeBackdrop?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().chromeBackdropSource(backdrop)) { content() }
}
