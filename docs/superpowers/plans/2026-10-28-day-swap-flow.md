# Day swap flow (PR 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A parent who has never seen a day swap proposes one, and the co-parent answers it, without a wrong turn; Home calls the reader "you".

**Architecture:** Direction is decided once, as `SwapSides` (which days go to whom), and every surface reads it: the selection bar, the review sheet, the grid's preview marks, the push and the answer card. The selection stays screen state in `CalendarScreen`, now saveable and with an explicit "selecting" flag, so the "⇄" button can open it empty. The push gains two date-list fields on the existing swap types; the receiving phone words them.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (Material 3 1.4), Hilt, Firestore rules + `firestore-tests` (mocha on the emulator), JUnit/MockK/Robolectric, Roborazzi screenshots.

**Spec:** `docs/superpowers/specs/2026-10-28-day-swap-ux-and-requests-design.md` (PR 1 part, steps 1–7).

## Global Constraints

- No local Android SDK: `./gradlew` runs only in CI. Locally run `node tools/check-invariants.js`, the detekt script in the scratchpad (`tools/detekt.sh`), `unused_imports.py`, and `cd firestore-tests && npm test` where a JDK 21 is available.
- Every new user-facing string goes into `values`, `values-cs`, `values-de`, `values-ru`, `values-uk` in the same commit; RU/UK say "проживание"/"проживання", never "опека"/"опіка"; English is sentence case.
- Parents are named through `ParentNames` (`labelFor`, `labelOrYou`, `isReader`), never "Mom"/"Dad"; colours through `ParentColors` (`fill`, `container`, `chipFill`, `onFill`), never `CoPlanlyColors.MomPink/DadBlue`.
- Corners from `MaterialTheme.shapes`/`CoPlanlyCorners`, gaps from `Spacing`, icon sizes from `IconSizes`, durations from `Motion.kt`; no literal `RoundedCornerShape`, no literal dp on icons.
- A borrowed (neighbouring-month) cell takes no swap preview and no pending mark (design item 10).
- Swap storage, `DayOverride`, the rules for `dayOverrides` and the request clock are unchanged.
- One push per offer: a multi-day offer stays one group with one announcement.
- Push data are strings; each new client key is bounded in `firestore.rules`' `notification_queue` create rule.
- Conventional Commits; commit trailers as the session's attribution reminder gives them.

## Review Focus

1. **A selection that spans two months** (pick 30 Nov, page, pick 2 Dec): both stay picked, the bar lists both, the sheet's mini months show both months. Pinned in Task 4 (`SwapTapTest`) and Task 6 (`SwapSidesTest`).
2. **A day with no custody answer inside the selection**: never gets a preview, is left out of the bar's lines and of the push, and the sheet counts it out loud. Pinned in Task 1 (`SwapSidesTest.unknownDaysAreLeftOut`).
3. **An older sender (no `datesToYou`/`datesToSender`)**: the push words the old sentence, byte for byte. Pinned in Task 3 (`SwapPushWordingTest.withoutFieldsIsNull`).
4. **A part-answered offer reopened**: card and dialog show only the days still waiting; with one day left, "Answer day by day" is gone and the button reads "Accept". Pinned in Task 9 (`DaySwapGroupWaitingTest`).
5. **Rotation and process death mid-selection**: the selection and the selecting flag survive (`rememberSaveable`). Pinned in Task 4 (`LocalDateSetSaverTest`).

---

### Task 1: One split of a swap into its two sides, and one date-list wording

**Files:**
- Create: `app/src/main/java/com/coparently/app/domain/custody/SwapSides.kt`
- Create: `app/src/main/java/com/coparently/app/utils/SwapDateList.kt`
- Test: `app/src/test/java/com/coparently/app/domain/custody/SwapSidesTest.kt`
- Test: `app/src/test/java/com/coparently/app/utils/SwapDateListTest.kt`

