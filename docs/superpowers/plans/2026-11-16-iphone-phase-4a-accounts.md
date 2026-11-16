# iPhone phase 4 part A — accounts — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship four account features on both phones:
- Sign in with Apple, on both phones;
- sign-in methods that can be linked and unlinked in Settings;
- a one-time question for a brand-new Apple or Google account;
- the Apple token revoked on unlink and deletion.

Ship one on the iPhone only: Google Calendar on the iPhone.

**Architecture:**
- **The sign-in sheets.** Apple's sheet is the shell's, as Google's is: a new pair of
  `CredentialSignIn` members, `signInWithApple()` and `linkApple()`.
  - On Android they run Firebase's web flow on the activity.
  - On the iPhone they run `ASAuthorizationController` through a Swift bridge, then Firebase
    through GitLive.
- **Linking and unlinking** e-mail and Google are `AccountSignIn` members.
- **Apple's refresh token is the server's.** It is exchanged by a new callable and kept in a
  closed collection. The server revokes it on unlink and deletion.
- **One new screen, "Sign-in methods"**, is opened from Settings → Account.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.9.3, GitLive firebase-kotlin-sdk 2.7.0,
Firebase Auth (Android SDK, iOS pods 11.8.0), AuthenticationServices (Swift), Cloud Functions
(Node, `europe-west3`), mocha, Firestore emulator tests.

**Spec:** `docs/superpowers/specs/2026-11-16-iphone-phase-4-design.md` §3 (goals 3.1, anti-goals
3.3, constraints 3.4, calls 3.8). The boards in the canvas's fifth row (approved by the owner on
16 November 2026) bind the look: https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH.

## Global Constraints

- **The last sign-in method is never removable.** Linking a method another account holds links
  nothing, and no message names or confirms anything about another account.
- **No lookup.** No account merge and no `fetchSignInMethodsForEmail`. The
  `account-exists-with-different-credential` sentence is generic (spec §3.8 call 2).
- **The build flag `APPLE_SIGN_IN`:**
  - `BuildConfig.APPLE_SIGN_IN` on Android, on in debug and off in release, overridable with
    `-PCOPLANLY_APPLE_SIGN_IN`. It is the same shape as `EDUPAGE_IMPORT` in
    `app/build.gradle.kts`.
  - `AppBuildValues.appleSignIn` in common code.
  - On the iPhone, the Info.plist key `CoplanlyAppleSignIn`: YES in Debug, NO in Release.
  - The Apple button and the Apple row appear only when the flag is on.
- **Apple's key, client secret and refresh tokens never reach a client.** The key lives in
  `functions/.env`: `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`, `APPLE_BUNDLE_ID`
  (`app.coplanly`) and `APPLE_SERVICES_ID`.
- **`apple_tokens/{uid}`** = `{refreshToken, clientId, storedAtMillis}`.
  - It is server-only: `allow read, write: if false`.
  - It is deleted on unlink and on account deletion.
  - A failed revoke never blocks erasure.
- **Every new callable is declared through `regional`** (`europe-west3`). The new callables are
  `exchangeAppleAuthCode` and `unlinkApple`.
- **Who is asked "Already use CoPlanly?":** only an account Firebase reports as new
  (`isNewUser`) at an Apple or Google sign-in. Once per uid; the answer is kept in
  `SecurePreferences`.
  - "Sign out and try again" deletes the empty account through `AccountDeletion`, then signs out.
- **Google Calendar on the iPhone uses Android's read-only scope and server exchange.**
  - The scope is `GoogleCalendarAccess.SCOPE`.
  - The exchange is `GoogleOAuthFunctions` (`exchangeGoogleAuthCode`,
    `refreshGoogleAccessToken`), with a server auth code requested for the web client id.
  - Its tokens are kept in `SecurePreferences`, never in Google Sign-In's session.
- **Strings go into all five locales**, in sentence case, following "Localization" (check 7).
- **Android changes only where the spec says:** the sign-in screen's button, the Account group's
  row, the new screen, the new-account question and the flag.
  - A screenshot that shows the sign-in screen or the Account group changes. Its baselines are
    re-recorded through the Regenerate workflow (touch `.github/regenerate-request`), never by
    hand.
  - The detekt baseline is never regenerated.
