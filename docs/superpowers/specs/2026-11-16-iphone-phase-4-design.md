# iPhone app, phase 4: parity with Android — design

Status: the phase order and part A were agreed with the owner on 16 November 2026 (chat). Parts B–E
are outlined here and get their own design pass before their plans, as phase 3's parts did.

Parent design: `docs/superpowers/specs/2026-10-27-iphone-app-design.md`. Phase 4 there is §5.4
(platform features) and §5.5 (server). Phase 3 is merged (pull requests #163–#166). ROADMAP MON-24
lists the stand-ins phase 4 replaces.

## 1. Intent

After phase 3 the iPhone runs every screen with the system's controls and the theme. What it still
lacks is what makes an app usable day to day and acceptable to the App Store:
- Sign in with Apple;
- a Google Calendar connection;
- pushes and reminders;
- background work and the widget;
- links that open the app;
- a handful of small platform halves.

Phase 4 brings the iPhone to parity with Android. Phase 5 is the cross-platform e2e test and
TestFlight; phase 6 is the App Store.

## 2. The parts (owner, 16 November 2026)

Each part has its own plan and its own pull request, as in phase 3.

| Part | What | Why here |
| --- | --- | --- |
| **A. Accounts** | Sign in with Apple on both platforms, sign-in methods linked in Settings, the Apple token revoked on deletion, Google Calendar on the iPhone | The owner starts here. The App Store refuses an app with Google sign-in and no Apple sign-in (guideline 4.8) |
| **B. Request clocks on the server (MON-25)** | The on-time steps of a request's clock (the deadline notice, the last chance's end, "No answer") run in a scheduled Cloud Function, for both platforms | Owner answer 2 of 27 October 2026. Required before an iPhone build reaches a user: an iPhone cannot run background work at a set time |
| **C. Pushes and local notifications** | APNs through FCM, a Notification Service Extension that words the push on the phone, event reminders, request reminders, support reminders | Builds on B |
| **D. Background and the widget** | Best-effort sync and the school import through `BGTaskScheduler`; the "Today" widget on WidgetKit | |
| **E. The rest, and release readiness** | Universal Links on `/pair/`, the paste button for an invitation code, the invitation's QR code, the QR scanner, the clipboard, opening links, on-device dictation, PAY by square's LZMA1, the privacy manifest, the App Store privacy label, the legal documents | Before TestFlight |

