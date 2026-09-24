# CoPlanly design audit: design system, gestures, UX/UI and 2026 readiness

Audit date: 2026-09-24. The audit had four inputs:

- **Full-screen screenshots of the real app.** 61 screens in three variants (`light-en-100`,
  `dark-en-100`, `light-ru-130`), 183 images in all. They were taken on an API 30 Pixel 6
  emulator by the UI tour (`.github/workflows/ui-tour.yml`, run 36016836998). The data was a
  realistic seeded family on the Firebase emulators: two parents, three children, a pet, custody
  with contact windows and a seasonal layer, events, expenses in two currencies, chat, a vault
  document, a pending swap and change requests. The images are on the branch
  `ui-tour/claude-charming-ritchie-d6uqz8`; open `index.html` there for the side-by-side gallery.
- **The Roborazzi component baselines** in `app/src/test/screenshots/`: 121 images covering five
  languages, both themes, font scales 1.0× and 1.5×, and two palettes.
- **A read of `presentation/`**: 206 files, 53.5k lines.
- **Computed contrast** of every colour pair in `theme/`.

It builds on `AUDIT-2026-09.md` §3–§4. It does not repeat anything that audit closed.

Unless a path starts with `app/` or `docs/`, it is relative to
`app/src/main/java/com/coparently/app/presentation/`. `[shot NN]` refers to a screen in the
tour, for example `ui-tour/light-en-100/20_expenses_list.png`.

## Verdict

CoPlanly's colour work is disciplined. The parent-colour system, the custody layering and the
empty states would pass a 2026 design review. Its product thinking is careful and honest.

The shell around that work is behind:

- **Insets, the keyboard and adaptive layout are not handled.** This is visible on every screen.
- **Components are re-implemented per screen,** so each screen looks slightly different.
- **The type system has no Cyrillic,** which affects two of the five shipped languages.
- **The key numbers are truncated.** The amount one parent owes the other is cut off even in
  English at 100% font.
- **No Material 3 Expressive API is used,** although the BOM that ships it has been in the
  build since July.

None of this blocks a closed test. The first two P0 fixes each take about an hour.

| Area | Grade | Short reason |
| --- | --- | --- |
| Colour and roles | **B** | Roles are used everywhere and parent colours go through one path. But `tertiary` text fails AA, and the purple parent fill fails 3:1 in dark |
| Typography | **C** | Poppins has no Cyrillic, so ru/uk fall back to Roboto (visible in every RU shot). There are 74 weight overrides |
| Shape and spacing | **C** | 71 raw corner shapes in 20 radii. 873 literal dp values, and `dimensions()` is used in only 11 files |
| Components | **C+** | Good primitives exist (`SectionGroup`, `EmptyState`). Banners, sticky save bars, invite codes and date pickers each exist 2–7 times |
| States | **C** | Empty states are unified. Error states have no shared component. There is no offline indicator in an offline-first app |
| Layout and IA | **C+** | Home opens on utility links. The calendar spends 37% of the screen above its first hour. The expense summary takes 60% of its screen |
| Gestures | **C** | Rich gestures, but the swap is long-press only, the delete zone is a screen quadrant, and the resize handles are 14 dp |
| Navigation and back | **C** | Tab policy is sound. Back from Day view leaves the tab, and forms drop edits silently |
| Motion | **B+** | One token vocabulary (`Motion.kt`). `MotionScheme` is never set, and there are no shared elements |
| Platform 2026 | **C−** | Edge-to-edge is on but insets are doubled and the keyboard pans the window. There is no adaptive layout, no widget and no Live Update |
| Accessibility | **C+** | The calendar has strong custom actions. There are zero `heading()` calls, and money is truncated with `maxLines = 1` |
| UX flows | **B−** | Core loops are honest. Pushes land on the launcher, Settings → Family holds 17 rows, and "Settle up" records nothing |

## 1. Corrections to the code-only audits

Before the screenshots existed, two agents audited the code alone. The screenshots settle four
of their claims:

