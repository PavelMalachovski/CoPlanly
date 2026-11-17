# CoPlanly for iPhone

The iPhone app (MON-24 phase 3). It is a thin shell, as the Android app is: every screen, ViewModel
and the data layer are the Kotlin Multiplatform modules', compiled into one static framework,
`Shared` (`:shared-ui`, exporting `:shared` and `:shared-data`). What lives here:

| Path | What it is |
| --- | --- |
| `project.yml` | The Xcode project, for [XcodeGen](https://github.com/yonaskolb/XcodeGen). The generated `CoPlanly.xcodeproj/` is not committed. |
| `CoPlanly/AppDelegate.swift` | The entry point: reads the configuration and calls `IosApp.shared.start(config:google:apple:)` with the Google and Apple sheets' bridges (`shared-ui/src/iosMain/kotlin/com/coparently/app/ios/`). |
| `CoPlanly/SceneDelegate.swift` | The window: shows `IosApp.shared.rootViewController()`, the native shell, and hands Google's sign-in answers (`openURLContexts`) to Google Sign-In. |
| `CoPlanly/GoogleSignInBridge.swift` | Google's sign-in sheet (Google Sign-In for iOS 9.2.0) for the shared sign-in screen and for connecting Google Calendar; see "Google sign-in" and "Google Calendar" below. |
| `CoPlanly/AppleSignInBridge.swift` | Apple's sign-in sheet (AuthenticationServices) for the sign-in screen and the sign-in methods, behind the `CoplanlyAppleSignIn` flag (YES in Debug, NO in Release; `project.yml`). |
| `CoPlanly/Info.plist` | Bundle `app.coplanly`, display name CoPlanly, iPhone only, portrait only, iOS 17.0 and later. Two build settings reach it: `CoplanlyAppleSignIn` (from `COPLANLY_APPLE_SIGN_IN`) and `GIDServerClientID` (from `COPLANLY_GOOGLE_WEB_CLIENT_ID`). |
| `CoPlanly/CoPlanly.entitlements` | Sign in with Apple (`com.apple.developer.applesignin`), pushes (`aps-environment`) and the App Group `group.app.coplanly`. |
| `CoPlanly/Assets.xcassets` | The icon (made from the Android launcher icon by `tools/generate-ios-icon.py`; never edit the PNG by hand) and the indigo accent colour. |
| `CoPlanlyUITests/` | The XCUITest walk of the native shell (`ShellUITests.testShellWalk`) and the tour of every screen (`testTour`), run in CI on the emulators. |
| `scripts/simulator.sh` | CI's simulator steps (the `ios-app` job): pick, launch, the shell's walk and the tour. |

## Running it on your Mac

1. **Xcode 26** from the App Store, opened once so it installs its components and an iOS
   simulator runtime. The project's Kotlin (2.4.21) supports Xcode up to 26.4; a newer Xcode prints
   a compatibility warning during the Gradle phase and has worked in CI so far. See
   `docs/IOS-RUNNER.md` for installing an older Xcode beside a newer one.
2. **A JDK 17** on your `PATH` or in `JAVA_HOME`, as for the Android build. Xcode's build phase runs
   `./gradlew`, which needs it.
3. **XcodeGen**: `brew install xcodegen`.
4. Generate the project, from the repository's root:

   ```bash
   cd iosApp
   xcodegen generate
   open CoPlanly.xcodeproj
   ```

   Run `xcodegen generate` again whenever `project.yml` changes, or after adding or removing a file
   in `CoPlanly/`.
5. Choose an iPhone simulator and **Run** (⌘R). The first build takes a while: Swift Package
   Manager fetches the Firebase iOS SDK (11.8.0), and the build phase "Build the Shared framework
   (Gradle)" runs `./gradlew :shared-ui:embedAndSignAppleFrameworkForXcode`, which compiles the
   shared modules for the simulator into `shared-ui/build/xcode-frameworks/`. Later builds
   compile only what changed.

To run on a phone of your own, set your team under Signing & Capabilities in Xcode (it is not
committed); nothing else changes.

## Which Firebase it talks to

**The emulators, unless you give it the real project.** Without a `GoogleService-Info.plist` in
`CoPlanly/`, the app starts Firebase with fake options for the `demo-coplanly` project and points
Firestore (8080), Functions (5001) and Auth (9099) at `127.0.0.1`, so an account you create on the
sign-in screen lives in the Auth emulator. Start the emulators from `firestore-tests/`,
which pins the Firebase CLI:

```bash
cd firestore-tests
npm ci
npx firebase emulators:start --project demo-coplanly --only auth,firestore,functions,storage
```

(`functions/` needs `npm ci` first for the Functions emulator, and the emulators need JDK 21, as
`firestore-tests/README.md` says.) Another host — your Mac's address, for a phone on the same
network — goes in the scheme's environment as `COPLANLY_EMULATOR_HOST`, or in the target's
`COPLANLY_EMULATOR_HOST` build setting.

**The real project**: download `GoogleService-Info.plist` for the iOS app `app.coplanly` from the
Firebase console, put it in `iosApp/CoPlanly/`, and run `xcodegen generate` again so the project
copies it into the app. **Never commit it**: `.gitignore` covers it, and CI never has one, so a CI
build can only ever reach the emulators. A configured emulator host still wins over the file.

## Google sign-in

The sign-in screen offers **Continue with Google** only when the app can run Google's sheet; without
it, the screen offers e-mail alone (CI and the emulators never offer Google). On your Mac:

1. **The iOS OAuth client.** In the Firebase console, Authentication → Sign-in method, Google must
   be enabled (it is, for Android). Then download `GoogleService-Info.plist` for the iOS app
   `app.coplanly` again: once Google sign-in is enabled it carries `CLIENT_ID` and
   `REVERSED_CLIENT_ID`, the iOS OAuth client Firebase creates in the Google Cloud project. If the
   file has neither, open Google Cloud console → APIs & Services → Credentials, check that an
   "iOS client" for bundle `app.coplanly` exists (create one if not), and download the file again.
2. **Put it in `iosApp/CoPlanly/`** and run `xcodegen generate`, as for "The real project" above.
   Never commit it.
3. **The URL scheme registers itself.** The build phase "Register the app's URL schemes" reads
   `REVERSED_CLIENT_ID` from that file and adds it to the built app's `CFBundleURLTypes`, which
   Google's sheet returns through, after the app's own `coplanly` scheme, which every build
   registers for the `coplanly://pair`, `guest` and `chat` links. To use another scheme, set the target's build setting
   `COPLANLY_GOOGLE_URL_SCHEME` locally (Xcode → the CoPlanly target → Build Settings, or an
   uncommitted `.xcconfig`); it is blank in `project.yml` and must stay so.
4. Run the app and sign in with Google. The app checks both halves at launch
   (`GoogleSignInBridge.available`): a `CLIENT_ID`, and its reversed id among the app's URL schemes.
   With either missing it offers no Google, rather than letting Google Sign-In stop the app.

Saved passwords are iOS's own: the e-mail and password fields carry the e-mail and password
keyboard types, which Compose Multiplatform turns into the text content types iOS autofill reads, so
the keyboard offers the Keychain's passwords; there is no separate button for them. The fields'
`ContentType` semantics do not reach iOS in Compose Multiplatform 1.9.3 (CMP-7154), so `NewPassword`
on sign-up is not honoured there yet.

Signing out of the app also ends Google Sign-In's own session (`GoogleSignInBridge.signOut`, which
calls `GIDSignIn.signOut`), and so does the first launch after an install, since the Keychain where
Google Sign-In keeps it survives an uninstall: on a shared phone the next "Continue with Google"
asks for an account rather than offering the previous parent's (part B, Task 12).