Parts B–E are outlined only. Each gets a design pass of its own, with before/after boards where it
brings a new kind of screen (phase 3's rule, spec `2026-11-10` §2).

## 3. Part A: accounts

### 3.1 Goals

1. **Sign in with Apple, on the iPhone and on Android.**
   - The sign-in screen offers "Sign in with Apple" beside Google and e-mail.
   - On the iPhone it is the system's `ASAuthorizationAppleIDButton` and sheet.
   - On Android it is Firebase Auth's web flow (`OAuthProvider("apple.com")`), so a parent who
     started on an iPhone can move to Android.
   - Firebase Auth holds the credential.
2. **Sign-in methods linked in Settings** (owner, 16 November 2026, option (a)).
   - Settings → Account gains a row, "Sign-in methods", that lists e-mail, Google and Apple, each
     marked as linked or not.
   - Any method can be added to the signed-in account (Firebase `linkWithCredential`). Any can be
     removed (`unlink`) except the last one, so an account can never lose every way in.
   - Adding a method that already belongs to **another** account (`credential-already-in-use`)
     links nothing. The row says the method belongs to another account and names nothing about it.
3. **A wrong-method sign-in is recognised, never merged.**
   - A parent who signs in with a method no account holds gets a new, empty account; Firebase
     cannot know it is the same person behind Apple's private relay address.
   - When a brand-new account has no profile yet and onboarding is about to start, the first
     onboarding step asks once: "Already use CoPlanly? Sign in the way you did before, then add
     this method in Settings." It offers "Sign out and try again" and "This is a new account".
   - Nothing is merged on the server (owner, option (a), rejecting (c)).
   - If Firebase answers `account-exists-with-different-credential` (the same verified e-mail
     already holds another method), the sign-in screen says which kind of method to use, and does
     not create an account.
4. **The Apple token is revoked when the account is deleted.**
   - Apple requires it. `deleteAccount` revokes the Apple refresh token through Apple's REST API
     (`/auth/revoke`), with the client secret signed on the server by the Apple key held in
     `functions/.env`. A failed revoke is logged and retried by the next run; it never blocks the
     erasure.
   - To have a refresh token to revoke, Apple's authorization code is exchanged on the server at
     sign-in or linking (`exchangeAppleAuthCode`, a new callable) and the refresh token is kept in
     a server-only document, `apple_tokens/{uid}`, closed to every client and deleted with the
     account.
5. **Google Calendar on the iPhone.**
   - The same read-only scope as Android (`calendar.events.owned.readonly`, Legal item 10), the
     same server-side token exchange (`exchangeGoogleAuthCode`/`refreshGoogleAccessToken`).
   - The iPhone asks Google Sign-In for iOS for a server auth code with the web client id as the
     server client id, so the functions' existing web client exchanges it unchanged.
   - It replaces `NoGoogleCalendar` and `NoGoogleCalendarSignIn`; Settings' Google Calendar row
     appears on the iPhone.
   - The import runs on the button and when the app comes to the foreground
     (`GoogleCalendarAutoImport`, as on Android). The background import is part D's.
   - Imported events stay private (item 3), as on Android.

### 3.2 Non-goals

- Merging two accounts on the server (rejected by the owner).
- Any other sign-in provider (Facebook, a phone number).
- Writing to Google Calendar or reading a calendar the parent does not own (Legal item 10).
- A background Google import on the iPhone (part D).
- Removing the e-mail and password method: it stays offered on both platforms.

### 3.3 Anti-goals (it fails even if it works)

- **A parent ends with no way into their account.** The last method is never removable.
- **A sign-in silently creates a second account that hides the parent's family**, with no word on
  screen.
- **A message names or confirms another account's address** ("this Apple ID belongs to x@…"): a
  sign-in screen must not become a way to look accounts up.
- **The Apple key or any token reaches the client.** The key lives in `functions/.env` alone, as
  the Google client secret does (SEC-1).
- **Android changes beyond the new button, the new row and their strings.** The 470 screenshot
  baselines move only where a screenshot shows the sign-in screen or the Account group, and then
  through the Regenerate workflow as a visible commit.

### 3.4 Constraints

- **Apple's rules.**
  - The Apple button follows Apple's Human Interface Guidelines: the system button on the iPhone,
    and Apple's published black or white button artwork and wording on Android.
  - "Sign in with Apple" sits at least as prominently as Google (guideline 4.8).
- **Apple's first-sign-in data.**
  - Apple gives the name only on the first authorisation. The app stores it in the profile then,
    and onboarding's unskippable Profile step (item 22) covers a parent who declines to share it.
  - The relay address (`@privaterelay.appleid.com`) is an ordinary address to the app. Nothing
    shows it to the co-parent beyond what an e-mail already shows today.
- **Firebase Auth projects.**
  - Apple must be enabled in Firebase Auth, with the Services ID, Team ID and key. That is the
    owner's console step; this part writes the steps into `iosApp/README.md`.
  - The return URL of the web flow is Firebase's own handler.
- **Strings** go into all five locales (design refresh item 9). English is sentence case.
- **The data layer reaches the platform only through seams** ("The data layer reaches the platform
  only through those interfaces"). Apple's sheet is a `CredentialSignIn` member like Google's.
  Linking and unlinking are `AccountSignIn` members.
- **Legal documents change in the same pull request** (Legal item 1):
  - the privacy policy, RoPA and DPIA name Apple as an identity provider, with what it shares;
  - they also name `apple_tokens` and its retention;
  - DATA-SAFETY is updated.
- **Every new callable runs in `europe-west3`** (Legal item 2). It goes through the redeem-style
  rate limit only if it takes a code anybody could guess; Apple's authorization code cannot be
  guessed, so it does not.
- **`tools/e2e/coverage.json`** names a test or an exemption for each new callable and collection
  (`exchangeAppleAuthCode`, `unlinkApple`, `apple_tokens`). Apple's real flow cannot run on the emulators, so it
  is exempted with that reason; the emulator test covers linking and unlinking with e-mail and a
  fake Google credential, which the Auth emulator accepts.

### 3.5 Approaches

**Apple on Android.**
1. **Firebase's web flow (chosen).** `FirebaseAuth.startActivityForSignInWithProvider` with
   `OAuthProvider("apple.com")` opens a Custom Tab, and Firebase completes the sign-in. No Android
   Apple SDK exists. It costs a Services ID and a return URL in the Firebase console.
2. No Apple on Android. Rejected: a parent who began on an iPhone could not move to Android unless
   they had linked another method first.

**Where linking lives.**
1. **A "Sign-in methods" row in Settings → Account (chosen, owner).** It opens a small screen
   listing the three methods.
2. A prompt after every sign-in. Rejected: it is noise for the parent with one phone.

**Recognising a wrong-method sign-in.**
1. **One question on the first onboarding step of a brand-new account (chosen).** A new account is
   exactly the case; an existing one never sees it.
2. A server lookup by e-mail. Rejected: Apple's relay address hides the e-mail, and a lookup would
   let anyone probe whether an address has an account.

### 3.6 What the owner sees first

Before the plan, before/after boards on the canvas (https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH),
a fifth row:
1. the sign-in screen on Android and on the iPhone, before and after (the Apple button);
2. the "Sign-in methods" screen, on both platforms;
3. the one-time onboarding question for a brand-new account;
4. the message for a method that belongs to another account.

The owner approves the row before the plan is written.

### 3.7 Tests

- **JVM and common tests** for:
  - the linking rules: the last method is not removable, and "already in use" links nothing;
  - the onboarding question appearing only for a brand-new account;
  - the new strings' parity;
  - `deleteAccount`'s revoke step, with Apple's endpoint stubbed: a failure never blocks the
    erasure.
- **Functions tests:**
  - `exchangeAppleAuthCode` with Apple's token endpoint stubbed;
  - the client secret's JWT shape (ES256, the key id, the team, the audience);
  - `apple_tokens` closed to clients, pinned in `firestore-tests`.
- **e2e on the emulators.**
  - Linking e-mail to a Google-signed account and unlinking it.
  - The last method refused.
  - A second account's method refused.
- **The iPhone walk.** It signs in by e-mail and opens Sign-in methods. Apple's sheet cannot run in
  CI, so its button is only checked for presence.
- **Device checklist.** A real Apple sign-in on an iPhone and on Android, linking each way, and the
  Apple token revoked after a deletion (Apple's "Apps using Apple ID" list).

### 3.8 Calls made after the builder check (the author's, for the owner to overturn)

1. **What a revoke can reach.**
   - Apple's token is revoked where the app holds one: a sign-in or link made on the iPhone, whose
     authorization code `exchangeAppleAuthCode` exchanges.
   - Each `apple_tokens` entry records the `client_id` it was issued to: the bundle id
     `app.coplanly`, or the Services ID.
   - Firebase's web flow on Android hands the app no authorization code. An Apple method added on
     Android therefore stores nothing and has nothing to revoke. It is still unlinked and deleted
     with the Firebase account.
   - Apple's revocation rule (guideline 5.1.1(v)) binds the iPhone app, which holds its tokens. The
     privacy policy says so plainly.
2. **No account lookup, and a generic message.**
   - `account-exists-with-different-credential`, when the project's one-account-per-e-mail setting
     raises it, gets one generic sentence: "This e-mail already signs in another way. Sign in the
     way you did before, then add this method in Settings."
   - No `fetchSignInMethodsForEmail` (e-mail enumeration protection hides it, and it would be a
     lookup). Which method to use is never named.
   - With one-account-per-e-mail off, that error never comes, and the onboarding question (goal 3)
     is the only net.
3. **The Apple button waits behind a build flag**, `APPLE_SIGN_IN` (`BuildConfig.APPLE_SIGN_IN`,
   `AppBuildValues.appleSignIn`), as EduPage's import does.
   - It is on in debug and off in release until the owner's console steps are done (the Services
     ID, the key, Apple enabled in Firebase).
   - Turning it on in release is its own small pull request, before the App Store. Design item 8
     forbids a button that fails.
   - The Sign-in methods row shows Apple under the same flag.
4. **Who is asked "Already use CoPlanly?".**
   - Only an account that Firebase reports as new at an Apple or Google sign-in
     (`AdditionalUserInfo.isNewUser`). An e-mail account created through "Create account" was
     created on purpose.
   - The answer is kept on the phone, per uid (`SecurePreferences`), so it asks once.
   - "Sign out and try again" signs out and deletes the empty account through `deleteAccount`. It
     holds nothing yet, and an empty account left behind would block that method's later linking.
5. **Unlinking Apple revokes at once.** The token is revoked and `apple_tokens/{uid}` deleted in
   the same callable (`unlinkApple`). Its retention is "while Apple is linked".
6. **The Google Calendar account may differ from the sign-in account**, as on Android.
   - The calendar connection is its own Google Sign-In request, with the scope and a server auth
     code.
   - Its tokens live where Android keeps them: the server exchange, then `SecurePreferences`. They
     do not live in Google Sign-In's session.
   - Ending that session at sign-out (`SignInProviderSession`) disconnects nothing the calendar
     keeps. Sign-out and deletion clear the calendar's keys as on Android.

### 3.9 Left to the builder

- The order of pull-request tasks inside part A.
- How Android's Apple button is drawn: Apple's artwork as a vector, or text with the logo glyph.
  Either way it follows Apple's guidelines.
- How `apple_tokens` is retried after a failed revoke: the next deletion run, or a sweep. It must
  not block erasure.
- Where the Google Calendar row sits on the iPhone. It is the Android row's place unless the boards
  say otherwise.

## 4. Parts B–E (outline)

Each has its own design pass. Each of these lines is a starting point, not a decision:

- **B.**
  - A scheduled function sweeps `request_deadlines` every few minutes and does what the
    proposer's phone does today: the deadline notice, the last chance's end and "No answer".
  - It writes as the server with its own clock.
  - The phones keep reminders, which are local notifications.
  - It changes `firestore.rules`' five shapes, the jobs on Android, and the DPIA (R22).
- **C.**
  - The FCM token is registered on the iPhone, replacing `NoPush`.
  - A Notification Service Extension words each type from the app's strings in the app's
    language.
  - Delivery to iOS sends an alert with `mutable-content` and a fixed neutral fallback.
  - A push the extension cannot word shows the fallback until Apple grants the filtering
    entitlement.
  - Local notifications serve event reminders, request reminders and support reminders.
- **D.**
  - `BGAppRefreshTask` runs best-effort sync and the school import.
  - WidgetKit draws the widget, in Swift, reading `TodayWidgetModel` through an App Group, with
    one computation and one wording (design item 18).
- **E.**
  - Universal Links with `apple-app-site-association`.
  - `UIPasteControl` on the pairing screen.
  - The invitation QR code, the QR scanner (AVFoundation), the clipboard, opening links, and
    `SFSpeechRecognizer` with on-device recognition only (item 28).
  - The LZMA1 encoder for PAY by square, byte for byte to `PaymentQrTest`.
  - The privacy manifest, the privacy label and the legal documents.

## 5. Open points

None for part A after the owner's answers of 16 November 2026. The boards in §3.6 are the next
thing the owner sees.
