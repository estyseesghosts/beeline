package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.ProductIdentity
import me.foxtails.palustris.R

/**
 * The open-source components that a build of the application includes. The names are proper nouns, so they
 * are not translated. Add a component here when a dependency is added to `app/build.gradle.kts`.
 */
internal val OpenSourceComponents = listOf(
    "AndroidX and Jetpack Compose",
    "Material 3",
    "Media3",
    "Room",
    "WorkManager",
    "Hilt",
    "OkHttp",
    "Coil",
    "avif-coder",
    "OpenDyslexic",
    "Inter",
    "ZCOOL KuaiLe",
    "Chocolate Classical Sans",
    "Chiron Hei HK",
    "Kiwi Maru",
    "Sarala",
    "UnifiedPush connector",
    "FFmpeg (LGPL)",
)

/** The About page: the product name, the version, the license, and the included open-source components. */
@Composable
fun AboutSettingsScreen(
    name: String = ProductIdentity.name,
    version: String = ProductIdentity.version,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        AboutValue(name, stringResource(R.string.settings_about_version, version), "about_version")
        AboutValue(
            stringResource(R.string.settings_about_license),
            stringResource(R.string.settings_about_license_summary),
            "about_license",
        )
        SettingsSection(stringResource(R.string.settings_about_components)) {
            SettingsNote(stringResource(R.string.settings_about_components_summary, name))
            OpenSourceComponents.forEach { component ->
                ListItem(headlineContent = { Text(component) })
            }
        }
    }
}

/** A row that shows a value and has no action. */
@Composable
private fun AboutValue(title: String, value: String, testTag: String) {
    ListItem(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
    )
}
