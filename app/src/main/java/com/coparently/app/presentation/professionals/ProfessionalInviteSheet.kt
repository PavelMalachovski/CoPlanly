package com.coparently.app.presentation.professionals

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.R
import com.coparently.app.domain.professionals.ProfessionalAccessDuration
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.PillChip

/**
 * Inviting a professional (MON-18): who, for how long, then a code.
 *
 * The sheet says outright what the professional will and will not read, and that the access only
 * starts once the co-parent consents too — an access grant must not be quieter about its scope,
 * or about who still has to agree, than the people involved would expect.
 *
 * @param state What to show.
 * @param onChooseRole Picks the profession; ignored once a code exists.
 * @param onChooseDuration Picks the length; ignored once a code exists.
 * @param onCreate Mints the invitation.
 * @param onDismiss Closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalInviteSheet(
    state: ProfessionalInviteState,
    onChooseRole: (ProfessionalRole) -> Unit,
    onChooseDuration: (ProfessionalAccessDuration) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.professional_invite_action),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.professional_invite_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val code = state.invite?.code
            if (code == null) {
                InviteChoices(state, onChooseRole, onChooseDuration, onCreate)
            } else {
                InviteCode(code)
            }
            state.errorRes?.let { res ->
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteChoices(
    state: ProfessionalInviteState,
    onChooseRole: (ProfessionalRole) -> Unit,
    onChooseDuration: (ProfessionalAccessDuration) -> Unit,
    onCreate: () -> Unit
) {
    GroupLabel(stringResource(R.string.professional_invite_role_label))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProfessionalRole.entries.forEach { role ->
            ChoiceChip(stringResource(role.labelRes()), role == state.role) { onChooseRole(role) }
        }
    }
    GroupLabel(stringResource(R.string.professional_invite_duration_label))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfessionalAccessDuration.entries.forEach { duration ->
            ChoiceChip(stringResource(duration.labelRes()), duration == state.duration) {
                onChooseDuration(duration)
            }
        }
    }
    Button(onClick = onCreate, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
        if (state.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.professional_invite_create))
        }
    }
}

/** One selectable chip, in the neutral selected style `FriendInviteSheet` uses. */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    PillChip(
        label = label,
        container = if (selected) MaterialTheme.colorScheme.secondaryContainer else null,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        onClick = onClick,
        selected = selected
    )
}

/** The minted code, large enough to read out, and the share sheet for sending it. */
@Composable
private fun InviteCode(code: String) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.professional_invite_code_label),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = code,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp
    )
    OutlinedButton(
        onClick = { context.startActivity(shareIntent(context, code)) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.professional_invite_share))
    }
}

/**
 * The Play Store listing, for a professional who does not have the app yet — the same link the
 * co-parent invitation carries (`PairingScreen`).
 */
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=app.coplanly"

/** Builds the share-sheet intent for a professional code. */
private fun shareIntent(context: Context, code: String): Intent {
    val message = context.getString(R.string.professional_share_message, code, PLAY_STORE_URL)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    return Intent.createChooser(sendIntent, context.getString(R.string.professional_invite_share))
}