**Interfaces:**
- Produces:
  - `data class SwapSides(val toOther: List<LocalDate>, val toOfferer: List<LocalDate>, val unknown: List<LocalDate>)` — sorted lists; `val offerable: List<LocalDate>` (`toOther + toOfferer`, sorted); `val isEmpty: Boolean`.
  - `SwapSides.Companion.of(dates: Collection<LocalDate>, holderOf: (LocalDate) -> String?, offererSlot: String): SwapSides` — a day held by `offererSlot` goes to the other parent (`toOther`); a day held by the other slot goes to the offerer (`toOfferer`); `null` holder → `unknown`. Duplicates collapse.
  - `SwapSides.Companion.ofOffer(group: DaySwapGroup, offererSlot: String): SwapSides` — from stored overrides: `toParent == offererSlot` → `toOfferer`, else `toOther`.
  - `fun receiverOf(holder: String?): String?` (top-level in `SwapSides.kt`): `"mom" ↔ "dad"`, else null. Replaces the two inline `when (getCustody(day))` copies in `CalendarScreen`.
  - `fun swapDateList(dates: List<LocalDate>, locale: Locale): String` — each date as `localizedDate("EEEdMMM", locale)`, joined with ", ".

- [ ] **Step 1: Write the failing tests**

```kotlin
// SwapSidesTest
@Test fun `a day the offerer holds goes to the other parent and theirs comes back`() {
    val sides = SwapSides.of(listOf(nov6, nov7, nov14), holderOf = { if (it == nov14) "dad" else "mom" }, offererSlot = "mom")
    assertEquals(listOf(nov6, nov7), sides.toOther)
    assertEquals(listOf(nov14), sides.toOfferer)
}
@Test fun unknownDaysAreLeftOut() {
    val sides = SwapSides.of(listOf(nov6, nov8), holderOf = { if (it == nov8) null else "mom" }, offererSlot = "mom")
    assertEquals(listOf(nov8), sides.unknown); assertEquals(listOf(nov6), sides.offerable)
}
@Test fun `an offer read back splits by whom each day goes to`() // ofOffer: toParent "mom" with offerer "mom" → toOfferer
@Test fun `two months in one selection stay sorted`() // nov30, dec2 → toOther == [nov30, dec2]

// SwapDateListTest
@Test fun `dates are listed in the reader's language`() {
    assertEquals("Fri, Nov 6, Sat, Nov 7", swapDateList(listOf(nov6, nov7), Locale.US)) // assert against localizedDate("EEEdMMM", Locale.US) joined, not a literal, if the JVM's pattern differs
}
```

- [ ] **Step 2:** `node tools/check-invariants.js` passes; tests fail in CI until Step 3 (no local Gradle).
- [ ] **Step 3: Implement** `SwapSides.kt` and `SwapDateList.kt` with KDoc; replace `CalendarScreen`'s two `when (getCustody(day)) { "mom" -> "dad" … }` with `receiverOf(getCustody(day))`.
- [ ] **Step 4:** detekt script and `unused_imports.py` clean.
- [ ] **Step 5: Commit** `feat: one split of a day swap into its two sides`

### Task 2: The swap push carries its dates and its sender

**Files:**
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/PushPayload.kt` (beside `DAY_COUNT`)
- Modify: `app/src/main/java/com/coparently/app/data/repository/CustodyModelRepository.kt` (`applyDayOverrides` :1162, `applyDayOverridesForDates` :1208, `announceRun`, `announceSwap` :1340, `notifyPartnerOfSwap` :1370, `swapPush` :1618)
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/CalendarViewModel.kt:340-428`
- Modify: `firestore.rules` (`notification_queue` create, after the `dayCount` bound)
- Test: `firestore-tests/rules/notification-payload.test.js`
- Test: `app/src/test/java/com/coparently/app/data/repository/SwapPushTest.kt`
- Test: `app/src/androidTest/java/com/coparently/app/e2e/TwoParentCustodyTest.kt` (both swap cases)

**Interfaces:**
- Consumes: `SwapSides.ofOffer` (Task 1). The wire carries ISO dates, so `swapDateList` is not used here.
- Produces:
  - `PushPayload.DATES_TO_YOU = "datesToYou"`, `PushPayload.DATES_TO_SENDER = "datesToSender"` — comma-separated ISO dates from the **addressee's** view; KDoc says an older build ignores them.
  - `data class SwapOfferer(val slot: String, val name: String)` (public, in `CustodyModelRepository.kt`; the ViewModel builds it).
  - `applyDayOverrides(date, transform, answerBy = null, offerer: SwapOfferer? = null)` and `applyDayOverridesForDates(dates, transform, answerBy = null, offerer: SwapOfferer? = null)`.
  - `internal fun swapPush(date: String, status: DayOverrideStatus, dayCount: Int, sides: SwapSides? = null, actorName: String? = null): Map<String, String>?` — for `PENDING` with `sides`, adds `DATES_TO_YOU` = `sides.toOther` joined, `DATES_TO_SENDER` = `sides.toOfferer` joined, each only when non-empty; adds `ACTOR` = name `take(100)` when not blank (every status). `internal` so the test reaches it.
  - `CalendarViewModel.swapSent: StateFlow<SwapSent?>`, `data class SwapSent(val groupId: String, val dayCount: Int)`, `fun clearSwapSent()`. Both offer functions set it on success.

- [ ] **Step 1: Write the failing rules test** in `notification-payload.test.js`: a co-parent's `day_swap_group_offered` with `datesToYou` of 14 ISO dates joined (153 chars) is allowed; 161 chars refused; same pair for `datesToSender`.
- [ ] **Step 2:** `cd firestore-tests && npm test` → the two "refused" cases FAIL (the key is unbounded today).
- [ ] **Step 3:** add to the create rule, with a comment like its neighbours:
  `isValidLength(request.resource.data.get('data', {}).get('datesToYou', ''), 0, 160) && isValidLength(request.resource.data.get('data', {}).get('datesToSender', ''), 0, 160) &&`
- [ ] **Step 4:** `npm test` → PASS.
- [ ] **Step 5: Write the failing unit test** `SwapPushTest`:
  - offered group with sides(toOther=[6,7], toOfferer=[14]) → `datesToYou == "2026-11-06,2026-11-07"`, `datesToSender == "2026-11-14"`, `type == "day_swap_group_offered"`, `dayCount == "3"`;
  - only toOther → no `datesToSender` key;
  - `ACCEPTED` with sides → no date keys; actor present;
  - `sides == null` → the map equals today's exactly (an offer from a caller that passes nothing).
- [ ] **Step 6: Implement.** `announceSwap`/`announceRun` compute `SwapSides.ofOffer(DaySwapGroup(changed days), offerer.slot)` from the written map when `offerer != null` and pass it with `offerer.name` down to `swapPush`. In `CalendarViewModel`, both offer functions build `SwapOfferer(me.slot, me.name)` from `parents.value.me` (`NotReady` when slot is null) and emit `SwapSent(groupId, targets.size)` on success.
- [ ] **Step 7:** e2e: in both `TwoParentCustodyTest` swap cases, read the queued push with `EmulatorEnvironment.awaitQueuedPush` and assert `datesToYou`/`datesToSender` and `actorName`.
- [ ] **Step 8:** invariants, detekt, unused imports clean.
- [ ] **Step 9: Commit** `feat: a day-swap push names its dates and its sender`

### Task 3: The receiving phone says who offers which days

**Files:**
- Create: `app/src/main/java/com/coparently/app/data/remote/firebase/SwapPushWording.kt`
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/PushNotifier.kt` (`compose` :111)
- Modify: `app/src/main/res/values*/push_strings.xml` (5 locales)
- Test: `app/src/test/java/com/coparently/app/data/remote/firebase/SwapPushWordingTest.kt`
- Test: `app/src/androidTest/java/com/coparently/app/data/remote/firebase/PushNotificationTest.kt` (one case per direction, EN and DE)