- **Repository rules.**
  - Repository content is in English.
  - Conventional Commits with "(MON-24)".
  - No model identifiers anywhere in the repository.
  - Commit trailers: `Co-Authored-By` and `Claude-Session`, as given in the dispatch.
- **Gradle and the Swift build.**
  - Gradle may be unable to reach Google's Maven in the cloud session. Then the Kotlin compile
    is proven by CI; an early run of the `shared` job is allowed.
  - Node suites (`functions`, `firestore-tests`, `tools`) run locally.
  - Swift is compiled only in CI.
- **Kotlin/Native lessons** (CLAUDE.md, part D's shapes):
  - An `NS_ENUM` may be a Kotlin enum entry or a top-level constant.
  - Category methods are extension functions that need their own import.
  - Initializers are constructors.

## Review Focus

1. **Linking Google while the signed-in account is e-mail only, when that Google account already
   belongs to another CoPlanly account.** Expect "already belongs to another account", the current
   account unchanged, no sign-out and no switch.
   - Test: Task 2, `AccountLinkingTest#credentialInUseLinksNothingAndKeepsSession`.
2. **Unlinking the only remaining method.** Expect the Remove action not offered, and a call
   refused if it is made anyway.
   - Test: Task 6, `SignInMethodsViewModelTest#lastMethodCannotBeRemoved`.
3. **"Sign out and try again" while offline.** Expect the deletion to fail visibly. The account
   stays signed in and the question stays; it never signs out leaving an orphan account silently.
   - Test: Task 5, `NewAccountAskViewModelTest#signOutAndRetryKeepsQuestionWhenDeletionFails`.
4. **Account deletion when Apple's revoke endpoint fails or times out.** Expect every other part
   of the erasure to complete, `apple_tokens/{uid}` deleted, and the failure logged with the uid
   only.
   - Test: Task 1, `apple-tokens.test.js` "revoke failure does not block deletion".
5. **An Apple sign-in where Apple shares no name and a relay e-mail.** Expect the account created,
   the profile name left empty so onboarding's Profile step asks for it, and the relay address
   stored like any address.
   - Test: Task 3, `AppleSignInOutcomeTest#noNameLeavesProfileNameEmpty`.

---

### Task 1: The server — Apple token exchange, revoke and closed collection

**Files:**
- Create: `functions/apple-auth.js` (pure helpers: client secret JWT, token exchange, revoke; no
  Firebase imports, as `calendar-feed.js` is)
- Modify: `functions/index.js`:
  - `exports.exchangeAppleAuthCode` and `exports.unlinkApple` through `regional`;
  - `deleteAccountDataImpl` revokes and deletes `apple_tokens/{uid}`.
- Modify: `firestore.rules`: `match /apple_tokens/{uid} { allow read, write: if false; }`
- Modify: `tools/e2e/coverage.json`: the two callables and the collection, exempt with the reason
  "Apple's real authorization cannot run on the emulators".
- Modify: `functions/.env.example` if present, else `functions/README.md`'s environment list:
  the five `APPLE_*` names.
- Test: `functions/test/apple-auth.test.js`, `functions/test/apple-tokens.test.js`,
  `firestore-tests/rules/apple-tokens.test.js`

**Interfaces:**
- Produces: callable `exchangeAppleAuthCode({code: string, clientId: 'app.coplanly' | <services
  id>})`.
  - It answers `{stored: true}`, or the HttpsError `failed-precondition` "apple-not-configured"
    when any `APPLE_*` is missing.
  - It requires auth.
  - It POSTs `https://appleid.apple.com/auth/token` with `grant_type=authorization_code`.
  - It writes `apple_tokens/{uid}`.
- Produces: callable `unlinkApple()`. It revokes the stored token at
  `https://appleid.apple.com/auth/revoke` (`token_type_hint=refresh_token`), deletes the doc, then
  `admin.auth().updateUser(uid, {providersToUnlink: ['apple.com']})`, and answers `{unlinked:
  true}`.
  - It refuses with `failed-precondition` "last-method" when `apple.com` is the user's only
    provider.

- [ ] **Step 1: Write the failing tests**
  - `apple-auth.test.js`:
    - `clientSecret` builds an ES256 JWT with header `{alg:'ES256', kid}` and claims `{iss: team,
      sub: clientId, aud: 'https://appleid.apple.com', iat, exp: iat + 300}`, verified with the
      test key pair generated in the test;
    - `exchangeCode` posts the five form fields;
    - `revoke` posts `client_id, client_secret, token, token_type_hint`.
    - Use an injected `fetch`.
  - `apple-tokens.test.js` (with the module's `*Impl` functions and a fake db, as the other
    functions tests do):
    - stores on exchange;
    - "apple-not-configured" without env;
    - unlink revokes, deletes the doc and unlinks;
    - unlink refuses the last method;
    - "revoke failure does not block deletion";
    - deletion with no token doc does nothing.
  - `firestore-tests/rules/apple-tokens.test.js`: the owner and a stranger are both refused read
    and write.
- [ ] **Step 2: Run the tests and confirm they fail.**
  `cd functions && npx mocha test/apple-auth.test.js test/apple-tokens.test.js`, and
  `cd firestore-tests && npm test -- rules/apple-tokens.test.js`.
- [ ] **Step 3: Implement the helpers, the callables, the deletion step and the rule.** Read the
  key from `APPLE_PRIVATE_KEY` (PEM with `\n` escapes) and sign with Node's `crypto` (no new
  dependency). Log the uid and outcome only, never a token.
