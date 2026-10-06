package me.foxtails.palustris.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.AppLayoutDirection
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.ui.layout.deviceLayoutDirection
import me.foxtails.palustris.ui.layout.forcingOppositeOf
import me.foxtails.palustris.ui.layout.resolveAgainst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the composition root rule for the effective layout direction.
 *
 * `ConnectedApp` resolves the stored override against the device direction and provides the result.
 * These tests apply the same rule, so they do not need the connected session host. A
 * `CompositionLocal` is not readable from the semantics tree, so the observations are captured
 * inside the composition instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class AppLayoutDirectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemPublishesTheDeviceDirectionUnchanged() {
        val observed = publishEveryCombination()
        assertEquals(AppLayoutDirection.entries.size * 2, observed.size)

        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { device ->
            assertEquals(device, observed[Key(device, AppLayoutDirection.System)]?.published)
        }
    }

    @Test
    fun aStoredOverrideNeverChangesTheDeviceDirectionBelowTheRoot() {
        val observed = publishEveryCombination()
        assertEquals(AppLayoutDirection.entries.size * 2, observed.size)

        // A forced direction never reaches the device direction that a caller below the root reads.
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { device ->
            AppLayoutDirection.entries.forEach { stored ->
                assertEquals(
                    "The device direction must not follow the forced direction",
                    device,
                    observed[Key(device, stored)]?.device,
                )
            }
        }
    }

    @Test
    fun theToggleStoresTheOverrideOppositeTheDeviceDirection() {
        assertEquals(AppLayoutDirection.ForceRtl, AppLayoutDirection.System.forcingOppositeOf(LayoutDirection.Ltr))
        assertEquals(AppLayoutDirection.ForceLtr, AppLayoutDirection.System.forcingOppositeOf(LayoutDirection.Rtl))
    }

    @Test
    fun everyCombinationPublishesTheForcedDirectionOverEitherDeviceDirection() {
        // One composition observes every combination, so no second setContent is needed.
        val observed = publishEveryCombination()
        assertEquals(AppLayoutDirection.entries.size * 2, observed.size)

        // An explicit override publishes its own direction over either device direction.
        assertEquals(
            LayoutDirection.Rtl,
            observed[Key(LayoutDirection.Ltr, AppLayoutDirection.ForceRtl)]?.published,
        )
        assertEquals(
            LayoutDirection.Rtl,
            observed[Key(LayoutDirection.Rtl, AppLayoutDirection.ForceRtl)]?.published,
        )
        assertEquals(
            LayoutDirection.Ltr,
            observed[Key(LayoutDirection.Ltr, AppLayoutDirection.ForceLtr)]?.published,
        )
        assertEquals(
            LayoutDirection.Ltr,
            observed[Key(LayoutDirection.Rtl, AppLayoutDirection.ForceLtr)]?.published,
        )

        // System is the only value that follows the device direction.
        assertEquals(
            LayoutDirection.Ltr,
            observed[Key(LayoutDirection.Ltr, AppLayoutDirection.System)]?.published,
        )
        assertEquals(
            LayoutDirection.Rtl,
            observed[Key(LayoutDirection.Rtl, AppLayoutDirection.System)]?.published,
        )
        assertNotEquals(
            observed[Key(LayoutDirection.Ltr, AppLayoutDirection.System)]?.published,
            observed[Key(LayoutDirection.Rtl, AppLayoutDirection.System)]?.published,
        )
    }

    @Test
    fun aUserWithoutTheStoredKeyKeepsTheDeviceDirection() {
        // AppPreferences defaults the field to System. The repository proves an absent or unknown
        // stored name resolves to this same default, so an upgrade cannot flip a user.
        assertEquals(AppLayoutDirection.System, AppPreferences().layoutDirection)
        assertEquals(
            LayoutDirection.Ltr,
            AppPreferences().layoutDirection.resolveAgainst(LayoutDirection.Ltr),
        )
        assertEquals(
            LayoutDirection.Rtl,
            AppPreferences().layoutDirection.resolveAgainst(LayoutDirection.Rtl),
        )
    }

    /** Applies the composition root rule for every device and stored value in one composition. */
    private fun publishEveryCombination(): Map<Key, Observation> {
        val recorded = mutableMapOf<Key, Observation>()
        compose.activity.setContent {
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { device ->
                CompositionLocalProvider(LocalConfiguration provides configuration(device)) {
                    val base = deviceLayoutDirection()
                    AppLayoutDirection.entries.forEach { stored ->
                        CompositionLocalProvider(LocalLayoutDirection provides stored.resolveAgainst(base)) {
                            recorded[Key(device, stored)] = Observation(
                                published = LocalLayoutDirection.current,
                                device = deviceLayoutDirection(),
                            )
                            Box(Modifier.size(10.dp))
                        }
                    }
                }
            }
        }
        var result: Map<Key, Observation> = emptyMap()
        compose.runOnIdle { result = recorded.toMap() }
        return result
    }

    private data class Key(val device: LayoutDirection, val stored: AppLayoutDirection)

    /**
     * A configuration whose platform layout direction matches [direction]. The platform derives the
     * layout direction from the locale, so only public API is needed here.
     */
    private fun configuration(direction: LayoutDirection): Configuration =
        Configuration(compose.activity.resources.configuration).apply {
            setLocales(
                LocaleList.forLanguageTags(if (direction == LayoutDirection.Rtl) "ar-SA" else "en-US"),
            )
        }

    private data class Observation(
        /** The direction the subtree received. */
        val published: LayoutDirection,
        /** The device direction a caller below the composition root still observes. */
        val device: LayoutDirection,
    )
}