**Interfaces:**
- Consumes: `PushPayload.DATES_TO_YOU/DATES_TO_SENDER/ACTOR`, `swapDateList`.
- Produces:
  - `internal sealed interface SwapPushWording { data class Gives(val toYou: List<LocalDate>); data class Asks(val toSender: List<LocalDate>); data class Both(val toYou: List<LocalDate>, val toSender: List<LocalDate>) }`
  - `internal fun swapPushWording(type: String?, data: Map<String, String>): SwapPushWording?` — non-null only for `DAY_SWAP_OFFERED`/`DAY_SWAP_GROUP_OFFERED` with at least one readable date in either field; unparseable dates are dropped; both empty → null.
  - Strings (`push_strings.xml`): `push_day_swap_gives_title` plurals "%1$s offers you %2$d day(s)", `push_day_swap_gives_body` "%1$s will be with you.", `push_day_swap_asks_title` plurals "%1$s asks for %2$d of your days" (one: "…for one of your days"), `push_day_swap_asks_body` "%1$s would go to %2$s.", `push_day_swap_both_title` "%1$s proposes a day swap", `push_day_swap_both_body` "You: %1$s. %2$s: %3$s." RU: "%1$s предлагает вам %2$d день/дня/дней", "%1$s — у вас.", "%1$s просит %2$d ваш день/ваших дня/ваших дней", "%1$s перейдут к %2$s." (one: "перейдёт"), "%1$s предлагает обмен днями", "Вам: %1$s. %2$s: %3$s.". Translate CS/DE/UK in the same spirit.