1. **Doubled status-bar inset: confirmed, measured.** On every screen the top-bar band runs from
   y = 63 px to y = 294 px on a 2.625× display. That is 88 dp, where an M3 `TopAppBar` is 64 dp.
   The extra 24 dp is exactly the status-bar height, applied a second time. The cause is the
   outer `Scaffold`, which pads the `NavHost` with `innerPadding` without `consumeWindowInsets`
   (`navigation/NavGraph.kt:154`), after which each screen's own `TopAppBar` adds the status bar
   again. The bottom does not double: the bottom navigation bar measures the standard 80 dp. So
   the fix is the one-line `consumeWindowInsets` that was proposed, and the top bar is where the
   screenshot suite should check it.
2. **"Nothing handles the IME": confirmed, in its milder form.** The window **pans**; it is not
   covered.
   - In chat [shot 18], the composer sits correctly above the keyboard. But the thread header
     (Bob, search, gear) has scrolled off the top, and the messages slide under the status bar.
   - In the event form [shot 15], the focused field stays visible, but the sticky **Save** bar is
     under the keyboard. To save, you have to dismiss the keyboard first.

   So this is P1, not P0. The fix does not change: `adjustResize` + `imePadding()`.
3. **Today is marked in the month grid.** The component fixture showed only the weekday header
   highlighted. The real grid draws a filled circle on today's number [shot 09].
4. **The design-refresh rule "no affordance may promise a feature that doesn't exist" is not
   broken by the Bakaláři row** (Settings → Sync, "Planned"). The row is inert on purpose, and its
   call site documents when it must come out. It is listed under P2 only as a reminder.

## 2. Findings, prioritised

Severity:

- **P0** is fixed before the closed test. It is either wrong information on screen or a defect
  on every screen.
- **P1** is fixed before public release.
- **P2** is polish and debt.

Effort: XS < 1 h, S ≤ ½ day, M ≤ 2 days, L > 2 days.

### P0

**D-1. The money sentence is truncated, in English, at 100% font.** Effort: S.

- **Evidence:**
  - [shot 20] Expenses, both currency cards: "Your co-parent owes yo…" next to *Settle up*. The
    amount owed, which is the only number on the screen a parent acts on, is never visible.
  - [shot 06] The Home tile reads "CZK3,540.00 ·…" and "You are owed CZK1,4…".
  - In RU the sentence is cut to "Второй роди…".
  - The component baselines show the same at 150% in de/uk (`home_stat_tiles/de_light_fs150`:
    "4.250,0…").
- **Cause:** `maxLines = 1` with ellipsis on money and on the balance sentence
  (`expenses/ExpenseSummaryHeader.kt`, `home/HomeScreen.kt` stat tiles), and a trailing filled
  button that takes a fixed share of the row.
- **Fix:** Let the balance sentence wrap to two lines. Move *Settle up* below it (or make it a
  text button). Never ellipsize an amount: put it on its own line with `softWrap = false`, and
  scale down with `autoSize` (Compose 1.8 `TextAutoSize`) if it must fit. Add a 2.0× font-scale
  variant to `ScreenshotVariants` so this cannot come back.

**D-2. Doubled status-bar inset on every screen** (§1.1). Effort: XS.

- **Impact:** 24 dp of dead band above every title. On the Calendar that is 24 dp less grid; on
  Chat it is one message less.
- **Fix:** `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)` on the `NavHost`.
  Re-run the UI tour and check that the top bar is 64 dp.

### P1: layout and information architecture

**D-3. The calendar spends 37% of the screen before the first hour.** Effort: M.

- **Evidence:** In Week and Day view [shots 10, 11], the header row, the change-request banner,
  the "Show for" label, the member chips and the week header come before the grid. The first hour
  line sits at y ≈ 330 of 888, and only about 6 hours are visible. In Month view [shot 09] the
  same stack pushes the sixth row under the FAB.
- **Problems, each seen in the screenshots:**
  - The RU title is truncated to "Сентябрь…" by the Today/Filters pills. This is a regression of
    design-refresh item 5, where the title *is* the view picker.
  - The banner is truncated in English at 100%: "2 change requests from your co-pare…".
  - "Show for" is a caption for three chips that could sit in the Filters sheet or in one scroll
    row with the other filters.
