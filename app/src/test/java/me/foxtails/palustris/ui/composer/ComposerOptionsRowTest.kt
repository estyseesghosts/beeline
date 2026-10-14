package me.foxtails.palustris.ui.composer

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.LocalOnlyRoute
import me.foxtails.palustris.domain.PostOptions
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.QuoteUnavailable
import me.foxtails.palustris.domain.ScheduleRoute
import me.foxtails.palustris.domain.ScheduledPost
import me.foxtails.palustris.domain.SchedulingCapability
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.posts.QuotePlaceholderCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerOptionsRowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun show(
        posting: PostingCapabilities,
        audience: Audience = Audience.Public,
        entryCount: Int = 1,
        options: PostOptions = PostOptions(),
        onOptions: (PostOptions) -> Unit = {},
    ) {
        val state = mutableStateOf(options)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ComposerOptionsRow(
                        options = state.value,
                        audience = audience,
                        posting = posting,
                        entryCount = entryCount,
                        onLocalOnly = { state.value = state.value.copy(localOnly = it); onOptions(state.value) },
                        onQuotePolicy = { state.value = state.value.copy(quotePolicy = it); onOptions(state.value) },
                        onScheduledAt = { state.value = state.value.copy(scheduledAtEpochMillis = it); onOptions(state.value) },
                        onContentType = { state.value = state.value.copy(contentType = it); onOptions(state.value) },
                    )
                }
            }
        }
    }

    @Test
    fun aServerThatOffersNothingShowsNoRow() {
        show(PostingCapabilities())

        compose.onNodeWithTag("composer_option_local_only").assertDoesNotExist()
        compose.onNodeWithTag("composer_option_quote_policy").assertDoesNotExist()
        compose.onNodeWithTag("composer_option_schedule").assertDoesNotExist()
        compose.onNodeWithTag("composer_option_content_type").assertDoesNotExist()
    }

    @Test
    fun theFormatSelectorStaysHiddenWithoutPostFormats() {
        show(PostingCapabilities(scheduling = SchedulingCapability(ScheduleRoute.Mastodon, 300L)))

        compose.onNodeWithTag("composer_option_schedule").assertIsDisplayed()
        compose.onNodeWithTag("composer_option_content_type").assertDoesNotExist()
    }

    @Test
    fun theFormatSelectorDefaultsToOmittingContentType() {
        var latest = PostOptions()
        show(
            PostingCapabilities(postFormats = listOf("text/plain", "text/markdown")),
            onOptions = { latest = it },
        )

        compose.onNodeWithTag("composer_option_content_type").assertIsDisplayed()
        compose.onNodeWithText("Default").assertIsDisplayed()

        assertEquals(null, latest.contentType)
    }

    @Test
    fun theFormatMenuSetsAContentType() {
        var latest = PostOptions()
        show(
            PostingCapabilities(postFormats = listOf("text/plain", "text/markdown")),
            onOptions = { latest = it },
        )

        compose.onNodeWithTag("composer_option_content_type").performClick()
        compose.onNodeWithText("text/markdown").performClick()

        assertEquals("text/markdown", latest.contentType)
    }

    @Test
    fun localOnlyTogglesWhereTheServerOffersIt() {
        var latest = PostOptions()
        show(PostingCapabilities(localOnly = LocalOnlyRoute.MisskeyFlag), onOptions = { latest = it })

        compose.onNodeWithTag("composer_option_local_only").assertIsDisplayed().performClick()

        assertTrue(latest.localOnly)
    }

    @Test
    fun pleromaLocalOnlyNeedsAPublicPost() {
        show(PostingCapabilities(localOnly = LocalOnlyRoute.PleromaVisibility), audience = Audience.Followers)

        compose.onNodeWithTag("composer_option_local_only").assertIsNotEnabled()
    }

    @Test
    fun theQuotePolicyMenuSetsAPolicy() {
        var latest = PostOptions()
        show(PostingCapabilities(quoteApprovalPolicy = true), onOptions = { latest = it })

        compose.onNodeWithTag("composer_option_quote_policy").performClick()
        compose.onNodeWithText("Nobody").performClick()

        assertEquals(me.foxtails.palustris.domain.QuoteApprovalPolicy.Nobody, latest.quotePolicy)
    }

    @Test
    fun aThreadCannotBeScheduled() {
        val scheduling = SchedulingCapability(ScheduleRoute.Mastodon, 300L)
        show(PostingCapabilities(scheduling = scheduling), entryCount = 1)
        compose.onNodeWithTag("composer_option_schedule").assertIsEnabled()
    }

    @Test
    fun aThreadOfSeveralPostsDisablesScheduling() {
        show(PostingCapabilities(scheduling = SchedulingCapability(ScheduleRoute.Mastodon, 300L)), entryCount = 2)
        compose.onNodeWithTag("composer_option_schedule").assertIsNotEnabled()
    }

    @Test
    fun theSchedulePickerRefusesATimeInsideTheLeadTime() {
        val soon = System.currentTimeMillis() + 60_000L
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ComposerSchedulePicker(initial = soon, minLeadSeconds = 300L, nowMillis = System::currentTimeMillis, onDismiss = {}, onConfirm = {})
                }
            }
        }
        compose.onNodeWithText("Next").performClick()

        compose.onNodeWithTag("composer_schedule_too_soon").assertExists()
        compose.onNodeWithTag("composer_schedule_confirm").assertIsNotEnabled()
    }

    @Test
    fun theSchedulePickerAcceptsATimeAheadOfTheLeadTime() {
        val later = System.currentTimeMillis() + 3_600_000L
        var confirmed: Long? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ComposerSchedulePicker(initial = later, minLeadSeconds = 300L, nowMillis = System::currentTimeMillis, onDismiss = {}, onConfirm = { confirmed = it })
                }
            }
        }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithTag("composer_schedule_too_soon").assertDoesNotExist()
        compose.onNodeWithTag("composer_schedule_confirm").assertIsEnabled().performClick()

        // The picker keeps the minute and the day, and drops the seconds.
        assertTrue(confirmed != null && kotlin.math.abs(confirmed!! - later) < 60_000L)
    }

    @Test
    fun theScheduledTabListsItemsAndReportsTaps() {
        var cancelled: ScheduledPost? = null
        val item = ScheduledPost("s1", System.currentTimeMillis() + 3_600_000L, "post later")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ScheduledList(
                        ScheduledTab(listOf(item), loading = false, error = null, minLeadSeconds = 300L, onRefresh = {}, onCancel = { cancelled = it }, onReschedule = { _, _ -> }),
                    )
                }
            }
        }

        compose.onNodeWithText("post later").assertIsDisplayed()
        compose.onNodeWithTag("scheduled_cancel").performClick()
        compose.onNodeWithTag("scheduled_cancel_confirm").performClick()

        assertEquals(item, cancelled)
    }

    @Test
    fun aQuotePlaceholderSaysWhyAndOffersAFetchOnlyForAReference() {
        var opened = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    androidx.compose.foundation.layout.Column {
                        QuotePlaceholderCard(QuoteUnavailable.Pending, onOpen = null)
                    }
                }
            }
        }
        compose.onNodeWithText("This quote is waiting for the author to approve it.").assertIsDisplayed()
        compose.onNodeWithText("View quoted post").assertDoesNotExist()

        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme { QuotePlaceholderCard(QuoteUnavailable.Unloaded, onOpen = { opened = true }) }
            }
        }
        compose.onNodeWithText("View quoted post").performClick()
        assertTrue(opened)
        assertFalse(compose.activity.isFinishing)
    }
}