- [ ] **Step 1: Write the failing test** `SwapPushWordingTest`: `Gives` from only `datesToYou`; `Asks` from only `datesToSender`; `Both`; `withoutFieldsIsNull` (no keys → null); `day_swap_accepted` with the keys → null; a garbled date dropped, all garbled → null.
- [ ] **Step 2:** fails in CI until Step 3.
- [ ] **Step 3: Implement** `swapPushWording`; in `compose`, before the `PUSH_TEXT` lookup, when it is non-null build title/body from the strings above (actor = `ACTOR` or `push_actor_fallback`; dates via `swapDateList(…, locale)`) and pass the body through `withDeadline` so "Answer by …" still follows.
- [ ] **Step 4:** add the strings in five locales; `node tools/check-invariants.js` → PASS (format arguments agree).
- [ ] **Step 5:** `PushNotificationTest`: post each direction, read back from `activeNotifications`, assert title and body in English and German; an old-shape payload still reads "Your co-parent proposes swapping …".
- [ ] **Step 6: Commit** `feat: a day-swap push says which days go to whom`

### Task 4: Selection mode that opens empty, survives paging, and only selects

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/calendar/SwapTap.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/CalendarScreen.kt:261-305, 560-575, 800-860`
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/MonthView.kt` (params :150-164, click :735-746)
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/components/CustodyLegend.kt` (trailing slot)
- Modify: `app/src/main/res/values*/day_swap_strings.xml`
- Test: `app/src/test/java/com/coparently/app/presentation/calendar/SwapTapTest.kt`, `LocalDateSetSaverTest.kt`

**Interfaces:**
- Produces:
  - `internal sealed interface SwapTap { data class Toggle(val date: LocalDate); data class PageAndToggle(val month: YearMonth, val date: LocalDate); data class OpenDay(val date: LocalDate) }`
  - `internal fun swapTapFor(date: LocalDate, shownMonth: YearMonth, selecting: Boolean): SwapTap`
  - `internal val LocalDateSetSaver: Saver<Set<LocalDate>, List<String>>` (ISO strings).
  - `MonthView(…, selectingSwap: Boolean = false, onSwapTap: ((LocalDate) -> Unit)? = null)` — the cell's `onClick` calls `onSwapTap(date)` whenever `selectingSwap`, for in-month **and** borrowed days, never `onDayClick`. `selectingSwap` replaces `swapSelection.isNotEmpty()` as the mode signal.
  - `CustodyLegend(…, trailing: (@Composable () -> Unit)? = null)`.
  - `CalendarScreen` state: `var selectingSwap by rememberSaveable { mutableStateOf(false) }`, `var swapSelection by rememberSaveable(stateSaver = LocalDateSetSaver) { mutableStateOf(emptySet()) }`. A long press sets `selectingSwap = true`; cancel, send and the tab-tap reset clear both.
  - Strings: `day_swap_start` "Swap days" (content description and tooltip), `day_swap_choose_title` "Choose days to swap", `day_swap_close_selection` "Stop choosing days".

- [ ] **Step 1: Write the failing tests:** `swapTapFor(nov6, Nov, selecting=true) == Toggle(nov6)`; `swapTapFor(dec2, Nov, true) == PageAndToggle(Dec, dec2)`; `swapTapFor(dec2, Nov, false) == OpenDay(dec2)`; `LocalDateSetSaver` round-trips `{nov30, dec2}`.
- [ ] **Step 2:** fail until Step 3.
- [ ] **Step 3: Implement.** `PageAndToggle` → `calendarViewModel.showMonth(month)` then `SwapSelection.toggled`. The "⇄" is an `OutlinedIconButton` (`Icons.Default.SwapHoriz`, 44 dp min touch target via `minimumInteractiveComponentSize`, `IconSizes.Standard`) wrapped in a `TooltipBox`, passed as `CustodyLegend`'s `trailing`, shown only when `offerSwapDay != null` and `viewMode == MONTH`. While `selectingSwap`, the header row is replaced by a bar: ✕ (`day_swap_close_selection`), title `day_swap_choose_title`, and previous/next month icon buttons calling `showMonth(shownMonth ∓ 1)`; `BackHandler` cancels. Remove the comment that says plain `remember` is deliberate and say why it is saveable now (the selection outlives paging by design).
- [ ] **Step 4:** strings in five locales; invariants, detekt clean.
- [ ] **Step 5: Commit** `feat: day selection opens from a button and keeps its days across months`

### Task 5: Each chosen and each waiting day shows whom it goes to

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/calendar/SwapMark.kt`
- Modify: `MonthView.kt` (params; cell border :680-693; arrows :751-773 removed; draw after the contact-window corner)
- Modify: `CalendarScreen.kt:377-395` (pending map), `CustodyLegend.kt`
- Modify: `app/src/test/java/com/coparently/app/screenshots/CalendarScreenshots.kt` (`MonthGridFixture` :75-90)
- Test: `app/src/test/java/com/coparently/app/presentation/calendar/SwapMarkTest.kt`, `app/src/test/java/com/coparently/app/presentation/theme/SwapPreviewContrastTest.kt`