## Sign in with Apple

The sign-in screen and Settings → Account → Sign-in methods offer Apple through the system's
button and sheet (`AppleSignInBridge.swift`, with the Kotlin side in `IosCredentialSignIn`), only
while `CoplanlyAppleSignIn` is YES: Debug builds say YES and Release builds NO (`project.yml`'s
`COPLANLY_APPLE_SIGN_IN`), as Android's `BuildConfig.APPLE_SIGN_IN` is on in debug and off in
release. An Apple method already linked stays listed and removable with the flag off. Kotlin makes
the nonce and hands Swift only its SHA-256; after Firebase signs in, Apple's authorization code goes
to the `exchangeAppleAuthCode` callable, which keeps the refresh token on the server so it can be
revoked on unlink and deletion. Apple's name arrives only at the first sign-in and fills a blank
profile name, never one already set.

**The entitlement comes first.** `CoPlanly.entitlements` carries
`com.apple.developer.applesignin`, so a build for a phone, and every Release build, signs only once
the App ID `app.coplanly` has the Sign in with Apple capability. The simulator's local signing does
not need it. The owner's steps, in order:

1. **In the Apple Developer account**: the Sign in with Apple capability on `app.coplanly`, a
   Services ID with Firebase's return URL, and a Sign in with Apple key.
