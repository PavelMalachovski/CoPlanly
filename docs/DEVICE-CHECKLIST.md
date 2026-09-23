# CoPlanly — device checklist

One script for the first session with a real phone. It covers everything the roadmap, CLAUDE.md
and `docs/AUDIT-2026-09.md` record as **written but never run on a device**. Written 2026-09-23
against `main` @ `44f9d66` plus the open integration branch `claude/charming-ritchie-d6uqz8`.

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

**Markers**

| Marker | Means |
| --- | --- |
| **1P** | One phone, one account is enough. |
| **2P** | Needs two phones signed in to two paired accounts at the same time. A **fallback** is given where one phone can cover part of it. There is no emulator: a fallback means signing out and back in on the same phone. |
| **3A** | Needs three accounts: you plus two co-parents. |
| **[branch]** | Only in a build that includes `claude/charming-ritchie-d6uqz8` (merged to `main`, or built from that branch). On `main` @ `44f9d66` the check does not apply yet. |
| **[#99]** | Lands with PR #99. Check it against the merged PR, because the details may differ. |
| **[CI]** | The `instrumented` CI job already exercises the mechanism on an emulator (table below). The phone still confirms it against real data and real services. |

**Warning: switching accounts wipes the phone's local data.** `AccountSwitchGuard` clears Room
when a *different* uid signs in. Records that already synced come back from the cloud. Records
that never synced, private events included, are gone. For that reason every one-phone fallback
that switches accounts comes after the checks that need local data.

**Logcat, used throughout.** Keep one terminal running this filter:

```bash
adb logcat -v time EncryptedDatabase:V DatabaseKey:V SyncService:V SyncWorker:V ChatMirror:V \
  MessageRepo:V ChatViewModel:V CoPlanlyMessaging:V PetsViewModel:V ChildInfoViewModel:V \
  CoPlanlyUpload:V CoPlanlyReceiptScan:V AccountDeletionService:V SelectedFamily:V \
  CustodyModelRepo:V HomeViewModel:V UserRepository:V AndroidRuntime:E *:S
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
`FakeFirebaseModule`, Room real) runs these on every Android pull request. A check they cover is
marked **[CI]** below: CI saw the mechanism work, so on the phone it is a quick confirmation, and
a failure there points at something the emulator does not have — real Firebase, real data, a
Play install, a vendor skin.

| Test (`app/src/androidTest/...`) | Covers | What only the phone still adds |
| --- | --- | --- |
| `presentation/common/PickerDatesTest`, `LocalDatePickerDialogTest` | §3.1: every picker's conversion and both picker composables, tapped, in Prague, Kiritimati (+14), Los Angeles and Pago Pago (−11) | Each *screen* saving what its picker returned, and a stored date staying put across a zone change |
| `presentation/settings/PerAppLocaleTest` | §3.6: `setApplicationLocales` to cs/de/ru/uk renders Settings in that language | The Settings row itself, Android 13's system setting, and §4.2 (a Play install) — never CI |
| `data/export/ExportFileWriterTest` | §6: a CSV (RFC 4180, statement first, formula guard, both clocks, no private event) and a PDF that `PdfRenderer` opens, written through the real writer; the share intent's `FileProvider` URI and read-only grant | Real revisions from the server, the share sheet, and a spreadsheet or PDF app opening the file |
| `presentation/navigation/MainNavigationSmokeTest` | A signed-in launch visiting Home, Calendar (month/week/day), Chat, Expenses and Settings without a crash; bottom bar on the tabs only; icon-only controls named and ≥ 48 dp | Everything that needs data, a co-parent or a server; TalkBack itself (§3.9) |

---

## 0. Before the session: owner ops

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
  1. [ ] `firebase deploy --only functions`. Deploy from a commit that contains `6e4ec8e`
         ([branch]), or account deletion will not remove Storage files (§7).
  2. [ ] Invoke `backfillFamilyDocuments`.
  3. [ ] Invoke `backfillRecordFamilyIds`.
  4. [ ] `firebase deploy --only firestore:rules,firestore:indexes`.

  If you run step 4 before step 3, each co-parent's expenses look empty on the other phone
  until step 3 has run.
- [ ] **REL-3 storage:** `firebase deploy --only storage`. Until this runs, **every pet and
      medical photo upload is refused**, because the bucket still enforces the July rules. That
      is a known failure, not a finding (§3.10).
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
| **B** | Current debug | `main` after the integration PR merges, or `claude/charming-ritchie-d6uqz8` | `./gradlew clean assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
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
on every run. The boxes below are marked **[CI]** where an emulator already proves the same thing,
so a failure there on a phone points at something the emulator does not have. What CI does not
cover is the reason this section still comes first:

- the plaintext file there was written by the **current** Room schema, not by an older build and
  then taken through the migration chain in the same launch as the conversion;
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
    the private event, the medical profile, and the chat history.
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
      (recovered twice, by a second `DatabaseKey`, and on disk as soon as `mint` returns); the
      process death between launches is the part left to the phone.
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
- [ ] **Privacy policy link hidden while the URL is blank** [branch].
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
  - [branch] The splash shows its finished frame and exits at once, with no 700 ms hold, and
    the splash and Auth logo do **not** pulse (`rememberReducedMotion` in
    `presentation/theme/Motion.kt`).
  - Without the branch, the hold and the pulses remain (a known item, AUDIT §3.3 point 8).
- [ ] Not a pass/fail check: write down anything in AUDIT §3.3 points 1–7 that looks wrong on the
      phone. Those points are the three expand/collapse styles, the calendar's four ways of
      moving, the Settings chevron, the banners without enter/exit, forms scaling from 0.8,
      the bottom sheets and the one bouncy spring.

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
      Record whether any school vacation actually appears: AUDIT §4.2 says `VacationBanner` has
      no caller.
- [ ] **Slovakia:** 1 Nov drawn. **15 Sep 2026 and 17 Nov 2026 not drawn** (both are working
      days by law in 2026). The note says school vacations are shown and spring holidays are not.
      **Day view on 29 Oct 2026** is labelled "Jesenné prázdniny" (app in Slovak or matching
      language) or "Autumn vacation"; **Day view on 17 Feb 2027** has no label (spring holidays
      are regional and not drawn).
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
- [ ] **School vacations are not marked on the month grid** for any country, Czechia included —
      record it, do not fail it: the month banner was removed on purpose (ROADMAP MON-13, "Where
      they show").
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
- [ ] **Home today card** [branch]: under "whose day it is", a line reads
      "15:00–19:00 · contact with <name>" in the window parent's colour. Without the branch,
      Home does not mention windows. **(screenshots)** `home_today_card` and
      `calendar_month_grid` show the line and the Month corner from fixed data; on the phone,
      check that a window you *saved* reaches them.
- [ ] The existing MON-6 midweek toggle still behaves as before (a whole day with overnight).
      Its warning now points to contact windows.
- [ ] **2P, mixed versions:** one phone on build A (`f6bab3e`, which has no windows) and one on
      B. Try a swap and a proposal each way. Windows survive a write from the newer phone. A
      write from the old phone may drop them, and that is allowed. A proposal or swap from the
      new phone never *changes* them. There is no one-phone fallback: the rules suite
      (`custody-models.test.js`) is the substitute.
- **If it fails:** `presentation/custody/ContactWindowsSection.kt`, `MonthView.kt`,
  `DayWeekView.kt`, `CustodyResolver.contactWindowsResolver` [branch]; tag `CustodyModelRepo`.

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

### 3.7 Push opt-out switch · 1P, full check 2P

- [ ] Settings → App → **Push notifications** off. In the Firebase console → Firestore →
      `users/{A's uid}`, `fcmToken` is removed or empty.
- [ ] Turn it on. The token is back. If the OS permission is denied, the switch shows off.
- [ ] **2P:** with push **on**, B creates an event and a push arrives on A. Turn A's push off,
      have B create another event, and no push arrives. Turn it on again and pushes arrive.
- [ ] **2P:** sign A out. B's next event must **not** arrive on A's phone
      (`FcmService.unregisterToken` runs on sign-out).
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

### 3.10 Pet and medical photo upload · 1P

- [ ] Pets → a pet → add a photo. Child → medical → add a photo.
  - **Before** `firebase deploy --only storage`: **expected to fail**. The logcat shows
    `PetsViewModel: Uploading a pet photo to pet_photos/<id> failed` or
    `ChildInfoViewModel: Uploading a medical photo to medical_photos/<id> failed` with a
    permission error. That confirms the diagnosis; it is not a new bug.
  - **After** the deploy: both succeed, and the files appear in the console under
    `pet_photos/<petId>/` and `medical_photos/<childId>/`.
- [ ] While here, attach a **receipt photo** to an expense and a **photo to an event** (tag
      `CoPlanlyUpload`). §7 needs the same four kinds of file on the throwaway account.

### 3.11 Seasonal schedules and holiday fairness (MON-14, MON-20) · 1P, proposal check 2P

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
- [ ] **Calendar feed (MON-17), if deployed:** an iPhone subscribed to the feed shows the layer's
      custody bars on its dates after the next refresh.
- **If it fails:** tag `SeasonalScheduleVM` / `CustodyModelRepo`; `presentation/custody/Seasonal*`,
  `HolidayFairnessCard.kt`, `domain/custody/SeasonalLayer.kt`, `HolidayFairness.kt`,
  `firestore.rules` `seasonalLayersKeptOrDropped`.

---

## 4. Release-build checks

### 4.1 REL-7: R8 and Gson on a device · 2P, fallback 1P

A green `assembleRelease` proves R8 ran. It does not prove that Gson still finds its field names.
That defect has shipped once before.

Preconditions: install build C: `adb uninstall app.coplanly && adb install app-release.apk`. Do
the same on B's phone if you have one.

- [ ] Sign in as A. Save a child's **medical profile**: blood type, two allergies, an
      intolerance, a hereditary condition, a vaccination with a date. Also add an activity and an
      emergency contact.
- [ ] Firebase console → `child_info/{id}`: `medicalProfile` has **readable keys**
      (`bloodType`, `intolerances`, `hereditaryConditions`, `vaccinations`), not `a`, `b`, `c`.
      Each list is non-empty.
- [ ] **2P:** on B's phone (also on build C), the same child's medical profile is **complete and
      non-empty**.
- [ ] **Fallback (1P):** run `adb shell pm clear app.coplanly`, sign in as A again, and let it
      sync. The medical profile comes back from Firestore complete. That covers both directions
      of the Gson mapping on a release build.
- [ ] A Google Calendar import on the release build works. It uses the `@Key` models.
- [ ] **Telemetry end to end** (a release build has `ENABLE_ANALYTICS=true`):
  - Run `adb shell setprop debug.firebase.analytics.app app.coplanly` and open Firebase
    console → Analytics → DebugView.
  - While consent is **declined**, no events arrive.
  - Grant consent in Settings → Usage statistics, and events start.
  - Decline again, and they stop.
- [ ] While on the release build, glance at the palette, the switcher and the second-co-parent
      invite (M-4, shipped but never seen).
- **If it fails:** `app/proguard-rules.pro` (the `-keepclassmembers ... { <fields>; }` rules) and
  `tools/check-r8-mapping.js`. A new Gson type without a rule is the likely cause.

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

> **[CI e2e]** `TwoParentChatTest` (the `e2e` job, two accounts on the Firebase emulators, UTC+14 vs UTC−11) proves the unread count, DELIVERED and READ agree across zones. The phones are still needed for what is drawn — the badge, the ticks, displayed times, on-screen order — and for push delivery.

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

> **[CI e2e]** `MultiFamilyTest` proves the data side: with a second co-parent selected, a new event gets that family's audience and `familyId` and its announcement goes to that thread, and none of it reaches the first co-parent. The switcher UI, the chat tab re-keying and pushes stay manual.

Preconditions: A is paired with **both** B and C (two families). Invite C from Settings → Family.

- [ ] Home and Expenses show a **switcher chip** beside the gear.
  - It names the family on screen by its co-parent.
  - It is absent on Calendar and Chat.
  - For an account with one co-parent it is absent everywhere.
- [ ] The chip and the Settings row open the same dialog. Switching families changes Home,
      Calendar and Expenses to that family's records.
- [ ] **[branch] Chat follows the selected family.** After switching to C's family, the Chat tab
      opens **C's** thread and the badge counts C's unread messages.
  - Switch back to B: B's thread and B's badge.
  - Messages B sent while you were on C's family arrive once you switch back.
  - Without the branch, chat stays on the first co-parent. That is the documented old
    behaviour.
- [ ] A **push** from the family *not* on screen, tapped, switches to that family first and then
      opens the target.
- [ ] **[branch] The cross-family dot.** With B's family on screen, have **C**:
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
  `data/family/OtherFamiliesSignals.kt` (tag `OtherFamiliesSignals`) [branch].

### 5.3 Also worth doing while two phones are paired · 2P

> Real FCM delivery cannot be emulated: the e2e job sees the `notification_queue` document written, never the push arrive.

- [ ] UX-15 with both parents on non-default colours (§3.3).
- [ ] MON-6b with mixed versions (§3.5).
- [ ] REL-7 on both phones (§4.1).
- [ ] The push opt-out end to end (§3.7).

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

## 6. Export (MON-3), landing in PR #99 · 1P [#99]

**Verify against the merged PR; the details may differ.** Expected: event versions, plus a PDF
and CSV export started from Settings.

**[CI]** `ExportFileWriterTest` writes both files on the emulator from a fixture (two revisions
with both clocks, a private event, a formula-looking message, an expense) and checks the CSV
parses as RFC 4180 with the statement first, the formula guard and no private event, that the
PDF opens in `PdfRenderer`, and that the share intent carries the `FileProvider` URI with a
read-only grant. Still yours: revisions that came from the server, the share sheet, and a real
spreadsheet and PDF app.

Preconditions:
- a build with PR #99 merged;
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
- [ ] The **share sheet** opens from both, and sending to e-mail or Drive delivers a file that
      opens.
- **If it fails:** read the PR's own description for the file and tag names.

---

## 7. Last: account deletion · 1P · **destructive**

Use the **throwaway account**, never A or B.

Preconditions:
- functions deployed from a commit containing `6e4ec8e` [branch];
- storage rules deployed;
- the throwaway account has authored an event with a photo, an expense with a receipt, a child
  with a medical photo and a pet with a photo (§3.10).

In the Firebase console → Storage, note the paths `event_images/<eventId>.jpg`,
`receipts/<expenseId>.jpg`, `medical_photos/<childId>/` and `pet_photos/<petId>/`.

- [ ] Settings → Account → **Delete account**, and confirm. It is a red confirming row, not a
      filled button.
- [ ] The app returns to sign-in, and the local data is wiped.
- [ ] In the Firebase console, the account's documents are gone from `events`, `expenses`,
      `child_info`, `pets` and `budgets`, and the Auth user is gone.
- [ ] **Storage:** all four paths are **gone**. Without `6e4ec8e` deployed, they stay behind,
      which is the defect that commit fixed.
- [ ] If the throwaway was paired, the co-parent's phone **keeps** records it had already
      downloaded (by design: nothing reconciles by absence). The web deletion page and the
      privacy policy say so.
- **If it fails:** tag `AccountDeletionService`; `firebase functions:log --only deleteAccount`;
  `deleteAuthoredFiles` and `deleteAccountDataImpl` in `functions/index.js`.

---

## After the session

- File every failure against its roadmap id (SEC-2, UX-13, REL-7, CQ-18, M-8, MON-6b, UX-15,
  MON-3), with the logcat lines.
- Tick the boxes this session closes in `docs/ROADMAP.md`: SEC-2's caveat, UX-13, REL-7, CQ-18,
  MON-6b's mixed-version note, M-8's acceptance note, "M-4 (shipped, unseen)".
- Also correct the matching "never run on a device" lines in CLAUDE.md (items 20 and 24, and the
  cross-time-zone known issue), in the same commit.
