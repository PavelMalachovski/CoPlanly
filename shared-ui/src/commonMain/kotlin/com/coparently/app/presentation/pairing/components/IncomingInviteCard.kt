package com.coparently.app.presentation.pairing.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.coparently.app.domain.model.PairingInvite
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.pairing_accept_button
import com.coparently.app.resources.pairing_reject_button
import com.coparently.app.resources.pairing_unknown_sender
import org.jetbrains.compose.resources.stringResource

/** One invitation addressed to this user, with accept and decline. */
@Composable
fun IncomingInviteCard(
    invite: PairingInvite,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.L),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = invite.fromUserName.ifEmpty {
                        stringResource(Res.string.pairing_unknown_sender)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = invite.fromUserEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                TextButton(onClick = onAccept) {
                    Text(stringResource(Res.string.pairing_accept_button))
                }
                TextButton(onClick = onReject) {
                    Text(stringResource(Res.string.pairing_reject_button))
                }
            }
        }
    }
}
