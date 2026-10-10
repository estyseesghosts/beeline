package me.foxtails.palustris.domain

/**
 * Why a quote is not shown as a card. The server knew about a quote and did not hand over its post,
 * so the row shows a short placeholder instead of nothing. [Unloaded] covers a quote that could not be
 * fetched, such as a reference with only an ID or an address.
 */
enum class QuoteUnavailable {
    Pending,
    Rejected,
    Revoked,
    Deleted,
    Unauthorized,
    BlockedAccount,
    BlockedDomain,
    MutedAccount,
    Hidden,
    Unloaded,
}

/** What the viewer's own account may do about quoting a post, from Mastodon 4.5's `quote_approval.current_user`. */
enum class QuoteApproval { Automatic, Manual, Denied, Unknown }

/** Who may quote a post, as Mastodon 4.5's `quote_approval_policy` names it. */
enum class QuoteApprovalPolicy(val wire: String) { Public("public"), Followers("followers"), Nobody("nobody") }

/** True when the server returned this post with any trace of a quote: the post, a reason, an ID or an address. */
fun Post.carriesQuote(): Boolean = quote != null || quoteUnavailable != null || quoteId != null || quoteUrl != null
