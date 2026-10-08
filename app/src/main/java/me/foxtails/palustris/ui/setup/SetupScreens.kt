package me.foxtails.palustris.ui.setup

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.foxtails.palustris.R
import me.foxtails.palustris.data.AppMessages
import me.foxtails.palustris.data.misskey.ServerAddress
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.session.SessionUi

@Composable
internal fun SetupInitialScreen(onNewUser: () -> Unit, onSignIn: () -> Unit) {
    SetupColumn {
        LogoSurface(Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        SetupPrimaryAction(stringResource(R.string.setup_new_user), onNewUser)
        Spacer(Modifier.height(12.dp))
        SetupSecondaryAction(stringResource(R.string.setup_sign_in), onSignIn)
    }
}

@Composable
internal fun SetupIntroductionScreen(onGetStarted: () -> Unit) {
    SetupColumn {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.25f),
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.setup_welcome_to), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.setup_the_fediverse), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.weight(1f))
                    Image(
                        painter = painterResource(R.drawable.beeline_mark),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(.65f).weight(1f),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SetupPrimaryAction(stringResource(R.string.setup_get_started), onGetStarted)
    }
}

@Composable
internal fun SetupServerScreen(
    state: SessionUi,
    onNext: (String) -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onCancel: () -> Unit,
) {
    var server by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val appMessages = AppMessages.from(LocalContext.current)
    val whitespaceError = stringResource(R.string.setup_server_whitespace_error)
    val invalidServerError = stringResource(R.string.setup_invalid_server)
    val submit: () -> Unit = {
        if (state.pending) {
            onComplete()
        } else {
            try {
                val normalized = ServerAddress.normalize(server, appMessages)
                validationError = null
                onNext(normalized)
            } catch (error: IllegalArgumentException) {
                validationError = error.message ?: invalidServerError
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text(
            stringResource(if (state.pending) R.string.sign_in_pending_title else R.string.setup_welcome_back),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!state.pending) {
            Text(
                stringResource(R.string.setup_heart),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Start),
            )
        }
        Spacer(Modifier.weight(1f, fill = true))
        if (state.pending) {
            Text(
                stringResource(
                    R.string.sign_in_pending_description,
                    stringResource(R.string.app_name),
                    state.origin?.removePrefix("https://").orEmpty(),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))
            state.error?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SetupPrimaryAction(stringResource(R.string.sign_in_button_authorized), onComplete, enabled = !state.busy)
            Spacer(Modifier.height(12.dp))
            SetupSecondaryAction(stringResource(R.string.sign_in_open_browser_again), onReopen, enabled = !state.busy)
            Spacer(Modifier.height(8.dp))
            SetupTextAction(stringResource(if (state.addingAccount) R.string.sign_in_cancel else R.string.sign_in_different_instance), onCancel, enabled = !state.busy)
        } else {
            Column(Modifier.fillMaxWidth().imePadding().animateContentSize(LocalPalustrisMotionScheme.current.gentleSize)) {
                validationError?.let {
                    Text(it, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), color = MaterialTheme.colorScheme.error)
                }
                SetupServerField(server, !state.busy, submit) { value ->
                    val trimmed = value.trim()
                    if (trimmed.any(Char::isWhitespace)) {
                        validationError = whitespaceError
                    } else {
                        server = trimmed
                        validationError = null
                    }
                }
                Spacer(Modifier.height(12.dp))
                SetupSecondaryAction(
                    label = stringResource(R.string.setup_next),
                    onClick = submit,
                    enabled = server.isNotBlank() && !state.busy,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SetupColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

@Composable
private fun LogoSurface(modifier: Modifier) {
    Surface(
        modifier = modifier.aspectRatio(1.25f),
        shape = RoundedCornerShape(48.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
            val wordmarkFontSize = maxWidth.value.coerceIn(28f, 56f).sp
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.setup_wordmark),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = wordmarkFontSize,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.beeline_mark),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(.65f).weight(1f),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SetupPrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).widthIn(max = 480.dp).springPress(interactionSource, enabled),
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun SetupSecondaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .widthIn(max = 480.dp)
            .springPress(interactionSource, enabled)
            .clip(RoundedCornerShape(percent = 50))
            .semantics { role = Role.Button },
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) { Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) } }
}

@Composable
private fun SetupTextAction(label: String, onClick: () -> Unit, enabled: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = label; role = Role.Button },
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.bodyLarge) } }
}

@Composable
private fun SetupServerField(
    value: String,
    enabled: Boolean,
    onSubmit: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    val fieldLabel = stringResource(R.string.setup_server_placeholder)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onPrimary),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        // The pill grows with large text instead of clipping. A long origin scrolls inside the one line.
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).widthIn(max = 480.dp)
            .testTag("setup_server_field")
            .semantics { contentDescription = fieldLabel },
        decorationBox = { field ->
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.isEmpty()) Text(stringResource(R.string.setup_server_placeholder), style = MaterialTheme.typography.labelLarge)
                    field()
                }
            }
        },
    )
}
