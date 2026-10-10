package me.foxtails.palustris.ui.posts

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.canTranslateTo
import me.foxtails.palustris.domain.isOwnedBy

/**
 * Which rows the share sheet shows for one post. [own] switches the card to the own-post variant.
 * A row shows only when its capability is Supported, so a server that lacks a feature never
 * offers it.
 */
internal data class PostShareRows(
    val own: Boolean,
    val edit: Boolean,
    val delete: Boolean,
    val translate: Boolean,
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
    )
}
