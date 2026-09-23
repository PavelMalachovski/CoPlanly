# CoPlanly — roadmap and backlog

One document. It replaces `docs/BACKLOG.md` (everything known to be missing, broken or worth
improving) and `docs/CoPlanly/MVP_phases.md` (the original three-phase feature plan), which were
merged here on 2026-08-25 and deleted. Audits written before that date cite them by their old
names; the content is all below.

It answers three questions, in this order:

1. **Where can each remaining piece of work actually be done** — a Claude session in the cloud, or
   your own computer and phone? §1.
2. **What did the three MVP phases ask for, and what is actually built?** §2.
3. **What is left, item by item, and why** — §3 through §9, with the dependency order in §10.

Last updated: 2026-08-25, after PR #76 (multi-family, M-1 … M-4). The reasoning behind most items
lives in `docs/AUDIT-2026-08.md` under the § numbers cited; `docs/DESIGN-multi-family.md` is the
plan of record for the `M-*` items.

> **Companion document:** `docs/LAUNCH-PLAYBOOK.md` takes the release blockers (§3), MVP 3 (§2) and
> monetisation (§7) and turns them into an order — the Google Play publication runbook, the store
> listing and professional-channel drafts, the free/paid line, and a 90-day sequence. This document
> stays the plan of record for *what* is open; that one is *in what order, by whom, and in whose
> words*.

## How to read this

**Every item has a stable id** (`REL-3`, `SEC-2`, `CQ-7`, `UX-11`, `MON-5`, `FAM-4`, `M-6`). Use it
in commit messages and PR titles, so an item can be traced from the plan to the diff without
matching prose. Ids are never reused: an item that turns out to be done keeps its number and gains
**DONE**, so a reader who finds it cited somewhere else can still find out what it was.

| Priority | Means |
| --- | --- |
| **P0** | Ships broken, loses data, or blocks the release outright. Do before anything else. |
| **P1** | A user hits it in normal use and cannot work around it. |
| **P2** | Real, but survivable and not on the launch path. |
| **P3** | Hygiene. Do it while touching the area anyway. |

Sizes are **S** (a day or less), **M** (a few days), **L** (a week or more).

Every open item also carries a **Where** line:

| Mark | Means |
| --- | --- |
| ☁️ **Cloud** | A Claude session does it end to end — Kotlin, rules, Cloud Functions, docs, tests. CI is the Android compiler and the unit-test runner. |
| ⚙️ **Cloud + a CI job that does not exist yet** | The work itself is code, but proving it needs a Gradle task or an emulator no workflow runs today. Add the job once, and it becomes ordinary cloud work. |
| 👁 **Cloud writes it, a device answers it** | It can be written and compiled here; whether it is *right* is only visible on a phone, in a console, or against a real Firebase project. |
| 💻 **Yours only** | A keystore, a console, a deploy, a second phone, a lawyer, a phone call. No session can do it. |

**Why the line exists.** The Claude sessions that do most of this work run in a container with **no
Android SDK**: Gradle cannot be invoked here at all, so nothing Android is compiled, run or seen
locally. What *is* verifiable in the session is the Firestore rules suite against the emulator
(`firestore-tests/`, needs a JDK 21+) and the Cloud Functions suite (`functions/`, mocha + eslint).
Everything Kotlin is proved by **CI** — `assembleDebug`, `testDebugUnitTest`, `lint`, `detekt`
(a gate again since **CQ-12**), `assembleRelease` so R8 runs, and — since September 2026 — the
`instrumented` job, an API 30 emulator running `connectedDebugAndroidTest` with Firebase replaced
in the graph and Room left real (this sentence used to say no emulator job existed). Besides the
migration tests it now runs a first set of device checks (September 2026): the date pickers in four
time zones, the per-app language switch, the export's files and share intent, a signed-in walk of
the main screens with a basic accessibility sweep — `docs/DEVICE-CHECKLIST.md` marks what they cover
**[CI]**. And no session holds
Firebase or Play credentials: every `firebase deploy`, every console change and every callable
invocation is yours.

---

## 1. Where the remaining work can be done

> **Device session:** every 👁 and 💻 item below that a phone can answer — SEC-2's conversion of an
> existing database, UX-13, REL-7, CQ-18, M-8, MON-6b, UX-15, the date-picker fix, the language
> split — is scripted as one ordered pass in `docs/DEVICE-CHECKLIST.md`, with the ops that must run
> first (REL-1, REL-3) and a one-phone fallback where one exists.

### ☁️ Cloud — a session can take these now

| Id | What | Pri | Size |
| --- | --- | --- | --- |
| **M-5** | Multi-family cleanup: delete `partnerId`, `User.role`, `Event.sharedWith`, `isPartnerOf` — **after** the ops steps in REL-3 | P2 | M |
| **M-8** | M-4's last leftover, and now a small one: chat follows the selected family and the switcher carries a cross-family chat dot (both done, September 2026); left is the same signal for change requests and custody proposals | P2 | S |
| **CQ-17** | Six dependencies worth moving | P3 | S |
| **MON-2** | Market facts checked (23 Sep 2026): **app2us has an Android build**; left: mediator count, ARPU, Facebook groups, app2us price on a phone | P0 | S |
| **MON-3** | The export ships, ungated; left: a PDF read on a device, and the paywall with MON-11 | P2 | S |
| **MON-4** | Decided and built; left: `Event.updatedAt` to epoch millis (answer 3's last compared field) | P1 | M |
| **MON-5** | The plan ships; swapping in the Ministry's own wording needs the form itself | P1 | S |
| **MON-6b** | Contact windows ship (schema 36), on the grid and on Home's today card; left: verifying the mixed-version path on two phones | P2 | S |
| **MON-8** | Bakaláři / EduPage school import — the parsing, once you supply a real export | P2 | L |
| **MON-11** | Payments (MVP 3) — the entitlement model, after MON-1 decides the price | P2 | L |
| **MON-12** | Intelligent suggestions (MVP 3) — behind SEC-1's proxy, never with a key in the client | P3 | M |
| **MON-13** | The tables and Germany's Länder are done (five countries; Ukraine's holidays are suspended by martial law) — left: school vacations outside Czechia, and whether Austria's patron-saint days are drawn at all | P2 | M |
| **FAM-4** | Custody per child | P2 | L |
| **MON-14** | Seasonal schedule layers (summer / school holidays override the base pattern), with "fill from school holidays" | P1 | M |
| **MON-15** | Search in chat — local Room FTS, never a server index | P1 | S |
| **MON-16** | Verifiable export: record ID + SHA-256 registered server-side, verified in the browser | P1 | S |
| **MON-17** | ICS calendar feed for a co-parent on an iPhone (secret revocable token, no private events) | P1 | M |
| **MON-18** | Free, expiring, two-consent access for a mediator or lawyer | P1 | M |
| **MON-19** | A pause before sending (undo window + a lexical nudge, no AI) | P2 | S |
| **MON-20** | Holiday fairness at a glance (who has which holidays, nights per parent) | P2 | S |
| **MON-21** | From the agreed parenting plan to a proposed schedule | P2 | M |
| **MON-22** | A private, local-only journal that can be attached to an export | P2 | M |
| **REL-4 (drafting)** | Done as far as the code can answer: every placeholder left is a fact only the owner has, listed at the top of each document. The deletion page and the privacy link in the app are written | P0 | S |

### ⚙️ Cloud, but a CI job has to be built first

| Id | What | Why it needs a job |
| --- | --- | --- |
| **CQ-1** | An instrumented job for migration tests — the schema export and the guard against a new gap are done | Running a migration test needs an Android emulator that no workflow starts. Worth adding at v34, when there is a second schema to migrate between. |

### 👁 Cloud writes it, only a device or a console can say whether it is right

| Id | What | What has to be seen |
| --- | --- | --- |
| **SEC-1 §1** | Storage rules keyed on Firestore state (cross-service rules — the "this needs the proxy" claim was a factual error) | The **Storage emulator does not resolve cross-service calls**, so `firestore-tests/` cannot cover it. Settle the verification story — a staging bucket against a real project — before writing the rule. |
| **SEC-5** | `androidx.security:security-crypto` is on an alpha holding OAuth tokens | A dependency bump compiles in CI; whether tokens survive it is a sign-in on a real device. |
| **UX-8** | The second half: two surfaces colour a chip from two different sources | An owner's answer to "what does a chip's colour mean" — the event's owner, or whose day it falls on. |
| **UX-13** | Light theme is unverifiable rather than incomplete — the cloud half is done (night window background, light+dark previews on the main screens' pieces) | Whether a dark cold start still flashes: only a device shows the window before Compose's first frame. |
| **FAM-5** | The event chip does not say who it is about | Chips are single-line with ellipsis and every colour channel is spent. Worth an owner's eye on a real device rather than a treatment invented blind. |
| **MON-3 (shipped, unseen)** | The PDF export and the share sheet | `PdfDocument` drawing, Cyrillic and Czech glyphs in the default typeface, page breaks, and whether the share sheet hands the file to a mail app — the layout is unit-tested, the drawing is not. |
| **M-4 (shipped, unseen)** | The colour palette, the family switcher, the second-co-parent invite | Kotlin compiled in CI; nobody has looked at it. |
| **M-8 (chat, shipped, unseen)** | Chat, its badge and `ChatMirror` follow the selected family | Unit tests pin the re-key; only an account with two co-parents on real phones shows a switch landing the Chat tab on the other thread, the badge moving with it, and messages from the family *left* arriving again after switching back. |
| **M-8 (dot, shipped, unseen)** | The switcher chip and dialog show a dot when a family not on screen has chat news | Three accounts (a parent and two co-parents) on at least two phones: the co-parent of the family *not* on screen sends, the dot appears on the chip and on that row within seconds; opening that family's thread clears it; sending from the family on screen never raises it; a one-family account shows exactly what it did. |

### 💻 Yours only — no session can do these

| Id | What | Note |
| --- | --- | --- |
| **REL-3 ops** | `firebase deploy --only functions` → invoke `backfillFamilyDocuments` → invoke `backfillRecordFamilyIds` → `firebase deploy --only firestore:rules` | **The order matters.** PR #76's isolation is inert until this runs, and running the rules deploy before the record backfill leaves each co-parent's expenses looking empty on the other phone. The functions deploy also ships the `onFamilyCreated` re-stamp trigger and the `sweepLapsedCalendarFriends` schedule. `functions/README.md` has the runbook. |
| **MON-4 deploy** | `firebase deploy --only firestore:rules` (the `event_versions` block) and `firebase deploy --only functions` (account deletion reaches revisions); trigger the Regenerate workflow for `37.json` | Until the rules are deployed every revision upload is refused and stays queued on the phone — nothing is lost, but nothing is recorded server-side either. The schema export is the one artefact only a machine with an Android SDK can produce; CI's schema guard fails until it is committed. Fold the rules deploy into REL-3's order: after the record backfill, like every rules deploy. |
| **REL-3 storage** | `firebase deploy --only storage` | One command that fixes a live bug: every pet and medical photo upload is refused today because the bucket still runs the July rules. |
| **REL-1** | Firebase console, Google Cloud console, a fresh `google-services.json`, the debug and release SHA-1 | A local build fails until this is done — deliberately, since `applicationId` changed to `app.coplanly`. |
| **REL-2** | Generate the release keystore and back it up in two places | The single most irreversible item in this document. |
| **REL-4 (legal)** | Fill the "Owner must fill" tables (identity, contact, Firestore region, dates, liability, law); a lawyer reads the drafts; the three pages get hosted; the URL goes into `publishedPrivacyPolicyUrl` | This app processes a child's health data. No template survives that unread. |
| **REL-6** | Play Console: Data Safety, listing, screenshots, content rating, a closed track with **real co-parent pairs** | This product cannot be tested by one person. |
| **REL-7** | Install a release build and confirm a child's medical profile reaches the co-parent non-empty | The one test CI cannot run: a green `assembleRelease` proves R8 ran, not that Gson still finds its field names. |
| **CQ-16** | Digital Asset Links | Needs a domain you own — the same one REL-4 needs. |
| **CQ-18** | Cross-time-zone chat on two phones | Two devices, two zones. Unit tests already drive the logic; this is the acceptance run. |
| **MON-1** | Price, unit (family, not seat), and what the free tier contains | A decision, and it shapes the code that follows. |
| **MON-9** | Distribution: mediators, Cochem courts, OSPOD, NGOs | Phone calls and meetings. A session can draft the material; it cannot make the call. |
| **MON-8 (input)** | A real Bakaláři or EduPage export | The parser is cloud work; it needs one actual file to be written against. |

### If you want a shortlist of what to hand a session next

In this order, and each is genuinely finishable in the cloud:

1. **MON-4's last item** — `Event.updatedAt` to epoch millis, the one compared field answer 3
   still reaches. Read `domain/custody/CustodyTimestamp.kt` first; the wire form keeps its name and
   type.
2. **MON-3's next slice** — the parenting plan is the other document worth exporting, and it is
   not in the record yet.
*(Everything that headed this list — **MON-4** and **MON-3**'s first version, **M-6**, **CQ-19**, **CQ-12**, **CQ-1**'s bleeding half,
**CQ-5**, **CQ-6 + CQ-8**, **SEC-2**, the three honesty gaps **CQ-20**, **UX-17**, **UX-18**, and
**UX-15**, which un-hid the colour picker, the **MON-13** holiday tables, and **CQ-13**'s ViewModel
tests — is done. **SEC-2**
carries one caveat that is not a cloud task: see its entry.)*

---

## 2. The three MVP phases, re-baselined against the code

This is the original `MVP_phases.md` matrix with two columns added: what is actually built, and
the item id that now carries whatever is left. Checked against the tree on 2026-08-25, not
remembered. **This closes MON-10** — the roadmap said "MVP 2 is next" for months after MVP 2 had
shipped, and a plan that describes work already done is worse than no plan.

### MVP 1 — Foundation and core calendar · **complete**

| Feature | Original detail | Diff | Pri | Status |
| --- | --- | --- | --- | --- |
| Setup custody model at start | Clear. Also %-wise | M | High | **Done.** `CustodyModel` with four presets (`EVERY_OTHER_WEEKEND` added by MON-6), and the %-wise half is the agreed split ratio in `family_settings/{pairId}` |
| Month first, week next, day third | No 3-day view | S | High | **Done.** `MONTH, WEEK, DAY`; there is no 3-day view and there should not be |
| Switch between "You" and "Him" view | In Day view, besides Week/Month | — | High | **Done.** `ParentFilter` in the calendar filter sheet |
| Have mom and dad selected at once | Show mutual views at the same time | M | High | **Done.** `ParentFilter.BOTH` is the default |
| Event type — default + add your own | Predefined and manual filters | L | High | **Done.** Five defaults plus user-defined types created in the filter sheet |
| Reoccurrence | Clear | S | High | **Done.** `RecurrenceExpander`; CQ-4 removed the two-year cliff |
| Confirm pickup | Other side sees it is picked up | S | High | **Done.** `pickupConfirmedBy` / `pickupConfirmedAt` |
| Notifications | 30 min or 1 h before pickup | M | High | **Done.** `ReminderScheduler` + WorkManager; the permission is asked contextually, never on cold start |
| Holidays and vacations by country | Clear | S | High | **Done for holidays, partly for vacations.** The country is asked for and stored (MON-13), and Czechia, Slovakia, Germany (with a Land setting for its state holidays), Austria and Russia each have a computed table verified against the Python `holidays` library; Ukraine's holidays are suspended under martial law and the picker says so. School vacations exist for Czechia only — the others are regional |
| Add events only you can see | Related to switching views | S | High | **Done.** `isPrivate`, filtered out of every sync path |
| Sat/Sun a different colour | Clear | S | High | **Done.** `DayCellFills` draws the weekend as a base layer under custody, never instead of it |

### MVP 2 — Communication, receipts and dashboards · **complete**

| Feature | Original detail | Diff | Pri | Status |
| --- | --- | --- | --- | --- |
| Receipts | Extra section | L | High | **Done.** On-device OCR (ML Kit → `ReceiptParser`); no receipt text or photo leaves the device |
| Change requests | Shown as notification and in the dashboard | M | High | **Done**, and the honesty gap that outlived it is closed too — a request that has not left the phone says Queued (**CQ-20**) |
| Weekly summary | Dashboard of next week's mutual activities | M | High | **Removed** (commit `340af30`). Home's seven-day card is what survives of it; whether that satisfies this row or the row reopens is an owner call |
| First screen updates | Last 5 changes both parents can see | L | Medium | **Done.** The recent-changes feed on Home |
| Structured chat → change request | Button, new date, notification | M | Medium | **Done** |
| Attach image to the event | Clear | L | Medium | **Done.** `Event.imageUrl` into `event_images/` — which is one of the two Storage prefixes the **live** bucket still covers; `pet_photos/` and `medical_photos/` are refused until REL-3's storage deploy runs |

### MVP 3 — Automation and integrations · **not started, and repriced**

Every line here was **Low** priority. Two of them are the strongest items in the whole plan, and
one of them should probably not be built at all.

| Feature | Original detail | Diff | Pri | Now |
| --- | --- | --- | --- | --- |
| Import (Bakaláři / EduPage) | From a PDF export, broken into events | XL | Low | **MON-8, P2 · L.** Mispriced at Low: every Czech parent's school schedule lives in one of those two systems, it solves cold-start, and no US competitor will build it. Document understanding makes XL smaller than when the line was written |
| Payments | Clear | XL | Low | **MON-11, P2 · L.** Gated on MON-1's pricing decision — and **Onward closed on 8 October 2024** built entirely on expense splitting and payments. Expense reimbursement does not carry a product on its own |
| Exports to PDF/CSV | Summary / punctuality. CSV preferred | M | Low | **MON-3, shipped (ungated).** Backwards at Low: this is the **first paid feature**. Willingness to pay concentrates on documentation you can hand to a lawyer. Built after **MON-4** was decided; the paywall waits on MON-1 and MON-11 |
| Intelligent suggestions | Based on past schedules | M-L | Low | **MON-12, P3 · M.** Only behind SEC-1's proxy — the AI subsystem was deleted with its key (MON-7), and it comes back as *one* feature, never eight |
| Time setting by dragging | Whole event by 15 min, corners by the minute | S | Low | **UX-16, done.** Move by 15 minutes and resize were already in `DayWeekView`; the corners now move by the minute. Needs a thumb to judge |

### What none of the three phases contains

The MVP plan is a feature plan. It has no line for the release blockers (§3), the security work
(§4), or the multi-family model (§9) — and **none of MVP 3 is worth starting before §3 is done**,
because an unpublished app earns nothing from any of it.

---

## 3. [REL] Release blockers — the app cannot be published until these are done

None of these is engineering. They are decisions, accounts, deploys, and a lawyer.

### REL-1 · **decided, half done** · `applicationId` is now `app.coplanly`

**Where:** 💻 yours only — the code half is committed.

Decided and changed in code while it still could be. After the first Play upload an
`applicationId` can never change — a different one is a different app, with no upgrade path for
anyone who installed the first. The old id said `com.coparently.app` while the product is CoPlanly
and the deep-link scheme is `coplanly://`.

`namespace` stays `com.coparently.app` on purpose: it is the Kotlin package and therefore where `R`
and `BuildConfig` are generated. Renaming it would touch every file in the tree for no user-visible
gain, and the two are allowed to differ.

- [x] Change `applicationId` in `app/build.gradle.kts`.

**The rest needs the consoles, and until it is done a local build fails.** That is deliberate: the
Google Services plugin matches `google-services.json` on the package name and will report *"No
matching client found for package name 'app.coplanly'"*. CI is unaffected — `google-services.json`
is gitignored, so the plugin is not applied there.

- [ ] **Firebase console** → project `coparently-a39c9` → Add app → Android → package name
      `app.coplanly`. Register it alongside the existing app rather than deleting that one.
- [ ] Download the new `google-services.json` and replace `app/google-services.json`. One file can
      hold both clients, so one download covers it.
- [ ] **Google Cloud console** → Credentials → the Android OAuth client used for Calendar: set the
      package name to `app.coplanly` and re-enter the debug SHA-1 (`keytool -list -v -keystore
      ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`).
      Google Sign-In and the Calendar scope both stop working otherwise, and the failure looks like
      a generic sign-in error rather than a config one.
- [ ] Add the **release** SHA-1 once **REL-2** produces a keystore.
- [ ] Re-check that pairing, guest and chat deep links still open the app. They use a custom scheme
      rather than the applicationId, so they should be unaffected — confirm rather than assume.
- [ ] Uninstall the old build from any test device first: to Android these are two different apps.

### REL-2 · **PARTLY DONE** · P0 · Signing configuration and the keystore

**Where:** 💻 yours only. The ~20-line `signingConfig` block can be prepared in the cloud; nothing
else here can.

**A correction to what this item used to say.** It described the keystore as "the single most
irreversible item in this document", on the reasoning that losing it means the app can never be
updated again. That is true of the **legacy self-signing** model and not of the one a new app is
placed in: a new Play app is enrolled in **Play App Signing** and uploads an AAB, so Google holds
the app signing key and you hold only an *upload* key — which can be reset through Play support if
lost. Back it up anyway (a reset costs days), but the genuinely irreversible decisions are the
`applicationId`, the Firestore region, and the first published price tier's currency set. Verify the
enrolment rule in the console rather than on this sentence; Play's requirements move.

- [x] The `signingConfig` block is written and merged. It reads `COPLANLY_RELEASE_STORE_FILE`,
      `COPLANLY_RELEASE_STORE_PASSWORD`, `COPLANLY_RELEASE_KEY_ALIAS` and
      `COPLANLY_RELEASE_KEY_PASSWORD` from `~/.gradle/gradle.properties` or the environment, never
      from a tracked file. **All four or none**, and with none the release build still succeeds
      and comes out unsigned — CI runs `assembleRelease` on every pull request and must keep doing
      so without a key. `docs/LAUNCH-PLAYBOOK.md` §2.3 has the `keytool` line.
- [ ] Generate the upload keystore, set those four properties, and back it up in two places.
- [x] `./gradlew bundleRelease` is scripted: `.github/workflows/release.yml` (September 2026) is a
      manual `workflow_dispatch` that decodes the keystore and `google-services.json` from
      repository secrets (`COPLANLY_UPLOAD_KEYSTORE_BASE64`, the three `COPLANLY_RELEASE_*`
      passwords, `GOOGLE_SERVICES_JSON_BASE64`), builds the AAB, runs `check-r8-mapping.js`, and
      uploads the bundle and R8's `mapping.txt` as artifacts — the mapping kept, or Crashlytics
      stack traces from that build are unreadable. It says in its summary whether the bundle came
      out signed. **Never run**: the secrets do not exist until the keystore does.
- [ ] Put the five secrets in the repository, run the workflow once, and install what it built on
      a phone before anything is uploaded (REL-7).

### REL-3 · P0 · Deploy the rules, the functions, and the storage rules

**Where:** 💻 yours only. Everything being deployed was written and tested in the cloud; none of it
does anything until this runs.

Everything server-side from both audits is **inert until this runs** — including the fix for a live
full-calendar disclosure (audit §2.1) and the whole of PR #76's family isolation.

**The multi-family ops sequence, in this order** (`functions/README.md` has the detail):

1. [ ] `firebase deploy --only functions`
2. [ ] Invoke `backfillFamilyDocuments` — every live pair gets `members`, `slots`, `caresFor`
3. [ ] Invoke `backfillRecordFamilyIds` — every record gets its `familyId`
4. [ ] `firebase deploy --only firestore:rules,firestore:indexes`

Both callables are idempotent and report per-reason counts. Step 1 also deploys `onFamilyCreated`,
which stamps a pair's pre-pairing records as their family document is created — including the ones
step 2 creates, so step 3's `stamped` may come out smaller than expected; run it anyway. Step 3 is
safe to re-run at any time afterwards and is the repair for anything the trigger missed; read its
`skippedReasons` and `unresolved` (`functions/README.md` says what each one means). **Running 4 before 3** leaves each
co-parent's expense and budget history looking empty on the other phone until 3 completes — nothing
is lost, since Room is the source of truth, but it is alarming to watch.

**Separately, and it fixes a live bug:**

- [ ] `firebase deploy --only storage`. The bucket still runs its July 2026 rules, which cover
      `receipts/` and `event_images/` only, so `pet_photos/**` and `medical_photos/**` fall through
      to the catch-all `allow read, write: if false` and **every pet and medical photo upload is
      refused today**. The client path is sound and was ruled out end to end. The ruleset *in the
      repository* is covered by `firestore-tests/rules/storage.test.js` (this line used to say
      Storage had no coverage); only the deploy settles what the bucket enforces. This also closes
      the unchecked box at `docs/REVIEW-2026-07-23.md:65`.

**And the accounts:**

- [ ] Set `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET` in `functions/.env` (SEC-1 §2).
      **Google Calendar sign-in does not work until these are set and the functions deployed** —
      the client secret is no longer in the APK, so the app has no other way to redeem a code.
      *(The SendGrid bullets that used to sit here are gone with email invitations — owner
      decision, August 2026; sharing a code is the whole invitation story.)*
- [ ] **Decide the Firestore region.** An EU region makes the whole GDPR story simpler and cannot
      be changed once data exists. Check what `coparently-a39c9` uses today.
- [ ] Sign a DPA with Google covering Firebase.

### REL-4 · P0 · Legal documents: review, host, link

**Where:** ☁️ the drafting and the deletion page; 💻 the review and the hosting.

Drafts are in `docs/legal/`. They are drafts. **Have a lawyer read them before publishing** — this
app processes a child's health data, which is special-category data under GDPR Art. 9, and no
template survives that unread.

- [x] Fill every placeholder the code can answer (September 2026, release-tails pass). The
      functions' region is stated (`us-central1`), retention says what the code does (90-day
      tombstones, the daily guest sweep, no sweep for lapsed friend grants), the pricing section
      and the invitation paragraph are written, and the terms no longer point at the EU ODR
      platform, which closed in July 2025.
