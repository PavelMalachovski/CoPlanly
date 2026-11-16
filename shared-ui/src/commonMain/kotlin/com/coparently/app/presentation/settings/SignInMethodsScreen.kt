package com.coparently.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.coparently.app.data.remote.firebase.SignInMethod
import com.coparently.app.presentation.auth.AuthEmailField
import com.coparently.app.presentation.auth.AuthPasswordField
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.PushedTopBar
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.platform.LocalAppBuildValues
import com.coparently.app.presentation.platform.LocalCredentialSignIn
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_back
import com.coparently.app.resources.common_dialog_cancel
import com.coparently.app.resources.sign_in_methods_add
import com.coparently.app.resources.sign_in_methods_add_email_title
import com.coparently.app.resources.sign_in_methods_apple
import com.coparently.app.resources.sign_in_methods_email
import com.coparently.app.resources.sign_in_methods_google
import com.coparently.app.resources.sign_in_methods_group
import com.coparently.app.resources.sign_in_methods_intro
import com.coparently.app.resources.sign_in_methods_last_note
import com.coparently.app.resources.sign_in_methods_linked
import com.coparently.app.resources.sign_in_methods_not_linked
import com.coparently.app.resources.sign_in_methods_remove
import com.coparently.app.resources.sign_in_methods_remove_message
import com.coparently.app.resources.sign_in_methods_remove_title
import com.coparently.app.resources.sign_in_methods_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The name of a sign-in method, as its row and Settings' Account row say it. */
internal fun SignInMethod.labelRes(): StringResource = when (this) {
    SignInMethod.EMAIL -> Res.string.sign_in_methods_email
    SignInMethod.GOOGLE -> Res.string.sign_in_methods_google
    SignInMethod.APPLE -> Res.string.sign_in_methods_apple
}

private fun SignInMethod.icon(): ImageVector = when (this) {
    SignInMethod.EMAIL -> Icons.Default.Email
    SignInMethod.GOOGLE -> Icons.Default.AccountCircle
    SignInMethod.APPLE -> Icons.Default.Key
}

/**
 * Settings → Account → *Sign-in methods* (MON-24 phase 4, part A; board 2): e-mail, Google and
 * Apple, each linked or not, with Add or Remove.
 *
 * Add opens a form for an e-mail and a password, or Google's or Apple's sheet; Remove asks first,
 * and is not drawn on the account's last method. Apple's row appears only under the build's
 * `APPLE_SIGN_IN` flag, on a platform with Apple's sheet, and Google's only where the credential
 * sheet is available, unless the account already holds the method. A method that belongs to another
 * account links nothing, and an attention banner over the list says so without naming anything
 * about that account.
 *
 * @param onNavigateUp Returns to Settings
 * @param viewModel The methods and their changes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInMethodsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SignInMethodsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val credentials = LocalCredentialSignIn.current
    val offered = SignInMethodsUiState.offeredMethods(
        googleAvailable = credentials.available,
        appleAvailable = credentials.appleAvailable && LocalAppBuildValues.current.appleSignIn
    )
    val scope = rememberCoroutineScope()
    // A sheet's link can finish after the screen left mid-flow, its coroutine cancelled before the
    // methods were read again: read them each time the screen opens.
    LaunchedEffect(Unit) { viewModel.refresh() }
    // By name: an enum is not a value every platform's saved state keeps.
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var addingEmail by rememberSaveable { mutableStateOf(false) }

    val add: (SignInMethod) -> Unit = { method ->
        when (method) {
            SignInMethod.EMAIL -> addingEmail = true
            SignInMethod.GOOGLE -> scope.launch { viewModel.addGoogle(credentials) }
            SignInMethod.APPLE -> scope.launch { viewModel.addApple(credentials) }
        }
    }

    Scaffold(
        containerColor = ScreenContainerColor,
        topBar = {
            PushedTopBar(
                title = stringResource(Res.string.sign_in_methods_title),
                backLabel = stringResource(Res.string.common_back),
                onBack = onNavigateUp
            )
        }
    ) { padding ->
        SignInMethodsContent(
            state = state,
            offered = offered,
            onAdd = add,
            onRemove = { method -> removing = method.name },
            modifier = Modifier.padding(padding)
        )
    }

    removing?.let { name -> SignInMethod.entries.firstOrNull { it.name == name } }?.let { method ->
        RemoveMethodDialog(
            method = method,
            onConfirm = {
                removing = null
                viewModel.remove(method)
            },
            onDismiss = { removing = null }
        )
    }

    if (addingEmail) {
        AddEmailDialog(
            onAdd = { email, password ->
                addingEmail = false
                viewModel.addEmail(email, password)
            },
            onDismiss = { addingEmail = false }
        )
    }
}

/**
 * The screen's body: the intro, the banner when the last change failed, the methods and the note
 * on the last one. Nothing is listed until the methods are first read.
 */
