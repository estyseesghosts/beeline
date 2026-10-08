package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.motion.TriggerSurface
import me.foxtails.palustris.ui.motion.TriggerSurfaceSource
import me.foxtails.palustris.ui.motion.rememberTriggerSurfaceState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ComposerSheet(
    onDismiss: () -> Unit,
    onSaveDraft: () -> Unit,
    saveEnabled: Boolean,
    closing: Boolean,
    triggerSource: TriggerSurfaceSource? = null,
    content: @Composable () -> Unit,
) {
    val surface = rememberTriggerSurfaceState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = surface.scrim(BottomSheetDefaults.ScrimColor),
    ) {
        TriggerSurface(
            state = surface,
            trigger = { triggerSource?.bounds },
            dismissLabel = stringResource(R.string.composer_close),
            onClosed = onDismiss,
            returnFocus = { triggerSource?.restoreFocus() },
        ) { requestClose ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = requestClose) { Icon(AppIcons.Close, stringResource(R.string.composer_close)) }
                        Text(stringResource(R.string.composer_new_post), style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(enabled = saveEnabled && !closing, onClick = onSaveDraft) {
                        Text(stringResource(R.string.composer_save_draft))
                    }
                }
                content()
            }
        }
    }
}