- [ ] Fill what only you can: each of `PRIVACY-POLICY.md`, `TERMS-OF-SERVICE.md` and
      `DATA-SAFETY.md` opens with an **"Owner must fill"** table — controller identity, address,
      IČO, contact addresses, the Firestore/Storage location (`{{FIRESTORE_REGION}}`), the dates,
      whether a DPO is appointed, the liability cap, governing law and courts, and the Families
      answer. *(yours; then regenerate the pages)*
- [ ] Legal review. *(yours)*
- [x] Both are **pages already**: `web/privacy/index.html` and `web/terms/index.html`, generated
      from the markdown by `tools/wrap-legal-page.js` in the deletion page's self-contained style
      (September 2026), each with a draft banner that lists the placeholders still in it and
      disappears only when the last one is filled. `firebase.json` carries the `hosting` block that
      serves `web/`. Regenerate the pages in the same commit as any edit to the markdown —
      `web/README.md` has the two commands.
- [ ] `firebase deploy --only hosting`, once the banner is gone. Play requires the privacy-policy
      URL in the listing. *(yours)*
- [x] The **web account-deletion page** is written: `web/delete-account/index.html`, one
      self-contained file in Czech and English, with `web/README.md` covering the placeholders,
      the hosting, and the two sentences that are the lawyer's to confirm rather than a
      developer's. Every claim on it mirrors `deleteAccountDataImpl` and the privacy policy's
      "Deleting your account" section — including the one that surprises people, that records you
      entered vanish from your co-parent's calendar too.
- [ ] Host it, and put the URL in **two** places: the Play Console's data-deletion field and
      `{{WEB_DELETION_URL}}` in the privacy policy. *(yours)*
- [x] The deletion page now matches the code again. It had listed the photographs as deleted
      while `deleteAccountDataImpl` left every Storage object in the bucket — fixed in the function
      (`deleteAuthoredFiles`, before the documents that name the files), not in the text — and it
      implied the co-parent's phone forgets, which it does not: nothing reconciles by absence, so
      a record already downloaded there stays. The page, the policy and the terms say so now.
