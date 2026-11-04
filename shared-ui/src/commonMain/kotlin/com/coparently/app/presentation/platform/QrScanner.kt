package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable

/** The full-screen QR scanner that reads a co-parent's invitation code (pairing). */
interface QrScanner : PlatformCapability {

    /**
     * A launcher for the scanner.
     *
     * @param onScanned Receives a code the scanner read and validated; a scan closed without one
     *   calls nothing
     */
    @Composable
    fun rememberScanner(onScanned: (code: String) -> Unit): PlatformLauncher
}
