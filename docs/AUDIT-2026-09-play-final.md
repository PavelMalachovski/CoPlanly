# CoPlanly — final Google Play readiness audit (September 2026)

Audit date: 2026-09-25, on `main` at `8b77d1d5` (PR #121, the AI assist's client half). Features
are frozen by the owner: this audit fixes only compliance and release defects, and writes down
what is left for the owner to do.

**What this document replaces.** Section 4 is the one ordered checklist from today to production.
It merges and supersedes `LAUNCH-PLAYBOOK.md` §2.8, `legal/LEGAL-REVIEW-2026-09.md` §4,
`DEVICE-CHECKLIST.md` §0, the ops order in `functions/README.md` and the AI switch-on steps there,
and `AUDIT-2026-10-release.md` §3.2. Those sections stay as the detailed runbooks each step links
to; the order and the "closed test or production" answer live here.

**How it was checked.** The code and configuration on `main`, not what the docs say about them:
the manifest, `app/build.gradle.kts`, the proguard rules, `res/xml/`, the permission call sites,
every `Log.` call, the Settings screen, `functions/index.js`'s deletion path, `web/`,
`docs/legal/`, `docs/play-listing/` and `.github/workflows/release.yml`.

**What could not be checked here, and why.**

- **The merged manifest.** This session reaches `maven.google.com` only as a redirect to
  `dl.google.com`, which the network policy refuses, so no Google library's AAR manifest could be
  read and Gradle cannot run. What the libraries add is stated from training knowledge (marked ⚠)
  and step C-4 below has the owner confirm it with `bundletool` on the real bundle.
- **Play Console and Play Help Center pages.** `support.google.com` is refused too. Three Play
  rules were confirmed through `developer.android.com` and web search (sources in §5); everything
  else about the Console is from training knowledge and marked ⚠. Play changes these screens
  often: check each answer against the Console as you reach it.
- **Anything on a phone or in the live Firebase project**: whether the deploys ran, the Firestore
  location, a release build on a device.

## 1. Verdict

**The app's code is ready for a closed test. The release is not, and what blocks it is almost all
owner work outside the code.**

What the binary does meets what Play checks automatically: target API 36 (the requirement since
31 August 2026, verified), 16 KB pages proven on an emulator, a minimal permission set with every
dangerous permission asked in context, the photo picker instead of any media permission, no
advertising id (now including the AdServices permissions, fixed below), backup off, cleartext off
in release, R8 on and probed, the AI assist off in release, and in-app account deletion.

This audit fixed four things (§3): two permissions Analytics merged in that contradicted the
"no advertising id" declaration, two backup domains left out of the exclusion rules, the
**missing 512 × 512 store icon** (Play will not save a listing without one), and a privacy policy
and Data Safety draft that said less than the code holds.

What stands between `main` and a closed test is **nine owner items, all P0**: the Firebase app and
`google-services.json` for `app.coplanly`; the upload key; the deploys (functions moved to
`europe-west3`, rules, storage) after confirming the Firestore location; the controller's identity
in the legal texts and the policy and deletion pages hosted, with the policy's URL built into the
app (Play requires the policy **inside** the app as well as in the Console); the Play Console
declarations; and **a reviewer demo account already paired with a demo co-parent**, without which
a reviewer sees an app that does nothing alone.

Before production, add six P1 items: counsel's sign-off; the 14-day closed test with 12 testers;
**Google's verification of the Calendar scope** (which needs a domain the project does not own
yet); **a decision on reporting and blocking in chat**, which Play's user-generated content policy
asks of any one-to-one messaging; the EduPage "Coming soon" row, which must become real or go; and
the device checks CI cannot run (REL-7, the Bakaláři import against a live school).

## 2. Findings

Severity: **P0** blocks the closed test; **P1** blocks production; **P2** later or advisory.
Status: *fixed in* a commit on this branch, *owner* (a step in §4), or *accepted* (with the reason).

