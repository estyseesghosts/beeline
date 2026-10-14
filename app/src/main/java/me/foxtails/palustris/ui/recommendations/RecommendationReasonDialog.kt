package me.foxtails.palustris.ui.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.RecommendationExplanation

/**
 * How the ranker produced one recommendation, read from the generic explanation. `Following`
 * means the server recommended it for a follow relationship; `Score` means it carries a numeric
 * breakdown; anything else renders the rows the server sent without a mode claim.
 */
enum class RecommendationReasonMode { Following, Score, Other }

/** One additive scoring row behind the final score, in server order. */
data class RecommendationReasonFactorRow(
    val label: String,
    val contribution: Double?,
    val detail: String?,
)

/** One learned-ranker row, kept separate so model weights never read as additive factors. */
data class RecommendationReasonLearnedRow(
    val label: String,
    val weight: Double?,
    val value: Double?,
)

/**
 * Dialog-ready rendering of one generic [RecommendationExplanation]. Pure and protocol-free:
 * Compose renders this, never Sharkey JSON.
 * Responsibility: reason-dialog content policy. Lifetime: one dialog invocation.
 */
data class RecommendationReasonContent(
    val mode: RecommendationReasonMode,
    val score: Double?,
    val topic: String?,
    /** The ranker type label the server sent, or null when it sent none. */
    val typeLabel: String?,
    val factors: List<RecommendationReasonFactorRow>,
    val learnedRows: List<RecommendationReasonLearnedRow>,
) {
    /** True when a numeric breakdown is worth showing: score mode with rows behind it. */
    val showBreakdown: Boolean get() = mode == RecommendationReasonMode.Score &&
        (score != null || factors.isNotEmpty())

    /** True when the learned-ranker section has rows to show. */
    val showLearned: Boolean get() = learnedRows.isNotEmpty()
}

/** Maps the generic explanation onto dialog content. Unknown mode labels read as [Other]. */
fun recommendationReasonContent(explanation: RecommendationExplanation): RecommendationReasonContent {
    val mode = when (explanation.mode?.lowercase()) {
        "following" -> RecommendationReasonMode.Following
        "score" -> RecommendationReasonMode.Score
        else -> RecommendationReasonMode.Other
    }
    return RecommendationReasonContent(
        mode = mode,
        score = explanation.score?.takeIf { it.isFinite() },
        topic = explanation.topic?.takeIf(String::isNotBlank),
        typeLabel = explanation.mode?.takeIf(String::isNotBlank),
        factors = explanation.factors.map {
            RecommendationReasonFactorRow(it.label, it.contribution?.takeIf(Double::isFinite), it.detail)
        },
        learnedRows = explanation.learnedRows.map {
            RecommendationReasonLearnedRow(it.label, it.weight?.takeIf(Double::isFinite), it.value?.takeIf(Double::isFinite))
        },
    )
}

/**
 * Why-recommended dialog for one recommended row. Renders the generic [RecommendationExplanation]
 * only: final score, topic, type, additive rows, and learned ranker rows when present. Opens from
 * recommended rows only; the caller gates it, this dialog only renders.
 */
@Composable
fun RecommendationReasonDialog(
    explanation: RecommendationExplanation,
    onDismiss: () -> Unit,
) {
    val content = remember(explanation) { recommendationReasonContent(explanation) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("recommendation_reason_close")) {
                Text(stringResource(R.string.recommendation_reason_close))
            }
        },
        title = {
            Text(
                stringResource(R.string.recommendation_reason_title),
                modifier = Modifier.testTag("recommendation_reason_title"),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content.score?.let { score ->
                    Text(
                        stringResource(R.string.recommendation_reason_score, score.toString()),
                        modifier = Modifier.testTag("recommendation_reason_score"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                content.topic?.let { topic ->
                    Text(
                        stringResource(R.string.recommendation_reason_topic, topic),
                        modifier = Modifier.testTag("recommendation_reason_topic"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                content.typeLabel?.let { type ->
                    Text(
                        stringResource(R.string.recommendation_reason_type, type),
                        modifier = Modifier.testTag("recommendation_reason_type"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (content.mode == RecommendationReasonMode.Following) {
                    Text(
                        stringResource(R.string.recommendation_reason_following),
                        modifier = Modifier.testTag("recommendation_reason_following"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (content.showBreakdown) {
                    Text(
                        stringResource(R.string.recommendation_reason_factors),
                        modifier = Modifier.testTag("recommendation_reason_factors_heading"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    content.factors.forEachIndexed { index, factor ->
                        Text(
                            factorLine(factor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp)
                                .testTag("recommendation_reason_factor_$index"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (content.showLearned) {
                    Text(
                        stringResource(R.string.recommendation_reason_learned),
                        modifier = Modifier.testTag("recommendation_reason_learned_heading"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    content.learnedRows.forEachIndexed { index, row ->
                        Text(
                            learnedLine(row),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp)
                                .testTag("recommendation_reason_learned_$index"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (content.score == null && content.topic == null && content.typeLabel == null &&
                    content.factors.isEmpty() && content.learnedRows.isEmpty() &&
                    content.mode != RecommendationReasonMode.Following
                ) {
                    Text(
                        stringResource(R.string.recommendation_reason_empty),
                        modifier = Modifier.testTag("recommendation_reason_empty"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        modifier = Modifier.testTag("recommendation_reason_dialog"),
    )
}

private fun factorLine(factor: RecommendationReasonFactorRow): String {
    val contribution = factor.contribution?.toString()
    val head = if (contribution == null) factor.label else "${factor.label}: $contribution"
    return if (factor.detail.isNullOrBlank()) head else "$head (${factor.detail})"
}

private fun learnedLine(row: RecommendationReasonLearnedRow): String {
    val parts = listOfNotNull(
        row.weight?.let { "weight $it" },
        row.value?.let { "value $it" },
    )
    return if (parts.isEmpty()) row.label else "${row.label} (${parts.joinToString(", ")})"
}
