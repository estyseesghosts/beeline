package me.foxtails.palustris.ui.emoji

import androidx.compose.runtime.staticCompositionLocalOf
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.PostMention

/** What the session knows about the account a mention names. */
sealed interface MentionProfile {
    /** A request is in flight. The bubble shows the handle. */
    data object Pending : MentionProfile

    /** The account, with the display name and the avatar. */
    data class Resolved(val account: Account) : MentionProfile

    /** The lookup failed. The session does not ask again; the bubble keeps showing the handle. */
    data object Failed : MentionProfile
}

/** Read mention profiles and ask for one from a post row without holding a source or a scope. */
interface MentionProfilePresentation {
    /** The known state of [mention], or null when nothing asked for it yet. */
    fun profile(mention: PostMention): MentionProfile?

    /** Asks for [mention] once. A handle that is known, pending or failed costs no request. */
    fun request(mention: PostMention)

    /** Records accounts the session already holds, so their mentions cost no request. */
    fun seed(accounts: List<Account>)
}

/** The inert default for previews and tests: nothing resolves and nothing is requested. */
internal object NoMentionProfiles : MentionProfilePresentation {
    override fun profile(mention: PostMention): MentionProfile? = null

    override fun request(mention: PostMention) = Unit

    override fun seed(accounts: List<Account>) = Unit
}

internal val LocalMentionProfiles = staticCompositionLocalOf<MentionProfilePresentation> { NoMentionProfiles }

/** The label of a resolved mention: the display name without custom emoji shortcodes, or null when nothing is left. */
internal fun mentionDisplayName(account: Account): String? =
    account.displayName.replace(Regex(":[A-Za-z0-9_]+:"), "").replace(Regex("\\s+"), " ").trim().ifBlank { null }
