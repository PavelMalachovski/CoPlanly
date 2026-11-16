package com.coparently.app.presentation.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.resources.Res
import com.coparently.app.resources.auth_email_label
import com.coparently.app.resources.auth_email_placeholder
import com.coparently.app.resources.auth_hide_password
import com.coparently.app.resources.auth_password_label
import com.coparently.app.resources.auth_password_placeholder
import com.coparently.app.resources.auth_show_password
import org.jetbrains.compose.resources.stringResource

/**
 * The e-mail address field of the sign-in screen, and of Settings' "Sign-in methods" when an e-mail
 * and password are added to the account (MON-24 phase 4, part A): one field, so the two read alike
 * and both platforms' autofill offer the same addresses.
 *
 * @param value The address typed so far
 * @param onValueChange Called with every edit
 * @param enabled False while a sign-in or a link is running
 * @param modifier Placed after the field's full width and its autofill hint
 */
@Composable
fun AuthEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(Res.string.auth_email_label)) },
        placeholder = { Text(stringResource(Res.string.auth_email_placeholder)) },
        leadingIcon = {
            AppIcon(
                imageVector = Icons.Default.Email,
                contentDescription = null
            )
        },
        // The autofill hint, which both platforms' autofill read (Task 9).
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.EmailAddress }
            .then(modifier),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        shape = MaterialTheme.shapes.small
    )
}

/**
 * The password field of the sign-in screen and of Settings' "Sign-in methods", with its show and
 * hide button.
 *
 * @param value The password typed so far
 * @param onValueChange Called with every edit
 * @param enabled False while a sign-in or a link is running
 * @param newPassword True when the password is a new one (a new account, or e-mail added in
 *   Settings), so autofill can offer to make one; false for an existing account's password
 * @param modifier Placed after the field's full width and its autofill hint
 */
@Composable
fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    newPassword: Boolean,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(Res.string.auth_password_label)) },
        placeholder = { Text(stringResource(Res.string.auth_password_placeholder)) },
        leadingIcon = {
            AppIcon(
                imageVector = Icons.Default.Lock,
                contentDescription = null
            )
        },
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                AppIcon(
                    imageVector = if (passwordVisible) {
                        Icons.Default.VisibilityOff
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = if (passwordVisible) {
                        stringResource(Res.string.auth_hide_password)
                    } else {
                        stringResource(Res.string.auth_show_password)
                    }
                )
            }
        },
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        // A new password is a new one, so autofill can offer to make one.
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentType = if (newPassword) ContentType.NewPassword else ContentType.Password
            }
            .then(modifier),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        shape = MaterialTheme.shapes.small
    )
}
