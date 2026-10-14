package me.foxtails.palustris.domain

/**
 * Which server fork serves a recommendations feed. Only DVD Sharkey speaks the v1 contract;
 * a second fork adds a value here while [Timeline.Recommended] stays stable.
 * Responsibility: name the fork behind a [RecommendationCapability]. Lifetime: static, none.
 */
enum class RecommendationFlavor {
    DVD_SHARKEY,
}