**Interfaces:**
- Consumes: `receiverOf` (Task 1).
- Produces:
  - `internal data class SwapMark(val toSlot: String, val pending: Boolean)`
  - `internal fun swapMarks(selection: Set<LocalDate>, holderOf: (LocalDate) -> String?, pendingTo: Map<LocalDate, String>): Map<LocalDate, SwapMark>` — a selected day with a known holder → `SwapMark(receiverOf(holder), pending=false)`; a pending day → `SwapMark(toParent, pending=true)`; a day in both → the selection's.
  - `MonthView(…, swapMarks: Map<LocalDate, SwapMark> = emptyMap())` replaces `pendingSwapDates` and `swapSelection`; drawn only when `isCurrentMonth`.
  - `CalendarScreen`: `pendingSwapTo: Map<LocalDate, String>` = pending overrides' `toParent`, minus `unansweredSwapDates` (same filter as today).
  - Drawing, per cell: diagonal hatch in `ParentColors.fill(toSlot)` at alpha `SWAP_HATCH_ALPHA = 0.35f` (pending `0.2f`), stripe width 3 dp every 8 dp; 2 dp border in `ParentColors.chipFill(toSlot)`, dashed (`PathEffect.dashPathEffect(4dp, 3dp)`) when pending; a pill at the bottom, `CoPlanlyCorners.Pill`, text `labelSmall` "→ " + first name or "you" (`parentNames.labelOrYou(toSlot)`, first word), filled `chipFill` with `ParentColors.onFill` text when selected, outlined `chipFill` on the cell base when pending. The cell's content description adds `day_swap_cell_goes_to` "goes to %1$s" / `day_swap_cell_waiting_to` "waiting, would go to %1$s".
  - `CustodyLegend(…, showPendingSwap: Boolean = false)` adds `LegendEntry(day_swap_legend_waiting "Waiting for an answer")` with a dashed swatch.