| # | Sev. | Finding | Evidence | Status |
| --- | --- | --- | --- | --- |
| F-1 | P0 | **No Firebase app for `app.coplanly` yet.** A local build fails without the new `google-services.json`; a build without the file at all crashes at launch. The release workflow refuses to run without it (good), so nothing can be uploaded until it exists | `app/build.gradle.kts:19-28,129`; `.github/workflows/release.yml` "Restore google-services.json" | Owner, B-1 |
| F-2 | P0 | **No upload key.** Without all four `COPLANLY_RELEASE_*` values the bundle comes out unsigned (deliberate for CI). Play requires an AAB and enrols new apps in Play App Signing ⚠ | `app/build.gradle.kts:45-72,191`; `release.yml` | Owner, C-3 |
| F-3 | P1 | **Analytics merges `ACCESS_ADSERVICES_AD_ID` and `ACCESS_ADSERVICES_ATTRIBUTION`** ⚠ into the manifest beside the already-removed `AD_ID`. The app shows no ads and the Data Safety draft and policy say no advertising id is read | `AndroidManifest.xml:22-39` | **Fixed in `fae4455d`** (`tools:node="remove"`; Analytics itself is unaffected) |
| F-4 | P0 | **The privacy policy is neither final nor hosted, and the app does not link it.** Play's User Data policy requires the policy in the Console field **and within the app** ⚠. `publishedPrivacyPolicyUrl` and `publishedTermsUrl` are blank, which (correctly) hides both links. The policy still carries `{{LEGAL_ENTITY_NAME}}`, `{{REGISTERED_ADDRESS}}`, `{{COMPANY_ID}}`, `{{PRIVACY_CONTACT_EMAIL}}`, `{{FIRESTORE_REGION}}`, `{{WEB_DELETION_URL}}`, `{{DATE}}` and the two AI placeholders, so `web/privacy/` shows the draft banner | `app/build.gradle.kts:78,84,91`; `docs/legal/PRIVACY-POLICY.md` | Owner, A-3, C-1, C-2 |
| F-5 | P2 | **Backup exclusions left out `root` and `external`.** `allowBackup="false"` stops cloud backup, but on Android 12+ device-to-device transfer follows `dataExtractionRules`, which excluded only database, sharedpref and file. Nothing of the app's own is in the two missing domains today (`no_backup/secure_prefs.bin` and `cache` are never transferred by Android), so this was hardening, not a leak | `res/xml/data_extraction_rules.xml` | **Fixed in `1716a399`** |
| F-6 | P0 | **No 512 × 512 store icon.** `docs/play-listing/` had screenshots (16 × 1080 × 1920, 24-bit, no alpha — correct) and feature graphics (1024 × 500 — correct) but no icon; the launcher icon is vector-only | `docs/play-listing/` | **Fixed in `b3aace09`**: `icon-512.png` (32-bit) from `icon-512.svg`, generated from the launcher icon's own paths. Look at it once before upload |
| F-7 | P1 | **The privacy policy and the Data Safety draft said less than the code holds.** The policy's "what you enter" list omitted the parenting plan's answers and change requests (both synced) and never mentioned the on-device journal; the Data Safety draft had no *Other user-generated content* row for that free text, and answered *location: no* without the caveat that Analytics derives a region from the IP on Google's side ⚠ | `docs/legal/PRIVACY-POLICY.md` §"What you enter"; `docs/legal/DATA-SAFETY.md` | **Fixed in `25f4c984`** (`web/privacy/` regenerated). The location row now tells you to check Firebase's own disclosure page |
| F-8 | P0 | **The web deletion page is written but not hosted.** Play's account-deletion requirement needs a URL that works without the app ⚠. The page matches `deleteAccountDataImpl` (checked: documents, professional grants, AI usage, the 30-day thread retention) but carries four placeholders | `web/delete-account/index.html`; `functions/index.js:3997` | Owner, C-1 |
| F-9 | P0 | **Reviewers cannot see the app without a paired demo family.** Every screen that matters is empty for one unpaired account; pairing needs a second account and an invitation code. Play's *App access* declaration must give working credentials ⚠ | `OnboardingStep` starts at `CoParent` (CLAUDE.md item 22) | Owner, D-3 |
| F-10 | P1 | **Chat has no in-app "report" and no named "block".** Play's UGC policy asks apps with one-to-one messaging for in-app blocking of users and reporting of content (verified, §5). *Unpair* ends the thread for new messages (`firestore.rules` requires a live pairing for `messages` create), which is arguably the block; there is no report route at all. A new feature under the freeze, so it is the owner's decision: build a minimal "Report" (mail to the privacy/support address with the message id), or record the argument that the thread is between two identified adults who can unpair | `res/values/*`: no report/block strings; `pairing_unpair_button` | **Done in code** (owner decision, A-5: the minimal version). A long press on a co-parent's message offers *Report message*, which opens the parent's email app to `BuildConfig.SUPPORT_EMAIL` (Gradle property `COPLANLY_SUPPORT_EMAIL`, or `publishedSupportEmail` in `app/build.gradle.kts`) with the message and conversation IDs and the time, never the text (`presentation/chat/ChatReport.kt`, `domain/chat/MessageReport.kt`). The thread's menu offers *Block and stop sharing*, which opens the existing unpair confirmation (unpairing is the block: the rules refuse a message once the pairing has ended). **Still open: the address is blank, which hides Report** — set it once the mailbox of A-3 exists. Device check `DEVICE-CHECKLIST.md` §5.1 |
| F-11 | P1 | **Google Calendar asks for the full `calendar` scope**, a sensitive scope ⚠. Until Google verifies the OAuth consent screen, users see an "unverified app" warning and the project is capped at 100 users ⚠. Verification wants a homepage and the privacy policy on a domain the owner has verified — and the project **owns no domain** (the manifest's deep-link comment says so). For the closed test, add each tester as an OAuth test user | `CredentialManagerService.kt:55`; `AndroidManifest.xml` deep-link comment | Owner, A-7, E-2 |
| F-12 | P1 | **The EduPage row says "Coming soon"** in every build. CLAUDE.md item 35 and the row's own comment say it must become real or go; a reviewer reads it as unfinished. (`AUDIT-2026-10-release.md`'s week-7 record says the school row renders only in debug — true of the old "Planned" row, stale now: the Bakaláři import is real and shows in release, correctly) | `SettingsScreen.kt:873-901`; `settings_account_strings.xml:98` | **Done** (owner decision, A-6: remove): the row and its three strings are gone from every build and all five locales; Bakaláři's row is unchanged. EduPage returns as a row only when its import exists |
| F-13 | P0 | **The server half is not recorded as deployed**: the functions' move to `europe-west3` (the app calls only that region, so while the live functions sit in `us-central1` every callable — pairing, deletion, exports — answers `NOT_FOUND` to a current build), the rules and indexes, and `storage.rules` (every photo, vault and chat-file upload is refused live) | `functions/README.md` "Region", "Admin operations"; CLAUDE.md known issue on `storage.rules` | Owner, B-3 … B-8 |
| F-14 | P0 | **Firestore's location is unknown** and cannot be changed once data exists. An EU location is what the policy and ROPA assume (L-3) | `PRIVACY-POLICY.md` `{{FIRESTORE_REGION}}` | Owner, A-2 |
| F-15 | P0 | **Play Console declarations not made**: content rating, target audience 18+ (not Families), ads *no*, advertising id *no*, Data safety, health apps, financial features *none*, government *no*, EU trader status ⚠ | `DATA-SAFETY.md`; LAUNCH-PLAYBOOK §2.5 | Owner, D-1 … D-4 |
| F-16 | P1 | **Closed test: 12 testers opted in for 14 consecutive days** before a personal account created after 13 November 2023 can apply for production (verified, §5). An organisation account is exempt | LAUNCH-PLAYBOOK §2.6 | Owner, D-7 |
| F-17 | P1 | **Checks only a phone can run**: REL-7 (a release build carries a child's medical profile to the co-parent non-empty), the SEC-2 upgrade over real data, and the Bakaláři import against a live school server — the import ships in release and has never met one | `DEVICE-CHECKLIST.md` §2.1, §3.18, LAUNCH-PLAYBOOK §2.7 | Owner, C-5 |
| F-18 | P1 | **Counsel's sign-off** on the policy, terms and DPIA, and the DPIA signed | `LEGAL-REVIEW-2026-09.md` §3 | Owner, E-1 |
| F-19 | P2 | **The merged manifest was not inspected** (§ intro). Expected from the libraries ⚠: `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (WorkManager — no typed `FOREGROUND_SERVICE_*`, so no foreground-service declaration), `c2dm.permission.RECEIVE` (FCM), `BIND_GET_INSTALL_REFERRER_SERVICE` (Analytics), and the app's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Anything else — above all `READ_MEDIA_*`, `READ_EXTERNAL_STORAGE`, a location or contacts permission, `QUERY_ALL_PACKAGES`, `SCHEDULE_EXACT_ALARM` — is a finding | — | Owner, C-4 |
| F-20 | P2 | A `Log.d` prints the Google account's email on sign-in. Release strips `Log.d/v/i` (`-assumenosideeffects`), and every `Log.w/e` was read: they carry record ids and error codes, never names, emails or message text | `CredentialManagerService.kt:138`; `proguard-rules.pro:77-81` | Accepted |
| F-21 | P2 | Dead code with an SDK behind it: `FeatureManager` (Remote Config, and a `logFeatureUsage` that would send a uid to Analytics) is injected nowhere, so Remote Config never fetches; the budget screens are unreachable (CLAUDE.md UX item 1) | `domain/feature/FeatureManager.kt`; `di/FeatureModule.kt` | Accepted; delete after release |
| F-22 | P2 | Calendar sign-in uses the deprecated `GoogleSignIn` API (`play-services-auth`) ⚠ | `MainActivity.kt:178` | Accepted; migrate to the Authorization API later |
| F-23 | P2 | Play will warn that the bundle has native code without debug symbols (SQLCipher, ML Kit ship stripped `.so`) ⚠. A warning, not a block | — | Accepted |
| F-24 | P2 | `docs/play-listing/README.md` still said the English screenshots were not generated; they were, in `6f538838` | — | **Fixed in `60c7627b`** |

**Checked and right** (no finding): `targetSdk 36`, `compileSdk 36`, `minSdk 26`, AGP 8.10.1
(16 KB zip alignment), the 16 KB CI leg loading SQLCipher and ML Kit; `isMinifyEnabled` and
`isShrinkResources` on, release not debuggable, the R8 mapping check in both CI and
`release.yml`; `AI_ASSIST_ENABLED` false in release and `r8Test`; telemetry off in the manifest
until consent (REL-5); `allowBackup="false"`; the release network-security config forbids
cleartext and the emulator exception lives in `src/debug/` only; exported components are the
launcher activity (whose extras are accepted only through `PushDestination.fromKey`, and whose
pairing link only pre-fills a code), the widget receiver, and nothing else — the QR scanner, the
FCM service and the `FileProvider` are not exported; `CAMERA`, `POST_NOTIFICATIONS` and
`RECORD_AUDIO` are each requested at the moment of use; every image pick is
`PickVisualMedia`/`OpenDocument`, so no media permission and no Photo and Video Permissions
declaration; no location, contacts, exact-alarm, foreground-service-type or package-query
permission; dictation is on-device only and offered only where the phone has an on-device
recognizer; `versionCode` is 100 + the workflow's run number; the language split is off so
per-app languages work from Play; no test hooks, emulator hosts or UI-tour switches in
`src/main`; no `StrictMode`; no user-visible TODO; in-app account deletion is in Settings →
Account.

## 3. What this branch changed

| Commit | What |
| --- | --- |
| `fae4455d` | `fix(manifest)`: remove `ACCESS_ADSERVICES_AD_ID` and `ACCESS_ADSERVICES_ATTRIBUTION` (F-3) |
| `1716a399` | `fix(backup)`: exclude `root` and `external` from backup and device transfer (F-5) |
| `b3aace09` | `docs(play-listing)`: the 512 × 512 store icon and its SVG source (F-6) |
| `60c7627b` | `docs(play-listing)`: the README says the English screenshots exist (F-24) |
| `25f4c984` | `docs(legal)`: policy and Data Safety name the parenting plan, change requests and journal; location caveat; `web/privacy/` regenerated (F-7) |
| this commit | this audit; LAUNCH-PLAYBOOK §2.8, LEGAL-REVIEW §4 and DEVICE-CHECKLIST §0 point here |

The two manifest/resource edits could not be built here. Both are conservative — a
`tools:node="remove"` on a permission, two `<exclude>` lines in a domain list Android documents —
and CI's `build-test`, `static` (lint) and `release` jobs are the proof on the pull request.

### 3.1 The final design pass (UI tour, five variants)

The last UI tour (`dark-en-100`, `light-cs-100`, `light-de-150`, `light-ru-130`,
`light-en-100-wide`; 65–70 shots each, none skipped except the sign-in screens, below) was read
screen by screen. **No P0**: no blank or broken screen, no amount cut off, no light-only colour in
the dark theme, nothing under a floating button that can be acted on. Fixed in `373af408`:

- `PillChip` reserves its 48 dp target outside the clip, so an interactive pill is drawn at chip
  height instead of as a 48 dp blob (Expenses analytics filters, Home's Review).
- The calendar title's chevron keeps its width at 1.3x; a long month used to measure it at zero.
- A borrowed month cell no longer shows pending-swap arrows (design item 10).
- The week custody band grows with its name instead of clipping it at large text.
- Chat bubbles hyphenate (a German compound broke mid-word).
- Onboarding colour swatches: a ring only on the chosen one, 48 dp, announced as radio buttons.
- Copy: sentence case ("Date & time", "Child's name"), an en dash, no-break spaces before the
  stat tiles' arrows and inside "5 expenses" in every locale, no "thousands of parents" claim on
  sign-up, Czech swap-dialog grammar.

**Owner decision, open:** Czech school-vacation names are printed in English to a German or
Russian reader, by the documented rule (local name only when the UI language is the country's).
Either translate the recurring break kinds into all five languages or keep the rule.

**Polish left for after the first release** (none blocks a closed test): Friends and
Professionals order the same invite/redeem job differently; the child and pet forms have no
`StickyActionBar`; Expenses has two horizontal insets; the compact widget ellipsises the handover
at 1.3x; Czech "(a)" gender brackets; role wording ("the other parent") where a name is known;
chevrons on tappable child and plan rows. The tour itself could not reach the sign-in screens
(a main-thread error in the harness) or, on the wide display, Add expense and the bottom of Home —
check those by hand in `DEVICE-CHECKLIST.md` §2.2.

## 4. The owner's checklist, from today to production

**CT** = required before the closed test goes out; **Prod** = required before production only.
Do the steps in order inside each phase; the phases overlap where the text says so. Each step
says where, what, and what it unblocks.

### Phase A — decisions and long poles (start today, in parallel)

| # | Step | Where / how | Unblocks | Needed for |
| --- | --- | --- | --- | --- |
| A-1 | **Developer account.** Decide personal or organisation. Organisation needs a D-U-N-S number (days to weeks) and is exempt from the 12-tester rule. Complete Play's identity verification, and the EU **trader status** (DSA) with a real address and contact ⚠ | play.google.com/console → sign-up; Account details → Developer verification / Trader status | Everything in D | CT |
| A-2 | **Confirm Firestore's location is in the EU** — irreversible once data exists. If it is in the US, stop and decide on a new EU project before any tester arrives (LEGAL-REVIEW L-3) | Firebase console → Firestore → Settings | B, the policy's `{{FIRESTORE_REGION}}` | CT |
| A-3 | **Controller identity and privacy mailbox.** Fill `{{LEGAL_ENTITY_NAME}}`, `{{REGISTERED_ADDRESS}}`, `{{COMPANY_ID}}`, `{{PRIVACY_CONTACT_EMAIL}}`, `{{DATE}}` in `PRIVACY-POLICY.md`, `TERMS-OF-SERVICE.md` and `web/delete-account/index.html`; for the AI placeholders write that writing help is not offered yet. Name who reads the mailbox (30-day clock, Art. 12(3)). Send policy, terms and DPIA to counsel now — it is a long pole | `docs/legal/`; `web/README.md` | C-1; E-1 | CT (placeholders), Prod (sign-off) |
| A-4 | **Recruit six real co-parent pairs** (12 testers) with Google accounts. The mediator channel is the route (LAUNCH-PLAYBOOK §2.6, §4.4) | — | D-6, D-7 | CT |
| A-5 | ~~**Decide chat reporting and blocking (F-10).**~~ **Decided: the minimal in-app report and a named block, built.** Set `COPLANLY_SUPPORT_EMAIL` (or `publishedSupportEmail`) to the A-3 mailbox, or Report stays hidden. ~~Either approve a minimal in-app "Report message" (opens mail to the support address with the message id) as a freeze exception, or record in the Console notes why unpair is the block and the thread is between two identified adults. Decide before the closed-test review, which may raise it~~ | Owner decision; then a code task | D-3, E-3 | Prod (decide before CT) |
| A-6 | ~~**Decide the EduPage row (F-12)**: remove it for release, or ship EduPage~~ **Decided and done**: removed | Owner decision; then a small code task | E-4 | Prod |
| A-7 | **Google OAuth consent screen (F-11).** Set app name, support email, logo, and the `.../auth/calendar` scope. While it is in *Testing*, add every tester's Google account as a test user (limit 100). For verification (Prod) you need a homepage and the policy on a domain you verify in Search Console — buy one, or drop the Calendar connection from the first release ⚠ | Google Cloud console → APIs & Services → OAuth consent screen | Calendar connection for testers; E-2 | CT (test users), Prod (verification) |

### Phase B — Firebase and the server (after A-2)

| # | Step | Where / how | Unblocks | Needed for |
| --- | --- | --- | --- | --- |
| B-1 | **Register `app.coplanly`** in project `coparently-a39c9` (beside the old app), download `google-services.json` into `app/`, and set the Android OAuth client's package to `app.coplanly` with the **debug** SHA-1 (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`) | Firebase console → Add app; Cloud console → Credentials (LAUNCH-PLAYBOOK §2.2) | Local builds, C-3, C-5 | CT |
| B-2 | **Processor terms and console settings**: accept the Google Cloud DPA and Firebase Data Processing Terms (with company details); Analytics retention 2 months, Google signals off, ads personalisation off, data sharing off; Cloud Logging `_Default` 30 days, no longer sink | Cloud console → Settings; Firebase → Project settings → Privacy; Analytics admin | Policy accuracy | CT |
| B-3 | **`functions/.env`**: `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`, `BACKFILL_ADMIN_UIDS` (your uid); optional `CALENDAR_FEED_BASE_URL`. Leave every `AI_*` unset | `functions/.env.example` | B-4 | CT |
| B-4 | **Move the functions to `europe-west3`**: `firebase functions:list`; `firebase functions:delete <name> --region us-central1 --force` for each; then `firebase deploy --only functions`. Afterwards check that `verifyExport` and `calendarFeed` are invokable by `allUsers` | `functions/README.md` "Region", "Verifiable exports" | Pairing, deletion, exports from a current build | CT |
| B-5 | **Backfills, in order**: `backfillParentSlots` if any pair still shares a slot, then `backfillFamilyDocuments`, then `backfillRecordFamilyIds`. Read each summary before the next | `functions/README.md` "Admin operations" | B-6 | CT |
| B-6 | `firebase deploy --only firestore:rules,firestore:indexes` — **only after B-5** | — | Family isolation, AI consent shape | CT |
| B-7 | `firebase deploy --only storage`, then `purgeLegacyPhotoPaths` once, then `backfillRecordFamilyIds` once more | `functions/README.md` "purgeLegacyPhotoPaths" | Every photo, vault document and chat file upload | CT |
| B-8 | `purgeParentHealthFields` once, after the first build carrying L-1 is on testers' phones | `functions/README.md` | Legal L-1 | Prod |

