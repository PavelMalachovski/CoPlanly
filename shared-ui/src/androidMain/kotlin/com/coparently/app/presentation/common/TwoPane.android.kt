package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** The configuration's `screenWidthDp`, which follows a fold, a rotation and a multi-window resize. */
@Composable
internal actual fun windowWidthDp(): Int = LocalConfiguration.current.screenWidthDp
