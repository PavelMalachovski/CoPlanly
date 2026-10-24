# A New User's First Week — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make CoPlanly's first minutes and everyday screens serve a new co-parent:
- a Home that leads with today;
- a requests pop-up that stops nagging;
- a four-step onboarding with a live schedule preview;
- a Family tab that replaces Expenses and empties Settings of the product.

**Architecture:** Presentation and navigation only. No Room schema, Firestore document or wire
format changes.
- One stored default changes ("Pause before sending").
- One stored preference changes meaning: put-off asks hold request ids.
- Expenses becomes a full-width detail screen reached from a new `FamilyScreen` hub.
- `CustodyModelRepository` gains one never-proposing entry point for the onboarding step.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (Material 3 1.4), Hilt, Navigation 2.9, JUnit/MockK/
Turbine, Roborazzi, Espresso/Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-10-24-new-user-review-design.md`

## Global Constraints

- **CLAUDE.md is binding.**
  - Compose only.
  - Stateless composables with ViewModel `StateFlow`.
  - `SectionGroup`/`SectionRow`/`GroupLabel` for rows.
  - `Spacing`/`IconSizes`/`MaterialTheme.shapes`/`colorScheme` tokens; no literal dp corners.
  - Parent colours via `ParentColors`; names via `ParentNames`; never "Mom"/"Dad".
  - KDoc on public declarations.
  - Conventional Commits.
- **Strings:**
  - Every new or changed user-facing string goes into `values`, `values-cs`, `values-de`,
    `values-ru` and `values-uk` in the same commit.
  - English is in sentence case.
  - Run `node tools/check-invariants.js` before each commit (locale completeness and format
    arguments). Expected output ends without `::error::`.
- **No Android SDK in this container.** Gradle cannot run here.
  - Write each test first, as the plan says. It is executed by CI on the pull request.
  - "Verify" steps that need Gradle are batched at the end of each phase as a push plus a read of
    the CI result. The PR's sticky "CI summary" comment lists failed tests.
  - CI runs only on a pull request, so Task 1.0 opens a draft PR.
- **One branch, one PR:** `claude/charming-meitner-fl3j13`. Push with
  `git push -u origin claude/charming-meitner-fl3j13`. Never push elsewhere.
- **Screenshot baselines** that change on purpose are re-recorded by touching
  `.github/regenerate-request` (the Regenerate workflow). Never commit images recorded by hand.
- **A ViewModel that is new gets a unit test file.**
- **Anti-goals (spec):**
  - No destination becomes unreachable.
  - Onboarding Next never proposes a schedule change.
  - Money stays one tap from the tab bar.
  - No stored shape changes.

## Review Focus

1. **A parent with two families.** Every new surface follows the family on screen: Family's money
   card, the requests seen-set, the onboarding schedule read. Seen ids are stored per family (Task
   1.1), and the money source filters with `FamilyView.counts` (Task 4.1).
2. **An invited parent offline at the custody step.** The server read cannot answer, the form
   shows, and Finish meets a shared document. The never-proposing call must write nothing and
   switch the step to the existing-schedule mode (Task 3.1 tests both).
3. **An unpaired parent at the custody step.** No pair means no read. Finish activates locally,
   as today's unpaired save does (Task 3.1 test).
4. **A push to `EXPENSES` from a cold start.** It must land on Expenses with Family beneath it,
   and Back goes to Family, not out of the app (Task 4.2 deep-link test in
   `MainNavigationSmokeTest`).
5. **A Czech reader at 200 % font.** The Family money card follows design item 15: amounts never
   cut, the pair stacks from font scale 1.3 (Task 4.1 screenshot at 2.0× German and Czech).

---

## Phase 1 — quick fixes

### Task 1.0: Commit the spec and plan, open the draft PR

**Files:**
- Add: `docs/superpowers/specs/2026-10-24-new-user-review-design.md`
- Add: `docs/superpowers/plans/2026-10-24-new-user-review.md`

- [ ] **Step 1:** `git add docs/superpowers && git commit -m "docs: design and plan for a new user's first week"`
- [ ] **Step 2:** Push. Open a **draft** PR to `main`, titled "A new user's first week: Home, onboarding, a Family tab".
  - The body follows the repo's PR template if one exists; otherwise a summary of the five phases.
  - End the body with the attribution lines.
  - Subscribe to its activity.

### Task 1.1: The requests pop-up keys on request ids

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/home/HomeAskQueue.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/home/PutOffAsksViewModel.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/home/AwaitingDialogs.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/home/HomeViewModel.kt:174-192,633-669`
- Modify: `app/src/main/java/com/coparently/app/presentation/home/HomeScreen.kt` (RequestsRow click)
- Test: `app/src/test/java/com/coparently/app/presentation/home/HomeAskQueueTest.kt`, `PutOffAsksViewModelTest.kt`, `HomeViewModelTest.kt:527`

