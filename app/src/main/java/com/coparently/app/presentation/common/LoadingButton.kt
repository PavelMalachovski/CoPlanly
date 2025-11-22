package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A button that displays a loading indicator when an operation is in progress.
 * Prevents multiple clicks and provides visual feedback during async operations.
 *
 * Features:
 * - Automatic disabling during loading
 * - Loading indicator with optional text
 * - Prevents multiple submissions
 * - Customizable appearance
 *
 * @param onClick Callback when button is clicked (not called when loading)
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled (independent of loading state)
 * @param isLoading Whether to show loading indicator
 * @param loadingText Optional text to show during loading
 * @param colors Button colors (defaults to primary colors)
 * @param content Button content (icon, text, etc.)
 */
@Composable
fun LoadingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingText: String? = null,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        colors = colors
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )

            if (loadingText != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(loadingText)
            }
        } else {
            content()
        }
    }
}

/**
 * A text button variant that displays a loading indicator.
 * Similar to LoadingButton but with text button styling.
 *
 * @param onClick Callback when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param isLoading Whether to show loading indicator
 * @param loadingText Optional text to show during loading
 * @param content Button content
 */
@Composable
fun LoadingTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingText: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )

            if (loadingText != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(loadingText)
            }
        } else {
            content()
        }
    }
}

/**
 * An outlined button variant that displays a loading indicator.
 * Similar to LoadingButton but with outlined button styling.
 *
 * @param onClick Callback when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param isLoading Whether to show loading indicator
 * @param loadingText Optional text to show during loading
 * @param content Button content
 */
@Composable
fun LoadingOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingText: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )

            if (loadingText != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(loadingText)
            }
        } else {
            content()
        }
    }
}
