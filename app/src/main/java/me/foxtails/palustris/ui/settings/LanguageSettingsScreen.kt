package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppLanguage

/** Every catalog language stays reachable on compact screens. One row is selected. */
@Composable
fun LanguageSettingsScreen(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    alwaysTranslatePosts: Boolean = false,
    onAlwaysTranslatePosts: (Boolean) -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxWidth().selectableGroup()) {
        // The setting is about reading, so it sits with the language. Its summary names the privacy cost.
        item(key = "always-translate") {
            val switchDescription = stringResource(R.string.settings_always_translate_posts_a11y)
            ListItem(
                modifier = Modifier.testTag("always_translate_posts"),
                headlineContent = { Text(stringResource(R.string.settings_always_translate_posts)) },
                supportingContent = { Text(stringResource(R.string.settings_always_translate_posts_summary)) },
                trailingContent = {
                    Switch(
                        checked = alwaysTranslatePosts,
                        onCheckedChange = onAlwaysTranslatePosts,
                        modifier = Modifier.semantics { contentDescription = switchDescription },
                    )
                },
            )
            HorizontalDivider()
        }
        items(AppLanguage.entries, key = { it.name }) { language ->
            val headline =
                if (language == AppLanguage.SystemDefault) stringResource(R.string.language_system_default)
                else appLanguageLabel(language)
            val supporting =
                language.tag ?: stringResource(R.string.language_system_default_description)
            ListItem(
                headlineContent = { Text(headline) },
                supportingContent = { Text(supporting) },
                leadingContent = {
                    RadioButton(
                        selected = language == selected,
                        onClick = null,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                },
                modifier = Modifier.selectable(
                    selected = language == selected,
                    onClick = { onSelected(language) },
                    role = Role.RadioButton,
                ),
            )
        }
    }
}

@Composable
internal fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SystemDefault -> stringResource(R.string.language_system_default)
    AppLanguage.English -> stringResource(R.string.language_name_english)
    AppLanguage.German -> stringResource(R.string.language_name_german)
    AppLanguage.Spanish -> stringResource(R.string.language_name_spanish)
    AppLanguage.SpanishSpain -> stringResource(R.string.language_name_spanish_spain)
    AppLanguage.SpanishLatinAmerica -> stringResource(R.string.language_name_spanish_latin_america)
    AppLanguage.French -> stringResource(R.string.language_name_french)
    AppLanguage.Hindi -> stringResource(R.string.language_name_hindi)
    AppLanguage.Japanese -> stringResource(R.string.language_name_japanese)
    AppLanguage.Korean -> stringResource(R.string.language_name_korean)
    AppLanguage.PortugueseBrazil -> stringResource(R.string.language_name_portuguese_brazil)
    AppLanguage.PortuguesePortugal -> stringResource(R.string.language_name_portuguese_portugal)
    AppLanguage.CantoneseHongKong -> stringResource(R.string.language_name_cantonese_hong_kong)
    AppLanguage.Chinese -> stringResource(R.string.language_name_chinese)
    AppLanguage.ChineseMainland -> stringResource(R.string.language_name_chinese_mainland)
    AppLanguage.ChineseTaiwan -> stringResource(R.string.language_name_chinese_taiwan)
    AppLanguage.Russian -> stringResource(R.string.language_name_russian)
    AppLanguage.Indonesian -> stringResource(R.string.language_name_indonesian)
}
