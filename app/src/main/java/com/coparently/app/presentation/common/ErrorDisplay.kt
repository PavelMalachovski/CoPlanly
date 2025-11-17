package com.coparently.app.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Composable to display error messages with retry option.
 * Automatically selects appropriate icon and styling based on error type.
 *
 * @param error Error details to display
 * @param modifier Modifier for customization
 * @param onDismiss Optional callback when error is dismissed
 */
@Composable
fun ErrorDisplay(
    error: UiError,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Error icon
                Icon(
                    imageVector = getErrorIcon(error.type),
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )

                // Error content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = getErrorTitle(error.type),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    // Retry button if available
                    if (error.retry != null) {
                        Button(
                            onClick = error.retry,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }

                // Dismiss button
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact error snackbar style display.
 */
@Composable
fun ErrorSnackbar(
    error: UiError,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier,
        action = if (error.retry != null) {
            {
                TextButton(onClick = error.retry) {
                    Text("Retry")
                }
            }
        } else null,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getErrorIcon(error.type),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(error.message)
        }
    }
}

/**
 * Full screen error display for critical errors.
 */
@Composable
fun ErrorScreen(
    error: UiError,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = getErrorIcon(error.type),
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = getErrorTitle(error.type),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (error.retry != null) {
                Button(
                    onClick = error.retry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again")
                }
            }
        }
    }
}

/**
 * Loading overlay with optional error handling.
 */
@Composable
fun LoadingOverlay(
    uiState: UiState<*>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()

        when (uiState) {
            is UiState.Loading -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            if (uiState.message != null) {
                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            is UiState.Error -> {
                ErrorDisplay(
                    error = uiState.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
            else -> { /* No overlay needed */ }
        }
    }
}

/**
 * Gets appropriate icon for error type.
 */
private fun getErrorIcon(type: ErrorType) = when (type) {
    ErrorType.NETWORK -> Icons.Default.Refresh
    ErrorType.AUTHENTICATION -> Icons.Default.Person
    ErrorType.PERMISSION -> Icons.Default.Lock
    ErrorType.NOT_FOUND -> Icons.Default.Search
    ErrorType.VALIDATION -> Icons.Default.Info
    ErrorType.SERVER -> Icons.Default.Warning
    ErrorType.UNKNOWN -> Icons.Default.Close
}

/**
 * Gets appropriate title for error type.
 */
private fun getErrorTitle(type: ErrorType) = when (type) {
    ErrorType.NETWORK -> "Network Error"
    ErrorType.AUTHENTICATION -> "Authentication Error"
    ErrorType.PERMISSION -> "Permission Denied"
    ErrorType.NOT_FOUND -> "Not Found"
    ErrorType.VALIDATION -> "Validation Error"
    ErrorType.SERVER -> "Server Error"
    ErrorType.UNKNOWN -> "Error"
}

