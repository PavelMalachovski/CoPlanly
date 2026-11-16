package com.coparently.app.presentation.common

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material's `Icon`, exactly the call every shared screen made before [AppIcon] existed.
 */
@Composable
actual fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color
) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier, tint = tint)
}
