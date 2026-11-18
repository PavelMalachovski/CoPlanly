package com.coparently.app.presentation.pairing.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.presentation.common.NativeControls
import com.coparently.app.presentation.common.nativeControls
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.headlineSmallEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.pairing_code_field_label
import com.coparently.app.resources.pairing_have_a_code
import com.coparently.app.resources.pairing_link_accounts
import org.jetbrains.compose.resources.stringResource

/**
 * Input for a code the user was given. Accepts a pasted pairing link or share
 * message too — the ViewModel extracts the code from it.
 *
 * [onSubmit] null leaves out the built-in "Link accounts" button, for a caller whose submit
 * means something else and carries its own wording and progress (the guest code screen used to
 * show both buttons, the first one worded for pairing).
 *
 * Always fills the width of its container, so unlike the other pairing
 * components it takes no `modifier` — adding one would push this composable
 * past detekt's parameter-count limit for no actual caller need.
 */
@Composable
fun CodeEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (() -> Unit)?,
    errorText: String?,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Text(
            text = stringResource(Res.string.pairing_have_a_code),
            style = MaterialTheme.typography.titleMedium
        )
        CodeFieldRow(native = nativeControls()?.takeIf { enabled }, onPaste = onValueChange) { fieldModifier ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(stringResource(Res.string.pairing_code_field_label)) },
                singleLine = true,
                enabled = enabled,
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.headlineSmallEmphasized.copy(letterSpacing = 6.sp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (value.length == InviteCodeGenerator.LENGTH) onSubmit?.invoke() }
                ),
                modifier = fieldModifier
            )
        }
        if (onSubmit != null) {
            Button(
                onClick = onSubmit,
                enabled = enabled && value.length == InviteCodeGenerator.LENGTH,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.pairing_link_accounts))
            }
        }
    }
}

/**
 * Lays the code field out: the whole width as Android has always drawn it when [native] is null,
 * and beside the system's paste button on the iPhone, whose text goes to [onPaste] as typed text
 * would. [field] draws the field inside the modifier it is handed.
 */
@Composable
private fun CodeFieldRow(
    native: NativeControls?,
    onPaste: (String) -> Unit,
    field: @Composable (Modifier) -> Unit
) {
    if (native == null) {
        field(Modifier.fillMaxWidth())
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            verticalAlignment = Alignment.Top
        ) {
            field(Modifier.weight(1f))
            native.PasteButton(
                onPaste = onPaste,
                background = MaterialTheme.colorScheme.background,
                modifier = Modifier.padding(top = PASTE_BUTTON_TOP)
            )
        }
    }
}

/**
 * How far below the field's top the paste button starts, so its 44 dp sit centred on the 56 dp
 * input: the outlined input starts 8 dp down (half its floating label) and the button 6 dp further,
 * (56 - 44) / 2. Off the 4 dp grid on purpose, and measured from the input, never from the field
 * with its error line, so the button does not drop when an error shows.
 */
private val PASTE_BUTTON_TOP = 14.dp
