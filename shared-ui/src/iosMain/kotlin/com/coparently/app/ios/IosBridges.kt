package com.coparently.app.ios

import com.coparently.app.data.remote.firebase.IosPushToken

/**
 * What the iPhone app's Swift side hands [IosApp.start] beside its configuration: the platform's
 * bridges, each a Kotlin interface the Swift side implements (phase 4 part D gathered them here).
 *
 * @property google Google's sign-in sheet (`GoogleSignInBridge`): the sign-in screen offers it only
 *   where it is available ([IosCredentialSignIn]), Settings connects Google Calendar through it where
 *   the build names the web client (`IosGoogleCalendarSignIn`), and its session ends with ours
 *   (`SignInProviderSession`).
 * @property apple Apple's sign-in sheet (`AppleSignInBridge`); the sign-in screen and the sign-in
 *   methods offer it only while the bundle's `CoplanlyAppleSignIn` flag is on
 *   ([IosCredentialSignIn.appleAvailable]).
 * @property pushToken This iPhone's Firebase Messaging token (`PushTokenBridge`): what the push
 *   registration keeps on the profile and detaches at sign-out (phase 4 part C).
 * @property widgetReload Asks WidgetKit to draw the Today widget again (`WidgetReloadBridge`): what
 *   the widget's snapshot writer calls once it has written a snapshot that changed (phase 4 part D).
 * @property background The background refresh's scheduling over `BGTaskScheduler`
 *   (`BackgroundRefreshBridge`): a request submitted on entering the background and at the end of
 *   each run while somebody is signed in, and cancelled when nobody is (sign-out, deletion; phase 4
 *   part D, [IosApp.runBackgroundRefresh]).
 */
class IosBridges(
    val google: GoogleIdTokenSource,
    val apple: AppleIdSource,
    val pushToken: IosPushToken,
    val widgetReload: WidgetReload,
    val background: BackgroundScheduling,
)