2. **In the Firebase console**: Apple enabled with the Services ID, the Team ID, the Key ID and
   the key.
3. **In `functions/.env`**: the five `APPLE_*` values, then the functions deployed.
4. **Turning the flag on in release** (`COPLANLY_APPLE_SIGN_IN` here and Android's
   `APPLE_SIGN_IN`), as its own pull request, after device check §3.42.

`functions/README.md`, "Sign in with Apple", has each step in full. Apple's sheet cannot run in CI:
the walk only signs in by e-mail and opens Sign-in methods.

## Google Calendar

Settings' Google Calendar row appears on the iPhone when the build carries the **web** OAuth
client id (the same value as Android's `default_web_client_id`) in the target's
`COPLANLY_GOOGLE_WEB_CLIENT_ID` build setting, which Info.plist passes on as `GIDServerClientID`,
and the Google sheet itself is available (see "Google sign-in"). It is blank in `project.yml`, so
without it the row is simply not drawn. A client id is no secret, but set it on your own Mac or
for the release build only, as the URL scheme. Connecting asks
Google for the same read-only scope as Android (`calendar.events.owned.readonly`) and a server auth
code, which the functions' `exchangeGoogleAuthCode` redeems with the web client
(`IosGoogleCalendarAccount`, `IosGoogleCalendarSignIn`). The tokens live in the app's Keychain
store, never in Google Sign-In's session; the import runs on the button and when the app comes to
the foreground.

Disconnecting, and signing out, clear the stored tokens first and then revoke the grant at Google's
revoke endpoint and call `GIDSignIn.disconnect`, best effort. GoogleSignIn-iOS has no way to force
a refresh token: if that revoke failed (for example offline), a reconnect may come back without
one, and the connection then falls to "reconnect" after about an hour (device check §3.42).

## Pushes

The app registers with Apple's push service at launch (no permission is asked for that), hands the
device token to Firebase Messaging (`PushTokenBridge.swift`, with Firebase's app-delegate swizzling
off in `Info.plist`) and the shared code keeps the FCM token on the profile, `users/{uid}.fcmToken`,
as Android does (`IosPushRegistration`). The notification permission is asked only where it is
needed: the Settings switch, an event's reminder, onboarding's notifications step. Settings' switch
reads on only with the permission given; off, sign-out and account deletion delete the field and
the token. A push that arrives while the app is open shows as a banner; a tapped one opens the
screen it is about, after switching to its family (`IosApp.openPush`). The app shares the signed-in
uid and its language with the notification extension through the App Group `group.app.coplanly`
(`AppGroupShare.kt`), cleared at sign-out. On the emulators (CI, no `GoogleService-Info.plist`)
Firebase Messaging is never touched and the switch's "on" fails for want of a token.

**The owner's steps**, before pushes reach a phone:

1. **In the Apple Developer account**: the Push Notifications and App Groups capabilities on
   `app.coplanly`, the group `group.app.coplanly`, and an APNs authentication key.
2. **In the Firebase console**: the APNs key uploaded under Cloud Messaging for the iOS app.
3. `aps-environment` is `development` in `CoPlanly.entitlements`; an App Store or TestFlight
   build's distribution profile signs it as `production`. Device check: a push reaches a phone
   built for development and one from TestFlight.

## In CI

The `ios-app` job ("iPhone app — build and launch (macOS)" in `.github/workflows/ci.yml`) runs on
`macos-latest` whenever the shared modules' jobs run, on any change under `iosApp/`, and on the
write gate's inputs (`tools/ios/`, `wire/current/`, `firestore.rules`, `functions/`;
`tools/ci-changes.js`, the `ios` output). It uses the image's Xcode (26.6 in November 2026, above
the 26.4 Kotlin 2.4.21 names; it warns and works) and a simulator it picks at run time: an
"iPhone 16" when the image has one, else the first iPhone on the newest iOS (an iPhone 17 Pro on
iOS 26.5 so far). Then, in order:

