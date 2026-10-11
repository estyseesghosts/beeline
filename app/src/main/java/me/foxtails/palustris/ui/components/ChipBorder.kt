package me.foxtails.palustris.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The one border rule for tab chips. An unselected chip uses the accent-tinted `outlineVariant`.
 * A selected chip has no border, because `secondaryContainer` already fills it.
 */
@Composable
internal fun destinationChipBorder(selected: Boolean): BorderStroke? =
    if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
