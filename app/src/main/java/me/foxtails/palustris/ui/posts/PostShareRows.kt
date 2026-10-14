package me.foxtails.palustris.ui.posts

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ReportRoute
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.canTranslateTo
import me.foxtails.palustris.domain.isOwnedBy

/**
 * Which rows the share sheet shows for one post. [own] switches the card to the own-post variant.
 * A row shows only when its capability is Supported, so a server that lacks a feature never
 * offers it. [report] follows the probed report route: it stays hidden while the Pixelfed
 * `v1.1/report` field mapping is unproven rather than sending guessed fields.
 */
internal data class PostShareRows(
    val own: Boolean,
    val edit: Boolean,
    val delete: Boolean,
    val translate: Boolean,
    val report: Boolean = true,
)

internal fun postShareRows(
    post: Post,
    account: AccountId,
    capabilities: ServerCapabilities,
    targetLanguage: String,
): PostShareRows {
    val own = post.isOwnedBy(account)
    return PostShareRows(
        own = own,
        edit = own && capabilities.ownPosts.edit == CapabilityStatus.Supported,
        delete = own && capabilities.ownPosts.delete == CapabilityStatus.Supported,
        translate = post.canTranslateTo(capabilities.translation, targetLanguage),
        report = capabilities.reportRoute == ReportRoute.V1Reports,
    )
}
