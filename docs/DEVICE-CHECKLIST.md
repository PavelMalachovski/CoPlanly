# CoPlanly — device checklist

One script for the first session with a real phone. It covers everything the roadmap, CLAUDE.md
and `docs/AUDIT-2026-09.md` record as **written but never run on a device**. Written 2026-09-23
and brought up to date on 2026-09-25 against `main` @ `8ed7cb1` (PR #117 merged), which carries
everything this checklist describes: the integration branch `claude/charming-ritchie-d6uqz8` and
PR #99 are both on `main`, so their old markers are gone.

**How to use it.** Work top to bottom. The order matters: the checks that only work once (an
upgrade over old data, a first launch) come first, and the one that destroys an account comes
last. Tick the boxes as you go. When a check fails, write down what you saw and the logcat
lines, and go on to the next one. Do not fix things during the session.

**Per pull request.** You do not need the whole script for every PR. CI's sticky comment on each
pull request ("CI summary", posted by the `report` job) has a section **"Manual checks this PR
needs"**: `tools/manual-test-plan.js` maps the paths the PR changes to the sections below, with a
link to each. Run those; changed app files it cannot map are listed under it, so decide those by
hand. The same comment links the PR's debug APK — a **UI-only** build, because CI has no
`google-services.json`, so sign-in and sync do not work in it. For any check here that needs an
account, build locally as §1 describes. If you renumber a section, update `RULES` in that script
in the same commit; its test in the CI `invariants` job fails otherwise.

**To look at whole screens without a phone**, use the UI tour rather than this script: touch
`.github/ui-tour-request` on a branch and push, and `.github/workflows/ui-tour.yml` photographs
every main screen of the real app on an emulator — signed in, paired, with a family's worth of
data — in light, dark and Russian at font 1.3, and commits the PNGs and an `index.html` gallery to
the branch `ui-tour/<branch>` (`git fetch` it). It shows what is drawn; it proves nothing this
checklist asks for, and a skipped screen is listed in each variant's `manifest.json`.

**Markers**

| Marker | Means |
| --- | --- |
| **1P** | One phone, one account is enough. |
| **2P** | Needs two phones signed in to two paired accounts at the same time. A **fallback** is given where one phone can cover part of it. There is no emulator: a fallback means signing out and back in on the same phone. |
| **3A** | Needs three accounts: you plus two co-parents. |
| **iPhone** | Needs an iPhone (or a Mac) with Apple Calendar, as well as the Android phone. |
| **[CI]** | The `instrumented`, `e2e` or `upgrade` CI job already exercises the mechanism on an emulator (tables below). The phone still confirms it against real data and real services. |

**Warning: switching accounts wipes the phone's local data.** `AccountSwitchGuard` clears Room
when a *different* uid signs in. Records that already synced come back from the cloud. Records
that never synced, private events included, are gone. For that reason every one-phone fallback
that switches accounts comes after the checks that need local data.

**Logcat, used throughout.** Keep one terminal running this filter:

```bash
adb logcat -v time EncryptedDatabase:V DatabaseKey:V SyncService:V SyncWorker:V ChatMirror:V \
  MessageRepo:V ChatViewModel:V CoPlanlyMessaging:V PetsViewModel:V ChildInfoViewModel:V \
  CoPlanlyUpload:V CoPlanlyReceiptScan:V AccountDeletionService:V SelectedFamily:V \
  CustodyModelRepo:V HomeViewModel:V UserRepository:V HealthConsentManager:V \
  HealthConsentViewModel:V DepartedThreadSource:V TermsOfServiceLink:V AndroidRuntime:E *:S
```

`AndroidRuntime:E` is where a crash shows up, including a failed Room migration
(`IllegalStateException: A migration from N to M was required but not found` or
`Migration didn't properly handle`). A release build strips `Log.d/v/i` but keeps `Log.w/e`.

**Look at the screenshots before the phone.** Every pull request that touches Android uploads a
`screenshots` artefact from CI (the `screenshots` job: Roborazzi on Robolectric, no emulator).
Unzip it and open `index.html`; it filters by component, language, theme, font scale and palette.
It renders Home's cards (handover hero, today card with a contact window, week timeline, stat
tiles), the month grid with every layer, the calendar banners, a Settings group, an empty state,
the Expenses summary header, a chat thread, the event preview, the consent screen and the family
switcher chip — in light and dark, in all five languages, at 1.0× and 1.5× text, and in the
default and a purple/orange palette (`ScreenshotVariants` says which combinations). What those
images settle is marked **(screenshots)** below: the static look of a component. They cannot show
anything a device adds — the window before Compose's first frame, insets, the keyboard, motion,
TalkBack, AppCompat's per-app locale switching, or a screen assembled from real data — so those
checks stay. A check marked **(screenshots)** only needs a glance on the phone, or none.

**What CI now covers.** The `instrumented` job (API 26/30/35 emulators, debug build, Firebase mocked by
`FakeFirebaseModule`, Room real) runs these on every Android pull request, the `r8-runtime` job
(the minified `r8Test` build, API 30) runs the `R8GsonProbe` row, and the `web` job (no Android
at all: Chromium and Node against the Functions emulator) runs the `web-tests/` row. A check they cover is
marked **[CI]** below: CI saw the mechanism work, so on the phone it is a quick confirmation, and
a failure there points at something the emulator does not have — real Firebase, real data, a
Play install, a vendor skin.

| Test (`app/src/androidTest/...`) | Covers | What only the phone still adds |
| --- | --- | --- |
| `presentation/common/PickerDatesTest`, `LocalDatePickerDialogTest` | §3.1: every picker's conversion and both picker composables, tapped, in Prague, Kiritimati (+14), Los Angeles and Pago Pago (−11) | Each *screen* saving what its picker returned, and a stored date staying put across a zone change |
| `presentation/settings/PerAppLocaleTest` | §3.6: `setApplicationLocales` to cs/de/ru/uk renders Settings in that language | The Settings row itself, Android 13's system setting, and §4.2 (a Play install) — never CI |
| `data/export/ExportFileWriterTest` | §6: a CSV (RFC 4180, statement first, formula guard, both clocks, no private event) and a PDF that `PdfRenderer` opens, written through the real writer; the share intent's `FileProvider` URI and read-only grant | Real revisions from the server, the share sheet, and a spreadsheet or PDF app opening the file |
| `data/remote/firebase/PushNotificationTest` | §3.7 and every push's wording: real data payloads through `PushNotifier` (what `CoPlanlyMessagingService` hands each message to), read back from `NotificationManager.activeNotifications` — every worded type in English and German, composed in all five languages with its names shown; an unknown type (even with a `title`/`body`) and a push for another account post nothing; each tap's PendingIntent matched to its deep link, `familyId` extra and request code, two families kept apart; every type posted to its channel and naming its screen (D-13). Runs on all three legs, 16 KB included (not a Hilt test) | FCM delivering it, the shade as the phone's skin draws it, a tap actually switching family (§5.2), and the language on Android 12 or older when the app language differs from the phone's (a push follows the phone's there) |
| `presentation/navigation/MainNavigationSmokeTest` | A signed-in launch visiting Home, Calendar (month/week/day), Chat, Expenses and Settings without a crash; bottom bar on the tabs only; icon-only controls named and ≥ 48 dp | Everything that needs data, a co-parent or a server; TalkBack itself (§3.9) |
| `upgrade/UpgradeSeedTest` → `adb install -r` → `upgrade/UpgradeVerifyTest` (the **`upgrade` job**, API 30, not `instrumented`) | §2.1 and §3.9's SEC-5 box for **one release step**: the base build (the PR's base commit, or the previous `main`) writes a family's rows into eight tables through its own SQLCipher open path, plus a refresh token, settings and the telemetry answer into the sealed store; this build is installed over it keeping the data, and must open the database with the **recovered** passphrase (the wrapped value unchanged), run every migration to the newest exported schema, read every row back (six tables also through its own DAOs), leave the file ciphertext, and keep the preferences and the consent answer | An upgrade from a build older than the base (a longer migration chain), the plaintext → encrypted conversion of an install that predates SEC-2, a hardware-backed Keystore, a reboot between launches, and a real family's volume of data |
| `app/src/r8Test/.../R8GsonProbe` — not an `androidTest`: the **`r8-runtime`** job runs it inside the *minified* `r8Test` build (`release` plus the probe) on API 30 | §4.1's Gson half: a child's medical profile (blood type, intolerances, hereditary conditions, a dated vaccination), medications, activities, emergency contacts and school, and a pet, written through the real repositories into Room with the source key names and read back equal; custody swaps, the event draft, the chat and revision `TypeToken`s, and the Google Calendar `@Key` models parsed | The Firestore document itself (the probe never signs in), the co-parent's phone reading it, a real Google Calendar import, and the telemetry check — a signed release build with a real `google-services.json` |
| `web-tests/verify-page.spec.js`, `calendar-feed.spec.js` — not an `androidTest`: the **`web`** job runs them with Playwright and Node against the Auth, Firestore and Functions emulators | §6's **verification page** in Chromium: receipts reserved and registered through the real callables, then the exported file (a match, with registered time, period, format and size), a copy with one byte changed (no match), the file against another record ID, the ID typed as people retype it, an unknown ID and a mere reservation (not found), a rate limit, a hostile server value shown as text; English and Czech, following the browser and switched by hand; 375 px wide with nothing scrolling sideways; only the fingerprint leaves the browser, and no uid, family id, e-mail or name is on the page or in the answer. The **calendar feed** (§3.11 and §3.13's feed boxes) through Mozilla's `ical.js` plus an octet-level RFC 5545 check — CRLF, 75-octet folding never inside a UTF-8 character, TEXT escaping, UID/DTSTAMP/DTSTART on every event, DTEND after DTSTART, UNTIL matching a floating DTSTART, unique and stable UIDs, every title round-tripping — for `buildFeed` in all five languages and for the real `calendarFeed` endpoint behind a link `createCalendarFeed` minted | The page **hosted** over https at its real address, and `EXPORT_VERIFY_URL` printed on a file; a file that travelled (e-mail, Drive, a USB stick) checked on a real computer in Safari or Firefox; a real calendar app (Apple Calendar on an iPhone, Google Calendar) subscribing to the link, refreshing, and drawing it |

**What the `e2e` job covers between two parents.** Two accounts in one emulator, each with the
production data layer, against the Auth, Firestore, Functions and Storage emulators and the real
`firestore.rules`/`storage.rules` (CLAUDE.md, "The `e2e` job"). Every shared collection, storage
path, push type and callable is either named by one of these tests or exempted with a reason in
`tools/e2e/coverage.json`, and CI fails when a new one is neither — so a two-phone feature arrives
here already exercised. A push is proved as far as the queue: the `notification_queue` document
addressed to the right parent with the right type and family. Its **delivery** is always the
phone's. A check below marked **[CI e2e]** is one of these, and the PR comment's manual plan says
so.

| Test (`app/src/androidTest/.../e2e/`) | Covers between the two parents | What only phones still add |
| --- | --- | --- |
| `TwoParentPairingTest` | Pairing on both phones, both slots, one conversation, `pairing_accepted` queued | The QR scan, the pairing screens |
| `TwoParentEventsTest`, `MultiFamilyTest` | Events through the sync's own query, a private event never on the server, tombstones; a second family's audience (§5.2) | The grid as drawn |
| `TwoParentChatTest`, `OneParentOnScreenTest` | Chat across the date line to unread, DELIVERED, READ; one parent's real screens (§5.1) | Two screens at once, displayed times, the push |
| `TwoParentAttachmentsTest` | Chat attachments and the vault, bytes and digests, a stranger refused (§5.5) | A viewer app opening the file |
| `TwoParentExpensesTest`, `TwoParentAgreementsTest` | A shared expense on the other parent's balance; the split ratio agreed, proposed, accepted, declined, withdrawn; the parenting plan's agreement lapsing on a reword, the other half unwritable | The Expenses and plan screens, the banners |
| `TwoParentFamilyRecordsTest` | Children, pets and budgets both ways with tombstones; records made before pairing shared and announced once | The forms |
| `TwoParentRecordPhotosTest` | Medical, pet, receipt and event photos (L-4): uploaded under the family's path, the co-parent's download matching its digest, a stranger refused, either parent deleting, no overwrite, the old flat paths closed; a photo taken before pairing moved into the family by `onFamilyCreated` and still opening for the co-parent after the uploader's phone writes its stale reference back (§3.10) | The thumbnails as drawn, a guest's screen without them, and the live bucket before `firebase deploy --only storage` |
| `TwoParentRequestsAndEventPushesTest` | Change requests accepted, declined, cancelled; `event_created`; event revisions immutable, none for a private event | The request screens |
| `TwoParentCustodyTest` | A pattern proposed, accepted, declined in two zones; single-day and group swaps; a self-accepted swap refused | The grid's band, markers and banners |
| `OnScreenAgreementsTest` | A's **real app** answering B on screen: B's change request raises the calendar's inline banner, A opens the inbox from it and accepts, B holds it accepted and the event moved; B's custody proposal pops up on A's Home naming B, accepted then (a second one) declined; B's day swaps pop up on Home, one accepted, one declined; a seasonal layer A accepts makes today's month cell say "With B" (§3.11) | Two screens at once, the push that makes A look, the band's colours and motion, the plan citation line (§3.12), a child's own band (§3.13) |
| `OnScreenFamiliesTest` | A paired with B and C, B's family on screen: C's message puts the dot on Home's switcher chip, named "New messages in another family"; C's row in the dialog says so; switching there brings C's thread onto the Chat tab, and A's reply reaches C (§5.2) | The push from the other family switching on tap, the chip on Expenses, TalkBack, change-request and schedule dots |
| `TwoParentAccessTest` | A calendar friend, a guest and a professional (two consents, never the chat, §5.4) redeemed and revoked; unpair on both phones with `pairing_removed`; a calendar feed serving shared events only until revoked; an export hash registered once and verified without an account; receipt photos; account deletion unpairing the co-parent (§7) | The invitation screens, a calendar app subscribing to the feed, the verification page |

