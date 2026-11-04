package com.coparently.app.di.koin

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Nothing: on Android the app's own modules bind what [uiModules] takes from the platform — the
 * on-device speech recognizer (`dictationModule`), the AI callable and whether the build offers it
 * (`aiModule`, from `BuildConfig`) and the invitation's QR code (`InviteQrCodes`, in
 * `presentationModule`, over `QRCodeService` and `BuildConfig.PAIRING_WEB_LINK`) — beside
 * [platformDataModule]'s seams.
 */
actual val platformUiModule: Module = module { }