- **Fix:**
  - Put the member filter inside Filters, and show its active state as a count on the Filters
    pill.
  - Make the banner a single-line chip, "2 requests · Review", or collapse it into the header
    when scrolled.
  - Let the title take the remaining width, with *Today* as an icon button below Compact width.

**D-4. Home leads with navigation, not the day.** Effort: S.

- **Evidence:** [shot 04] The first thing under the title is a `SectionGroup` of Contacts, Child
  Information and Pets. Those are links to reference screens, and they duplicate Settings →
  Family [shot 25]. The handover hero and the today card, the surfaces the design refresh made the
  point of Home and the best-designed ones in the app (V8), start below the fold on the RU
  variant.
- **Fix:** Order Home as hero → today → week → "Bob changed" → stat tiles, and put the three
  links last, or into a "Family" tab or hub (see D-12).

**D-5. The pending swap is a modal dialog on launch.** Effort: S.

- **The dialog is an owner decision, not an accident.** This entry first read as a defect, and it
  is not one. `presentation/home/AwaitingDialogs.kt` records that the owner walkthrough (items
  4/13) called for the pop-up: what waits on this parent's answer should confront them on open,
  instead of hiding behind a row they may never tap. *Later* puts it away for that screen instance
  only, and it returns on the next visit, which the KDoc calls "the level of insistence a request
  that blocks the other parent deserves". What follows is therefore a proposal for the owner to
  weigh. It is not a fix to apply.
- **Evidence:** [shots 01, 05, 06, 07] The *Day swap* dialog is still over Home after scrolling,
  and it appears behind the event sheet. A parent who opens the app to check tonight's pickup has
  to decide on a swap first, or tap *Later* on every visit.
- **Also seen:**
  - In RU the three buttons wrap: *Принять* alone on one line, *Позже · Отклонить* below it.
    M3's dialog stacks its button slots when they overflow, and the dismiss slot holds two
    buttons in one row.
  - The message ended in a double period, "3 октября 2026 г..". The FULL date pattern ends with
    "г." in ru and "р." in uk, and the sentence added another period. **Fixed:** ru and uk now end
    on the date. ru, uk and cs also put the date after a colon, because the formatter's
    nominative weekday read as a grammatical error mid-sentence ("у кого будет ребёнок суббота").