- [ ] **Step 1: Write the failing tests:** `swapMarks` cases above; `SwapPreviewContrastTest`: for every preset in `ParentColorChoice.entries` and both themes, `onSurface` day number over (base ⊕ custody wash ⊕ hatch at 0.35) ≥ 4.5:1, using `ParentColorsTest`'s compositing helpers; pill text on `chipFill` ≥ 4.5:1.
- [ ] **Step 2:** fail until Step 3. If a preset fails contrast, lower `SWAP_HATCH_ALPHA` rather than changing a test target.
- [ ] **Step 3: Implement.** Delete `SWAP_ARROW_SIZE`, the arrows and the `primary` selection border. Update `MonthGridFixture` to pass one selected and one pending `SwapMark`.
- [ ] **Step 4:** strings in five locales; invariants, detekt clean.
- [ ] **Step 5: Commit** `feat: the month grid marks whom each chosen or waiting day goes to`

### Task 6: The selection bar lists each side

**Files:**
- Modify: `CalendarScreen.kt` (`DaySwapSelectionBar` :1127 and its call :830)
- Modify: `day_swap_strings.xml` ×5
- Modify: `CalendarScreenshots.kt` (new `selectionBar()` snapshot, TEXT_HEAVY variants)

**Interfaces:**
- Consumes: `SwapSides.of`, `swapDateList`, `ParentNames`.
- Produces: `internal fun DaySwapSelectionBar(sides: SwapSides, parentNames: ParentNames, onNext: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier)` — `internal` for the screenshot. One row per non-empty side: colour dot (`ParentColors.fill`), bold receiver ("you" via `labelOrYou`), `day_swap_bar_side` plurals "%1$s — %2$d day: %3$s"; dates via `swapDateList` (they may wrap; no ellipsis on this line). The skipped count reuses the existing `day_swap_skipped_days` plurals. With no day picked: `day_swap_select_hint`. Buttons: text "Cancel", filled "Next" (`day_swap_next`), Next disabled while `sides.offerable` is empty.
- [ ] **Step 1:** screenshot test `selectionBar()` with sides toOther=[6,7], toOfferer=[14] and one unknown.
- [ ] **Step 2: Implement**, replacing `dayCount` with `sides`; drop the `maxLines = 1` comment's rationale only where the text may now wrap (the dates line), keep it on the hint.
- [ ] **Step 3:** strings ×5, invariants, detekt.
- [ ] **Step 4: Commit** `feat: the swap bar says which days go to whom`