**Interfaces:**
- Produces:
  - `HomeUiState.Dashboard.awaitingRequestIds: List<String>` replaces `awaitingRequestCount`.
    Ids are `"change:<ChangeRequest.id>"` and `"event:<Event.id>"`.
  - `HomeAsk.keys: Set<String>` replaces `key`. Proposal, Split and Swap return `setOf(<their
    old key>)`.
  - `HomeAsk.Requests(ids: Set<String>)` returns `keys = ids.map { "request_$it" }`.
  - `HomeAskQueue.of(asks, swaps, requestIds: Collection<String>)`.
  - `HomeAskQueue.next(queue, putOff)` returns the first ask whose `keys` are not all in `putOff`.
  - `PutOffAsksViewModel.putOff(revisions: Set<String>, waiting: Set<String>)`.

- [ ] **Step 1: Write the failing tests** in `HomeAskQueueTest`:
  - `requests ask stays put off when one of them is answered`:
    - queue of `Requests(setOf("change:a","change:b","event:c"))`;
    - putOff = its keys;
    - new queue `Requests(setOf("change:a","event:c"))`;
    - assert `next(...) == null`.
  - `requests ask opens again when a new request arrives`:
    - putOff keys of {a,b};
    - queue {a,b,d};
    - assert `next` is the Requests ask.
  - `no requests ask when nothing waits`: `of(HomeAsks(), emptyList(), emptyList())` is empty.
  - Add to `PutOffAsksViewModelTest`:
    - `putting off several revisions stores them all for the family on screen`;
    - `answered revisions are forgotten on the next put off`: stored {a,b}, put off {c} with
      waiting {b,c}, so stored is {b,c}.
- [ ] **Step 2: Implement** the interface changes above.
  - `AwaitingDialogs` builds `waiting` as the union of every ask's `keys`.
  - `dismiss(ask)` calls `putOffAsks.putOff(ask.keys, waiting)`.
  - The dialog's Review already calls `onDismiss()` first, so Review records the ids. Keep that
    order.
  - Home's `RequestsRow` click also puts off the current Requests ask's keys before it opens the
    inbox. Expose `onRequestsOpened: (Set<String>) -> Unit` from `AwaitingDialogs`' host, or call
    `PutOffAsksViewModel` from `HomeScreen`. One `hiltViewModel()` instance per Home either way.
  - The spec says "by any route". Home's two routes are what is built; the calendar banner is
    not. Record this in the spec's Phase 1 item 1 in the same commit: "opening it from Home".
- [ ] **Step 3:** Update `HomeViewModelTest.kt:527` to assert `dashboard.awaitingRequestIds.size == 1`.
- [ ] **Step 4:** `node tools/check-invariants.js`. Then commit: `fix(home): the requests pop-up opens again only for a new request`

### Task 1.2: The requests pop-up and the balance name the co-parent

**Files:**
- Modify: `AwaitingDialogs.kt:390-408` (`RequestsDialog` gains `coParentName: String?`)
- Modify: `app/src/main/res/values*/home_strings.xml`: add `home_dialog_requests_message_named` ("%1$s is waiting for your answer on some requests.")
- Modify: `app/src/main/java/com/coparently/app/presentation/expenses/ExpenseSummaryHeader.kt:415-445`
- Modify: `app/src/main/res/values*/expenses_strings.xml`: add `expenses_balance_owed_to_you_named` ("%1$s owes you %2$s") and `expenses_balance_you_owe_named` ("You owe %1$s %2$s")

Rules:
- The co-parent's name comes from the `ParentNames` already in scope: the slot that is not the
  signed-in parent's.
- With no name known, fall back to the existing generic keys. Update the KDoc at
  `ExpenseSummaryHeader.kt:415` to say why the name is now used: in a family of two the
  counterparty is the co-parent on screen.
- Czech wording: "%1$s čeká na vaši odpověď na žádosti.", "%1$s vám dluží %2$s", "Dlužíte
  %1$s %2$s". German, Russian and Ukrainian are translated likewise.

- [ ] **Step 1:** Add the strings in five locales; `node tools/check-invariants.js` passes.
- [ ] **Step 2:** Implement. Then commit: `fix: name the co-parent in the requests pop-up and the balance`