- [x] The privacy policy is linked from Settings → Account and from the consent screen, through
      `BuildConfig.PRIVACY_POLICY_URL` and `PrivacyPolicyLink` — and **neither renders while the
      value is blank**, which it is until the policy is hosted (design rule #8).
- [ ] Set `publishedPrivacyPolicyUrl` in `app/build.gradle.kts` once the policy is hosted. That
      single line is what makes both links appear. *(yours, or a session once you give the URL)*

This unblocks **CQ-16** too — both want the same domain.

### REL-5 · **DONE** · Analytics consent for the EU

**Where:** ☁️ cloud, 👁 still worth a look on a device.

- [x] First-run consent screen, defaulting to **off**, persisted, reachable again from Settings.
- [x] Wired to `setAnalyticsCollectionEnabled` / `setCrashlyticsCollectionEnabled` at **runtime**,
      not only at injection time.
- [x] `docs/legal/DATA-SAFETY.md` updated — both are now *optional* and changeable, not required.

Three things this turned up that the item did not anticipate:

**The build flags were already being defeated.** `CoPlanlyApplication.onCreate` called
`FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)` unconditionally, a
moment after `FirebaseModule` had applied `BuildConfig.ENABLE_CRASHLYTICS` — so debug builds
reported into the production project regardless of the flag the August 2026 audit added. That
line is gone; the setters now have exactly one caller, `TelemetryConsentApplier`.

**The real fix was in the manifest, not in Kotlin.** Both SDKs auto-initialise before any app
code runs, so a release build collected an `app_open` before anybody could be asked. They now
carry `firebase_analytics_collection_enabled=false` and
`firebase_crashlytics_collection_enabled=false`, which are the *resumable* knobs —
`firebase_analytics_collection_deactivated` is a different one and must stay `false`, because
setting it true disables Analytics permanently and a granted consent would silently do nothing.

**Consent is a route before sign-in, not a step in the wizard.** The onboarding questionnaire
belongs to an account; this question is older than the account, and asking it after
authentication would already have cost a `screen_view`.

Still worth a device pass: the screen has never been rendered, and the decline button is
deliberately first and quieter — confirm that reads as intended rather than as a disabled
control.

### REL-6 · P0 · Play Console

**Where:** 💻 yours only. The Data Safety answers can be re-derived from the schema in the cloud.

- [ ] Data Safety declaration — answers derived from the real schema are drafted in
      `docs/legal/DATA-SAFETY.md`. Check them against the code before submitting; a wrong
      declaration is a policy violation, not a typo.
- [ ] Store listing, screenshots, feature graphic. Czech first, English second.
- [ ] Content rating questionnaire — declare user-to-user communication (chat).
- [ ] **Google OAuth consent screen** (September 2026 audit, not tracked before): the app asks
      for the sensitive `calendar` scope, so until the screen is verified every closed-test
      tester must be added as an OAuth test user or sees "unverified app" (100-user cap).
      Consider `calendar.events` instead.
- [ ] Target audience 18+ only, not in the Families programme (`DATA-SAFETY.md`).
- [ ] Closed testing track with **real co-parent pairs** — this product cannot be tested by one
      person, and the failure modes only appear across two devices.

### REL-7 · P0 · Prove R8 on a device — the one test CI cannot run

**Where:** 💻 yours only, by definition.

- [ ] Install a **release** build, save a child's medical profile, confirm it reaches the co-parent
      non-empty.

A green `assembleRelease` proves the build survives shrinking. It does **not** prove Gson still
finds its field names afterwards, which is the defect (audit §2.8) the keep rules in
`proguard-rules.pro` were written for, and which had already shipped once. Nothing but a real APK
answers this. While you are there, the same run is the cheapest moment to eyeball what M-4 shipped
unseen: the colour picker, the family switcher, and the second-co-parent invite.

---

## 4. [SEC] Security

The August audit closed fifteen findings, four of them critical, and PR #76 closed the
cross-family expense leak. What follows is what neither closed. Full reasoning in audit §3.

### SEC-1 · P0 · Two independent pieces, not one build

**Where:** ☁️ the OAuth callable; 👁 the Storage rules — they cannot be covered by
`firestore-tests/`, so settle the verification story first.

The item used to read "one Cloud Function proxy closes three holes". Two of the three do not need
a proxy, and one of them does not need one for a reason that was a **factual error** in the
original.

1. **Cloud Storage** — every rule is `request.auth != null`. Any signed-in CoPlanly user who learns
   an object path can **overwrite or delete** it: a receipt, an event photo, a photograph attached
   to a child's medical record. Paths are not secrets — they are built from ids a co-parent has
   held, and an ex-partner's local Room copy survives both sign-out and the unpair sweep. Not
   patched with a plain owner check on purpose: **both** parents legitimately manage the same
   files, so a uid check would deny the co-parent a deletion the app itself offers them.

   **This does not need the proxy.** Both this item and `storage.rules` asserted that Storage rules
   cannot read Firestore. They can: cross-service Security Rules (September 2022) give Storage
   rules `firestore.get()` and `firestore.exists()`, two document reads per evaluation. So the fix
   is a rules change keyed on the state `firestore.rules` already gates on — **S, not L**.

   Two traps, both found by looking rather than by reasoning:
   - A receipt is **uploaded before its expense document exists** (`ExpenseViewModel.addExpense`
     mints a UUID, uploads, then writes the expense). A rule requiring the document would reject
     every first upload. Gate *overwrite and delete*, which is the harm; a create at a fresh UUID
     path is an orphan blob.
   - **The Storage emulator does not resolve cross-service calls.** Verified with both emulators
     running and the document confirmed present (firebase-js-sdk#6803, firebase-tools#5251). This
     project has already shipped one broken delete rule by testing a rule some other way. **Settle
     how it will be verified before writing it** — a staging bucket against a real project is the
     likely answer.
2. ~~**OAuth token exchange** — the Google client secret ships in every APK.~~ **Done.**
   `exchangeGoogleAuthCode` and `refreshGoogleAccessToken` hold the secret in the functions'
   environment; the client sends its authorization code and gets tokens back, and still stores
   them where it always did — moving their storage server-side is a separate, larger decision.

   The part worth knowing is what the move would have opened if done naively. A refresh callable
   that refreshed whatever it was handed is an **oracle**: it turns any stolen refresh token into
   an access token for any signed-in caller, which is precisely the capability that taking the
   secret out of the APK removes. So the exchange records a SHA-256 of the refresh token against
   the caller's uid (`google_oauth/{uid}`, denied to every client in both directions) and the
   refresh grant refuses a token that is not the one this account was issued. An unknown token is
   refused rather than trusted on first use, because trust-on-first-use hands the account to
   whoever presents a stolen token first; the cost is one re-consent for anybody whose Calendar
   was connected before this shipped, surfacing as the reconnect prompt the app already has.

   PKCE is untouched and still absent. It is the *other* half of this line and it is not the half
   that was leaking a credential.
3. **Model calls, if AI ever returns.** The Gemini key used to ship in the APK too; the subsystem
   and the key are gone (**MON-7**), so this is a precondition rather than a live hole — nothing
   goes to a model again without the proxy in front of it.

### SEC-2 · **DONE, with one step nobody has taken** · P1 · M · Room is not encrypted at rest

**Where:** ☁️ written; 👁 **the first launch on a phone that already holds data is an acceptance
step, and it has not been run.** See the caveat at the end — it is the honest state of this item.

Room opens through SQLCipher. The passphrase is 256 random bits, wrapped by the existing
`EncryptionManager` under an Android Keystore key and kept in a preferences file of its own; the
database file, header included, is ciphertext. `allowBackup="false"` and `data_extraction_rules.xml`
had already closed cloud backup and device transfer, so what this closes is the residual case: a
rooted or physically held device, where a child's medical profile, the whole chat history and the
`isPrivate` events that never leave the phone were readable by any SQLite viewer.

**The audit's "smaller first step" was not smaller, it was broken, and the reasoning is worth
keeping.** Field-level encryption of the medical profile only sounds cheaper until you notice that
`child_info` syncs and the key is device-bound: the co-parent's phone would receive ciphertext its
own Keystore cannot open. Making that work means decrypting on every way out and re-encrypting on
every way in, forever, to cover one table instead of eleven. `SensitiveMedicalData`, the unused
half-implementation of exactly that idea, is deleted with this change rather than left as an
invitation.

**Three decisions worth not re-litigating.**

- **The passphrase does not live in `EncryptedPreferences`,** although that class is also
  Keystore-backed. When it cannot open its store it clears the file and mints a fresh keyset —
  right for the Google refresh token it was written for, where the cost is one re-authorisation,
  and catastrophic here: it would hand out a *different* key on the next launch and leave the
  database unopenable with nothing to say why.
- **Where a half-finished conversion got to is read from the files, not from a flag.**
  `SqlCipherMigration.next` is a total function of four booleans and `SqlCipherMigrationTest` walks
  all sixteen states, because four of them are ones where the wrong answer deletes the only copy of
  a family's calendar. A flag in preferences can be cleared, restored, or written out of order with
  the thing it describes; the SQLite header in the file cannot disagree with the file it is in.
- **The plaintext database is deleted only after a verified encrypted copy exists beside it under
  a different name.** That ordering is the whole safety argument; nothing may reorder it. A failure
  that leaves the plaintext file intact falls back to opening it unencrypted and retries next
  launch — deliberately, because crashing makes the app unusable and wiping trades data the user
  has for a property they did not have a moment ago.

**The caveat, restated (September 2026): the SQLCipher *open* path has now run** — the
`instrumented` CI job keeps Room real and passed on `main` on 2026-09-01 — but the export, the
verification and the swap of an existing plaintext file have not: the emulator starts from an
empty database, so the conversion is still exercised nowhere. Nothing is published,
so no install but the developer's own is at stake — but *the first launch on a device that already
has data is an acceptance step somebody has to perform*, and it belongs in **REL-7**'s list. What to
watch for: the app opens, the calendar and chat are still there, and
`adb shell run-as app.coplanly` shows the database file no longer starting with `SQLite format 3`.

**One thing rides on that check.** `docs/legal/PRIVACY-POLICY.md` now tells the user their database
is encrypted on the device, and `DATA-SAFETY.md` records what backs the claim. Neither document is
hosted yet (**REL-4**), so nothing false is published — but if the acceptance run fails, the
sentence comes out of the policy in the same commit as whatever fixes it. A privacy policy is the
last place to leave a claim the code does not keep.

### SEC-6 · **PARTLY DONE** · P1 · What the September 2026 audit closed, and what it left

**Where:** ☁️ the closed half is on `claude/parent-sync-onboarding-p1e84p`; the open half is
listed so it is not lost. Full evidence in that branch's commit messages and CLAUDE.md items 22–23.

Closed, each with a regression test on the emulator or in `functions/test`:

- [x] Any signed-in account could write an expense, budget, event, child or pet *into* somebody
      else's family (`familyId`/`sharedWith` were unbound on create) — and a malformed one crashed
      both parents' Expenses screen on every open. Bound to the writer (`isMyAudience`,
      `familyIsMineOrBlank`); the expense and budget readers skip a document that does not parse.
- [x] A parent could forge a split-ratio proposal in the co-parent's name and accept it alone.
- [x] An ex-partner could keep posting chat after unpair, and the server pushed it under any name.
- [x] `unpairCoParent` did not revoke when the caller was the second co-parent of a multi-family
      parent; `deleteAccount` skipped every pairing of such a parent, the parenting plan and the
      OAuth fingerprint.
- [x] A co-parent could strip the creator out of a child's record or hand it to a stranger.
- [x] A change request could be rewritten while being "accepted", or re-addressed.
- [x] Client writes could move `role`/`partnerId`/`partnerIds`; an invitation could be minted that
      never expired; two accounts could redeem one code at once.
- [x] Client: an encrypted database was handed to the framework helper on a SQLCipher failure,
      which deletes it; a transient Keystore refusal read as "key lost"; pushes were shown to
      whoever was signed in on the device, and the token never detached on sign-out; an offline
      sign-out kept the previous account's Google Calendar credential; the advertising-id
      permission was merged in; cleartext was allowed on API 26–27; a chat deep link with a `/`
      crashed the exported activity; the invite code went to the clipboard unmarked.

Open, in the order they matter:

- [x] **Expenses and budgets recorded before pairing never reached the co-parent under the
      family-keyed rules** (September 2026). They upload with `familyId: ""` and the client never
      re-stamps the remote copy (`FamilyIdBackfill` is Room-only, CLAUDE.md item 18). Fixed
      server-side rather than by a client re-queue: `stampOwnBlankFamilyIds` in
      `functions/index.js` stamps an author's blank records in all six collections, run by the
      new `onFamilyCreated` trigger the moment a pair forms and by `backfillRecordFamilyIds` as
      the re-runnable backstop. It stamps only when the family is not a guess — exactly one live,
      mutual co-parent and no trace of an earlier one — and counts the rest as `unresolved`.
      `backfillRecordFamilyIds` itself used to guess: it read the singular `partnerId`, so a
      two-family author's blanks were stamped with whichever family that field named. Inert until
      REL-3 step 1 deploys.
- [ ] **What the re-stamp deliberately leaves**: blank records of an author with two co-parents
      (`ambiguous`) or with evidence of an earlier one (`priorRelationship`) stay readable by their
      author only, and no client path reliably stamps them later: a budget edit echoes the stored
      `""` (the budgets update rule pins `familyId`), and an expense's download maps `""` back to a
      null Room value that `FamilyIdBackfill`, having already run for that co-parent, does not
      revisit. Deciding these needs a person — a
      per-record "which family is this" prompt — not a server heuristic. Count them first:
      `backfillRecordFamilyIds` reports `unresolved`, and today it is expected to be near zero.
- [ ] Cloud Storage: any signed-in user can still overwrite or delete any object (audit §3.1,
      SEC-1 §1). The cross-service rule is drafted in the audit report; the emulator cannot
      evaluate `firestore.get()` from Storage rules, so it needs a staging bucket.
- [ ] Invite-code redemption has no rate limit and no App Check. Space is 31⁶ and codes expire in
      24 h, so this is a growing risk, not a live one; `enforceAppCheck` needs the client wired
      to Play Integrity first.
- [ ] `onEventCreated`/`onChildInfoUpdated` still compose English `title`/`body` server-side and
      notify only the first co-parent; `guest_accepted`/`calendar_friend_accepted` have no client
      wording and are dropped on arrival (item 15's four-way rule).
- [ ] The Firebase SDK's offline cache, WorkManager's input data (reminder titles) and Coil's
      image cache are plaintext files under Android's file-based encryption only; the privacy
      policy now says so rather than claiming otherwise.
- [ ] Messages accept any `timestamp`; a bound would break the offline outbox, so a back-dated
      message stays possible. Bound it server-side in `onChatMessageCreated` if it ever matters.

### SEC-5 · P3 · S · `androidx.security:security-crypto` is on an alpha

**Where:** 👁 the bump compiles in CI; whether tokens survive it is a sign-in on a real device.

`1.1.0-alpha06`, holding OAuth tokens in production, on a branch that is effectively frozen.
Decide: pin and document, or move off it.

---

## 5. [CQ] Code quality, correctness and platform

### CQ-1 · **PARTLY DONE** · P1 · S · Restore the Room schemas (v15 → v33)

**Where:** ☁️ the gap is closed and fenced; ⚙️ what is left needs an emulator job, and only pays
off from v34 onward.

`CoPlanlyDatabase` reached `version = 33` while `app/schemas/` stopped at `14.json`, and nothing
noticed for nineteen versions. `MigrationTestHelper` builds a database at a past version *from
those files*, so every migration since v14 shipped with no fixture to test it against — and
`DatabaseModule` deliberately refuses destructive migration above v4, which makes a broken one a
**crash on launch** for somebody with real data rather than a wipe.

**Done, and it is the half that stops the bleeding:**

- **Schema 33 is exported and committed**, by `.github/workflows/regenerate.yml` — the answer to
  why this sat still so long, which was never the decision but the mechanics: exporting needs an
  Android SDK that the sessions writing this repository do not have.
- **A bumped version with no schema beside it fails the build**, with a message naming the
  workflow that produces one. That check is a step in `ci.yml` — `git status --porcelain --
  app/schemas` after the build — and **not** `DatabaseSchemaExportTest`, which this line credited
  until MON-5 tripped over it. The test reads the schema directory off disk, and kapt *writes*
  that directory during the build immediately before it, so the file it looks for has just been
  created whether or not anybody committed it: it passed on a tree missing the export it exists
  to demand. The test is kept for its second assertion, which no build regenerates away — a file
  whose name and contents disagree would have `MigrationTestHelper` build a database at the wrong
  version and validate against it.

**Accepted, deliberately: v15 → v32 are gone.** They were never committed and a build of today's
code cannot produce them; rebuilding each historical commit was considered and rejected, since old
commits may not build at all under today's Kotlin and AGP and the result would be a partial set
that looks complete. The consequence is stated rather than hidden: those nineteen migrations, plus
**SEC-4**'s timestamp conversion and **FAM-2**'s dead `childId` columns, stay unprovable.

**Left:** nothing on the CI side — the `instrumented` job exists (September 2026) and runs the
six migration tests that have schemas plus 33→34; the eight tests for the missing schemas stay
`@Ignore`d, as `CLAUDE.md` explains, until somebody restores a schema. The old **CQ-2** id, the
untested migrations that shipped in `versionCode 2`, is folded in here and dies with the same
reasoning.

### CQ-5 · **DONE** · P1 · M · Sync downloads the entire event collection every 15 minutes

**Where:** ☁️ cloud. The two design questions below are settled; the answers are recorded under
them.

`observeEventsSharedWith` has no date window and no limit. Re-measured on 2026-08-25: a bound
appears **twice** in all of `app/src/main` — a `limit(1)` on a user lookup and the chat's
`limitToLast` from CQ-6 — so nothing the calendar reads is bounded at all. (The backlog used to say
"exactly once"; CQ-6 added the second.) A couple with ~4 events a day reaches 4–5 thousand
documents in three years,
and `SyncWorker` runs every 15 minutes on both devices. A Firestore bill that scales with tenure
rather than usage, landing first on the users who stayed longest.

**It is not "a rolling window plus a `lastSyncAt` delta"**, as the backlog used to say:

* a delta on `updatedAt` would miss every deletion, because tombstones deliberately do not move it
  (and `updatedAt` carries SEC-4's ordering defect anyway);
* a date window on `startDateTime` cuts off the master row of a recurring series that began before
  it.

**Both are answered by the same decision, and the second dissolves into the first: the bound is a
change cursor, not a date window.**

**`serverUpdatedAt`**, a `FieldValue.serverTimestamp()`, is written on every create, update and
tombstone — stamped inside `FirestoreEventDataSource` rather than in the four maps that reach it,
so no caller can forget. `SyncService` keeps a per-uid high-water mark and asks only for documents
written after it.

- *A date window cuts off a recurring master.* A change cursor has no such edge: a document is
  fetched because it **changed**, wherever its dates fall. The question stops existing.
- *An `updatedAt` delta misses deletions.* A tombstone deliberately does not move `updatedAt`, but
  it does move `serverUpdatedAt`, because the stamp is on the write and not on the meaning.
- *Why the server's clock.* Stamping from the device would let a co-parent whose clock runs a few
  minutes slow write a document below the reader's cursor, which the reader would then never
  fetch. Same hole SEC-4 closed for the custody schedule, closed the same way.

**A full sweep every 24 hours is what makes the delta safe, not a precaution.** A document written
before `serverUpdatedAt` existed has no such field, and Firestore excludes a document lacking the
field from a `whereGreaterThan` outright rather than treating it as zero — the silent exclusion
that dropped pre-fix `budgets` from their `whereIn`. Without the sweep, a delta would never deliver
a single event created before this shipped. With it, they arrive within a day and their first edit
gives them the field for good, so **this needed no backfill and no ops step** — which mattered,
because the last ops step is still undone. A sweep only ever adds and updates, never reconciles by
absence, so it cannot delete anything (item 14).

96 full collection reads a day become 1 sweep and 95 deltas that on a quiet day return nothing.

`EventSyncWindow` holds the rule, out of Firestore so it is unit tested; the composite index
(`sharedWith` CONTAINS + `serverUpdatedAt` ASC) is in `firestore.indexes.json` and **must be
deployed with the rules** or the delta query fails at runtime.

**Still open, and deliberately not bundled here:** `HomeViewModel` holds three subscriptions to the
whole events table plus one to all expenses and filters the current month **in memory**, while
`EventDao.getEventsForParentPaginated` sits written and never called. That is a Room-side cost, not
a Firestore bill, and it wants its own change. Audit §8.6.

### CQ-6 + CQ-8 · **DONE** · P2 · M · The chat's last unbounded query, and a listener that gives up

**Where:** ☁️ cloud.

**CQ-8 is fixed structurally.** `data/chat/ChatMirror` owns both Firestore listeners from the
process — started in `CoPlanlyApplication.onCreate` beside `SessionProfileSynchronizer` and
`TelemetryConsentApplier` — and restarts either one when it ends. Both structural answers this item
named are in it: it **awaits `ensureConversation` before subscribing**, which is the direct fix for
what was seen in production, and it **drops the Activity-scoped collector's role** as the thing
keeping the mirrors alive.

`ChatViewModel.unreadCount` is now `MessageRepository.observeUnreadCount` — a Room `COUNT(*)`,
with the conversation id *derived* from the two uids rather than read from the conversation
document, so the badge subscribes to neither listener. That was the other half of CQ-6's cost and
it is gone: a badge is no longer materialising every message a pair ever exchanged, forever, to
produce one integer.

**Two layers of recovery, and the item's caution still stands.** `reconnecting()` is unchanged —
fast, bounded, eight attempts. `ChatMirror` is the slow outer supervisor at five minutes, for an
outage that outlives the inner retry. The warning against an unbounded retry was about the inner
one and still holds; the outer loop is bounded in *rate* rather than in count, and its delay is
injected so the give-up path is testable instead of spinning the virtual clock — which is exactly
what `ChatMirrorTest` does.

**CQ-6's last half is done too.** `MessageDao.getMessages` takes a limit, and `ChatWindow` decides
it: 50 on open, +50 per "load earlier". The bound is written as an inner `ORDER BY … DESC LIMIT n`
flipped back to ascending by the outer query — a plain `ASC LIMIT n` takes the *oldest* n, which is
the same trap `limitToLast` exists to avoid on the Firestore side of this feature.

The affordance is a button at **index 0 of the list**, not pinned above it. That is what makes it
need no scroll anchor: a reader has to be at the top to press it, so after the window grows they
are still at the top and the newly loaded messages appear directly below. Growing rather than
paging is the other half of that — a window that slid would take the messages they were reading off
the bottom.

`ChatWindow.hasMore` is `loaded >= limit`, derived from what came back rather than from a second
count query. It is wrong in exactly one case, deliberately: a thread of exactly 50 messages offers
the button once and it disappears after a grow that finds nothing. A button that does nothing once
beats history that is silently unreachable.

**The background, kept because it explains the shape.** The home screen already answered its badge
with a Room `COUNT(*)`, and the remote listener was already bounded to the newest 200 messages
(`limitToLast`, not `limit` — the order is ascending, so `limit` would pin the window to the oldest
messages and a live thread would stop updating at the bound).

**CQ-8 was the reason CQ-6 could not be done alone.** Both mirror branches in `MessageRepositoryImpl` now
go through `reconnecting()` (`retryWhen`, exponential backoff, eight attempts, capped at a minute)
before reaching the `.catch` that ends the mirror — which covers what was seen in production: on
the first launch after install both listeners were denied ~0.5 s before `ensureConversation`
created the document, and that whole session ran on local data while looking entirely healthy. An
outage longer than the backoff still ends in that state and still lasts until the process restarts,
because `.catch` *completes* the flow and `SharingStarted.WhileSubscribed` cannot restart it:
`NavGraph.rememberChatUnreadCount()` holds an Activity-scoped `ChatViewModel` collecting
`unreadCount` for the whole process lifetime, so the subscriber count never reaches zero. That same
collector is what keeps the mirror alive at all, which is why the cheap count cannot replace it
until something else does.

Structural fixes: await `ensureConversation` before subscribing, or drop the Activity-scoped
collector. **Do not** "fix" it by removing the `.catch` — an uncaught failure in
`viewModelScope.launch` terminates the process — and do not make the retry unbounded: a genuinely
broken rule would then reconnect for the life of the process, and any test of the give-up path
spins on the virtual clock instead of finishing.

### CQ-11 · **DONE** · P3 · S · Error handling is declared but not wired

**Where:** ☁️ cloud.

**Done:** all ten `printStackTrace()` calls now record to Crashlytics; `SyncWorker` logs and reports
both its failure paths and gained the `NetworkType.CONNECTED` constraint it was missing.

**Decided (September 2026): the error model is two shapes, and `AppError` is the smaller one.**
Measured against the code rather than the audit, `ErrorHandler` was not unwired — `EventViewModel`
routes all twelve of its failure paths through it, and since CQ-14 `AppError.toUiText()` words the
result. What was dead was the part that read as coverage: `getRetryAction` (it returned `null` on
every branch and had no caller), `shouldRetry` and `userMessage` (read by nothing — the latter was
"logs-only" with no log reading it), and the `SyncError` variant (never constructed) with its
string. All of it is deleted, and `ErrorHandlerTest` pins what is left: each failure is recorded
and classified, an I/O failure says whether the device was offline, a classified error passes
through.

The model in use, which is the rule for new code:

- **A ViewModel that knows which operation failed says so with its own resource** —
  `UiText.Res(R.string.change_request_error_apply_failed)`, `custody_setup_save_failed`,
  `pets_delete_failed` — and logs the exception. This is most of the app, and it is more useful to a
  parent than a sentence chosen by exception type, so it is **not** to be routed through
  `ErrorHandler`.
- **A ViewModel whose failures arrive as an arbitrary `Throwable` from a use case** — today only
  `EventViewModel` — classifies them with `ErrorHandler.handleError` and words the `AppError` with
  `toUiText()`, mapping a `ValidationError.field` to something more specific where it can.
- **A screen that branches on an outcome gets a typed code** (`EventOperation`, `SyncFailure`,
  `SwapError`, `PetSaveOutcome`), never text (CQ-14).

The 228 `catch` blocks are not a defect of this model; each is a local decision about what a failure
means at that call site. The ones that matter to a user are the ones whose outcome reaches a screen,
and those are the ViewModel tests CQ-13 added.

### CQ-12 · **DONE** · detekt gates again

CI's first run found **194 weighted issues**, essentially all pre-existing: `AddEditEventScreen` at
1,246 lines, `AnalyticsManager` with 22 functions, and every screen added since the baseline was
last generated. Refactoring those to get a first CI run green would have buried the change that
introduced CI in an unrelated rewrite, so detekt ran with `continue-on-error: true` — a report,
not a gate.

What kept it there was not the decision but the *mechanics*: regenerating a baseline needs
`./gradlew detektBaseline`, which needs an Android SDK, which the sessions writing this repository
do not have. `.github/workflows/regenerate.yml` is the answer — it runs the task and commits the
result back to the branch. The baseline is regenerated, `continue-on-error` is gone, and a new
violation fails the build.

**The regeneration is deliberately manual**, and that matters more than it looks: `detektBaseline`
overwrites the file with everything detekt currently finds, so a job doing it automatically would
absolve every new violation silently — the exact opposite of a gate. Asking for it is a commit,
and accepting debt is therefore visible in the history.

Do not put `continue-on-error` back to turn a red build green. Fix the finding, or regenerate the
baseline through the workflow so the acceptance is somebody's decision rather than a side effect.
The debt the baseline records is still there to work down; the baseline is what stops it growing.

### CQ-13 · **DONE** · P2 · M · Test coverage is concentrated in pure domain logic

**Where:** ☁️ cloud — JVM unit tests are exactly what CI runs.

**Every one of the twenty-five ViewModels now has a test file** (September 2026). The thirteen that
had none — ChangeRequest, RequestChange, TelemetryConsent, Contacts, CustodySetup, Budget, Friend,
GuestAccept, CustodyConflict, ParentingPlan, Pets, AuthState and Sync — got two to six tests each,
aimed at behaviour a regression would hurt rather than at construction: the custom-pattern week
shortcut and the every-other-weekend form reopening on a Monday midweek (`CustodySetupViewModelTest`),
a friend profile saved from a route that never collected the grant (item 17,
`FriendViewModelTest`) and a revoke's result reaching the screen, telemetry's `DENIED` stored as an
answer rather than left unanswered, accepting a change request whose event has not synced, the
onboarding decision counting only this account's own records (`AuthStateViewModelTest`), a pet
photo whose delete failed staying on the record, a conflict choice that archives a same-id rejected
pattern and can be retried after a failure, and each save path's localised error. `SettingsViewModel`
has its push-switch tests from earlier in the month.

They were written in a session with no Android SDK, so CI's `build-test` job is their first run.

The first four CI runs are the argument: 30 unit tests were failing because their mocks had gone
stale against collaborators added months earlier, and nobody knew. Tests that do not run are not
coverage.

**Still thin, and worth a line when touching them:** the rest of `SettingsViewModel` (account
deletion, the family dialogs), `SyncViewModel.handleSignInResult` (it takes a Play-services `Task`),
and the Compose screens themselves, which only the instrumented job reaches. It reaches the main
screens now, as a signed-in smoke walk (`MainNavigationSmokeTest`) — a crash check with Firebase
mocked, not a behavioural test of any one screen.

### CQ-14 · **DONE** · P2 · M · User-facing strings produced inside ViewModels and services

**Where:** ☁️ cloud.

`GoogleCalendarSyncState.message`, sync/status errors, `NavGraph`'s "Checking authentication…" —
hardcoded English, unreachable by `stringResource`. Extracting them needs a resource-provider
abstraction. **Do not** inject `Context` into a ViewModel ad hoc to fix one. Blocks **UX-12**.

**Done (September 2026), and the abstraction is a type rather than a provider.**
`presentation/common/UiText.kt` holds *which* string — a resource with arguments, a plural, a date,
or text that is already the user's own (a name) — and composition resolves it (`asString()`), or the
Activity's `Context` does inside a snackbar or Toast lambda. A provider injected into the ViewModel
was the rejected alternative: it resolves against the application's configuration, which under
AppCompat's per-app locales can still be the previous language on older APIs, while composition
follows the Activity. Where a screen *branches* on an outcome, the answer is still a typed code
(`EventOperation`, `SyncFailure`, the existing `SwapError`), never a `UiText`.

What moved, every one of them checked against a composable that actually renders it:
`GoogleCalendarSyncState` (and `CalendarSyncRepository`'s `SyncResult`, which now reports facts —
counts, the window as dates, a `SyncFailure` — instead of sentences), `EventUiState.Error`
(`AppError` is mapped by type in `presentation/common/ErrorText.kt`; its `userMessage` has since
been deleted — CQ-11), `ChangeRequestViewModel.errorMessage` (inbox Toast and Home snackbar),
`RequestChangeUiState.Error`,
the child and pet list errors, the expense save error and receipt warnings, the custody-setup save
error, the Co-parent sync row's error line, the event form's title/description validation, and the
FCM notification channel's name and description (system Settings shows them). Every place on that
list that used to print `e.message` — raw exception text, in English, sometimes a class name — now
prints a localised sentence and logs the exception instead.

Deliberately left, each for a stated reason: `UiError.message` and the `UiState.Loading/Success`
messages (no screen renders them; Settings, the only collector, reads the state's type — the
literals it passed were dropped), `SettingsUiState.successMessage` (never rendered; removed),
`AppError.userMessage` (logs; since deleted by CQ-11, when nothing turned out to log it),
`CredentialManagerService`'s error strings (logged, no longer shown), the unused validators in `utils/ValidationUtils.kt` (no caller), and three **stored** fallbacks —
`"Untitled Event"` on a Google import, `"Unknown"` as a chat `senderName`, `"Co-parent"` as a
conversation's partner name. Those are data written to Room and Firestore, not text drawn from a
ViewModel; localising them would bake the writer's language into a record the other parent reads.

It used to be recorded as blocking **SEC-3** too. It did not: push text moved to the *receiving*
device, which has a `Context` and all five translations. Worth remembering when the next item
claims to be blocked behind this one — the question to ask is which side of the wire the string is
finally read on.

### CQ-15 · **DONE** · P3 · S · Dead code

**Where:** ☁️ cloud.

**Most of the original list was wrong, and it was measured rather than re-read** — three entries had
consumers all along and two more had since been wired. 1,264 lines were deleted; the rest stays.

**Closed in the September 2026 audit.** `ErrorDisplay` and `CoPlanlySnackbarHost` were deleted
rather than wired: UX-2 had already decided a failed list read stays a loaded empty value, and both
carried hardcoded English and literal colours. `LoadingSkeleton.kt`, the unused skeletons,
`AnimatedTheme`/`CoPlanlyDynamicTheme` (no caller) and the Glance dependencies (no widget) went
with them.

**Not to be deleted**: the five `EventDao` methods including `getEventsForParentPaginated`, which is
the thing CQ-5's Home-screen half would use. Deleting it now would be deleting the answer.

### CQ-16 · P3 · S · No Digital Asset Links

**Where:** 💻 yours — it needs a domain and the release fingerprint.

`CredManMissingDal` is disabled in `app/build.gradle.kts` with that rationale. Credential Manager's
password sign-in cannot share a credential with a website, and the pairing deep link stays a custom
scheme rather than a verified App Link. Both need the domain **REL-4** needs for hosting.

### CQ-17 · P3 · S · Dependencies worth moving

**Where:** ☁️ cloud for the bumps; 👁 the sign-in ones want a device.

**September 2026 (release-tails pass): one moved, four stay, each for a stated reason.** The
session had no Android SDK and could not reach Maven Central, so "safe to make blind" meant a
version whose existence could be confirmed and whose API the code demonstrably does not depend on
changing.

| Dependency | Now | State |
| --- | --- | --- |
| `androidx.work` | **2.10.5** (was 2.9.0) | **Done.** 2.10.x fixes the Doze/foreground bugs that hit a 15-minute sync; same API, `hilt-work` 1.2.0 unchanged. 2.11 raises minSdk and changes more — a separate step. |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | **Left.** See **SEC-5** — whether stored OAuth tokens survive is a sign-in on a real device. |
| `play-services-auth` | 21.2.0, deprecated | **Left.** Not a version bump: `CredentialManagerService` still calls `GoogleSignIn`/`GoogleSignInClient` for the Calendar scope, so dropping it means moving that flow to `AuthorizationClient` — a sign-in change only a device can judge. Both it and Credential Manager stay in the graph until then. |
| `google-api-services-calendar` | `v3-rev20220715` | **Left.** The current revision (`v3-rev20260708-2.0.0` per the client-library repository) pulls a newer `google-api-client` than the pinned `google-api-client-android:2.2.0`, so the bump is really a pair, and Maven Central could not be reached to check what the two resolve to. The surface the app uses (`events().list/get/insert/update/delete`) is stable across revisions; what wants checking is resolution and R8, then one import and one export on a device. |
| `firebase-functions` (Node) | ^4.5.0 (lockfile 4.9.0, the last 4.x), gen-1 API | **Left.** Already at the top of its major. 5.x/6.x move the gen-1 triggers behind `firebase-functions/v1` and v6 changes the default export; every function then needs a `firebase deploy` to prove it, which is yours. ESLint 8 → 9 needs a flat config and goes with it. |

*(`retrofit` left the graph with the AI subsystem — MON-7.)*

### CQ-18 · P3 · S · Cross-time-zone chat was implemented but never verified on two devices

**Where:** 💻 yours — two phones, two zones.

Epoch-millis message times are covered by unit tests that drive two zones explicitly
(`ChatReadStateTimeZoneTest`) plus a 12→13 migration test. The **two-phone acceptance run** — set
one phone 2–3 hours apart, send, confirm unread counts, badge clearing and READ ticks — was
deferred, not run. Everything else in that acceptance round passed on real devices.

### CQ-19 · **DONE** · Deleting a child or a pet removed the document outright

`ChildInfoRepositoryImpl.deleteChildInfo` and `PetRepositoryImpl.deletePet` both called the data
source's `.delete()` and both discarded the `Result` — exactly what **CQ-3**'s tombstone rule exists
to prevent. Two consequences: the co-parent's phone never learned of the deletion (nothing
reconciles by absence, correctly), so the record stayed on their device forever; and a refused or
offline remote delete left the local row gone and the document alive, so the next download
re-inserted it.

Pre-existing and systemic across both record types — the multi-child work only made the child half
*reachable*, by putting a Delete action on the editor where before there was none.

Fixed with the treatment `data/sync/Tombstone.kt` already defined, Room schema 32, and no rule
widened: tombstoning turns a `delete` into an `update`, and both collections already admitted the
creator and their co-parent as writers (pinned in `deletion-tombstones.test.js`, including that a
tombstoned record stays *readable* — a deletion nobody may read is a deletion nobody is told
about). Three things that were not obvious going in:

- **`child_info` is synced in two places** — the repository's `pullOnce` *and* `SyncService` — so
  the outbox split had to be made twice. Sending a pending tombstone through `upsertChildInfo`,
  which is a `set()`, would rewrite the document from a row that exists only to record its own
  deletion, wiping the tombstone and resurrecting the child on both phones.
- **`getChildInfoById`/`getPetById` deliberately still return a tombstoned row**, mirroring
  `EventDao.getEventById`. The sync path needs "there is a row this device has deleted" and "there
  is no such row" to be opposite answers; a user's question is filtered at the repository boundary.
- **The hard-delete methods on both Firestore data sources were removed**, not left beside the
  tombstone writers. Neither had a caller left, and a `.delete()` sitting next to a `tombstone()`
  is a trap — unlike `FirestoreEventDataSource.deleteEvent`, which keeps exactly one legitimate
  caller (an event turned private has to leave Firestore with no trace).

The 90-day server sweep now covers all four collections. `budgets` and `change_requests` still
delete by other means, and the two halves must be added together: a collection in the sweep list
with no client writing tombstones sweeps nothing, and a client writing them into an unlisted
collection keeps them for ever.

### CQ-20 · **DONE** · A change request said "Sent" whether or not it left the phone

`ChangeRequestRepositoryImpl.publish` catches everything and returns, leaving
`syncedToFirestore = false` for the outbox to retry, and `RequestChangeViewModel` set `Sent`
either way. The flag reached the domain model and **no** screen read it, so there was no way to
tell a request the co-parent has from one sitting in Room.

The request card's status chip now says **Queued**, with the clock icon `MessagesList` already
uses for a message that has not left, so the two surfaces say "not delivered" the same way. The
rule is `!syncedToFirestore` alone, and it is unambiguous: a request mirrored down from the
co-parent is always written back marked synced, so only this device's own writes can be unsynced.
That also covers a *reply* made offline — accepting an incoming request while disconnected shows
Queued rather than "Accepted", which is the honest report of what the co-parent knows.

The state that lied is renamed `Saved`: the screen pops on it whether or not the write landed, so
the honest report of what happened is the chip, not the state's name.

---

## 6. [UX] Design and usability

The theme layer is genuinely good: contrast documented pair by pair, `ParentColors` solving the
fill-versus-text problem properly, Settings and the month grid exemplary. The gap is between that
layer and the screens.

### UX-8 · **PARTLY DONE** · P3 · S · Two surfaces colour a chip from two different sources

**Where:** 👁 the remaining half is an owner's answer, not code.

The loud half is fixed: "whose day is it" is its own line at `titleMedium` in that parent's colour
through `ParentColors.text`, rather than a 12sp grey suffix on the date.

**Left open:** `CalendarBanners` colours from `entry.dayParent ?: event.parentOwner` while the event
chip colours from `event.parentOwner`, so one visual channel carries two meanings on adjacent
cards. That is a question about what a chip's colour *means* — the event's owner, or whose day it
falls on — and it wants an answer before either call site changes.

### UX-9 · **DONE** · P2 · M · Five different empty-state anatomies

**Where:** ☁️ cloud.

**Done (September 2026).** One composable, `EmptyState` in `presentation/common/DesignSystem.kt`:
an icon on a tonal disc, a title, an optional description and an optional primary action. It takes
a `modifier` (every caller now passes its Scaffold padding), and it scrolls when its height is
bounded and it does not fit, so it no longer clips at large font scales; inside a parent that
already scrolls it wraps instead. Every variant below renders through it — Contacts, ChildInfo
(and the child-detail "record no longer exists" state, which now has its own sentence), Pets,
Friends, Home's week and recent-changes cards, Conversations, the message thread, Budgets,
Expenses and EventList. `AnimatedEmptyState` is deleted, and with it the only use of
`lottie-compose` and `res/raw/empty_state_animation.json` — whose Lottie file had **no layers**,
so the "animation" was a 200 dp blank square above every empty state. **Contacts** now offers "Add
a contact", which opens the children's list (a contact lives on a child's record, and
`ContactsViewModel` never writes); **Home's empty week** offers "Add event". Not yet seen on a
device, including the pets-only family, for whom Contacts' action lands on an empty child list.

What it replaced:

`AnimatedEmptyState` in five places, plus bespoke variants in Contacts, ChildInfo, Pets, Friends and
Home — Home's being the `Card { Text }` pattern the August refresh explicitly outlawed. The previous
design review asked for consolidation to one; the count went from two to five.

**Contacts matters most**: an emergency surface, first on Home, and empty it offers two grey
sentences and no action. Separately, `AnimatedEmptyState` takes no `modifier` and hardcodes
`fillMaxSize().padding(32)`, so `Scaffold` padding does not apply and content renders under the top
bar in `ConversationsScreen` and `BudgetScreen`; it also does not scroll, so it clips at large font
scales. Audit §9.12.

### UX-12 · **DONE** · P2 · S · Clerical English success messages

**Where:** ☁️ cloud. Blocked behind **CQ-14** for the ViewModel-side strings.

**Done with CQ-14.** `EventUiState.OperationSuccess` carries an `EventOperation`, not a sentence, and
`CalendarScreen` offers Undo on `EventOperation.RESCHEDULED` (`EventViewModelTest` pins it). None of
the seven "… successfully" literals was ever rendered — they existed only to be compared — so no
string replaced them.

"Event created successfully", "Event rescheduled" and friends are still English literals — and
`CalendarScreen` **branches on the literal** `"Event rescheduled"`, so localising that string
silently removes the undo snackbar. Fix the branch first, then the strings.

### UX-13 · P3 · M · Light theme is unverifiable rather than incomplete

**Where:** 👁 cloud writes the previews and the theme fix; only a device shows the flash.

**Cloud half done (September 2026).** `Theme.CoPlanly` is now `Theme.AppCompat.DayNight.NoActionBar`
(still AppCompat, as per-app locales require) with `android:windowBackground` =
`@color/window_background`, which `values-night/colors.xml` overrides with `DarkBackground`. It
follows the *system* night mode: the in-app choice lives in Compose and is not known before the
first frame. Previews: `PreviewWrapper` defaulted `darkTheme` to `false`, so **every "Dark Mode"
preview in the project rendered the light theme** — it now follows the preview's `uiMode`.
New `@LightDarkPreviews` cover a piece of each main screen that previews without a ViewModel:
`SectionGroup`/`SectionRow`/`PillChip` and `EmptyState` (Settings, Home, Chat, Expenses),
`DayAgendaCard` (Calendar and Home), `HandoverHero` and `StatTiles` (Home), `ChatThreadHeader`
(Chat), beside the existing `ExpenseSummaryHeader` and `CalendarHeader`. detekt's
`UnusedPrivateMember` now ignores `@Preview`/`@LightDarkPreviews` functions, which it used to
report as unused. **Left for a device:** whether the dark cold start still flashes.

`LightColorScheme` is complete and correct and the setting works — but there are six
`LightDarkPreviews` across 148 UI files, two of them on dead components, and **none on any of the
six main screens**. There is no `values-night/`, and `themes.xml` uses an AppCompat *Light* parent
regardless of theme, so a cold start in dark mode flashes a white window before Compose draws.
Audit §9.15.

### UX-14 · **DONE** · P3 · S · Four different brand purples

**Where:** ☁️ cloud; the launcher icon wants a glance on a device.

**Done (September 2026).** `CoPlanlyColors.BrandPrimary` `#4F46E5` — the light theme's `primary` —
is the one brand colour and its source of truth. `@color/brand_primary` (system splash) now holds
the same value, `@color/ic_launcher_background` aliases it, the launcher background drawable is a
flat fill of it instead of the `#6750A4 → #4F46E5` gradient, and the Compose splash draws it flat
instead of the same gradient. XML cannot read a Kotlin constant, so `Color.kt` and `colors.xml`
each say the other must match. The dark theme's `#C2C1FF` primary is the same hue at a light tone,
not a second brand colour. Not seen on a device: the launcher icon and the splash hand-off.

`brand_primary` `#6750A4` (system splash), `BrandPrimary` `#4F46E5` (Compose), launcher background
`#6200EE`, and a splash gradient between the first two. Icon, system splash, Compose splash and app
do not agree. Audit §9.16.

### UX-15 · **DONE** · Thread the chosen palette into `ParentColors` (was P1 · M)

**Where:** ☁️ cloud wrote it; 👁 a device with two parents who picked two non-default colours is
the acceptance check — nothing has seen it rendered yet.

**What shipped (September 2026).** One `CompositionLocal`, provided once:

- `LocalParentPalette` (`theme/ParentColors.kt`, `staticCompositionLocalOf`, default
  `ParentPalette.Default`) is provided in `MainActivity` beside `LocalGoogleSignInCallback`, from
  a new Activity-scoped `ParentPaletteViewModel` that maps `ParentsSource.observe()` to
  `Parents.palette`. It is collected with `collectAsStateWithLifecycle`, so the shared
  `ParentsSource` upstream still stops in the background. No new Firestore listener: it is the
  same shared flow every `parents`-exposing ViewModel already subscribes to.
- `ParentColors.fill`, `container` and `text` became `@Composable @ReadOnlyComposable` and take
  `palette = LocalParentPalette.current` as their default, so the existing calls picked the
  palette up with no change and no screen threads a parameter (no detekt `LongParameterList`
  churn either). Every call site was already in composable scope.
- Every raw `CoPlanlyColors.MomPink`/`DadBlue` outside `theme/` is gone: `MonthView` (custody
  wash, handover triangle, proposal preview, event dots), `DayWeekView` (hour-cell wash, proposal
  preview, event block fill/border/accent, the custody band), `AddEditEventScreen` (owner cards),
  `EventPreviewSheet`, `EventListScreen` (the parent line was raw pink *as text* — now
  `ParentColors.text`), `CalendarFilters`, `ExpenseSummaryHeader`'s split bar and
  `CustodySetupScreen` (pattern grid, 14-day preview, legend, first-parent dot).
- **The week view's custody band label now clears AA.** It was white `labelSmall` on the full
  hue — 4.35:1 on pink. The band is drawn in the new `ParentColors.chipFill` (each choice's deep
  tone, ≥ 5.6:1 under white for all four) and the label colour comes from
  `ParentColors.onFill`, which picks black or white by WCAG contrast ratio so a future palette
  entry cannot silently fail. `ParentColorsTest` pins both, per choice.
- `PARENT_COLOUR_PICKER_ENABLED` and its two `if`s (Settings → Family, onboarding profile step)
  were removed; the picker shows.

Deliberately unchanged: the slot ids, the saturation rule (washes still go through `container`
at the custody alpha, dots and bars through `fill` at full strength), the weekend/holiday/friend
colours, and `DynamicTheme`'s use of the blue family as a *theme* colour. The Settings swatch
shows the parent's own stored choice rather than the collision-resolved palette, which is what
`ParentPalette.of` documents. Not done here: the white day numbers on `CustodySetupScreen`'s
14-day preview sit on the hue at 70% alpha and have the same AA question the band had.

*History:*

**September 2026 audit:** worse than this item said. No composable reads `ParentsSource.palette`,
so even the `ParentColors` calls take the default palette — the "My colour" picker changed nothing
anywhere. It is now **hidden** behind `PARENT_COLOUR_PICKER_ENABLED` (Settings and onboarding);
turn it on in the same change that threads the palette through. `docs/AUDIT-2026-09.md` §4.2
lists the sites.

*Original text:*

**Where:** ☁️ cloud.

Thirteen `ParentColors.*` calls against forty-four direct `MomPink`/`DadBlue` uses outside `theme/`.
Most are legitimate fills — but the rule exists so the decision lives in one place, and the place it
broke (parent hues as 8sp text on Custody Setup) is exactly where it was bypassed. Audit §9.17.
**Now larger than it was**: M-4 made the parent colour a *chosen* value rather than a slot-derived
one, so a direct `MomPink` reference is no longer merely a style violation — it draws the wrong
person's colour for anyone who picked purple or orange.

### UX-16 · **DONE** · Drag an event to reschedule it

**Where:** 👁 what is left is acceptance — nobody can tell whether a drag feels right without a thumb.

MVP 3's "time setting by dragging corners": drag the whole event by 15-minute steps, drag a corner
by single minutes. This line said it was open long after most of it had shipped; checked against
`presentation/calendar/DayWeekView.kt` (September 2026):

- **Move — was already there.** `EventChip` runs `detectDragGesturesAfterLongPress`: a long press
  lifts the block (haptic), the drag's vertical travel snaps to `MOVE_SNAP_MINUTES` (15) and its
  horizontal travel to whole day columns in week view, and the drop calls
  `EventViewModel.moveEvent` with a minute-of-day, which keeps the duration and offers Undo
  (`EventOperation.RESCHEDULED`). Dropping on the delete target deletes instead.
- **Nested in the pager — was already solved,** by the long press itself: until it fires, a
  horizontal swipe belongs to the `HorizontalPager` and pages the day or week; after it, the chip's
  handler consumes every change, so the pager never sees the drag. The resize handles consume from
  the first touch, and they are small pills at the block's top and bottom edges, so a swipe that
  starts anywhere else still pages.
- **Resize by the minute — the gap, now closed.** Both handles existed, with a live `HH:mm – HH:mm`
  badge over the block, but snapped to the same 15-minute grid as the move. They now step by
  `RESIZE_STEP_MINUTES` (1) — the precise gesture is the one for "pickup is at 15:40" — and never
  make an event shorter than `MIN_EVENT_MINUTES` (15): a corner dragged past the other one leaves a
  quarter-hour block instead of refusing the drop, as it used to. The badge shows exactly what the
  drop writes (`resizedStart`/`resizedEnd` serve both), and a drop that changes nothing writes
  nothing.

Left, deliberately: **Month view has no drag** — its cells carry dots, not blocks, and a dot has no
duration to move. Continuation slices of a multi-day or overnight event stay fixed, since which end
a drag on the middle day means is ambiguous. Acceptance on a device: whether one minute per dp (an hour
row is about 60 dp) is controllable under a thumb, or wants a coarser step on compact screens.

### UX-17 · **DONE** · A proposed split ratio could not be withdrawn, and the proposer was told nothing

`SplitRatioTransition.withdraw` existed and was unit-tested from the day the feature landed, and
**nothing called it** — so a parent who proposed 70/30 by mistake could only wait for the co-parent
to answer it. `ExpenseViewModel.pendingRatioProposal` even documented the missing half: "the
proposer sees theirs as a waiting state instead", which was not true of any state that existed.

Now it does. `FamilySettingsRepository.withdrawProposal()`, a `myPendingRatioProposal` mirror of
the existing flow, and a quieter waiting banner with one action.

Three decisions worth keeping. It lives on **Expenses, next to the co-parent's banner**, rather
than in Settings as this item first said — both halves of one conversation belong where the money
is. It is **not dismissible**: "Later" is meaningful for a question somebody else asked, but this
is the parent's own open question and the way out of it is an answer or a withdrawal. And it sends
**no push**: the other three answers announce a decision, a withdrawal decides nothing, and the
co-parent's banner is derived from the document — a type for it would cost the four places SEC-3
requires to agree, to announce that something stopped existing.

### UX-18 · **DONE** · A ratio agreed before pairing reached the pair silently

`publishCachedRatioIfMissing` wrote `family_settings/{pairId}` with no `notifyPartner`, so a parent
who set 70/30 in the wizard had it become the pair's agreement of record — priced onto every
expense from that moment — while the co-parent learned of it only by opening Settings.

`PushPayload.SPLIT_RATIO_AGREED`, the four places CLAUDE.md item 15 requires to agree, and ten
strings across five locales. Two things it does not do, both deliberate:

- **It does not route through `propose`**, which is the tempting one-line fix. An unanswered
  proposal leaves the pair splitting evenly, which is the exact bug `publishCachedRatioIfMissing`
  was written to end.
- **It carries no figure.** This item first proposed the wording "the split is now X/Y" — but the
  three sibling types state the rule already, and it holds here: a push naming a number a reader
  may act on puts it on a lock screen, written by the other side and unverifiable until the app is
  opened. The app is where the number, and the setting that changes it, actually are.

---

## 7. [MON] Monetisation and product

**There is no billing layer at all** — no Play Billing dependency, no purchase code, no entitlement
model, no paywall. Everything below assumes that gets built; **MON-1** is the decision that shapes
it, and it should be made before the code.

### MON-1 · P0 · decision · Pricing, and who pays

**Where:** 💻 yours — it is a decision. Everything after it is cloud work.

The audit's recommendation (§10.4), for a Czech-first launch:

- **99–149 CZK/month**, or **990–1,490 CZK/year.** 149 is the top of the "without thinking about
  it" band; 1,200 CZK/year is about 2.4% of one average monthly wage.
- **One subscription per family, the second parent free.** This is what the winning European
  products do (CoParently.de, 2houses, ParentDocket) and the opposite of the American per-parent
  model — which correlates with OurFamilyWizard's 2.5★ on Trustpilot (MON-2; the audit said 1.4) against 4.6★ in the stores,
  the signature of court-mandated use plus per-seat billing. There is a product reason as well as a
  market one: in a conflicted pair, **one** person will pay. Charging both loses both.
- **A free tier is not optional.** The product does nothing until *both* parents install it, so a
  paywall at the door kills the network effect that makes it work. Free should cover calendar and
  custody **completely**; charge for documentation and export.

**A wrinkle M-4 introduced:** a subscription is per *family*, and a person can now have two. Decide
whether an entitlement follows the payer across their families or is bought per relationship —
`familyId` makes either expressible, and getting it wrong after launch is a refund queue.

- [ ] Decide the price, the unit (family, not seat), and what free contains.
- [ ] Then build: Play Billing, an entitlement model, a paywall, restore-purchases, and the
      server-side check that a second parent inherits the family's entitlement. *(→ MON-11)*

### MON-2 · **CHECKED, FOUR LEFT** · P0 · S · Verify the market facts before acting on any of this

**Where:** ☁️ done as far as a cloud session can; 💻 what is left needs a phone, the justice.cz
register or a paid data source.

**Done 23 September 2026 — audit §10.10 is the record,** 29 rows, each with its source and what kind
of source it is, and §10 marks every fact inline. The pages themselves still could not be opened —
the egress policy refuses the Play Store, the App Store, app2us, the vendors and justice.cz alike —
so a "confirmed" means the primary page is in the search index saying so.

**The answer to §1 changes the Czech plan: app2us has an Android build.** It is on Google Play as
"app2us Family" (`com.app2us.family`), the vendor states iOS 15+ and Android 12+, and the App Store
"Rodina" listing the audit leaned on is an unrelated app with the same name. It is reported at
**149 CZK/month or 1,490 CZK/year** — exactly the top of MON-1's band — with a one-month trial and
no free tier, and its calendar splits care "with hourly precision". So "an Android-first Czech
product has a clear run" is gone; what is left of the gap is **the free tier**, five locales against
two, and the parenting plan (MON-5). That bears directly on MON-1.

Also corrected: OurFamilyWizard is 2.5★ on Trustpilot, not 1.4★ (MON-1's line below keeps its
shape, with the right number); AppClose is 4.6★ on Play, so "Android is underserved" holds for
TalkingParents and 2houses, not for the category; Custody X Change Bronze is $72/year ($144 is
Gold); alternating care reached 27.4% in 2024.

**Left:** confirm the app2us price on a phone (the index summary did not name its page); the
mediator count against the register; Czech ARPU (paywalled); the Facebook groups.

The original list, in order of how much each answer moves the plan (audit §10.7):

1. **app2us "Rodina": is there an Android build, and what does it cost in CZK?** This single answer
   changes the Czech strategy more than anything else found.
2. Custody X Change's price (sources disagreed: $72 vs $144/year for Bronze).
3. Fayr Premium's price; AppClose and 2houses Play ratings.
4. The registered family-mediator count, against the justice.cz register.
5. Czech mobile ARPU by country (only a global Android figure was available).
6. Current single-parent household numbers — the figure found (~175,700) is from 2015.
7. Czech Facebook groups: closed groups are not indexed and need manual search. *(yours)*

### MON-3 · **SHIPPED, UNGATED** · P1 · M · Export to PDF/CSV — the first paid feature

**Where:** ☁️ built in the cloud after MON-4 was decided; 👁 the PDF has to be looked at on a device.

Willingness to pay concentrates on **documentation you can hand to a lawyer or a court**: an
immutable log of who changed what and when, handover punctuality, an expense ledger with receipts.
Audit §7.2.

**What ships.** Settings → Family → *Export the record*: a period, then a CSV or an A4 PDF, made on
the phone with no network (`android.graphics.pdf.PdfDocument`, no new dependency) and handed to the
share sheet through the existing `FileProvider`. It holds every saved revision of every calendar
entry that touches the period — its whole history, with the device time and the server's
`recordedAt`, both labelled — the chat thread, and the expenses. It says on its face what the owner
decided it is: *a record of what the parents recorded and wrote, not of what happened*. CLAUDE.md
item 26 has the invariants; `domain/export` is pure Kotlin and unit-tested, down to RFC 4180 and the
formula-injection guard.

**Ungated, on purpose.** MON-1 has not set a price, so there is no entitlement to check, and a
flag standing in for one would be the kind of gate this project has learned not to trust (REL-5).
**The paywall arrives with MON-11's entitlement layer**, and the export is the first thing it gates.
Until then the feature is free, which is also the honest way to learn whether anyone uses it.

**Not in this version, and worth doing next:**

- **The parenting plan (MON-5)** — the other document two parents hand to a court.
- **Handover punctuality** — `HandoverCalculator` knows the schedule; nothing records whether a
  handover happened, and inventing it would break the record's own claim. Needs a product decision
  about what "on time" is recorded as, and by whom.
- **Receipt photos** — the expense rows carry no image. Embedding them makes the PDF large and puts
  a third party's document in a file meant for a court; a link that only the pair can open is the
  likelier shape.
- **Change requests** — their before-image is already stored (design §2) and would read well beside
  the revision it led to.

### MON-4 · **DECIDED 2026-09-23** · P1 · M · Decide what a court-facing record guarantees — **prerequisite for MON-3**

**Where:** ☁️ cloud. The owner's three answers are in; what is left is one cloud item (below).

**`docs/DESIGN-court-record.md` is the paper, and §9 now carries the owner's answers:**

1. **A communication record, not a truth record.** The export lists what the two parents recorded
   and wrote in the app and when, and says on its face that it does not say whether any of it is
   true.
2. **Append-only: chat (already) and full event versioning.** The owner chose versions over the
   recommended edit trail — every saved revision of an event is kept whole, in its own immutable
   document. §4 records both shapes and why the trail lost.
3. **Clock: epoch millis on every compared field, plus a server-stamped `recordedAt` beside the
   device time on each revision.** The export prints both, labelled.

Two findings from writing the paper still stand and are worth keeping in view:

**The chat is already an unalterable record.** `firestore.rules` sets `allow delete: if false` on
`messages`, and update is two disjoint `hasOnly` branches — `isRead` alone, or a constrained
`conversationId` re-point. Content, sender, timestamp and attachments cannot be changed by either
parent. The activity feed rides the same collection. That guarantee is now **pinned** in
`firestore-tests/rules/event-versions.test.js`, so a rule edit that widens the `hasOnly` fails a
test instead of passing silently.

**Events were the weak half, and are now versioned.** `event_versions/{versionId}` holds one
document per create, update and delete of a non-private event — the event in its wire format,
`editorUid`, `deviceTimeMillis` and a `recordedAt` the rule pins to `request.time` — and the rule
allows `create` only. See CLAUDE.md item 25 for the invariants and the design doc §4 for why the
collection is top-level, why there is no stored revision number, and why a missing event does not
refuse a revision.

**Left from answer 3 (☁️ cloud, M):** `Event.updatedAt` is still a naive `LocalDateTime`, and it is
compared — `ConflictResolver` decides a sync conflict on it. Move it to epoch millis the way SEC-4
moved custody, reading `domain/custody/CustodyTimestamp.kt` first: the Firestore field keeps its
name and ISO-string type and only changes the zone it expresses, so a co-parent on an older build
keeps reading it. The revisions do not depend on this, which is why it did not ship with them.

**Left as a limit, not a task:** the events rule does not *require* a revision beside each write,
so an older build or a modified client can still edit without recording one. Demanding it
(`existsAfter`) would refuse every edit from a co-parent on an older build; it waits until the app
can require an update (design §8).

### MON-5 · **BUILT; THE OFFICIAL WORDING IS STILL OWED** · P1 · M · Digitise the official Rodičovský plán

**Where:** ☁️ the machinery is written; 💻 **one thing only you can supply — see the end.**

The Ministry of Justice publishes an official parenting-plan template
(`vyzivne.justice.cz/rodicovsky-plan`). In practice each parent fills it in separately and a mediator
or OSPOD compares the two to surface agreement and disagreement.

**What ships.** A parenting-plan screen off Settings: seven sections, fourteen questions, each
parent answering their own half, the two shown side by side, and a tick that turns two answers into
an agreement. `parenting_plans/{familyId}` holds both halves in maps keyed by uid, and
`firestore.rules` lets each parent write **their own key and nothing else** — the security model is
the feature, because a parent who could edit the other's half could put words in their mouth in a
document the two of them may hand to a court. Thirteen rules tests pin that, including the two ways
of getting it wrong that look like it works: writing into the other key, and smuggling your own key
in beside theirs.

**The one idea worth not undoing.** An agreement records **the wording** the other parent had, not
a flag on the question. A flag would go stale the moment either answer was edited, and neither
phone can clear the other's — a parent may only write their own half — so an agreement would keep
claiming two people had settled text that no longer exists. Comparing the stored text makes it
lapse by itself, on both devices, with no cross-write and nothing to clean up.

**What is still owed, and it is the half that makes this a moat.** The **official** wording could
not be obtained: `vyzivne.justice.cz`, `msp.gov.cz` and `edukace.cochem.cz` are all refused by the
egress policy the cloud sessions run under, and search results paraphrase the form rather than
reproduce it. So the fourteen questions are *this project's*, covering the areas § 858 of the
občanský zákoník names as parental responsibility plus the practical headings the Ministry's own
page lists — and the screen says so to the user, because a document that claims to be the Ministry's
form and is not would be design rule 8's forbidden promise in the one place it does real damage.

**To finish it:** put the form's text in front of a session — the PDF, or its sections pasted in.
Replacing the catalogue is a data edit: `ParentingPlanCatalogue` holds ids, the five
`parenting_plan_strings.xml` hold wording, `PlanStringsTest` fails the build if the two drift, and
stored answers keyed by an id that survives are untouched. Then, and only then, the disclaimer
comes out. Audit §10.6.

### MON-6b · **CONTACT WINDOWS DONE** · P2 · S · Contact afternoons on top of the whole days

**Where:** ☁️ done in a cloud session; 📱 the mixed-version check below needs two phones.

`CustodyModel` assigns each day of the cycle to exactly one parent (`momDayIndices`), so an
arrangement of the form "every second weekend **plus Wednesday afternoon**" — which is most Czech
contact orders, not an edge case — could only be entered by rounding the afternoon up to a whole
day or dropping it.

**Owner decision (September 2026): keep one parent per day, and overlay "contact windows".** A
window is `{cycle day, from, to, parent slot}` (`domain/custody/ContactWindow.kt`), repeating
with the cycle exactly like `momDayIndices`. `getCustodyFor` is **unchanged** — whose *day* it is
does not move for an afternoon, so the grid's colour, the handover walk, swaps and every build
already shipped keep their answer — and `CustodyModel.contactWindowsOn(date)` is the new question.
What it took, and the choices worth knowing:

- **Storage.** Room `custody_models.contactWindowsJson` (schema 36, `MIGRATION_35_36`, null =
  none) and the document's `contactWindows`, both as lists of `ContactWindowCodec` strings
  (`"9|15:00|19:00|dad"`) — never a Gson serialisation of the data class. The proposal sub-map
  carries its own list.
- **Older builds, and why a missing key is not "none".** An older build rewrites the whole
  document with `set()` and cannot carry a key it has never heard of. So: this build always writes
  the key on a pattern write (`[]` for none); a document *without* it is read as "written by an
  older build" and the mirror keeps this device's copy; and proposal/swap writes send the stored
  list back **verbatim** (`SharedCustody.contactWindowsWire`), because `firestore.rules` now
  refuses a proposal-only or swap write that *changes* `contactWindows` — a pattern change riding
  on a write whose banner is suppressed — while allowing one that **drops** it, which is exactly
  what an older co-parent's swap or proposal does. Cases in `custody-models.test.js`. A proposal
  sub-map with no list (an older proposer) keeps the agreed windows rather than removing them.
- **Equivalence and the diff see windows.** `isEquivalentTo` compares each date's windows by
  content, so a pairing conflict that differs only in the afternoons is shown, not silently
  settled; `CustodyPatternDiff.contactWindowsChanged` stops a windows-only proposal being described
  as "nothing on the calendar would change". `complemented` flips each window's slot with the days.
- **Setup.** A "Contact windows" section under every pattern type (weekday, every week or one
  week of the cycle, from/to via the existing `TimePickerDialog`, which parent). **The MON-6
  midweek toggle is left exactly as it was** — it is the whole-day-with-overnight shape, and no
  saved schedule is converted — and its warning now points to a contact window for the
  afternoon-only case instead of to `CUSTOM`, which could never express one.
- **Calendar.** Day and Week draw an hour band in the window parent's custody tint over the cell's
  own base (weekend grey survives inside it), with a full-hue edge — the saturation rule's two
  strengths of one hue. Month marks the day with a small corner triangle in the window parent's
  full hue, laid over everything else, so the weekend base, the band and the handover diagonal
  read as before; the hours are in the cell's description and one tap away in Day view. A window
  naming the parent who already has the day (the pattern's, or an accepted swap's) is not drawn.

- **Home's today card** (September 2026) lists the day's windows under the line that says whose
  day it is — "15:00–19:00 · contact with Alex", marker in the window parent's hue, name through
  `ParentNames`. The filter moved out of `CalendarScreen` into
  `CustodyResolver.contactWindowsResolver`, which the grid and `HomeViewModel` both call with their
  own custody lookup, so a window naming the day's own parent (or a parent an accepted swap gave the
  day to) is dropped in both places by one rule. `TodayAgenda.contactWindows` carries it; tests in
  `CustodyResolverTest`, `HomeWeekTest` and `HomeViewModelTest`.

