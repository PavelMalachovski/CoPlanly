# Invited parent's first minute — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A parent invited by the other parent never types the code, sees who invited them and why the app is fair before linking, and can reach the calendar in three steps after linking.

**Architecture:** A pure parser and a Play Install Referrer reader feed the existing `MainActivity._pendingPairingCode`; `PairingScreen` swaps its confirmation dialog for a full-screen invitation view when the parent is not yet linked; `OnboardingUiState` gains a `shortPath` flag that narrows `steps` to CoParent → Notifications → compact Profile. Copy changes in five locales.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, `com.android.installreferrer:installreferrer:2.2`, JUnit/MockK/Turbine, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-23-invited-parent-first-run-design.md`

## Global Constraints

- All new user-facing strings in `values`, `values-cs`, `values-de`, `values-ru`, `values-uk`, same commit; English in sentence case; `node tools/check-invariants.js` passes.
- Never "Mom"/"Dad"; names only. Never "nothing changes without your yes" — the schedule fact is exactly "A schedule change or a day swap waits for your yes."
- A referrer code is only a prefill: it goes through `PairingViewModel.redeemCode` → preview → the parent's tap. Never auto-redeem.
- Compose only, stateless composables, Hilt, KDoc on public API, theme tokens (`Spacing`, `IconSizes`, shapes), minSdk 26.
- Conventional Commits; commit trailers from the session.
- **No Android SDK in this session** (dl.google.com is blocked): Gradle steps are verified by CI on the pull request. Locally runnable: `node tools/check-invariants.js`, `node --test tools/test/*.test.js`, `cd web-tests && npx playwright test pair-page.spec.js`.

## Review Focus

1. A referrer present on a **second** sign-in (sign-out, other account) must not reopen pairing → Task 1 test `readsOnlyOnce`.
2. A malformed referrer (`utm_source=google-play&utm_medium=organic`, `pair=abc`, empty) yields no code → Task 1 parser tests.
3. A deep-link code already pending wins over the referrer → Task 2 (`MainActivity` only fills an empty slot).
4. Back on the invitation view must decline, not leave the code half-redeemed → Task 3 (`BackHandler` → `answerConfirmation(false)`).
5. Pressing Continue after going back from the short path returns to the full wizard → Task 4 test `continueAfterShortPathRestoresFullWizard`.

---

### Task 1: Referrer parsing and the one-shot reader

**Files:**
- Create: `app/src/main/java/com/coparently/app/domain/pairing/InstallReferrer.kt`
- Create: `app/src/main/java/com/coparently/app/data/pairing/PlayInstallReferrerSource.kt`
- Create: `app/src/main/java/com/coparently/app/di/InstallReferrerModule.kt`
- Modify: `app/build.gradle.kts` (dependency `com.android.installreferrer:installreferrer:2.2`)
- Modify: `app/src/main/java/com/coparently/app/presentation/pairing/PairingLink.kt`
- Test: `app/src/test/java/com/coparently/app/domain/pairing/InstallReferrerTest.kt`

**Interfaces:**
- Produces: `object InstallReferrer { const val PAIR_KEY = "pair"; fun pairingCode(referrer: String?): String?; fun encode(code: String): String }` — `encode` returns the URL-encoded value for Play's `referrer=` parameter (`pair%3DK7X2Q9`).
- Produces: `interface InstallReferrerSource { suspend fun pairingCodeOnce(): String? }` (domain) bound to `PlayInstallReferrerSource` (Singleton).
- Produces: `internal fun playStoreUrlFor(code: String?): String` in `PairingLink.kt`; `PLAY_STORE_URL` stays for professional/friend invitations.

- [ ] **Step 1: Write failing tests** in `InstallReferrerTest`:
  - `pairingCode("pair=K7X2Q9")` == `"K7X2Q9"`; lower case `"pair=k7x2q9"` → `"K7X2Q9"`.
  - `pairingCode("utm_source=google-play&pair=K7X2Q9&utm_medium=x")` == `"K7X2Q9"`.
  - `pairingCode("utm_source=google-play&utm_medium=organic")`, `"pair=abc"`, `""`, `null` → `null`.
  - `pairingCode("pair%3DK7X2Q9")` == `"K7X2Q9"` (the raw form if Play hands it back undecoded).
  - `encode("K7X2Q9")` == `"pair%3DK7X2Q9"`; `playStoreUrlFor("K7X2Q9")` == `"https://play.google.com/store/apps/details?id=app.coplanly&referrer=pair%3DK7X2Q9"`; `playStoreUrlFor(null)` == `PLAY_STORE_URL`.
- [ ] **Step 2: Implement** `InstallReferrer` (URL-decode, split on `&`, take `pair=`, validate with `InviteCodeGenerator.LENGTH` and `[A-Z0-9]`, as `PairingUri.extractCode` does) and `playStoreUrlFor`.
- [ ] **Step 3: Implement `PlayInstallReferrerSource(@ApplicationContext context)`**: a plain `SharedPreferences` file `install_referrer` with boolean `read`. If `read`, return null. Otherwise connect `InstallReferrerClient` inside `suspendCancellableCoroutine` with a 5 s `withTimeoutOrNull`; on `OK` take `installReferrer`, end the connection, set `read = true` (also on `FEATURE_NOT_SUPPORTED`/`SERVICE_UNAVAILABLE` — "once" means once per install), return `InstallReferrer.pairingCode(...)`. On timeout/exception: log, leave `read` false, return null. Plain prefs rather than `EncryptedPreferences` on purpose: the flag is not secret, the code is never stored, and `EncryptedPreferences.clear()` at sign-out would re-arm it.
- [ ] **Step 4: Bind** in `InstallReferrerModule` (`@Binds @Singleton`).
- [ ] **Step 5: Run** `node tools/check-invariants.js` (locally); Gradle tests run in CI. Commit `feat: read the pairing code from the Play install referrer`.

### Task 2: Wire the referrer into launch and the invitation links

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/MainActivity.kt` (inject `InstallReferrerSource`; in `onCreate`, `lifecycleScope.launch { source.pairingCodeOnce()?.let { if (_pendingPairingCode.value == null) _pendingPairingCode.value = it } }`)
- Modify: `app/src/main/java/com/coparently/app/presentation/pairing/PairingScreen.kt:422` → `playStoreUrlFor(invite.code)`
- Modify: `web/pair/index.html` (`PLAY_URL` gains `&referrer=` + `encodeURIComponent('pair=' + code)` once the code is known; both the button and `S.browser_fallback_url`)
- Modify: `web-tests/pair-page.spec.js` (expected Play URL carries the referrer)
- Modify: `app/src/test/.../MainActivity*` tests only if an existing test constructs `MainActivity` dependencies; the `androidTest` `FakeFirebaseModule` is untouched — add a `@TestInstallIn` fake `InstallReferrerSource` returning null in `app/src/androidTest/java/com/coparently/app/testing/FakeInstallReferrerModule.kt`, so no instrumented test talks to Play.

**Interfaces:**
- Consumes: `InstallReferrerSource.pairingCodeOnce()`, `playStoreUrlFor(code)` (Task 1).

- [ ] **Step 1: Update `pair-page.spec.js`** expectation first; run `cd web-tests && npx playwright test pair-page.spec.js` → FAIL.
- [ ] **Step 2: Change `web/pair/index.html`**; rerun → PASS.
- [ ] **Step 3: MainActivity + share message + androidTest fake.**
- [ ] **Step 4: Commit** `feat: carry the pairing code through the Play install`.

### Task 3: The invitation view

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/pairing/InvitationView.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/pairing/PairingScreen.kt:227` (show `InvitationView` instead of `InviteConfirmationDialog` while the pairing state is not `Paired`; keep the dialog for a paired parent)
- Modify: `app/src/main/res/values*/pairing_strings.xml` (5 locales)
- Test: `app/src/test/java/com/coparently/app/screenshots/InvitationScreenshots.kt` (Roborazzi, text-heavy matrix, named and unnamed)
- Test: `app/src/test/java/com/coparently/app/presentation/pairing/InvitationViewTest.kt` (Robolectric Compose: buttons call back; Back declines)

**Interfaces:**
- Produces: `@Composable internal fun InvitationView(inviterName: String?, isBusy: Boolean, onAnswer: (Boolean) -> Unit, modifier: Modifier = Modifier)`.
- Consumes: `PairingFormState.confirmation`, `PairingViewModel.answerConfirmation(Boolean)`.

Content (strings, English):
- `pairing_invitation_title` "%1$s invites you to CoPlanly"; `pairing_invitation_title_unnamed` "You're invited to CoPlanly"
- `pairing_invitation_lede` "One calendar for your children's two homes — whose day it is, what changed, who paid."
- `pairing_invitation_fact_same` "You both see the same calendar. Neither of you is in charge of it."
- `pairing_invitation_fact_agree` "A schedule change or a day swap waits for your yes."
- `pairing_invitation_fact_messages` "Sent messages can't be edited or deleted — by either of you."
- `pairing_invitation_fact_unlink` "You can unlink at any time in Settings."
- `pairing_invitation_link` "Link with %1$s"; `pairing_invitation_link_unnamed` "Link"; `pairing_invitation_not_now` "Not now"

Layout: scrollable column — `HeroIcon` (`Icons.Default.Diversity3` or `People`), title (`headlineSmall`, `heading()`), lede (`bodyLarge`), `SectionGroup` of four `SectionRow`s with icons (`CalendarMonth`, `HowToVote`/`ThumbUp`, `Lock`, `LinkOff`), `StickyActionBar` with the primary button and a `TextButton` "Not now". `BackHandler { onAnswer(false) }`. Primary disabled while `isBusy`.

- [ ] **Step 1: Write `InvitationViewTest`**: `primaryLinks` (click "Link with Alice" → `onAnswer(true)`), `notNowDeclines`, `unnamedUsesFallbackTitle`, `backDeclines` (`Espresso.pressBack()` / `activity.onBackPressedDispatcher` → `onAnswer(false)`).
- [ ] **Step 2: Implement `InvitationView` and the strings** (cs/de/ru/uk drafted, flagged for native review in the PR body).
- [ ] **Step 3: Switch `PairingScreen`**: when `form.confirmation != null` and the pairing state is not paired, render `InvitationView` in place of the screen's content (full screen, its own Scaffold), else the existing dialog.
- [ ] **Step 4: Add `InvitationScreenshots`** (variants via `ScreenshotVariants`' text-heavy set).
- [ ] **Step 5: Commit** `feat: show the invitation before linking a new co-parent`.

### Task 4: The short path in onboarding

**Files:**
- Modify: `app/src/main/java/com/coparently/app/domain/holidays/HolidayCountry.kt` (add `fun ofCodeOrNull(code: String?): HolidayCountry?` — strict, no fallback)
- Modify: `app/src/main/java/com/coparently/app/presentation/onboarding/OnboardingStep.kt` (add `val SHORT_PATH = listOf(CoParent, Notifications, Profile)` in the companion; `Notifications` dropped by the ViewModel when already granted)
- Modify: `app/src/main/java/com/coparently/app/presentation/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/onboarding/OnboardingScreen.kt` (`LinkedCoParent` gets the action; `ProfileStep` compact when `state.shortPath`)
- Modify: `app/src/main/res/values*/onboarding_strings.xml`
- Test: `app/src/test/java/com/coparently/app/presentation/onboarding/OnboardingViewModelTest.kt`, `.../domain/holidays/HolidayCountryTest.kt` (or the existing test file for it)

**Interfaces:**
- Produces in `OnboardingUiState`: `val shortPath: Boolean = false`, `val skipNotifications: Boolean = false`; `steps` becomes `if (shortPath) OnboardingStep.SHORT_PATH.filterNot { skipNotifications && it == OnboardingStep.Notifications } else OnboardingStep.stepsFor(caresFor)`; `val canOpenCalendarNow: Boolean get() = coParent is CoParentLink.Linked && (fetch as? CoParentFetch.Done)?.found?.isEmpty == false`.
- Produces in `OnboardingViewModel`: `fun openCalendarNow(deviceRegion: String?, notificationsGranted: Boolean)` — no-op unless `canOpenCalendarNow`; sets `shortPath = true`, `skipNotifications = notificationsGranted`, `country = HolidayCountry.ofCodeOrNull(deviceRegion) ?: country` (region cleared when the country changes), then `leaveStep(CoParent)`. `next()` on `CoParent` sets `shortPath = false`.
- String: `onboarding_coparent_open_calendar` "Looks right — open the calendar".

- [ ] **Step 1: Write failing tests**:
  - `HolidayCountry.ofCodeOrNull("de") == GERMANY`, `ofCodeOrNull("US") == null`, `ofCodeOrNull(null) == null`.
  - `openCalendarNowWalksShortPath`: linked + `Done(CoParentData(children = 1))` → after `openCalendarNow("DE", false)`: `step == Notifications`, `steps == [CoParent, Notifications, Profile]`, `country == GERMANY`.
  - `openCalendarNowSkipsGrantedNotifications`: `openCalendarNow(null, true)` → `step == Profile`, country unchanged.
  - `openCalendarNowIgnoredWhenNothingArrived`: `Done(CoParentData())` and `Running` → state unchanged.
  - `nextOnShortProfileFinishes`: name "Bob", on Profile, `next()` → `userRepository.updateUser` called with `countryCode = "DE"` and `onboardingCompletedAt != null`; `isFinished == true`.
  - `continueAfterShortPathRestoresFullWizard`: short path → `back()` to CoParent → `next()` → `shortPath == false`, `steps == stepsFor(caresFor)`.
- [ ] **Step 2: Implement** `ofCodeOrNull`, the state fields, `openCalendarNow`, the `next()` reset.
- [ ] **Step 3: Screen**: in `LinkedCoParent`, when `state.canOpenCalendarNow`, a `FilledTonalButton` "Looks right — open the calendar" reading `LocalConfiguration.current.locales[0].country` and `hasNotificationPermission(context)` and calling `openCalendarNow`. In `ProfileStep`, when `state.shortPath`, render only the name field and the `CountryPicker` (with region); hide colour, date of birth, phone.
- [ ] **Step 4: Commit** `feat: let an invited parent open the calendar right after linking`.

### Task 5: First-run copy

**Files:**
- Modify (5 locales each): `auth_strings.xml` (`auth_tagline`, `auth_sign_up_subtitle`), `onboarding_strings.xml` (`onboarding_intro_title`, `onboarding_intro_body`, `onboarding_coparent_body`), `home_strings.xml` (`home_pairing_prompt`), `pairing_strings.xml` (`pairing_share_message`, `pairing_share_message_web` — **argument numbers and order unchanged**).

English values:
- `auth_tagline` "One calendar for children with two homes"
- `auth_sign_up_subtitle` "Whose day it is, what changed, who paid — the same for both parents"
- `onboarding_intro_title` "A few questions to set up your calendar"
- `onboarding_intro_body` "Your children, the custody schedule and how you split costs — so the calendar is ready when you open it. Only your name is required; everything else can wait.\n\nYou can also keep emergency details here, such as a blood group or a grandparent's number, for whoever is with your child."
- `onboarding_coparent_body` "Linking puts the two of you on one calendar. If your co-parent has already set things up, you will see it straight away. If they haven't joined yet, invite them."
- `home_pairing_prompt` "Once your co-parent joins, you both see the same days, agree changes before they happen and split costs in one place."
- `pairing_share_message` "%1$s invites you to CoPlanly — one calendar for our children's two homes. We both see the same days, and schedule changes need both of us to agree.\nInstall: %4$s (the code comes along)\nAlready have the app? %3$s · Code: %2$s\nOn an iPhone? Ask me for our calendar link, it opens in Apple Calendar."
- `pairing_share_message_web` "%1$s invites you to CoPlanly — one calendar for our children's two homes. We both see the same days, and schedule changes need both of us to agree: %2$s\nCode, if you need it: %3$s"

- [ ] **Step 1:** Edit all five locales. **Step 2:** `node tools/check-invariants.js` → PASS. **Step 3:** Commit `feat: say what CoPlanly solves on the first screens`.

### Task 6: Docs, legal, checklist, baselines

**Files:**
- Modify: `docs/legal/PRIVACY-POLICY.md`, `docs/legal/DATA-SAFETY.md`, `docs/legal/RECORDS-OF-PROCESSING.md` (one line each: the Play install referrer is read once on the phone to prefill an invitation code; nothing is sent to our servers); regenerate `web/privacy/` per `web/README.md`.
- Modify: `docs/DEVICE-CHECKLIST.md` (new check under the pairing section: install from a Play link carrying the code on an internal-testing track; the invitation view opens after sign-up with the code filled; a second sign-in does not reopen it) and `tools/manual-test-plan.js` `RULES` only if the section number is new.
- Modify: `CLAUDE.md` item 37 (the invitation view replaces the dialog for an unlinked parent; the referrer path) and onboarding item 22 (the short path).
- Touch: `.github/regenerate-request` (new screenshot baselines).
- Create: `docs/RELEASE-STATUS-2026-10-23.md` (G5: done in code / blocking, ordered, who does each, pointing into ROADMAP §3 and `AUDIT-2026-10-release.md`).

- [ ] **Step 1:** Edit the docs; run `node --test tools/test/*.test.js` → PASS. **Step 2:** Commit `docs: invited-parent path in the policy, checklist and CLAUDE.md`, then `docs: release status as of 23 October 2026`.

### Task 7: Verify on CI

- [ ] **Step 1:** Push; with the owner's consent open a draft PR so CI runs (`build-test`, `static`, `detekt`, `screenshots`, `instrumented`, `web`, `invariants`).
- [ ] **Step 2:** Fix every red job; after Regenerate's bot commit, confirm `screenshots` verifies.