### Task 1.3: Chat activity cards are neutral, centred cards

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/chat/MessagesList.kt:458-561`
- Test: `app/src/test/java/com/coparently/app/screenshots/` (the existing chat-thread screenshot test; add one activity card to its fixture if it has none)

**Interfaces:**
- `MessageItem` branches on `message.activity != null` into a new
  `@Composable internal fun ActivityCard(message: Message, text: String, time: String, onOpen: (() -> Unit)?)`
  in a new file `presentation/chat/ActivityCard.kt`.

Rules:
- The card is centred (`Alignment.CenterHorizontally`), at most 85 % of the width, on
  `colorScheme.surfaceContainer`, in `MaterialTheme.shapes.medium`, with `Spacing.M` padding.
- Text uses `bodyMedium` in `onSurfaceVariant`, with the time in `labelSmall`.
- A chevron (`IconSizes.Small`) shows when `onOpen != null`.
- It has no ticks and no bubble shape.
- The settle-up payment block (`SettleUpPayment`) renders under it exactly as under the bubble
  today.
- `LocalChatEventCard`, `ChatCardTarget` and the report action are unchanged. Cards were never
  reportable.
- Semantics: one merged node, its text, and `Role.Button` when tappable.

- [ ] **Step 1:** Add an activity card message (`ActivityKind.EVENT_DECLINED` by the current user) to the chat-thread screenshot fixture.
- [ ] **Step 2:** Implement `ActivityCard`. Then commit: `feat(chat): system cards drawn as neutral centred cards, not the sender's bubble`

### Task 1.4: A colour legend over the calendar

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/calendar/components/CustodyLegend.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/components/MiniMonth.kt:216-270` (use the extracted pieces)
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/CalendarScreen.kt` (~801-815, after `FamilyMemberFilterStrip`)
- Strings: `calendar_legend_handover` ("Handover"), `calendar_legend_contact` ("Contact time") in five locales
- Test: `app/src/test/java/com/coparently/app/screenshots/` — add `CalendarLegendScreenshots` (text-heavy variant set)

**Interfaces:**
- `@Composable internal fun LegendEntry(label: String, swatch: @Composable () -> Unit)`, moved out
  of `MiniMonth.kt`.
- `@Composable internal fun CustodyLegend(parentNames: ParentNames, showContactWindows: Boolean, modifier: Modifier = Modifier)`:
  - one `FlowRow`;
  - each parent's name with `ParentColors.container(slot, alpha = ParentColors.custodyTintAlpha)`
    over `surface`;
  - a small square with the handover diagonal drawn as `MonthView` draws it;
  - when `showContactWindows`, a corner-triangle swatch.

Rules:
- `CalendarScreen` shows it in all three views, below the member strip.
- `showContactWindows` = the active model has any contact window.
- Its height must not change between months: it reads no month-specific data.

- [ ] **Step 1:** Add the screenshot test.
- [ ] **Step 2:** Extract and implement. Strings in five locales; invariants pass. Then commit: `feat(calendar): a legend for whose colour is whose and what the marks mean`

