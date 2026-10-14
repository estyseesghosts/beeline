package me.foxtails.palustris.domain

/**
 * Why one recommended post was ranked where it sits, in fork-neutral rows. Adapters map
 * protocol payloads onto this at the boundary; UI renders it without protocol knowledge.
 * Responsibility: generic ranking rationale for one recommended post. Lifetime: page-scoped,
 * discarded with the feed page.
 */
data class RecommendationExplanation(
    /** Which fork's ranker produced this; a second fork reuses these rows unchanged. */
    val flavor: RecommendationFlavor,
    /** Final rank score, or null when the server sent none. */
    val score: Double? = null,
    /** Topic label the ranker assigned, or null when it sent none. */
    val topic: String? = null,
    /** Ranker mode label the server sent, or null when it sent none. */
    val mode: String? = null,
    /** Additive factor rows behind the score, in server order. */
    val factors: List<RecommendationFactor> = emptyList(),
    /** Learned-ranker rows, in server order. Empty when the server sent none. */
    val learnedRows: List<RecommendationLearnedRow> = emptyList(),
)

/** One additive scoring factor, such as a recency multiplier or subtotal. */
data class RecommendationFactor(
    val label: String,
    val contribution: Double? = null,
    val detail: String? = null,
)

/** One learned-ranker row, kept separate so additive factors never absorb model weights. */
data class RecommendationLearnedRow(
    val label: String,
    val weight: Double? = null,
    val value: Double? = null,
)
