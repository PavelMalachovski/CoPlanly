# iPhone phase 4, part E1: the platform halves — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the iPhone's last capability stand-ins with the iPhone's own:
- links, files, the dialler and mail;
- the clipboard;
- the invitation QR and the QR scanner;
- a paste button;
- on-device dictation;
- PAY by square's LZMA1.

**Architecture:**
- Each capability is an `iosMain` class behind the `presentation/platform/` interface it already
  has, wired in `iosPlatformServices` or `platformUiModule`.
- Pure decisions (mail and tel addresses, the LZMA1 encoder) live in `commonMain` and are tested on
  the JVM.
- The paste button is a new `NativeControls` member, so Android's branch draws nothing.

**Tech Stack:**
- Kotlin/Native on UIKit, AVFoundation, Speech, QuickLook and CoreImage;
- Compose Multiplatform 1.9.3;
- Koin 4.1.1;
- `org.tukaani:xz` as the test oracle.

**Spec:** `docs/superpowers/specs/2026-11-18-iphone-phase-4e1-platform-halves-design.md`

## Global Constraints

- **CLAUDE.md design item 1 (native controls).**
  - Every adaptive control asks `nativeControls()` and makes exactly the Material call on null.
  - A new member gets its iPhone half in `IosNativeControls` and its recording in
    `RecordingNativeControls` (`app/src/test/.../presentation/common/`).
  - An in-place native control takes a `background`.
  - Never lay a native view over Compose content. The scanner and Quick Look are presented
    controllers.
- **Item 28.** Dictation is on-device only: `requiresOnDeviceRecognition = true` on every request,
  and availability is decided by `supportsOnDeviceRecognition`. Audio buffers are never kept.
- **Item 37.** The invitation QR carries exactly `pairingLinkFor(code, web)` with the same `web` the
  share text reads. A scanned QR is handed over only when `PairingUri.extractCode` accepts it.
- **Five locales** (`en`, `cs`, `de`, `ru`, `uk`) for every new string, `InfoPlist.strings` included.
- **No change to what Android ships**, except the new `NativeControls` member, whose null branch
  draws nothing. Android keeps `org.tukaani:xz`. The 470 screenshots must not move.