### Task 1.5: Small fixes — e-mails, pause default, switcher icon

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/pairing/components/PairedPartnerCard.kt:39-66` — drop the e-mail line and its `pairing_partner_email_unknown` use (delete the string in five locales if no other user)
- Modify: `app/src/main/java/com/coparently/app/presentation/common/SignedInAsRow.kt` — add `showEmail: Boolean = true`; `PairingScreen.kt:190` passes `false`
- Modify: `app/src/main/java/com/coparently/app/data/repository/PreferencesRepositoryImpl.kt:51-52` — default `true`
- Modify: `app/src/main/java/com/coparently/app/presentation/common/FamilySwitcher.kt:83` — `Icons.Default.Groups`
- Test: `app/src/test/java/com/coparently/app/data/repository/PreferencesRepositoryImplTest.kt` (create if absent)

- [ ] **Step 1: Write the failing tests:**
  - `pause before sending is on for a parent who never set it`: an empty `EncryptedPreferences`
    mock returning the default, so the flow's first value is `true`;
  - `a stored off stays off`.
- [ ] **Step 2:** Implement all four. Then commit: `fix: no e-mail addresses on pairing, pause before sending on by default, a family icon on the switcher`

### Task 1.6: Phase 1 on CI

- [ ] **Step 1:** Push. Read the PR's CI summary.
  - Expected: `build-test`, `static`, `detekt` and `invariants` green.
  - `screenshots` fails only on the images Tasks 1.3 and 1.4 changed or added.
- [ ] **Step 2:** Fix any red that is not an intended screenshot change, and push again.

---

## Phase 2 — Home

### Task 2.1: Home leads with today

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/home/HomeScreen.kt:171-177,436-642`
- Modify: `app/src/main/java/com/coparently/app/presentation/navigation/NavGraph.kt` (drop Home's `onOpenContacts`, `onOpenChildInfo`, `onOpenPets` wiring)
- Modify: `app/src/main/java/com/coparently/app/presentation/stickers/StickerViews.kt:95` only if `HomeStickers` loses its last caller (then delete it and anything only it used)

**Interfaces:**
- `HomeScreen(...)` loses `onOpenContacts`, `onOpenChildInfo` and `onOpenPets`.
- `Dashboard`'s `actNow` is: `HandoverHero` (when `nextHandover`), `DayAgendaCard`, `RequestsRow`
  group.
- `readAfter` is: "This week", the changes feed, `StatTiles`. The order is unchanged.
- The Contacts/Child/Pets group and the `HomeStickers(...)` call are removed.

- [ ] **Step 1:** Implement.
  - Update the KDoc that describes the order.
  - Replace the "emergency surface first" comment with a pointer to the spec's Phase 2.
- [ ] **Step 2:** `UiTourTest` — the `home_*` shots need no change. Any test that taps "Contacts" on Home moves to the Family tab in Task 4.4; grep `androidTest` for `home_contacts`/`settings_child_info_title` used from Home.
- [ ] **Step 3:** Commit: `feat(home): lead with today — handover, today and requests first`

---

## Phase 3 — onboarding

### Task 3.1: A custody save that never proposes

**Files:**
- Modify: `app/src/main/java/com/coparently/app/data/repository/CustodyModelRepository.kt` (beside `submitPattern`, ~line 414)
- Test: `app/src/test/java/com/coparently/app/data/repository/CustodyModelRepositoryTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface FirstSchedule {
      data object Activated : FirstSchedule
      data class SharedExists(val model: CustodyModel) : FirstSchedule
      data object Unavailable : FirstSchedule
  }
  suspend fun checkSharedSchedule(): FirstSchedule?
  suspend fun activateFirstSchedule(model: CustodyModel): FirstSchedule
  ```
- `checkSharedSchedule()` returns:
  - null when unpaired, or when a `Source.SERVER` read of the pair's document (under
    `withTimeoutOrNull(SHARED_READ_TIMEOUT_MS = 5_000)`) proves it absent;
  - `SharedExists(model)` when the read finds it;
  - `Unavailable` when the read cannot answer.

  Its KDoc states the null.
- `activateFirstSchedule(model)` reads the families once (`currentFamilies()`):
  - unpaired → `saveAndActivate`, then `Activated`;
  - paired and the read proves the document absent → `saveAndActivate(withActiveLayers(model,
    families))`, then `Activated`;
  - found → `SharedExists(found.model)`, with **no write of any kind**;
  - unreadable → `Unavailable`.

  It never calls `CustodyProposalTransition.propose`.

- [ ] **Step 1: Write the failing tests:**
  - `activateFirstSchedule never proposes over an existing shared schedule`:
    - paired, `getCustody` returns a document;
    - assert `SharedExists`;
    - `coVerify(exactly = 0) { firestoreCustodyDataSource.setCustody(any(), any(), any()) }`;
    - no Room write (`coVerify(exactly = 0)` on the DAO insert the class uses).
  - `activateFirstSchedule activates when the pair has no document`: absent, so `Activated` and
    `saveAndActivate`'s DAO insert ran.
  - `activateFirstSchedule activates unpaired without a read`.
  - `activateFirstSchedule reports an unreadable server and writes nothing`.
  - `checkSharedSchedule is null unpaired and SharedExists when the document is there`.
- [ ] **Step 2:** Implement. Then commit: `feat(custody): a first-schedule save that can only activate, never propose`

