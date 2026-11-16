package com.coparently.app.presentation.common

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The one way a shared screen draws an icon (MON-24 phase 3, part D): Material's `Icon` on Android,
 * line for line, and the icon's SF Symbol on the iPhone where one is mapped. Its parameters are
 * Material's `Icon`'s, in the same order and with the same names and defaults, so a call site reads
 * as it did. `AppIconGuardTest` refuses a direct Material `Icon` in `commonMain`.
 *
 * @param imageVector The Material icon, which also names the symbol on the iPhone
 * @param contentDescription What TalkBack or VoiceOver says, or null for a decorative icon
 * @param modifier The icon's modifier
 * @param tint The icon's colour
 */
@Composable
expect fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
)
