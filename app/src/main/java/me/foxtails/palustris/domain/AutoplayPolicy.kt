package me.foxtails.palustris.domain

/** One video tile as the autoplay policy sees it. [key] is stable for the tile, [order] is its position in the list. */
data class AutoplayCandidate(
    val key: String,
    val order: Int,
    /** Visible area divided by full area, from 0 to 1. */
    val visibleFraction: Float,
    /** False when the post is hidden, filtered, behind a content warning, or sensitive and unrevealed. */
    val contentVisible: Boolean,
)

/** Conditions outside the list that gate all autoplay. */
data class AutoplayEnvironment(
    val settingEnabled: Boolean,
    /** Null means the network state is unknown, which counts as metered. */
    val networkMetered: Boolean?,
    val reducedMotion: Boolean,
    val foreground: Boolean,
) {
    val allowsAutoplay: Boolean
        get() = settingEnabled && networkMetered == false && !reducedMotion && foreground
}

/**
 * Picks the one video that may autoplay. Pure and stateless: the caller passes the current
 * selection back in, which is how the hysteresis works.
 */
object AutoplayPolicy {
    const val START_FRACTION = 0.6f
    const val KEEP_FRACTION = 0.4f

    /** Clips shorter than this loop. Longer videos play once. */
    const val LOOP_BELOW_MS = 30_000L

    fun select(
        candidates: List<AutoplayCandidate>,
        environment: AutoplayEnvironment,
        currentKey: String?,
    ): String? {
        if (!environment.allowsAutoplay) return null
        return candidates
            .filter { it.contentVisible }
            .filter { it.visibleFraction >= if (it.key == currentKey) KEEP_FRACTION else START_FRACTION }
            .sortedWith(compareByDescending<AutoplayCandidate> { it.visibleFraction }.thenBy { it.order })
            .firstOrNull()
            ?.key
    }

    /** A clip of unknown duration plays once. */
    fun shouldLoop(durationMs: Long?): Boolean = durationMs != null && durationMs in 1 until LOOP_BELOW_MS
}
