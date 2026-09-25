package com.coparently.app.presentation.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing

/**
 * The message composer: a borderless pill field beside a round filled send button — the
 * messenger anatomy, adopted in the August 2026 second pass. The field is a filled
 * `TextField` on `surfaceContainerHigh` (the same step incoming bubbles sit on) rather than
 * an outlined one: the pill *is* the boundary, and a stroked border on top of it read as a
 * form input in the middle of a conversation. The send button is disabled — not hidden —
 * while the field is blank, so the affordance never jumps around as the user types.
 *
 * Stateless: the text lives in `ChatScreen`, because more than one thing seeds it — a draft
 * arriving from Expenses, and a message template — and a composable that owns its own text
 * cannot be re-seeded with a value it already held. Picking the same template twice has to
 * work.
 *
 * The leading `+` this used to carry is gone. It was captioned "attach" but opened message
 * templates — the August 2026 audit's clearest "icon promises one thing, does another". The
 * templates now live in a labelled chip above this row (see `ChatScreen`), and the real attach
 * button landed with attachments themselves (MON-23): `ChatAttachButton`, beside this row in
 * `ChatScreen`, not inside it.
 *
 * @param value Current composer text
 * @param onValueChange Called on every edit
 * @param onSendMessage Called with the text when the user sends; the caller clears [value]
 * @param modifier Modifier for the row
 * @param focusRequester Lets the caller move focus here when it seeds the field
 * @param placeholder What the empty field says ("Listening…" while dictating)
 * @param trailingIcon Drawn inside the pill at its end — the voice-dictation microphone
 *   (`DictationMicButton`), only on phones that can recognise speech on the device. Inside the
 *   field rather than a fourth control in the row, so the send button keeps its place.
 */
@Composable
// Stateless on purpose (the caller owns the text and the dictation state), so everything the
// field shows arrives as a parameter; all but the first three have defaults.
@Suppress("LongParameterList")
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    placeholder: String = stringResource(R.string.chat_type_message),
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        // Bottom, not centre: when the field grows to a second line the send button stays
        // anchored beside the line being typed, the way every messenger composer behaves.
        verticalAlignment = Alignment.Bottom
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(end = Spacing.S)
                .focusRequester(focusRequester),
            placeholder = { Text(placeholder) },
            trailingIcon = trailingIcon,
            shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            maxLines = 4
        )

        FilledIconButton(
            onClick = { if (value.isNotBlank()) onSendMessage(value) },
            enabled = value.isNotBlank(),
            modifier = Modifier.size(SEND_BUTTON_SIZE),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.chat_send),
                modifier = Modifier.size(IconSizes.Standard)
            )
        }
    }
}

/** Diameter of the round send button — sized to the pill field's single-line height. */
private val SEND_BUTTON_SIZE = 48.dp
