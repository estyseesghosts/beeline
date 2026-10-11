package me.foxtails.palustris.domain

/** Selects a visibility once for a composition and never overwrites an explicit choice. */
object PostingVisibilityPolicy {
    fun forNewPost(preferences: PostPreferences, capabilities: ServerCapabilities): Audience =
        supportedOrThrow(preferences.defaultAudience, capabilities)

    /**
     * A reply or quote takes the audience of the post it answers. `repliesUnlisted` narrows a public
     * target to Unlisted and never widens another audience.
     */
    fun forReply(preferences: PostPreferences, capabilities: ServerCapabilities, context: Audience? = null): Audience {
        val requested = when {
            context == null -> if (preferences.repliesUnlisted) Audience.Unlisted else preferences.defaultAudience
            context == Audience.Public && preferences.repliesUnlisted -> Audience.Unlisted
            else -> context
        }
        return supportedOrThrow(requested, capabilities)
    }

    fun validateExplicit(audience: Audience, capabilities: ServerCapabilities): Audience =
        supportedOrThrow(audience, capabilities)

    private fun supportedOrThrow(audience: Audience, capabilities: ServerCapabilities): Audience {
        if (audience !in capabilities.audiences) throw SourceError.Unsupported("audience:${audience.name}")
        return audience
    }
}