---

## 0. Before the session: owner ops

> These steps are also in the release order of `docs/AUDIT-2026-09-play-final.md` §4 (phase B
> and C-5), which says which of them the closed test needs.

Without these, several checks below fail for reasons that have nothing to do with the app. Do
them the evening before, from the commit you will build the app from.

- [ ] **REL-1: `google-services.json` for `app.coplanly`.** Without it **a local build fails**
      ("No matching client found for package name 'app.coplanly'"). If the file is missing
      entirely, the build succeeds but the app crashes at launch in `FirebaseModule`.
  - [ ] Firebase console → project `coparently-a39c9` → Add app → Android → `app.coplanly`.
  - [ ] Download the new `google-services.json` into `app/`.
  - [ ] Google Cloud console → Credentials → the Android OAuth client: package `app.coplanly`,
        plus the **debug** SHA-1:
        `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`.
        If this is missing, Google sign-in fails with what looks like a generic error.
- [ ] **REL-3: the multi-family ops sequence, in this order** (`functions/README.md` → "Admin
      operations"):
  1. [ ] `firebase deploy --only functions`. Deploy from current `main` (it contains `6e4ec8e`,
         without which account deletion does not remove Storage files, §7).
  2. [ ] Invoke `backfillFamilyDocuments`.
  3. [ ] Invoke `backfillRecordFamilyIds`.
  4. [ ] `firebase deploy --only firestore:rules,firestore:indexes`.

  If you run step 4 before step 3, each co-parent's expenses look empty on the other phone
  until step 3 has run.
- [ ] **REL-3 storage:** `firebase deploy --only storage`. Until this runs, **every photo upload
      is refused**, because the bucket still enforces the July rules, which know neither the
      family-keyed photo paths of L-4 nor `pet_photos/`/`medical_photos/` at all. That is a known
      failure, not a finding (§3.10). The same deploy is what lets the document vault and chat
      attachments upload at all (§5.5): they live under `family_documents/` and
      `chat_attachments/`, which the July rules refuse. Then run `purgeLegacyPhotoPaths` once
      (L-4; `functions/README.md`).
- [ ] **REL-3 accounts:** set `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET` in
      `functions/.env` before the functions deploy. Without them, connecting Google Calendar
      cannot work.
- [ ] **Accounts:** at least two test accounts (A = you, B = co-parent). For the M-8 checks,
      add a third (C = a second co-parent). Also create one throwaway account for the deletion
      check in §7.
- [ ] **Tools on the PC:** `adb` (platform-tools); JDK 17+; `bundletool.jar` (from
      github.com/google/bundletool/releases, only for §4.2). On the phone: Developer options →
      USB debugging on.

---

## 1. Builds to prepare the evening before

All commands run from Git Bash in the repository root. Keep the resulting files side by side.

| # | Build | From | Command | File |
| --- | --- | --- | --- | --- |
| **A** | Last build **before SQLCipher** (plaintext DB, schema v33) | `f6bab3e` (PR #89 merge, the parent of SEC-2's `2dcc2ae`) | see below | `app-debug-OLD-v33.apk` |
| **A′** *(optional, stronger)* | Oldest build with the `app.coplanly` id (plaintext DB, schema **v24**) | `02be524` (REL-1) | same as A | `app-debug-OLD-v24.apk` |
| **B** | Current debug | `main` | `./gradlew clean assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| **C** | Current **release**, signed with the debug key | same as B | see below | `app/build/outputs/apk/release/app-release.apk` |
| **D** | Current release **AAB** | same as B | see below | `app/build/outputs/bundle/release/app-release.aab` |

Why not older than `02be524`: before it, the applicationId was `com.coparently.app`, which
Android treats as a different app, so there is no upgrade path to test from it.

**A / A′: the old build, in its own worktree** so the main checkout is untouched:

```bash
git worktree add ../coplanly-old f6bab3e          # or 02be524 for A′
cp app/google-services.json ../coplanly-old/app/
(cd ../coplanly-old && ./gradlew assembleDebug)
cp ../coplanly-old/app/build/outputs/apk/debug/app-debug.apk ./app-debug-OLD-v33.apk
git worktree remove ../coplanly-old --force
```

It must be built **on the same PC** as B. Both builds are then signed with the same
`~/.android/debug.keystore`, and that is what lets `adb install -r` upgrade A to B in place.
All builds share `versionCode` 2, which Android accepts as an in-place update. If A′ does not
build under today's toolchain, skip it: A is the required run.

**C / D: release, signed with the debug key.** REL-2's upload keystore may not exist yet. The
debug key's SHA-1 is already registered (§0), so Google sign-in works on a release build signed
with it. The signing config reads four properties:

```bash
KS="$HOME/.android/debug.keystore"
./gradlew assembleRelease bundleRelease \
  -PCOPLANLY_RELEASE_STORE_FILE="$KS" -PCOPLANLY_RELEASE_STORE_PASSWORD=android \
  -PCOPLANLY_RELEASE_KEY_ALIAS=androiddebugkey -PCOPLANLY_RELEASE_KEY_PASSWORD=android
node tools/check-r8-mapping.js   # the same check CI runs; it must pass before C is worth installing
```

If the upload keystore from REL-2 already exists, use it instead, and register its SHA-1 as well.

---

## 2. First-launch and one-shot checks (in this order)

### 2.1 SEC-2 + Room migrations: upgrade over real plaintext data · 1P · **do this first**

These checks can be run only once per install. Room migrations 24→33 have never run on a device
either, and 33→36 have run only in CI.

**What CI now covers, and what it does not** (September 2026). `EncryptedDatabaseTest` runs in
the `instrumented` job on API 26, 30 and 35 with 16 KB pages, and converts a plaintext database
on every run. And the **`upgrade` job** ("Android — upgrade over main") does this section's
install-over for one release step on every pull request that reaches the database or the
preference store: it installs the **base build** (the PR's base commit, or the previous `main`),
has it write rows into events, a private event, expenses, a child, a pet, a message, a custody
model, the journal and the user through its own code — plus a Google refresh token, settings and
the telemetry answer into the sealed store — then `adb install -r`s this build over it and checks
from inside it that everything opens and is still there. Since `main` already ships SQLCipher,
what that job exercises is **encrypted → encrypted with the migrations between the two schema
versions**, not this section's plaintext conversion. The boxes below are marked **[CI]** where an
emulator already proves the same thing, so a failure there on a phone points at something the
emulator does not have. What CI does not cover is the reason this section still comes first:

- the plaintext file `EncryptedDatabaseTest` converts was written by the **current** Room schema,
  and the `upgrade` job's base build already encrypts — nothing in CI takes a file an older,
  pre-SEC-2 build wrote through the migration chain in the same launch as the conversion, and
  nothing upgrades across more than one release step;
- the emulator's Keystore is **software-backed**, not a phone's hardware (TEE/StrongBox) one;
- nothing reboots between two launches;
- the data is three rows in one table, not a family's real calendar, chat and medical profile.

Preconditions: a factory-fresh phone, or `adb uninstall app.coplanly`. Build A (or A′).

- [ ] Install the old build: `adb install app-debug-OLD-v33.apk`.
- [ ] Sign in as **A**. Go through onboarding and enter real-looking data:
  - [ ] a custody pattern;
  - [ ] 3+ events, including a **recurring** one and a **private** one;
  - [ ] a child with a medical profile (blood type, allergies, a vaccination);
  - [ ] a pet;
  - [ ] two expenses in two currencies;
  - [ ] if B is paired on another phone, a few chat messages both ways.

  Wait for one sync. Sync errors from the old build against the new rules are irrelevant here:
  what matters is the local rows.
- [ ] Confirm the file is **plaintext**:
      `adb exec-out run-as app.coplanly head -c 16 databases/coparently_database | od -c | head -1`
      prints `S Q L i t e   f o r m a t   3 \0`.
- [ ] Note the counts on screen: events this month, children, pets, expenses, messages.
- [ ] Install over it **without uninstalling**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] Launch.
  - **Expected:** no crash. Every record from the old build is still there: the same counts,
    the private event, the medical profile, and the chat history. **[CI]** for one release step
    (the `upgrade` job: the base build's rows in eight tables, read back by SQL and through the
    new build's DAOs after its migrations); the older build and the real data are the phone's.
  - **If it fails:** a crash in `AndroidRuntime` naming a migration points to
    `DatabaseMigrations.kt`. `EncryptedDatabase: Could not open the database encrypted` means
    it fell back to plaintext and will retry next launch. That is safe, but it is a finding.
    `DatabaseKey: ... could not be unwrapped` is serious. The code is in
    `data/local/security/EncryptedDatabase.kt`, `SqlCipherMigration.kt` and `DatabaseKey.kt`.
- [ ] The file is now **ciphertext**: the same `head -c 16 | od -c` command prints random
      bytes, not `SQLite format 3`. **[CI]** for a current-schema file: the header check, plus
      the platform's SQLite refusing to read it.
- [ ] No leftover files: `adb shell run-as app.coplanly ls -la databases/` shows no
      `coparently_database.migrating*`, and `shared_prefs/` contains the `database_key` store.
      **[CI]**, including a killed conversion's leftovers (a stale export beside the original,
      an export whose rename never happened, a leftover beside an encrypted file).
- [ ] Run `adb shell am force-stop app.coplanly`, then relaunch twice. Everything still opens:
      the passphrase is **recovered** each time, never re-minted. **[CI]** within one process
      (recovered twice, by a second `DatabaseKey`, and on disk as soon as `mint` returns), and
      across a process death *and* an app replacement in the `upgrade` job (the wrapped value the
      base build stored is unchanged after the new build opened the database twice); the reboot
      below is the part left to the phone.
- [ ] **Reboot the phone**, then relaunch. It still opens: the Keystore key survives a reboot.
      *Not in CI* — phone only.
- [ ] Migrated rows get the new defaults:
  - [ ] Settings → Country shows **Czechia** (schema 33 default) and no state;
  - [ ] the custody pattern has **no** contact windows;
  - [ ] the parenting plan is empty but opens.
- [ ] **If this fails in any way:** `docs/legal/PRIVACY-POLICY.md` says the database is
      encrypted. That sentence comes out in the same commit as the fix (ROADMAP SEC-2).
- [ ] *(optional)* Repeat the whole section with **A′** (`02be524`, schema v24) to run
      migrations 24→36 on a device. Run `adb uninstall app.coplanly` first.

### 2.2 Fresh install: first run · 1P

Preconditions: `adb uninstall app.coplanly`, set the system to **dark** theme
(`adb shell cmd uimode night yes`), and install B. Android 13+ phone for the notification checks.

- [ ] **UX-13, dark cold start.** Record the screen:
      `adb shell screenrecord /sdcard/cold.mp4`, then in another terminal
      `adb shell monkey -p app.coplanly -c android.intent.category.LAUNCHER 1`. Stop after
      5 seconds and run `adb pull /sdcard/cold.mp4`. Step through frames.
  - **Expected:** no white frame between the launcher and the splash. The window is the dark
    background, then the brand-purple splash appears.
  - Repeat with `am force-stop` plus launch, three times.
  - Then set the **system** theme to light and the **in-app** theme to dark. A light window
    before the first frame is **expected**: the window follows the system theme, because the
    in-app choice is not known before Compose draws. Note it, but it is not a failure.
  - **If it fails:** look at `res/values/themes.xml` and `res/values-night/colors.xml`
    (`window_background`).
  - **(screenshots)** The *rest* of UX-13 — whether the light theme renders correctly once
    Compose draws — is in the artefact's `light` images; this device check is only about the
    window before the first frame.
- [ ] **REL-5, telemetry consent screen**, shown before sign-in on a fresh install. Its layout,
      in every language and at 1.5× text, is in the screenshots (`consent_screen`); on the phone
      check that it appears, and when.
  - **Expected:** the decline button comes first. It is outlined, **enabled**, and reads as a
    real choice rather than a disabled control. The default is off.
  - Decline. Later, Settings → App → **Usage statistics** shows the switch off.
  - In a debug build, collection stays off even if you grant consent (`ENABLE_ANALYTICS=false`),
    so the end-to-end check is in §4.1.
- [ ] **Privacy policy link hidden while the URL is blank.**
  - **Expected:** no "Privacy policy" link on the consent screen, and no such row in
    Settings → Account. `PRIVACY_POLICY_URL` is blank until REL-4 hosts the policy.
  - *(optional)* Build once with `-PCOPLANLY_PRIVACY_POLICY_URL=https://example.com`: both
    links appear and open the browser.
  - **If it fails:** `presentation/common/PrivacyPolicyLink.kt`, log tag `PrivacyPolicyLink`.
- [ ] **Notification permission is never requested on cold start.**
  - **Expected:** sign in and finish onboarding, and no system notification dialog appears.
  - **Expected:** the dialog appears the first time you (a) turn on Settings → App →
    Push notifications, or (b) pick a reminder chip in the event form.
  - To re-test, run `adb shell pm revoke app.coplanly android.permission.POST_NOTIFICATIONS`,
    then `adb shell pm clear-permission-flags app.coplanly android.permission.POST_NOTIFICATIONS user-set user-fixed`.
  - **If it fails:** `presentation/common/NotificationPermission.kt`, `SettingsScreen.kt`,
    `AddEditEventScreen.kt`.
- [ ] **Onboarding, co-parent first** (CLAUDE.md item 22). The wizard opens on the co-parent
      step. "Next" and "Not now" behave the same, which is a known open item (AUDIT §4.2).

### 2.3 What signing in agrees to (L-12) · 1P

Preconditions: signed out (or the fresh install of §2.2, before signing in). Build B has
`PRIVACY_POLICY_URL` and `TERMS_URL` blank until REL-4 hosts the documents.

- [ ] **The age line is always there.** On the sign-in screen, in both modes (sign in and create
      an account), a line under the form says CoPlanly is for parents and guardians aged 18 or
      over and that continuing confirms it. It names **no** terms of service while `TERMS_URL` is
      blank, and no link sits under it.
- [ ] **The terms clause only comes with its link.** Build once with
      `-PCOPLANLY_TERMS_URL=https://example.com/terms -PCOPLANLY_PRIVACY_POLICY_URL=https://example.com/privacy`.
      The line now also says continuing accepts the terms of service, and two text buttons sit
      under it, **Terms of service** and **Privacy policy**; each opens the browser at its
      address. With only one URL set, only that button appears, and the terms clause only with
      the terms URL.
- [ ] **Settings follows the same URLs.** In that build, Settings → Account shows a **Privacy
      policy** row and a **Terms of service** row, each opening its address. In build B neither
      row exists.
- [ ] Check the line in German and Russian at 1.5× text: it wraps, nothing is cut off.
- **If it fails:** `presentation/auth/AuthLegalNotice.kt`, `presentation/common/TermsOfServiceLink.kt`
  and `PrivacyPolicyLink.kt` (tags `TermsOfServiceLink`, `PrivacyPolicyLink`), the
  `publishedTermsUrl`/`publishedPrivacyPolicyUrl` values in `app/build.gradle.kts`.

---

## 3. Everyday checks: one phone

Signed in as A, with some data (the §2.2 install). Unless a check says otherwise, keep the system
language as it is.

### 3.1 Date pickers: the off-by-one fix · 1P [CI]

**[CI]** Every picker below now opens one of two composables (`LocalDatePickerDialog`, or the
child/pet `DatePickerDialog` that wraps it) and converts through `PickerDates`; CI taps both in a
UTC+1, +14, −8 and −11 zone and checks the highlighted and the returned day. What is left here is
each screen's own save and the stored date surviving a zone change, so one UTC+ and one UTC−
pass over the list is enough.

Material3 date pickers work in UTC-midnight millis. Before the fix, east of Greenwich the picker
highlighted the previous day, and west of it the app saved the previous day. Test in **one UTC+
zone and one UTC− zone**.

Preconditions: Settings → System → Date & time → automatic time zone **off**. Choose e.g.
**Tokyo (UTC+9)**. Then run `adb shell am force-stop app.coplanly` so the app starts in the new
zone.

For each picker, choose **the 15th of next month**. Check three things: the field shows the
15th; reopening the picker highlights the 15th; and after saving, the record shows the 15th in
its list or calendar.

- [ ] Event form: **start date**.
- [ ] Event form: **recurrence end date**.
- [ ] Change request: open an event **the co-parent created** → Request change → new date
      (`RequestChangeScreen.kt`). This needs B's event: do it on the paired phone, or come back
      to it after §5.
- [ ] Child: **date of birth** (the shared `childinfo/components/DatePickerDialog.kt`).
- [ ] Child → medical → **vaccination date** (`VaccinationListEditor.kt`).
- [ ] Pet: **date of birth**.
- [ ] Profile and onboarding date of birth (the same dialog).
- [ ] Expense date and custody start date (these were already right, so this is a regression
      check).

Then switch the zone to **New York (UTC−4)** or **Honolulu (UTC−10)**, force-stop, and:

- [ ] Reopen every record above. Each **still shows the 15th**: a stored date does not move
      when the zone changes.
- [ ] Repeat the pick-and-save on two of the pickers in this zone.

**If it fails:** look for `ZoneOffset.UTC` versus `systemDefault()` in the named file. Set the
zone back to automatic afterwards.

### 3.2 Motion · 1P

- [ ] **Tabs fade through** (Home ↔ Calendar ↔ Chat ↔ Expenses): a short fade and a slight scale,
      about 300 ms, with **no horizontal slide**.
- [ ] **Detail screens push:** the gear, an event and Friends slide in from the right, and Back
      slides out. Friends, Friend detail and Friend profile no longer use a slow 700 ms
      crossfade.
- [ ] Opening a tab from a detail screen, or returning to one, still slides.
- [ ] Month paging settles the same way in both swipe directions (a 500 ms tween).
- [ ] **Reduced motion.** Turn on Accessibility → Remove animations, or set Developer options →
      Animator duration scale to *off*, then cold-start the app.
  - Tweens become instant.
  - The Auth logo does **not** pulse, and a list waiting on the network shows still grey
    placeholders rather than a sweeping shimmer (`rememberReducedMotion` in
    `presentation/theme/Motion.kt`).
- [ ] **One splash** (October 2026 audit, D-25). Force-stop the app and launch it from the icon,
      signed in, three times.
  - The system splash (the icon on the brand indigo) goes straight to Home. No second screen with
    the "CoPlanly" wordmark and a tagline follows it, and the "Loading…" screen does not flash
    between them.
  - The splash never stays for more than about 1.5 s; on a slow first start the loading screen
    shows after that instead.
- [ ] **Forms push:** "+" on the calendar, an expense, a child and a pet form slide in from the
      right like any detail screen — none of them zooms in from the middle. The event form's
      "whose day" cards grow slightly when chosen, without a bounce.
- [ ] **Predictive back over edits** (Android 14+, gesture navigation). Open an expense, change the
      amount, and start a back swipe without letting go: the form shrinks a little under the
      finger. Let go early and nothing happens; finish the swipe and "Discard changes?" appears.
      With nothing changed, the same swipe goes straight back.
- [ ] **A rail on a wide window.** Turn the phone on its side, or open the app on a tablet or an
      unfolded foldable: Home, Calendar, Chat and Expenses sit in a rail down the left edge,
      with Chat's unread badge, instead of a bar along the bottom. A detail screen hides it as
      it hides the bar. Upright on a phone, the bar is back.
- [ ] **One column on a wide window** (week 6). On a tablet, or with the phone on its side, open
      Settings, an expense and the event form. Each is a column about 640 dp wide in the middle,
      its top bar included, with the window's background either side. Home, Calendar, Chat and
      Expenses still use the whole width beside the rail. Upright on a phone nothing changes.
- [ ] **Rotation keeps a child's or a pet's form.** Edit a child: type a name, add a medication and
      an allergy, then rotate the phone. Everything typed is still there, and Back still asks
      before dropping it. The same for a pet's breed and vaccination.
- [ ] Not a pass/fail check: write down anything in AUDIT §3.3 points 1–7 that still looks wrong
      on the phone. Of those points, forms scaling from 0.8 and the one bouncy spring are fixed;
      the three expand/collapse styles, the calendar's four ways of moving, the Settings chevron,
      the banners without enter/exit and the bottom sheets are what is left to look at.

### 3.3 Parent colour reaches every screen (UX-15) · 1P, full check 2P

Settings → Family → **My colour** → choose a **non-default** colour (purple or orange).

**(screenshots)** The `purpleorange` images already show the month grid (wash, dots, handover
triangle, contact-window corner), Home's today card, week timeline and handover hero, the event
preview, the Expenses split bar and the family switcher. A pink or blue in any of them is the
bug, found without a phone. Spend the device time on the surfaces the screenshots do not render:
Day/Week, the event form, Filters, Custody setup and the onboarding picker.

- [ ] Every surface below shows the new colour, not pink or blue:
  - [ ] Month grid: the custody wash (about 14% alpha), event dots, handover triangle.
  - [ ] Day/Week: hour-cell wash, event blocks (fill, border, accent), and the **custody band**,
        whose label must be readable. The band uses the deep tone, with black or white text
        chosen by contrast.
  - [ ] Event form owner cards, and the event preview sheet.
  - [ ] Calendar → Filters: the parent labels.
  - [ ] Expenses: the split bar in the summary header.
  - [ ] Custody setup: the pattern grid, the 14-day preview, the legend.
  - [ ] Home: the today card and the week card.
  - [ ] The onboarding profile step's picker, if you pass through it.
- [ ] Known and not a failure: the white day numbers on Custody setup's 14-day preview sit on
      the hue at 70% and may be hard to read. Write down how it looks.
- [ ] **2P:** B picks a *different* non-default colour. Each phone then shows both parents'
      chosen colours. If both picked the same colour, one of them is resolved to its partner
      colour (`ParentPalette.of`).
  - **Fallback (1P, after §2 is done):** sign in as B on the same phone, pick a colour, sign
    back in as A. A's screens now show B's choice for B.
- **If it fails:** a raw `CoPlanlyColors.MomPink`/`DadBlue` in that screen is the bug
  (CLAUDE.md design item 12). `theme/ParentColors.kt`.

### 3.4 Holidays by country and Land (MON-13) · 1P

Settings → Family → **Country** (and **State** when it appears). Open the calendar at
October–November 2026 and check the dates below.

- [ ] **Czechia:** 28 Sep, 28 Oct, 17 Nov drawn. The coverage note mentions school vacations.
      **29–30 Oct 2026** carry the school-vacation line (below).
- [ ] **Slovakia:** 1 Nov drawn. **15 Sep 2026 and 17 Nov 2026 not drawn** (both are working
      days by law in 2026). The note says school vacations are shown and the spring holidays need
      a region. **Day view on 29 Oct 2026** is labelled "Jesenné prázdniny" (app in Slovak or
      matching language) or "Autumn vacation"; **Day view on 17 Feb 2027** has no label (no kraj
      chosen, so no spring week).
  - A **Region (kraj)** row appears under Country (and region chips in the onboarding profile
    step), listing the eight kraje by their Slovak names after "Nationwide only".
  - **Banskobystrický kraj**: the note now says its spring holidays are shown; **17 Feb 2027**
    is labelled "Jarné prázdniny"/"Spring vacation" and 15–19 Feb 2027 carry the school-vacation
    line; **24 Feb 2027** has no label. **Košický kraj**: 22–26 Feb 2027 instead.
  - Change the country to Germany with a kraj chosen: the row turns into **State**, shows
    "Nationwide only", and no Slovak spring week stays on the grid.
- [ ] **Germany**, no state: 3 Oct drawn, and the note asks you to pick a state to add its
      school vacations and public holidays. **Day view on 2 Nov 2026 has no label.**
  - **Bavaria** adds 1 Nov and 6 Jan 2027, and the note now mentions school vacations.
    **Day view on 2–6 Nov 2026** is labelled "Herbstferien"/"Autumn vacation", and on
    **18 Nov 2026** "Buß- und Bettag" as a school-free day.
  - **Saxony** adds 31 Oct and **18 Nov 2026** (Buß- und Bettag, a public holiday there — the
    day's cell is tinted as a holiday, unlike Bavaria's).
  - **Berlin** adds 8 Mar 2027; **Day view on 19 Oct 2026** is labelled "Herbstferien".
  - Change the country to Austria: the state row disappears and Bavaria's days go.
- [ ] **Austria:** 26 Oct, 1 Nov, 8 Dec drawn. There is no state picker. The note says the
      nationwide school vacations are shown and the semester and summer breaks are not. **Day view
      on 28 Oct 2026** is labelled "Herbstferien"; **2 Nov 2026** "Allerseelen".
- [ ] **School-vacation line on the month grid, and how it looks.** Germany → Bavaria, month
      view, **November 2026**: 2–6 Nov and 18 Nov each carry a thin grey line along the cell's
      bottom edge; the days around them do not. Then Czechia, **December 2026**: 23–31 Dec carry
      it, **24–26 Dec included** (public holidays inside the break keep both the red tint and the
      line), and the January days the last row borrows (1–2 Jan 2027) carry it too, fainter, like
      the custody band there. Judge it in **light and dark theme**, on a custody-coloured cell, on
      a weekend and on a Wednesday with a contact-window corner: the line must read as a line
      under the cell — never as a colour of its own, a parent's hue or the friend's teal — and must
      not hide whose day it is. Swipe between October and November: **the grid does not change
      height** (the reason the old month banner was removed). Then **Czechia, July–August 2026**:
      every cell carries it; record whether that reads as calm texture or as noise — that is the
      owner call ROADMAP MON-13 names. With TalkBack, a vacation day reads its vacation's name,
      and 24 Dec reads "Christmas Eve" and "School vacation".
- [ ] **Settings → App → Data sources and licences** opens a screen with an up-arrow and no
      bottom bar; the OpenHolidays row names ODbL 1.0, and tapping the licence row opens
      opendatacommons.org in the browser. Check the screen in one non-English language.
- [ ] **Russia:** 4 Nov drawn.
- [ ] **Ukraine:** nothing drawn, and the note says holidays are suspended under martial law.
- [ ] **Other:** nothing drawn, and the note says so.
- [ ] Holiday names: with the app language matching the country (e.g. Deutsch with Germany),
      the local name shows; otherwise the English name.
- **If it fails:** the data is pinned by `HolidayReferenceTest` (public holidays) and
  `SchoolVacationReferenceTest` (school vacations), so suspect the rendering or the setting
  (`domain/holidays/HolidayCountry.kt`, `HolidayLocation`) rather than the tables — unless a
  ministry has changed a published date since the pinned dataset commit, in which case regenerate
  with `tools/generate-school-vacation-fixture.py`.

### 3.5 Contact windows (MON-6b) · 1P

Custody setup → **Contact windows** → add one on **today's weekday**, 15:00–19:00, for the parent
who does **not** have today.

- [ ] **Month:** today's cell has a small corner triangle in the window parent's full colour. The
      weekend grey, custody band and handover diagonal still read as before.
- [ ] **Day/Week:** a 15:00–19:00 band in the window parent's tint, with a full-colour edge. On
      a weekend day the grey shows through inside the band.
- [ ] Add a window naming the parent who **already has** that day. It is **not drawn**.
- [ ] **Home today card:** under "whose day it is", a line reads
      "15:00–19:00 · contact with <name>" in the window parent's colour. **(screenshots)** `home_today_card` and
      `calendar_month_grid` show the line and the Month corner from fixed data; on the phone,
      check that a window you *saved* reaches them.
- [ ] The existing MON-6 midweek toggle still behaves as before (a whole day with overnight).
      Its warning now points to contact windows.
- [ ] **2P, mixed versions:** one phone on build A (`f6bab3e`, which has no windows) and one on
      B. Try a swap and a proposal each way. Windows survive a write from the newer phone. A
      write from the old phone may drop them, and that is allowed. A proposal or swap from the
      new phone never *changes* them. There is no one-phone fallback: the rules suite
      (`custody-models.test.js`) is the substitute. **[CI]** the wire contracts
      (`WireContractTest`, `custody_models` fixtures) run both builds' mappers over each other's
      documents: an older build's document keeps its missing `contactWindows` key through a write,
      an unreadable window survives a swap write verbatim, and the `upgrade` job has the *previous*
      build read this one's pattern and swap writes. The phone still shows the grid each build draws
      and the real timing of two syncs.
- **If it fails:** `presentation/custody/ContactWindowsSection.kt`, `MonthView.kt`,
  `DayWeekView.kt`, `CustodyResolver.contactWindowsResolver`; tag `CustodyModelRepo`.

### 3.6 Per-app language picker, debug APK part · 1P [CI]

**[CI]** `PerAppLocaleTest` sets each of cs, de, ru and uk through
`AppCompatDelegate.setApplicationLocales` and sees Settings render in it on API 30 — so
`MainActivity` is still an `AppCompatActivity` and composition follows the choice. It does not tap
the Settings row, and it cannot see Android 13's system setting or a Play install.

- [ ] Settings → App → **Language** → **Deutsch**, while the phone is in another language. The
      UI switches at once.
- [ ] Force-stop and relaunch: still German.
- [ ] On Android 13+, system Settings → Apps → CoPlanly → Language shows Deutsch. Change it there
      and the app follows.
- [ ] Go through Čeština, Русский, Українська and English, then back to **System default**.
- [ ] Known and not a failure (AUDIT §4.2): Home's dates may keep the old language until the
      process restarts.
- **(screenshots)** Whether each translation *fits* — clipping, ellipsis, wrapping at 1.5× text
  in German and Ukrainian — is in the artefact for the components it renders. This section is
  about the picker switching the language, which only a device shows.
- The install that matters is the Play-like one in §4.2. A sideloaded APK always contains every
  language, so this part cannot catch the split bug.

### 3.7 Push opt-out switch · 1P, full check 2P [CI]

**[CI]** The JVM test `FcmServicePushSwitchTest` pins this device's side of the first two boxes:
off deletes `fcmToken` from `users/{uid}` and the token itself, on writes a fresh token back,
and while off nothing re-registers one. `PushNotificationTest` (instrumented) pins what arrives:
a push for another account posts nothing and triggers no sync, every type is worded in the
reader's language, posts to its own channel (chat, schedule, money, family — D-13) and names the
screen its tap opens, and the tap carries the family. What only the phones add: the console
showing the field gone, the OS permission turning the switch off, a real push arriving or not, and
the tap actually landing on that screen.

- [ ] Settings → App → **Push notifications** off. In the Firebase console → Firestore →
      `users/{A's uid}`, `fcmToken` is removed or empty.
- [ ] Turn it on. The token is back. If the OS permission is denied, the switch shows off.
- [ ] **2P:** with push **on**, B creates an event and a push arrives on A. Turn A's push off,
      have B create another event, and no push arrives. Turn it on again and pushes arrive.
- [ ] **2P:** sign A out. B's next event must **not** arrive on A's phone
      (`FcmService.unregisterToken` runs on sign-out).
- [ ] **2P, D-13:** tap B's event push on A: the **calendar** opens, not Home. A day-swap offer
      opens Home (where it is answered), a change request the inbox, a split proposal Expenses.
      System Settings → Apps → CoPlanly → Notifications lists **Messages, Schedule, Expenses,
      Family** and no "CoPlanly notifications"; muting Expenses silences B's split proposal and
      not B's event.
- **If it fails:** tag `CoPlanlyMessaging` (it also logs when it drops a push addressed to
  another uid); `data/remote/firebase/FcmService.kt`.

### 3.8 Receipt OCR, on-device only · 1P

- [ ] Expenses → add → scan a real paper receipt. The camera permission is asked at this moment,
      not earlier.
- [ ] The amount (and the date or merchant, where the receipt has them) is prefilled.
- [ ] Put the phone in **airplane mode** and scan again. It still works: ML Kit's text model is
      bundled, and nothing leaves the phone (CLAUDE.md item 10).
- **If it fails:** tag `CoPlanlyReceiptScan`; `data/mlkit/MlKitReceiptTextRecognizer.kt`,
  `ExpenseViewModel.scanReceipt`.

### 3.9 Other things only a phone shows (AUDIT-2026-09 §5) · 1P

- [ ] **Insets**, in light and dark: on every tab, look for a doubled gap above the top bar or
      above the bottom bar.
- [ ] **Keyboard:** type in the chat composer, the event form, the expense form, the onboarding
      profile step and above the custody Save bar. Does the keyboard cover the field? Nothing
      uses `imePadding()` today.
- [ ] **Home → tap an event.** The preview sheet opens, Edit works from it, and there is no
      Delete (Home passes `onDelete = null`).
- [ ] **TalkBack:** delete an expense through the actions menu, and toggle "Private" on an event.
      Each switch announces its name. (**[CI]** checks only that icon-only controls on Home,
      Calendar, Chat, Expenses and Settings carry a label and are at least 48 dp — on empty
      screens. TalkBack's reading order and the controls that appear with data are still yours.)
- [ ] **Google Calendar** connect and import. This needs REL-3's OAuth env and a functions
      deploy. It also covers SEC-5 (tokens in `EncryptedPreferences`): relaunch the app and the
      account stays connected.
- [ ] **SEC-5 upgrade:** with Google Calendar connected on the *previous* build, install
      this one over it (no uninstall). Calendar is still connected, Settings keep their values,
      and the app does not ask the telemetry question again — the old store was copied into
      `no_backup/secure_prefs.bin` on the first launch. **[CI]** runs that copy on the emulators
      (`EncryptedPreferencesMigrationTest`), and the `upgrade` job carries a sealed store the base
      build wrote — refresh token, settings, telemetry answer — across `adb install -r`; only a
      phone has a store an older, pre-SEC-5 build wrote under a hardware-backed Keystore.

### 3.10 Record photos: medical, pet, receipt and event (L-4) · 2P, guest check 3 accounts

> **[CI e2e]** `TwoParentRecordPhotosTest` runs the mechanism for all four kinds through the production `FirebaseImageStorage` and `SharedFileCache` on the emulators under the repository's `storage.rules`: the family path, the co-parent's digest-checked download, a stranger refused, either parent deleting, the move of a photo taken before pairing. It cannot prove the live bucket has the deploy (first box), nor what a screen draws.

- [ ] **Before** `firebase deploy --only storage`: every photo upload fails. The logcat shows
      `PetsViewModel: Uploading a pet photo …`, `ChildInfoViewModel: Uploading a medical photo …`
      or `CoPlanlyUpload: … upload failed` with a permission error. That confirms the diagnosis;
      it is not a new bug.
- [ ] **After** the deploy, on A (paired with B): Pets → a pet → add a photo; Child → medical → add
      a photo; an expense with a receipt photo; an event with a photo. Each thumbnail appears on
      A **without a network round trip** (the upload keeps its own copy). In the console the
      files are under `pet_photos/<A__B>/<petId>/`, `medical_photos/<A__B>/<childId>/`,
      `receipts/<A__B>/<expenseId>/` and `event_images/<A__B>/<eventId>/`, each with custom
      metadata `uploader` and `sha256`. **No** file has a download token (Storage → the file →
      "Create new access token" is the only token, none pre-existing).
- [ ] On B after a sync: the four thumbnails appear; each opens full-screen and zooms. Airplane
      mode after one view: the same photo still opens (verified cache).
- [ ] B removes the pet photo and saves: it disappears on both phones, and the object is gone from
      the console. A replaces the receipt photo: the old object is gone, the new one shown on B.
- [ ] **Guest check (3 accounts):** A invites a guest (C) to the child's record. On C the record
      shows its medical notes **without** the photo strip — no empty frame, no broken image. A
      calendar friend of the family sees the event preview **without** the photo.
- [ ] **Before pairing:** a fresh account D, unpaired, adds a pet with a photo — the thumbnail
      shows on D, and the console has it under `pet_photos/solo_<D>/<petId>/`. D pairs with E.
      Within a minute the console shows the photo under `pet_photos/<D__E>/<petId>/`, the `solo_`
      folder is empty, and E sees the photo after a sync.
- [ ] **Legacy photos:** on a phone that still holds a record from a build before L-4, the record
      opens without its old photo (no broken image). After `purgeLegacyPhotoPaths`, the console
      holds nothing under the flat `receipts/<id>.jpg`, `event_images/<id>.jpg`,
      `medical_photos/<childId>/…` or `pet_photos/<petId>/…` paths.
- [ ] §7 needs the same four kinds of photo on the throwaway account.

### 3.11 Seasonal schedules and holiday fairness (MON-14, MON-20) · 1P, proposal check 2P

> **[CI e2e]** `OnScreenAgreementsTest` runs the **2P proposal check's** mechanism on one real screen: B proposes a one-day layer, the proposal pops up on A's Home naming B, A accepts, and today's month cell then says "With B" on A's grid while B's phone holds the decision. The phone still adds the editor itself, the band's colour, B's grid, and everything above the 2P line.

Custody setup, below the preview: **Seasonal schedules**, then **Holiday fairness**. Needs a saved
base pattern; schema 38 (the Regenerate workflow must have exported `38.json` for CI to be green).

- [ ] With no base pattern saved, the section says to save one first and offers no editor.
- [ ] **Add a seasonal schedule** → name "Summer" → **Fill from school holidays** (Czech account):
      the chips list the next breaks, widened over weekends and public holidays — autumn
      28 Oct–1 Nov 2026, Christmas 23 Dec–3 Jan, Easter 25–29 Mar 2027, summer 1 Jul–31 Aug
      2027. No spring break (district-dependent, deliberately absent). Tap the summer chip: the
      range fills; the name is kept if you typed one.
- [ ] Shapes: **Split in half** with the other parent first → Save. On an **unpaired** account the
      month grid for July/August now shows the first half in that parent's band and the second in
      yours; June and September are the base pattern again. **No new colour** on the grid — the
      band is the ordinary custody band, the weekend grey still underneath.
- [ ] The row reads "1 Jul 2027 – 31 Aug 2027 · <name> 31 · <name> 31". Tap it → **Delete** →
      the summer grid returns to the base pattern.
- [ ] An overlapping "Christmas" (all with one parent) inside a longer "Winter" layer: Christmas
      wins on its days, Winter on the rest.
- [ ] A contact window on the base pattern does **not** appear inside a layer's dates.
- [ ] **Holiday fairness:** the year chips switch between this year and the next; "Nights" adds
      up to 365/366; Christmas Eve, Christmas Day, New Year's Day and Easter name a parent (or
      show both counts when split); a child's birthday row appears when a date of birth is set;
      school vacations show day counts per parent. "Show other public holidays" lists the rest.
      Names only, never Mom/Dad; each parent's colour is their chosen one (§3.3).
- [ ] **Propose a change** opens the seasonal editor; nothing changes until it is saved.
- [ ] **2P, paired, schedule already shared:** A adds a layer → "Sent to your co-parent for
      approval"; A's grid is unchanged; B gets the proposal (inbox banner), whose description
      says "The seasonal schedules change too" even when no day in the next weeks moves. B
      accepts → both grids change on the layer's dates. While B's own proposal is pending, A's
      seasonal change is refused with "Answer it first".
- [ ] **2P, mixed versions:** a swap or a proposal from a build without MON-14 keeps the layers on
      the newer phone (the rules allow the older build to drop the key; the mirror keeps its
      copy). No one-phone fallback: `custody-models.test.js` "seasonal layers" is the substitute.
      **[CI]** the wire contracts cover the document half: an `L2;…` layer and a `C2;…` child
      schedule are kept verbatim through a swap write *and* a pattern write, a `p2|…` citation
      survives, and the previous build reads this build's writes in the `upgrade` job. What is left
      here is the grid on each phone and the proposal banner.
- [ ] **Calendar feed (MON-17), if deployed:** an iPhone subscribed to the feed shows the layer's
      custody bars on its dates after the next refresh. **[CI]** the `web` job parses the feed —
      layer, swap and contact windows included — as a calendar app would; what is left is the app.
- **If it fails:** tag `SeasonalScheduleVM` / `CustodyModelRepo`; `presentation/custody/Seasonal*`,
  `HolidayFairnessCard.kt`, `domain/custody/SeasonalLayer.kt`, `HolidayFairness.kt`,
  `firestore.rules` `seasonalLayersKeptOrDropped`.

### 3.12 From the parenting plan to the schedule (MON-21) · 2P, 1P fallback

Settings → Family → **Parenting plan**, then Custody setup. Needs two paired accounts that already
share a custody schedule, and **`firebase deploy --only firestore:rules`** with the
`proposalPlanCitation` clauses — until then the live rules refuse every proposal that cites the
plan (their `hasOnly` lists do not name the key), and the repository falls back to a local save.

- [ ] A and B each answer **"How will you divide weekdays, weekends and school holidays"** with the
      same words (e.g. "Week on, week off, changing on Monday") and each ticks the other's. The
      question reads **Agreed**, and a row **Propose as the schedule** appears under it. It does
      not appear under an agreed question that is not about the schedule (the doctor, the
      handover place), nor under one only one parent has ticked.
- [ ] A taps it: Custody setup opens with **From your parenting plan** at the top quoting the
      question and the agreed wording ("You both agreed"), and the form below is **not** filled in
      from it. A builds week-on-week-off and saves → "sent for approval"; A's grid is unchanged.
- [ ] B: the proposal card in the inbox (change requests) reads **"From the parenting plan: How
      will you divide weekdays, …"** under the description of what changes.
- [ ] B, the same proposal on the other two surfaces: **Home's pop-up** carries the same line under
      the description, in smaller grey text; the **calendar's** "review" banner above the grid is
      one line taller, with "From the parenting plan: …" (ellipsised, not wrapped) under it. After
      A edits the answer (next check) the banner reads **"Plan answer changed since proposed: …"**
      and the pop-up the long "changed since" sentence. A proposal made without the plan leaves
      both exactly as they were before MON-21 (a one-line banner).
- [ ] **Export:** B, Settings → Family → *Export the record*, a period covering the proposal, both
      formats. The chat's "proposed a new schedule" message carries **"Proposed from the parenting
      plan answer to: How will you divide weekdays, …"** — under the message text in the PDF, in
      the *Notes* column of that message's row in the CSV. It still names the question after the
      proposal was accepted, and after the answer was edited (it records what was cited, not the
      plan today). A proposal made before this build, or without the plan, prints no such line.
- [ ] **Changed since:** before B answers, A edits the answer (the agreement lapses). B's card now
      says the answer **has changed since this was proposed**, live, without reopening. B accepts
      or declines; the card disappears, and a later proposal A makes without the plan shows no
      plan line at all.
- [ ] A holiday answer ("How will you divide the school holidays") agreed the same way:
      **Propose as the schedule** opens Custody setup with the **seasonal-layer editor already
      open**, the answer quoted at the top of the dialog; saving the layer sends a proposal whose
      card on B cites the holiday question. Dismissing the dialog leaves the quote above the
      seasonal list; adding a layer from there does **not** cite the plan.
- [ ] While **B's own** schedule proposal waits for A, A's plan screen shows the row with
      "<B's name> has a schedule proposal waiting for your answer…" and it does nothing on tap.
- [ ] **Mixed versions:** B on a build without MON-21 accepts or declines a cited proposal
      normally (the key is simply dropped); a swap offered by B while A's cited proposal is
      pending keeps A's proposal.
- **1P fallback:** none that shows the citation — it is read on the other phone. On one phone
  check only that the row appears under an agreed schedule question and that the editor quotes it;
  `custody-models.test.js` "parenting-plan citation (MON-21)" and `PlanScheduleLinkTest` cover the
  rest.
- **If it fails:** tag `CustodySetupViewModel` / `SeasonalScheduleVM` / `ChangeRequestViewModel`;
  `domain/parentingplan/PlanScheduleLink.kt`, `PlanCitation.kt`,
  `presentation/parentingplan/PlanReferenceSource.kt`, `firestore.rules` `planCitationValid`; for
  the other surfaces `presentation/home/AwaitingDialogs.kt`, `ChangeRequestBanner`
  (`CalendarBanners.kt`), and for the export `RecordFormat.planCitation` and the
  `CUSTODY_PROPOSED` card's `activity.planCitation`.

### 3.13 A child's own schedule (FAM-4) · 1P, proposal check 2P

Custody setup with a saved family pattern and **two children** (Settings → Family → children).
Schema 42 (the Regenerate workflow must have exported `42.json` for CI to be green); the
`childOverridesKeptOrDropped` rule needs `firebase deploy --only firestore:rules` before a paired
family's proposal can carry an override.

- [ ] **With one child** there is no "Different schedule for a child" section at all, Home's hero
      is the one family sentence, and the calendar looks exactly as before. Add a second child:
      the section appears under the seasonal schedules, one row per child, each reading "Follows
      the family schedule". Pets are never listed.
- [ ] Tap a child: the same editor opens titled **"Schedule for <name>"**, with a line saying
      seasonal schedules and one-off swaps do not change it; it starts from the family pattern.
      The seasonal section and the child rows are gone. Back (arrow and system back) returns to
      the family editor, unchanged; it does not leave the screen.
- [ ] Unpaired: choose **Custom**, clear every day (the child is always with the other parent) →
      Save is enabled → Save. The row now reads "Own schedule". Reopen it: it opens as Custom with
      the same days.
- [ ] Calendar, month view: the band is still the family's. Filter to **that child alone** → the
      band becomes the child's (all one parent here), with **no new colour**; swap arrows are gone
      and a long press offers no swap. Filter to both children, or to the other child → the family
      band again.
- [ ] Home on a day the family schedule gives the children to the other parent: under the hero's
      chips, one line per child — "Ema is with <name> today", "Tomáš is with <name> today" — names
      only, no dot or tint. On a day they are all with the same parent the lines are absent.
- [ ] Reopen the child → **Follow the family schedule again** → the screen closes, the row reads
      "Follows the family schedule", and Home and the filtered grid are the family's again.
- [ ] **2P, paired, schedule already shared:** A gives a child their own schedule → "Sent to your
      co-parent for approval"; A's grid is unchanged. B's proposal description says "A child's own
      schedule changes too" even though no family day moves. B accepts → both phones' filtered
      grids and Home heroes follow it. A then changes the **family** pattern: B's proposal keeps
      the child's schedule (it is not proposed away).
- [ ] **2P, mixed versions:** a swap or proposal from a build without FAM-4 keeps the child's
      schedule on the newer phone (the key is dropped, the mirror keeps its copy). No one-phone
      fallback: `custody-models.test.js` "per-child overrides (FAM-4)" is the substitute.
- [ ] **Calendar feed (MON-17), if deployed:** the subscribed calendar still shows the **family**
      schedule only — no per-child bars. **[CI]** for the feed's validity (the `web` job); the
      absence of per-child bars is `functions/test/calendar-feed.test.js`'s.
- **If it fails:** tag `CustodySetupViewModel` / `CustodyModelRepo`; `presentation/custody/Child*`,
  `presentation/calendar/ChildCustodyBand.kt`, `presentation/home/ChildrenToday*.kt`,
  `domain/custody/ChildScheduleOverride.kt`, `ChildCustody.kt`, `firestore.rules`
  `childOverridesKeptOrDropped`.

### 3.14 Contrast levels (Android 14+) · 1P

The theme follows the system's contrast setting (October 2026 audit, D-25; CLAUDE.md design item
17). The schemes' arithmetic is `ContrastSchemesTest`'s job and three components are rendered at
high contrast by the `screenshots` job; what only a phone shows is the setting reaching the app,
and every other screen.

- [ ] On an Android 14+ phone open the app on Home, then Settings → Accessibility → Colour and
      motion → **Contrast** → Medium, and switch back to the app **without restarting it**.
  - Secondary text (a row's second line, captions) and outlines are visibly darker (lighter in
    dark theme); backgrounds, cards and the parent colours are unchanged.
- [ ] Set **High**. Body text is black (white in dark theme); nothing that was readable becomes
      harder to read — check Home, the calendar's month and week, Chat, Expenses and Settings.
  - The custody bands, the weekend grey and the holiday reds look exactly as at standard: they
    are the app's own colours and do not move with the level.
  - An event that belongs to neither parent is tinted green in week and day view, not pink.
- [ ] Set **Standard** again: the app returns to its usual colours, still without a restart.
- [ ] On Android 13 or older nothing changes, and there is no setting to try.
- **If it fails:** `presentation/theme/ContrastLevel.kt` (`rememberSystemContrastLevel`, the
  listener), `ContrastSchemes.kt` (generated by `tools/generate-contrast-schemes.py`), `Theme.kt`.

### 3.15 The "Today" widget · 1P, sync check 2P

The home-screen widget (October 2026 audit, week 6; CLAUDE.md design item 18). The layout and the
wording are tested (`TodayWidgetTextTest`, `TodayWidgetContentTest`), and the UI tour draws the
widget's own `RemoteViews` in each variant. What only a phone shows:
- the launcher placing and resizing it
- the redraws
- the system's night mode
- a tap

- [ ] Long-press the home screen → Widgets → CoPlanly. The picker shows **Today** with its
      preview. Place it at its default size.
  - It reads "Today · <date>", then whose day it is in that parent's colour ("Today with Alex").
    It shows a contact window if today has one, the next handover ("Handover to Sam in 5 days"),
    and a count ("3 events today").
  - Stretch it taller. The events appear with their times and owners' marks, and more than four
    end in "+N more".
- [ ] Compare it with Home at the same moment: the same parent, the same handover, the same events.
- [ ] Add an event for today in the app. Within a few seconds of saving, the widget lists it.
      Delete it, and it goes.
- [ ] **2P:** have the co-parent accept a swap for today or add an event today. After this phone
      syncs, the widget follows **without the app being opened**.
- [ ] Leave the phone past midnight, or move the date forward a day in the system settings.
      Within minutes the widget shows the new day.
- [ ] Switch the **system** to dark theme, and the widget follows it. Switch the app's own theme
      setting instead, and the widget does not: the launcher draws it.
- [ ] Tap the widget, and the app opens.
- [ ] Sign out. The widget says "Sign in to CoPlanly to see your day." and names nobody. Sign in
      as another account: until that account's app has been opened once, the widget names nobody
      from the first account.
- [ ] On Android 13+, set the app's language to Russian: the widget speaks Russian. On Android 12
      and older it follows the device language, as pushes do.
- **If it fails:** `presentation/widget/` — `TodayWidget.kt` (loading, the redraw stamp),
  `TodayWidgetRefresher.kt` (Room observer, names, midnight), `TodayWidgetLines.kt` (`TodayWidgetText`, the wording);
  `res/xml/today_widget_info.xml`.

### 3.16 The tour's defects, fixed in week 7 · 1P, "Later" check 2P

Release audit R-1, R-2, R-5 and R-9 (`docs/AUDIT-2026-10-release.md`). Unit tests hold the logic:
`PutOffAsksViewModelTest`, `PreferencesRepositoryCurrencyTest` and `TodayWidgetTextTest`'s
12-hour case. What only a phone shows is the gesture, the device setting and a relaunch.

- [ ] **The Add button (R-1).** Open Expenses on a month with more expenses than fit on screen.
      Before any scroll, no amount sits under the "+" (list and Analytics alike): the content
      ends above it. Scroll down: the "+" leaves, and no amount sits under it. Scroll up a little, and it
      returns. Scroll to the very end: it returns, and the last row's amount is clear of it.
      With TalkBack on, the button stays where it is.
- [ ] **"Later" holds (R-2), 2P.** Have the co-parent offer a day swap. On Home, press **Later**.
      Go to Calendar and back to Home, then close the app from Recents and reopen it: the dialog
      does not return, and the calendar's banner still offers the swap. Have the co-parent change
      the offer (withdraw and offer another day): the dialog asks again.
- [ ] **Currency (R-5).** On a phone whose language and region are English (United States), with
      the account's country Czechia and no currency ever picked in Settings: Add expense opens
      on CZK, and Settings shows CZK. Pick EUR in Settings: new expenses open on EUR, and a later
      country change does not move it.
- [ ] **The reader's clock (R-9).** Turn *Use 24-hour format* off in the system settings, and
      reopen the app. Every time is 12-hour: Home's week and today card, the event preview, Day
      view's contact windows, the chat bubbles, the widget, a reminder. Turn it on again: every
      time is 24-hour, with no screen still saying "PM".
- **If it fails:** `presentation/common/FabScrollVisibility.kt`, `presentation/home/PutOffAsksViewModel.kt`,
  `data/money/CurrencyHints.kt` with `PreferencesRepositoryImpl`, and `utils/LocalizedDates.kt`
  (`shortTime`, `ClockFormat`).

### 3.17 Large screens and the scanner turned sideways · 1P

Release audit R-8 and §3.1, week 8. `TwoPaneTest` measures the panes, and the UI tour's
`light-en-100-wide` variant draws the tabs at 1280 dp. What only a device shows is a real tablet or
foldable, a fold and unfold, and a camera turning.

- [ ] On a tablet held sideways (or a foldable unfolded): Home shows two columns — contacts, the
      handover and today on one side, the week, the changes and the month on the other. Expenses
      shows the month's summary and chart beside its list, with no List/Analytics switch. Chat
      with one co-parent is one centred column; with two families' threads, the list sits beside
      the open thread and a tap changes the thread without leaving the tab.
- [ ] Fold the phone (or turn the tablet upright): every tab goes back to one column, nothing lost
      and nothing doubled.
- [ ] Open the QR scanner and turn the phone sideways, then back. The preview keeps running and a
      code scanned sideways pairs. Deny the camera with "don't ask again", turn the phone: the
      screen still offers Settings, not Allow.
- **If it fails:** `presentation/common/TwoPane.kt`, the tab's screen (`HomeScreen`,
  `ExpenseScreen`, `ConversationsScreen`), `presentation/pairing/QrScannerScreen.kt`.

