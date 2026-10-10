@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.foxtails.palustris.ui.posts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.data.auth.toDraft
import me.foxtails.palustris.data.auth.toJson
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Poll
import me.foxtails.palustris.domain.PollLimits
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.PollProblem
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftEntry
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.UiStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostPollOwnerTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val me = AccountId(connection, "me")
    private val limits = PollLimits(maxOptions = 4, maxOptionLength = 10, minExpirySeconds = 300, maxExpirySeconds = 86_400)

    private fun poll(multiple: Boolean = false, voted: Boolean = false, expired: Boolean = false) = Poll(
        id = "poll-1",
        options = listOf(PollOption("Cats", 3), PollOption("Dogs", 2)),
        multiple = multiple,
        expired = expired,
        voted = voted,
        votersCount = 5,
    )

    private fun owned(poll: Poll?, revision: Long = 1L) = OwnedPost(
        me,
        Post(
            id = EntityId(connection.origin, "p"),
            author = Account(me, "Me", "@me@example.org"),
            text = "Vote",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            poll = poll,
        ),
        sessionRevision = revision,
    )

    private class FakeSource : SocialSource {
        override val capabilities = ServerCapabilities()
        val votes = mutableListOf<Triple<EntityId, String, List<Int>>>()
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun votePoll(postId: EntityId, pollId: String, choices: List<Int>): Poll {
            votes += Triple(postId, pollId, choices)
            gate?.await()
            failure?.let { throw it }
            return Poll(
                id = pollId,
                options = listOf(PollOption("Cats", 40, picked = 0 in choices), PollOption("Dogs", 2, picked = 1 in choices)),
                voted = true,
                votersCount = 42,
            )
        }
    }

    @Test
    fun aVoteShowsAtOnceThenTheServerAnswerReplacesIt() = runTest {
        val source = FakeSource().apply { gate = CompletableDeferred() }
        val owner = PostPollOwner(me, 1L, source, this, UiStrings.Default)
        val post = owned(poll())

        owner.vote(post, listOf(0))
        advanceUntilIdle()

        val optimistic = requireNotNull(owner.poll(post))
        assertTrue(optimistic.voted)
        assertEquals(listOf(0), optimistic.ownVotes)
        assertEquals(4, optimistic.options[0].votes)
        assertEquals(PollVoteStatus.Sending, owner.status(post.post.id))

        source.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(40, requireNotNull(owner.poll(post)).options[0].votes)
        assertEquals(PollVoteStatus.Idle, owner.status(post.post.id))
        assertEquals(listOf(Triple(post.post.id, "poll-1", listOf(0))), source.votes)
    }

    @Test
    fun aFailedVoteRestoresThePollAndNamesTheError() = runTest {
        val source = FakeSource().apply { failure = SourceError.NetworkUnavailable }
        val owner = PostPollOwner(me, 1L, source, this, UiStrings.Default)
        val post = owned(poll())

        owner.vote(post, listOf(1))
        advanceUntilIdle()

        val shown = requireNotNull(owner.poll(post))
        assertFalse(shown.voted)
        assertEquals(2, shown.options[1].votes)
        assertTrue(owner.status(post.post.id) is PollVoteStatus.Failed)

        owner.dismissFailure(post.post.id)
        assertEquals(PollVoteStatus.Idle, owner.status(post.post.id))
    }

    @Test
    fun theOwnerRefusesVotesThatCouldNotSucceed() = runTest {
        val source = FakeSource()
        val owner = PostPollOwner(me, 1L, source, this, UiStrings.Default)

        owner.vote(owned(poll(voted = true)), listOf(0))
        owner.vote(owned(poll(expired = true)), listOf(0))
        owner.vote(owned(poll()), emptyList())
        owner.vote(owned(poll()), listOf(0, 1))
        owner.vote(owned(poll()), listOf(5))
        owner.vote(owned(poll(), revision = 2L), listOf(0))
        owner.vote(owned(null), listOf(0))
        advanceUntilIdle()

        assertTrue(source.votes.isEmpty())
    }

    @Test
    fun aMultipleChoicePollTakesSeveralChoicesInOrder() = runTest {
        val source = FakeSource()
        val owner = PostPollOwner(me, 1L, source, this, UiStrings.Default)

        owner.vote(owned(poll(multiple = true)), listOf(1, 0, 1))
        advanceUntilIdle()

        assertEquals(listOf(0, 1), source.votes.single().third)
    }

    @Test
    fun aRetiredOwnerIgnoresVotes() = runTest {
        val source = FakeSource()
        val owner = PostPollOwner(me, 1L, source, this, UiStrings.Default)
        owner.retire()

        owner.vote(owned(poll()), listOf(0))
        advanceUntilIdle()

        assertTrue(source.votes.isEmpty())
    }

    @Test
    fun aFresherCopyOfThePostWinsOverTheSessionVote() {
        val session = poll().withVote(listOf(0))
        val fresher = session.copy(options = session.options.map { it.copy(votes = it.votes + 10) })

        assertEquals(fresher, mergedPoll(fresher, session))
        assertEquals(session, mergedPoll(poll(), session))
        assertNull(mergedPoll(null, session))
        assertEquals(poll(), mergedPoll(poll(), session.copy(id = "other")))
    }

    @Test
    fun pollRequestsAreCheckedAgainstTheServerLimits() {
        fun problem(request: PollRequest, to: PollLimits? = limits) = request.problem(to)

        assertNull(problem(PollRequest(listOf("a", "b"), expiresInSeconds = 600)))
        assertEquals(PollProblem.Unsupported, problem(PollRequest(listOf("a", "b")), null))
        assertEquals(PollProblem.TooFewOptions, problem(PollRequest(listOf("a", " "))))
        assertEquals(PollProblem.TooManyOptions, problem(PollRequest(listOf("a", "b", "c", "d", "e"))))
        assertEquals(PollProblem.OptionTooLong, problem(PollRequest(listOf("a", "x".repeat(11)))))
        assertEquals(PollProblem.DuplicateOptions, problem(PollRequest(listOf("Cats", "cats"))))
        assertEquals(PollProblem.ExpiryOutOfRange, problem(PollRequest(listOf("a", "b"), expiresInSeconds = 60)))
        assertEquals(PollProblem.ExpiryOutOfRange, problem(PollRequest(listOf("a", "b"), expiresInSeconds = 100_000)))
        assertEquals(listOf("a", "b"), PollRequest(listOf(" a ", "", "b")).cleaned().choices)
    }

    @Test
    fun draftsKeepThePollOfEveryEntryAcrossStorage() {
        val draft = PostDraft(
            id = "d1",
            accountId = me,
            text = "first",
            poll = PollRequest(listOf("a", "b"), multiple = true, expiresInSeconds = 3600, hideTotals = true),
            followUps = listOf(PostDraftEntry(id = "e2", text = "second", poll = PollRequest(listOf("c", "d")))),
        )

        val restored = draft.toJson().toDraft()

        assertEquals(draft.poll, restored.poll)
        assertEquals(draft.followUps.single().poll, restored.followUps.single().poll)
        assertEquals(2, restored.allPolls.size)
    }

    @Test
    fun aDraftSavedBeforePollsHadARelativeExpiryReadsWithoutOne() {
        val json = PostDraft(id = "d2", accountId = me, text = "old", poll = PollRequest(listOf("a", "b"))).toJson()
        json.getJSONObject("poll").remove("expiresInSeconds")
        json.getJSONObject("poll").put("expiresAt", 1_789_000_000_000L)

        assertNull(json.toDraft().poll?.expiresInSeconds)
    }
}