### Task 7: The review sheet shows both sides and the result

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/components/DaySwapSheet.kt` (whole public signature and body)
- Modify: `CalendarScreen.kt:1028-1065`, `ChangeRequestsScreen.kt:209` (its caller)
- Modify: `day_swap_strings.xml` ×5
- Test: `CalendarScreenshots.kt` (`swapSheet()` over `DaySwapSheetContent`)

**Interfaces:**
- Consumes: `SwapSides`, `swapMiniMonths(days: Map<LocalDate, String>, familyEvents, custodyFor)`, `SwapMonths`, `AnswerByField`.
- Produces:
  - `fun DaySwapSheet(sides: SwapSides, coParentName: String, months: List<MiniMonthModel>, parentNames: ParentNames, onOffer: (dates: List<LocalDate>, note: String?, answerBy: AnswerByPick) -> Unit, onChangeDays: () -> Unit, onDismiss: () -> Unit)`
  - `internal fun DaySwapSheetContent(…same minus onDismiss…)` — the body, split out for Roborazzi (a `ModalBottomSheet` opens its own window).
  - Order: title `day_swap_sheet_title` "Swap with %1$s"; group `day_swap_you_give` "You give %1$s" with day chips (`swapDateList` per date, chip in `ParentColors.container(receiver)` with a `chipFill` border); group `day_swap_you_take` "You take"; empty groups not drawn; `SwapMonths`; caption `day_swap_sheet_caption` "This is how it will look if %1$s accepts. Nothing changes until they answer."; note field labelled `day_swap_note_to` "Message to %1$s (optional)"; `AnswerByField`; buttons text `day_swap_change_days` "Change days" → `onChangeDays` (keeps the selection, closes the sheet), filled `day_swap_send_to` "Send to %1$s".
  - Day view's "Swap this day" calls the same sheet with one-day sides.
- [ ] **Step 1:** screenshot `swapSheet()` for a two-sided offer.
- [ ] **Step 2: Implement.** Remove `SwapHeading`/`SwapSummary`/`DirectionLines` once nothing calls them; keep `isConsecutive` (other callers).
- [ ] **Step 3:** strings ×5, invariants, detekt, unused imports.
- [ ] **Step 4: Commit** `feat: the swap sheet shows what each parent gives and the month after`

### Task 8: After sending, the parent is told, and Day view names the direction

**Files:**
- Modify: `CalendarScreen.kt` (snackbar effect beside the `swapError` one)
- Modify: `calendar/components/DaySwapStrip.kt:69-90`
- Modify: `day_swap_strings.xml` ×5
- Test: `app/src/test/java/com/coparently/app/presentation/calendar/CalendarViewModelSwapTest.kt` (or the existing `CalendarViewModelTest`)

**Interfaces:**
- Consumes: `CalendarViewModel.swapSent`, `ChangeRequestViewModel.withdrawSwapGroup(group: DaySwapGroup)`.
- Produces:
  - Snackbar `day_swap_sent` "Sent to %1$s. The days stay as they are until they answer." with action `day_swap_withdraw` "Withdraw"; Withdraw builds the group from `dayOverrides` whose `groupId == sent.groupId` and calls `withdrawSwapGroup`. Then `clearSwapSent()`.
  - Strip wording for `OfferedByMe`: `isReader(toParent)` → `day_swap_strip_asked_for` "You asked %1$s for this day. Waiting for an answer."; else `day_swap_strip_offered_to` "You offered this day to %1$s. Waiting for an answer." (co-parent's name). The deadline stays on the inbox card; the strip carries none (decision: the strip has no clock to read).
- [ ] **Step 1: Write the failing VM test:** a successful `offerDaySwapForDates` emits `SwapSent(groupId, 3)`; a refused one emits none and sets `swapError`.
- [ ] **Step 2: Implement**; strings ×5; invariants, detekt.
- [ ] **Step 3: Commit** `feat: a sent swap says so, and Day view says which way it goes`

### Task 9: The co-parent answers one main pair, or day by day as its own mode

**Files:**
- Modify: `domain/custody/DaySwapInbox.kt` (`DaySwapGroup`)
- Modify: `presentation/changerequests/SwapDayAnswers.kt`, `ChangeRequestsScreen.kt:469-560` (`DaySwapCard`, `daySwapSentence`), `presentation/home/AwaitingDialogs.kt:315-410` (`SwapDialog`, `swapDialogMessage`, `swapDirection`)
- Modify: `day_swap_strings.xml`, `home_strings.xml` ×5
- Test: `app/src/test/java/com/coparently/app/domain/custody/DaySwapGroupWaitingTest.kt`; `RequestScreenshots.kt` (card, both modes)

**Interfaces:**
- Consumes: `SwapSides.ofOffer`, `swapDateList`, `AnswerButtons`.
- Produces:
  - `fun DaySwapGroup.waitingOn(uid: String): DaySwapGroup?` — only the swaps `DaySwapInbox.awaitsAnswerFrom(it, uid)`; null when none.
  - `internal fun SwapOfferHeading(sides: SwapSides, offererName: String): String` (@Composable) — `toOfferer` empty → `day_swap_heading_gives` plurals "%1$s offers you %2$d day"; `toOther` empty → `day_swap_heading_asks` plurals "%1$s asks for %2$d of your days"; both → `day_swap_heading_both` "%1$s proposes a day swap". Push (Task 3) and heading say the same thing.
  - `internal fun SwapSidesChips(sides: SwapSides, readerLabel: String, offererName: String, parentNames: ParentNames)` — group "You" (days to the reader) and group "{name}" (days to the offerer), chips as Task 7. Used by the card and the dialog.
  - `SwapDayAnswers(group, uid, parentNames, onAnswer, onBack)` becomes the per-day mode: one row per waiting day, `swapDateList(listOf(day)) → labelOrYou(toParent)` and a `SingleChoiceSegmentedButtonRow` Yes/No (`day_swap_yes`, `day_swap_no`); "Back" text button; "Send answer" (`day_swap_send_answers`) enabled once one day is marked. No longer drawn beside the main buttons.
  - Card and dialog: draw `group.waitingOn(uid) ?: group` (answered days stay in `AnsweredDays`); heading, chips, mini months, note in a quote block (`day_swap_note_from` "%1$s wrote:" above the note, `bodyMedium` italic, start border in `outlineVariant`), deadline line (unchanged `CardClockDetails`); `AnswerButtons` with accept `day_swap_accept_swap` "Accept swap", or `day_swap_accept` when one day waits; text button `day_swap_answer_by_day` "Answer day by day" shown at two waiting days or more, toggling a `rememberSaveable(group.revision)` flag. Item 42's `hidesAnswers`/decline-only apply in both modes.
- [ ] **Step 1: Write the failing test** `DaySwapGroupWaitingTest`: three days, one accepted → `waitingOn` has two; all answered → null; days the reader offered → null.
- [ ] **Step 2: Implement**; replace `swapDialogMessage`/`swapDirection`/`daySwapSentence` with `SwapOfferHeading` (the sender's own card keeps "You offered/asked …" through the existing `day_swap_inbox_*_by_you_days` plurals).
- [ ] **Step 3:** screenshots: card in main mode and in per-day mode; strings ×5; invariants, detekt.
- [ ] **Step 4: Commit** `feat: a swap is answered in one pair of buttons, or day by day on request`

### Task 10: Home calls the reader "you"

**Files:**
- Modify: `presentation/home/HomeScreen.kt:691-705`, `presentation/home/ChildrenTodayLines.kt:30-36`
- Modify: `home_strings.xml`, `child_schedule_strings.xml` ×5
- Test: `HomeScreenshots.kt` (hero with the handover to the reader)

**Interfaces:**
- Produces strings: `home_handover_hero_today_you` (EN "Handover to you today", RU "Передача сегодня, дальше — у вас", UK "Передача сьогодні, далі — у вас", CS "Dnes přebíráte vy", DE "Heute Übergabe an dich"), `home_handover_hero_tomorrow_you` (same shape), plurals `home_handover_hero_in_days_you` (`%1$d` only), `home_child_with_parent_today_you` (EN "%1$s is with you today", RU "Сегодня %1$s с вами", UK "Сьогодні %1$s з вами", CS "Dnes je %1$s s vámi", DE "%1$s ist heute bei dir").
- [ ] **Step 1:** screenshot fixture where `info.toParent` is the reader's slot.
- [ ] **Step 2: Implement:** `if (parentNames.isReader(info.toParent))` pick the `_you` resource (plurals take only the count); same in `ChildrenTodayLines`.
- [ ] **Step 3:** invariants (format arguments per locale), detekt.
- [ ] **Step 4: Commit** `feat: Home says "you" when the handover or a child is the reader's`