---

### 3.18 The Bakaláři school import · 1P, sharing check 2P

MON-8. The parsers, the import planner and the token refresh are JVM-tested against the Bakaláři
API's published sample responses; **no real school account has ever been used**, so this section
is the first time the import meets a real server. Needs a parent (or student) Bakaláři login.

- [ ] Settings → Sync → the Bakaláři row → Connect a school. Type your town: the school appears.
      Pick it (or enter its address by hand, e.g. `https://skola.bakalari.cz`).
- [ ] Sign in with a **wrong** password: a plain "wrong username or password", nothing stored.
      Then the right one: the screen names the child and class as Bakaláři knows them.
- [ ] Choose the child (and, with two families, the family). Confirm. Within a minute the calendar
      shows, on each school day of the next four weeks, one event from the first lesson's start to
      the last lesson's end — **compare three days with the Bakaláři app**, including one with a
      substitution or a cancelled lesson.
- [ ] School events for the child's class appear on their days; another class's do not.
      A day off (ředitelské volno) appears as an all-day event; a public holiday or the
      Christmas/Easter/summer vacation is **not** duplicated (the calendar already draws it).
- [ ] Update now twice: no duplicates. Delete one imported event, Update now: it does not come
      back.
- [ ] On the co-parent's phone: the imported events are there, tagged with the child.
- [ ] Settings → Sync → the row: "last updated" shows today. Turn airplane mode on, Update now:
      an error line, nothing lost.