**Left.** The mixed-version path — one phone on this build, one on an older one, a swap and a
proposal each way — is covered by the rules suite and the unit tests but has not been run on two
devices.

### MON-8 · P2 · L · Bakaláři / EduPage school import

**Where:** ☁️ cloud for the parser and the mapping — but 💻 you have to supply one real export
file, or it is written blind.

MVP 3 listed it as **XL, Low**. It is the highest strategic item in the roadmap and mispriced. Every
Czech parent's school schedule lives in one of those two systems; an import that fills the calendar
on day one solves cold-start, is a local moat no US competitor will build, and is the most credible
reason to choose CoPlanly over a generic shared calendar. Document understanding makes the XL
estimate smaller than when the line was written. Audit §6.4, §7.3.

**And decide where an import lands**, which M-4 sharpened: Google Calendar imports became private
precisely to dissolve that question, but a *school* import is the opposite — it is about the child,
so it belongs to a family and must be shared. With two families on one account, "the selected one at
import time" is a footgun the first time somebody imports while looking at the wrong family.

**Settings already announces it, and that announcement has an expiry.** The Sync group carries an
inert row — muted, not tappable, captioned "not built yet", badged *Planned* — so that a Czech
parent opening Settings learns the app means to read their school's system, which is the first
question this product will be asked. It is not the affordance design rule 8 forbids, because it
promises nothing it cannot do and says so in words rather than by being greyed out.

