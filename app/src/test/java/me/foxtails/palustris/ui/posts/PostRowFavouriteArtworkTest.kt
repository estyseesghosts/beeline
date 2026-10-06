package me.foxtails.palustris.ui.posts

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.FavouriteArtworkStyle
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.AppIcons
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostRowFavouriteArtworkTest {
    @Test
    fun stylesUseSelectedAndUnselectedArtwork() {
        val unselected = ownedPost(false)
        val selected = ownedPost(true)

        assertEquals(AppIcons.HollowStar, favouriteIconFor(unselected, FavouriteArtworkStyle.Star))
        assertEquals(AppIcons.FilledStar, favouriteIconFor(selected, FavouriteArtworkStyle.Star))
        assertEquals(AppIcons.HollowHeart, favouriteIconFor(unselected, FavouriteArtworkStyle.Heart))
        assertEquals(AppIcons.FilledHeart, favouriteIconFor(selected, FavouriteArtworkStyle.Heart))
    }

    @Test
    fun reactionOnlySelectionsUseSelectedArtwork() {
        val reaction = ownedPost(false, myReaction = "👍")
        val multipleReactions = ownedPost(false, selectedReactions = listOf(EmojiChoice("👍", "👍", null)))

        assertEquals(AppIcons.FilledHeart, favouriteIconFor(reaction, FavouriteArtworkStyle.Heart))
        assertEquals(AppIcons.FilledStar, favouriteIconFor(reaction, FavouriteArtworkStyle.Star))
        assertEquals(AppIcons.FilledHeart, favouriteIconFor(multipleReactions, FavouriteArtworkStyle.Heart))
        assertEquals(AppIcons.FilledStar, favouriteIconFor(multipleReactions, FavouriteArtworkStyle.Star))
        assertEquals(setOf(me.foxtails.palustris.domain.PostAction.Favorite, me.foxtails.palustris.domain.PostAction.React), actionsForPost(setOf(me.foxtails.palustris.domain.PostAction.Favorite, me.foxtails.palustris.domain.PostAction.React), reaction.post))
    }

    @Test
    fun missingStyleUsesHeartFallback() {
        assertEquals(FavouriteArtworkStyle.Heart, PostRowPresentation(emptySet()).favouriteArtworkStyle)
    }

    private fun ownedPost(
        selected: Boolean,
        myReaction: String? = null,
        selectedReactions: List<EmojiChoice> = emptyList(),
    ): OwnedPost {
        val connection = Connection("https://example.test", Protocol.MISSKEY)
        val accountId = AccountId(connection, "account")
        val account = Account(accountId, "Account", "account")
        return OwnedPost(accountId, Post(EntityId("connection", "post"), account, "text", 0L, Audience.Public, favourited = selected, myReaction = myReaction, selectedReactions = selectedReactions))
    }
}