- [ ] Disconnect: the connection goes, the imported events stay. Sign out and back in: no school
      connection is remembered.
- [ ] Two children at the same school: connect each with its own Bakaláři login (Bakaláři issues
      one per child); each child's events carry that child.
- **If it fails:** `data/school/bakalari/` (parsers, client), `domain/school/SchoolImportPlanner`,
  `presentation/school/`. Capture the failing JSON (with names and ids redacted) and add it to
  `app/src/test/resources/school/bakalari/` with a test.

### 3.19 A child's health details need a consent first (L-2) · 1P, withdrawal check 2P

Preconditions: an account that has **never** agreed (a fresh account, or one that withdrew), with
at least one child. Settings → Account → **Health details consent** reads "Not given".

- [ ] **The medical section is locked.** Settings → Family → **Child information**, open a child
      (or add one). Allergies, medications, the medical profile, medical notes and medical photos
      are replaced by one line — medical details are optional and need your consent first — and an
      **Add medical details** button. The rest of the form (name, date of birth, activities,
      contacts, school) works as before and saves.
- [ ] **The dialog says what it covers.** Tap **Add medical details**. The dialog "Your child's
      health details" lists what counts as health details, that the co-parent can read and edit
      them and a guest with access can read them, that they are stored on our servers, that they
      are optional, and that withdrawing in Settings deletes what you added. **Not now** closes it
      and the section stays locked; nothing is recorded.
