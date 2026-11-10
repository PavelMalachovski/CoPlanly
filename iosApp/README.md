# CoPlanly for iPhone

The iPhone app (MON-24 phase 3). It is a thin shell, as the Android app is: every screen, ViewModel
and the data layer are the Kotlin Multiplatform modules', compiled into one static framework,
`Shared` (`:shared-ui`, exporting `:shared` and `:shared-data`). What lives here:

| Path | What it is |
| --- | --- |
| `project.yml` | The Xcode project, for [XcodeGen](https://github.com/yonaskolb/XcodeGen). The generated `CoPlanly.xcodeproj/` is not committed. |
| `CoPlanly/AppDelegate.swift` | The entry point: reads the configuration, calls `IosApp.shared.start(config:)` and shows `IosApp.shared.rootViewController()` (`shared-ui/src/iosMain/kotlin/com/coparently/app/ios/`). |
| `CoPlanly/Info.plist` | Bundle `app.coplanly`, display name CoPlanly, iPhone only, portrait only, iOS 17.0 and later. |
| `CoPlanly/CoPlanly.entitlements` | Empty for now; push and sign-in add theirs. |
| `CoPlanly/Assets.xcassets` | The icon (made from the Android launcher icon by `tools/generate-ios-icon.py`; never edit the PNG by hand) and the indigo accent colour. |
| `scripts/simulator.sh` | CI's simulator steps (the `ios-app` job). |

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
Firestore (8080) and Functions (5001) at `127.0.0.1`. Start the emulators from `firestore-tests/`,
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

## In CI

The `ios-app` job ("iPhone app — build and launch (macOS)" in `.github/workflows/ci.yml`) runs on
`macos-latest` whenever the shared modules' jobs run and on any change under `iosApp/`
(`tools/ci-changes.js`, the `ios` output). It generates the project, builds it with `xcodebuild`
for a simulator it picks at run time (an "iPhone 16" when the image has one), launches the app,
fails unless it is still running 20 seconds later, and uploads a screenshot and the app's logs as
the `ios-launch` artifact. No emulator runs there, so the app's Firestore listeners fail quietly;
the job checks that the app starts and draws, not that it syncs.