- [ ] **Step 4: Run the suites.**
  `cd functions && npm test && npm run lint`, `cd firestore-tests && npm test`,
  `node tools/check-e2e-coverage.js`. Expected: pass.
- [ ] **Step 5: Commit.** Message: `feat(functions): exchange, keep and revoke Apple's refresh
  token (MON-24)`

### Task 2: The account seams — outcome, linking, unlinking, providers

**Files:**
- Modify: `shared-data/src/commonMain/kotlin/com/coparently/app/data/remote/firebase/AccountSignIn.kt`
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/remote/firebase/SignInMethods.kt`.
  It holds `SignInMethod` (`EMAIL`, `GOOGLE`, `APPLE`, with `providerId` `password`,
  `google.com`, `apple.com`), `SignInOutcome(isNewUser: Boolean)` and `LinkFailure`
  (`InUseByAnotherAccount`, `LastMethod`, `NeedsRecentSignIn`, `Other`).
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/FirebaseAuthService.kt`
  (Android), `shared-data/src/iosMain/.../data/remote/firebase/IosFirebaseAuth.kt` (iPhone),
  and the iOS stand-in `NoAccountSignIn`.
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/remote/firebase/AppleTokens.kt`.
  `class AppleTokens(functions: CallableFunctions)` with
  `suspend fun store(code: String, clientId: String): Result<Unit>` and
  `suspend fun unlink(): Result<Unit>`; bind it in `dataModules`.
- Test: `shared-data/src/commonTest/.../SignInMethodsTest.kt`, the app's
  `FirebaseAuthServiceLinkTest` (MockK) as `AccountLinkingTest`

**Interfaces:**
- Produces, on `AccountSignIn`:
  - `signInWithGoogleIdToken(idToken: String): Result<SignInOutcome>` (it was `Result<Any>`;
    update its callers);
  - `suspend fun linkGoogle(idToken: String): Result<Unit>`;
  - `suspend fun linkEmail(email: String, password: String): Result<Unit>`;
  - `suspend fun unlink(method: SignInMethod): Result<Unit>`. EMAIL and GOOGLE go to the SDK's
    `unlink`. APPLE goes through `AppleTokens.unlink()`, then a `reload()`.
  - `fun linkedMethods(): Flow<Set<SignInMethod>>`, read from the current user's providers and
    re-read after each link or unlink.
- A failure is `Result.failure(LinkException(LinkFailure))`, mapped from Firebase's
  `credential-already-in-use`/`email-already-in-use` → `InUseByAnotherAccount` and
  `requires-recent-login` → `NeedsRecentSignIn`.

- [ ] **Step 1: Write the failing tests.**
  - `SignInMethodsTest`: `SignInMethod.fromProviderId("apple.com") == APPLE`, and an unknown
    provider gives null.
  - `AccountLinkingTest`:
    - `credentialInUseLinksNothingAndKeepsSession`: the mocked `FirebaseUser.linkWithCredential`
      throws `FirebaseAuthUserCollisionException`; the result is `InUseByAnotherAccount`,
      `signOut` is never called and the current uid is unchanged;
    - `linkedMethodsReadsProviders`;
    - `googleSignInReportsNewUser`: `AdditionalUserInfo.isNewUser` true → `SignInOutcome(true)`.
- [ ] **Step 2: Implement both platforms and the stand-in.** The stand-in fails every call with
  an `IllegalStateException` saying "not on this phone".
- [ ] **Step 3: Verify.** Run `./gradlew :shared-data:testAndroidHostTest :app:testDebugUnitTest
  :shared-data:compileKotlinIosSimulatorArm64 detekt`. Expected: green. If Gradle cannot reach
  Google's Maven, say so; CI is the proof.
- [ ] **Step 4: Commit.** Message: `feat: link and unlink sign-in methods behind the account seam
  (MON-24)`

### Task 3: Apple on Android — the web flow and the flag

**Files:**
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/platform/CredentialSignIn.kt`.
  Add `val appleAvailable: Boolean` (default false), `suspend fun signInWithApple():
  Result<AppleSignInResult>` and `suspend fun linkApple(): Result<Unit>`, with
  `data class AppleSignInResult(val outcome: SignInOutcome, val givenName: String?, val
  familyName: String?)`.