- [ ] **"I agree" records the consent.** Open it again and tap **I agree**: the medical fields
      appear at once. Settings → Account → **Health details consent** now reads "Given on <today>".
      In the Firebase console, `users/<uid>.healthDataConsent` is `{version: 1, atMillis: <now>}`
      and nothing else. Onboarding's child step uses the same dialog: on a fresh account it asks
      there too, and one "I agree" unlocks both places.
- [ ] Add an allergy, a medication and a medical photo to the child, and save.
- [ ] **Withdrawal, one phone.** Settings → Account → Health details consent → **Withdraw**. The
      confirmation says the medical details you added are deleted, on the co-parent's phone too,
      and that records the co-parent created keep theirs. Confirm with **Withdraw and delete**.
  - **Expected:** a message says the consent was withdrawn and the details you added were
    deleted; the row reads "Not given"; the child's medical section is locked again and, once
    unlocked, empty — allergies, medications, the medical profile, notes and photos all gone.
    Name, activities, contacts and school are untouched. `healthDataConsent` is gone from
    `users/<uid>`, and the photo's object is gone from `medical_photos/<family>/<childId>/`.
- [ ] **All or nothing with photos.** Agree again, add a medical photo, save, then turn on airplane
      mode and withdraw (it gives up after about 20 seconds). **Expected:** "Some medical photos could not be deleted, so nothing was
      withdrawn"; the row still says "Given on …" and the child's other medical details are still
      there. Turn the network back on and withdraw again: it succeeds.