**But it is a promise with a clock on it.** Whoever finishes this item turns that row into the real
import; whoever abandons the item deletes the row and its three strings in all five locales
(`settings_school_import_*`). A *Planned* badge still sitting in Settings a year from now is rule 8's
lie arriving slowly instead of at once, and it will be nobody's job unless it is written here.

### MON-9 · P2 · ongoing · Distribution: the channel is professional, not search

**Where:** 💻 yours — phone calls and meetings. A session can draft the material.

This audience does not search for the category — it is handed to them at a specific moment, by a
professional, during the worst month of their year. Audit §10.5.

- **Mediators.** A Czech court can order a first meeting with a registered mediator, up to three
  hours (§ 100(3) o.s.ř.) — a guaranteed moment with **both parents present at once**, the hardest
  thing to arrange in this market. One source puts registered *family* mediators at ~25, about half
  active; if that holds, the entire channel is coverable personally in a week.
- **Courts running Cochem practice** (Nový Jičín since 2016, Most since 2017).
- **OSPOD** offices at municipalities with extended competence.
- **NGOs and portals**: stridavka.cz (which already publishes a co-parenting tools roundup — a
  directly reachable placement), zustavamerodici.cz, APERIO, sancedetem.cz, Unie otců, Liga
  otevřených mužů.