1. It generates the project and builds the app and the UI tests (`build-for-testing`).
2. It launches the app, fails unless it is still running 20 seconds later, and uploads a screenshot
   and the app's logs as the `ios-launch` artifact.
3. On fresh Firebase emulators it seeds a paired family (`tools/ios/seed-shell-family.js`) and runs
   the XCUITest walk (`CoPlanlyUITests`): sign-in by e-mail, the tabs, Family → Expenses → the Add
   expense sheet, a back swipe completed and one let go of, a tab tap popping two screens, a sheet
   with edits swiped down asking "Discard changes?", sign-out to fresh tabs, a `coplanly://pair`
   link and, last, deleting the account. Its screenshots are the `ios-shell` artifact.
4. On emulators again, the shared modules' iOS tests run on the same booted simulator (the
   Keychain tests included), with the write gate (`IosWriteGateTest` and
   `tools/ios/check-ios-writes.js`, which compares every document the iPhone wrote with what
   Android writes). No test may skip itself for want of the emulators.
5. **The tour** (part B, Task 13), last, on emulators of its own, so a run that reaches the job's
   240-minute limit loses the tour and never the write gate: the seed with `--tour` adds an event
   today with a change request about it, a child, a pet, an expense and a vault document's index
   row; `ShellUITests.testTour` signs in, visits every destination it can reach from that family,
   and saves a screenshot named `tour-<route>` for each. A destination it cannot reach is written
   to `tour.txt` with the reason, as the Android UI tour does. Only a crash or a failed launch fails
   the job: `simulator.sh tour` then writes `tour-crashed` with the tour's own failure lines, and
   the job's last step fails on it. Any other failure of the step (its 50-minute limit, the seed)
   leaves a notice, and the uploads after it run either way. The tour stops visiting after 35
   minutes, so it always writes its summary. The screenshots, `manifest.json` (from `xcrun xcresulttool export attachments`) and `tour.txt` are
   the `ios-tour` artifact. To read it against the app's destinations, compare `tour.txt` with
   `AppDestinationCatalog.all` (`shared-ui/.../navigation/AppDestination.kt`); every route is on
   it, reached or skipped. Since part C it also shows six of the system's controls in use (a switch,
   a segment, the date sheet, an option menu, an action sheet and the event preview sheet), shot as
   `tour-control-…` and listed in `tour.txt` the same way.

CI never has a `GoogleService-Info.plist`, so it never offers Google sign-in and never reaches the
real project.

To run the tour on your Mac: start the emulators as above, then, from the repository's root,
`iosApp/scripts/simulator.sh tour <simulator udid> iosApp/build/tour` after a `build-for-testing`
into `iosApp/build` (the commands are in the `ios-app` job's "Build the app" step).