- [ ] **2P: only this parent's children are cleared.** With A and B paired, B (who has agreed)
      creates a child with an allergy; A agrees and creates another child with an allergy. A
      withdraws. **Expected:** on **both** phones A's child has no medical details any more, and
      B's child **keeps** its allergy — B's entries rest on B's own consent. B's Settings row still
      says "Given on …".
- **If it fails:** tags `HealthConsentManager`, `HealthConsentViewModel`, `UserRepository`;
  `domain/consent/HealthConsent.kt`, `data/consent/HealthConsentManager.kt`,
  `presentation/consent/HealthConsentUi.kt`, `firestore.rules` `healthConsentValid`.

### 3.20 The calendar feed on an iPhone (MON-17) · 1P, paired account, iPhone

Preconditions: functions deployed (§0), A paired with B, a custody schedule with at least one
contact window, a few shared events and one **private** event. An iPhone signed in to iCloud with
Apple Calendar. **[CI]** the `web` job already parses the feed the way a calendar app does (see
"What CI now covers"); what is left is the real app on a real iPhone.

- [ ] Settings → Sync → **Calendar feed for iPhone and other calendars**. The screen says what is
      included (custody days and contact times, each with the parent's name, and shared events)
      and what is not (private events, chat, expenses, children's records), and that anyone with
      the link can see the calendar. Unpaired, it says to link a co-parent first and offers no link.
- [ ] **Create a link**, then **Share link** and send it to the iPhone (a message or an e-mail to
      yourself). The screen says the link cannot be shown again, and the list gains one entry,
      "Not opened by a calendar yet".
- [ ] **Subscribe.** On the iPhone, tap the link. Apple Calendar offers to subscribe; accept.
      **Expected:** the custody days appear as all-day entries, one per run of days, reading
      "With <name>" (a name, never "Mom"/"Dad") and not marking you busy; each contact window at its hours with the parent's name, and the shared
      events at their times. The **private** event is nowhere. Czech, Cyrillic and emoji in titles
      read correctly.
- [ ] Back on the Android phone, the link's entry now says "Last opened by a calendar on <today>".
- [ ] **Refresh.** Add a shared event on the Android phone and wait for Apple Calendar's next
      refresh (Settings → Calendar → Accounts → Subscribed Calendars → Fetch sets how often; or
      pull to refresh in the Calendar app). **Expected:** the new event appears. Delete it on
      Android: after the next refresh it is gone.
- [ ] **Revoke.** Settings → Sync → the feed screen → the link → **Revoke link**, and confirm.
      **Expected:** after the next refresh Apple Calendar stops receiving updates (it keeps what it
      last fetched or reports that the subscription failed — either is acceptable; it must never
      show new events). Opening the old address in Safari answers "not found". A link nobody
      opens for 90 days stops working the same way (the `sweepIdleCalendarFeeds` job).
- **If it fails:** `presentation/settings/CalendarFeedScreen.kt`, `CalendarFeedViewModel.kt`,
  `data/repository/CalendarFeedRepositoryImpl.kt`; server side `functions/calendar-feed.js` and
  `firebase functions:log --only calendarFeed` (it never logs the token).

### 3.21 Voice dictation in chat, on-device only · 1P

Preconditions: a paired account with a chat thread. One phone on **Android 13 or later** whose
system reports on-device speech recognition (a Pixel 6 or later does; install the language pack
in the phone's speech settings if asked) and, if you have one, a phone on Android 12 or older. The
app's microphone appears **only** where recognition runs on the phone — there is no fallback to an
online recognizer (`OnDeviceSpeechDictation`'s KDoc says why).

- [ ] **Which phones.** On the Android 12 (or older) phone, and on any phone without on-device
      recognition, the composer has **no microphone** at all — the pill is the field alone. On the
      Android 13+ phone a microphone sits at the end of the pill, the send button beside it
      unchanged. TalkBack reads it "Type by voice".
- [ ] **Permission, in context.** On a fresh install nothing asks for the microphone at start or
      on opening chat. The first tap of the microphone asks. Deny: a short line under the chips says
      why the microphone is needed and that the audio is not recorded or sent, with **Open
      settings**, which opens this app's page in the phone's settings. Allow there and come back:
      the next tap starts listening without asking.
- [ ] **Dictate.** Tap the microphone and speak a sentence. The glyph becomes a stop square
      ("Stop voice typing"), the empty field says "Listening…", and the words appear in the field
      **as you speak**. It stops by itself when you pause, or tap stop. Edit the text, then send it:
      it arrives as an ordinary message on the co-parent's phone.
- [ ] **Appends, never replaces.** Type "Hi." first, then dictate: the result is "Hi. <your
      words>". Type while it listens: listening stops and what was heard stays.
- [ ] **Airplane mode.** Switch airplane mode on and dictate again. It still works — the proof that
      the audio does not leave the phone. (If the phone lacks the language pack offline, the line
      says the language is not installed; it must never fall back to working only online.)
- [ ] **Language.** Settings → App → Language → Čeština (or Deutsch/Русский/Українська). Dictate in
      that language: it is recognised in that language, not in the phone's.
- [ ] **Reduced motion.** With Remove animations on (Accessibility), the stop glyph stands still;
      with it off, it breathes slowly.
- [ ] **Leaving.** While listening, press Home, or switch tab: listening stops (the system's
      microphone indicator goes off).
- **If it fails:** `data/dictation/OnDeviceSpeechDictation.kt`, `presentation/chat/DictationViewModel.kt`,
  `presentation/chat/ChatDictation.kt`, `presentation/common/MicrophonePermission.kt`.

### 3.22 AI reply suggestions and the month in review · 1P, paired account, debug build

Preconditions: a **debug** build (`BuildConfig.AI_ASSIST_ENABLED` is true there, false in release
unless built with `-PCOPLANLY_AI_ASSIST_ENABLED=true`), the `aiAssist` callable deployed to
`europe-west3` with its model configured (§0), an account paired with a co-parent, a thread with a
few messages from both, a custody schedule and a couple of expenses this month. Settings → App →
**AI drafts and summaries** reads "Off" — if it reads "Turned on", tap **Turn off** first.

- [ ] **Release shows nothing.** On a release build (or `-PCOPLANLY_AI_ASSIST_ENABLED=false`), the
      templates sheet has no "Suggest a reply" row, Month in review has no Summary group, and
      Settings → App has no AI row. The month's figures still show.
- [ ] **The consent comes first.** Chat → **Templates** → **Suggest a reply** (subtitle says the
      draft is AI-generated and for you to edit). The dialog says exactly what is sent (the last 20
      messages of this thread, the co-parent's included, and what you typed; a month's figures and
      both names), to Google Cloud in the EU, that nothing is kept, that nothing reaches the
      co-parent unless you send it, and where to turn it off. **Cancel**: nothing happens and in
      the Firebase console `users/<uid>` has no `aiConsent`.
- [ ] **A draft goes into the composer, never out.** Tap **Suggest a reply** again → **I agree**.
      The row says it is writing, then the sheet closes and the draft is in the message box with
      the keyboard up, in the app's language. Nothing is sent: the co-parent's phone shows no new
      message. `users/<uid>.aiConsent` is `{version: 1, grantedAt: <server time>}`. Edit and send
      it as an ordinary message.
- [ ] **The row words each refusal.** In airplane mode tap **Suggest a reply**: the row says the
      service could not be reached and can be tapped again. In a thread with no messages it says
      there is nothing to reply to yet.
- [ ] **Month in review.** Settings → Family → **Month in review**: this month, with days per
      parent **by name**, handovers, special days, swaps offered and agreed, shared events, and one
      block per currency with who paid what and the transfer that evens it out (or "Even"). The
      right arrow is disabled on the current month; the left arrow goes back a month and the
      figures change. At 200 % font no amount is cut off.
- [ ] **The summary is marked and checkable.** **Summarise this month**: a few sentences appear
      above the figures under "Written by AI from the figures below", in the app's language,
      naming parents, with no blame. Going to another month removes it. A month with no custody
      schedule offers no summary.
- [ ] **Turning it off.** Settings → App → AI drafts and summaries → **Turn off** → confirm. A
      message says it is off; the row reads "Off"; `aiConsent` is gone from `users/<uid>`. The next
      **Suggest a reply** asks for consent again.
- **If it fails:** tags `AiAssist`, `AiConsentManager`; `data/ai/FunctionsAiAssistRepository.kt`,
  `data/ai/AiAssistErrors.kt`, `presentation/ai/`, `presentation/chat/ReplySuggestion*`,
  `presentation/review/`; server side `firebase functions:log --only aiAssist`.

---

## 4. Release-build checks

### 4.1 REL-7: R8 and Gson on a device · 2P, fallback 1P

A green `assembleRelease` proves R8 ran. It does not prove that Gson still finds its field names.
That defect has shipped once before.

**[CI] since September 2026, as far as one process can go.** The `r8-runtime` job runs the
minified `r8Test` build (`release` plus the probe in `app/src/r8Test/`) on an emulator and writes
this section's child — the same medical profile, activity and emergency contact — through
`ChildInfoRepositoryImpl` into Room, then checks the stored JSON carries `bloodType`,
`intolerances`, `hereditaryConditions` and `vaccinations` and reads back complete; a pet, custody
swaps, the event draft and the chat mappers the same way (CLAUDE.md, the `r8-runtime` job). The
Firestore map is built from the same Gson call on the same instance, so a green job makes the
console check below a confirmation. What the job cannot do is below: it never signs in, so the
document itself, the co-parent's phone and the telemetry check are still the phone's.

Preconditions: install build C: `adb uninstall app.coplanly && adb install app-release.apk`. Do
the same on B's phone if you have one.

- [ ] Sign in as A. Save a child's **medical profile**: blood type, two allergies, an
      intolerance, a hereditary condition, a vaccination with a date. Also add an activity and an
      emergency contact. **[CI]** for the Room half (`r8-runtime`).
- [ ] Firebase console → `child_info/{id}`: `medicalProfile` has **readable keys**
      (`bloodType`, `intolerances`, `hereditaryConditions`, `vaccinations`), not `a`, `b`, `c`.
      Each list is non-empty. **[CI]** for the keys Gson writes; the document itself is the
      phone's.
- [ ] **2P:** on B's phone (also on build C), the same child's medical profile is **complete and
      non-empty**.
- [ ] **Fallback (1P):** run `adb shell pm clear app.coplanly`, sign in as A again, and let it
      sync. The medical profile comes back from Firestore complete. That covers both directions
      of the Gson mapping on a release build.
- [ ] A Google Calendar import on the release build works. It uses the `@Key` models. **[CI]** for
      parsing a page into them (`r8-runtime`); the OAuth sign-in and the network are the phone's.
- [ ] **Telemetry end to end** (a release build has `ENABLE_ANALYTICS=true`):
  - Run `adb shell setprop debug.firebase.analytics.app app.coplanly` and open Firebase
    console → Analytics → DebugView.
  - While consent is **declined**, no events arrive.
  - Grant consent in Settings → Usage statistics, and events start.
  - Decline again, and they stop.
- [ ] While on the release build, glance at the palette, the switcher and the second-co-parent
      invite (M-4, shipped but never seen).
- **If it fails:** `app/proguard-rules.pro` (the `-keepclassmembers ... { <fields>; }` rules) and
  `tools/check-r8-mapping.js`. A new Gson type without a rule is the likely cause. If the
  `r8-runtime` job was green on the same commit, the fault is past Gson — the Firestore mapping
  or the sync — and its `r8-probe` artefact holds the JSON the minified build wrote.

### 4.2 Language picker after a Play-like install · 1P

An AAB is split by language, and Play installs only the phone's languages. The fix is
`bundle { language { enableSplit = false } }`, and only an install from the **bundle** can prove
it. Until the Play Console exists (REL-6), `bundletool` stands in for internal app sharing:

```bash
adb uninstall app.coplanly
java -jar bundletool.jar build-apks --bundle app/build/outputs/bundle/release/app-release.aab \
  --output coplanly.apks --connected-device \
  --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android
java -jar bundletool.jar install-apks --apks coplanly.apks
```

- [ ] With the phone in (say) English, pick **Deutsch** in Settings → Language. The UI switches
      to German, not partly and not after a restart only.
- [ ] Repeat with Čeština and Русский.
- [ ] Once REL-6 exists, repeat through **internal app sharing** from the Play Console. That is
      the real test; bundletool is the closest stand-in.

---

## 5. Two phones, or three accounts

Preconditions: A and B are paired, and each phone has its own account signed in.

### 5.1 Cross-time-zone chat (CQ-18) · 2P, no real fallback

> **[CI e2e]** `TwoParentChatTest` (the `e2e` job, two accounts on the Firebase emulators, UTC+14 vs UTC−11) proves the unread count, DELIVERED and READ agree across zones. `OneParentOnScreenTest` adds one screen: A's real app draws B's message in the thread, and what A types into the composer reaches B. The phones are still needed for two screens at once in two zones — the badge, the ticks, displayed times, on-screen order — and for push delivery.

- [ ] Set B's phone **2–3 hours** away from A's (e.g. A on Prague, B on Dubai or on
      Reykjavík). Force-stop both apps.
- [ ] B sends a message. On A it arrives **unread**, the badge counts it, and the time shown is
      correct *in A's zone*.
- [ ] A opens the thread. The badge clears. On B the message's ticks reach **READ**.
- [ ] Reverse the roles.
- [ ] Order: messages sent alternately from both phones sort by real time, not by wall clock.
- [ ] **Partial fallback (1P):** send a few messages, change the phone's zone by 3 hours, and
      force-stop. The times shift by 3 hours and the order is unchanged. This checks display
      only, not the read or unread logic.
- **If it fails:** tags `ChatMirror`, `MessageRepo`, `ChatViewModel`. `ChatReadState` holds the
  logic, and `ChatReadStateTimeZoneTest` is the unit-level pin.

### 5.2 Family switcher and chat following the selected family (M-8) · 3A, 2P or 1P fallback

> **[CI e2e]** `MultiFamilyTest` proves the data side: with a second co-parent selected, a new event gets that family's audience and `familyId` and its announcement goes to that thread, and none of it reaches the first co-parent. `OnScreenFamiliesTest` drives the switcher on A's real screens: with B's family on screen, C's chat message puts the dot on Home's chip (its description names "New messages"), C's row in the dialog says the same, choosing it brings C's thread onto the Chat tab, and A's reply reaches C. Still manual: the push from the other family, the chip on Expenses, the change-request and schedule kinds of the dot, TalkBack, and live arrival on several screens at once.

Preconditions: A is paired with **both** B and C (two families). Invite C from Settings → Family.

- [ ] Home and Expenses show a **switcher chip** beside the gear.
  - It names the family on screen by its co-parent.
  - It is absent on Calendar and Chat.
  - For an account with one co-parent it is absent everywhere.
- [ ] The chip and the Settings row open the same dialog. Switching families changes Home,
      Calendar and Expenses to that family's records.
- [ ] **Chat follows the selected family.** After switching to C's family, the Chat tab
      opens **C's** thread and the badge counts C's unread messages.
  - Switch back to B: B's thread and B's badge.
  - Messages B sent while you were on C's family arrive once you switch back.
- [ ] A **push** from the family *not* on screen, tapped, switches to that family first and then
      opens the target.
- [ ] **The cross-family dot.** With B's family on screen, have **C**:
  - send a chat message — a dot appears on the chip and on C's row in the dialog, whose line
    says "New messages";
  - file a change request on one of C's events — C's row also says "A change request is
    waiting";
  - propose a new custody schedule, or offer a day swap — C's row also says "A schedule
    proposal is waiting".
  - With TalkBack on, the chip reads those kinds, not only "new".
  - Each kind goes once it is answered (or the thread is read) on C's family; the dot goes with
    the last.
  - Never a number. News in the family **on screen** (B) never raises it. A one-family account
    shows no chip at all.
  - logcat (`OtherFamiliesSignals`) shows no "gave up" and no missing-index error.
- **Fallback (1P):**
  - Generate the invite codes as A.
  - Sign out, sign in as B, redeem, send a chat message.
  - Do the same as C.
  - Sign back in as A and check the chip, the switch and the Chat tab.

  This cannot show live arrival or pushes. Each sign-in as a different account wipes local
  data, so do it only after §2–§4.
- **If it fails:** tags `SelectedFamily`, `ChatMirror`, `ChatViewModel`;
  `presentation/common/FamilySwitcher.kt`, `data/chat/ChatPartnerSource.kt`,
  `data/family/OtherFamiliesSignals.kt` (tag `OtherFamiliesSignals`).

### 5.3 Also worth doing while two phones are paired · 2P

> Real FCM delivery cannot be emulated: the e2e job sees the `notification_queue` document written, never the push arrive.
>
> **[CI e2e]** `OnScreenAgreementsTest` answers the co-parent on one real screen: a change request's calendar banner and the inbox's Accept, Home's pop-ups for a custody proposal (accept and decline) and for day swaps (accept and decline), each confirmed on the other phone's data. What two phones add here is both screens at once and the push that brings the second parent to the screen.

- [ ] UX-15 with both parents on non-default colours (§3.3).
- [ ] MON-6b with mixed versions (§3.5).
- [ ] REL-7 on both phones (§4.1).
- [ ] The push opt-out end to end (§3.7).
- [ ] MON-4's event time (schema 39) with **one phone on the previous build**: events the
      upgraded phone creates or edits still arrive on the older one, and the older phone's
      still arrive on the upgraded one — `events.updatedAt` stays offset-free text both can
      parse. A missing event here means the wire form broke; tags `SyncService`,
      `EventRepository`. The conflict rule itself (`ConflictResolverTest`, two zones) is hard to
      reach by hand: the sync uploads a phone's own edits before it downloads, so it only
      decides when an upload failed and the download that follows succeeded.
      **[CI]** "one phone on the previous build" is now a wire contract first (CLAUDE.md, the rule
      under item 5 of "Things that are easy to get wrong"): `WireContractTest` reads older and newer
      builds' `events`, `messages` (legacy ISO `timestamp` included), `child_info`, `pets`,
      `expenses`, `budgets` and `event_versions` documents through this build's mappers and writes
      them back, and the `upgrade` job runs the *previous* build's copy of it over what this build
      writes. A PR that changes `app/src/test/resources/wire/current/` is the one to do this check
      for; otherwise it confirms what CI saw. The phone adds the real sync timing, both builds'
      screens, and the conflict rule under a failed upload.

### 5.4 Professional access (MON-18) · 3A, 2P or 1P fallback

Needs the REL-3 functions **and** rules deploys: without the functions the code does not redeem,
without the rules the grant opens nothing. Accounts: A and B paired, P a third account (the
professional), signed in on its own phone or after A/B on the fallback phone.

- [ ] A: Settings → Family → **Professionals** → Invite. Role and length are chosen first; the
      sheet says what is and is not shared. The code shares through the share sheet.
- [ ] P: Settings → Professionals → enter the code. It says the code was accepted and that
      reading starts once both parents consent. The family appears with Calendar and Parenting
      plan **disabled** and "waiting for both parents' consent".
- [ ] A and B each get the push "Professional access requested", in each phone's language.
- [ ] A's row reads "waiting for your co-parent's consent"; B's reads "waiting for your consent".
      B opens it → **I consent**. Both rows turn to "can read until {date}".
- [ ] P: Calendar opens, four weeks per page, each day names whose day it is and lists the
      shared events. No private event of A or B appears. Nothing can be tapped to edit.
- [ ] P: Parenting plan shows both parents' answers side by side, "Agreed" where both ticked,
      and the disclaimer. Nothing is editable.
- [ ] P has no way to chat, see expenses or child records (there is no route to them at all).
- [ ] A (not B) → the row → **End access** → confirm. P's calendar and plan show "nothing to
      show" at once, and the row leaves both parents' lists.
- [ ] Unpair A and B while a grant is active: the grant disappears from P's list.
- **Fallback (1P):** do the steps as A, then B, then P by signing in and out. This cannot show
  pushes arriving or P's view emptying live.
- **If it fails:** tag `ProfessionalRepository`; `presentation/professionals/`,
  `firestore.rules` `isProfessionalOf`, `functions/index.js` `acceptProfessionalInvitationImpl`.

---

### 5.5 Document vault and chat attachments (MON-23) · 2P, 1P fallback

Needs `firebase deploy --only storage` **and** the rules and indexes deploy (§0): before the
storage deploy every upload is refused; before the rules deploy the vault list is refused.
A and B paired.

**[CI]** The `e2e` job runs the mechanism on the Storage emulator against the real rules
(`TwoParentAttachmentsTest`): a vault document opens for B, who cannot delete it, and A's delete is
a tombstone; a chat file stays off the server while its upload fails and arrives after the retry,
with B's download matching its SHA-256; a stranger's download is refused. What is left here is
the screens, the pickers, the camera, a viewer app and a real network.

- [ ] A: Settings → Family → **Documents**. The first line says everything here is shared and
      nothing is private. **Add a file** → pick a PDF → name it, choose *Court orders* → **Upload
      and share**. "Uploading…" shows, then the row appears under *Court orders* with size, "added
      by" A's name and the date.
- [ ] A: **Take a photo** (grant the camera) of a paper → *School* → it appears under *School*.
- [ ] A: try a `.zip` or a video from the picker if the provider offers one → refused with the
      "Only PDF, JPEG, PNG, HEIC and WebP" sentence; a file over 20 MB → the size sentence.
- [ ] B: the vault shows both within seconds, without a refresh. Tapping each opens it in a
      viewer (PDF viewer, gallery). B has **no** delete button on A's rows.
- [ ] A: delete the school photo → confirm → it leaves both phones' lists.
- [ ] A, in Chat: the **paperclip** left of the composer → pick a photo → the dialog names the
      file and size and says it cannot be deleted later → **Send**. The thumbnail appears above a
      bubble carrying the file name; it ticks once it is sent.
- [ ] Same for a PDF: a chip with the name and size. B sees the thumbnail and the chip; tapping
      opens each. Image and PDF are also listed in an export (§6) as name + SHA-256.
- [ ] A in **flight mode** → send a photo: "Not uploaded yet" under it, the clock tick, never a
      check. Leave flight mode, pull to refresh: "Uploading…", then the tick. B receives it once.
- [ ] Open the same attachment twice: the second time it opens without a download (cache).
- [ ] **Vault offline (schema 43 cache).** A: open Documents online once, go back, switch on
      **flight mode**, reopen Documents. The list A last saw is shown under the line "Can't reach
      the server — showing the list this phone saw last. It may be out of date"; a document
      already opened once still opens (file cache), one never opened does not open until the
      network returns (note how long it takes to say so — Storage retries).
      Meanwhile B files a new document; A leaves flight mode: the line goes and B's document
      appears without reopening the screen. With two families (M-8), switch family offline:
      the other family's list is never shown under this one.
- **Fallback (1P):** do A's steps, sign in as B on the same phone and look.
- **If it fails:** tag `FamilyDocuments` / `ChatAttachments` / `MessageRepo`; `storage.rules`
  (`isOneOfPair`, `isAcceptableSharedFile`), `firestore.rules` `family_documents`,
  `data/files/`, `data/chat/ChatAttachmentOutbox.kt`.

---

## 6. Export (MON-3) · 1P

Expected: event versions, plus a PDF and CSV export started from Settings → Family → **Export the
record**.

**[CI]** `ExportFileWriterTest` writes both files on the emulator from a fixture (two revisions
with both clocks, a private event, a formula-looking message, an expense) and checks the CSV
parses as RFC 4180 with the statement first, the formula guard and no private event, that the
PDF opens in `PdfRenderer`, and that the share intent carries the `FileProvider` URI with a
read-only grant. Still yours: revisions that came from the server, the share sheet, and a real
spreadsheet and PDF app.

Preconditions:
- several events, including one **private** event;
- one shared event **edited twice**, changing the time and then the title.

- [ ] Settings → Export. Choose a date range that covers those events.
- [ ] **Generate the CSV.** It opens in a spreadsheet app, and the columns are readable.
- [ ] **Generate the PDF.** It opens in a PDF viewer.
- [ ] Both have a **header** saying they are a **communication record**, and not a certified or
      court-verified document.
- [ ] **Both clocks** are shown and **labelled**: the device time and the server time. They are
      not merged into one unlabelled timestamp.
- [ ] The event edited twice appears **as versions**: the original plus two edits, each with
      its own times.
- [ ] The **private** event appears **nowhere**, in either file.
- [ ] With **Also include → The parenting plan** ticked (the default) and a plan both parents
      have answered, both files end with a **Parenting plan** section: the "not the Ministry of
      Justice form" sentence, "as it stood when this record was generated", each question with
      **both parents' answers under their names** (never "Mom"/"Dad") and **Agreed** only where
      each ticked the other's current answer. Edit one answer on the other phone, export again:
      that question now reads **Not agreed**. Untick the box: the section is gone.
- [ ] In **airplane mode**, the plan section says it is **this phone's copy** and the record's face
      carries the incomplete line.
- [ ] **Private journal (MON-22, schema 41).** Settings → Family → **Private journal**: the screen
      says the entries stay on this phone and are never shared. Add two entries (one dated inside
      the export range, one outside it), edit one, swipe one away and tap **Undo** — it comes back
      with its text and day. On the **co-parent's phone** nothing of the journal appears anywhere.
      Export with **My private journal** unticked (the default): no journal section. Tick it: both
      files carry a **Private journal** section, after the expenses and before the plan, that
      starts with "one parent's own private notes … the other parent has not seen them", lists only
      the entry inside the range under your **name**, and shows **Last edited** for the edited one.
- [ ] The **share sheet** opens from both, and sending to e-mail or Drive delivers a file that
      opens.
- [ ] **Verification page (MON-16), once hosted · [CI]:** the `web` job already drives
      `web/verify/` in Chromium against the emulator — match, one-byte change, wrong ID, unknown ID,
      both languages, 375 px, nothing identifying on the page (see "What CI now covers"). Still
      yours: on a **computer**, open the hosted page over **https** (it refuses to fingerprint over
      plain http), choose the PDF **as it arrived by e-mail** — not a copy re-saved from a viewer —
      and see **Match** with the registration time and the period you exported; open the PDF in a
      viewer, print it to PDF again, and check the re-printed file: **No match**. Type the record ID
      printed in the PDF footer: **Registered**. Once in Safari or Firefox as well as Chrome.
- **If it fails:** `presentation/export/`, `domain/export/`, `data/export/ExportFileWriter.kt`,
  `data/versions/`; for the page, `web/verify/index.html` and `functions/export-receipts.js`.

---

## 7. Last: account deletion · 1P · **destructive**

Use the **throwaway account**, never A or B. If it is paired with A, §7.1 runs at the same time:
read it before you delete anything.

Preconditions:
- functions deployed from current `main` (it contains `6e4ec8e`);
- storage rules deployed;
- the throwaway account has authored an event with a photo, an expense with a receipt, a child
  with a medical photo and a pet with a photo (§3.10) — and, if it is paired, a vault document
  and a chat attachment (§5.5).

In the Firebase console → Storage, note the folders `event_images/<family>/<eventId>/`,
`receipts/<family>/<expenseId>/`, `medical_photos/<family>/<childId>/` and
`pet_photos/<family>/<petId>/` (`<family>` is `solo_<uid>` for an unpaired throwaway account).

- [ ] Settings → Account → **Delete account**, and confirm. It is a red confirming row, not a
      filled button.
- [ ] The app returns to sign-in, and the local data is wiped.
- [ ] In the Firebase console, the account's documents are gone from `events`, `expenses`,
      `child_info`, `pets` and `budgets`, and the Auth user is gone.
- [ ] **Storage:** all four folders are **gone**, and so is every `solo_<uid>/` folder of the
      throwaway account (L-4). Without `6e4ec8e` deployed, they stay behind,
      which is the defect that commit fixed. So are `family_documents/<familyId>/<docId>/` for
      the throwaway's own filings (the co-parent's stay). A conversation is deleted whole, with
      its `chat_attachments/<conversationId>/` folder, **only when nobody else in it still has an
      account**; a thread with a co-parent is kept for 30 days instead (L-5, §7.1).
- [ ] If the throwaway was paired, the co-parent's phone **keeps** records it had already
      downloaded (by design: nothing reconciles by absence). The web deletion page and the
      privacy policy say so.
- **If it fails:** tag `AccountDeletionService`; `firebase functions:log --only deleteAccount`;
  `deleteAuthoredFiles` and `deleteAccountDataImpl` in `functions/index.js`.

### 7.1 The departed parent's thread is kept for the one who remains (L-5) · 2P · **destructive**

Preconditions: the throwaway account **paired with A**, with a few chat messages both ways and
one attachment, on a second phone (or the same phone after §7's deletion, signing A back in —
the check reads A's side). Push notifications on for A.

- [ ] Delete the throwaway account as in §7.
- [ ] **One push, the specific one.** A's phone gets **one** notification, "Co-parent account
      deleted", naming the throwaway's name and a date 30 days from today, and saying to export
      the conversation before then. There is **no** "pairing removed" notification as well. The
      Firebase console's `notification_queue` holds one `coparent_account_deleted` entry for A and
      no `pairing_removed` one.
- [ ] **The thread stays, dated and read-only.** A's Chat tab still shows the thread with every
      message and the attachment (it still opens). Above it an attention banner reads "<name>
      deleted their account. This conversation will be deleted on <date>. Export it before
      then." with an **Export** button, and where the composer was, one line says messages can no
      longer be sent. The conversation document in the console carries `retainedUntilMillis`,
      `departedUid` and `departedName`.
- [ ] **Export it.** Tap **Export** on the banner. The export screen opens for **that** thread
      (route `export?thread=<conversationId>`), names the departed parent by name, and a CSV or PDF
      of the period contains the thread's messages under both names.
- [ ] A is unpaired (Settings → Family names no co-parent), yet the kept thread is still there.
- [ ] *(optional, operator)* **The sweep.** In the console, set the conversation's
      `retainedUntilMillis` to a time in the past and wait for the next 07:00 UTC run of
      `sweepRetentionLimits` (or check back after 30 days). **Expected:** the conversation, its
      messages and its `chat_attachments/<conversationId>/` folder are gone, and the banner and
      thread disappear from A's Chat tab.
- **If it fails:** tags `DepartedThreadSource`, `ChatViewModel`, `CoPlanlyMessaging`;
  `data/chat/DepartedThreadSource.kt`, `presentation/chat/DepartedThreadNotice.kt`,
  `retainOrDeleteConversations` and `sweepRetainedConversationsImpl` in `functions/index.js`,
  `firebase functions:log --only deleteAccount,sweepRetentionLimits`.

---

## After the session

- File every failure against its roadmap id (SEC-2, UX-13, REL-7, CQ-18, M-8, MON-6b, UX-15,
  MON-3, MON-17), or its legal-review id (L-2, L-5, L-12), with the logcat lines.
- Tick the boxes this session closes in `docs/ROADMAP.md`: SEC-2's caveat, UX-13, REL-7, CQ-18,
  MON-6b's mixed-version note, M-8's acceptance note, "M-4 (shipped, unseen)".
- Also correct the matching "never run on a device" lines in CLAUDE.md (items 20 and 24, and the
  cross-time-zone known issue), in the same commit.
