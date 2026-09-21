package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.shell.DetailActions
import me.foxtails.palustris.ui.shell.LargePostOrigin
import me.foxtails.palustris.ui.shell.ShellDetailCallbacks
import me.foxtails.palustris.ui.shell.ThreadContract
import me.foxtails.palustris.ui.shell.detailActionsFor
import me.foxtails.palustris.ui.shell.detailThreadOwner
import me.foxtails.palustris.ui.shell.fallbackDetailActions
import me.foxtails.palustris.ui.shell.withResolvedActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DetailActionPolicyTest {
    private val account = AppShellFixtures.account("detail-policy")
    private val post = OwnedPost(
        account.id,
        Post(EntityId(account.id.connection.origin, "post"), account, "text", 0L, Audience.Public),
    )
    private val choice = EmojiChoice("thumbsup", "thumbsup", null)

    private class Recorder {
        var favorite = 0
        var reply = 0
        var reshare = 0
        var bookmark = 0
        var react = 0
        fun fallback() = DetailActions({ favorite++ }, { reply++ }, { reshare++ }, { bookmark++ }, { _, _ -> react++ })
    }

    @Test
    fun profileAndSavedOriginsOwnTheirReaction() {
        var profileReactions = 0
        var bookmarkReactions = 0
        val recorder = Recorder()
        val profile = AppShellFixtures.profileContract(onReact = { _, _ -> profileReactions++ })
        val bookmarks = AppShellFixtures.bookmarks(onReact = { _, _ -> bookmarkReactions++ })

        detailActionsFor(LargePostOrigin.Profile, profile, bookmarks, recorder.fallback()).react(post, choice)
        detailActionsFor(LargePostOrigin.Saved, profile, bookmarks, recorder.fallback()).react(post, choice)

        assertEquals(1, profileReactions)
        assertEquals(1, bookmarkReactions)
        assertEquals(0, recorder.react)
    }

    @Test
    fun otherOriginUsesFallbackForReaction() {
        val recorder = Recorder()
        detailActionsFor(
            origin = LargePostOrigin.Home,
            profile = AppShellFixtures.profileContract(),
            bookmarks = AppShellFixtures.bookmarks(),
            fallback = recorder.fallback(),
        ).react(post, choice)

        assertEquals(1, recorder.react)
    }

    @Test
    fun activeThreadOwnsWideDetailMutations() {
        var favorite = 0
        var reshare = 0
        var bookmark = 0
        var react = 0
        val recorder = Recorder()
        val actions = detailActionsFor(
            origin = LargePostOrigin.Home,
            profile = AppShellFixtures.profileContract(),
            bookmarks = AppShellFixtures.bookmarks(),
            fallback = recorder.fallback(),
            thread = ThreadContract(
                state = null,
                actions = object : ThreadContract.Actions {
                    override fun activate(post: OwnedPost?, enabled: Boolean) = Unit
                    override fun deactivate() = Unit
                    override fun refresh() = Unit
                    override fun continueAcquisition() = Unit
                    override fun favorite(post: OwnedPost) { favorite++ }
                    override fun repost(post: OwnedPost) { reshare++ }
                    override fun bookmark(post: OwnedPost) { bookmark++ }
                    override fun react(post: OwnedPost, choice: EmojiChoice) { react++ }
                },
            ),
        )

        actions.favorite(post)
        actions.reshare(post)
        actions.bookmark(post)
        actions.react(post, choice)
        actions.reply(post)

        assertEquals(1, favorite)
        assertEquals(1, reshare)
        assertEquals(1, bookmark)
        assertEquals(1, react)
        assertEquals(0, recorder.favorite)
        assertEquals(0, recorder.reshare)
        assertEquals(0, recorder.bookmark)
        assertEquals(0, recorder.react)
        assertEquals(1, recorder.reply)
    }

    private fun detailCallbacks(
        onReact: (OwnedPost) -> Unit = {},
        onReply: (OwnedPost) -> Unit = {},
        onReshare: (OwnedPost) -> Unit = {},
        onBookmark: (OwnedPost) -> Unit = {},
        onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
        onOpenProfile: (Account) -> Unit = {},
        onClose: () -> Unit = {},
    ) = ShellDetailCallbacks(
        onClose = onClose,
        onReact = onReact,
        onReply = onReply,
        onReshare = onReshare,
        onBookmark = onBookmark,
        onReaction = onReaction,
        onOpenProfile = onOpenProfile,
        onSearchHashtag = {},
        onOpenReactionBubble = { _, _, _ -> },
        onOpenReactionPicker = {},
        onOpenMedia = {},
        onThreadRefresh = {},
        onThreadContinue = {},
        quoteEnabled = false,
        onQuote = {},
    )

    @Test
    fun fallbackDetailActionsForwardsShellCallbacks() {
        var favorite = 0
        var reply = 0
        var reshare = 0
        var bookmark = 0
        var react = 0
        val callbacks = detailCallbacks(
            onReact = { favorite++ },
            onReply = { reply++ },
            onReshare = { reshare++ },
            onBookmark = { bookmark++ },
            onReaction = { _, _ -> react++ },
        )

        val fallback = fallbackDetailActions(callbacks)
        fallback.favorite(post)
        fallback.reply(post)
        fallback.reshare(post)
        fallback.bookmark(post)
        fallback.react(post, choice)

        assertEquals(1, favorite)
        assertEquals(1, reply)
        assertEquals(1, reshare)
        assertEquals(1, bookmark)
        assertEquals(1, react)
    }

    @Test
    fun resolvedCallbacksKeepNonMutationHandlers() {
        var closes = 0
        var profiles = 0
        val base = detailCallbacks(onClose = { closes++ }, onOpenProfile = { profiles++ })
        val resolved = DetailActions({ }, { }, { }, { }, { _, _ -> })

        val combined = base.withResolvedActions(resolved)
        combined.onClose()
        combined.onOpenProfile(account)

        assertEquals(1, closes)
        assertEquals(1, profiles)
    }

    @Test
    fun threadOwnerAppliesOnlyToWideDetailWithSelection() {
        val thread = AppShellFixtures.thread()

        assertNull(detailThreadOwner(hasSelection = false, origin = LargePostOrigin.Home, thread = thread))
        assertNull(detailThreadOwner(hasSelection = true, origin = LargePostOrigin.Other, thread = thread))
        assertSame(thread, detailThreadOwner(hasSelection = true, origin = LargePostOrigin.Home, thread = thread))
    }

    @Test
    fun compactDetailKeepsFallbackMutations() {
        var favorite = 0
        var reshare = 0
        var bookmark = 0
        var react = 0
        val base = detailCallbacks(
            onReact = { favorite++ },
            onReshare = { reshare++ },
            onBookmark = { bookmark++ },
            onReaction = { _, _ -> react++ },
        )
        // Compact detail never passes the thread owner.
        val detail = detailActionsFor(
            origin = LargePostOrigin.Home,
            profile = AppShellFixtures.profileContract(),
            bookmarks = AppShellFixtures.bookmarks(),
            fallback = fallbackDetailActions(base),
        )
        val resolved = base.withResolvedActions(detail)

        resolved.onReact(post)
        resolved.onReshare(post)
        resolved.onBookmark(post)
        resolved.onReaction(post, choice)

        assertEquals(1, favorite)
        assertEquals(1, reshare)
        assertEquals(1, bookmark)
        assertEquals(1, react)
    }

    @Test
    fun compactDetailRoutesProfileReactionToProfile() {
        var fallbackReactions = 0
        var profileReactions = 0
        val base = detailCallbacks(onReaction = { _, _ -> fallbackReactions++ })
        val detail = detailActionsFor(
            origin = LargePostOrigin.Profile,
            profile = AppShellFixtures.profileContract(onReact = { _, _ -> profileReactions++ }),
            bookmarks = AppShellFixtures.bookmarks(),
            fallback = fallbackDetailActions(base),
        )
        val resolved = base.withResolvedActions(detail)

        resolved.onReaction(post, choice)

        assertEquals(1, profileReactions)
        assertEquals(0, fallbackReactions)
    }

    @Test
    fun largeDetailRoutesMutationsThroughThread() {
        var fallbackBookmarks = 0
        var threadBookmarks = 0
        var replies = 0
        val base = detailCallbacks(
            onReply = { replies++ },
            onBookmark = { fallbackBookmarks++ },
        )
        val thread = AppShellFixtures.thread(onBookmark = { threadBookmarks++ })
        val detail = detailActionsFor(
            origin = LargePostOrigin.Home,
            profile = AppShellFixtures.profileContract(),
            bookmarks = AppShellFixtures.bookmarks(),
            fallback = fallbackDetailActions(base),
            thread = detailThreadOwner(hasSelection = true, origin = LargePostOrigin.Home, thread = thread),
        )
        val resolved = base.withResolvedActions(detail)

        resolved.onBookmark(post)
        resolved.onReply(post)

        assertEquals(1, threadBookmarks)
        assertEquals(0, fallbackBookmarks)
        assertEquals(1, replies)
    }
}
