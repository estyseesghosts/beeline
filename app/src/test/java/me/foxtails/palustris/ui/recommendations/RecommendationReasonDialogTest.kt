package me.foxtails.palustris.ui.recommendations

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.RecommendationExplanation
import me.foxtails.palustris.domain.RecommendationFactor
import me.foxtails.palustris.domain.RecommendationFlavor
import me.foxtails.palustris.domain.RecommendationLearnedRow
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Slice 4: the reason dialog renders the generic explanation for score versus following modes.
 * It never parses protocol JSON: every fixture here is already domain-mapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecommendationReasonDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun show(explanation: RecommendationExplanation) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    RecommendationReasonDialog(explanation = explanation, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun scoreExplanation() = RecommendationExplanation(
        flavor = RecommendationFlavor.DVD_SHARKEY,
        score = 1.5,
        topic = "art",
        mode = "score",
        factors = listOf(
            RecommendationFactor("recency", 0.5, "x1.2"),
            RecommendationFactor("subtotal", 1.0),
        ),
        learnedRows = listOf(RecommendationLearnedRow("ranker", 0.7, 0.9)),
    )

    @Test fun scoreModeRendersScoreTopicTypeFactorsAndLearnedRows() {
        show(scoreExplanation())

        compose.onNodeWithTag("recommendation_reason_dialog").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_title").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_score").assertIsDisplayed()
        compose.onNodeWithText("Final score: 1.5").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_topic").assertIsDisplayed()
        compose.onNodeWithText("Topic: art").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_type").assertIsDisplayed()
        compose.onNodeWithText("Type: score").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_factors_heading").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_factor_0").assertIsDisplayed()
        compose.onNodeWithText("recency: 0.5 (x1.2)").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_factor_1").assertIsDisplayed()
        compose.onNodeWithText("subtotal: 1.0").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_learned_heading").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_learned_0").assertIsDisplayed()
        compose.onNodeWithText("ranker (weight 0.7, value 0.9)").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_following").assertDoesNotExist()
        compose.onNodeWithTag("recommendation_reason_close").assertIsDisplayed()
    }

    @Test fun followingModeRendersTheFollowLineWithoutABreakdown() {
        show(
            RecommendationExplanation(
                flavor = RecommendationFlavor.DVD_SHARKEY,
                topic = "art",
                mode = "following",
            ),
        )

        compose.onNodeWithTag("recommendation_reason_dialog").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_following").assertIsDisplayed()
        compose.onNodeWithText("Recommended because you follow this account.").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_score").assertDoesNotExist()
        compose.onNodeWithTag("recommendation_reason_factors_heading").assertDoesNotExist()
        compose.onNodeWithTag("recommendation_reason_factor_0").assertDoesNotExist()
        compose.onNodeWithTag("recommendation_reason_learned_heading").assertDoesNotExist()
        compose.onNodeWithTag("recommendation_reason_empty").assertDoesNotExist()
    }

    @Test fun emptyExplanationRendersTheFallback() {
        show(RecommendationExplanation(flavor = RecommendationFlavor.DVD_SHARKEY))

        compose.onNodeWithTag("recommendation_reason_empty").assertIsDisplayed()
        compose.onNodeWithTag("recommendation_reason_score").assertDoesNotExist()
        compose.onNodeWithTag("recommendation_reason_factors_heading").assertDoesNotExist()
    }
}
