package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.coparently.app.presentation.theme.Spacing

/**
 * The one way a parent answers a request (UX audit M1): an outlined Decline and a filled Accept,
 * sharing the width in that order, and an optional text "Later" under them.
 *
 * Home's dialogs had three text buttons with no emphasis, the swap card a filled Accept beside a
 * text Decline and the change-request card a filled Accept beside an outlined one — four looks for
 * one question, and in Russian at 130 % the dialog's "Accept" was pushed onto a line of its own
 * above the other two. One component draws them all now. From [STACK_CONTROLS_FONT_SCALE] the two
 * answers stand one above the other at full width ([SideBySideOrStacked]), so neither label breaks.
 *
 * @param acceptLabel The positive answer, worded for what is accepted.
 * @param declineLabel The negative answer.
 * @param onAccept Accepts; null leaves Accept out, which a card past its deadline does while only
 *   Decline is still possible (`CardClock.declineOnly`).
 * @param onDecline Declines.
 * @param later The "ask me later" label and action of a dialog, or null on a card, which stays put.
 */
@Composable
fun AnswerButtons(
    acceptLabel: String,
    declineLabel: String,
    onAccept: (() -> Unit)?,
    onDecline: () -> Unit,
    later: Pair<String, () -> Unit>? = null
) {
    val decline: @Composable (Modifier) -> Unit = { sized ->
        OutlinedButton(onClick = onDecline, modifier = sized) { Text(declineLabel) }
    }
    val accept: (@Composable (Modifier) -> Unit)? = if (onAccept == null) {
        null
    } else {
        { sized -> Button(onClick = onAccept, modifier = sized) { Text(acceptLabel) } }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SideBySideOrStacked(controls = listOfNotNull(decline, accept))
        later?.let { (label, action) ->
            TextButton(onClick = action) { Text(label) }
        }
    }
}
