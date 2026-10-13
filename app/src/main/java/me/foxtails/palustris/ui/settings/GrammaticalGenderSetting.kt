package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppGrammaticalGender
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.hasGrammaticalGenderForms
import me.foxtails.palustris.ui.localization.AppLocaleController

/**
 * Whether the Language page shows the grammatical gender setting. The setting has an effect only
 * for a language with gender catalogs. A `SystemDefault` language uses the device language.
 */
@Composable
internal fun showsGrammaticalGender(selected: AppLanguage): Boolean {
    val device = AppLocaleController.languageForLocale(LocalConfiguration.current.locales.get(0))
    return (if (selected == AppLanguage.SystemDefault) device else selected).hasGrammaticalGenderForms
}

/** One row that names the current form. A press opens the three choices. */
@Composable
internal fun GrammaticalGenderRow(selected: AppGrammaticalGender, onSelected: (AppGrammaticalGender) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListItem(
            modifier = Modifier
                .testTag("grammatical_gender")
                .clickable(role = Role.DropdownList) { expanded = true },
            headlineContent = { Text(stringResource(R.string.settings_grammatical_gender)) },
            supportingContent = {
                Column {
                    Text(grammaticalGenderLabel(selected))
                    Text(stringResource(R.string.settings_grammatical_gender_summary))
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppGrammaticalGender.entries.forEach { gender ->
                DropdownMenuItem(
                    text = { Text(grammaticalGenderLabel(gender)) },
                    onClick = {
                        expanded = false
                        onSelected(gender)
                    },
                )
            }
        }
    }
}

@Composable
internal fun grammaticalGenderLabel(gender: AppGrammaticalGender): String = stringResource(
    when (gender) {
        AppGrammaticalGender.Masculine -> R.string.settings_grammatical_gender_masculine
        AppGrammaticalGender.Feminine -> R.string.settings_grammatical_gender_feminine
        AppGrammaticalGender.Neutral -> R.string.settings_grammatical_gender_neutral
    },
)
