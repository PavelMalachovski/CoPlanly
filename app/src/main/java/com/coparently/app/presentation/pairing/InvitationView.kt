package com.coparently.app.presentation.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.coparently.app.R
import com.coparently.app.presentation.common.HeroIcon
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.StickyActionBar
import com.coparently.app.presentation.theme.Spacing

/**
 * The invitation a parent who is not linked yet answers before a code is redeemed — in place of
 * the "Link with {name}?" dialog a linked parent still sees for a second co-parent's code.
 *
 * Most first runs are this parent's: invited by the other parent, not looking for an app, and
 * often wary that it is meant against them. So the screen says who invited them, what CoPlanly is
 * in one line, and four rules that hold for both parents alike — each one true of the app today
 * (design item 8). The schedule fact is worded narrowly on purpose: an event marked important
 * lands on both calendars without an answer, so "nothing changes without your yes" would be a
 * promise the app does not keep.
 *
 * Every way a code arrives — typed, scanned, a link, the install referrer — passes through
 * [PairingViewModel.redeemCode] and reaches this view; nothing is redeemed until the parent taps
 * the primary button. Back and "Not now" decline.
 *
 * @param inviterName The first name the server gave for the code's inviter, or null when it could
 *   not say; the title then invites without a name.
 * @param onAnswer True to redeem the code, false to decline.
 * @param modifier Applied to the screen's root.
 */
@Composable
internal fun InvitationView(
    inviterName: String?,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onAnswer(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            StickyActionBar(
                label = stringResource(R.string.pairing_invitation_link),
                onClick = { onAnswer(true) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.XL, vertical = Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            HeroIcon(Icons.Default.Groups)
            Text(
                text = if (inviterName != null) {
                    stringResource(R.string.pairing_invitation_title, inviterName)
                } else {
                    stringResource(R.string.pairing_invitation_title_unnamed)
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.pairing_invitation_lede),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            InvitationFacts()
            TextButton(onClick = { onAnswer(false) }) {
                Text(stringResource(R.string.pairing_invitation_not_now))
            }
        }
    }
}

/** The four rules both parents share, each true of the app today (see [InvitationView]). */
@Composable
private fun InvitationFacts() {
    SectionGroup(modifier = Modifier.fillMaxWidth()) {
        SectionRow(
            icon = Icons.Default.CalendarMonth,
            title = stringResource(R.string.pairing_invitation_fact_same)
        )
        Divider()
        SectionRow(
            icon = Icons.Default.ThumbUp,
            title = stringResource(R.string.pairing_invitation_fact_agree)
        )
        Divider()
        SectionRow(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.pairing_invitation_fact_messages)
        )
        Divider()
        SectionRow(
            icon = Icons.Default.LinkOff,
            title = stringResource(R.string.pairing_invitation_fact_unlink)
        )
    }
}

/**
 * [InvitationView] over the pairing screen, as a full-window dialog, so the screen beneath keeps
 * its state and effects while the parent decides. Back is handled by the view itself.
 */
@Composable
internal fun InvitationDialog(confirmation: InviteConfirmation, onAnswer: (Boolean) -> Unit) {
    Dialog(
        onDismissRequest = { onAnswer(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        InvitationView(inviterName = confirmation.inviterName, onAnswer = onAnswer)
    }
}

/**
 * How a code waiting for the parent's yes is asked about. A parent not yet linked sees the full
 * invitation — who invited them and the rules both parents share; one already linked, entering a
 * second co-parent's code, the short dialog, whose "invites you to CoPlanly" would be wrong for them.
 */
@Composable
internal fun InviteQuestion(confirmation: InviteConfirmation, isLinked: Boolean, onAnswer: (Boolean) -> Unit) {
    if (isLinked) InviteConfirmationDialog(confirmation, onAnswer) else InvitationDialog(confirmation, onAnswer)
}
