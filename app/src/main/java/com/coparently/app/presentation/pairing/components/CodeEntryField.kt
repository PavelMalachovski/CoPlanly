package com.coparently.app.presentation.pairing.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import com.coparently.app.R
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.headlineSmallEmphasized

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
            text = stringResource(R.string.pairing_have_a_code),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.pairing_code_field_label)) },
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
            modifier = Modifier.fillMaxWidth()
        )
        if (onSubmit != null) {
            Button(
                onClick = onSubmit,
                enabled = enabled && value.length == InviteCodeGenerator.LENGTH,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pairing_link_accounts))
            }
        }
    }
}
