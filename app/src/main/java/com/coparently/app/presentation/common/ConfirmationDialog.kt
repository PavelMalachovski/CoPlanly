package com.coparently.app.presentation.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coparently.app.R

/**
 * Reusable confirmation dialog component.
 * Used for critical actions that require user confirmation.
 *
 * @param title Dialog title
 * @param message Confirmation message
 * @param confirmText Text for confirm button (default: `common_confirm`, localised — these defaults
 *   used to be English literals, which every caller that forgot to pass one showed as-is)
 * @param dismissText Text for dismiss button (default: `common_dialog_cancel`, localised)
 * @param onConfirm Callback when user confirms
 * @param onDismiss Callback when user dismisses
 * @param modifier Optional modifier
 * @param isDestructive If true, uses error colors for confirm button
 */
@Composable
@Suppress("LongParameterList") // the dialog's whole anatomy; every caller names its arguments
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = stringResource(R.string.common_confirm),
    dismissText: String = stringResource(R.string.common_dialog_cancel),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        // Both answers are text buttons, as Material's dialogs have them: this one confirmed with
        // a filled button while every other dialog in the app used text (D-20). A destructive
        // confirm says so in the error colour rather than with a red slab.
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
        modifier = modifier
    )
}
