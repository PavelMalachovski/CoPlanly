package com.coparently.app.presentation.professionals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalGrantPolicy
import com.coparently.app.domain.professionals.ProfessionalGrantStatus
import com.coparently.app.presentation.theme.Spacing

/**
 * One professional's card, as a parent reads it (MON-18): who they are, what they can read and
 * until when, and where the two consents stand.
 *
 * At most two actions, and they are not symmetric. **Consent** appears only while the grant is
 * waiting for *this* parent — it is the second key, and nothing opens without it. **End access**
 * is always there and needs nobody else: revocation takes one parent, activation takes two. It is
 * red, and it asks once before it acts, following the sign-out anatomy (design item 8).
 *
 * @param grant The grant shown.
 * @param viewerUid The signed-in parent.
 * @param onConsent Adds this parent's consent.
 * @param onRevoke Deletes the grant.
 * @param onDismiss Closes the card.
 */
@Composable
fun ProfessionalGrantDialog(
    grant: ProfessionalGrant,
    viewerUid: String?,
    onConsent: () -> Unit,
    onRevoke: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmingRevoke by rememberSaveable(grant.id) { mutableStateOf(false) }
    val waitingForMe = ProfessionalGrantPolicy.statusFor(grant, viewerUid, System.currentTimeMillis()) ==
        ProfessionalGrantStatus.WAITING_FOR_YOU
    val until = rememberAccessDate(grant.expiresAtMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(grant.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(
                    text = stringResource(grant.role.labelRes()),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(grantStatusText(grant, viewerUid))
                Text(
                    text = if (confirmingRevoke) {
                        stringResource(R.string.professional_revoke_confirm, grant.name)
                    } else {
                        stringResource(R.string.professional_dialog_body, grant.name, until)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            when {
                confirmingRevoke -> TextButton(onClick = onRevoke) { RevokeLabel() }
                waitingForMe -> TextButton(onClick = onConsent) {
                    Text(stringResource(R.string.professional_dialog_consent))
                }
                else -> TextButton(onClick = { confirmingRevoke = true }) { RevokeLabel() }
            }
        },
        dismissButton = {
            if (waitingForMe && !confirmingRevoke) {
                TextButton(onClick = { confirmingRevoke = true }) { RevokeLabel() }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.professional_dialog_close))
                }
            }
        }
    )
}

/** "End access", in the error colour every destructive action in the app wears. */
@Composable
private fun RevokeLabel() {
    Text(
        stringResource(R.string.professional_dialog_revoke),
        color = MaterialTheme.colorScheme.error
    )
}
