package com.coparently.app.presentation.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.consent.HealthConsent
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.localizedDate
import java.time.Instant
import java.time.ZoneId

/**
 * The consent dialog shown before a child's health details are entered (GDPR Art. 9(2)(a)).
 *
 * The [ConfirmationDialog] anatomy: text buttons, "I agree" and "Not now". Its wording is what
 * `HEALTH_CONSENT_VERSION` versions — change one, bump the other.
 *
 * @param onAgree Records the consent
 * @param onNotNow Closes the dialog and records nothing
 */
@Composable
fun HealthConsentDialog(onAgree: () -> Unit, onNotNow: () -> Unit) {
    ConfirmationDialog(
        title = stringResource(R.string.health_consent_dialog_title),
        message = stringResource(R.string.health_consent_dialog_message),
        confirmText = stringResource(R.string.health_consent_agree),
        dismissText = stringResource(R.string.health_consent_not_now),
        onConfirm = onAgree,
        onDismiss = onNotNow
    )
}

/**
 * What a child's medical section shows until the parent has consented: one line and an action
 * that opens [HealthConsentDialog] again. Nothing in it can be edited, and nothing else on the
 * form depends on it.
 *
 * @param onAddMedicalDetails Opens the consent dialog
 * @param modifier Modifier applied to the column
 * @param enabled Whether the action accepts input, e.g. false while the form saves
 */
@Composable
fun LockedMedicalSection(
    onAddMedicalDetails: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(
            text = stringResource(R.string.health_consent_locked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onAddMedicalDetails, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.Small)
            )
            Spacer(modifier = Modifier.width(Spacing.S))
            Text(stringResource(R.string.health_consent_add_action))
        }
    }
}

/**
 * The Settings row for the child-health consent: when it was given, or that it was not, and the
 * one action — Withdraw — while there is something to withdraw. A row inside the caller's
 * `SectionGroup`; the caller places the dividers.
 *
 * A consent to an older wording still shows its date and can still be withdrawn: the details
 * entered under it exist until it is.
 *
 * @param consent The stored consent, or null when never given or withdrawn
 * @param withdrawing Whether a withdrawal is in flight; hides the action
 * @param onWithdraw Asks for confirmation; the caller shows [HealthConsentWithdrawDialog]
 */
@Composable
fun HealthConsentSettingsRow(
    consent: HealthConsent?,
    withdrawing: Boolean,
    onWithdraw: () -> Unit
) {
    val dateFormatter = remember { localizedDate("yMMMd") }
    val supporting = consent?.let {
        val day = Instant.ofEpochMilli(it.atMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        stringResource(R.string.health_consent_settings_given, day.format(dateFormatter))
    } ?: stringResource(R.string.health_consent_settings_not_given)
    SectionRow(
        icon = Icons.Default.MedicalServices,
        title = stringResource(R.string.health_consent_settings_title),
        supporting = supporting,
        trailing = if (consent != null && !withdrawing) {
            {
                TextButton(
                    onClick = onWithdraw,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.health_consent_settings_withdraw))
                }
            }
        } else {
            null
        }
    )
}

/**
 * Confirms a withdrawal, in the destructive [ConfirmationDialog] style. Says what is deleted, and
 * that the co-parent's own records keep theirs.
 *
 * @param onConfirm Withdraws
 * @param onDismiss Keeps the consent
 */
@Composable
fun HealthConsentWithdrawDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmationDialog(
        title = stringResource(R.string.health_consent_withdraw_title),
        message = stringResource(R.string.health_consent_withdraw_message),
        confirmText = stringResource(R.string.health_consent_withdraw_confirm),
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