- **The OFW playbook, localised**: free professional accounts with unlimited clients, plus promo
  codes to hand to families. Revenue-sharing with courts is neither available nor legally plausible
  in Czechia; free professional accounts are.

### MON-10 · **DONE** · Re-baseline the roadmap

This document is it. §2 is the re-baseline, and `MVP_phases.md` and `BACKLOG.md` were merged here so
there is one place to be behind rather than two.

### MON-11 · P2 · L · Payments (MVP 3)

**Where:** ☁️ cloud for the code; 💻 the Play merchant and tax setup is yours. Gated on **MON-1**.

Two different things travel under this word in the MVP plan, and they should not be built together:

- **The entitlement layer** — Play Billing, a paywall, restore-purchases, and the server-side check
  that the second parent inherits the family's entitlement. This is what MON-1 decides the shape of.
- **Parent-to-parent reimbursement** — actually moving money between two parents. **Onward closed on
  8 October 2024** built entirely on this. The balance is already computed per currency; the honest
  first version is an export and a payment link, not a payment rail.

**The first thing the entitlement layer gates is MON-3's export**, which ships ungated until then
(September 2026). Decide with MON-1 whether an export already made keeps working after a
subscription lapses — it should: the file is the parent's, and a record that vanished on
non-payment is the opposite of what it sells — and gate the *making* of a new one, in
`ExportViewModel.export`, behind a server-checked entitlement rather than a client flag.

### MON-12 · P3 · M · Intelligent suggestions (MVP 3)

**Where:** ☁️ cloud, and only behind **SEC-1**'s proxy.

MVP 3's "suggestions based on past schedules". Two roads, and only one needs a model at all:
patterns in the existing custody and event data are ordinary computation on-device, while anything
generative goes through the proxy with no key in the client.

If a model does return, the ranking from MON-7 stands: **tone check before sending** is what
competitors charge for everywhere (OFW's ToneMeter, TalkingParents' Sentiment Scanner,
CoParently.de's tone detector at €4.99). Two hard constraints, both non-negotiable: it must **never
block** sending, and the analysis must **never be stored** — a saved "your message was aggressive"
verdict is discoverable material in a custody dispute, which makes it a liability to the user rather
than a feature. Anything resembling emotion inference deserves a legal read under the EU AI Act
before launch.

### MON-13 · **TABLES AND REGIONS DONE** · P2 · M · Holidays by country — school vacations are left

**Where:** ☁️ done: the setting, the registry, five tables verified against a maintained dataset,
and Germany's sixteen Länder. What remains (school vacations outside Czechia, Austria's
patron-saint days) is a product decision before it is code.

MVP 1 asked for "holidays and vacations by country" and shipped one country. There was **no country
setting anywhere in the app** — no field, no picker, not even a constant — so `CalendarScreen`
called `CzechHolidays` directly and a German, Russian or Ukrainian family got Czech public holidays
on their grid, in an app that ships in five languages.