### Task 3.2: The custody form becomes reusable composables

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/custody/CustodyPatternForm.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/custody/CustodySetupScreen.kt:218,453-500,605-700,840-870`
- Create: `app/src/main/java/com/coparently/app/presentation/custody/SchedulePreviewMonth.kt`

**Interfaces:**
- `@Composable internal fun CustodyPatternForm(state: CustodySetupUiState, parentNames: ParentNames, actions: CustodyPatternActions, modifier: Modifier = Modifier)`
  - It covers: pattern type cards (the moved `ModelTypeCard`), the custom-days editor when
    CUSTOM, the start date (`DatePickerField`) and who starts first.
  - It excludes: midweek, contact windows, per-child and answer-by. Those stay in
    `CustodySetupScreen` only.
- `internal data class CustodyPatternActions(val selectType: (CustodyModelType) -> Unit, val setStartDate: (LocalDate) -> Unit, val setMomFirst: (Boolean) -> Unit, val setCustomDays: …)`, matching
  `CustodySetupViewModel`'s setters at :235-290.
- `@Composable internal fun SchedulePreviewMonth(model: CustodyModel, parentNames: ParentNames, modifier: Modifier = Modifier)`:
  - `MiniMonth(MiniMonthModel.build(YearMonth.from(maxOf(today, model.startDate)), model::getCustodyFor, emptyList(), emptyMap()), parentNames)`;
  - plus the one-line `custodyPreviewText`, made `internal`.

Rules:
- `CustodySetupScreen` renders the same form through `CustodyPatternForm`. Its 14-chip preview
  card is replaced by `SchedulePreviewMonth` for the family scope (the chips stay for a child's
  scope only if the month cannot show it; otherwise use the month there too).
- Test: add `SchedulePreviewMonth` to `app/src/test/java/com/coparently/app/screenshots/RequestScreenshots.kt`'s neighbour, `CustodyScreenshots.kt` (new): week on/week off from 2026-05-04, both palettes.

- [ ] **Step 1:** Add the screenshot test.
- [ ] **Step 2:** Extract and implement, with no behaviour change in `CustodySetupScreen` beyond the preview. Then commit: `refactor(custody): the pattern form and a month preview as shared composables`

### Task 3.3: Four onboarding steps with the embedded schedule

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/onboarding/OnboardingStep.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/custody/CustodySetupViewModel.kt`
- Modify: strings in `onboarding_strings.xml` (five locales): `onboarding_custody_looks_right` ("Looks right"), `onboarding_custody_propose_change` ("Propose a change"), `onboarding_custody_existing_body` ("This is the schedule you share with %1$s. A change is proposed to them and waits for their answer."), `onboarding_custody_checking` ("Checking for a schedule you already share…"). Delete the keys only removed steps used.
- Test: `OnboardingStepTest.kt`, `OnboardingViewModelTest.kt`, `app/src/test/java/com/coparently/app/presentation/custody/CustodySetupViewModelTest.kt`

**Interfaces:**
- `enum class OnboardingStep { CoParent, Notifications, Profile, Custody }`.
  - `stepsFor()` takes no argument and returns all four.
  - `isSkippable` is true for every step except Profile.
- `SHORT_PATH`, `shortPath`, `openCalendarNow` and `canOpenCalendarNow` are removed.
  `openCalendarNow`'s country guess (`HolidayCountry.ofCodeOrNull` from the device region)
  preselects the country for every profile whose stored country is the default and was never
  chosen. Keep the existing rule `openCalendarNow` used.
- The Profile step shows name, colour and country/region. `dateOfBirth`/`phone` leave
  `OnboardingUiState` and `saveProfile`.
- `CustodySetupViewModel` gains:
  ```kotlin
  sealed interface OnboardingScheduleMode {
      data object Checking : OnboardingScheduleMode
      data object Form : OnboardingScheduleMode
      data class Existing(val model: CustodyModel) : OnboardingScheduleMode
  }
  val onboardingMode: StateFlow<OnboardingScheduleMode>   // starts Checking
  fun checkOnboardingMode()                                // checkSharedSchedule(): SharedExists → Existing, else Form
  fun saveFirstSchedule(onDone: () -> Unit)                // activateFirstSchedule(state.toPatternModel(UUID)) with contact windows none:
                                                           //   Activated → onDone(); SharedExists → mode = Existing; Unavailable → error custody_setup_save_failed
  ```
