package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.EntityId

/**
 * The line under a translated post: where it came from and a toggle back to the original.
 * It also shows the loading and failure states of a translation request. It holds no state;
 * the owner behind [presentation] does.
 */
@Composable
internal fun PostTranslationFooter(
    entry: TranslationEntry?,
    id: EntityId,
    presentation: PostTranslationPresentation?,
    modifier: Modifier = Modifier,
) {
    when (entry) {
        null -> Unit
        TranslationEntry.Loading -> Text(
            stringResource(R.string.post_translate_loading),
            modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("post_translation_loading"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is TranslationEntry.Failed -> Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.message,
                modifier = Modifier.weight(1f).testTag("post_translation_error"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { presentation?.dismissFailure(id) }) { Text(stringResource(R.string.post_translate_dismiss)) }
        }
        is TranslationEntry.Ready -> Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!entry.showOriginal) {
                Text(
                    translatedFromLabel(entry),
                    modifier = Modifier.weight(1f).testTag("post_translation_source"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("", modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { presentation?.toggleOriginal(id) }, modifier = Modifier.testTag("post_translation_toggle")) {
                Text(stringResource(if (entry.showOriginal) R.string.post_translate_show_translation else R.string.post_translate_show_original))
            }
        }
    }
}

@Composable
private fun translatedFromLabel(entry: TranslationEntry.Ready): String {
    val translation = entry.translation
    val language = translation.sourceLanguage?.let { code ->
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).takeIf(String::isNotBlank) ?: code
    }
    val provider = translation.provider
    return when {
        language != null && provider != null -> stringResource(R.string.post_translate_from_by, language, provider)
        language != null -> stringResource(R.string.post_translate_from, language)
        provider != null -> stringResource(R.string.post_translate_by, provider)
        else -> stringResource(R.string.post_translate_done)
    }
}
