# iPhone phase 4, part E1: the platform halves — design

Status: draft, 18 November 2026; builder check done (no question for the owner). Parent: `docs/superpowers/specs/2026-11-16-iphone-phase-4-design.md`
§2 and §4 (part E). The owner split part E in two on 18 November 2026 (chat):
- **E1** (this document) is code CI can prove;
- **E2** is Universal Links, the privacy manifest, the App Store privacy label and the legal
  documents, which wait on the owner's console steps (the Apple Team ID, hosting).

## 1. Intent

After part D the iPhone still answers six of the shell's capabilities with a stand-in, so a parent on
an iPhone cannot:
- scan a co-parent's invitation QR, or show their own;
- copy an invitation code or an IBAN;
- open a link, a vault document, the dialler or a mail draft;
- dictate a message;
- get a Slovak PAY by square code (`Lzma1Raw.encode` answers null on iOS, so `PaymentQr` draws
  nothing for an SK account).

E1 replaces each with the iPhone's own, so every screen offers on the iPhone what it offers on
Android. The rule is design item 8: an affordance appears only where the phone can keep it, and
`available` already decides that on every screen.

## 2. Goals

1. **`IosOpenUrl`** replaces `NoOpenUrl`.
   - `open(url)`: `UIApplication.openURL` for `http`, `https` and `mailto`; false for anything else
     or when iOS refuses.
   - `openFile(path, contentType)`: the file in Quick Look (`QLPreviewController`, presented), from
     the path `SharedFileCache` already holds; false when the file is missing.
   - `dial(number)`: `tel:` with the digits and `+` kept. It answers false where `canOpenURL` says no
     (an iPad, a simulator), so the screen shows its existing "no dialler" snackbar.
   - `composeEmail(address, subject, body)`: a `mailto:` link, percent-encoded by RFC 6068, opened in
     the default mail app. It answers false with no mail app, so the chat report's existing failure
     path runs.
   - `openAppSettings()`: `UIApplication.openSettingsURLString`.
   - `available` is true.
2. **`IosClipboard`** replaces `NoClipboard`. `copySensitive` writes plain text to
   `UIPasteboard.generalPasteboard` with `localOnly = true`, so it never reaches the parent's Mac
   through Universal Clipboard. This is the iPhone's nearest to Android's "sensitive" mark.
3. **`IosInviteQrCodes`** replaces `NoInviteQrCodes`.
   - It draws the matrix `IosQrCodes` already makes (CoreImage, part C) for
     `pairingLinkFor(code, AppBuildValues.pairingWebLink)`, the link the share text carries (item 37:
     the QR and the share text never disagree).
   - It answers an `ImageBitmap` at the size Android's `AndroidInviteQrCodes` draws.
   - *Author's call after the builder check:* `web` comes from the same build values the share text
     reads, never from a separate copy. `IosApp`'s `PAIRING_WEB_LINK` is `true`, as Android's
     `publishedPairingWebLink` is. Koin's iPhone `AppBuildValues` (`PlatformUiModule.ios.kt`), which
     says `pairingWebLink = false` today, is bound to the shell's one instance. So no ViewModel reads
     a second answer, and `IosInviteQrCodesTest` holds the QR to the share text's link.
4. **`IosQrScanner`** replaces `NoQrScanner`.
   - A presented, full-screen controller in Kotlin (`ios/controls/QrScannerController.kt`): an
     `AVCaptureSession` with `AVCaptureMetadataOutput` for `.qr` only, a preview layer, a square
     guide, a one-line hint and a Cancel button.
   - The hint is `pairing_scan_hint`, read from `Res`; Cancel and the camera question are the
     system's.
   - Like Android's `QRScannerActivity`, the first string that `PairingUri.extractCode` accepts is
     handed to `onScanned` once, and the controller dismisses. Any other QR (a menu, a poster) is
     ignored and scanning goes on, so a stray code never reaches `redeemCode` or the
     ten-misses-an-hour lock (item 37). Cancel or a swipe down hands nothing. *(Author's call after
     the builder check; the contract already said so.)*
   - Camera permission goes through the existing `NSCameraUsageDescription`. On a refusal it hands
     nothing and logs; the form keeps its code field.
   - `available` is true when a back camera exists; false on a simulator, so the scan button is left
     out there, as on an emulator.