- Modify: `app/src/main/java/com/coparently/app/presentation/auth/AndroidCredentialSignIn.kt`.
  - Sign-in uses `FirebaseAuth.startActivityForSignInWithProvider(activity,
    OAuthProvider.newBuilder("apple.com").setScopes(listOf("email","name")).build())`.
  - Linking uses `currentUser.startActivityForLinkWithProvider`.
  - `appleAvailable = BuildConfig.APPLE_SIGN_IN`.
- Modify: `app/build.gradle.kts`. Add `appleSignInFlag(default)` and the buildConfigField
  `APPLE_SIGN_IN` (debug true, release false), mirroring `edupageImportFlag`.
- Modify: `AppBuildValues` (`appleSignIn: Boolean`), `AndroidPlatform.kt`, `IosApp.kt` (read
  `CoplanlyAppleSignIn` from the bundle), and every other `AppBuildValues(` constructor call (the
  screenshot frame, previews, tests).
- Test: `app/src/test/.../AppleSignInOutcomeTest.kt`

- [ ] **Step 1: Write the failing test** `noNameLeavesProfileNameEmpty`. It maps a result with
  null names to no display-name update. It also writes `givenName` "Jana" and `familyName`
  "Nováková" as "Jana Nováková" (through the same `ProfileIdentity` path a Google name takes).
- [ ] **Step 2: Implement.** Don't store any Apple token on Android: the web flow hands no
  authorization code (spec §3.8 call 1); write that in the KDoc.
- [ ] **Step 3: Verify** as in Task 2, Step 3, plus `assembleDebug`.
- [ ] **Step 4: Commit.** Message: `feat(android): sign in with Apple through Firebase's web flow,
  behind a flag (MON-24)`

### Task 4: Apple on the iPhone — the system sheet and the code exchange