- **Proposal (owner's call):**
  - Keep the pop-up for a *new* ask, once. After *Later*, show the ask as an inline attention card
    at the top of Home until it is answered. The "Bob changed" section already has the anatomy.
    The ask still confronts the parent on open, still cannot hide behind a row, and stops
    re-opening over the day's plan on every visit.
  - With that change, drop *Later* from the dialog. Tapping outside already means later, and two
    buttons fit on one row in every language.

**D-6. The expense summary takes 60% of the Expenses screen.** Effort: S.

- **Evidence:** [shots 20, 22] Two currency cards (CZK, EUR), each with a bar, a legend and a
  settle row, reach y ≈ 540 of 888. On the *Analytics* tab, the analytics (the chart) are
  entirely below the fold. The first view shows only filter chips, so the tab looks empty.
- **Fix:**
  - Collapse the summary to one line per currency once the list scrolls (a `LargeTopAppBar`-style
    collapse).
  - Put the analytics above the list-level filters.
  - Consider one card with a currency segmented control when there are two or more currencies.

**D-7. Forms: the save action is not where the thumb is, and defaults ignore the family.**
Effort: S.

- **Add expense** [shot 24] has no sticky Save; it is below the fold. *Event* has one.
- The currency defaults to **USD** for a Czech family whose expenses are in CZK and EUR.
  `ExpenseEntity.currency` defaults to `"USD"`, and the Settings currency shows "USD $" [shot 27].
  The default should come from the country (MON-13 already stores it) or from the last-used
  currency.
- The Date field renders in the disabled style (grey outline and text), yet it is the picker.
- **Custody setup** in RU at 130% [shot 38]: the *Pattern start date* field is hidden behind the
  sticky save bar. It needs content padding equal to the bar height, which a shared
  `StickyActionBar` (D-15) would own.

**D-8. Poppins has no Cyrillic** (F-1). Effort: M.

- **Evidence:** every `light-ru-130` shot. The Latin text ("Bob", "Alice", "CZK") is Poppins and
  the Cyrillic is Roboto, so headings and body change typeface in the middle of a line.
- **Scope:** ru and uk are two of the five shipped languages. ₴ and № fall back too.
- **Fix:** move to a family with Latin-Ext and Cyrillic. For a 2026 look, the candidates are:
  - **Google Sans Flex** / **Roboto Flex**, which are variable and fit M3 Expressive's emphasised
    type.
  - **Manrope** or **Onest**, which are geometric like Poppins and have Cyrillic.

  Regenerate the screenshot baselines.

### P1: platform and gestures

**D-9. Keyboard** (§1.2). Effort: S.

- Put `android:windowSoftInputMode="adjustResize"` on `MainActivity`.
- Add `imePadding()` to the chat column, the onboarding bottom bar, and every sticky-save form.
- Add `imeNestedScroll()` on the message list.

**D-10. Gestures that are hidden or misfire** (UX audit G1–G4). Effort: S each.

- Offering a swap is **long-press only** on a month cell. Add a visible "Offer this day" action
  in the day's preview.
- The drag-to-delete target in Week/Day is a **screen quadrant** (`calendar/DayWeekView.kt:1134`),
  not the FAB it animates into. A drag that ends bottom-right deletes.
- The resize handles are **14 dp** (visible as the two pink pills in [shot 11]). Use a 48 dp
  touch target and add a11y actions "Extend 15 min" and "Shorten 15 min".
- Journal delete is swipe-only.

**D-11. Back and forms** (N1, N2, N3). Effort: M.

- Back from a Day view reached by tapping a month cell leaves the Calendar tab. It should return
  to Month.
- Event, expense, child and pet forms discard edits on Back and on configuration change. Add a
  discard guard and `rememberSaveable` state.
- There is no `PredictiveBackHandler` for the sheets and forms. `enableOnBackInvokedCallback` is
  on, so the system animation plays, but nothing previews the discard.

**D-12. Settings is the family hub by accident** (U2). Effort: L.

- **Evidence:** [shots 25–29] The FAMILY group holds 17 rows before SYNC starts: pairing,
  friends, professionals, "what you co-parent", colour, country, split, child info, pets, custody,
  plan, export, documents, journal, my data, co-parent data. The RU version needs four
  screen-heights to reach *Sync*.
- **Fix:** a Family screen (from Home's hero or a fifth destination on Medium and wider widths),
  grouped as People, Schedule, Records and Agreements. Settings keeps App, Sync and Account.

**D-13. Pushes land on the launcher** (U1, P6). Effort: M.

- Most push types open `MainActivity` with no deep link.
- There is one notification channel for everything.
- Give each type its route, with channels per category (chat, schedule, money, family) so a
  parent can silence money without silencing a handover.

### P1: design system and accessibility

**D-14. Contrast failures** (F-2, F-3, F-4). Effort: S.

- **Light `tertiary` as text:** 3.77:1 on surface. Visible as the green "Synced at 15:05"
  [shot 27] and "Up to date" in the chat header [shot 17]. Darken it to `#047857`, or add a
  `success` role.
- **Purple parent fill in dark:** 2.09:1. Add a theme-aware fill, and a `ParentColorsTest` case
  asserting at least 3:1 on every surface in both themes.
- **Holiday numerals on custody washes:** 3.44–4.15:1. The red "28" in [shot 09] fails on the
  pink wash.
- **Dark custody washes:** maroon and navy [shots 09–11] are close in luminance to each other and
  to the weekend grey. Custody is readable by hue alone, which colour-blind parents do not
  get — see also A3, the colour legend.

**D-15. Components rebuilt per screen** (F-5, F-6). Effort: M.

- At least seven banner anatomies (change request, proposal, seasonal, sync, budget, vault
  outdated, plan disclaimer).
- Four sticky save bars with four heights: 52 dp fixed, and they clip at 200%.
- Four invite-code renderings.
- Three date-picker wrappers.
- Add `InlineBanner(tone, icon, text, action)`, `ErrorState`, `StickyActionBar` and `InviteCode`
  to `common/DesignSystem.kt`, and move the screens onto them. This is also where D-7's
  hidden-field defect gets fixed once.

**D-16. No offline indicator** (F-7). Effort: S.

- The app is offline-first and silent about it. Chat shows "Up to date" and the sync row shows
  "Synced at …", but nothing says "you are offline; changes will sync".
- Add one `ConnectivityBanner` in the top-level scaffold.

**D-17. Accessibility structure** (A1, A2). Effort: S.

- There are zero `semantics { heading() }` calls. TalkBack users cannot jump between Settings
  groups, Home sections or plan areas.
- Remove `maxLines = 1` from names and money (see D-1).

### P2

- **D-18. Dates and words the locale should decide.**
  - 36 fixed `DateTimeFormatter.ofPattern` patterns (F-9). In RU the event form shows
    "четверг, окт. 01, 2026" [shot 16], which is English word order with Russian words.
  - The chat's swap card prints an ISO date, "Day swap offered: 2026-10-03" [shot 17]
    (`chat/ActivityCardText.kt` passes the raw value as `%1$s`).
  - Home prints an all-day birthday as "12:00 AM" [shot 06].
  - Home says "день родителя Alice" in lower case [shot 05].
  - Promote Home's `localizedDate`/`shortTime` to `common/` and use them everywhere.
- **D-19. Radius and spacing tokens** (F-8): 71 raw `RoundedCornerShape`, 873 literal dp. Add
  `Spacing` tokens and a detekt `ForbiddenMethodCall` outside `theme/`.
- **D-20. Button hierarchy** (F-12). The dark-theme primary is a light lavender with dark text
  [shots 07, 16]. It is correct M3, but on a dark surface it reads as disabled next to the light
  theme's saturated indigo. There is no tonal tier, and dialogs mix filled and text confirms.
  - Give secondary actions `FilledTonalButton`.
  - Consider `primaryContainer` for the dark sticky CTA.
- **D-21. Title case vs sentence case.** Custody options are "Week On / Week Off", "2-2-3
  Split" and "Custom Schedule"; the event form has "Event Title", "Assigned To" and "Start Time".
  Settings and Home use sentence case. M3 and every other screen say sentence case.
- **D-22. Pairing leads with the e-mail** [shot 45]. The long address wraps over three lines in
  RU, and the "Paired since" fact is below it. Lead with the name and the date, and put the
  address in a secondary line with ellipsis in the middle.
- **D-23. Family switcher dialog** [shot 57]. The radio button and the name touch: there is no
  gap between the `RadioButton` and its label. It shows neither the pending-signal dot nor the
  signal line that design-refresh item 13 promises for a family with something waiting. Verify
  this with seeded signals.
- **D-24. Onboarding step 1 in RU** [shot 80] shows "Получаем данные от Bob…" with a spinner and
  no progress or timeout text. EN and dark had already loaded. Word a fallback ("Continue — the
  rest arrives in the background").
- **D-25. Toasts** (8) → Snackbars (F-13). **Dead tokens** (F-14). **Material contrast levels**
  (F-16). **Icon sizes** (F-18). **Motion leftovers** (form `scaleIn(0.8)`, the one bouncy spring,
  shimmer under reduced motion, two splash screens ≈1.2 s).
- **D-26. The Bakaláři "Planned" row** stays under the documented condition. Review it at each
  release.

## 3. What is already right (keep it)

- **Parent colour identifies a person.** The picker, `LocalParentPalette` and `ParentColors` form
  one path, and the screens contain no raw pink or blue. This is rare, and it is what lets the
  purple and orange palettes work.
- **Layered custody grid** (`DayCellFills`). The weekend as base, custody as overlay, a borrowed
  month at reduced alpha, and school vacation as a line: [shot 09] reads as one system in light.
- **The today card and the handover hero** (V8) are the best surfaces. Clear hierarchy and one
  action.
- **`EmptyState`** is used everywhere. No list has a bespoke blank.
- **Honest status words:** chat ticks from `Message.status`, "Queued" on change requests, and the
  parenting-plan disclaimer.
- **Motion tokens:** one vocabulary, fade-through between tabs and a push for detail screens.
- **Five languages with per-app locale.** The RU at 130% variant mostly holds. Where it breaks,
  it breaks at the specific places listed above, not in general.

## 4. 2026 readiness

| 2026 expectation | Status | Evidence and what to do |
| --- | --- | --- |
| **Material 3 Expressive** (M3 1.4 in the BOM) | ✗ not adopted | There are zero uses of `ButtonGroup`, `LoadingIndicator`, `FloatingToolbar`, `MaterialShapes` or `MotionScheme.expressive()`. Start where it pays:<br>• the calendar's Month/Week/Day picker → connected `ButtonGroup`<br>• list skeleton spinners → `LoadingIndicator`<br>• the Week/Day FAB plus actions → `FloatingToolbar`<br>• a `MotionScheme` set once in `CoPlanlyTheme`, so the spring tokens replace per-call tweens |
| **Edge-to-edge** (enforced from targetSdk 35) | ◐ on, insets wrong | D-2 (doubled top) and D-9 (keyboard). The system-bar scrims are fine |
| **Predictive back** | ◐ system only | `enableOnBackInvokedCallback="true"`. No `PredictiveBackHandler` for sheets or forms (D-11) |
| **Adaptive layouts** (Android 16 ignores orientation locks at ≥ 600 dp) | ✗ none | Add `NavigationSuiteScaffold` (rail at Medium and wider), `widthIn(max = 640.dp)` on forms and Settings, and list-detail for Chat and Expenses. A family tablet is a real device for this audience |
| **Live Updates / progress notifications** (Android 16) | ✗ | Handover day is a natural Live Update: "Leo → Bob at 17:00, school gate" |
| **Widgets** (Glance) | ✗ | Add a "Today" widget, whose data the today card already has |
| **Themed icon** | ✓ | The adaptive icon has a `monochrome` layer |
| **Per-app language** | ✓ | AppCompat locales in five languages |
| **Dynamic colour** | — deliberate | Brand colour plus parent colours. Right for this product, but delete the dead `dynamicColor` branch |
| **Contrast levels** (Android 14+) | ✗ | F-16. Export medium- and high-contrast schemes |
| **Typography** | ✗ | D-8. A variable font with Cyrillic, plus the emphasised styles of M3 Expressive |

## 5. Roadmap

This is ordered for the closed test first. Each step leaves the app shippable.

1. **Week 1, the P0s and cheap P1s** (≈2 days):
   - D-2 `consumeWindowInsets`, D-9 keyboard, D-1 money wrap.
   - D-5: the double period (done). The inline card waits for the owner's decision.
   - D-7 currency default and Date field style.
   - A 2.0× variant in `ScreenshotVariants`.
   - Re-run the UI tour.
2. **Week 2, the system** (≈4 days):
   - D-15 `InlineBanner`/`StickyActionBar`/`InviteCode`/`ErrorState`, and move the screens onto
     them.
   - D-14 contrast fixes with `ParentColorsTest`.
   - D-16 offline banner, D-17 headings.
   - D-8 font swap, then regenerate the baselines.
3. **Week 3, the layout** (≈4 days):
   - D-3 calendar header budget, D-4 Home order, D-6 expense summary collapse.
   - D-10 gestures, D-11 back and discard guard.
4. **Before public release:**
   - D-12 Family hub, D-13 push deep links and channels.
   - The adaptive shell, Expressive components.
   - Widget and Live Update, D-18 date formatting.

## 6. How to repeat this audit

- **Screenshots:** touch `.github/ui-tour-request` on any branch (or run *UI tour* via
  `workflow_dispatch`). About 25 minutes later the branch `ui-tour/<branch>` holds
  `ui-tour/<variant>/NN_screen.png`, `index.html` and `logs/`.
- **A new screen** gets a `camera.shot` in `app/src/androidTest/java/com/coparently/app/e2e/UiTourTest.kt`.
- **Known gaps in the tour:**
  - The two auth screens are skipped: under the paused Compose clock, a GMS Task resumes off the
    main thread. This is a test-environment limit, not an app defect.
  - The tour runs at API 30 only, so there is no Android 16 edge-to-edge enforcement and no
    predictive-back animation.
