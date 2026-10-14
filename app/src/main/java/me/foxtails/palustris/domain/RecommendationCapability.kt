package me.foxtails.palustris.domain

/**
 * Availability of the recommendations feed on one account's server. [status] keeps the
 * standard meaning: Unknown before a probe reports, Supported only with full evidence,
 * Denied when the account disabled it, Unsupported on other hosts, TemporarilyUnavailable
 * on transient failure. Never infer one from another.
 * Responsibility: flavor-gated availability for [Timeline.Recommended]. Lifetime: snapshot-scoped,
 * refreshed by the capability probe.
 */
data class RecommendationCapability(
    val flavor: RecommendationFlavor,
    val status: CapabilityStatus = CapabilityStatus.Unknown,
    /** Validated connection origin the probe gated, such as `https://dvd.chat`. */
    val origin: String,
)