**Files:**
- Create: `iosApp/CoPlanly/AppleSignInBridge.swift`.
  - It runs `ASAuthorizationAppleIDProvider` requests (scopes `.fullName`, `.email`) with a
    random nonce whose SHA-256 goes in the request.
  - It answers `(idToken, rawNonce, authorizationCode, givenName, familyName)` or an error code.
  - It sits behind a Kotlin interface `AppleIdSource`, as `GoogleSignInBridge` sits behind
    `GoogleIdTokenSource`.
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/AppleIdSource.kt`
- Modify: `IosCredentialSignIn` with the two new members.
  - Sign-in uses GitLive's `OAuthProvider.credential("apple.com", idToken, rawNonce)`, then
    `signInWithCredential`, then `AppleTokens.store(authorizationCode, "app.coplanly")`.
  - Linking uses `linkWithCredential` and the same store.
  - A failed store is logged and ignored: the account works, and only the revoke is lost.
- Modify: `iosApp/project.yml`: the `com.apple.developer.applesignin` entitlement (`Default`) and
  the `CoplanlyAppleSignIn` Info.plist key per configuration.
  `AppDelegate.swift` passes the bridge to `IosApp.start`.
- Test: `shared-ui/src/iosTest/.../AppleNonceTest.kt`, if the nonce helper is Kotlin; else a
  Swift unit test is out of scope. Say so in the report.

- [ ] **Step 1: Write the failing test** (if Kotlin holds the nonce helper):
  `nonceHashIsSha256Hex`. `sha256Hex("abc") ==
  "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"`.
- [ ] **Step 2: Implement.**
  - The Swift bridge presents on the top view controller, as `GoogleSignInBridge` does.
  - User cancellation is `ASAuthorizationError.canceled` → a cancel result, never an error banner.
- [ ] **Step 3: Verify.** Run `./gradlew :shared-ui:compileKotlinIosSimulatorArm64
  :shared-ui:compileTestKotlinIosSimulatorArm64`. Expected: green, or "Gradle cannot run here; CI".
- [ ] **Step 4: Commit.** Message: `feat(ios): sign in with Apple through the system sheet
  (MON-24)`

### Task 5: The sign-in screen and the new-account question

**Files:**
- Modify: `presentation/auth/AuthViewModel.kt`. Add `suspend fun signInWithApple(credentials:
  CredentialSignIn)`, mirroring `signInWithGoogle` (the `finally`, the analytics event
  `logLogin("apple")`).
  - After an Apple or Google sign-in whose outcome `isNewUser`, it records
    `NewAccountAsk.pending(uid)` in `SecurePreferences`.
- Modify: `presentation/auth/AuthScreen.kt`. Add the Apple button above Google (boards 1 and 4),
  shown only when `credentials.appleAvailable && buildValues.appleSignIn`.
  - On Android it is drawn per Apple's guidelines: black, the white Apple glyph, "Sign in with
    Apple", Google's height.
  - On the iPhone it comes through a new `NativeControls` member, `appleSignInButton(onClick,
    modifier, background)`. It is `ASAuthorizationAppleIDButton`, in-place, with a background
    (part C's interop rules), recorded in `RecordingNativeControls`.
- Modify: `AuthError` and `AuthErrors` (both platforms). Add `ExistsWithDifferentMethod`, worded
  "This e-mail already signs in another way. Sign in the way you did before, then add this method
  in Settings." (board 4b).
- Create: `presentation/onboarding/NewAccountAsk.kt` (the store key per uid, `pending`, `answer`)
  and `NewAccountAskViewModel` in `onboardingUiModule`.
  - It exposes `ask: StateFlow<Boolean>`, `keepNewAccount()` and `signOutAndRetry()`, which goes
    through `AccountDeletion` then `AuthSession.signOutCompletely`.
- Modify: `OnboardingScreen.kt`. While `ask` is true, draw the question (board 3) before the first
  step.
- Strings (five locales, in `auth_strings.xml` and `onboarding_strings.xml`):
  `auth_sign_in_with_apple`, `auth_error_exists_with_different_method`, `new_account_ask_title`
  ("Already use CoPlanly?"), `new_account_ask_body` ("If you signed in another way before, sign
  in that way, then add this method in Settings."), `new_account_ask_retry` ("Sign out and try
  again"), `new_account_ask_new` ("This is a new account").
- Test: `shared-ui/src/androidHostTest/.../NewAccountAskViewModelTest.kt` and `AuthViewModelTest`
  additions (in the app)

- [ ] **Step 1: Write the failing tests.**
  - `NewAccountAskViewModelTest`:
    - `asksOnlyWhenPendingForThisUid`;
    - `keepNewAccountClearsTheQuestion`;
    - `signOutAndRetryDeletesThenSignsOut` (order verified);
    - `signOutAndRetryKeepsQuestionWhenDeletionFails`: deletion fails → no sign-out, `ask` still
      true, an error `UiText` emitted.
  - `AuthViewModelTest`:
    - `appleNewUserMarksQuestion`;
    - `googleExistingUserDoesNotMarkQuestion`;
    - `emailSignUpNeverMarksQuestion`;
    - `existsWithDifferentMethodMapsToGenericError`.
- [ ] **Step 2: Implement** the code above and the strings. Then run
  `node tools/check-invariants.js`; expected: pass.
- [ ] **Step 3: Verify** as in Task 2, Step 3. Add `node --test tools/test/*.test.js`.
- [ ] **Step 4: Commit.** Message: `feat: offer Apple on the sign-in screen and ask a new account
  once (MON-24)`

### Task 6: The "Sign-in methods" screen and the Settings row

**Files:**
- Create: `presentation/settings/SignInMethodsScreen.kt` and `SignInMethodsViewModel.kt`
  (`settingsUiModule`).
- Modify: `presentation/navigation/Screen.kt`, `AppDestinationCatalog` (`PUSH`) and
  `rememberAppDestinations`. Add `Screen.SignInMethods`.
- Modify: `SettingsScreen.kt`. In the Account group, add the row "Sign-in methods" (board 2), with
  a value naming the linked methods.
- Modify: `iosApp/CoPlanlyUITests/ShellUITests.swift`. The walk opens Settings → Sign-in methods
  and sees the e-mail row; the tour's `tourRoutes` gains the route.
- Strings (five locales, `settings_account_strings.xml`): `sign_in_methods_title`,
  `sign_in_methods_email`, `sign_in_methods_google`, `sign_in_methods_apple`,
  `sign_in_methods_add`, `sign_in_methods_remove`, `sign_in_methods_last_note` ("You can remove
  any method except the last one."), `sign_in_methods_in_use` ("This sign-in already belongs to
  another CoPlanly account. Sign in with it to use that account.").
- Test: `shared-ui/src/androidHostTest/.../SignInMethodsViewModelTest.kt`; the app's
  `AppDestinationCatalog` tests if they enumerate routes.

**Interfaces:**
- Consumes: `AccountSignIn` (Task 2) and `CredentialSignIn` (Tasks 3 and 4).
- Behaviour:
  - Adding E-mail asks for an e-mail and a password (the existing auth field components) →
    `linkEmail`.
  - Adding Google → `credentials.googleIdToken()` → `linkGoogle`.
  - Adding Apple → `credentials.linkApple()`; its row shows only under the flag.
  - Removing a method → `ConfirmationDialog`, then `unlink`.

- [ ] **Step 1: Write the failing tests** (`SignInMethodsViewModelTest`):
  - `lastMethodCannotBeRemoved`: one linked → `canRemove` false, and `remove()` makes no call;
  - `inUseShowsBannerAndKeepsMethods`;
  - `needsRecentSignInAsksToSignInAgain` (a `UiText` telling the parent to sign out and in, then
    retry);
  - `appleRowHiddenWithoutFlag`.
- [ ] **Step 2: Implement** the screen, the row, the route and the strings. The banner is an
  `InlineBanner` (ATTENTION). Rows are `SectionGroup`/`SectionRow`, so the iPhone's inset-grouped
  look comes from part C.
- [ ] **Step 3: Verify** as in Task 5, Step 3.
- [ ] **Step 4: Commit.** Message: `feat: list, add and remove sign-in methods in Settings
  (MON-24)`

### Task 7: Google Calendar on the iPhone

**Files:**
- Create: `shared-data/src/iosMain/.../data/remote/google/IosGoogleCalendarAccount.kt`.
  - It implements `GoogleCalendarAccount`, `GoogleCalendarConnectionSource` and
    `CredentialProvider`, the three `NoGoogleCalendar` stood in for.
  - It keeps the tokens in `SecurePreferences` under the keys `CredentialManagerService` uses
    (read them there and reuse the same names, so the sign-out and deletion clearing applies).
  - It exchanges and refreshes through `GoogleOAuthFunctions`.
- Modify: `PlatformDataModule.ios.kt`. Bind it in place of `NoGoogleCalendar`.
- Modify: `GoogleSignInBridge.swift` and its Kotlin interface. Add `requestCalendarAccess(
  completion)`, which calls `GIDSignIn.signIn(withPresenting:hint:additionalScopes:
  [calendar.events.owned.readonly])` with `serverClientID` = the web client id from Info.plist
  (`GIDServerClientID`) and answers the `serverAuthCode`.
  - It does not touch the sign-in's session state used by `IosCredentialSignIn`.
- Create: `shared-ui/src/iosMain/.../platform/IosGoogleCalendarSignIn.kt`. It implements
  `GoogleCalendarSignIn` over the bridge and the account above, in place of
  `NoGoogleCalendarSignIn` in `ProvideIosShell`.
- Modify: `iosApp/project.yml` / Info.plist: `GIDServerClientID` from the build setting
  `COPLANLY_GOOGLE_WEB_CLIENT_ID`. Blank → the row is not offered (`available` false).
- Test: `shared-data/src/iosTest/.../IosGoogleCalendarAccountTest.kt`.
  - `connectionReflectsStoredTokens`;
  - `refreshUsesServerAndStoresNewToken` (a fake `CallableFunctions`);
  - `disconnectClearsKeys`.

- [ ] **Step 1: Write the failing tests** above.
- [ ] **Step 2: Implement.** The import itself (`CalendarSyncRepository`,
  `GoogleCalendarAutoImport`) is common code already; nothing there changes.
- [ ] **Step 3: Verify** with the iOS compile commands of Task 4, Step 3.
- [ ] **Step 4: Commit.** Message: `feat(ios): connect Google Calendar on the iPhone (MON-24)`

### Task 8: Record part A

**Files:**
- Modify: `docs/PRIVACY-POLICY.md` (and regenerate `web/privacy/` per `web/README.md`),
  `docs/legal/RECORDS-OF-PROCESSING.md`, `docs/legal/DPIA.md` and `docs/DATA-SAFETY.md`.
  - Apple is an identity provider: what it shares (name once, an e-mail or a relay address).
  - `apple_tokens` is kept while Apple is linked, revoked and deleted on unlink or deletion; an
    Android-added Apple method has no token (spec §3.8 call 1).
  - Google Calendar on the iPhone is the same scope as on Android.
- Modify: `iosApp/README.md` and `functions/README.md`. Add the owner's console steps:
  1. In the Apple Developer account: the Sign in with Apple capability, a Services ID with
     Firebase's return URL, and a key.
  2. In the Firebase console: Apple enabled with those values.
  3. In `functions/.env`: the five `APPLE_*` values.
  4. Turning the flag on in release as its own pull request.
- Modify: `docs/DEVICE-CHECKLIST.md`. Add §3.42, Accounts:
  - Apple sign-in on an iPhone and on Android;
  - linking each method each way;
  - a method that belongs to another account refused;
  - the last method not removable;
  - the new-account question;
  - the Apple token gone from "Apps using Apple ID" after a deletion;
  - Google Calendar on an iPhone.
- Modify: `docs/ROADMAP.md` MON-24 (part A of phase 4 built). `CLAUDE.md`: a short entry under
  "Things that are easy to get wrong" for the account rules — the last method, no lookup, Apple's
  tokens server-only, the flag. Add the stand-ins replaced to the iPhone notes.
- Modify: `tools/manual-test-plan.js`'s `RULES`, if it maps auth paths, to name §3.42.

- [ ] **Step 1: Write the edits.**
- [ ] **Step 2: Run `node tools/check-invariants.js && node --test tools/test/*.test.js`.**
  Expected: pass. Check 6 (Google scope) still names one scope.
- [ ] **Step 3: Commit.** Message: `docs: record the iPhone app's phase 4 part A, accounts (MON-24)`
