package me.foxtails.palustris.ui.profile

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.ui.motion.TriggerSurface
import me.foxtails.palustris.ui.motion.TriggerSurfaceSource
import me.foxtails.palustris.ui.motion.rememberTriggerSurfaceState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EditProfileSheet(
    account: Account,
    editor: EditableProfile?,
    editorBase: EditableProfile?,
    capabilities: EditableProfileCapabilities,
    emoji: Map<String, me.foxtails.palustris.domain.CustomEmoji>,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onEditorChange: (EditableProfile) -> Unit,
    onSave: (me.foxtails.palustris.domain.EditableProfilePatch) -> Unit,
    onClose: () -> Unit,
    triggerSource: TriggerSurfaceSource? = null,
) {
    if (editor == null) return
    val surface = rememberTriggerSurfaceState()
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = surface.scrim(BottomSheetDefaults.ScrimColor),
    ) {
        TriggerSurface(
            state = surface,
            trigger = { triggerSource?.bounds },
            dismissLabel = stringResource(R.string.composer_close),
            onClosed = onClose,
            returnFocus = { triggerSource?.restoreFocus() },
        ) { requestClose ->
            EditProfileScreen(
                editor = editor,
                capabilities = capabilities,
                emoji = emoji,
                handle = account.handle,
                loading = loading,
                saving = saving,
                error = error,
                onEditorChange = onEditorChange,
                onSave = { editorBase?.let { onSave(editableProfilePatch(it, editor)) } },
                onClose = requestClose,
            )
        }
    }
}