### Task 11: Records, baselines and the pull request

**Files:**
- Modify: `CLAUDE.md` (design refresh: a new item 23, "A day swap shows its direction everywhere": `SwapSides`, `SwapMark`, the ⇄ button in the legend row, borrowed tap pages and selects, push fields `datesToYou`/`datesToSender`, the two answer modes; and item 13's `MonthView` line about the grey arrows, now gone)
- Modify: `docs/DEVICE-CHECKLIST.md` (two-phone check: the push's title and body in each direction, on a locked screen, EN and RU) and `tools/manual-test-plan.js` `RULES` if the section number is new (`node --test tools/test/*.test.js`)
- Modify: `.github/regenerate-request` (append a line)

- [ ] **Step 1:** docs; `node tools/check-invariants.js` and `node --test tools/test/*.test.js` → PASS.
- [ ] **Step 2:** push the branch, open a **draft** PR "Day swaps understood the first time" (body: what changed, per spec step; screenshots note; the device check), subscribe to its activity.
- [ ] **Step 3:** touch `.github/regenerate-request`, push; after the bot's baseline commit, push a real follow-up commit so CI runs (the bot's own run is "action_required").
- [ ] **Step 4:** drive CI green per the repo's rules; mark ready for review; merge when green (owner's standing instruction for this work).
