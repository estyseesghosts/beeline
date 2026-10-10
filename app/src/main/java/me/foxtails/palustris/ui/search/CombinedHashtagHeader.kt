package me.foxtails.palustris.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R

/** Hashtags named in the "Includes" line before the rest collapse into "and N more". */
private const val NAMED_HASHTAGS = 2

/**
 * The line above combined hashtag results. It names the extra hashtags that the search applied
 * and offers a plain search of the typed hashtag. Callers show it only when [combinedTags] is not empty.
 */
@Composable
internal fun CombinedHashtagHeader(
    primaryTag: String,
    combinedTags: List<String>,
    onShowOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val named = combinedTags.take(NAMED_HASHTAGS).joinToString(", ") { "#$it" }
    val remaining = combinedTags.size - NAMED_HASHTAGS
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search_hashtag_combined"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            if (remaining > 0) {
                stringResource(R.string.search_hashtag_includes_more, named, remaining)
            } else {
                stringResource(R.string.search_hashtag_includes, named)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onShowOnly, modifier = Modifier.testTag("search_hashtag_show_only")) {
            Text(stringResource(R.string.search_hashtag_show_only, "#$primaryTag"))
        }
    }
}
