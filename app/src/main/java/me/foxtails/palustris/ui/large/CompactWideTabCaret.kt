package me.foxtails.palustris.ui.large

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme

/**
 * Shared tab-chip visibility state for the compact-wide contextual caret.
 *
 * The feature screen owns expansion state and its toggle callback. This holder only mirrors the
 * current registration so the floating navigation can present the same state contextually.
 * It never duplicates the state machine: [expanded] reflects the feature-owned chip visibility
 * and [onToggle] invokes the feature-owned toggle directly.
 */
data class TabCaretUiState(
    val expanded: Boolean,
    val onToggle: () -> Unit,
    val owner: Any? = null,
)

/**
 * Hosts the current compact-wide tab caret registration.
 *
 * One host lives in the shell. Each destination with a tab-chip dock registers when it composes
 * in compact-wide presentation and clears on disposal. A null state means the current screen
 * exposes no tab chips, so the contextual caret stays absent.
 */
class CompactWideTabCaretHost {
    var caretState by mutableStateOf<TabCaretUiState?>(null)
}

@Composable
internal fun rememberCompactWideTabCaretHost(): CompactWideTabCaretHost = remember {
    CompactWideTabCaretHost()
}

/**
 * Reports a feature-owned chip visibility state to the shell host.
 *
 * Registration uses an owner token so overlapping destination transitions cannot clear a newer
 * registration when the outgoing destination disposes.
 */
@Composable
internal fun CompactWideTabCaretRegistration(
    host: CompactWideTabCaretHost?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (host == null) return
    val latestToggle by rememberUpdatedState(onToggle)
    val owner = remember { Any() }
    SideEffect {
        host.caretState = TabCaretUiState(expanded = expanded, onToggle = { latestToggle() }, owner = owner)
    }
    DisposableEffect(host, owner) {
        onDispose {
            if (host.caretState?.owner === owner) {
                host.caretState = null
            }
        }
    }
}

/**
 * Renders the compact-wide contextual tab caret as a floating button.
 *
 * The button shares the contextual-action family sizing, alignment, and spacing: it is 56 dp,
 * centers in the 56 dp floating column, and sits one placement gap below the composer action.
 * Its icon and accessible label reflect chip expansion, and pressing it invokes the existing
 * feature-owned toggle.
 */
@Composable
internal fun ContextualTabCaretButton(
    state: TabCaretUiState,
    modifier: Modifier = Modifier,
) {
    val scheme = LocalPalustrisMotionScheme.current
    val rotation by animateFloatAsState(
        targetValue = if (state.expanded) 0f else 180f,
        animationSpec = scheme.gentle,
        label = "compactWideTabCaretRotation",
    )
    val description = stringResource(
        if (state.expanded) R.string.hide_destination_chips else R.string.show_destination_chips,
    )
    val interactionSource = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = state.onToggle,
        modifier = modifier
            .size(NAVIGATION_CARET_SIZE_DP.dp)
            .semantics { contentDescription = description }
            .testTag(COMPACT_WIDE_TAB_CARET_TAG),
        interactionSource = interactionSource,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(
            imageVector = AppIcons.CaretUp,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

internal const val COMPACT_WIDE_TAB_CARET_TAG = "compact_wide_tab_caret"
