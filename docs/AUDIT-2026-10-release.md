# CoPlanly release-readiness audit: design system, UX/UI and Google Play

Audit date: 2026-09-24, after weeks 1–6 of `AUDIT-2026-10-design.md` (#107–#112). It repeats that
audit's method and its twelve areas, so the grades compare directly, and adds what a Google Play
release needs on top.

**Inputs.**

- **The UI tour of week 6** (run on `claude/charming-ritchie-d6uqz8` at `4de6aea`, the tree that
  merges as #112): 65–66 screens in each of four variants — `light-en-100`, `dark-en-100`,
  `light-ru-130` and `light-en-100-wide` (a 1280 × 800 dp display) — 261 images, plus the Today
  widget drawn from its own `RemoteViews`, compact and tall, in every variant. Branch
  `ui-tour/claude-charming-ritchie-d6uqz8`, `index.html`. The only skips are the two auth screens
  (a test-environment limit, §6 of the design audit) and two scroll positions past the end of a
  screen.
- **The Roborazzi baselines**: 200 images, verified on every CI run.
- **CI on the same tree**: 1978 JVM tests, three emulator legs (API 26, 30, 35 with 16 KB pages),
  the two-parent e2e suite, the upgrade-over-main install, the R8 runtime probe, the rules and
  functions suites.
- **A read of the manifest, the build, `web/`, `docs/legal/` and `docs/LAUNCH-PLAYBOOK.md`.**

`[shot NN]` means `ui-tour/light-en-100/NN_*.png` unless a variant is named.

## Verdict

**The design system is ready for a closed test and, with one short week of fixes, for production.**
Ten of the twelve areas are at B+ or better; the two at B wait mostly on decisions the owner has
already been asked. The tour found **four defects a reviewer would notice in the first minute** —
money under a floating button, a pop-up that "Later" does not put off, a wrong sentence in
onboarding, and a Settings row for a feature that does not exist. None is large, all four are
code-only, and they are the first week of the plan below.

**Google Play readiness is a different question with a different answer: the app cannot be
published yet, and the reasons are almost all outside the code.** The technical requirements Play
checks automatically are met (target API 36, 16 KB pages proven on an emulator, no advertising id,
an in-app deletion, a consent gate, minified and probed release builds). What is missing is the
operations and paperwork: a Firebase app and `google-services.json` for `app.coplanly`, the release
keystore, the rules and storage deploys, a lawyer's read of the privacy policy and its hosting,
Google's verification of the Calendar OAuth scope, the Play Console declarations, and a closed test
of twelve testers for fourteen days. §3 orders them.

## 1. Grades

| Area | October audit | After week 6 (this audit) | What the tour showed | What stands between it and an A |
| --- | --- | --- | --- | --- |
| Colour and roles | B | **A−** | Parent colours hold in light, dark and the widget; contrast schemes held by tests | A phone walk of the contrast levels (checklist §3.14) |
| Typography | C | **B+** | Onest in all four variants, Cyrillic intact at 130 % | 7 weight overrides; title case survives in "Date of Birth" (R-6) |
| Shape and spacing | C | **B+** | Corners from tokens everywhere | ~100 off-grid paddings (26 × 6 dp, 24 × 14 dp, 14 × 10 dp, 10 × 20 dp) |
| Components | C+ | **A−** | Shared banners, empty/error states, sticky bars, tonal add buttons | Dates of birth are outlined buttons, not the date field (R-7) |
| States | C | **A−** | Empty states everywhere [shots 47, 48, 52, 53] | — |
| Layout and IA | C+ | **B** | Detail screens capped at 640 dp on the wide display | The tabs stretch to 1280 dp (R-8); Home's links and the Family hub (owner, D-4/D-12) |
| Gestures | C | **A−** | "Swap this day", delete over the button only | — |
| Navigation and back | C | **A−** | Rail on the wide display, predictive back over forms | — |
| Motion | B+ | **A** | — | — |
| Platform 2026 | C− | **B+** | Widget, rail, width cap, edge-to-edge, themed icon | List-detail on wide tabs (R-8); a baseline profile (§3.2) |
| Accessibility | C+ | **A−** | 130 % Russian reads whole; headings on sections | FAB over amounts (R-1); a TalkBack walk on a phone |
| UX flows | B− | **B** | Onboarding opens on what the co-parent entered | R-2, R-3, R-4; "Settle up" and the Family hub (owner) |

**Where it is, in one line: 1 A, 8 A−/B+, 3 at B+ or B — nothing below B.** The target of B+ or A in
every area is two weeks of code (§4, weeks 7–8) plus the owner's answers to three questions.

## 2. What the tour found

Numbered R-n so they can be referred to from a pull request. P0 blocks a release candidate, P1
should ship before the closed test, P2 before production.

| # | Priority | Finding | Evidence | Fix |
| --- | --- | --- | --- | --- |
| **R-1** | P0 | **The Expenses FAB covers money.** The last row's amount and the analytics total sit under the floating button — "CZK1,89…", "Total CZK3,5…", "450,00 CZK" behind the "+" in Russian. Design item 15 says money is never cut off; this is the same defect by another route | [shots 22, 24]; `light-ru-130` 23, 24 | Bottom content padding of FAB height + 16 dp on the Expenses list and analytics (and every tab with a FAB); a screenshot of the list's last row |
| **R-2** | P1 | **"Later" on the day-swap pop-up does not put it off.** The tour presses Later and the same dialog is back on the next Home screen, five shots in a row and again after the family switch | [shots 01, 04–07, 57] | Remember "Later" per swap for the session (or until the swap changes). Independent of D-5's inline card, which remains the owner's question |
| **R-3** | P1 | **Wrong helper text in onboarding's split step**: "Kept for you and your co-parent, in case of an emergency." — the sentence from the emergency-contacts step | [shot 86] | The split step's own line, in five locales |
| **R-4** | P1 | **Settings offers "Import from Bakaláři and EduPage — Planned. Not built yet."** A row for a feature that does not exist is what design item 8 forbids; a store reviewer reads it as unfinished | [shot 28] | Remove the row until the import exists (or gate it off in release builds) |
| **R-5** | P2 | Currency defaults to **USD** for a Czech account on an English device (Add expense, Settings) while every expense is CZK | [shots 25, 28] | The default follows the country first (D-7's intent), the device locale second |
| **R-6** | P2 | Child records print **"Date of Birth: 2016-04-12"** — title case and an ISO date — where the rest of the app says "Apr 12, 2016" | [shots 33, 34] | Sentence case, `localizedDate` |
| **R-7** | P2 | **Date of birth is an outlined button** on My details, the pet form and onboarding's About you, where every other date is the shared date field | [shots 38, 54, 83] | The shared `LocalDatePickerDialog` field anatomy |
| **R-8** | P1 (large screens) | **The four tabs stretch to the full 1280 dp.** Home's cards, the Expenses summary and the chat thread run edge to edge beside the rail | `light-en-100-wide` 01–06, 19–24 | Home as two columns from 840 dp; Expenses and Chat as list-detail (`ListDetailPaneScaffold`, material3-adaptive); until then a 840 dp cap |
| **R-9** | P2 (owner) | Two time formats on one screen: "10:00 AM" in This week, "15:30–16:00" in the today card | [shot 04] | Owner question 6 of #112 |
| **R-10** | P2 | The friend screen's field is labelled **"Give them this code"** above "Link accounts" — it reads as an instruction to share, while it asks to *enter* a code | [shot 47] | "Enter their code", with the share action separate |

What is right and should stay: the empty states [47, 48, 52, 53], the export's statement of what the
file is [49, 50], the custody editor's preview and holiday fairness [39–42], the parenting plan's
honesty about the official form [43], the widget in both themes [09, 10], and Russian at 130 %,
which wraps everywhere and cuts nothing but the one FAB overlap.

## 3. Google Play readiness

### 3.1 What the code already meets

| Requirement | Status | Evidence |
| --- | --- | --- |
| Target API (Android 16, API 36) | ✓ | `app/build.gradle.kts` `targetSdk = 36` |
| 16 KB memory pages | ✓ | CI leg on `google_apis_ps16k` loads SQLCipher and ML Kit (`NativeLibrariesTest`) |
| No advertising id | ✓ | `AD_ID` removed in the manifest with `tools:node="remove"` |
| Analytics and crash reporting off until consent | ✓ | REL-5, `TelemetryConsentApplier` (LAUNCH-PLAYBOOK §2.5 still says it is missing — stale) |
| In-app account deletion | ✓ | PR #68, `deleteAccount` callable |
| Web deletion route | ◐ written, not hosted | `web/delete-account/index.html` |
| Privacy policy and terms | ◐ drafted, not reviewed or hosted | `docs/legal/`, `web/privacy/`, `web/terms/`; the in-app link stays blank until `COPLANLY_PRIVACY_POLICY_URL` is set |
| Data Safety answers | ◐ drafted | `docs/legal/DATA-SAFETY.md` (health data, photos, sharing with the co-parent) |
| Backups | ✓ | `allowBackup="false"`, data-extraction rules |
| Release signing | ◐ config ready, no keystore | `signingConfigs.release` built from properties (REL-2) |
| R8 correctness | ✓ in CI | `release` mapping check and the `r8-runtime` probe; REL-7 on a real phone remains |
| Permissions | ✓ minimal | Internet, network state, notifications (asked in context), camera (QR and receipts, optional) — no media, location, calendar or exact-alarm permissions |
| Predictive back, edge-to-edge, per-app language, themed icon | ✓ | design audit §4 |
| Large screens | ◐ | Rail and detail-screen cap ship; tabs stretch (R-8). Android 16 ignores `QRScannerActivity`'s portrait lock on large screens — the scanner must survive landscape (checklist) |
| Widget | ✓ | Preview, description, resizable, target cells (design item 18) |

### 3.2 What is missing, in the order it has to happen

> Superseded by the ordered owner checklist in `docs/AUDIT-2026-09-play-final.md` §4 (2026-09-25),
> which re-checks this list against `main` and adds the reviewer's demo account, the in-app policy
> link and the chat reporting question.

| # | Item | Who | Why it blocks |
| --- | --- | --- | --- |
| 1 | **Firebase app for `app.coplanly`**, a new `google-services.json`, debug and release SHA-1 (REL-1) | Owner, console | No signed-in build exists without it |
| 2 | **Release keystore**, backed up twice (REL-2); enrol in Play App Signing | Owner | The one irreversible step |
| 3 | **Deploys**: functions, the two backfills, Firestore rules and indexes, **storage rules** (REL-3) | Owner, CLI | Vault, chat attachments and pet photos are refused live until `firebase deploy --only storage` |
| 4 | **Google Calendar OAuth verification** for the calendar scope in Google Cloud | Owner | A sensitive scope; unverified apps show a warning and are capped at 100 users |
| 5 | **Legal review and hosting** of privacy, terms and deletion pages; set the URLs (REL-4) | Owner, lawyer | The app processes a child's health data |
| 6 | **Play Console**: listing (name, short and full description, 512 px icon, 1024 × 500 feature graphic, 4–6 phone screenshots — the tour is the source), Data Safety, content rating (users communicate), target audience 18+, not in the Families programme, **health apps declaration**, financial features none | Owner | Required before any track |
| 7 | **UGC decision**: the chat is private between two known parents, but Play's UGC policy asks for a way to report objectionable content. Decide between an in-app "report" that mails support, or a documented argument that the unpair flow is the block; record the answer in the Console | Owner | A reviewer may flag it |
| 8 | **REL-7 on a real phone**: a release build, a child's medical profile reaches the co-parent non-empty | Owner, phone | The one test CI cannot run |
| 9 | **Closed test**: 12 testers opted in for 14 days (six real co-parent pairs, LAUNCH-PLAYBOOK §2.6) | Owner | Required for new personal accounts before production |
| 10 | **Baseline profile** (`androidx.baselineprofile`, a Macrobenchmark module) | Code | Not required; cold start and the calendar's first frame are what Android vitals rate |

Items 1–9 are the same list `LAUNCH-PLAYBOOK.md` §2.8 keeps; two of its lines are now stale and are
corrected here: item 9 ("analytics consent gate") is done (REL-5), and item 5's `signingConfig` is
in the build, waiting only for the keystore.

## 4. The improvement plan

**Week 7 — the tour's defects (code, one PR).** R-1, R-2, R-3, R-4, R-6, R-7, R-10, and R-5 after
checking D-7. Snap the off-grid paddings to the 4 dp grid (6 → 8, 10 → 8 or 12, 14 → 12 or 16,
20 → a new `Spacing.L2` 20 or 16/24), with the calendar's deliberate 9 dp kept and commented, and a
Regenerate for the baselines. Review the seven weight overrides. *Moves Typography and Shape to A−,
Components and Accessibility to A, UX flows to B+.*

**Week 8 — large screens and speed (code, one PR).** Home in two columns from 840 dp; Expenses and
Chat as list-detail with `material3-adaptive`; a baseline profile; the QR scanner in landscape.
The tour's wide variant proves it. *Moves Layout and IA and Platform to A−.*

**Owner decisions (no code until answered).** D-4 (Home's links as tiles), D-5 (a pending swap as
an inline card), D-12 (a Family screen out of Settings), "Settle up" (rename, or record a
settlement), the time format, Material 3 Expressive (alpha), and the UGC question of §3.2. *D-12
and Settle up take UX flows to A−.*

**Release track (owner, in parallel with weeks 7–8).** §3.2 items 1–9 in order. Items 5 and 9 are
the long poles — a lawyer's calendar and a fortnight that cannot be compressed — so start both now.

### Week 7 record

The owner answered four questions before the code: the time follows the reader's clock, "Later"
holds until the ask changes, the school-import row stays in debug builds only, and the default
currency goes country → last expense → device. For R-1 the owner chose the button that hides on
scroll over moving "+" into the top bar.

| # | What changed |
| --- | --- |
| R-1 | `ScrollAwareFab` (`common/FabScrollVisibility.kt`): the Expenses "+" leaves while the list moves forward, returns on the way back and at the end, where the existing 88 dp clearance keeps the last amount clear. A TalkBack scroll does not hide it |
| R-2 | `PutOffAsksViewModel` stores what "Later" put off, under each ask's revision (`DaySwapGroup.revision`, the proposal's time, the number of requests), and forgets what is no longer waiting |
| R-3 | The split step has its own footnote (`onboarding_split_footnote`, five locales) |
| R-4 | The Bakaláři / EduPage row renders only when `BuildConfig.DEBUG` |
| R-5 | `PreferencesRepositoryImpl.getDefaultCurrencyFlow` resolves, when nothing was chosen, from `CurrencyHints` (country, last expense) before the device guess |
| R-6 | "Date of birth" in sentence case, and the child's detail prints the reader's date |
| R-7 | `DatePickerField` on My details, the child and pet forms and onboarding; the expense date uses it too |
| R-9 | `shortTime()`/`dateWithTime()` follow `ClockFormat` (the device setting); twenty literal `"HH:mm"` and three locale-style formatters replaced, the widget and reminders read the setting themselves |
| R-10 | The friend screen's field says "Enter the code you were given" |
| Grid | The 6/10/14/18/20 dp paddings and gaps snapped to `Spacing` steps; the calendar banners' 9 dp is the one gap left off the grid, commented; the grid cells' own marks keep their literals |
| Weights | Eight of the thirteen `fontWeight` overrides became `*Emphasized` roles (one new, `bodyLargeEmphasized`) or the button's own role. Left: the chat search highlight (a span, not a style) and four `Normal` weights on the calendar's quiet labels, which have no role lighter than their style |

R-8 (tabs on a wide window) is week 8's. The Regenerate run moved 118 of the 200 screenshot
baselines (the grid, the weights and the 12-hour English times) and dropped one detekt baseline
entry, adding none.

The UI tour on the regenerated head walked every screen in all four variants, with no section cut
short. It shows the fixes on device:
- after "Later", Home opens with no dialog (shot 04)
- the last expense's amount rests clear of the "+" (23)
- the Add expense form opens on CZK for the tour's Czech family on an English (US) device (25)
- the pet form's date of birth is the date field (38)
- the onboarding split step carries its own footnote (86)
- English times are 12-hour in both the today card and the week list (04)

### Week 8 record

| # | What changed |
| --- | --- |
| R-8 | From 840 dp the tabs are two panes (`common/TwoPane.kt`): Home's act-now half beside its read-after half; the Expenses month's summary and chart beside its list, with no List/Analytics switch; Chat's list beside the open thread, and a single thread capped at 840 dp. The pair is at most 1200 dp, centred. Built on `Row`, not `ListDetailPaneScaffold`: nothing navigates between the panes, so `material3-adaptive` would add a dependency and no behaviour |
| §3.2 speed | A hand-written baseline profile (`app/src/main/baseline-prof.txt`, package wildcards over the start path) with `profileinstaller`; the release job's `tools/check-baseline-profile.sh` proves the app's rules merged and the APK carries the compiled profile. A recorded profile needs a Macrobenchmark module and a device per release, and stays open |
| §3.1 scanner | The QR scanner's portrait lock is gone (Android 16 ignores it on large screens, so every device now takes the one path), and the "don't ask again" state survives a turn |

## 5. How this audit was made, and how to repeat it

As in `AUDIT-2026-10-design.md` §6. Since week 6 the tour clears system error dialogs before each
variant and survives a broken section, so a run that ends early says so in its manifest.
