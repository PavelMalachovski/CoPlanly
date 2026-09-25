package com.coparently.app.presentation.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.ai.AiConsent
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.SectionGroupScope
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.utils.localizedDate
import java.time.Instant
import java.time.ZoneId

/**
 * The consent dialog shown before the first AI request (and again after a withdrawal, or when
 * the wording's version moves). It says exactly what is sent, to whom and where, that nothing is
 * kept, that nothing reaches the co-parent unless the parent sends it, and where to turn it off.
 *
 * The [ConfirmationDialog] anatomy: text buttons, "I agree" and "Cancel". Its wording is what
 * `AI_CONSENT_VERSION` versions — change one, bump the other (and the server's).
 *
 * @param onAgree Records the consent and sends the waiting request
 * @param onCancel Closes the dialog; nothing is recorded or sent
 */
@Composable
fun AiConsentDialog(onAgree: () -> Unit, onCancel: () -> Unit) {
    ConfirmationDialog(
        title = stringResource(R.string.ai_consent_dialog_title),
        message = stringResource(R.string.ai_consent_dialog_message),
        confirmText = stringResource(R.string.ai_consent_agree),
        dismissText = stringResource(R.string.common_dialog_cancel),
        onConfirm = onAgree,
        onDismiss = onCancel
    )
}

/**
 * The Settings row for the AI-assist consent, followed by its divider — or nothing at all when
 * this build does not offer the assist (design item 8: no row for a feature that is not there).
 *
 * Shows when the consent was given, or that it was not, and the one action — Turn off — while
 * there is something to withdraw. Confirms in the destructive dialog style and reports the outcome
 * through the screen's snackbar.
 *
 * @param snackbarHostState The Settings screen's snackbar host
 * @param viewModel The consent's state
 */
@Composable
fun SectionGroupScope.AiConsentSettingsEntry(
    snackbarHostState: SnackbarHostState,
    viewModel: AiConsentViewModel = hiltViewModel()
) {
    if (!viewModel.available) return
    val consent by viewModel.consent.collectAsState()
    val withdrawing by viewModel.withdrawing.collectAsState()
    var confirming by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message.asString(context)) }
    }

    AiConsentSettingsRow(
        consent = consent,
        withdrawing = withdrawing,
        onWithdraw = { confirming = true }
    )
    Divider()

    if (confirming) {
        ConfirmationDialog(
            title = stringResource(R.string.ai_consent_withdraw_title),
            message = stringResource(R.string.ai_consent_withdraw_message),
            confirmText = stringResource(R.string.ai_consent_withdraw_confirm),
            isDestructive = true,
            onConfirm = {
                confirming = false
                viewModel.withdraw()
            },
            onDismiss = { confirming = false }
        )
    }
}

/**
 * The row itself: its title, when the consent was given (or that it was not), and Turn off.
 *
 * @param consent The stored consent, or null when never given or withdrawn
 * @param withdrawing Whether a withdrawal is in flight; hides the action
 * @param onWithdraw Asks for confirmation
 */
@Composable
internal fun AiConsentSettingsRow(
    consent: AiConsent?,
    withdrawing: Boolean,
    onWithdraw: () -> Unit
) {
    val dateFormatter = remember { localizedDate("yMMMd") }
    val supporting = when {
        consent == null -> stringResource(R.string.ai_consent_settings_not_given)
        consent.grantedAtMillis == null -> stringResource(R.string.ai_consent_settings_given_now)
        else -> {
            val day = Instant.ofEpochMilli(consent.grantedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            stringResource(R.string.ai_consent_settings_given, day.format(dateFormatter))
        }
    }
    SectionRow(
        icon = Icons.Default.AutoAwesome,
        title = stringResource(R.string.ai_consent_settings_title),
        supporting = supporting,
        trailing = if (consent != null && !withdrawing) {
            {
                TextButton(
                    onClick = onWithdraw,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ai_consent_settings_withdraw))
                }
            }
        } else {
            null
        }
    )
}
