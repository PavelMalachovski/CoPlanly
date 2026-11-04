package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** The window's container width in dp. */
@Composable
internal actual fun windowWidthDp(): Int =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }
