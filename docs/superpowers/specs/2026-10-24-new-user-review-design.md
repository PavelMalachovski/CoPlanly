# A new user's first week: Home, onboarding, a Family tab and the quick fixes

Date: 2026-10-24. Owner decisions taken in conversation on this date.

## Why

On 24 October the UI tour was run on `main` (e853849) and read screen by screen, as three new users
would read it:
- a mother invited by her former partner;
- a father who starts the app himself;
- a high-conflict pair whose lawyers will read the record.

The owner's goal is usefulness first, then the widest spread once the iPhone version exists, then
a subscription. Spread depends on the *invited* parent staying. The review found that the app's
strength is real but buried, and that the first minutes ask a lot and show little.

What the review found:
- Home leads with three reference rows (Contacts, Child information, Pets) and a "Send a sticker"
  row. The answer the parent opened the app for, whose day it is and when the next handover is,
  comes fourth.
- The requests pop-up reopens every time the parent answers a request. Its key is
  `requests_<count>` (`HomeAskQueue.kt:33`), so a lower count reads as a new ask. The tour caught it
  on four screens in a row, once under an open bottom sheet.
- Onboarding is nine steps. The custody schedule, the one thing people install the app for, is
  step 9 and a button that leaves the wizard. The profile step asks for the parent's own date of
  birth and phone, which nothing needs.
- The product's core lives in Settings: 21 rows in Family alone, among them the custody schedule,
  parenting plan, export, month in review, documents and journal. "Pause before sending" is the
  best tool for a high-conflict pair, and it is off by default, between Currency and Theme.
- The calendar has no legend. A new user cannot tell that pink is Alice, a triangle is a handover
  or the blue band is a contact window.
- Chat activity cards ("Event declined: …") are drawn as the sender's own bubble. In a
  high-conflict thread that reads as words the parent wrote.
- The pairing screen prints both parents' full e-mail addresses.
- Czech copy uses gender hedges ("Bob zadal(a)") and "druhý rodič" where the name is known.

## Goals

1. Home answers "who has the children today, what waits on me, what is coming" before anything
   else.
2. The requests pop-up opens again only when a request arrives that the parent has not seen.
3. Onboarding runs: link the co-parent, then notifications, then name and country, then the
   custody schedule *with a live month preview*, then done.
4. A **Family** tab replaces the Expenses tab. It holds money and everything about the family's
   arrangements, records and people. Settings keeps only the app and the account.
5. The quick fixes listed in Phase 1.
6. Obvious Czech copy defects are fixed now; a native speaker reviews the whole of it later.

## Non-goals

- Billing or a paywall (MON-11).
- The iPhone app.
- A full Czech copy review. That is the native speaker's.
- New prompt cards that chase a parent to add children or contacts after onboarding. The Family
  tab's rows are the route; see Phase 3 for the one hint that is in scope.
- The other review findings, left for later:
  - the inbox's full mini-month per card;
  - the chat header's "Up to date";
  - the week number in week view;
  - truncated chips in week view;
  - "Who starts first" as a switch;
  - the long event form.
- A badge on the Family tab.

## Anti-goals

These would make the work a failure even if it builds:
- A feature that becomes unreachable. Every destination Settings reaches today stays reachable,
  from Family or from Settings.
- An invited parent whose Next in onboarding silently *proposes* a schedule change to the pair's
  existing schedule.
- Money moving more than one tap away from the tab bar.
- A stored record, preference or wire format changing shape. This is navigation and presentation
  only. The one stored default that changes is "Pause before sending", and only for a parent who
  never set it.
- A string added in fewer than five locales.

## Constraints

- **CLAUDE.md applies.**
  - Compose only.
  - `SectionGroup`/`SectionRow` and the other shared primitives.
  - Theme tokens (`Spacing`, `IconSizes`, shapes, `MaterialTheme.colorScheme`).
  - Parent colours through `ParentColors`.
  - Names, never "Mom"/"Dad".
  - Strings in all five locales, in sentence case.
  - Conventional Commits.
  - A new ViewModel arrives with a test file.
- **No Android SDK in this environment.** The build, the unit tests, lint and detekt are proven by
  CI on the pull request. The screens are proven by a UI tour of the branch, run by touching
  `.github/ui-tour-request`. Screenshot baselines that change on purpose are re-recorded through
  the Regenerate workflow.
- **One pull request** for all phases, on `claude/charming-meitner-fl3j13` (owner decision). Each
  phase is its own commit or commits, so the history reads in phases.