- The Custody step:
  - `Checking` shows a progress row with `onboarding_custody_checking`.
  - `Form` shows `CustodyPatternForm` and `SchedulePreviewMonth`. Finish calls
    `saveFirstSchedule { viewModel.finish() }`.
  - `Existing` shows `SchedulePreviewMonth(model)` and `onboarding_custody_existing_body` (the
    co-parent's name). The primary button "Looks right" calls `finish()` and writes nothing. The
    text button "Propose a change" opens `Screen.CustodySetup.routeFor()` (the existing
    `onOpenCustodySetup`) and does not finish.
  - Skip finishes without a schedule.

- [ ] **Step 1: Write the failing tests:**
  - `OnboardingStepTest`: the list is exactly `[CoParent, Notifications, Profile, Custody]`;
    only Profile is not skippable.
  - `OnboardingViewModelTest`:
    - delete the tests of removed steps (`:356-536`, `:614-858`, `:880-945` short path);
    - keep and adapt profile, finish and custody-reporting tests;
    - add `the profile step no longer saves date of birth or phone`: the saved user keeps the
      stored values.
  - `CustodySetupViewModelTest`:
    - `onboarding mode is Existing when the pair already shares a schedule`;
    - `onboarding mode is Form when none is shared or the server cannot answer`;
    - `saving the first schedule never proposes`: `activateFirstSchedule` returns
      `SharedExists`, so mode is `Existing`, `onDone` is not called, and
      `coVerify(exactly = 0) { repository.submitPattern(any(), any(), any()) }`.
- [ ] **Step 2:** Implement.
  - Delete the removed steps' composables and ViewModel code.
  - Update the KDoc at `OnboardingStep.kt:18-21`.
  - Update `UiTourOnboardingTest` only if a string it waits on was removed (it walks Next/Finish
    generically).
- [ ] **Step 3:** Invariants pass. Then commit: `feat(onboarding): four steps, with the schedule and its month preview inside the wizard`

### Task 3.4: Phases 2–3 on CI

- [ ] **Step 1:** Push. Read the CI summary. Fix every red except intended screenshot changes, and push again.

---

## Phase 4 — the Family tab

### Task 4.1: The month's money as a shared source, and the Family screen

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/common/MonthMoneySource.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/home/HomeViewModel.kt:450-525` (use it; delete the moved code; `MonthSpendDependencies` shrinks or goes)
- Modify: `app/src/main/java/com/coparently/app/presentation/home/HomeScreen.kt:870-900` (`balanceCaption` moves)
- Create: `app/src/main/java/com/coparently/app/presentation/family/FamilyScreen.kt`, `FamilyViewModel.kt`, `FamilyMoneyCard.kt`
- Strings: `family_strings.xml` (five locales) — `nav_family` ("Family"; cs "Rodina", de "Familie", ru "Семья", uk "Сім'я"), `family_title`, the group labels `family_group_money` ("Money"), `family_group_agreements` ("Agreements"), `family_group_home` ("Children and home"), `family_group_people` ("People"), `family_group_mine` ("Only mine"), `family_money_this_month` ("This month"), `family_money_open` ("Expenses"), `family_children_add` ("Add your children")
- Test: `app/src/test/java/com/coparently/app/presentation/family/FamilyViewModelTest.kt`, `app/src/test/java/com/coparently/app/presentation/common/MonthMoneySourceTest.kt`, `app/src/test/java/com/coparently/app/screenshots/FamilyScreenshots.kt`

**Interfaces:**
- `data class MonthMoney(val spend: MonthSpend, val balances: List<CurrencyBalance>)`
- `class MonthMoneySource @Inject constructor(expenseRepository: ExpenseRepository, preferencesRepository: PreferencesRepository, familyViewSource: FamilyViewSource, parentsSource: ParentsSource, userRepository: UserRepository) { fun observe(): Flow<MonthMoney> }`
  - Exactly HomeViewModel's `monthExpenses` + `monthSpend` + `monthBalances` logic, moved.
  - `MonthSpend` and `CurrencyAmount` move with it if they are only Home's.
- `@Composable internal fun balanceCaption(balances: List<CurrencyBalance>): String`, moved to
  `presentation/common/MoneyText.kt` from `HomeScreen.kt:870`.
- `@HiltViewModel class FamilyViewModel @Inject constructor(monthMoneySource: MonthMoneySource, childInfoRepository: ChildInfoRepository)` exposes:
  - `money: StateFlow<MonthMoney?>` (null while loading);
  - `hasChildren: StateFlow<Boolean>` (from `getAllChildInfo()`, the family on screen).
- `FamilyScreen(onOpenExpenses, onOpenCustody, onOpenParentingPlan, onOpenExport, onOpenMonthReview, onOpenChildInfo, onOpenPets, onOpenContacts, onOpenDocuments, onOpenPairing, onOpenMyProfile, onOpenCoParentProfile, onOpenFriends, onOpenProfessionals, onOpenUnassignedRecords, onOpenJournal, onOpenStickers, onOpenSettings, viewModel: FamilyViewModel = hiltViewModel(), settingsViewModel: SettingsViewModel = hiltViewModel())`.
  - Top bar: title, `FamilySwitcherChip()`, gear.
  - A `LazyColumn` of the five groups in the spec's order and contents.
  - The family-kind dialog and the split-ratio dialog with its row move here *from*
    `SettingsScreen`, reusing `SettingsViewModel`'s `caresFor`/`myCaresFor`/split state and its
    dialog composables. Make those dialogs `internal` if they are private.
  - Pets show when `FamilyKind.PETS in caresFor`. Children read `family_children_add` as
    supporting text when `!hasChildren`.
  - `UnassignedRecordsRow` and `StickersRow` are reused as they are.
- `FamilyMoneyCard(money: MonthMoney?, onOpen: () -> Unit)`:
  - one tonal card;
  - label `family_money_this_month`;
  - the spend per currency, one line each, `formatMoney`, never cut (design item 15);
  - `balanceCaption(balances)` under it;
  - a trailing chevron;
  - the whole card opens Expenses.
  - From font scale 1.3 nothing sits beside the amounts.

- [ ] **Step 1: Write the failing tests:**
  - `MonthMoneySourceTest`: move HomeViewModelTest's month-spend and balance cases here unchanged
    (same fixtures, same expected figures), including "an expense of another family counts for
    nothing".
  - `FamilyViewModelTest`: `money follows the source`; `hasChildren is false with no child
    records and true with one`.
  - `FamilyScreenshots`: `FamilyMoneyCard` with CZK and EUR balances, text-heavy variants (2.0×
    German included).
- [ ] **Step 2:** Implement. `HomeViewModel` reads `MonthMoneySource`; HomeViewModelTest's moved cases are deleted there.
- [ ] **Step 3:** Invariants pass. Then commit: `feat(family): a Family hub with the month's money, agreements, home, people and what is only mine`

### Task 4.2: Family replaces Expenses in the tab bar; Expenses becomes a detail screen

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/navigation/BottomNavDestination.kt:84-89`
- Modify: `app/src/main/java/com/coparently/app/presentation/navigation/NavGraph.kt:316-323,1146-1177,1484,1574-1850`
- Modify: `app/src/main/java/com/coparently/app/presentation/expenses/ExpenseScreen.kt:154,229-244`
- Modify: `app/src/main/res/values*/navigation.xml` — `nav_family` added (if not in Task 4.1's file), `nav_expenses` kept for the Expenses title
- Test: `app/src/androidTest/java/com/coparently/app/presentation/navigation/MainNavigationSmokeTest.kt`

**Interfaces:**
- `BottomNavDestination.FAMILY(route = Screen.Family.route, labelRes = R.string.nav_family, Icons.Filled.Groups, Icons.Outlined.Groups)` replaces `EXPENSES`.
- `Screen.Family = "family"`.
- `ExpenseScreen(onNavigateUp: () -> Unit, onAddExpense, onEditExpense, onSettleUp, viewModel)`:
  - `onOpenSettings` is removed;
  - `navigationIcon` is the up arrow;
  - `FamilySwitcherChip` and the FAB stay.
- NavGraph:
  - `family` is a tab `composable()` with `tabEnter`/`tabExit`.
  - `expenses` stays a full-width `composable()` (not `pane()`) with the slide transitions detail
    screens use.
  - Home's spend tile opens `Screen.Expenses.route` with `navigate`, not `navigateToTab`.
  - `PushDestination.EXPENSES`: `navigateToTab(FAMILY)`, then `navigate(Screen.Expenses.route)`.
  - `onSettleUp` keeps its `popUpTo(Home){saveState=true}; launchSingleTop; restoreState=false`
    shape so Expenses does not stay on the Chat tab's stack.
  - Wire every `FamilyScreen` callback to the routes Settings used (NG:492-557).

- [ ] **Step 1: Update the failing tests** in `MainNavigationSmokeTest`:
  - the tab loop uses `FAMILY`;
  - `family tab opens and its money card opens expenses with an up arrow`;
  - `back from expenses returns to the family tab`.
- [ ] **Step 2:** Implement. Then commit: `feat(navigation): a Family tab in place of Expenses; Expenses opens from it`

### Task 4.3: Settings keeps the app and the account

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/settings/SettingsScreen.kt:209-790`
- Modify: `NavGraph.kt:484-557` (drop the moved callbacks)
- Modify: `app/src/test/java/com/coparently/app/screenshots/DesignSystemScreenshots.kt:61,135` and `ContrastScreenshots.kt:30` (`SettingsFamilyGroupFixture` becomes an App group fixture)

Rules:
- Settings' groups become **App**, **Sync**, **Account**, with rows as the spec's Phase 4 lists.
- "My colour", country and region move from Family to App.
- "Family shown" moves to Sync.
- Remove every moved row, its callback parameter and its dialog state from `SettingsScreen`,
  and the strings no one uses any more (five locales).
- `SettingsScreen` loses its `LongParameterList` weight accordingly. Keep the `@Suppress` only if
  still needed.

- [ ] **Step 1:** Rebuild the screenshot fixture to match.
- [ ] **Step 2:** Implement. Invariants pass. Then commit: `refactor(settings): settings keep the app and the account; the family moved to its tab`

### Task 4.4: Android tests follow the new tabs

**Files:**
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/UiTourTest.kt:269-356,360-380,441-448,488`
- Modify: `app/src/androidTest/java/com/coparently/app/qa/IsolationScenarioTest.kt:123`
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/OnScreenIsolationTest.kt:68,86,89,95,111`
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/OnScreenFamiliesTest.kt:156-157`

Rules:
- `UiTourTest`:
  - Add a `fromFamily(name, rowTitle, content)` beside `fromSettings`.
  - Every shot whose row moved uses it.
  - Add `camera.shot("family")` on opening the tab.
  - `expensesSection` opens Family, then the money card. Its shot names stay `expenses_list`,
    `expenses_scrolled`, `expenses_analytics` and `expense_add`, so `tools/play-promo/captions.js`
    still resolves.
  - `SETTINGS_SCREENFULS` becomes 2.
- `OnScreenIsolationTest`: `openSettingsRow` becomes `openFamilyRow` for child info and pets.
- `OnScreenFamiliesTest`: the pairing row is reached from Family.

- [ ] **Step 1:** Implement. Then commit: `test: android tests reach the family's screens from the Family tab`

### Task 4.5: CLAUDE.md and the checklist

**Files:**
- Modify: `CLAUDE.md`:
  - design refresh item 1 (Home/Settings/Expenses/Family render through the primitives);
  - item 4 (Settings is App, Sync, Account; Family is the tab);
  - item 13 (the chip is in the Home, Family and Expenses top bars);
  - item 19 (Expenses is the one full-width detail screen, and why);
  - UX overhaul item 1 (tabs Home / Calendar / Chat / Family; deep links);
  - "Things that are easy to get wrong" item 22 (onboarding is four steps; Next never proposes;
    `activateFirstSchedule`);
  - Design refresh item 5 (a legend row under the member strip).
- Modify: `docs/DEVICE-CHECKLIST.md` — a check for:
  - the Family tab;
  - a push to Expenses from a cold start;
  - the onboarding schedule step for an invited parent with a slow network.

  Add the matching `RULES` entries in `tools/manual-test-plan.js` if the checklist sections are
  new, and keep `node --test tools/test/*.test.js` green.

- [ ] **Step 1:** Edit. Run `node tools/check-invariants.js && node --test tools/test/*.test.js`. Expected: no failures.
- [ ] **Step 2:** Commit: `docs: CLAUDE.md and the device checklist for the Family tab and the new onboarding`

---

## Phase 5 — tone and language

### Task 5.1: Czech without gender hedges; natural forms

**Files:**
- Modify: `app/src/main/res/values-cs/*.xml`. These are the keys the review listed; re-grep
  `grep -n '(a)' app/src/main/res/values-cs/*.xml`:
  - `calendar_event_pickup_confirmed`
  - `chat_departed_banner`
  - `settle_up_no_account`
  - `common_signed_in_as`
  - `event_acceptance_offered_to_you`
  - `event_form_pickup_confirmed_by`
  - `expenses_row_subtitle`
  - `expenses_split_proposal_waiting_body_named`
  - `export_col_by`
  - `friend_calendar_added_by`
  - `home_section_partner_changed`
  - the `onboarding_coparent_*` keys
  - `onboarding_from_coparent`
  - `parenting_plan_their_answer_missing`
  - the `push_*` bodies
  - `documents_row_meta`
- Modify: the Czech for "Den rodiče %s" and "Za %d dny přebírá: %s" (find by grep).
- Check `values-ru`/`values-uk` for the same `(а)`/`(ла)` hedge pattern and fix likewise.

Rules:
- Rephrase so no verb needs gender:
  - a noun phrase: "Od: Bob", "Záznam od Boba";
  - the present tense where it reads naturally;
  - "Bob — přidáno";
  - or the pattern already documented at `values-cs/calendar_strings.xml:91` and
    `home_strings.xml:80`.
- Keep every format argument. Invariants check this.
- "Den rodiče Alice" becomes "U %1$s" only if `%1$s` takes the genitive naturally for any name;
  otherwise use "Den s: %1$s". Pick one, apply it everywhere the pattern repeats, and note it in a
  comment.
- "Za 2 dny přebírá: Bob" becomes "Za 2 dny předání: %s".

- [ ] **Step 1:** Edit. Invariants pass. Then commit: `fix(cs): Czech copy without gender hedges`

### Task 5.2: Final CI, baselines, UI tour, hand-over

- [ ] **Step 1:** Push. Fix every non-screenshot red.
- [ ] **Step 2:** Touch `.github/regenerate-request` (a line naming why: "Family tab, Home order, legend, chat cards, onboarding preview"). Commit `chore(ci): regenerate screenshot baselines for the new user's first week`. Push, and wait for the bot commit.
- [ ] **Step 3:** Touch `.github/ui-tour-request` with `variants: light-cs-100 light-en-100`. Push. Fetch `ui-tour/claude-charming-meitner-fl3j13`, and review home, onboarding, family, settings, calendar and chat shots against the spec.
- [ ] **Step 4:** Confirm CI is green on the head. Mark the PR ready for review. Report to the owner with before/after images.
