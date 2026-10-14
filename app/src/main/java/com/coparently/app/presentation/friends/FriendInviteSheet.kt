package com.coparently.app.presentation.friends

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.guests.GuestAccessDuration
import com.coparently.app.presentation.common.InviteCodeText
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.pairing.PLAY_STORE_URL
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyMediumEmphasized

/**
 * Offering somebody read access to the family calendar (item 16).
 *
 * Two steps, in this order and not the other, exactly as `GuestInviteSheet` argues: choose how
 * long, then get a code. The length is not an afterthought left on a default nobody read — it is
 * the only thing between "the grandmother has the children this weekend" and a stranger reading
 * the family's calendar forever, so it is chosen before the code exists and cannot be changed
 * after, since by then the invitation document already carries it.
 *
 * @param state What to show — see [FriendInviteState].
 * @param myName The inviting parent's own name, for the shared message; null while unknown.
 * @param onChooseDuration Picks a different length; ignored once a code exists.
 * @param onCreate Mints the invitation.
 * @param onDismiss Closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendInviteSheet(
    state: FriendInviteState,
    myName: String?,
    onChooseDuration: (GuestAccessDuration) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        FriendInviteContent(
            state = state,
            onChooseDuration = onChooseDuration,
            onCreate = onCreate,
            onShare = { code -> context.startActivity(friendShareIntent(context, myName, code)) },
            onDone = onDismiss
        )
    }
}

/**
 * The body of [FriendInviteSheet], without the sheet around it, so the screenshot tests can
 * render it (a `ModalBottomSheet` opens its own window).
 *
 * The sheet says what the friend will and will not see (phase 2, spec §4.3) before anything else:
 * an access grant must not be quieter about its scope than the person accepting it would expect.
 * Once the code exists it says where the friend types it, and shares it — read out over the phone
 * is how these used to travel, with no way to send one at all.
 *
 * @param state What to show — see [FriendInviteState].
 * @param onChooseDuration Picks a different length; ignored once a code exists.
 * @param onCreate Mints the invitation.
 * @param onShare Opens the share sheet for the minted code.
 * @param onDone Closes the sheet; the invitation stands.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FriendInviteContent(
    state: FriendInviteState,
    onChooseDuration: (GuestAccessDuration) -> Unit,
    onCreate: () -> Unit,
    onShare: (String) -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.XL)
            .padding(bottom = Spacing.XL),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Text(
            text = stringResource(R.string.friend_invite_action),
            style = MaterialTheme.typography.titleLarge
        )
        ScopeLine(
            icon = Icons.Default.Check,
            lead = stringResource(R.string.friend_invite_will_see_lead),
            body = stringResource(R.string.friend_invite_will_see_body)
        )
        ScopeLine(
            icon = Icons.Default.Close,
            lead = stringResource(R.string.friend_invite_wont_see_lead),
            body = stringResource(R.string.friend_invite_wont_see_body)
        )

        val code = state.invite?.code
        if (code == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                GuestAccessDuration.entries.forEach { duration ->
                    PillChip(
                        label = stringResource(duration.labelRes()),
                        container = if (duration == state.duration) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            null
                        },
                        contentColor = if (duration == state.duration) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = { onChooseDuration(duration) },
                        selected = duration == state.duration
                    )
                }
            }
            CreateButton(isBusy = state.isBusy, onCreate = onCreate)
        } else {
            MintedCode(code = code, onShare = onShare, onDone = onDone)
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

/** "Will see …" or "Won't see …": the lead in the emphasized role, the rest as body text. */
@Composable
private fun ScopeLine(icon: ImageVector, lead: String, body: String) {
    val emphasis = MaterialTheme.typography.bodyMediumEmphasized.toSpanStyle()
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.XXS).size(IconSizes.Small)
        )
        Text(
            text = buildAnnotatedString {
                withStyle(emphasis) { append(lead) }
                append(' ')
                append(body)
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CreateButton(isBusy: Boolean, onCreate: () -> Unit) {
    Button(
        onClick = onCreate,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.friend_invite_action))
        }
    }
}

/**
 * Step two: the code, large enough to read out over the phone, where the friend types it, and the
 * way to send it.
 */
@Composable
private fun MintedCode(code: String, onShare: (String) -> Unit, onDone: () -> Unit) {
    Text(
        text = stringResource(R.string.friend_invite_code_label),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    InviteCodeText(code = code)
    Text(
        text = stringResource(R.string.friend_invite_where_to_enter),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(onClick = { onShare(code) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.friend_invite_share))
    }
    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.friend_invite_done))
    }
}

/**
 * The share sheet for a friend's code: who invites, the code, where to type it and where to get
 * the app, in one message, as the co-parent and professional invitations are shared.
 */
private fun friendShareIntent(context: Context, myName: String?, code: String): Intent {
    val message = context.getString(R.string.friend_share_message, myName.orEmpty(), code, PLAY_STORE_URL)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    return Intent.createChooser(send, context.getString(R.string.friend_invite_share))
}
