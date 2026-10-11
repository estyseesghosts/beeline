package me.foxtails.palustris.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarsPolicyTest {
    private val background = Color(0xFF101412)

    @Test
    fun barIsTransparentOnEveryLevelWithGestureNavigation() {
        for (sdk in listOf(29, 30, 33, 35, 37)) {
            assertEquals(
                NavigationBarPolicy.Transparent,
                navigationBarPolicy(sdk, threeButtonNavigation = false, background, mediaViewerOpen = false),
            )
        }
    }

    @Test
    fun threeButtonNavigationIsTransparentFromAndroid11() {
        for (sdk in listOf(30, 34, 36)) {
            assertEquals(
                NavigationBarPolicy.Transparent,
                navigationBarPolicy(sdk, threeButtonNavigation = true, background, mediaViewerOpen = false),
            )
        }
    }

    @Test
    fun android10WithThreeButtonsKeepsAnOpaqueBarInTheBackgroundColor() {
        assertEquals(
            NavigationBarPolicy.Opaque(background),
            navigationBarPolicy(29, threeButtonNavigation = true, background, mediaViewerOpen = false),
        )
    }

    @Test
    fun mediaViewerIsBlackOnEveryLevel() {
        assertEquals(NavigationBarPolicy.Black, navigationBarPolicy(29, true, background, mediaViewerOpen = true))
        assertEquals(NavigationBarPolicy.Black, navigationBarPolicy(35, false, background, mediaViewerOpen = true))
    }
}