@Composable
private fun SignInMethodsContent(
    state: SignInMethodsUiState,
    offered: Set<SignInMethod>,
    onAdd: (SignInMethod) -> Unit,
    onRemove: (SignInMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text(
            text = stringResource(Res.string.sign_in_methods_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.banner?.let { message ->
            InlineBanner(
                text = message.asString(),
                icon = Icons.Default.Warning,
                tone = BannerTone.ATTENTION
            )
        }
        val rows = state.rows(offered)
        if (state.linked == null) {
            // Until the methods are first read (offline, the auth state settling).
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (rows.isNotEmpty()) {
            Column {
                GroupLabel(stringResource(Res.string.sign_in_methods_group))
                SectionGroup {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) Divider()
                        MethodRow(
                            row = row,
                            working = state.working,
                            onAdd = { onAdd(row.method) },
                            onRemove = { onRemove(row.method) }
                        )
                    }
                }
            }
            Text(
                text = stringResource(Res.string.sign_in_methods_last_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.XS)
            )
        }
    }
}

/**
 * One method: its name, whether it is linked, and Add, Remove or nothing — the account's last
 * method has no Remove. While a change runs, the changing row shows a spinner and every other
 * action waits.
 */
@Composable
private fun MethodRow(
    row: SignInMethodRow,
    working: SignInMethod?,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    val idle = working == null
    SectionRow(
        icon = row.method.icon(),
        title = stringResource(row.method.labelRes()),
        supporting = stringResource(
            if (row.linked) Res.string.sign_in_methods_linked else Res.string.sign_in_methods_not_linked
        ),
        trailing = when {
            working == row.method -> {
                { CircularProgressIndicator(modifier = Modifier.size(IconSizes.Standard)) }
            }
            !row.linked -> {
                {
                    TextButton(onClick = onAdd, enabled = idle) {
                        Text(stringResource(Res.string.sign_in_methods_add))
                    }
                }
            }
            row.canRemove -> {
                {
                    TextButton(onClick = onRemove, enabled = idle) {
                        Text(
                            text = stringResource(Res.string.sign_in_methods_remove),
                            color = if (idle) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            else -> null
        }
    )
}

/** Asks before [method] is removed: an action sheet on the iPhone, as every destructive question. */
@Composable
private fun RemoveMethodDialog(method: SignInMethod, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmationDialog(
        title = stringResource(Res.string.sign_in_methods_remove_title, stringResource(method.labelRes())),
        message = stringResource(Res.string.sign_in_methods_remove_message),
        confirmText = stringResource(Res.string.sign_in_methods_remove),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isDestructive = true
    )
}

/**
 * The form for adding an e-mail address and a password: the sign-in screen's own fields, in a
 * dialog that carries content (so a Compose dialog on both platforms). The password is kept only
 * while the dialog is open, never in saved state.
 */
@Composable
private fun AddEmailDialog(
    onAdd: (email: String, password: String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sign_in_methods_add_email_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                AuthEmailField(value = email, onValueChange = { email = it }, enabled = true)
                AuthPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = true,
                    newPassword = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(email, password) },
                enabled = email.isNotBlank() && password.isNotEmpty()
            ) {
                Text(stringResource(Res.string.sign_in_methods_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_dialog_cancel))
            }
        }
    )
}
