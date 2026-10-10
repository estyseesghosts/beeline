package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Poll
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.ui.UiStrings

/** Where one poll's vote stands. */
sealed interface PollVoteStatus {
    data object Idle : PollVoteStatus
    data object Sending : PollVoteStatus
    data class Failed(val message: String) : PollVoteStatus
}

/** Read and drive poll votes from a post row without holding a source or a scope. */
interface PostPollPresentation {
    /** The poll to show for [ownedPost]: a vote made in this session wins over an older copy of the post. */
    fun poll(ownedPost: OwnedPost): Poll?
    fun status(id: EntityId): PollVoteStatus
    fun vote(ownedPost: OwnedPost, choices: List<Int>)
    fun dismissFailure(id: EntityId)
}

internal val LocalPostPolls = staticCompositionLocalOf<PostPollPresentation?> { null }

/**
 * Owns the poll votes of one connected session and releases them with it. A vote shows at once,
 * then the server's answer replaces it. Every surface reads the same entries, so a poll voted on
 * in one list reads as voted in every other list. The entries live only for the session.
 */
class PostPollOwner(
    private val accountId: AccountId,
    private val sessionRevision: Long,
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val uiStrings: UiStrings,
) : PostPollPresentation {
    private val polls = mutableStateMapOf<EntityId, Poll>()
    private val statuses = mutableStateMapOf<EntityId, PollVoteStatus>()
    private val jobs = mutableMapOf<EntityId, Job>()
    private var retired = false

    override fun poll(ownedPost: OwnedPost): Poll? = mergedPoll(ownedPost.post.poll, polls[ownedPost.effectiveTargetId()])

    override fun status(id: EntityId): PollVoteStatus = statuses[id] ?: PollVoteStatus.Idle

    override fun vote(ownedPost: OwnedPost, choices: List<Int>) {
        if (retired || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        val target = ownedPost.effectiveTargetId()
        val current = poll(ownedPost) ?: return
        if (current.id.isBlank() || !current.open || statuses[target] == PollVoteStatus.Sending) return
        val picked = choices.distinct().sorted()
        val valid = picked.isNotEmpty() && picked.all { it in current.options.indices } && (current.multiple || picked.size == 1)
        if (!valid) return
        val before = polls[target]
        polls[target] = current.withVote(picked)
        statuses[target] = PollVoteStatus.Sending
        jobs[target] = scope.launch {
            try {
                val result = source.votePoll(target, current.id, picked)
                if (!retired) {
                    polls[target] = result
                    statuses.remove(target)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!retired) {
                    // Put back what the post showed before the vote, and say why it failed.
                    if (before == null) polls.remove(target) else polls[target] = before
                    statuses[target] = PollVoteStatus.Failed(uiStrings.sourceError(error))
                }
            } finally {
                jobs.remove(target)
            }
        }
    }

    override fun dismissFailure(id: EntityId) {
        if (statuses[id] is PollVoteStatus.Failed) statuses.remove(id)
    }

    /** Retires the owner with its connected entry. Later votes are ignored and answers dropped. */
    fun retire() {
        retired = true
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        polls.clear()
        statuses.clear()
    }
}

/**
 * The poll a row shows. A session vote stands in for the post's own copy while the copy has not
 * seen that vote; a copy that shows the account voted, with at least as many votes, is newer.
 */
internal fun mergedPoll(base: Poll?, session: Poll?): Poll? = when {
    base == null -> null
    session == null || session.id != base.id -> base
    base.voted && base.totalVotes >= session.totalVotes -> base
    else -> session
}