- **Kotlin/Native naming in tests.** A test name in commonTest contains no comma or parentheses.
- **Kotlin/Native interop.**
  - An Objective-C category method needs its own import (part D's lessons, CLAUDE.md "Part D's
    shapes").
  - Gradle cannot reach Google's Maven from cloud sessions, so a task whose code compiles only for
    iOS is checked by CI's `shared`/`ios-app` jobs at the end. Run `:shared:compileKotlinIosSimulatorArm64`
    and `:shared-ui:compileKotlinIosSimulatorArm64` locally where they resolve.
- **detekt.** No `@Suppress` and no baseline edits. Split a long parameter list into a value class
  rather than suppressing it.
- **Commits** are Conventional Commits.

## Review Focus

1. **A QR that is not an invitation** (a menu, a payment code) is shown to the scanner. Expected:
   scanning goes on, nothing reaches `redeemCode`. Pinned in Task 4 by `ScannedCodeFilterTest`.
2. **A mail draft whose body has a line break, `&` or Cyrillic** (the chat report). Expected: the
   draft shows the text as written. Pinned in Task 2 by `MailtoUriTest`.
3. **A phone number typed with spaces, dashes or brackets.** Expected: the dialler gets the digits
   and a leading `+`. Pinned in Task 2 by `TelUriTest`.
4. **A PAY by square document of the maximum length the form allows, with a long note.** Expected:
   the same bytes as Android. Pinned in Task 1 by the parity suite's long-input cases.
5. **Dictation in a language without an on-device model**, or the app sent to the background
   mid-dictation. Expected: no microphone in the first case; the session ends in the second.
   Pinned in Task 6 by `IosSpeechDictationTest`.

---

### Task 1: LZMA1 encoder in common Kotlin, held to `org.tukaani:xz`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/coparently/app/domain/platform/Lzma1Encoder.kt`. Split
  into several files under `domain/platform/lzma/` if large; the port's internal structure is the
  builder's.
- Modify: `shared/src/iosMain/kotlin/com/coparently/app/domain/platform/Lzma1Raw.kt`, so the iOS
  `actual` returns `Lzma1Encoder.encode(input)`.
- Create: `shared/src/androidHostTest/kotlin/com/coparently/app/domain/platform/Lzma1EncoderParityTest.kt`
- Create: `shared/src/commonTest/kotlin/com/coparently/app/domain/platform/Lzma1RawVectorTest.kt`

**Interfaces:**
- Produces: `object Lzma1Encoder { fun encode(input: ByteArray): ByteArray }`. This is raw LZMA1 with
  no header and the end marker. The settings are fixed: `LZMAEncoderNormal`, the BT4 match finder,
  nice length 64, xz-java's default depth limit, lc 3, lp 0, pb 2, dictionary `1 shl 17`.
- `Lzma1Raw.encode` keeps its signature (`ByteArray?`). On iOS it now never returns null.

- [ ] **Step 1: Write the parity test** (`Lzma1EncoderParityTest`, JVM). The oracle is the library
  call `Lzma1Raw` (androidMain) makes. Copy `Lzma1RawTest.paymentQrCompress` as a private helper.
  - `paymentQrTestDocumentMatches`: `Lzma1RawTest`'s document (its CRC-prefixed bytes) gives
    identical bytes.
  - `emptyInputMatches`: `ByteArray(0)` gives identical bytes.
  - `seededRandomInputsMatch`: 2,000 inputs from `Random(20261118)`, lengths 0..2048. Half are
    random bytes. Half are drawn from a 16-symbol alphabet, so matches and repeated distances
    happen. On failure, print the index, the length and the input as hex.
  - `longRepeatsMatch`: runs of one byte (300, 1,000, 4,096 bytes), a 7-byte pattern repeated to
    3,000 bytes, and a 2-KiB block repeated three times, so distances past `1 shl 11` are used.
  - `payBySquareShapedDocumentsMatch`: for note lengths 0, 1, 70, 140 and 280 (Cyrillic and Czech
    diacritics included), build the document as `PaymentQr.PayBySquare` builds it, with CRC, and
    compare.
  - Each assertion is `assertContentEquals(oracle(input), Lzma1Encoder.encode(input))`.
- [ ] **Step 2: Run it and see it fail.** Run
  `./gradlew :shared:testAndroidHostTest --tests '*Lzma1EncoderParityTest*'`. Expected: compile
  failure, because `Lzma1Encoder` does not exist.
- [ ] **Step 3: Port the encoder.** Port xz-java (`org.tukaani:xz`, the version the build resolves)
  `LZMAOutputStream` (raw, with the end marker), `LZMAEncoder`, `LZMAEncoderNormal`, `LZCoder`'s
  state and price tables, `LZEncoder` with `BT4` and `Hash234`, `RangeEncoder`, `LiteralCoder` and
  `LengthCoder`.
  - Port only the paths these settings reach: no LZMA2 and no fast mode.
  - Keep integer arithmetic bit for bit: Java's `>>>` is Kotlin's `ushr`, and int overflow wraps
    the same way.
  - The KDoc names the origin (xz-java, public domain, by Lasse Collin and Igor Pavlov) and the
    version ported.
- [ ] **Step 4: Run the parity test until it passes.** Run
  `./gradlew :shared:testAndroidHostTest --tests '*Lzma1EncoderParityTest*'`. Expected: PASS.
- [ ] **Step 5: Hold the vector on every platform** (`Lzma1RawVectorTest`, commonTest).
  - `paymentQrTestDocumentCompressesToTheReferenceBytes`: the same document and
    `PAY_BY_SQUARE_EXPECTED` as `Lzma1RawTest`, copied as literals; there is no java.util in
    common code.
  - It asserts that `Lzma1Raw.encode(withCrc)` is non-null and equal to the reference.
- [ ] **Step 6: Point the iOS actual at the port** and update its KDoc. Run
  `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64`.
  Expected: PASS. The iOS compile may need CI.
- [ ] **Step 7: Commit.** `feat(shared): LZMA1 encoder in common Kotlin for PAY by square on the iPhone`

### Task 2: Links, files, the dialler, mail and the clipboard on the iPhone

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/platform/PlatformUris.kt`
- Create: `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/platform/MailtoUriTest.kt`
  and `TelUriTest.kt`
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/platform/IosOpenUrl.kt`
  (Quick Look's data source inside or beside it) and `IosClipboard.kt`
- Modify: `IosPlatform.kt`, to wire them, delete `NoOpenUrl` and `NoClipboard`, and update the
  header comment.
- Modify: `iosApp/CoPlanly/Info.plist`, adding `LSApplicationQueriesSchemes` = `mailto`, `tel`.

**Interfaces:**
- Produces, in `PlatformUris.kt`:
  - `fun mailtoUri(address: String, subject: String, body: String): String`. Subject and body are
    UTF-8 percent-encoded, keeping only RFC 3986 unreserved characters. A line break `\n` becomes
    `%0D%0A`. The address keeps unreserved characters, `@` and `+`.
  - `fun telUri(number: String): String?`. It keeps digits and a leading `+`, and returns null when
    no digit is left.
- Produces: `internal object IosOpenUrl : OpenUrl` and `internal object IosClipboard : SensitiveClipboard`.

- [ ] **Step 1: Write the failing tests.**
  - `MailtoUriTest`:
    ```kotlin
    assertEquals(
        "mailto:support+app@coplanly.app?subject=Report%20%26%20more%3F&body=Line%201%0D%0A%D0%96%20%232",
        mailtoUri("support+app@coplanly.app", "Report & more?", "Line 1\nЖ #2"),
    )
    assertEquals("mailto:a@b.cz?subject=&body=", mailtoUri("a@b.cz", "", ""))
    ```
  - `TelUriTest`:
    ```kotlin
    assertEquals("tel:+420777123456", telUri("+420 777-123 456"))
    assertEquals("tel:7771234", telUri("777 (12) 34"))
    assertEquals("tel:1234", telUri("12+34"))
    assertNull(telUri("abc"))
    ```
- [ ] **Step 2: Run them and see them fail.** Run
  `./gradlew :shared-ui:testAndroidHostTest --tests '*MailtoUriTest*' --tests '*TelUriTest*'`.
  Expected: compile failure.
- [ ] **Step 3: Implement both functions.** Run the same command. Expected: PASS.
- [ ] **Step 4: Implement `IosOpenUrl`.** `available = true`, and:
  - `open(url)`:
    - `http`/`https`: call `UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)`
      and return true.
    - `mailto`: return false unless `canOpenURL`; otherwise open it and return true.
    - Any other scheme or an unparseable URL: false.
  - `openFile(path, contentType)`:
    - False when the file does not exist.
    - Otherwise present a `QLPreviewController` whose data source returns
      `NSURL.fileURLWithPath(path)`, through `presentWhenSettled` on `topViewController`
      (`ios/controls/PickerSheet.kt`), and return true.
    - The data source object is held for as long as the controller is shown.
  - `dial(number)`: `telUri(number)`. Null or `!canOpenURL` gives false; otherwise open it and
    return true.
  - `composeEmail(...)`: `mailtoUri(...)`, then the same as a `mailto` `open`.
  - `openAppSettings()`: open `UIApplicationOpenSettingsURLString`, return true.
- [ ] **Step 5: Implement `IosClipboard`.** `available = true`. `copySensitive` calls
  `UIPasteboard.generalPasteboard.setItems(listOf(mapOf("public.utf8-plain-text" to text)),
  mapOf(UIPasteboardOptionLocalOnly to true))`. The label is not stored, since iOS has no clip
  label.
- [ ] **Step 6: Wire both** in `iosPlatformServices`, delete the two stand-ins and update the file's
  header comment. Run `./gradlew :shared-ui:compileKotlinIosSimulatorArm64` where it resolves.
- [ ] **Step 7: Commit.** `feat(ios): open links, files, the dialler and mail, and copy to the clipboard`

### Task 3: The invitation QR, from the shell's one set of build values

**Files:**
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/pairing/IosInviteQrCodes.kt`
- Create: `shared-ui/src/iosTest/kotlin/com/coparently/app/presentation/pairing/IosInviteQrCodesTest.kt`
- Modify:
  - `shared-ui/src/iosMain/kotlin/com/coparently/app/di/koin/PlatformUiModule.ios.kt`: drop the
    `AppBuildValues` stand-in block and `NoInviteQrCodes`'s binding.
  - `IosUiUnavailable.kt`: delete `NoInviteQrCodes`.
  - `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosApp.kt`: build `AppBuildValues` once,
    before `startKoin`, and bind it in `iosAppModule`, so Koin and the shell share the instance;
    `PAIRING_WEB_LINK` stays `true`.
  - `iosAppModule` (wherever it is declared): `single<AppBuildValues> { values }` and
    `single<InviteQrCodes> { IosInviteQrCodes(get(), IosQrCodes(IosShareSheet)) }`.

**Interfaces:**
- Consumes: `IosQrCodes.encode(content): QrMatrix?` and
  `pairingLinkFor(code: String, web: Boolean): String` (`presentation/pairing/PairingLink.kt`).
- Produces: `internal class IosInviteQrCodes(private val buildValues: AppBuildValues, private val qrCodes: QrCodes) : InviteQrCodes`.
  It is `available = true`. `forInvite(code)` draws the matrix of
  `pairingLinkFor(code, buildValues.pairingWebLink)` into a 512 × 512 `ImageBitmap`: black on white,
  a quiet zone of one module, as Android's `QRCodeService` draws it. It returns null when `encode`
  does.

- [ ] **Step 1: Write the failing test** (`IosInviteQrCodesTest`, simulator).
  - For `web` in `true` and `false`, build `IosInviteQrCodes(values(web), IosQrCodes(IosShareSheet))`
    and call `forInvite("ABC234")`.
  - Turn the bitmap into a `CIImage` (through its pixels) and read it with
    `CIDetector(CIDetectorTypeQRCode)`.
  - Assert that the decoded message equals `pairingLinkFor("ABC234", web)` and that the bitmap is
    512 × 512.
- [ ] **Step 2: Implement `IosInviteQrCodes`** and the binding changes above. Confirm that nothing in
  `platformUiModule` still declares `AppBuildValues`; Koin's `verify` would fail on two.
- [ ] **Step 3: Run** `./gradlew :shared-ui:compileTestKotlinIosSimulatorArm64` where it resolves.
  The test runs in CI's `ios-app` job.
- [ ] **Step 4: Commit.** `feat(ios): draw the invitation QR from the share text's link`

### Task 4: The QR scanner

**Files:**
- Create: `shared/src/commonMain/kotlin/com/coparently/app/domain/pairing/ScannedCodeFilter.kt`
- Create: `shared/src/commonTest/kotlin/com/coparently/app/domain/pairing/ScannedCodeFilterTest.kt`
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/controls/QrScannerController.kt`
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/platform/IosQrScanner.kt`
- Modify: `IosPlatform.kt`, to wire `IosQrScanner` and delete `NoQrScanner`.

**Interfaces:**
- Produces:
  - `class ScannedCodeFilter`. Its `fun accept(payload: String): String?` returns
    `PairingUri.extractCode(payload)` the first time a valid code is seen, and null for invalid
    payloads and for every later call, so a code is handed over once per scan.
  - `internal object IosQrScanner : QrScanner`. `available` is
    `AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) != null`. `rememberScanner(onScanned)`
    returns a launcher that presents `QrScannerController` full screen.

- [ ] **Step 1: Write the failing test** (`ScannedCodeFilterTest`).
  - `aMenuQrIsIgnored`: `accept("https://restaurant.example/menu")` is null.
  - `aValidCodeIsHandedOverOnce`: `accept(pairingLinkFor("ABC234", true))` is `"ABC234"`; a second
    `accept` of the same payload is null.
  - `aValidCodeAfterNoiseIsHandedOver`: noise first, then a bare `"ABC234"`, gives `"ABC234"`.
  - Use codes `PairingUri.extractCode` accepts. Read its rules first.
- [ ] **Step 2: Run** `./gradlew :shared:testAndroidHostTest --tests '*ScannedCodeFilterTest*'`.
  Expected: fails. Implement it, then run again. Expected: PASS.
- [ ] **Step 3: Implement `QrScannerController`**, a `UIViewController`:
  - `AVCaptureSession` with the back camera, `AVCaptureMetadataOutput` with
    `metadataObjectTypes = [AVMetadataObjectTypeQRCode]`, and a delegate on the main queue that
    feeds each string through a `ScannedCodeFilter`.
  - On a code: stop the session, dismiss, then call `onScanned` once.
  - An `AVCaptureVideoPreviewLayer` fills the view, with a square guide (a bordered `CALayer`, the
    side 0.65 of the shorter edge) and the hint `pairing_qr_hint`, read from `Res` before
    presenting.
  - A system Cancel bar button (`UIBarButtonSystemItemCancel`) in a navigation controller
    dismisses with nothing.
  - Presented with `modalPresentationStyle = UIModalPresentationFullScreen` through
    `presentWhenSettled`.
  - Camera permission:
    - `.authorized` starts the session;
    - `.notDetermined` calls `requestAccessForMediaType`, then starts the session or dismisses;
    - denied or restricted dismisses at once and logs with `logger.w`.
  - The session starts and stops off the main thread, on a serial queue.
- [ ] **Step 4: Wire `IosQrScanner`** in `iosPlatformServices` and delete `NoQrScanner`. Compile
  where it resolves.
- [ ] **Step 5: Commit.** `feat(ios): scan an invitation QR with the camera`

### Task 5: The paste button in the code field

**Files:**
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/NativeControls.kt`:
  a new member.
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/controls/PasteButton.kt`
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/controls/IosNativeControls.kt`
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/pairing/components/CodeEntryField.kt`
- Modify: `app/src/test/java/com/coparently/app/presentation/common/RecordingNativeControls.kt` and
  `NativeControlsTest.kt`
- Modify: `iosApp/CoPlanlyUITests/ShellUITests.swift`: `tourControls` gets `paste`, shot as
  `tour-control-paste`.

**Interfaces:**
- Produces:
  ```kotlin
  @Composable
  fun PasteButton(onPaste: (String) -> Unit, background: Color, modifier: Modifier)
  ```
  This is a `NativeControls` member, with KDoc in the file's style.
  - The iPhone half is a `UIPasteControl` (`UIPasteControlConfiguration`, `displayMode` icon and
    label, `cornerStyle` capsule), hosted in place at 44 pt high in a view painted `background`.
  - Its `target` is a `UIResponder` subclass whose `pasteItemProviders` reads the first provider's
    plain text and calls `onPaste` on the main thread.
  - It is an accessibility element named by the system.
- `CodeEntryField` draws `nativeControls()?.PasteButton(onPaste = { onValueChange(it) },
  background = MaterialTheme.colorScheme.surfaceContainer, modifier = …)` next to the field, only
  when the native controls exist and `enabled`. The field's own `onValueChange` parses a code or a
  link. Android (null) draws exactly what it drew.

- [ ] **Step 1: Write the failing tests** (`NativeControlsTest`, Robolectric).
  - `codeEntryFieldDrawsNoPasteButtonOnAndroid`: with no native controls, no node exists with the
    content description `RecordingNativeControls` uses for paste.
  - `codeEntryFieldAsksTheNativePasteButton`: with `RecordingNativeControls`, one `PasteButton`
    call is recorded. Invoking its `onPaste("ABC234")` reaches the field's `onValueChange` with
    `"ABC234"`.
  - `codeEntryFieldHidesThePasteButtonWhileDisabled`: no call when `enabled = false`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*NativeControlsTest*'`. Expected:
  fails. Implement the member, the recording, the iPhone half and `CodeEntryField`, then run again.
  Expected: PASS.
- [ ] **Step 3: Check that the screenshots do not move.** Run
  `./gradlew :app:verifyRoborazziDebug --tests '*Pairing*'` if baselines resolve locally; otherwise
  the `screenshots` job is the check.
- [ ] **Step 4: Add the tour shot.**
  - `tourControls` gains `"paste"`.
  - Its step opens the pairing screen of the seeded paired account ("Add another co-parent",
    `anotherCoParentSection`) and shoots `tour-control-paste` with the field in view.
  - It skips with a reason when the button is not found, as the other controls do. Mirror an
    existing control's code.
- [ ] **Step 5: Commit.** `feat(ios): a system paste button in the code field`

### Task 6: On-device dictation

**Files:**
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/chat/IosSpeechDictation.kt`
  (with `SpeechRecognizerSource`) and `AppleSpeechRecognizerSource.kt`
- Create: `shared-ui/src/iosTest/kotlin/com/coparently/app/presentation/chat/IosSpeechDictationTest.kt`
- Modify:
  - `PlatformUiModule.ios.kt`: bind `SpeechDictation` to
    `IosSpeechDictation(AppleSpeechRecognizerSource)`.
  - `IosUiUnavailable.kt`: delete `NoSpeechDictation`.
  - `IosPlatform.kt`: `IosDictation` replaces `NoDictation`. `IosPermissions` handles `MICROPHONE`.
- Modify: `iosApp/CoPlanly/Info.plist` and the five `iosApp/CoPlanly/*.lproj/InfoPlist.strings`.

**Interfaces:**
- Produces:
  ```kotlin
  internal interface SpeechRecognizerSource {
      /** Whether the phone recognizes [languageTag] on the device; false when it has no recognizer for it. */
      fun supportsOnDevice(languageTag: String): Boolean
      /** Starts recognizing with on-device recognition only; null when it cannot start. */
      fun begin(languageTag: String, onText: (text: String, isFinal: Boolean) -> Unit, onFailure: (DictationFailure) -> Unit): SpeechSession?
  }
  internal interface SpeechSession { fun finish(); fun abort() }
  internal class IosSpeechDictation(
      private val source: SpeechRecognizerSource,
      private val languageTag: () -> String = ::phoneDictationLanguage,
  ) : SpeechDictation
  internal fun phoneDictationLanguage(): String
  ```
  - `phoneDictationLanguage()`: the first entry of `NSUserDefaults.standardUserDefaults`'
    `AppleLanguages` (the per-app choice iOS writes into the app's domain), else
    `NSLocale.preferredLanguages.first`, else `"en"`.
  - `isAvailable() = source.supportsOnDevice(languageTag())`.
  - `start(tag, listener)`:
    - use `tag ?: languageTag()`; an unsupported tag gives `onFailure(LANGUAGE_UNAVAILABLE)`;
    - a second `start` while running gives `BUSY`;
    - `onText` becomes `onPartial` or `onFinal`.
  - `stop()` calls `finish`. `cancel()` and `onBackground()` call `abort`.
  - `onBackground()` is internal. The class registers it for
    `UIApplicationDidEnterBackgroundNotification` at construction.
- `AppleSpeechRecognizerSource` (Speech and AVFoundation):
  - `SFSpeechRecognizer(NSLocale(tag))`, with `supportsOnDeviceRecognition` checked;
  - `SFSpeechAudioBufferRecognitionRequest` with `requiresOnDeviceRecognition = true` and
    `shouldReportPartialResults = true`;
  - `AVAudioEngine` input tap appending buffers to the request only;
  - `AVAudioSession` category `record`, mode `measurement`, deactivated with
    `notifyOthersOnDeactivation` on finish or abort;
  - an `AVAudioSessionInterruptionNotification` aborts.
- `IosDictation : Dictation` has `available = true`; `rememberLanguageTag()` returns
  `phoneDictationLanguage()`.
- `IosPermissions` and `MICROPHONE`:
  - `isGranted` is true when `AVAudioApplication.sharedInstance.recordPermission` is granted and
    `SFSpeechRecognizer.authorizationStatus()` is authorized.
  - `rememberRequest` asks the microphone, then speech, and calls `action` only when both are
    granted, else `denied`.

- [ ] **Step 1: Write the failing tests** (`IosSpeechDictationTest`, simulator, with a fake source).
  - `noMicrophoneWithoutAnOnDeviceModel`: the fake says false, so `isAvailable()` is false.
  - `anUnsupportedLanguageFailsAtStart`: the listener gets `LANGUAGE_UNAVAILABLE` and the source's
    `begin` is never called.
  - `textArrivesAsPartialThenFinal`.
  - `aSecondStartIsBusy`.
  - `backgroundAbortsTheSession`: after `start`, `onBackground()` calls `abort` once, and a later
    `stop` does nothing.
  - `stopFinishesOnce`.
- [ ] **Step 2: Implement it**, with `AppleSpeechRecognizerSource`, the permission and the wiring.
  Every `SFSpeechAudioBufferRecognitionRequest` the source makes sets
  `requiresOnDeviceRecognition = true`; there is no other code path.
- [ ] **Step 3: Add the plist keys.** Add `NSMicrophoneUsageDescription` and
  `NSSpeechRecognitionUsageDescription` to `Info.plist` with the English text, and to each
  `InfoPlist.strings`, verbatim:

  | locale | NSMicrophoneUsageDescription | NSSpeechRecognitionUsageDescription |
  | --- | --- | --- |
  | en | CoPlanly uses the microphone only while you dictate a chat message. The audio stays on your iPhone. | Your words are turned into text on your iPhone. Nothing you say is sent anywhere. |
  | cs | CoPlanly používá mikrofon jen při diktování zprávy do chatu. Zvuk zůstává ve vašem iPhonu. | Vaše slova se převádějí na text přímo ve vašem iPhonu. Nic z toho, co řeknete, se nikam neodesílá. |
  | de | CoPlanly verwendet das Mikrofon nur, während du eine Chatnachricht diktierst. Der Ton bleibt auf deinem iPhone. | Deine Worte werden auf deinem iPhone in Text umgewandelt. Nichts, was du sagst, wird irgendwohin gesendet. |
  | ru | CoPlanly использует микрофон только пока вы диктуете сообщение в чат. Звук остаётся на вашем iPhone. | Ваши слова превращаются в текст прямо на iPhone. Ничего из сказанного никуда не отправляется. |
  | uk | CoPlanly використовує мікрофон лише поки ви диктуєте повідомлення в чат. Звук залишається на вашому iPhone. | Ваші слова перетворюються на текст просто на iPhone. Нічого зі сказаного нікуди не надсилається. |
- [ ] **Step 4: Compile** where it resolves. The tests run in CI's `ios-app` job.
- [ ] **Step 5: Commit.** `feat(ios): on-device dictation in the chat composer`

### Task 7: Record part E1

**Files:**
- Modify: `docs/DEVICE-CHECKLIST.md`. Add a new `### 3.45 The iPhone's platform halves · 1P, an
  iPhone, an Android phone, a development build`, listing the spec §7 device checks in the
  checklist's style. The dictation step records which of the five languages show no microphone.
- Modify: `tools/manual-test-plan.js`. A rule maps the new files of Tasks 1–6
  (`K/presentation/platform/Ios*.kt`, `K/ios/controls/QrScannerController.kt`,
  `K/ios/controls/PasteButton.kt`, `K/presentation/chat/IosSpeechDictation.kt`,
  `K/presentation/chat/AppleSpeechRecognizerSource.kt`, `shared/**/Lzma1Encoder*`, `**/lzma/**`,
  `iosApp/CoPlanly/*.lproj/InfoPlist.strings`) to `3.45`. Run
  `node --test tools/test/*.test.js`. Expected: PASS.
- Modify: `CLAUDE.md`.
  - Add a short "**Phase 4's part E1**" paragraph beside part D's shapes: the six halves, the
    paste control as a `NativeControls` member, the scanner's filter, the LZMA1 port held to
    xz-java on the JVM, and `AppBuildValues` bound once by the shell.
  - Update the stand-in sentence ("Still stand-ins on the iPhone: …") to keep only
    `NoRequestClockJobs`, `NoAiAssist` and `NoAppLanguages`.
- Modify: `docs/ROADMAP.md`, the MON-24 current stand-in list, in the same way.

- [ ] **Step 1: Make the edits above.** Then run
  `node tools/check-invariants.js && node --test tools/test/*.test.js`. Expected: PASS.
- [ ] **Step 2: Commit.** `docs: record iPhone phase 4 part E1`
