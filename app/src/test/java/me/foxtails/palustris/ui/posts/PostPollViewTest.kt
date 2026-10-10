package me.foxtails.palustris.ui.posts

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Poll
import me.foxtails.palustris.domain.PollLimits
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.composer.ComposerPollEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostPollViewTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val me = AccountId(connection, "me")

    private class RecordingPolls(var current: Poll?) : PostPollPresentation {
        val votes = mutableListOf<List<Int>>()
        override fun poll(ownedPost: OwnedPost): Poll? = current
        override fun status(id: EntityId): PollVoteStatus = PollVoteStatus.Idle
        override fun vote(ownedPost: OwnedPost, choices: List<Int>) { votes += choices }
        override fun dismissFailure(id: EntityId) = Unit
    }

    private fun poll(multiple: Boolean = false, voted: Boolean = false, expired: Boolean = false) = Poll(
        id = "poll-1",
        options = listOf(PollOption("Cats", 3, picked = voted), PollOption("Dogs", 1)),
        multiple = multiple,
        voted = voted,
        expired = expired,
        votersCount = 4,
    )

    private fun owned(poll: Poll) = OwnedPost(
        me,
        Post(EntityId(connection.origin, "p"), Account(me, "Me", "@me@example.org"), "Vote", 0, Audience.Public, poll = poll),
    )

    private fun show(polls: RecordingPolls) {
        val post = owned(requireNotNull(polls.current))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    CompositionLocalProvider(LocalPostPolls provides polls) { PostPollOptions(post) }
                }
            }
        }
    }

    @Test
    fun anOpenPollOffersChoicesAndSendsTheVote() {
        val polls = RecordingPolls(poll())
        show(polls)

        compose.onNodeWithTag("post_poll_vote").assertIsNotEnabled()
        compose.onNodeWithTag("post_poll_option_1").performClick()
        compose.onNodeWithTag("post_poll_vote").assertIsEnabled().performClick()

        assertEquals(listOf(listOf(1)), polls.votes)
    }

    @Test
    fun aMultipleChoicePollKeepsSeveralChoices() {
        val polls = RecordingPolls(poll(multiple = true))
        show(polls)

        compose.onNodeWithTag("post_poll_option_0").performClick()
        compose.onNodeWithTag("post_poll_option_1").performClick()
        compose.onNodeWithTag("post_poll_vote").performClick()

        assertEquals(listOf(listOf(0, 1)), polls.votes)
    }

    @Test
    fun aVotedOrEndedPollShowsResultsAndNoVoteButton() {
        show(RecordingPolls(poll(voted = true)))
        compose.onNodeWithTag("post_poll_result_0").assertIsDisplayed()
        compose.onNodeWithTag("post_poll_vote").assertDoesNotExist()
    }

    @Test
    fun anExpiredPollDisablesVoting() {
        show(RecordingPolls(poll(expired = true)))
        compose.onNodeWithTag("post_poll_result_1").assertIsDisplayed()
        compose.onNodeWithTag("post_poll_option_0").assertDoesNotExist()
        compose.onNodeWithTag("post_poll_vote").assertDoesNotExist()
    }

    @Test
    fun theComposerEditorEditsChoicesWithinTheServerLimits() {
        val limits = PollLimits(maxOptions = 3, maxOptionLength = 20, minExpirySeconds = 300, maxExpirySeconds = 86_400)
        val state = mutableStateOf(PollRequest(listOf("", ""), expiresInSeconds = 86_400))
        var removed = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ComposerPollEditor(
                        poll = state.value,
                        limits = limits,
                        onChange = { state.value = it },
                        onRemove = { removed = true },
                    )
                }
            }
        }

        compose.onNodeWithTag("composer_poll_choice_0").performTextInput("Cats")
        compose.onNodeWithTag("composer_poll_add_choice").performClick()
        compose.onNodeWithTag("composer_poll_choice_2").assertIsDisplayed()
        compose.onNodeWithTag("composer_poll_add_choice").assertDoesNotExist()
        compose.onNodeWithTag("composer_poll_multiple").performClick()
        compose.onNodeWithTag("composer_poll_duration_3600").performClick()
        compose.onNodeWithTag("composer_poll_remove").performClick()

        assertEquals(listOf("Cats", "", ""), state.value.choices)
        assertEquals(true, state.value.multiple)
        assertEquals(3_600L, state.value.expiresInSeconds)
        assertEquals(true, removed)
    }
}
