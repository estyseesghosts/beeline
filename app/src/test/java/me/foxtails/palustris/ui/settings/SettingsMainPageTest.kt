package me.foxtails.palustris.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.InMemoryAppPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Settings main page lists every page once, and each row opens its own page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SettingsMainPageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val rows = listOf(
        "settings_display" to SettingsRoute.Display,
        "settings_language" to SettingsRoute.Language,
        "settings_timeline_media" to SettingsRoute.TimelineMedia,
        "settings_posting" to SettingsRoute.Posting,
        "settings_content_warnings" to SettingsRoute.ContentWarnings,
        "settings_blocks_mutes" to SettingsRoute.PrivacyAccounts,
        "settings_privacy" to SettingsRoute.Privacy,
        "settings_notifications" to SettingsRoute.Notifications,
    )

    @Test
    fun everyRowOpensItsOwnPage() {
        val opened = mutableListOf<SettingsRoute>()
        compose.setContent { SettingsScreen(AppPreferences(), onRoute = { opened += it }) }

        rows.forEach { (tag, _) -> compose.onNodeWithTag(tag).performScrollTo().performClick() }

        assertEquals(rows.map { it.second }, opened)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h480dp-420dpi")
    fun theLastRowStaysReachableOnAShortScreenWithALargeFont() {
        compose.activity.resources.configuration.fontScale = 2.0f
        compose.activity.runOnUiThread {
            compose.activity.setContent { SettingsScreen(AppPreferences(), onRoute = {}) }
        }

        compose.onNodeWithTag(rows.last().first).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun timelineAndMediaOpensFromTheMainPageAndReturnsToIt() {
        var route by mutableStateOf<SettingsRoute>(SettingsRoute.Main)
        compose.setContent {
            SettingsHost(
                state = AppPreferencesState(loaded = true),
                route = route,
                onRoute = { route = it },
                onBack = {},
            )
        }

        compose.onNodeWithTag("settings_timeline_media").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(SettingsRoute.TimelineMedia, route)
        compose.onNodeWithText("Autoplay videos").assertIsDisplayed()
        compose.onNodeWithText("Combine related hashtags").assertIsDisplayed()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertEquals(SettingsRoute.Main, route)
    }

    @Test
    fun theTrackingParameterRowIsASwitchThatShowsItsState() {
        var clean by mutableStateOf(false)
        compose.setContent {
            Column { PrivacySettingsScreen(clean, { clean = it }) }
        }

        compose.onNodeWithTag("tracking_cleanup").assertIsOff().performClick()

        compose.onNodeWithTag("tracking_cleanup").assertIsOn()
        assertEquals(true, clean)
    }

    @Test
    fun theFavouriteEmojiCommandStoresANormalizedValueForItsAccountOnly() {
        val accountA = AccountId(Connection("https://a.example", Protocol.MISSKEY), "a")
        val accountB = AccountId(Connection("https://b.example", Protocol.MISSKEY), "b")
        val repository = InMemoryPostPreferencesRepository()
        val model = SettingsViewModel(InMemoryAppPreferencesRepository(), repository)

        model.setPostFavouriteEmoji(accountA, " ⭐ ")
        model.setPostFavouriteEmoji(accountB, "   ")
        compose.waitForIdle()

        assertEquals("⭐", runBlocking { repository.observe(accountA).first() }.favouriteEmoji)
        // A blank value is not a reaction, so the default stays.
        assertEquals(DEFAULT_FAVOURITE_EMOJI, runBlocking { repository.observe(accountB).first() }.favouriteEmoji)
    }

    @Test
    fun timelineAndMediaSwitchesShowAndReportTheirState() {
        var autoplay by mutableStateOf(true)
        var combine by mutableStateOf(false)
        compose.setContent {
            TimelineMediaSettingsScreen(
                preferences = AppPreferences(autoplayVideos = autoplay, combineRelatedHashtags = combine),
                onAutoplayVideos = { autoplay = it },
                onCombineRelatedHashtags = { combine = it },
            )
        }

        compose.onNodeWithTag("autoplay_videos").assertIsOn().performClick()
        compose.onNodeWithTag("combine_related_hashtags").assertIsOff().performClick()

        compose.onNodeWithTag("autoplay_videos").assertIsOff()
        compose.onNodeWithTag("combine_related_hashtags").assertIsOn()
        assertEquals(false, autoplay)
        assertEquals(true, combine)
    }
}