**Done: the country is now a stored, chosen fact.** `User.countryCode` (Room schema 33,
`NOT NULL DEFAULT 'CZ'`, so every existing account becomes Czechia and nobody's calendar changes),
a picker on the wizard's profile step beside the parent colour, a Settings row, and
`HolidayProvider` + `HolidayCountry` where the hardcoded call used to be. A country is stored
rather than "which holidays to show" because other features will want it — currency defaults,
which legal text applies, whether a school import is even available (MON-8).

**Deliberately per parent, not per family.** Two separated parents can live in two countries, and a
public holiday is a fact about where *you* are. This reverses what an earlier draft of this
document said, and the reversal has a cost worth stating: the school-vacation strips, which are
genuinely about the child's school, follow the viewer too. A per-family school calendar is the
honest fix and is part of what is left.

**Done (September 2026): the tables, from verified data.** In August the owner, offered a choice
between authoring the five tables from memory with a "needs a native check" marker and waiting
for verified data, chose to wait — a holiday table is a set of user-visible facts and a wrong date
is worse than no date. That decision is **superseded, not overruled**: its condition was met. An
independent, maintained dataset became obtainable — the Python `holidays` library (v0.105,
MIT, community-maintained, citing the legislation behind each rule) — and the tables were written
against it rather than from memory.

- **How it is verified.** The providers (`SlovakHolidays`, `GermanHolidays`, `AustrianHolidays`,
  `RussianHolidays`) are plain computed Kotlin with no runtime dependency, written as rule tables
  (`HolidayRule.kt`: ISO month-days and named Easter offsets from the shared
  `gregorianEasterSunday`). `tools/generate-holiday-fixture.py` writes the library's output for
  2020–2035 into `HolidayReferenceFixture.kt`, and `HolidayReferenceTest` compares every year —
  dates and both names — with what the providers produce. Re-run the script when bumping the
  library; a changed line is a law that changed or a library correction, and either way the
  provider changes with it.
- **Slovakia** — public holidays by year, which is exactly what memory would have got wrong:
  1 September off until 2023 (Act 530/2023), 17 November until 2024, and 8 May and 15 September
  working days in 2026 only (Act 261/2025). Only days off are drawn.
- **Germany** — the nine **nationwide** holidays, plus the chosen Land's own (below). A parent who
  has not named a state sees the nine; said in the KDoc, the same trade Czechia's
  district-dependent spring break makes.
- **Austria** — the thirteen nationwide holidays. Good Friday (Protestant-only until 2019), 24
  and 31 December and the Länder patron-saint days are bank holidays in the reference data and
  are not drawn.
- **Russia** — the fourteen statutory days of Labour Code art. 112. The annual transfer decree
  (bridge days, and the day in lieu of a holiday that fell on a weekend) is **not computable for a
  future year** and is not drawn; the library lists it per decreed year only, and the generator
  filters those rows out of the comparison with the reason written beside the filter.
- **Ukraine** — deliberately **no provider**. Under martial law (since 24 Feb 2022) public
  holidays are not days off, and the library returns none from 2023. Computing the pre-war list
  would draw days off nobody has; the picker now says *why* nothing is drawn
  (`HolidayCountry.holidaysSuspended` → `country_holidays_suspended`) instead of "not in the app
  yet", which was true of the app and false of the country. When martial law ends, add the table
  the law then describes.
- **The picker states coverage per country** (`HolidayCountry.coverage`): holidays and school
  vacations (Czechia), public holidays only (four), suspended (Ukraine), none (Other). It is
  derived from the provider, so it cannot promise school vacations a provider does not return.
  The calendar filter's "Czech holidays" title became "Holidays" in all five locales.

**Done (September 2026): the region, for Germany.** `User.regionCode` (Room schema 35, nullable
with no default, so every existing account is "nationwide" and nobody's calendar changes) is an
ISO 3166-2 suffix, synced to `users/{uid}.regionCode` beside `countryCode` (`""` for none, so a
cleared region is cleared by the merge). `HolidayProvider.regions`/`forRegion` and
`HolidayLocation` carry it to the grid; `HolidayCountry.regionOrNull` drops a code that is not
the country's, and changing country clears it. The picker is a second chip row under the country
chips on the wizard's profile step, and a second Settings row ("State") with its own dialog —
both **only when the chosen country has regions**. The coverage note says which it draws:
nationwide only with a nudge to pick a state, or the state's days as well.
- **The data.** `GermanState` holds each Land's additions; `generate-holiday-fixture.py --regions`
  writes `HolidayRegionReferenceFixture.kt` (only the days each state *adds*, and the script
  refuses to write it if a state's list is not a superset of the nationwide one), and
  `HolidayReferenceTest` rebuilds every state's list 2020–2035 as nationwide + those days.
- **Left out on purpose**, and said in `GermanState`'s KDoc and the generator: the library's
  `catholic` category (Assumption Day in Bavaria, Corpus Christi in parts of Saxony and Thuringia
  — holidays only in Catholic-majority *municipalities*, which a state cannot identify), and
  **Augsburg**, which the library models as a pseudo-subdivision for a city holiday. Kept: Berlin's
  one-off anniversaries (2020, 2025, 2028) and Brandenburg's statutory Easter and Whit Sundays.
- **Austria has no region picker, deliberately.** The library returns **no** regional *public*
  holiday for any of its nine Länder; the patron-saint days (St. Leopold, St. Joseph, St. Florian,
  …) are in its `bank` category — school-free and many offices close, but not statutory days off.
  A state setting that changed nothing on the grid would be design rule 8's promise. **Owner call
  if wanted:** draw the patron day as a separate, labelled kind of day, which needs its own name on
  the grid rather than passing as a public holiday.

**Left.** No school vacations outside Czechia — Germany's and Austria's are set per state,
Slovakia's spring break per region, Russia's per region or school — and none were invented. The
region setting makes Germany's per-state school calendars *reachable* (the library carries them),
but school vacations still follow the viewer rather than the child, so a per-family school
calendar remains the honest fix for the per-viewer strips described above before any are drawn.

---

### Where to be stronger than the competition (September 2026)

`docs/COMPETITORS-2026-09.md` compares the app with AppClose feature by feature. Two gaps are
worth closing outright (**MON-14**, **MON-15**). The rest of this block is a bet: build where
AppClose is structurally weak rather than copy it. Its weak points:
- **per-parent billing** that turns a non-payer's account read-only;
- **US data residency**;
- **English and Spanish only**;
- **iOS/web reach we cannot match soon**;
- **an export whose trust rests on the vendor's affidavit**.

Each item below says which of those it answers.

**A principle for MON-1, not a feature: communication never goes read-only.** AppClose's
"Read-Only Mode" for a parent who does not pay is its most-cited complaint, and lawyers have
written advisories about it: a paywall that can stall a court-ordered channel is a liability. Whatever
MON-1 decides, messaging, the calendar and the custody schedule stay usable by both parents
regardless of who pays. The paid tier is documentation (export, verification, versions),
never the ability to talk.

### MON-14 · P1 · M · Seasonal schedule layers (summer, school holidays)

**Where:** ☁️ cloud; 📱 a look at the grid.

A base pattern plus **layers with a date range that override it**. AppClose's precedence is
holiday > summer > regular. In CZ/DE, summer care is routinely split differently from term time
(e.g. two blocks of two weeks each), and today the only way to enter that is day overrides, one
day at a time.

- A layer is `{name, from, to, pattern (the same shape as the base: cycle length, day
  indices, contact windows), priority}` stored **inside** the custody model document. It is not a
  second model: `CustodyModel.getCustodyFor(date)` resolves the layers first, the base second.
  `getCustodyFor` stays the one question (item 24's rule that a window never splits a day holds
  within a layer too).
- Wire form: a codec of plain strings, like `ContactWindowCodec`. **A missing key is not "no
  layers"**, the same older-build rule as item 24: the mirror keeps its copy when the key is
  absent. Proposal and swap writes carry the stored list verbatim, and the rule refuses a
  proposal-only or swap write that changes it.
- **The part AppClose does not have:** "Fill from school holidays". The Czech vacation table
  (and the German Länder once MON-13 has school vacations) proposes the layer's dates; the
  parents confirm. Never auto-applied — a proposal, like every other schedule change.
- Holiday fairness (below, **MON-20**) reads these layers.

### MON-15 · P1 · S · Search in chat

**Where:** ☁️ cloud.

Search over this device's Room copy of the thread: SQLite FTS4 on `messages.text`, or a `LIKE`
if FTS proves awkward under SQLCipher. Results jump to the message in context. It never queries
Firestore: the mirror already holds the thread, and a server-side search would need an index
that exposes message text to a service. Local-only is also the privacy answer. It needs a schema
bump and a migration (run the Regenerate workflow after *this* bump, not after a batch — see
CLAUDE.md on the missing `35.json`). Search in the export (MON-3) comes free with the same
query.

### MON-16 · P1 · S · A verifiable export, without anybody's affidavit

**Where:** ☁️ cloud (functions + rules + client).

**Answers:** AppClose's "certified records" rest on the vendor's affidavit. This does the same job
with arithmetic.

Every generated export (MON-3) gets:
- a **record ID** and a **SHA-256** of the exact file bytes;
- both registered by a callable in `export_receipts/{id}` with `recordedAt = request.time`
  (server clock, per MON-4's decision), the family id, the generating uid and the date range.

The PDF footer prints the ID and a short verification instruction. A lawyer, or the other
parent, uploads the file to a static verification page (hosted with the privacy policy, REL-4).
The page hashes it **in the browser** and asks the callable whether that hash was registered, and
when. The file itself never leaves the verifier's machine. A single altered byte fails. Pairs
with a test that pins message immutability in `firestore.rules` (called out as missing in
`DESIGN-court-record.md`).

### MON-17 · P1 · M · A calendar feed for a co-parent on an iPhone

**Where:** ☁️ cloud (functions); 📱 subscribing from an iPhone.

**Answers:** the iOS gap, the one that outweighs any single feature. A native iOS app is an owner
decision and not planned. A read-only **ICS subscription** lets an iPhone parent see the custody
days and shared events in Apple Calendar today. It is not a substitute for the app (no chat, no
changes), and the settings text must say exactly that (design item 8).

- An HTTPS function serves `text/calendar` for a **secret, revocable token** (random ≥128 bits,
  stored hashed, one per subscriber, revoked from Settings). The token is the whole
  authorisation, so it names one family and one subscriber and expires if unused.
- Content: custody days as all-day events titled with the parent's *name* (never Mom/Dad),
  contact windows as timed events, and non-private shared events. **Private events never**
  (item 3), tombstoned events never.
- Rate-limit it and cache per token. Calendar clients poll hourly, and the function must not fan
  out to Firestore per poll.

### MON-18 · P1 · M · Free, expiring access for a mediator or lawyer

**Where:** ☁️ cloud; 📱 the invite flow.

**Answers:** AppClose Pro. Mediators are this product's distribution channel (MON-9), and a free
portal is how AppClose earns their recommendation.

A **professional grant** beside the calendar friend (item 16):
- central, family-scoped, **always expiring**, and granted by **both** parents: each parent's
  consent is a separate key, the same own-key-only shape as the parenting plan;
- read-only access to the calendar, the parenting plan and the exports the parents choose to
  share. **Never** the chat by default: a parent may attach an export instead.

No new portal app. The professional uses the same Android app, or the web export verification
(MON-16). A web read-only view is a later step.

### MON-19 · P2 · S · A pause before sending

**Where:** ☁️ cloud.

**Answers:** tone checks (Co-Parent Assist, ToneMeter) **without** a model, a key or a data
flow. An opt-in setting ("Give me a moment before sending") holds an outgoing message for a
short, cancellable interval with an Undo, like Gmail's undo send. There is also a gentle,
purely lexical nudge (all-caps, several exclamation marks, words from a short per-locale list)
that **never blocks and never stores anything**. It is honest about what it is: no "AI". A real
tone model remains MON-12, behind SEC-1's proxy and an EU AI Act review.

### MON-20 · P2 · S · Holiday fairness at a glance

**Where:** ☁️ cloud.

**Answers:** nothing a competitor ships. The holiday tables (MON-13) and the schedule are both in
the app, so it can answer: over this year and the next, who has Christmas Eve, Easter, the
children's birthdays and each school break, and how many nights each parent has in total. Shown
as a small read-only summary in the custody settings, with the proposal flow (never an automatic
change) when a parent wants to rebalance. It uses **MON-14**'s layers.

### MON-21 · P2 · M · From the parenting plan to the schedule

**Where:** ☁️ cloud.

**Answers:** the Czech parenting plan (MON-5) is a document today. When both parents have agreed
the plan's custody and holiday sections, offer to **propose** the matching schedule: a base
pattern and MON-14 layers, through the normal proposal and accept flow. The agreement already
records the exact wording (item 21), so the proposal can cite the answer it came from. Unique in
CZ, and a reason for a mediator to recommend the app.

### MON-22 · P2 · M · A private journal

**Where:** ☁️ cloud.

**Answers:** AppClose's journal and notes. Entries are **local-only by default** (Room, under
SQLCipher, never synced), and a parent may *attach* chosen entries to an export (MON-3/MON-16).
They are not shared with the co-parent: a journal about the other parent that syncs to them is a
different, worse product.

## 8. [FAM] More than one child, more than one pet

Found in August 2026 by asking a question nobody had asked: what happens when a pair is raising two
children, or two children and a dog. Three of the five items are done — the wizard, the "who is this
about" reference, and events knowing who they are about. Two remain.

### FAM-4 · P2 · L · Custody per child

**Where:** ☁️ cloud. **SEC-4** was its prerequisite and is done.

One schedule per pair stays the default; a per-child schedule is an override. It drags Home's
handover hero (singular today), the calendar banners and `getCustody` with it. The reason it waited
was SEC-4: `lastModifiedAt` was a naive local date-time that already decided which phone's schedule
survived, and multiplying the documents would have multiplied that defect before fixing it. That is
now fixed, so the blocker is gone.

Genuinely rarer than FAM-2 and FAM-3 — a teenager who negotiated their own arrangement, an infant
who stays with one parent — which is why it is last rather than never.

### FAM-5 · P2 · S · The event chip does not say who it is about

**Where:** 👁 the treatment is an owner's call on a real device; the code is small once decided.

Only reachable in an unfiltered day or week view, and only for a family with two or more members.
The constraints are the interesting part: `softWrap = false` plus ellipsis means a prefix costs
title, and pink/blue/teal/grey are taken by the parent slots, a calendar friend and the weekend —
and M-4 spent two more hues (purple, orange) on chosen parent colours, so the colour channel is now
comprehensively unavailable. An initial-letter avatar at chip height is the obvious candidate; so is
doing nothing and leaving the filter to answer it.

---

## 9. [M] More than one co-parent

`docs/DESIGN-multi-family.md` is the plan of record. M-1 … M-4 shipped in PR #76: `families/{id}` as
a first-class document, `familyId` on the six shared collections, the slot and `caresFor` moved onto
the family, and the switcher — plus the isolation the whole thing exists for, a chosen parent colour
and a second co-parent you can actually invite.

**None of it is live until REL-3's ops sequence runs.** The rules are deployed last, on purpose.

### M-5 · P2 · M · Cleanup

**Where:** ☁️ cloud — but only after REL-3's ops sequence has run and settled.

Delete `partnerId`, `User.role`, `Event.sharedWith`, `isPartnerOf`. Each is written today so that a
co-parent on an older build keeps working; each is a second source of truth until it goes. Do not
start this while any device might still be on a pre-#76 build.

### M-6 · **DONE** · A calendar friend was granted per person, not per family

The defect PR #76 existed to close, in the one collection it did not reach.
`calendar_friends/{friendUid}` stored `familyParents: [uidA, uidB]`, and `isCalendarFriendOf(owner)`
asked only whether `owner` was in that array — while the `events` read rule reached it with the
event's **creator**. So a grandmother admitted by Alice-and-Bob read every event **Alice** created,
including the ones belonging to Alice's family with Carol, whom she has never met. Same shape as the
expenses leak: the rule asked "am I a friend of this author", never "is this record mine to see".

The grant now carries the `familyId` it was issued for, and the rule requires the record's own
`familyId` to match **and** its creator to be one of that family's two parents. Four things worth
keeping:

- **The second check is not redundant.** An event's `familyId` is client-written and the create
  rule does not pin it (M-2), so without `ownerUid in familyParents` any account could stamp a
  foreign family's id onto its own event and have it appear in that family's friend view.
- **Narrowing a widening disjunct is safe to deploy early.** The friend branch only ever *adds*
  access, so the condition can deny a friend but can never deny the app its own writes — unlike
  pinning `familyId` on a create rule. An unstamped record is simply invisible to a friend until
  `backfillRecordFamilyIds` has run, which now stamps the grants too (one pass, no fifth ops step).
- **The client query shape changed, and the old one is now rejected outright** rather than
  serving a subset — measured in the emulator, not reasoned. `whereIn("createdByFirebaseUid",
  [a, b])` cannot satisfy a rule keyed on the record's own family; `whereEqualTo("familyId", …)`
  plus the creator clause can. Nothing in the app issues either yet — the friend's calendar view
  is still unbuilt — so this cost nothing today and is pinned by a test for whoever builds it.
- **The family is chosen when the code is generated, not when it is redeemed.** Those can be days
  apart, and a parent with two families may be looking at the other one by then. The callable
  never trusts the id it is sent: it checks it against the inviter's live co-parents and falls
  back to the family they are showing, which is also what an invitation from an older build gets.

**Lapsed grants are swept** (September 2026): `sweepLapsedCalendarFriends` deletes
`calendar_friends/{uid}` daily at 05:00 UTC once `expiresAtMillis` has passed. Nothing leaked
before it — the rule refuses an expired read at `request.time` — but the row lingered in the
parents' list. A grant with no positive numeric expiry is never swept: the callable does not write
one, and the rule admits nothing through it.

### M-7 · P3 · S · Which family does an imported calendar belong to

**Where:** ☁️ cloud, once decided.

M-4 dissolved the urgent half by making Google Calendar imports `isPrivate` — a personal calendar's
contents are exactly what a co-parenting app has no business forwarding, and a private event belongs
to nobody but its owner. What is left is only the question of whether a *shared* import should ever
exist. If it should, the honest answer is a per-calendar mapping, which is a screen of its own; the
cheap answer, "the selected family at import time", is a footgun the first time somebody imports
while looking at the wrong family. `CalendarSyncRepository` says so at the call site, and that line
is where the answer goes. Related: **MON-8**, where a school import is the opposite case — it *is*
about the child and must be shared.

### M-8 · P2 · S · What M-4 deliberately left — chip, pushes, chat and the chat dot done

**Where:** ☁️ cloud for what is left; a phone with two paired accounts for acceptance.

- **Done — a switcher chip in the top bar** of Home and Expenses (`presentation/common/
  FamilySwitcher.kt`, `FamilySwitcherChip`), beside the gear, naming the family on screen by its
  co-parent. It **appears at two, not at one**: a parent with one co-parent sees the top bar they
  always saw. It opens the dialog the Settings row opens — the dialog moved out of `SettingsScreen`
  into the same file, and both entry points share `FamilySwitcherViewModel`, which replaced
  `SettingsViewModel`'s own family state. The list is now *observed* off the signed-in Room row
  (`SelectedFamilySource.observeFamilies`) rather than reloaded when Settings opens, because a chip
  has no "opens" moment; the co-parents' names stay the one remote read, one per family, only at
  two or more, cached per ViewModel. Not on the Calendar header, deliberately: design item 5 gave
  that row a fixed four (title, Today, Filters, gear) and a person's name would squeeze the
  Month/Week/Day title first. Not on Chat either, for the reason in the badges bullet below.
- **Done — pushes carry `familyId`** (`PushPayload.FAMILY_ID`, a field, not a type, so item 15's
  four-place rule does not apply). `FcmService.queueNotificationForUser` stamps every client push
  with `FamilyKey.orNull(sender, addressee)` — one place, because a push goes to one co-parent and a
  pair *is* a family — and the Cloud Functions stamp `chat_message` (its conversation id, which is
  the family id) and `pairing_accepted` (the new family, so the inviter lands on it).
  `pairing_removed` names none: that family is gone. The rule's new `isPushFamily` bounds the key
  at 258 characters (two 128-character uids and the separator) **and** requires both the sender and
  the addressee to be in it, read off the id itself with no document read; `firestore-tests`
  `notification-payload.test.js` pins accept/foreign-to-sender/foreign-to-addressee/overlong/
  non-string. On the phone, `CoPlanlyMessagingService` puts the id on the tap intent as an extra
  *and* into the PendingIntent request code (extras are not part of a PendingIntent's identity, so
  two same-typed pushes from two families would otherwise share one and the older notification
  would switch to the newer one's family), and `MainActivity.readLaunchIntent` switches through
  `SelectedFamilySource.select` **before** it arms any deep link, since `NavGraph` navigates the
  moment one appears. `select` refuses a family the account is not in, so a stale notification
  opens on whatever is showing.
- **Done (September 2026) — chat follows the selected family.** This was step (1) of the order
  the badges bullet used to give, and a defect in its own right: `ChatViewModel.unreadCount`,
  `coParentLink` and `ChatMirror` keyed on `PairingRepository.observePairingState()`, which reads
  the **server's** `users/{uid}.partnerId` — `partnersOf(...)[0]`, the *first* co-parent — so the
  Chat tab, its badge and the process-wide mirror followed the first family whatever the switcher
  said. They now read `data/chat/ChatPartnerSource`, which joins the pairing state with
  `SelectedFamilySource.observe`: the server decides **whether** there is a co-parent (`Loading`
  → resolving, `NotPaired` → nobody, whatever a stale Room row says), the projection decides
  **which**, and the server's partner is the fallback for the moment after a first pairing before
  the row has caught up. A one-family account resolves to the same uid either way and does not
  re-emit, so it sees exactly what it saw before. `ChatMirror` keeps every CQ-8 guarantee —
  `ensureConversation` awaited before either listener, the outer five-minute restart loop, the
  bounded eight-attempt inner retry, the `.catch` — and its `collectLatest` now cancels the old
  thread's two listeners on a switch before the new thread's conversation is ensured, so the
  mirror holds one family's listeners at a time, never an accumulating set. `ChatMirrorTest`
  pins the re-key and the released listener; `ChatViewModelTest` pins the link, the thread, the
  badge and the co-parent action moving with the selection, and a one-family account not
  flickering; `ChatPartnerSourceTest` pins the rule. **Not seen on a device**: see §1's
  "👁" table. The Chat tab still carries no switcher chip — the original reason is gone, but the
  tab renders the thread in place (design item 7) and `ChatThreadHeader` already names the
  co-parent, so adding one is a layout decision rather than a fix.
- **Not done, by design — badges that *count* across families.** `ChatMirror` mirrors **only**
  the selected family (a switch cancels the previous one's listeners — the alternative is N
  listener pairs for the process lifetime), and a thread's own `observeMessages` runs only while
  it is open. So a non-selected family's messages reach Room only when that family is selected
  again, and a Room `COUNT(*)` across conversations would **undercount every family not on
  screen**, silently; a badge that says 0 when it is not is worse than none (design item 8). The
  bottom-bar and Home badges count the **selected** family only (Home through the Room
  `partnerId`, Chat through `ChatPartnerSource`).
- **Done (September 2026) — a dot for the other families' chat.** Step (2) of the order this
  bullet used to give. `data/chat/OtherFamiliesUnreadSource` holds one **conversation-document**
  listener per family *not* on screen — the messages collection is never read — and derives "has
  unread" from `lastMessageAt > lastReadAt[me]` (`ChatReadState.hasUnread`, strictly newer, so a
  mark written at the newest message covers it). It is a yes/no, so the switcher chip carries a
  Material `Badge` dot and each dialog row its own, **never a number**, each with a content
  description (`family_switcher_unread_other`, `family_switcher_unread_row`). Properties the code
  holds and the tests pin (`OtherFamiliesUnreadSourceTest`, `FamilySwitcherViewModelTest`,
  `ChatReadStateTest`): **no listener at all at one family**, and the state refuses a dot at one
  family or for the family on screen even if the source says otherwise; the listeners are one
  `shareIn(WhileSubscribed)` for the process, so Home's chip, Expenses' chip and the Settings
  dialog share them and nothing listens while no switcher is on screen; `flatMapLatest`
  re-derives and **cancels** them on a switch, an unpair, a new pairing or a sign-out; and a
  failing listener retries with `reconnecting()`'s bound (eight attempts, exponential, capped at a
  minute) and then gives up to "no dot" rather than retrying for the process's life — the next
  subscription starts it again. **No rule change**: `conversations` already allows a participant
  to read (`get`/listen to) their own document. A conversation that does not exist yet reads as
  *denied* (the rule keys on `resource.data.participants`), which is why the give-up path matters:
  the source never creates a conversation, it only reads one. Two known limits, both small:
  `lastMessageAt` does not name its sender, so a message **I** sent could raise my own dot if my
  read mark never reached the server (sent offline, then switched away) — the open thread
  re-asserts the mark on every change to its messages, own sends included, so the normal path is
  covered; and the dot says *chat* only. Cost: N−1 single-document listeners while a switcher is
  on screen, zero for a one-family account. **Not seen on a device** — see §1's "👁" table.
- **Not done — the same question for change requests and custody proposals**, whose queries
  resolve through the projected `partnerId` and so see only the selected family by construction.
  Each would need its own per-family document read or listener, and neither has a single
  document per family the way a conversation does; until then the push (which switches the family
  on tap) is the cross-family signal for those.

---

## 10. The order to actually do it in

Not a wish-list ordering — a dependency ordering. Each block assumes the one above it.

**This week, and none of it is code**

1. **REL-3's ops sequence** — deploy functions, run the two backfills, deploy the rules. Everything
   from both audits *and* all of PR #76 is inert until this runs, and one of the fixes closes a live
   full-calendar disclosure. The same rules deploy carries MON-4's `event_versions` block; until it
   lands, every event revision waits on the phone that saved it. Trigger the Regenerate workflow
   for `app/schemas/.../37.json` on the same day — CI's schema guard is red until it is committed.
2. **REL-3's storage deploy** — one command; without it every pet and medical photo upload is
   refused on a live device today.
3. **REL-1's console half** — a local build fails until it is done.
4. ~~**MON-2 §1** — find out whether app2us "Rodina" has an Android build.~~ **Done 23 September
   2026: it does** (Google Play, `com.app2us.family`). MON-1 is now priced against a Czech
   incumbent on both platforms at 149 CZK/month.

**Then the two CI jobs** — cloud work, and the pair everything later leans on

5. ~~**M-6** — the calendar-friend grant is still per person.~~ **Done.**
6. ~~**CQ-19** — a deleted child or pet never reaches the other phone.~~ **Done**, and it added the
   third migration `app/schemas/` cannot describe.
7. ~~**CQ-12** then **CQ-1** — make detekt gate again, then restore the schemas.~~ **Done**, both
   through `.github/workflows/regenerate.yml`, which does what only a machine with an Android SDK
   can and commits the result back. What is left of CQ-1 is the emulator job, and it is worth
   adding at v34 — with one exported schema there is nothing to migrate *from*.

**Before any launch**

8. **REL-2, REL-4, REL-5, REL-6, REL-7** — keystore, legal, consent, Play Console, and the one
   device test CI cannot run.
9. **SEC-1** — the OAuth callable, and the Storage rules once their verification story is settled.

**Then the product bets, in descending confidence**

10. ~~**MON-4 then MON-3**~~ — **done in that order** (September 2026): the owner decided what the
    record guarantees, events became versioned, and the export shipped ungated. Left: MON-4's
    `Event.updatedAt` migration, and the deploy in §1 ("MON-4 deploy").
11. **MON-5** — the Rodičovský plán. The cheapest local moat and the reason a mediator recommends
    you.
12. **MON-1** then **MON-11** — decide the price before writing the entitlement layer, and decide
    what a subscription means now that a person can have two families. MON-3's export is the first
    thing it gates.
13. **MON-8** — the school import.

**Structural, whenever it fits**

14. **CQ-5**, and **CQ-6 + CQ-8** together. All three grow worse with tenure, so they land on your
    longest-standing users first.
15. **M-5**. (**CQ-14**, **UX-12**, **CQ-13** and **UX-9**, which used to open this line, are done.)

**One thread runs through this document.** The security holes, the release-only Gson corruption, the
plaintext refresh token, the two-year recurrence bug, thirty unit tests failing against a
constructor that changed months ago — none of it was carelessness. They are the failure modes of a
codebase with careful reasoning and, until recently, no automation to check it. CI is not low on the
list because it was urgent; it was built first because everything above it is a symptom. The two
last of that automation — the baseline that lets detekt gate and the schema export that makes a
migration provable — is **CQ-12** and **CQ-1**, and both landed the same way: by giving the cloud a
job that runs the one thing it cannot.

---

## 11. Done — so it is not re-litigated

Kept rather than deleted, because the reasoning is what stops each one coming back. Full arguments
in `docs/AUDIT-2026-08.md` under the § numbers cited.

### September 2026

- **Onboarding links the co-parent first.** The wizard used to end with the invitation, so the
  second parent retyped everything the first had entered. `CoParent` is now the first step; the
  steps after it open on what the link brought back (children, pets, the agreed split, the shared
  schedule), the pairing transition asks for a sync on both phones (`SyncRequester`), the
  backfill announces itself once (`RECORDS_SHARED`), and a schedule saved before pairing is
  published to a pair that has none. CLAUDE.md item 22 has the six rules that hold it up.
- **SEC-6's closed half** — see that entry.

### Security

- **SEC-3 · Notification text is composed on the client.** A push could claim to be anything: the
  sending device wrote `title` and `body`, and the other phone rendered them verbatim with the app's
  own icon, on a lock screen, from someone the reader may be trying to keep at a distance. Composed
  on the **receiving** device now — a payload carries a `type` and the few names it needs
  (`PushPayload`), the app writes the sentence from its own string resources, and **drops a type it
  has no wording for**. That fallback *is* the forgery, so never reintroduce it. Two rules hold it:
  a client payload carrying `title` or `body` **at all** is refused (presence, not size — a length
  bound lets an empty one through), and `data.type` must be in an allow-list that excludes
  `pairing_accepted`, `pairing_removed` and `chat_message`, which only Cloud Functions can produce.
  Adding a type means four places agreeing, and one missing means a push that silently never
  appears.
- **SEC-4 · The custody schedule was ordered by a naive local date-time.** Two phones 2–3 zones
  apart could have the wrong side win **and overwrite**. Now epoch millis (schema 29) and a `>`.
  The interesting half is the wire form — read `domain/custody/CustodyTimestamp.kt` before touching
  it: the field keeps its name *and* its ISO-string type and only the zone changed, because changing
  the type leaves an older build reading a blank (and a blank compares equal to their last
  dismissal, so every future change goes silently un-announced), while adding a numeric field beside
  it puts a new key in `affectedKeys()` and `hasOnly` denies the first such write outright.
- **PR #68/#69** closed: the `calendar_friends` self-issued grant that disclosed a whole family's
  calendar; the `users.partnerId` self-reference revocation bypass; invitations accepting an
  unverified email; `change_requests` forgery; `events` update rewriting the audience; membership
  reads against absent fields; R8 destroying a child's medical profile in release;
  `EncryptedPreferences` falling back to plaintext permanently; personal data in diagnostics;
  telemetry flags nothing read; the Gemini key bound as a bare `String`; unfiltered collection
  queries. Plus **account deletion** (server-side teardown and local wipe — Play's requirement and
  GDPR Art. 17) and **invitation email that actually sends**.

### Correctness

- **CQ-2** — the untested migrations that shipped in `versionCode 2`. Folded into **CQ-1**, which is
  the only place they can be tested from.
- **CQ-3 · Deletions never reached the other parent.** Parent A deleted an event and parent B kept
  it forever; a failed remote delete meant the next sync **restored it locally**. Fixed with
  tombstones (`data/sync/Tombstone.kt`): `update()` with `deletedAtMillis`/`deletedBy` — never
  `set()`, which would replace the fields the read rules are keyed on — plus a Room outbox that
  retries and a 90-day server sweep. Three things not to undo: **do not reconcile by absence** (it
  takes the whole calendar the first time an audience narrows or a snapshot comes back partial),
  **do not decide a deletion by timestamp** (`updatedAt` carries SEC-4's defect; a tombstone wins by
  rule, deliberately), and **do not shorten the sweep** — it is the deadline for the other phone to
  come back and collect the deletion.
- **CQ-4 · Daily recurring events vanished after ~2 years.** `count++` ran per loop iteration rather
  than per occurrence emitted, and the walk always started at the event's start, so a daily event
  stopped 730 days in regardless of the window queried — an empty month three years out, with the
  master row intact. Occurrences are indexed now, so a distant range costs the same as a near one;
  the month-end drift (the 31st becoming the 28th permanently after one February) went with it.
- **CQ-7 · The Google Calendar import silently truncated at 50 events.** `maxResults = 50` and no
  `pageToken` — and "Found 50 events" is also what a complete import reports, so the user believed
  it had finished. Every page is followed now, within a stated window, with a *different* message
  when the cap is reached.
- **CQ-9 · `ChildInfoViewModel` could overwrite the wrong child's record.** `init` collected the
  whole list for the ViewModel's lifetime and set `_currentChildInfo = list.first()` on every
  emission, so a background sync tick while editing child B reset the state to child A — and the
  save then landed on **child A's real row**, id and `createdAt` included. The editor observes one
  child by id now. Keep the split: a list screen reads the list, an editor observes its one record.
- **CQ-10 · `syncWithFirestore()` meant two incompatible things.** A one-shot in three repositories
  and an endless `callbackFlow` in three others — adding the wrong one to `performFullSync()` by
  analogy would have made it never return, WorkManager would have killed it at ten minutes, and sync
  would have stopped entirely with no exception and no log. Renamed by shape: `pullOnce()` versus
  `observeRemote()`, seven repositories. The danger was never in any implementation; it was that the
  two had one word between them.

### Design

- **UX-1 · A paired parent was told they had no co-parent, on every cold start.** `Loading` is its
  own state now and the page asserts nothing while it holds — with a settle window, because a page
  that waits for ever is worse than one that offers something to do.
- **UX-2 · No main screen had a loading state.** Every list started at `emptyList()` and branched on
  `isEmpty()`, so "nothing yet" and "nothing at all" were the same value: Home asserted "$0.00" and
  "All settled" before it knew, and Contacts told a parent opening the emergency surface in a hurry
  that there were none, a frame before showing them. One type rather than six flags: `Loadable<T>`
  plus `stateInLoadable`. **The calendar is deliberately excluded** — its grid is structurally
  present either way, and `Loading` on every re-anchor would flash.
- **UX-3 · Budgets could not be edited or deleted** — a typo in a limit was permanent — plus a
  keyless `remember` inside `items{}` that showed another budget's figure on a recycled row.
- **UX-4 · There was no way to jump to a date.** The dialog was built and never opened.
- **UX-5 · "Today" did not survive midnight.** `remember { LocalDate.now() }` with no key. Reading
  it inline was no better: correct whenever it ran, but nothing made it run. `rememberToday()` makes
  midnight a recomposition trigger.
- **UX-6 · Adaptive sizing and font scale were switched off at the entry point.** The window size
  class never reached the theme, so `adaptiveDimensions()` — the only code reading `fontScale` and
  `isTouchExplorationEnabled` — was dead.
- **UX-7 · Touch targets.** The calendar header's month title *is* the Month/Week/Day switcher and
  was a bare ~28dp `clickable`, which TalkBack did not announce as a control either.
- **UX-10 · Budget status was carried by colour alone.** Now a word and a shape first, colour third
  — a circle and a triangle rather than two tints of one shape, because the point is to survive the
  colour being discarded. Two things fell out that were not in the item: the two screens decided the
  same three states in a *third* palette, and the percentage was painted in the status colour, which
  put amber at ~1.7:1 as text.
- **UX-11 · The Google Calendar row had a switch *and* a chevron.** The switch moved into the
  expanded block, not the chevron out of the row: expanding is what that row *is*.
- **MON-6 · The Czech custody preset.** `EVERY_OTHER_WEEKEND` (výhradní péče se stykem), listed
  second because the enum's order is the picker's order. Its switch asks "who does the child live
  with" rather than "who starts first" — this pattern does not alternate blocks, so a parent asked
  who starts would answer about the first weekend and set it inverted. What it exposed is
  **MON-6b**, since done as contact windows.
- **MON-7 · The AI subsystem is deleted.** 23 files, ~3,200 lines, reachable from no navigation
  graph, while the Gemini key shipped in every APK. `generativeai`, `retrofit`, `converter-gson`,
  `okhttp` and `logging-interceptor` went with it. It is in git history. See **MON-12** for the
  terms on which it returns. Boundaries that outlive the deletion: receipt OCR stays on-device; AI
  never acts on the co-parent's behalf; AI never adjudicates who is right or who is late more often;
  chat content reaches a model only on an explicit user action.

### Family shape

- **FAM-1 · The wizard could only ever create one child and one pet** — and its relatives step wrote
  emergency contacts onto whichever child was saved first, so with two children they were silently
  mis-filed. Both steps are repeatable lists now, and **nobody is asked how many children they
  have**: the steps collect names and the count falls out of them. A family with one child sees the
  form they saw before.
- **FAM-2 · One reference for "who this is about".** `Expense.childId`/`Budget.childId` — which
  nothing wrote and nothing read — became `forMembers`, a list of `domain/family/FamilyMemberRef`
  (`"child:abc"` / `"pet:xyz"` on the wire, never a Gson serialisation of the type). Covering pets is
  what makes a vet's bill expressible at all. Two rules with an obvious wrong answer one keystroke
  away: **naming nobody is not naming everybody**, and **an unrecognised reference survives a round
  trip** as `Unknown`.
- **FAM-3 · Events know who they are about.** `Event.forMembers`, empty meaning "the whole family",
  and a filter strip that appears at two members and not at one. `firestore.rules` needed no change
  — the `events` block validates with `keys().hasAll([...])`, presence-based. What it left is
  **FAM-5**.
- **M-1 … M-4 · A parent can co-parent with more than one other adult.** `families/{id}` keyed by
  `FamilyKey.of(a, b)`; `familyId` on the six shared collections, stamped at create and never
  re-derived, with null meaning "mine alone"; the slot and `caresFor` moved onto the family (`slots`
  admin-only, `caresFor` member-writable — the asymmetry is the whole security surface); and the
  isolation itself. The lesson worth carrying: **a softening fallback for unstamped documents
  re-opened the leak**, because Firestore validates a query by its *structure*, not by running the
  rule over results — while any branch mentioned `isPartnerOf(createdByFirebaseUid)`, the old
  `whereIn` query was served. Measured in the emulator, not reasoned. Which is why
  `backfillRecordFamilyIds` must finish before the rules deploy.