### Phase C — web pages and the build (after A-3 and B-1)

| # | Step | Where / how | Unblocks | Needed for |
| --- | --- | --- | --- | --- |
| C-1 | **Host the four pages.** With A-3's placeholders filled, regenerate (`npx marked docs/legal/PRIVACY-POLICY.md \| node tools/wrap-legal-page.js privacy > web/privacy/index.html`, same for terms), check `grep -o '{{[A-Z_]*}}' web/*/index.html` is empty, then `firebase deploy --only hosting`. Put the deletion URL into `{{WEB_DELETION_URL}}` (policy, `DATA-SAFETY.md`) and regenerate once more | `web/README.md` | C-2, D-3 | CT |
| C-2 | **Build the URLs into the app**: set `publishedPrivacyPolicyUrl`, `publishedTermsUrl` and `publishedExportVerifyUrl` in `app/build.gradle.kts` (public URLs, not secrets) and merge. This turns on the policy link in Settings and on the consent screen, which Play requires in-app ⚠, and the terms clause on the sign-in screen | `app/build.gradle.kts:78-91` | C-3 | CT |
| C-3 | **Upload key and the bundle.** `keytool -genkeypair -v -keystore coplanly-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`; back it up in two places; add five repository secrets — `COPLANLY_UPLOAD_KEYSTORE_BASE64` (`base64 -w0 coplanly-upload.jks`), `COPLANLY_RELEASE_STORE_PASSWORD`, `COPLANLY_RELEASE_KEY_ALIAS`, `COPLANLY_RELEASE_KEY_PASSWORD`, `GOOGLE_SERVICES_JSON_BASE64`; run **Actions → Release bundle**. Its summary must say *signed with the upload key*; keep the `coplanly-release-mapping` artefact with every upload | `.github/workflows/release.yml`; LAUNCH-PLAYBOOK §2.3 | D-5 | CT |
| C-4 | **Check the merged permissions (F-19)**: `java -jar bundletool.jar dump manifest --bundle app-release.aab \| grep -E 'uses-permission\|<service\|<receiver\|exported'`. Expect only the list in F-19 plus `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `CAMERA`, `RECORD_AUDIO`, and no `AD_ID` or `ACCESS_ADSERVICES_*` | bundletool from github.com/google/bundletool/releases | Data safety and permission answers | CT |
| C-5 | **Device session** (`DEVICE-CHECKLIST.md`, §1 builds, then §2 in order): the upgrade over real data (§2.1), first run (§2.2), REL-7 on the release build (LAUNCH-PLAYBOOK §2.7), and the Bakaláři import against a live school (§3.18). The rest of the checklist can run during the closed test | a phone, `adb` | Confidence to invite testers | CT (§2, REL-7), Prod (§3.18 and the rest) |

### Phase D — Play Console (after C-3; D-1 to D-4 can start as soon as A-1 is done)

| # | Step | Where / how | Unblocks | Needed for |
| --- | --- | --- | --- | --- |
| D-1 | **Create the app**: name `CoPlanly`, default language, app (not game), free, category **Parenting** ⚠ (a *Social* category would bring the child-safety-standards publication requirement), contact email | Console → Create app | Everything below | CT |
| D-2 | **Store listing, Czech and English**: short and full descriptions (drafts in LAUNCH-PLAYBOOK §3.3), `docs/play-listing/icon-512.png`, `feature-graphic-{cs,en}.png`, `cs/01-08.png` and `en/01-08.png`. Captions claim only what ships (no AI, no price) | Console → Grow → Store presence → Main store listing | Closed track roll-out | CT |
| D-3 | **App content**, each from its source: **Privacy policy** (C-1 URL); **App access** — "all functionality requires sign-in", with the demo credentials and instructions below; **Ads**: no; **Content rating** (IARC: users interact and share information with each other; no purchases; no violence) ⚠; **Target audience**: 18+ only, not in the Families programme, not appealing to children; **Data safety** from `docs/legal/DATA-SAFETY.md` row by row, with the deletion URL; **Advertising ID**: not used; **Health apps**: declare honestly that it stores a child's health details as records, with no diagnosis or treatment ⚠ — let counsel read the answer; **Financial features**: none; **Government apps**: no ⚠. Answer A-5's UGC position in the reviewer notes if no report button ships | Console → Policy → App content | Any track but internal | CT |
| D-4 | **The reviewer's demo family (F-9).** Create two email/password accounts you control, e.g. `review.parent1@<your domain>` and `review.parent2@…`, with non-expiring passwords and no second factor. Sign in as parent 1, invite parent 2 and accept on a second phone, then seed: a custody schedule, a week of events, a chat thread with a file, two expenses in CZK, one child (give the health consent so the gate is visible), a pending day swap from parent 2. Put parent 1's credentials in *App access* with three lines: the family is already linked; Settings → Account → Delete account shows deletion (**please do not run it on this account**); Google Calendar is optional. Recreate the pair if a reviewer deletes it | two phones or one phone and the second account on an emulator | Review of every track | CT |
| D-5 | **Internal testing first**: upload the AAB, install from the internal link, sign in, pair two test accounts. Then copy the **app signing key** SHA-1 (Console → Test and release → App integrity) into the Firebase app and the Android OAuth client — without it Google sign-in fails only in Play-installed builds | Console → Internal testing | D-6 | CT |
| D-6 | **Closed test**: create the track, add the testers' Google accounts (or a Google Group), countries, release notes; send for review. Testers must opt in from the link | Console → Closed testing | D-7 | CT |
| D-7 | **Run it for 14 days with at least 12 opted-in testers** continuously (a tester who leaves and rejoins restarts their own clock). Fix what they find in normal pull requests; each upload needs a higher `versionCode`, which the workflow gives | — | E-5 | Prod |

### Phase E — production

| # | Step | Where / how | Unblocks | Needed for |
| --- | --- | --- | --- | --- |
| E-1 | Counsel's sign-off on the policy, terms and DPIA; the DPIA signed by the director. Regenerate and redeploy the pages if anything changed | `LEGAL-REVIEW-2026-09.md` §3 | E-5 | Prod |
| E-2 | Calendar scope verified by Google (A-7), or the Calendar connection kept to ≤ 100 users knowingly | Cloud console → OAuth consent screen → Publish, submit for verification | Unwarned Calendar sign-in | Prod |
| E-3 | Chat report/block resolved as decided in A-5 — **built**; the release build must carry the support address | code or Console note | Review | Prod |
| E-4 | ~~EduPage row resolved (A-6)~~ **Done**: the row was removed | code | Review | Prod |
| E-5 | **Apply for production access** (Dashboard), answering Google's questions about the closed test | Console → Dashboard | E-6 | Prod |
| E-6 | **Production release** with a staged rollout (for example 20 %), in the EU countries you chose; watch Android vitals and Crashlytics (the mapping is uploaded by the Crashlytics plugin; keep the artefact anyway) | Console → Production | — | Prod |
| E-7 | After release: B-8 if not yet run; record every operator run's summary in the ops log | — | — | Prod |

### Later, and only after billing exists — the AI assist (MON-12)

Not part of either release: the client flag is off in release until MON-11. When it is time,
`functions/README.md` "Switching it on" is the runbook: enable the Vertex AI API, enable the
Claude model in Model Garden and confirm it is offered in the chosen `europe-…` region, grant the
functions' service account `roles/aiplatform.user`, settle Google's retention and write it into
the policy (`{{AI_PROVIDER_RETENTION}}`) and ROPA P15, set `AI_ENABLED`/`AI_MODEL`/
`AI_VERTEX_REGION`, deploy `aiAssist`, `deleteAccount` and the rules, try one consented account,
and only then build with `-PCOPLANLY_AI_ASSIST_ENABLED=true` — re-reading `DATA-SAFETY.md`'s
"AI writing help" note before the Data safety form is resubmitted.

## 5. Sources

Verified in this session:

- Target API: [Meet Google Play's target API level requirement](https://developer.android.com/google/play/requirements/target-sdk)
  — "Starting August 31, 2026: New apps and app updates must target Android 16 (API level 36) or
  higher", extension to 1 November 2026 on request.
- Closed testing: [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
  (through search results; the page itself is not reachable from this session) — 12 testers opted
  in for the preceding 14 days, personal accounts created after 13 November 2023.
- User-generated content: [User Generated Content](https://support.google.com/googleplay/android-developer/answer/9876937)
  (through search results) — in-app reporting of UGC and users, and in-app blocking for
  one-to-one interaction such as direct messages.
- Photo and video permissions: [Understanding restricted permissions with minimum-scope alternatives](https://support.google.com/googleplay/android-developer/answer/14115180)
  (through search results) — `READ_MEDIA_IMAGES/VIDEO` only where a system picker cannot serve
  the core use. The app uses the picker and declares neither.
- Analytics and the advertising permissions: [firebase/flutterfire#12922](https://github.com/firebase/flutterfire/issues/12922),
  [invertase/react-native-firebase#8176](https://github.com/invertase/react-native-firebase/issues/8176)
  — `firebase-analytics` merges `AD_ID`, `ACCESS_ADSERVICES_AD_ID` and
  `ACCESS_ADSERVICES_ATTRIBUTION`.

From training knowledge (⚠ in the text), to be checked in the Console as each step is reached:
Play App Signing and AAB for new apps; the privacy policy required both in the Console and inside
the app; the account-deletion web URL; the content rating, target audience, advertising-id,
health apps, financial features and government declarations; EU trader status; the category's
effect on child-safety standards; sensitive-scope OAuth verification and the 100-user cap; the
exact permissions each library merges; the missing-debug-symbols warning.