5. **Paste button.** `NativeControls.PasteButton(onPaste: (String) -> Unit, modifier, background)`
   is a new member, drawn inside `CodeEntryField`, beside the field. So every place a code is
   entered gets it: the pairing form, "Add another co-parent"'s field and the guest code screen.
   - The iPhone half is a `UIPasteControl` (iOS 16+, the deployment target is 17). It pastes without
     iOS's "Allow Paste" alert, which a programmatic `UIPasteboard` read would raise.
   - It is in place, so it takes a `background` (design item 1's interop rule).
   - On null (Android) nothing is drawn: Android has no paste button and gets none.
   - The pasted text goes through the field's existing parsing (a code or a pairing link).
6. **On-device dictation**, item 28 on the iPhone:
   - **Recognition.** `IosSpeechDictation` replaces `NoSpeechDictation`: `SFSpeechRecognizer` for
     the language tag, `SFSpeechAudioBufferRecognitionRequest` with
     `requiresOnDeviceRecognition = true`, and audio from `AVAudioEngine`.
   - **Availability.** `isAvailable()` is true only when a recognizer exists for the language and
     `supportsOnDeviceRecognition` is true; otherwise there is no microphone button, as on Android
     below 13. Never the server recognizer: that is item 28's rule, and it holds on iOS.
   - **Results.** Partial and final results reach the existing `DictationListener`, and words are
     appended to the draft by `DictationText.merge`.
   - **Ending.** The audio engine stops and the session is deactivated on `stop`, `cancel`, the app
     entering the background, and an audio-session interruption. Buffers are never kept.
   - **Permissions.** `IosPermissions` asks for the microphone (`AVAudioApplication`) and speech
     recognition (`SFSpeechRecognizer.requestAuthorization`) on the first tap, through the existing
     requester. Both must be granted, else the existing failure message shows.
   - **Plist strings.** `NSMicrophoneUsageDescription` and `NSSpeechRecognitionUsageDescription` go
     in `Info.plist` and in the five `InfoPlist.strings`. They say the words stay on the phone.
   - **Language.** `Dictation.rememberLanguageTag()` follows the contract Android keeps: the per-app
     choice first, else the phone's first language. On the iPhone the per-app choice (Settings →
     CoPlanly → Language) is the `AppleLanguages` array iOS writes into the app's own
     `NSUserDefaults` domain. With none there, the tag is `NSLocale.preferredLanguages.first`, never
     the English fallback. *(Author's call after the builder check.)*
   - **No on-device model, no button.** A language iOS has no on-device model for shows no microphone.
     That is design item 8's and item 28's rule, as on an Android phone without one. Device check
     §3.45 records which of the five languages had one on the phone tested, rather than assuming
     Czech does.
7. **LZMA1 for PAY by square.**
   - `Lzma1Encoder` in `shared/src/commonMain/.../domain/platform/` is a pure-Kotlin port of
     `org.tukaani:xz`'s raw LZMA1 encoder. It covers exactly the path `Lzma1Raw` uses on Android:
     `LZMAEncoderNormal`, the BT4 match finder, nice length 64, the library's default depth limit,
     lc 3, lp 0, pb 2, a 128 KiB dictionary, no header, with the end marker.
   - The iOS `Lzma1Raw.encode` calls it; Android keeps `org.tukaani:xz`, so no Android payment code
     changes.
   - The port is held byte for byte to the library by a JVM test. It compares the two on:
     - `PaymentQrTest`'s document;
     - generated PAY by square documents for each field length the form allows;
     - 2,000 seeded random inputs from 0 to 2,048 bytes;
     - inputs built to exercise long matches and repeated distances.
   - The simulator runs `PaymentQrTest`'s expected code too.
   - xz-java is in the public domain; the port's KDoc names its origin and version.

## 3. Non-goals

- Universal Links, `apple-app-site-association`, the privacy manifest, the privacy label and the legal
  documents: E2.
- The per-app language picker (`NoAppLanguages`) stays: iOS sets an app's language in its own
  Settings app, and the parent design says so.
- AI assist (`NoAiAssist`) stays off: it is off in release on Android too until billing (item 36).
- Replacing `org.tukaani:xz` on Android with the port. It would change the code Android
  ships for nothing; it can follow once the port has run in production.
- A paste button on Android.

## 4. Anti-goals (it fails even if it works)

- Dictation that can fall back to Apple's servers, even once. `requiresOnDeviceRecognition` is set
  on every request, and availability is decided by `supportsOnDeviceRecognition`, never by the
  recognizer's general availability.
- A PAY by square code from the iPhone that differs by a byte from Android's for the same payment.
  Two phones in one family must show the same code.
- A native view laid over Compose content (the scanner and Quick Look are presented controllers;
  the paste control is in place with a background).
- A clipboard write that reaches Universal Clipboard.
- A QR whose link differs from the share text's.

## 5. Constraints

- Design item 1:
  - every adaptive control asks `nativeControls()` and makes exactly the Material call on null;
  - a new member gets its iPhone half in `IosNativeControls` and its recording in
    `RecordingNativeControls`;
  - in-place native controls take a `background`.
- Item 28 (on-device only), item 37 (one link), Legal item 1: the privacy policy already describes
  dictation as on-device. If its wording names Android's recognizer only, it gets one sentence for
  the iPhone in this pull request, with `web/privacy/` regenerated.
- Five-locale rule for every new string, plist strings included.
- Kotlin/Native lessons of part D (enum vs constant imports, category methods as extensions).
- `ios-app` CI job: the tour gets one more control shot, `tour-control-paste`.

## 6. Approaches (decided)

- **Split E (owner, 18 November 2026).** E1 code now, E2 paperwork after the console steps.
- **LZMA1 (the author's call).**
  - *A faithful port, held to the library on the JVM* (chosen). It is the only way to keep the two
    phones' codes identical.
  - *A simpler valid encoder* (greedy matches) was rejected: banks would read it, but the code would
    differ from Android's for the same payment, and `PaymentQrTest`'s vector would not hold.
  - *Apple's Compression framework* was rejected: it writes LZMA2 in an xz container, not raw LZMA1.
- **Scanner (the author's call).**
  - *A Kotlin `AVCaptureSession` controller* (chosen).
  - *VisionKit's `DataScannerViewController`* was rejected: it needs the A12 Neural Engine check,
    shows its own highlighting UI, and is harder to drive from Kotlin/Native.
- **Mail (the author's call).**
  - *`mailto:`* (chosen). It reaches whichever mail app the parent uses, as Android's `ACTION_SENDTO`
    does.
  - *`MFMailComposeViewController`* was rejected: it works only with Apple Mail configured.

## 7. Tests

- **`Lzma1EncoderParityTest`** (`shared/src/androidHostTest/`) is the parity suite above. Its random
  seed is printed on failure.
- **`Lzma1RawTest`'s vector** runs in `commonTest` on the simulator through `Lzma1Raw` (iOS) too.
- **`MailtoTest`** (`commonTest`, a pure `mailtoUri(address, subject, body)` in `shared-ui`): spaces,
  `&`, `?`, `#`, line breaks, Cyrillic and an address with `+`.
- **`TelUriTest`**: digits, `+`, spaces and dashes dropped.
- **`NativeControlsTest`** records `PasteButton` on the pairing form; Android's branch draws nothing,
  and the screenshots do not move.
- **`IosInviteQrCodesTest`** (`iosTest`): the matrix decodes, via CoreImage's detector, to exactly
  `pairingLinkFor(code, web)` for both values of `web`.
- **Dictation**: a test over a fake recognizer seam (`SpeechRecognizerSource`):
  - unavailable without on-device support;
  - every request sets `requiresOnDeviceRecognition`;
  - the session ends on background.
- **XCUITest tour**: `tour-control-paste`, shot on the pairing screen of the paired account ("Add another co-parent",
  `anotherCoParentSection`, which draws `CodeEntryField`), so the tour's
  seeded paired family reaches it without a new seed. Skipped with a reason if unreachable, as the
  tour's other controls are.
- **Device check §3.45 (new)**:
  - scan a real invitation QR;
  - show one's own QR and scan it with an Android phone;
  - paste a code;
  - copy an IBAN and confirm it is not on a paired Mac;
  - open a vault PDF in Quick Look;
  - dial a contact;
  - write a report mail draft;
  - dictate in flight mode (on-device proof), in each of the five languages the phone has an
    on-device model for, recording which ones show no microphone;
  - a Slovak bank app reads the PAY by square code, and it matches an Android phone's for the same
    payment.

## 8. Left to the builder

- **The port's code.** Its internal structure and file split, as long as it stays in `commonMain`,
  free of platform types, and passes the parity suite.
- **The scanner's look.** The guide's size and the hint's placement, within the theme's tokens.
- **Quick Look.** The controller's data source wiring and how a missing file is reported (false, as
  specified).
- **Which audio-session category and mode dictation uses.** It must not duck other audio longer than
  the session.