- **CLAUDE.md is updated in the same pull request** where it describes what changes:
  - design refresh items 1, 4, 13 and 19;
  - UX overhaul item 1 (the tab list);
  - "Things that are easy to get wrong" item 22 (onboarding).

  Add `docs/DEVICE-CHECKLIST.md` entries for what only a phone shows.

## Approaches considered

**The Family tab.**
- **A — a hub screen, with Expenses as a detail screen opened from its first card.** Chosen. The
  Expenses screen stays whole. Money is one tap from the tab.
- **B — a "Money | Family" switch at the top of the tab, with Expenses embedded.** Rejected. It
  brings back the mixed tab the change is meant to remove: two screens and two primary actions in
  one place.

**The custody step in onboarding.**
- **A — the pattern form embedded in the wizard step, with a `MiniMonth` preview that redraws on
  every choice.** Chosen. The preview is the moment of value.
- **B — keep the step as a button to the custody screen and add the preview there.** Rejected. The
  moment of value stays behind a navigation.

## Phase 1 — quick fixes

1. **The requests pop-up.**
   - Key `HomeAsk.Requests` on the identity of what waits, not the count. `HomeViewModel` already
     holds `RequestInbox.waitingOnMe` before reducing it to a count (`HomeViewModel.kt:633`).
     Expose the waiting change-request ids and event ids.
   - **Seen means "was waiting when the parent last dealt with the ask"** (builder's question;
     designer's call). Both "Later" and "Review" record the waiting ids as seen, and so does
     opening the Requests inbox by any route.
   - The stored state holds the seen *ids* per family. Today's `PutOffAsksViewModel` stores one
     revision key and prunes it to what is still waiting. That storage changes:
     - keep the seen ids that are still waiting;
     - drop the answered ones;
     - the ask shows only when a waiting id is not in the seen set.

     A newest-arrival key or an id-set hash cannot pass "answer one" under the old pruning, so
     neither is used.
   - Fewer waiting ids must never reopen the pop-up.
   - Unit tests cover all three cases: answering one, a new one arriving, and all answered. Put
     them in `HomeAskQueueTest`, beside it, or in `PutOffAsksViewModelTest`.
   - The dialog's message names the co-parent ("Bob is waiting for your answer…") through the
     `ParentNames` it already receives.
2. **Activity cards in chat.**
   - A message with `activity != null` renders as a centred, neutral card. Use `surfaceContainer`
     and the `DaySeparator` family, not a sided bubble. It is neither the reader's colour nor the
     sender's.
   - It still shows its time and, when tappable, its chevron. Its target is unchanged
     (`ChatCardTarget`).
   - It names who acted when the sentence does not already. Use the existing `ActivityCardText`
     wording, adding a "by {name}" line only if the sentence lacks the actor.
   - Settle-up's payment block (`SettleUpPayment`) keeps working under it.
3. **Calendar legend.**
   - Extract `MiniMonthLegend`/`LegendEntry` (`MiniMonth.kt:216,252`) into a shared composable.
   - Show one constant-height row under the member filter strip, in Month, Week and Day view.
   - The row holds: each parent's name with their wash swatch; the handover diagonal; and, only
     when the family has any, the contact-window mark.
   - It must not change height between months (`CalendarScreen` comment, about lines 692–710).
4. **No e-mail addresses on the pairing screen.**
   - `PairedPartnerCard` drops the partner's address.
   - The pairing screen's `SignedInAsRow` shows the name only.
   - Settings → Account keeps the signed-in address. It is the parent's own, and it is how they
     know which account they are in.
5. **"Pause before sending" on by default.**
   - `PreferencesRepositoryImpl.kt:51` default `false` becomes `true`.
   - A parent who switched it off keeps it off: the stored value wins.
   - Settings wording unchanged.
6. **The family switcher chip's icon.** `Icons.Default.SwapHoriz` reads as "swap a day". Use a
   family or people icon (`Icons.Default.Groups` or similar).
7. **Names where the name is known.**
   - `expenses_balance_owed_to_you`/`expenses_balance_you_owe` take the co-parent's name. Their
     KDoc (`ExpenseSummaryHeader.kt:415`) says the net figure carries no slot, but in a family of
     two the counterparty *is* the co-parent on screen. Fall back to the generic wording when no
     name is known.
   - Same for `home_dialog_requests_message`.

## Phase 2 — Home

Home's dashboard, top to bottom, on a phone:
1. `HandoverHero`, when there is a next handover.
2. The today card (`DayAgendaCard`).
3. `RequestsRow`.
4. "This week".
5. The co-parent's changes.
6. `StatTiles`. The money tile opens the Expenses detail screen (Phase 4).

Rules:
- On a window of 840 dp or more, `actNow` is hero, today and requests; `readAfter` is week,
  changes and tiles.
- Removed from Home: the Contacts / Child information / Pets group, and `HomeStickers`. Both the
  unseen-sticker card and the "Send a sticker" row go; both live in Family (Phase 4).
- The Aug 2026 "emergency surface first" owner decision is superseded by this owner decision.
  Contacts and child information are one tap from the Family tab.
- `HomeScreen`'s `onOpenContacts`/`onOpenChildInfo`/`onOpenPets` parameters go away with their
  rows.
- `HomeSkeleton` already draws hero-first and needs no change.

## Phase 3 — onboarding

Steps: **CoParent → Notifications → Profile → Custody → finish.**
- `Intro`, `Family`, `Child`, `Relatives`, `Pet` and `Split` leave the wizard.
- Their code is deleted rather than hidden. The screens they duplicate (child info, contacts,
  pets, split dialog) remain.
- `stepsFor` no longer depends on `caresFor`. An empty `caresFor` already reads as "show
  everything" (`FamilyKind.effective`).

The **Profile** step:
- Shows name, colour and country/region.
- Date of birth and phone leave it. They remain on My details.
- Name stays the only requirement.

**One path.**
- The short path (`SHORT_PATH`, `openCalendarNow`, `shortPath` state) merges into this one, since
  the full path is now that short.
- "Looks right — open the calendar" goes. The custody step plays its role: see below.
- The device-region country guess that `openCalendarNow` made applies to every new profile, as a
  preselection the parent can change.

The **Custody** step embeds the pattern form:
- Extract `ModelTypeCard` and what the form needs from `CustodySetupScreen` into internal
  composables shared by both screens.
- Use `CustodySetupViewModel` (Hilt) for state.
- Under the form, a `MiniMonth` of the month that holds the pattern's start date. It is built from
  `state.toPatternModel("preview")` with `MiniMonthModel.build`, as `ProposedSchedulePreview` does.
  It redraws on each change.
- Finish saves through the same `CustodySetupViewModel.save` path.
- Advanced options stay on the full custody screen, reachable later from Family:
  - midweek;
  - contact windows;
  - per-child;
  - answer-by.

**Choosing the mode** (builder's question; designer's call). Room may not hold the pair's
schedule yet when an invited parent reaches this step seconds after pairing.
- On entering the step, read the pair's custody document from the server, bounded to a few
  seconds, with a short loading state.
- If the read finds a document, use the existing-schedule mode below.
- If the read finds none, or cannot answer, show the form.
- Finish from the form calls a repository variant that **activates or does nothing, and never
  proposes**. It answers "a shared schedule exists" without writing, and the step then switches to
  the existing-schedule mode.
- `CustodyModelRepository.submit`'s proposal branch is not reachable from the wizard. That is
  what makes "Next never proposes" a property of the code, not of timing. The unit test drives the
  variant with a shared document present and asserts that nothing was written.

**When the pair already has a schedule** (`custodyType != null`, the invited parent's usual case):
- Show no form. Show the existing schedule's `MiniMonth` and its one-line description.
- Primary button: "Looks right". It finishes without writing anything.
- Secondary button: "Propose a change". It opens the full custody screen, whose save is the
  ordinary proposal with its answer-by.
- Next in the wizard never proposes.

**Custody is skippable.** Skip finishes without a schedule.

`finish()` and the `onboardingCompletedAt` marker are unchanged. `OnboardingState.isNeeded` is
unchanged.

**The one hint in scope.** When the family has no child records, the Family tab's "Children" row
reads "Add your children" (Phase 4).

## Phase 4 — the Family tab

**Tabs:** Home · Calendar · Chat · **Family**.
- `BottomNavDestination.EXPENSES` becomes `FAMILY`, with route `family` and a family/people icon.
- Label: "Family" / "Rodina" / "Familie" / "Семья" / "Сім'я".
- It is a tab `composable()` with `tabEnter`/`tabExit`, like the other three.

**Family screen** (`presentation/family/FamilyScreen.kt` and a ViewModel).
- Top bar: title, `FamilySwitcherChip`, gear.
- Body, through `SectionGroup`/`SectionRow`, grouped and labelled:
  1. **Money.**
     - A summary card: this month's shared spend and who owes whom, per currency, worded as
       Home's money tile words them.
     - Tapping it opens Expenses, where Settle up stays (planner's call: Settle up needs the
       Expenses screen's dialog and ViewModel, so it is one tap away rather than duplicated).
     - A row "Expense split — Alice 60 / Bob 40" opens the existing split dialog.
     - An empty month says so and still opens Expenses.
  2. **Agreements:** custody schedule, parenting plan, export the record, month in review.
  3. **Children and home:**
     - children (the "Add your children" hint when there are none);
     - pets (only when `caresFor` includes pets or a pet exists, as Settings decides today);
     - contacts;
     - documents;
     - what you co-parent (the family-kind dialog).
  4. **People:**
     - co-parent (pairing);
     - my details (designer's call: it held the date of birth and phone that leave onboarding,
       so it must stay reachable);
     - co-parent details;
     - family friends;
     - professionals;
     - the unassigned-records row while any wait.
  5. **Only mine:** private journal; my stickers (with the unseen-sticker count, and where
     "Send a sticker" now lives).
- On a wide window it is a readable single column like the other hubs. No two-pane.

**Expenses becomes a detail screen.**
- Route `expenses`, opened from the Family money card, Home's money tile and the `EXPENSES` push
  destination. A push opens the Family tab, then Expenses on top.
- It gets an up arrow, keeps its FAB and `FamilySwitcherChip`, and loses the gear.
- It stays a full-width `composable()` with slide transitions, not `pane()`, because its two-pane
  layout needs the width. Record this as the one exception in CLAUDE.md design item 19.
- `onSettleUp` keeps landing on the Chat tab without leaving Expenses on the tab's stack.

**Settings shrinks to the app and the account.**
- **App:**
  - my colour;
  - country/region;
  - theme;
  - language;
  - currency;
  - pause before sending;
  - telemetry;
  - health consent;
  - push notifications;
  - data sources.
- **Sync:**
  - co-parent sync;
  - Google Calendar;
  - calendar feed;
  - Bakaláři import;
  - "Family shown" (the switcher dialog, while there are two families).
- **Account:** unchanged.
- Every row that moved to Family is removed from Settings, together with its callback. No
  destination lives in two places, except Expenses, which Home's tile and Family both open.
- Design refresh item 4 in CLAUDE.md is rewritten to this.

## Phase 5 — tone and language

- Czech:
  - Replace every `(a)` gender hedge in `values-cs` with a form that needs no gender. The
    explorer's list of keys is in the conversation; re-grep `values-cs` for `(a)`. Use a noun
    phrase or the name, as `calendar_strings.xml:91` and `home_strings.xml:80` already document.
  - "Den rodiče Alice" becomes a natural form, "U Alice" or "Den s Alicí". Pick one and use it
    wherever the pattern repeats.
  - "Za 2 dny přebírá: Bob" loses the colon.
- Russian and Ukrainian forms are checked for the same hedge pattern.
- English strings touched stay in sentence case.

## Tests and proof

- **Unit tests:**
  - `OnboardingStepTest` and `OnboardingViewModelTest` change with the steps. Delete the tests of
    removed steps; add tests for the custody step's two modes and "Next never proposes".
  - `HomeAskQueue` gets the tests above.
  - The new `FamilyViewModel` gets a test file.
- **Android tests that name Expenses as a tab or rows in Settings:**
  - `UiTourTest`: its expenses and settings sections start from Family. Add a `family` shot.
  - `MainNavigationSmokeTest`.
  - `qa/IsolationScenarioTest`.
  - `e2e/OnScreenIsolationTest`.
  - `e2e/OnScreenFamiliesTest`.

  Each moves to the Family tab.
- **Screenshot tests.** `DesignSystemScreenshots`/`ContrastScreenshots`' Settings fixture is
  rebuilt to look like the new Settings. Add a screenshot for the Family screen's money card and a
  group. Re-record baselines with Regenerate.
- `tools/play-promo/captions.js` and `docs/play-listing/README.md` refer to tour shot names. Keep
  `expenses_list` as a shot name so they still resolve.
- **Proof the owner sees.** A UI tour of the branch (`variants: light-cs-100 light-en-100`) before
  the PR is handed over, and a short before/after of Home, onboarding, Family and Settings.

## Left to the builder

- Exact icons for the Family tab and the chip, within Material's set.
- How the requests key is derived (newest arrival or an id-set hash), as long as the three test
  cases hold.
- The compact form of the money card. Reusing `ExpenseSummaryHeader` with a flag, or a small new
  composable beside it, are both fine.
- The order of rows inside each Family group, beyond what is listed.
- Commit boundaries within each phase.
