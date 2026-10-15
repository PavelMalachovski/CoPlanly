# Tester feedback, phase 2: who is who, what the co-parent wrote, where the answers are — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A parent can tell a family friend from a co-parent (and the friend gets the read-only calendar the screen promises), reads the creator's description beside a mini month on an event waiting for an answer, and finds from Home what waits on whom and how every past request ended.

**Architecture:** Requests stay derived. Nothing new is stored.
- **One request source.** `RequestsSource` scopes `change_requests`, events, day swaps and the custody document to the family on screen. Pure builders (`RequestScope`, `RequestHistory`, `RequestInbox`) turn that into three sections. The inbox, Home's Requests row and feed, and the calendar banner all read this one source, so they cannot disagree.
- **One small calendar.** `MiniMonth` is built from the month grid's own fill and dot rules. Its inputs are computed off the main thread by the inbox ViewModel.
- **Family friends.** The friend's calendar reuses the professional's list agenda. It reads events through a family-scoped Firestore listener and keeps no Room copy. `acceptCalendarFriendInvitation` copies the parents' first names onto the grant.

**Tech Stack:**
- App: Kotlin 2.1, Jetpack Compose with Material 3 1.4, Hilt, Room (read only, no schema change) and Firestore listeners.
- Tests: JUnit and MockK with coroutines-test and Turbine; Roborazzi for screenshots; Android instrumented e2e on the Firebase emulators.
- Cloud Functions: Node with mocha.

**Spec:** `docs/superpowers/specs/2026-10-14-zuzana-feedback-phase-2-design.md`. The plan argues from the spec, so read both.

## Global Constraints

**Strings and wording**
- Every new or changed user-facing string goes into all five locales in the same commit: `values`, `values-cs`, `values-de`, `values-ru` and `values-uk`.
- English is in sentence case.
- `node tools/check-invariants.js` must pass. It checks that every key is in every locale and that the format arguments agree.
- Text a ViewModel or a pure builder produces is a `UiText`, resolved in composition. Never inject a `Context` into a ViewModel.

**Parents' names and colours**
- Never show the words "Mom" or "Dad". Every parent label goes through `ParentNames`.
- Every parent hue goes through `ParentColors`. Never use a raw `CoPlanlyColors.MomPink` or `DadBlue`, and no literal hex.

**Design system**
- Use the shared primitives: `SectionGroup`, `SectionRow`, `GroupLabel` (heading semantics), `EmptyState`, `ErrorState`, `PillChip`, `InviteCodeText`.
- Icon sizes come from `IconSizes`, spacing from `Spacing`, corners from `MaterialTheme.shapes` or `CoPlanlyCorners`. detekt refuses `RoundedCornerShape` outside `presentation/theme/`.

**Storage and backend**
- No new Room column, collection, push type or rules change.
- There is one new Firestore field, `calendar_friends/{id}.parentNames`. Only the callable writes it, as admin.

**Screenshots**
- Every new or changed component gets a screenshot test.
- A sheet's body is split out of the sheet, because a node capture cannot see a `ModalBottomSheet`'s window.
- Baselines are recorded only by the Regenerate workflow, in Task 14. Until then the `screenshots` CI job reports the new components as having no baseline, and that is expected.

**Code rules**
- KDoc on every public class and function. Code and comments in English.
- minSdk 26: no `LocalDate.ofInstant` or other API 34+ `java.time` calls.
- Read the detekt limits as exactly where a finding starts, not as a ceiling:
  - a function with **6** parameters is flagged; a constructor with **7**;
  - `LongMethod` 60, `LargeClass` 600, `ReturnCount` 3, `ComplexCondition` 4, `NestedBlockDepth` 4;
  - **11** functions in an interface is flagged. `FriendRepository` has 9 today.
- Conventional Commits. End every commit message with the session's `Co-Authored-By` and `Claude-Session` lines. Never put a model name in a commit, a PR, the code or the docs.

**Verifying in a cloud session**
- There is no Android SDK, so `./gradlew` cannot run. A pure-JVM test class can run through the scratchpad harness (`harness3/`, `build-harness3.sh`) once its sources are copied in.
- Everything else is proved by CI on PR #130, branch `claude/zuzana-testing-feedback-ntk5s5`. Push after each task, and read the CI summary comment before starting the next task that builds on it.
- Functions: `cd functions && npm test && npm run lint`.

## Review Focus

Five cases the spec implies but its tests do not name. Each is pinned by a test in the task shown.

1. **A parent in two families.** A request, an event, a swap or a count from the family *not* on screen must appear nowhere: not in the inbox, not in Home's row or feed, and not in the calendar's banner. The calendar banner must show the same number as Home's row. Task 4 (`RequestsSourceTest`) and Task 4 step 6.
2. **One answer, one feed row.** When the co-parent accepts an event I created, Home's feed shows "Accepted Swimming", not that and "Updated Swimming" too. The same holds for an accepted new time. Task 6 (`HomeFeedTest`).
3. **Hidden events stay hidden.** An event still waiting for the co-parent's answer, or one they declined, is hidden on both parents' calendars. It must not appear on a family friend's calendar, nor on a professional's, which share the mapper. Task 9 (`ProfessionalEventsTest`).
4. **An answer made offline.** It must read "Queued" in History, not claim an outcome the co-parent has not received (the CQ-20 rule). Task 2 (`RequestHistoryTest`) and Task 5's screenshot fixture.
5. **A swap decided by an older build.** If its `decidedAt` is blank or unparseable, it still appears in History, dated by `requestedAt`. It is never dropped. Task 2 (`RequestHistoryTest`).

---

### Task 1: The mini month

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/calendar/MiniMonthModel.kt`
- Create: `app/src/main/java/com/coparently/app/presentation/calendar/DayCellColors.kt`
- Create: `app/src/main/java/com/coparently/app/presentation/calendar/components/MiniMonth.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/MonthView.kt`:
  - the colour mapping in `DayCell`, ~438-476;
  - `eventDotColor`, :885;
  - `adjacentScale`, :905;
  - the constants at :945-960, and `HOLIDAY_TINT_ALPHA` at :105.
- Modify: `app/src/main/res/values*/calendar_strings.xml` (5 files)
- Test: `app/src/test/java/com/coparently/app/presentation/calendar/MiniMonthModelTest.kt`
- Test: `app/src/test/java/com/coparently/app/screenshots/RequestScreenshots.kt` (new class, `ScreenshotVariants.TEXT_HEAVY`, same shape as `FeatureScreenshots`)

The spec says `presentation/common/`. The file goes beside `DayCellFills` instead, which it must reuse, so that no `common → calendar` import is introduced. The spec leaves placement to the builder.

**Interfaces:**
- Consumes:
  - `DayCellFills.monthCell(...)`
  - `eventsByDay(events): Map<LocalDate, List<Event>>` (internal, `CalendarScreen.kt:181`)
  - `CalendarVisibility.visible(events)`
  - `RecurrenceExpander.expandAll(masters, from, to)`
  - `CustodyHelper.isWeekend`
  - `rememberToday()`
  - `ParentColors.container/fill/custodyTintAlpha`
- Produces:

```kotlin
// MiniMonthModel.kt — package com.coparently.app.presentation.calendar
enum class MiniMonthMark { NEW_EVENT }
data class MiniMonthDot(val parentOwner: String, val friendParticipates: Boolean)
data class MiniMonthModel(
    val month: YearMonth,
    val custody: Map<LocalDate, String>,      // slot per grid date; absent = no custody
    val dots: Map<LocalDate, MiniMonthDot>,   // the day's first event, as MonthView colours it
    val marks: Map<LocalDate, MiniMonthMark>
) {
    companion object {
        fun build(month: YearMonth, custodyFor: (LocalDate) -> String?, events: List<Event>,
                  marks: Map<LocalDate, MiniMonthMark>): MiniMonthModel
        fun forAwaitingEvent(event: Event, familyEvents: List<Event>,
                             custodyFor: (LocalDate) -> String?): MiniMonthModel
    }
}
object MiniMonthGrid { fun weeks(month: YearMonth): List<List<LocalDate>> }   // Monday first, whole weeks

// DayCellColors.kt — lifted out of MonthView unchanged
@Composable internal fun dayCellBaseColor(base: DayCellBase): Color
@Composable internal fun dayCellOverlayColor(fill: DayCellFill): Color
@Composable internal fun eventDotColor(parentOwner: String, isCurrentMonth: Boolean, friendParticipates: Boolean = false): Color
internal fun adjacentScale(isAdjacentMonth: Boolean): Float
internal const val MAX_EVENT_DOTS = 3
internal val EVENT_DOT_SIZE = 6.dp
internal val EVENT_DOT_HALO = 1.dp
internal const val OUTSIDE_MONTH_DOT_ALPHA = 0.4f
internal const val HOLIDAY_TINT_ALPHA = 0.10f

// components/MiniMonth.kt
@Composable fun MiniMonth(model: MiniMonthModel, parentNames: ParentNames,
                          modifier: Modifier = Modifier, today: LocalDate = rememberToday().value)
```

- [ ] **Step 1: Write the failing tests** in `MiniMonthModelTest`.

```kotlin
@Test fun `a month starting on Friday borrows the days before it back to Monday`() {
    val weeks = MiniMonthGrid.weeks(YearMonth.of(2026, 5))
    assertEquals(5, weeks.size)
    assertEquals(LocalDate.of(2026, 4, 27), weeks.first().first())
    assertEquals(LocalDate.of(2026, 5, 31), weeks.last().last())
    assertTrue(weeks.all { it.size == 7 && it.first().dayOfWeek == DayOfWeek.MONDAY })
}
@Test fun `a February that starts on Monday is exactly four weeks`()   // 2027-02: 4 rows, 02-01 .. 02-28
@Test fun `a leap February keeps its 29th`()                         // 2028-02: 5 rows, 2028-01-31 .. 2028-03-05, contains 2028-02-29
@Test fun `a month can need six weeks`()                             // 2026-08: 6 rows, 2026-07-27 .. 2026-09-06
@Test fun `custody and each day's first event are taken for every cell, borrowed days included`()
    // custodyFor returns "mom" on even days of month, null otherwise; custody map has exactly those
    // grid dates; an event from 2026-04-29 10:00 (borrowed day) to 2026-04-30 09:00 dots both
    // days; on a day with two events the dot is the earlier one's owner
@Test fun `an awaiting event is marked on its start day and draws no dot of its own`()
    // forAwaitingEvent(pending event on 2026-10-21): marks == {2026-10-21: NEW_EVENT}; dots has no 2026-10-21
@Test fun `declined events draw no dot and a weekly event draws one on each occurrence`()
    // October 2026 (grid 09-28 .. 11-01): a DECLINED event on 10-14 → no dot on 10-14; a weekly
    // visible event from 10-05 → dots on exactly 10-05, 10-12, 10-19 and 10-26
```

- [ ] **Step 2: Run them and see them fail.**
  Run: `./gradlew testDebugUnitTest --tests '*MiniMonthModelTest*'`. Expected: compilation failure (`MiniMonthGrid` unresolved).

- [ ] **Step 3: Implement `MiniMonthModel.kt`.**
  - `weeks` runs from the Monday on or before the 1st to the Sunday on or after the last day.
  - `build` keys `custody` and `dots` over every grid date. Dots come from `eventsByDay(events)[date]?.first()`.
  - `forAwaitingEvent` uses the month of `event.startDateTime`. It expands `CalendarVisibility.visible(familyEvents)` over the grid's first to last date with `RecurrenceExpander.expandAll`, and marks the start date `NEW_EVENT`. Visibility is applied to masters, before expansion (`CalendarVisibility`'s KDoc).

- [ ] **Step 4: Run the tests and see them pass.**
  Same command. Expected: 7 tests pass.

- [ ] **Step 5: Lift the cell colours out of `MonthView` into `DayCellColors.kt`.**
  - `DayCell` now calls `dayCellBaseColor(fill.base)` and `dayCellOverlayColor(fill)`.
  - Its handover and proposal colours stay in `MonthView` and use the now-internal `adjacentScale`.
  - Not one pixel may change. The CI `screenshots` job must not list `calendar_month_grid` among the changed images. That is this step's test.

- [ ] **Step 6: Write `MiniMonth`.** Draw the month grid's rules, not a new look.

  **Each cell**
  - Fill: `DayCellFills.monthCell(isWeekend, isCurrentMonth, custody = model.custody[date], previousCustody = null, isPublicHoliday = false)`. Draw its base, then its overlay, as two chained `Modifier.background` calls. `previousCustody = null` means no handover diagonal.
  - Today: a filled `colorScheme.primary` circle with the number in `onPrimary`, as `MonthView` draws it.
  - A `NEW_EVENT` mark: a 2 dp `colorScheme.primary` ring (`border(2.dp, primary, CircleShape)`) around the day number.
  - The day number is in `labelMedium`. A day from a neighbouring month has its number at `onSurface` alpha 0.3, as `MonthView` does.
  - At most one dot under the number, sized `EVENT_DOT_SIZE`. It sits in a ring of the cell's base colour (`EVENT_DOT_HALO`) and takes `eventDotColor(...)`.
  - Height: `heightIn(min = 34.dp)` per cell, never a fixed height.

  **Header**
  - One header row of `DayOfWeek.getDisplayName(TextStyle.NARROW, locale)`, starting on Monday.

  **Legend** (a `FlowRow` under the grid)
  - One entry per parent for each of `"mom"` and `"dad"` that `parentNames.isKnown(slot)`. Each entry is a 12 dp swatch of `ParentColors.container(slot, alpha = ParentColors.custodyTintAlpha)` over `surface`, followed by `parentNames.labelFor(slot)`.
  - When any mark exists, one more entry: a ring swatch and `mini_month_legend_new_event`.

  **Semantics**
  - The grid is one node. Use `Modifier.clearAndSetSemantics { contentDescription = … }` on the grid column.
  - The description is the month (`localizedDate("yMMMM")`), then, for each marked date, `mini_month_marked_day` or `mini_month_marked_day_no_custody`.
  - The date is `localizedDate("EEEEMMMMd")`. Whose day it is comes from the existing `calendar_agenda_custody_day` with `parentNames.labelFor(slot)`. The mark is `mini_month_mark_new_event`.
  - The cells are not focusable and not clickable.

- [ ] **Step 7: Add the strings to `calendar_strings.xml`** in all five locales.

  | Key | English |
  | --- | --- |
  | `mini_month_legend_new_event` | New event |
  | `mini_month_mark_new_event` | new event |
  | `mini_month_marked_day` | %1$s, %2$s: %3$s. |
  | `mini_month_marked_day_no_custody` | %1$s: %2$s. |

- [ ] **Step 8: Add the screenshot test.** In `RequestScreenshots`:

```kotlin
@Test fun miniMonth() = snap("requests_mini_month") {
    MiniMonth(
        model = MiniMonthModel.build(
            ScreenshotFixtures.MONTH, ScreenshotFixtures::custodyFor,
            ScreenshotFixtures.monthEvents.values.flatten(),
            mapOf(ScreenshotFixtures.TODAY.plusDays(8) to MiniMonthMark.NEW_EVENT)
        ),
        parentNames = ScreenshotFixtures.parentNames,
        today = ScreenshotFixtures.TODAY
    )
}
```

- [ ] **Step 9: Commit.**
  `git commit -m "feat(calendar): a mini month drawn with the month grid's own rules"`

---

### Task 2: Who a request is between, and how it ended

**Files:**
- Create: `app/src/main/java/com/coparently/app/domain/changerequests/RequestScope.kt`
- Create: `app/src/main/java/com/coparently/app/domain/changerequests/RequestHistory.kt`
- Modify: `app/src/main/java/com/coparently/app/domain/custody/DaySwapInbox.kt` (add `allGroups`)
- Test: `app/src/test/java/com/coparently/app/domain/changerequests/RequestScopeTest.kt`
- Test: `app/src/test/java/com/coparently/app/domain/changerequests/RequestHistoryTest.kt`
- Test: `app/src/test/java/com/coparently/app/domain/custody/DaySwapInboxTest.kt`

**Interfaces:**
- Consumes:
  - `FamilyKey.orNull(a, b)`
  - `ChangeRequest`, `ChangeRequestStatus`
  - `Event` (`acceptance`, `acceptedBy`, `acceptedAt`, `createdByFirebaseUid`, `familyId`, `syncedToFirestore`)
  - `DayOverride`, `DaySwapGroup`
  - `CustodyDecision(outcome, by, at, proposalAt, note)`
  - All stored times are naive local ISO (`ISO_LOCAL_DATE_TIME`). Parse them with `LocalDateTime.parse`.
- Produces:

```kotlin
data class RequestScope(val myUid: String, val coParentUid: String, val familyId: String, val soleFamily: Boolean) {
    fun covers(request: ChangeRequest): Boolean   // between me and the co-parent, either direction
    fun covers(event: Event): Boolean             // creator is one of us AND (familyId == familyId, or blank while soleFamily)
    fun isPair(uid: String?): Boolean             // uid is myUid or coParentUid
    companion object { fun of(myUid: String?, coParentUid: String?, coParents: Set<String>): RequestScope? }
}

enum class RequestOutcome { ACCEPTED, DECLINED, WITHDRAWN }
sealed interface RequestSubject {
    data class NewTime(val eventId: String, val eventTitle: String, val proposedStart: LocalDateTime) : RequestSubject
    data class DaySwap(val firstDate: LocalDate, val dayCount: Int) : RequestSubject
    data class NewEvent(val eventId: String, val eventTitle: String, val start: LocalDateTime) : RequestSubject
    data object CustodySchedule : RequestSubject
}
data class HistoryEntry(
    val key: String, val subject: RequestSubject, val outcome: RequestOutcome,
    val askedBy: String, val decidedBy: String, val decidedAt: LocalDateTime, val queued: Boolean = false
)
object RequestHistory {
    /** Newest first. Keeps only what [scope] covers. */
    fun build(scope: RequestScope, changeRequests: List<ChangeRequest>, events: List<Event>,
              overrides: Map<String, DayOverride>, decision: CustodyDecision?): List<HistoryEntry>
}
// DaySwapInbox
fun allGroups(overrides: Map<String, DayOverride>): List<DaySwapGroup>   // groups() without the today bound
```

**How `RequestScope.of` decides**
- It answers null when `myUid` or `coParentUid` is blank, or when `FamilyKey.orNull` does.
- `soleFamily = coParents.size <= 1`.

**What `RequestHistory.build` emits**

| Source | Included when | Key | Outcome | `askedBy`, `decidedBy` | `decidedAt` | `queued` |
| --- | --- | --- | --- | --- | --- | --- |
| Change request | covered by the scope, status not PENDING | `cr_<id>` | ACCEPTED, DECLINED, or WITHDRAWN for CANCELLED | `requestedBy`; `requestedTo` for an answer, `requestedBy` for a withdrawal | `respondedAt ?: createdAt` | `!syncedToFirestore` |
| Swap group | `allGroups`; no day still pending; requester `isPair` | `swap_<group.key>` | the first day's status | `requestedBy`; `decidedBy`, or the other party when it is null | `decidedAt` parsed, else `requestedAt` parsed; skip the group only when neither parses | false |
| Event | covered by the scope; acceptance ACCEPTED or DECLINED; `acceptedAt != null` | `ev_<id>` | the acceptance | `createdByFirebaseUid`; `acceptedBy` or the other party | `acceptedAt` | `!syncedToFirestore && acceptedBy == myUid` |
| Custody decision | non-null; `isPair(by)`; `at` parses | `custody_<proposalAt>` | the decision's outcome | the party who is not `by`; `by` | `at` | false |

Sort by `decidedAt` descending, then by `key`.

- [ ] **Step 1: Write the failing tests.**

  `RequestScopeTest`:
  - `no scope without both parents`: `of("", "u2", …)`, `of("u1", null, …)` and `of("u1", "u1", …)` are all null.
  - `a request is covered in either direction and only between the two`: u1→u2 and u2→u1 are covered; u1→u3 and u3→u1 are not.
  - `an event of this family is covered and one of the other family is not`: familyId `u1__u2` is covered; `u1__u3` is not.
  - `an unstamped event is covered only while there is one co-parent`: `soleFamily` true → covered; false → not.
  - `an event created outside the pair is never covered`: creator `u9` with `familyId = u1__u2` → false.

  `RequestHistoryTest` (scope `u1`/`u2`):
  - `my request the co-parent accepted is answered by them at respondedAt`: one entry; ACCEPTED; `askedBy u1`; `decidedBy u2`; `decidedAt == respondedAt`; `NewTime(eventId, title, proposedStart)`.
  - `a withdrawn request without respondedAt is dated by its creation`: WITHDRAWN; `decidedBy == requestedBy`; `decidedAt == createdAt`.
  - `a swap offered as one group is one entry with its first date and day count, past days included`: a group of three days on 2026-09-01..03, decided, with today 2026-10-14 → `DaySwap(2026-09-01, 3)`.
  - `a swap group with a day still pending is not history`.
  - `a swap decided by an older build without decidedAt is dated by requestedAt` (Review Focus 5). Also cover `decidedAt = "garbage"`.
  - `an event the co-parent declined is history and a pending one is not`: DECLINED, dated by `acceptedAt`, subject `NewEvent`.
  - `the custody decision is one entry, asked by the other parent`: `by = u2` → `askedBy u1`.
  - `another family's request and event are left out`: requests u1→u3, and an event with familyId `u1__u3` → empty.
  - `entries are newest first`.
  - `an answer this phone has not uploaded is marked queued` (Review Focus 4): a DECLINED request with `syncedToFirestore = false` → `queued`.

  `DaySwapInboxTest`:
  - `allGroups keeps past days and groups them like groups()`.

- [ ] **Step 2: Run them and see them fail.**
  Run: `./gradlew testDebugUnitTest --tests '*RequestScopeTest*' --tests '*RequestHistoryTest*' --tests '*DaySwapInboxTest*'`. Expected: compilation failure.

- [ ] **Step 3: Implement** `RequestScope.kt`, `RequestHistory.kt` and `DaySwapInbox.allGroups`.
  `allGroups` shares the grouping with `groups()`. Extract a private `group(swaps)` that both call, so the two cannot drift.

- [ ] **Step 4: Run the tests and see them pass.** Same command.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(requests): a request's family scope and a history derived from what both phones hold"`

---

### Task 3: The inbox's three sections

**Files:**
- Create: `app/src/main/java/com/coparently/app/domain/changerequests/RequestInbox.kt`
- Test: `app/src/test/java/com/coparently/app/domain/changerequests/RequestInboxTest.kt`

**Interfaces:**
- Consumes:
  - `RequestScope` and `RequestHistory.build` (Task 2)
  - `AwaitingAnswers.swapOffers` and `eventAwaitsAnswerFrom`
  - `DaySwapInbox.groups`
  - `SharedCustody.proposal` and `lastDecision`
- Produces:

```kotlin
data class Waiting(
    val proposal: CustodyProposal? = null,
    val events: List<Event> = emptyList(),
    val swaps: List<DaySwapGroup> = emptyList(),
    val changeRequests: List<ChangeRequest> = emptyList()
) {
    val count: Int                  // proposal (0/1) + events + swaps + changeRequests
    val countWithoutProposal: Int   // what the calendar banner counts; the proposal has its own banner
    val isEmpty: Boolean
}
data class RequestInbox(
    val waitingOnMe: Waiting = Waiting(),
    val waitingOnThem: Waiting = Waiting(),
    val history: List<HistoryEntry> = emptyList()
) {
    val isEmpty: Boolean
    companion object {
        fun build(scope: RequestScope, changeRequests: List<ChangeRequest>, events: List<Event>,
                  overrides: Map<String, DayOverride>, shared: SharedCustody?, today: LocalDate): RequestInbox
    }
}
```

The order inside each list follows the inbox as it is today.

| List | Waiting on me | Waiting on them |
| --- | --- | --- |
| Proposal | `shared.proposal` when `proposedBy != myUid` | `shared.proposal` when `proposedBy == myUid` |
| Events, by start | covered and `AwaitingAnswers.eventAwaitsAnswerFrom(it, myUid)` | covered, pending, `createdByFirebaseUid == myUid` |
| Swaps | `AwaitingAnswers.swapOffers(overrides, myUid, today)` | `DaySwapInbox.groups(overrides, today)` whose first day `requestedBy == myUid` and with any day pending |
| Change requests, by `createdAt` | covered, PENDING, `requestedTo == myUid` | covered, PENDING, `requestedBy == myUid` |

History is `RequestHistory.build(scope, changeRequests, events, overrides, shared?.lastDecision)`.

- [ ] **Step 1: Write the failing tests** in `RequestInboxTest`.
  - `what waits on me is the co-parent's proposal, their pending events, swaps offered to me and their pending requests`.
  - `what waits on them is my proposal, my pending events, my pending swap offers and my pending requests`.
  - `an answered item leaves both waiting sections and appears in history`.
  - `a swap I offered never waits on me`.
  - `count includes the proposal and countWithoutProposal does not`: with a proposal from u2, one event and one swap → 3 and 2.
  - `an inbox is empty only when all three sections are`.

- [ ] **Step 2: Run them and see them fail.**
  `./gradlew testDebugUnitTest --tests '*RequestInboxTest*'`. Expected: compilation failure.

- [ ] **Step 3: Implement** `RequestInbox.kt`.

- [ ] **Step 4: Run the tests and see them pass.**

- [ ] **Step 5: Commit.**
  `git commit -m "feat(requests): the inbox's three sections as one pure model"`

---

### Task 4: One source for the family on screen's requests

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestsSource.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/ChangeRequestViewModel.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/CalendarScreen.kt:572-583`
- Modify: `app/src/main/java/com/coparently/app/domain/changerequests/AwaitingAnswers.kt` (delete `count`)
- Test: `app/src/test/java/com/coparently/app/presentation/changerequests/RequestsSourceTest.kt`
- Test: `app/src/test/java/com/coparently/app/presentation/changerequests/ChangeRequestViewModelTest.kt`
- Test: `app/src/test/java/com/coparently/app/domain/changerequests/AwaitingAnswersTest.kt`

**Interfaces:**
- Consumes:
  - `ChatPartnerSource.resolve(pairing, projected)`
  - `UserRepository.observeSignedInUser()` and `User.projectedCoParentUid()` (`presentation/common/CoParentOnScreen.kt`)
  - `User.partnerIds`
  - `FamilyAudience.coParents(myUid, livePartners, selectedPartner)`
  - Tasks 1–3
- Produces:

```kotlin
sealed interface RequestsState {
    data object Loading : RequestsState
    data object NoCoParent : RequestsState
    data class Ready(
        val scope: RequestScope,
        val inbox: RequestInbox,
        val changeRequests: List<ChangeRequest>,   // the scope's, every status
        val events: List<Event>                    // the scope's, every acceptance
    ) : RequestsState
}
class RequestsSource @Inject constructor(
    changeRequestRepository: ChangeRequestRepository, eventRepository: EventRepository,
    custodyModelRepository: CustodyModelRepository, userRepository: UserRepository,
    pairingRepository: PairingRepository
) { fun observe(): Flow<RequestsState> }

// ChangeRequestViewModel — constructor gains `requestsSource: RequestsSource` (8th; the class already
// carries @Suppress("LongParameterList"))
val requests: StateFlow<RequestsState>                     // initial Loading
val miniMonths: StateFlow<Map<String, MiniMonthModel>>     // event id → model, for every waiting event
```

**What `observe()` does**
1. Combine `observeCurrentUserId()`, `observePairingState()` and `observeSignedInUser()`, then apply `distinctUntilChanged`.
2. Resolve the co-parent: Resolving → `Loading`, None → `NoCoParent`, Linked → build a `RequestScope`. A blank uid is `Loading`.
3. `flatMapLatest` into a combine of `getAllChangeRequests()`, `getAllEvents()`, `observeDayOverrides()` and `observeShared()`, which yields `Ready`.
4. Read `LocalDate.now()` on every emission, as the existing swap flows do. They outlive midnight.

**Changes to `ChangeRequestViewModel`**
- `miniMonths` combines `requests`, `custodyModelRepository.getActiveModel()` and `observeDayOverrides()`.
- For each event in `waitingOnMe.events + waitingOnThem.events` it calls `MiniMonthModel.forAwaitingEvent(event, ready.events, CustodyResolver.resolver(model, overrides, legacy = { null }))`.
- It runs with `.flowOn(Dispatchers.Default)`.
- **Remove** `changeRequests`, `hasLoaded`, `pendingIncomingCount`, `daySwaps`, `daySwapGroups` and `eventsAwaitingMe`. After step 6 nothing reads them.
- `pendingProposal*`, the answer functions and `errorMessage` stay. Home's dialogs and the calendar read them.

- [ ] **Step 1: Write the failing tests** in `RequestsSourceTest`.
  Use mocks of the five repositories. The signed-in row is `User(id = "u1", partnerId = <shown>, partnerIds = …)`.
  - `loading until the pairing state answers`.
  - `no co-parent when not paired`.
  - `the family on screen decides`: paired with u2 (server), projected u3, `partnerIds [u2, u3]` → `scope.coParentUid == "u3"` and `soleFamily == false`.
  - `another family's request, event and count are in neither section` (Review Focus 1):
    - setup: u2's pending request to u1, plus a pending event with familyId `u1__u2`, while u3 is on screen;
    - expect `inbox.waitingOnMe.count == 0`;
    - with u2 on screen, expect 2.
  - `history and waiting come from the same lists`.

- [ ] **Step 2: Update `ChangeRequestViewModelTest`.**
  - Build the ViewModel with `requestsSource = RequestsSource(changeRequestRepository, eventRepository, custodyModelRepository, userRepository, pairingRepository)`, stubbing `observeCurrentUserId`, `getAllUsers` and `observePairingState` (Paired u2).
  - Replace `an empty inbox is told apart from one that has not loaded` with `the inbox is Loading until its sources answer, then Ready even when empty`. Before `requests.emit(emptyList())` it is `RequestsState.Loading`; after, it is a `Ready` whose inbox `isEmpty`.
  - Add `a waiting event gets a mini month of its own month`: an event pending from u2 on 2026-10-21 → `miniMonths.value["e1"]?.month == YearMonth.of(2026, 10)`.

- [ ] **Step 3: Run the tests and see them fail.**
  `./gradlew testDebugUnitTest --tests '*RequestsSourceTest*' --tests '*ChangeRequestViewModelTest*'`.

- [ ] **Step 4: Implement `RequestsSource`** and the ViewModel changes.

- [ ] **Step 5: Run the tests and see them pass.**

- [ ] **Step 6: Give the calendar banner Home's count** (Review Focus 1).
  - In `CalendarScreen`, replace `pendingChangeRequests`, `inboxUserId` and `eventsAwaitingMe`, and their `AwaitingAnswers.count(...)`, with `(requests as? RequestsState.Ready)?.inbox?.waitingOnMe?.countWithoutProposal ?: 0`, read from `changeRequestViewModel.requests`.
  - Update the comment there. Home's row is now "Requests".
  - Delete `AwaitingAnswers.count` and the assertions on it in `AwaitingAnswersTest` (lines 55, 95 and 102). Keep the `swapOffers` and `eventCount` assertions.

- [ ] **Step 7: Run** `./gradlew testDebugUnitTest --tests '*AwaitingAnswersTest*' --tests '*ChangeRequest*'` and `./gradlew detekt`. Expected: they pass.

- [ ] **Step 8: Commit.**
  `git commit -m "refactor(requests): the inbox and the calendar banner read the family on screen's requests"`

---

### Task 5: The inbox in sections, with the awaiting card and History

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/changerequests/InboxItems.kt`
- Create: `app/src/main/java/com/coparently/app/presentation/changerequests/AwaitingEventCard.kt` (moves the private card out of the screen)
- Create: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestHistoryGroup.kt`
- Create: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestText.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/ChangeRequestsScreen.kt`. Rewrite the body. `DaySwapCard`, `CustodyProposalCard` and `ChangeRequestCard` stay.
- Modify: `app/src/main/java/com/coparently/app/presentation/common/ParentNames.kt` (add `slotForUid`)
- Modify: `app/src/main/java/com/coparently/app/presentation/navigation/NavGraph.kt`: `Screen.ChangeRequests` at :1779 and its `pane` at :421.
- Modify these string files, five locales each: `change_requests_strings.xml`, `event_acceptance_strings.xml`, `custody_strings.xml`.
- Test: `app/src/test/java/com/coparently/app/presentation/changerequests/InboxItemsTest.kt`
- Test: `app/src/test/java/com/coparently/app/presentation/common/ParentNamesTest.kt`
- Test: `RequestScreenshots` (3 more snaps)

**Interfaces:**
- Consumes: Tasks 1–4. `ChangeRequestHighlight.forEvent(requests, eventId)`, `shortTime()`, `localizedDate(skeleton)`, `dateWithTime(skeleton)`, `GroupLabel`, `EmptyState`, `PillChip`.
- Produces:

```kotlin
enum class InboxSection(val routeValue: String) {
    WAITING_ON_ME("waiting"), WAITING_ON_THEM("sent"), HISTORY("history");
    companion object { fun fromRoute(value: String?): InboxSection? }
}
sealed interface InboxItem {
    val key: String
    data class Header(val section: InboxSection) : InboxItem              // "h_<routeValue>"
    data class Proposal(val proposal: CustodyProposal, val mine: Boolean) : InboxItem   // "p_mine"/"p_theirs"
    data class AwaitingEvent(val event: Event, val mine: Boolean) : InboxItem          // "ev_<id>"
    data class Swap(val group: DaySwapGroup) : InboxItem                  // "sw_<group.key>"
    data class Request(val request: ChangeRequest) : InboxItem            // "cr_<id>"
    data class History(val entries: List<HistoryEntry>) : InboxItem       // "history" — one group
}
object InboxItems {
    fun of(inbox: RequestInbox): List<InboxItem>
    fun indexOf(items: List<InboxItem>, section: InboxSection): Int        // the header's index, or -1
    fun indexOfRequest(items: List<InboxItem>, requestId: String): Int     // pending card, or the History item
}
data class AwaitingEventActions(val onAccept: () -> Unit, val onDecline: () -> Unit)
@Composable internal fun AwaitingEventCard(event: Event, miniMonth: MiniMonthModel?, parentNames: ParentNames,
                                           actions: AwaitingEventActions?, modifier: Modifier = Modifier)
@Composable internal fun RequestHistoryGroup(entries: List<HistoryEntry>, parentNames: ParentNames, myUid: String,
                                             highlightedKey: String? = null, modifier: Modifier = Modifier)
@Composable internal fun swapDatesText(firstDate: LocalDate, dayCount: Int): String
@Composable internal fun subjectTitle(subject: RequestSubject): String
fun ChangeRequestsScreen(onBack: () -> Unit, onOpenEvent: (String) -> Unit, linkedEventId: String? = null,
                         openSection: InboxSection? = null, viewModel: ChangeRequestViewModel = hiltViewModel())
// NavGraph
data object ChangeRequests : Screen("change_requests?eventId={eventId}&section={section}") {
    const val ARG_EVENT_ID = "eventId"; const val ARG_SECTION = "section"
    fun createRoute(eventId: String? = null, section: InboxSection? = null): String
}
// ParentNames
fun slotForUid(uid: String?): String?
```

**The order of `InboxItems.of`**
1. When `waitingOnMe` is not empty, `Header(WAITING_ON_ME)`, then:
   - the co-parent's `Proposal(mine = false)`;
   - their `AwaitingEvent`s;
   - `Swap`s offered to me;
   - their `Request`s.
2. The same for `waitingOnThem`, with `mine = true`.
3. When `history` is not empty, `Header(HISTORY)` and one `History(entries)`.

This flat list is the single record of what the `LazyColumn` draws. Indexing it replaces `ChangeRequestHighlight.indexInInbox`, which miscounted by one when a proposal was pending. Delete `indexInInbox` and its five tests (`ChangeRequestHighlightTest` :86-:126). Keep `forEvent` and its tests.

- [ ] **Step 1: Write the failing tests.**

  `InboxItemsTest`:
  - `sections come in order with their items, and an empty section has no header`.
  - `a request below a pending proposal is found at its exact index`: the case the old count got wrong.
  - `an answered request is found at the History item`: the chat-card highlight still lands.
  - `the history header can be scrolled to`.
  - `every key is unique`: one item of each kind.
  - `fromRoute reads the three values and nothing else`.

  `ParentNamesTest`:
  - `slotForUid answers each parent's slot and null for a stranger`.

- [ ] **Step 2: Run them and see them fail.**
  `./gradlew testDebugUnitTest --tests '*InboxItemsTest*' --tests '*ParentNamesTest*'`.

- [ ] **Step 3: Implement `InboxItems.kt`** and `ParentNames.slotForUid`. Run the tests and see them pass.

- [ ] **Step 4: Write `AwaitingEventCard`** (spec §5.1), top to bottom.
  1. The title, in `titleMedium`.
  2. When it happens:
     - start and end on one day → `event_acceptance_when`: the date `localizedDate("MMMEd")` and both times through `shortTime()`;
     - no end → `event_acceptance_when_start`;
     - an end on another day → `event_acceptance_when_span`, both sides through `dateWithTime("MMMEd")`.
  3. Who it is from:
     - when `actions != null`, `event_acceptance_offered_to_you` with `labelForUid(createdByFirebaseUid)`;
     - when null, `event_acceptance_offered_by_you` with `labelFor(event.parentOwner)`.
  4. The creator's words, only when `description` is not blank.
     - A 28 dp disc filled with `ParentColors.container(slot, alpha = ParentColors.custodyTintAlpha)`, with the creator's initial in `ParentColors.text(slot)`. `slot` is `parentNames.slotForUid(createdByFirebaseUid)`; with no slot, use `surfaceVariant` and `onSurfaceVariant`.
     - Beside it a bubble: a `Surface(shape = MaterialTheme.shapes.medium, color = surfaceContainerHighest)`. In it, the creator's name in `labelMedium` with `ParentColors.text(slot)`, then the description in `bodyMedium` with `maxLines = 6` and `TextOverflow.Ellipsis`.
     - The disc is decorative (`contentDescription = null`).
  5. `MiniMonth(miniMonth)` when it is not null.
  6. `event_acceptance_hidden_note`.
  7. The actions:
     - with actions, Accept (`Button`) and Decline (`TextButton`), as today;
     - without, `event_acceptance_waiting` in `labelMedium`.

  This follows spec §6.2: there is no new Withdraw. Spec §5.1 was corrected to match in the commit that added this plan.

- [ ] **Step 5: Write `RequestHistoryGroup`** (spec §6.2). It is one `SectionGroup`, with a `Divider()` between rows. The rows are not clickable. Each row has:
  - **Title line:** `subjectTitle(subject)` on the left, with a neutral `PillChip` on the right.
    - The chip's colours are `container = surfaceVariant` and `contentColor = onSurfaceVariant`.
    - Its icon is `Icons.Default.Check` for ACCEPTED, `Icons.Default.Close` for DECLINED and `Icons.AutoMirrored.Filled.Undo` for WITHDRAWN.
    - Its label is `change_request_status_accepted`, `_declined` or `_cancelled`.
    - A `queued` entry instead shows the existing Queued chip (`change_request_status_queued`, `Icons.Default.Schedule`, `change_request_status_queued_desc`).
  - **Proposed value**, only for `NewTime` and `NewEvent`: `dateWithTime("MMMEd")` of the proposed or start time, in `bodyMedium`.
  - **Who and when:** `request_history_when` with (the who-text, `dateWithTime("MMMd")` of `decidedAt`), in `bodySmall` and `onSurfaceVariant`. The who-text is one of:
    - `request_history_withdrawn_by_you` when `decidedBy == myUid`;
    - `request_history_withdrawn_by` with the name, for another's withdrawal;
    - `request_history_yours_answered` when `askedBy == myUid`;
    - `request_history_theirs_answered` otherwise.
  - A row whose `"cr_" + id` key matches `highlightedKey` gets `secondaryContainer` behind it, as `ChangeRequestCard` highlights a card.

  The time sits in its own " · " part, not inside the sentence. In Czech, Russian and Ukrainian a date inside "answered %s" would need a preposition and a case. This is the "wording polish" the spec leaves to the builder.

  `subjectTitle` maps the four subjects:
  - `NewTime` → `request_history_new_time` with the title;
  - `DaySwap` → `request_history_day_swap` with `swapDatesText`;
  - `NewEvent` → the title as written;
  - `CustodySchedule` → `request_history_custody`.

  `swapDatesText` gives `localizedDate("MMMd")` for one day, and otherwise `request_swap_first_and_more` with `quantity = dayCount - 1`.

- [ ] **Step 6: Rewrite the `ChangeRequestsScreen` body.**
  - Read `viewModel.requests` and `viewModel.miniMonths`. Before `Ready` the `LazyColumn` is not drawn.
  - When `Ready.inbox.isEmpty`, show `EmptyState(icon = Icons.Default.SwapHoriz, title = change_request_empty_title, description = change_request_empty_hint, modifier = Modifier.padding(padding))` in place of the bespoke column.
  - Otherwise draw `items(InboxItems.of(inbox), key = { it.key })`. Each item kind maps to:

    | Item | Drawn as |
    | --- | --- |
    | `Header` | `GroupLabel`: `request_section_waiting_on_you`; `request_section_waiting_on_them` with `parentNames.labelForUid(scope.coParentUid)`; or `request_section_history` |
    | `Proposal` | `CustodyProposalCard` |
    | `AwaitingEvent` | `AwaitingEventCard`, with actions null when `mine` |
    | `Swap` | `DaySwapCard` |
    | `Request` | `ChangeRequestCard` |
    | `History` | `RequestHistoryGroup` |

  - `CustodyProposalCard` takes `onAccept: (() -> Unit)?` and `onDecline: (() -> Unit)?`. When both are null, it shows `custody_proposal_inbox_body_yours` and `event_acceptance_waiting`, with no diff, citation or buttons.
  - `DaySwapCard` takes the current uid from `scope.myUid`.
  - **Scrolling.** One `LaunchedEffect(linkedEventId, openSection, ready != null)` handles both arrivals. It runs once the state is `Ready`:
    - from a chat card: `forEvent(ready.changeRequests, linkedEventId)` and then `indexOfRequest`, or the `change_request_link_missing` snackbar as today;
    - for a section: `indexOf(items, openSection)`.
  - The title stays `change_request_inbox_title`, whose value becomes "Requests".

- [ ] **Step 7: Add the `section` route argument.**
  - It is a `NavType.StringType` that is nullable with default null. Read it with `InboxSection.fromRoute(...)` and pass it to `openSection`.
  - `createRoute(eventId, section)` → `"change_requests?eventId=${eventId ?: "null"}&section=${section?.routeValue ?: "null"}"`.

- [ ] **Step 8: Update the strings** in all five locales.

  Values that change:
  - `change_request_inbox_title`: Requests
  - `change_request_empty_title`: No requests yet
  - `change_request_status_cancelled`: Withdrawn

  Keys that are deleted, because they lose their last reader: `change_request_incoming`, `change_request_outgoing`, `day_swap_inbox_section`, `event_acceptance_section`.

  New keys:

  | Key | English |
  | --- | --- |
  | `request_section_waiting_on_you` | Waiting for your answer |
  | `request_section_waiting_on_them` | Waiting for %1$s |
  | `request_section_history` | History |
  | `request_history_new_time` | New time for %1$s |
  | `request_history_day_swap` | Day swap: %1$s |
  | `request_history_custody` | New custody schedule |
  | `request_history_yours_answered` | Your request · %1$s answered |
  | `request_history_theirs_answered` | %1$s's request · you answered |
  | `request_history_withdrawn_by_you` | You withdrew it |
  | `request_history_withdrawn_by` | %1$s withdrew it |
  | `request_history_when` | %1$s · %2$s |
  | `request_swap_first_and_more` (plurals) | one: %1$s and %2$d more day; other: %1$s and %2$d more days |
  | `event_acceptance_when` | %1$s · %2$s–%3$s |
  | `event_acceptance_when_start` | %1$s · %2$s |
  | `event_acceptance_when_span` | %1$s – %2$s |
  | `custody_proposal_inbox_body_yours` | You proposed a new custody schedule |

- [ ] **Step 9: Add three screenshot tests** to `RequestScreenshots`.
  - `requests_awaiting_event`: the recipient's view. Pavel (`CO_PARENT_UID`, slot `dad`) created a PENDING event on Olya's days (`parentOwner = "mom"`) on `TODAY.plusDays(8)` from 16:00 to 17:30. Its description is three lines, long enough to reach the 6-line ellipsis at 2.0×. The mini month comes from `MiniMonthModel.forAwaitingEvent`.
  - `requests_awaiting_event_plain`: the creator's view (`actions = null`). Olya (`MY_UID`) created the event on Pavel's days (`parentOwner = "dad"`), with a blank description.
  - `requests_history`: one entry of each subject and outcome, including a queued DECLINED one (Review Focus 4).
  - Every fixture date is pinned to May 2026. Never use `LocalDate.now()`.

- [ ] **Step 10: Run the checks.**
  `./gradlew testDebugUnitTest detekt` and `node tools/check-invariants.js`. Expected: they pass. In CI, the `screenshots` job lists the four `requests_*` components as new.

- [ ] **Step 11: Commit.**
  `git commit -m "feat(requests): the inbox in three sections, the creator's words and a mini month on the card"`

---

### Task 6: Home: the Requests row and the co-parent's answers

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/home/RequestsRow.kt`
- Create: `app/src/main/java/com/coparently/app/presentation/home/HomeFeed.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/home/HomeViewModel.kt`:
  - in the constructor, `changeRequestRepository` becomes `requestsSource`, which keeps it at 7 parameters;
  - `ActivityKind` and `ActivityItem`;
  - `recentChanges` and `awaitingRequestCount`;
  - `Dashboard`.
- Modify: `app/src/main/java/com/coparently/app/presentation/home/HomeScreen.kt`: the awaiting row at :459-480, `ActivityGroup` at :989, a new parameter, and the removal of `hasPendingProposal`.
- Modify: `app/src/main/java/com/coparently/app/presentation/common/UiText.kt` (add `Weekday`)
- Modify: `app/src/main/java/com/coparently/app/presentation/navigation/NavGraph.kt` (Home's call site at :289)
- Modify: `app/src/main/res/values*/home_strings.xml`
- Test: `app/src/test/java/com/coparently/app/presentation/home/RequestsRowTextTest.kt`
- Test: `app/src/test/java/com/coparently/app/presentation/home/HomeFeedTest.kt`
- Test: `app/src/test/java/com/coparently/app/presentation/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `RequestsSource` and `RequestsState` (Task 4), `HistoryEntry` (Task 2), `InboxSection` (Task 5), `swapDatesText` (Task 5).
- Produces:

```kotlin
data class RequestsRowState(val waitingOnMe: Int = 0, val waitingOnThem: Int = 0, val latestAnswer: HistoryEntry? = null)
object RequestsRowText {
    /** The co-parent's newest ACCEPTED/DECLINED answer to one of my requests, decided within 7 days of [now]. */
    fun latestAnswer(history: List<HistoryEntry>, scope: RequestScope, now: LocalDateTime): HistoryEntry?
    fun parts(state: RequestsRowState, coParentName: String, today: LocalDate): List<UiText>
    /** Today, Yesterday, a weekday for 2–6 days ago, else the date. */
    fun relativeDay(date: LocalDate, today: LocalDate): UiText
}
object HomeFeed {
    fun items(events: List<Event>, changeRequests: List<ChangeRequest>, history: List<HistoryEntry>,
              partnerId: String, maxItems: Int): List<ActivityItem>
}
enum class ActivityKind { EVENT_CREATED, EVENT_UPDATED, PICKUP_CONFIRMED, CHANGE_REQUESTED, ANSWER }
data class ActivityItem(/* existing fields */, val answer: HistoryEntry? = null)   // non-null exactly for ANSWER
// UiText — resolved with getDisplayName(TextStyle.FULL, locale), first letter title-cased
data class Weekday(val day: DayOfWeek) : UiText
// HomeUiState.Dashboard gains
val requests: RequestsRowState = RequestsRowState()
// HomeScreen gains
onOpenRequestHistory: () -> Unit
```

**What `RequestsRowText.parts` returns,** in order:
1. `UiText.Plural(R.plurals.home_requests_waiting_on_you, waitingOnMe)`, when it is above 0.
2. `UiText.Plural(R.plurals.home_requests_waiting_on_them, waitingOnThem, listOf(waitingOnThem, coParentName))`, when it is above 0.
3. `UiText.Res(home_requests_answer_accepted or _declined, listOf(relativeDay(decidedAt.toLocalDate(), today), coParentName))`, when there is a latest answer.
4. With none of these: `listOf(UiText.Res(R.string.home_requests_none))`.

The relative day leads, as a label ("Yesterday: Pavel accepted your request"). This sidesteps the preposition and case a weekday would need inside a Czech, Russian or Ukrainian sentence.

**What `HomeFeed.items` does**
- It moves the existing `recentChanges` rules out of the ViewModel unchanged: events with `lastModifiedBy == partnerId` that are not private, via `toActivityItem`, and change requests with `requestedBy == partnerId`.
- It adds one `ANSWER` item per history entry with `decidedBy == partnerId`, `askedBy != partnerId`, an outcome other than WITHDRAWN and a subject other than `CustodySchedule`. Its timestamp is `decidedAt`, its `eventId` is the subject's (or `""`) and its id is `"ans_<key>"`.
- It drops an event item when an ANSWER names the same `eventId` and `|decidedAt − event.updatedAt| <= ANSWER_EDIT_WINDOW` (1 minute). Accepting a change request rewrites the event a moment before `respondedAt`; accepting an event sets `updatedAt = acceptedAt`.
- It sorts newest first and takes `maxItems`.
- It owns `NEAR_THRESHOLD` and `toActivityItem`, both moved from `HomeViewModel`.

**Changes to `HomeViewModel`**
- `private val requests = requestsSource.observe().stateIn(viewModelScope, WhileSubscribed(STOP_TIMEOUT_MS), RequestsState.Loading)`. There is one upstream.
- `recentChanges` is `HomeFeed.items(...)` over `getAllEvents()` plus `Ready.changeRequests` and `Ready.inbox.history`.
- `Dashboard.requests` is built from `Ready`: `waitingOnMe.count`, `waitingOnThem.count` and `RequestsRowText.latestAnswer(...)`, or the default while not `Ready`.
- `awaitingRequestCount` stays, for `AwaitingDialogs`. It becomes `waitingOnMe.changeRequests.size + waitingOnMe.events.size`, which is now scoped. `awaitingSwaps` is unchanged.

**Changes to `HomeScreen`**
- The row replaces "Awaiting your answer" in the same place. It is always present on the dashboard.
- `SectionRow(title = home_requests_title, icon = Icons.Default.SwapHoriz, supporting = <the parts joined by " · ">, onClick = onOpenChangeRequests)`.
- Resolve the parts with `parts.map { it.asString() }.joinToString(" · ")`. `asString()` is composable, so it cannot be called inside `joinToString`'s lambda.
- The trailing slot shows the count in `titleSmallEmphasized` and `primary` when `waitingOnMe > 0`; otherwise `HomeChevron()`.
- The co-parent's name is `parentNames.labelForUid(state.partner?.id)`.
- `Dashboard` loses `hasPendingProposal`, because the proposal is in `waitingOnMe`.

**Changes to `ActivityGroup`**
- An ANSWER row's marker is a 24 dp disc in `surfaceVariant` holding `Icons.Default.Check` or `Icons.Default.Close` in `onSurfaceVariant` at `IconSizes.Small`.
- Its label:

  | Subject | Accepted | Declined |
  | --- | --- | --- |
  | `NewTime` | `home_activity_accepted_new_time` | `home_activity_declined_new_time` |
  | `DaySwap` | `home_activity_accepted_swap`, with `swapDatesText` | `home_activity_declined_swap`, with `swapDatesText` |
  | `NewEvent` | `home_activity_accepted_event` | `home_activity_declined_event` |

- A tap on an ANSWER row opens `onOpenRequestHistory`.
- A last row, `home_requests_all` with a chevron and `onClick = onOpenRequestHistory`, follows whenever the group has items.

**Changes to `NavGraph`**
- `onOpenRequestHistory = { navController.navigate(Screen.ChangeRequests.createRoute(section = InboxSection.HISTORY)) }`.

- [ ] **Step 1: Write the failing tests.**

  `RequestsRowTextTest`:
  - `nothing waiting and no recent answer reads no requests yet`.
  - `each part appears only when it has something to say, in order`. Check every combination of (0 or 2, 0 or 1, answer or none) against the exact `UiText` list, plural arguments included.
  - `an answer older than seven days is not mentioned`: 7 days ago at the same minute is included; one minute before that is not.
  - `only the co-parent's answers to my requests count`: my answer to theirs, a withdrawal and a custody decision are all excluded.
  - `relative days`: today → `Res(home_requests_day_today)`; yesterday → `Res(home_requests_day_yesterday)`; 3 days ago (2026-10-11, a Sunday) → `Weekday(SUNDAY)`; 7 days ago → `UiText.Date`.

  `HomeFeedTest`:
  - `the co-parent's answers to my requests appear with their outcome`.
  - `an accepted event is one Accepted row, not also an Updated one` (Review Focus 2).
  - `an accepted new time is one answer row, not also the event update it caused` (Review Focus 2).
  - `answers share the cap with the rest, newest first`.
  - `my answers to the co-parent's requests are not in their feed`.

  `HomeViewModelTest`:
  - Build the ViewModel with `requestsSource = RequestsSource(changeRequestRepository, eventRepository, custodyModelRepository, userRepository, pairingRepository)`.
  - Add `every { observeShared() } returns flowOf(null)` and `every { getActiveModel() } returns flowOf(null)` to the custody mock. Its mocks are not relaxed.
  - Add `the requests row counts what waits on each parent in the family on screen`.

- [ ] **Step 2: Run them and see them fail.**
  `./gradlew testDebugUnitTest --tests '*RequestsRowTextTest*' --tests '*HomeFeedTest*' --tests '*HomeViewModelTest*'`.

- [ ] **Step 3: Implement** `RequestsRow.kt`, `HomeFeed.kt`, `UiText.Weekday` (both `asString` overloads), and the `HomeViewModel` and `HomeScreen` changes.

- [ ] **Step 4: Run the tests and see them pass.** Then run `./gradlew detekt`.

- [ ] **Step 5: Update `home_strings.xml`** in all five locales. Delete `home_awaiting_title`.

  | Key | English |
  | --- | --- |
  | `home_requests_title` | Requests |
  | `home_requests_waiting_on_you` (plurals) | one and other: %1$d waiting for your answer |
  | `home_requests_waiting_on_them` (plurals) | one and other: %1$d waiting for %2$s |
  | `home_requests_answer_accepted` | %1$s: %2$s accepted your request |
  | `home_requests_answer_declined` | %1$s: %2$s declined your request |
  | `home_requests_day_today` | Today |
  | `home_requests_day_yesterday` | Yesterday |
  | `home_requests_none` | No requests yet |
  | `home_requests_all` | All requests and answers |
  | `home_activity_accepted_new_time` | Accepted your new time for %1$s |
  | `home_activity_declined_new_time` | Declined your new time for %1$s |
  | `home_activity_accepted_swap` | Accepted your day swap: %1$s |
  | `home_activity_declined_swap` | Declined your day swap: %1$s |
  | `home_activity_accepted_event` | Accepted %1$s |
  | `home_activity_declined_event` | Declined %1$s |

- [ ] **Step 6: Run** `node tools/check-invariants.js`. Expected: it passes.

- [ ] **Step 7: Commit.**
  `git commit -m "feat(home): a Requests row that is always there, and the co-parent's answers in the feed"`

---

### Task 7: The event form says who will read the description

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/event/AddEditEventScreen.kt`:
  - add the new function beside `showParentOwnerSelector` at :169;
  - give the description field's `supportingText` (~:793) a hint.
- Modify: `app/src/main/res/values*/event_strings.xml`
- Test: `app/src/test/java/com/coparently/app/presentation/event/AddEditEventScreenTest.kt`

**Interfaces:**
- Consumes: `EventAcceptanceTransition.required(event, creatorSlot, partnerUid)`.
- Produces:

```kotlin
/** Whether a description typed now will be read by the co-parent when they answer: the same rule as [EventAcceptanceTransition.required], for a new event. */
internal fun descriptionReachesCoParent(isNewEvent: Boolean, isPaired: Boolean, isPrivate: Boolean,
                                        parentOwner: String?, mySlot: String?): Boolean
```

- [ ] **Step 1: Write the failing test.** `the description hint follows the rule that makes an event wait for an answer`.
  - For every combination of `isPaired`, `isPrivate`, `parentOwner` in (`"mom"`, `"dad"`, null) and `mySlot` in (`"mom"`, null), a new event's `descriptionReachesCoParent(true, …)` equals `EventAcceptanceTransition.required(event.copy(isPrivate = …, parentOwner = owner ?: "mom"), mySlot, if (isPaired) "u2" else null)`. A null owner always answers false.
  - `isNewEvent = false` always answers false.

- [ ] **Step 2: Run it and see it fail.**
  `./gradlew testDebugUnitTest --tests '*AddEditEventScreenTest*'`.

- [ ] **Step 3: Implement it.**
  - When `descriptionError == null` and `descriptionReachesCoParent(eventId == null, isPaired, isPrivate, parentOwner, currentUser?.slot)`, the description's `supportingText` is `stringResource(R.string.event_form_description_coparent_hint, parentNames.labelFor(parentOwner))`.
  - The error branch is unchanged, and so is the null case.
  - Add `event_form_description_coparent_hint` "%1$s will see this when answering" to all five locales.

- [ ] **Step 4: Run the test and see it pass.**

- [ ] **Step 5: Commit.**
  `git commit -m "feat(event): the description says the co-parent will read it when they answer"`

---

### Task 8: A friend's grant names the two parents

**Files:**
- Modify: `functions/index.js`, `acceptCalendarFriendInvitationImpl` at :991. Add the read after `familyId` is computed, before the first `tx.set`.
- Modify: `functions/test/friend-invite.test.js`
- Modify: `app/src/main/java/com/coparently/app/domain/friends/FriendProfile.kt` (`CalendarFriendGrant` at :84)
- Modify: `app/src/main/java/com/coparently/app/data/repository/FriendMappers.kt` (`grantFrom` at :34)
- Test: `app/src/test/java/com/coparently/app/data/repository/FriendMappersTest.kt`

**Interfaces:**
- Consumes: `previewFirstName(name)` (`functions/index.js:577`, first word, `''` for an address, at most 40 characters).
- Produces:
  - the stored field `calendar_friends/{familyId}__{friendUid}.parentNames: {uid: firstName}`, with only non-empty names and omitted when both are empty;
  - `CalendarFriendGrant(..., val parentNames: Map<String, String> = emptyMap())`.

- [ ] **Step 1: Write the failing function tests** in `friend-invite.test.js` under `describe('acceptCalendarFriendInvitation')`. Reuse the fixture at :137.
  - `names both parents by first name on the grant`: with `alice.name = 'Alice Smith'` and `bob.name = 'Bob'`, `parentNames` is `{alice: 'Alice', bob: 'Bob'}`.
  - `never copies an e-mail address as a name`: with `bob.name = 'bob@example.com'`, `parentNames` is `{alice: 'Alice'}`.

- [ ] **Step 2: Run them and see them fail.**
  `cd functions && npx mocha test/friend-invite.test.js`.

- [ ] **Step 3: Implement it.**
  - `const partnerSnap = await tx.get(db.collection('users').doc(partnerId))` sits beside the legacy target read. Every read comes before any write.
  - Build `parentNames` from `previewFirstName(inviter?.name)` and `previewFirstName(partnerSnap.data()?.name)`, and `Object.assign` it into the grant when it is non-empty.

- [ ] **Step 4: Run the tests and see them pass.**
  `cd functions && npm test && npm run lint`.

- [ ] **Step 5: Write the failing mapper tests** in `FriendMappersTest`.
  - `reads the parents' first names`.
  - `ignores names that are not a map, values that are not text, and uids outside the family`.
  - `an older grant without names reads as none`.

- [ ] **Step 6: Implement `grantFrom`.** Read `data["parentNames"] as? Map<*, *>`. Keep the entries whose key is a `String` in `familyParents` and whose value is a non-blank `String`. Then run `./gradlew testDebugUnitTest --tests '*FriendMappersTest*'` and see it pass.

- [ ] **Step 7: Commit.**
  `git commit -m "feat(friends): a friend's grant carries the two parents' first names"`

---

### Task 9: A friend's read of the family's events

**Files:**
- Create: `app/src/main/java/com/coparently/app/domain/friends/FriendCalendarRead.kt`
- Modify: `app/src/main/java/com/coparently/app/domain/repository/FriendRepository.kt`
- Modify: `app/src/main/java/com/coparently/app/data/repository/FriendRepositoryImpl.kt`
- Modify: `app/src/main/java/com/coparently/app/data/repository/ProfessionalEvents.kt`
- Test: `app/src/test/java/com/coparently/app/data/repository/ProfessionalEventsTest.kt`
- Test: `app/src/test/java/com/coparently/app/data/repository/FriendCalendarReadTest.kt`

**Interfaces:**
- Consumes: `FamilyKey.membersOf(familyId)`, `ProfessionalEvents.from(documents, from, to)` and `CalendarVisibility.visible`.
- Produces:

```kotlin
sealed interface FriendCalendarRead {
    data class Events(val events: List<Event>) : FriendCalendarRead
    data object AccessEnded : FriendCalendarRead      // the rules refused the read: revoked or lapsed
    data object Failed : FriendCalendarRead           // anything else; the screen offers Retry
}
// FriendRepository (10 functions after this; the interface limit is 11)
fun observeFamilyEvents(familyId: String, from: LocalDate, to: LocalDate): Flow<FriendCalendarRead>
// FriendRepositoryImpl.kt, top level
internal fun friendCalendarReadFor(error: Throwable): FriendCalendarRead
```

**How the read works**
- It is a `callbackFlow` over `events.whereEqualTo("familyId", familyId).whereIn("createdByFirebaseUid", membersOf(familyId).toList())`. That is the query `firestore-tests/rules/friend-calendar.test.js:332` pins.
- Each snapshot sends `Events(ProfessionalEvents.from(docs.map { it.data.orEmpty() }, from, to))`.
- An error sends `friendCalendarReadFor(e)` and closes the flow:
  - `FirebaseFirestoreException` with `PERMISSION_DENIED` → `AccessEnded`;
  - anything else → `Failed`.
- A `familyId` that does not name two members sends `Failed`.
- Nothing is written to Room.

**Changes to `ProfessionalEvents.toEvent`**
- Also copy:
  - `acceptance`, parsed as `EventRepositoryImpl` parses it, with an unknown value read as `NOT_REQUIRED`;
  - `acceptedBy`, `acceptedAt`, `friendParticipates` and `lastModifiedBy`.
- `from` applies `CalendarVisibility.visible` to the masters before expansion.
- This also stops a professional seeing a pending or declined event (Review Focus 3).

- [ ] **Step 1: Write the failing tests.**

  `ProfessionalEventsTest`:
  - `an event waiting for an answer or declined is not shown`.
  - `who takes part survives the mapping`: `friendParticipates == "f1"`.

  `FriendCalendarReadTest`:
  - `a refused read means access ended`.
  - `an unavailable server is a failure to retry`.
  - `an error that is not Firestore's is a failure`.

- [ ] **Step 2: Run them and see them fail.**
  `./gradlew testDebugUnitTest --tests '*ProfessionalEventsTest*' --tests '*FriendCalendarReadTest*'`.

- [ ] **Step 3: Implement it.** `TestFriendRepository` is a strict mockk factory, so the new interface method needs no change there.

- [ ] **Step 4: Run the tests and see them pass.** Run `./gradlew testDebugUnitTest` too, since the professional calendar shares the mapper.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(friends): read the family's events as the rules let a friend, and hide unanswered events"`

---

### Task 10: The family friend's calendar

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/friends/FriendCalendarViewModel.kt`
- Create: `app/src/main/java/com/coparently/app/presentation/friends/FriendCalendarScreen.kt`
- Create: `app/src/main/java/com/coparently/app/presentation/friends/FriendEventDetails.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/professionals/ProfessionalCalendarScreen.kt` (`WindowPager` becomes `internal`)
- Modify: `app/src/main/java/com/coparently/app/presentation/navigation/NavGraph.kt` (`Screen.FriendCalendar` and its `pane`)
- Modify: `app/src/main/res/values*/friend_strings.xml`
- Test: `app/src/test/java/com/coparently/app/presentation/friends/FriendCalendarViewModelTest.kt`
- Test: `app/src/test/java/com/coparently/app/screenshots/FriendScreenshots.kt` (new class, TEXT_HEAVY)

**Interfaces:**
- Consumes:
  - `FriendRepository.observeFamilyEvents` (Task 9)
  - `observeMyGrants()`
  - `CalendarFriendGrant.parentNames` (Task 8)
  - `ProfessionalAgenda.days(start, dayCount, events, custody = null)`
  - `ProfessionalDay`
  - `WindowPager`
- Produces:

```kotlin
sealed interface FriendCalendarUiState {
    data object Loading : FriendCalendarUiState
    data object AccessEnded : FriendCalendarUiState
    data object Failed : FriendCalendarUiState
    data class Ready(val grant: CalendarFriendGrant?, val days: List<ProfessionalDay>) : FriendCalendarUiState
}
@HiltViewModel class FriendCalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle, friendRepository: FriendRepository
) : ViewModel() {
    val uiState: StateFlow<FriendCalendarUiState>
    fun previous(); fun next(); fun retry()
    companion object { const val ARG_FAMILY_ID = "familyId"; const val WINDOW_DAYS = 28 }
}
@Composable fun FriendCalendarScreen(onNavigateUp: () -> Unit, viewModel: FriendCalendarViewModel = hiltViewModel())
@Composable internal fun FriendAgendaDay(day: ProfessionalDay, grant: CalendarFriendGrant?, onOpenEvent: (Event) -> Unit)
@Composable internal fun FriendEventDetails(event: Event, addedBy: String?, takesPart: Boolean)
// NavGraph
data object FriendCalendar : Screen("friend_calendar/{familyId}") { fun createRoute(familyId: String) = "friend_calendar/$familyId" }
```

**How `FriendCalendarViewModel` decides**
- The window starts at `LocalDate.now()` and spans `WINDOW_DAYS`. `previous()` and `next()` move it by 28 days.
- `retry()` bumps a counter that re-subscribes the read.
- `uiState` is:
  - `AccessEnded` when the read says so, **or** when the grant for this family was in `observeMyGrants()` and has since left it. Firestore does not promise to end a live listener when another document revokes it.
  - Otherwise `Failed` when the read failed.
  - Otherwise `Ready(grant, ProfessionalAgenda.days(start, WINDOW_DAYS, events, custody = null))`.
  - `Loading` until the first read answers.
- A grant that has not arrived yet is not an ended one. Right after a redeem the read can answer first.

**What `FriendCalendarScreen` shows**
- Top bar: `friend_calendar_title`.
- The `Ready` header has three parts:
  - `friend_calendar_family` with the two names, when the grant names both parents (in `familyParents` order); nothing otherwise;
  - `friend_calendar_read_only`;
  - `WindowPager`.
- One `FriendAgendaDay` per day:
  - a `SectionGroup` headed by the FULL date;
  - with no events, the existing `professional_calendar_no_events`;
  - otherwise one `SectionRow` per event, titled with the event's title.
- An event row's supporting text is `shortTime()` of the start alone, or, when the grant names the creator, `friend_calendar_event_supporting` with that time and `friend_calendar_added_by` with `grant.parentNames[createdByFirebaseUid]`.
- A trailing `PillChip(friend_calendar_you_take_part, leadingDot = CoPlanlyColors.FriendTeal, contentColor = onSurfaceVariant)` appears when `friendParticipates == grant?.friendUid`.
- A tap opens a `ModalBottomSheet` whose body is `FriendEventDetails`: the title, the time line, the description, and the take-part chip. It has no actions.
- `AccessEnded` → `EmptyState(Icons.Default.EventBusy, friend_calendar_access_ended)`.
- `Failed` → `ErrorState(friend_calendar_failed, onRetry = viewModel::retry)`.
- `Loading` → a `CircularProgressIndicator`, as the professional screen shows.

- [ ] **Step 1: Write the failing tests** in `FriendCalendarViewModelTest`. Use a strict `FriendRepository` mock and `SavedStateHandle(mapOf("familyId" to "u1__u2"))`.
  - `loading until the first read answers`.
  - `events are laid out over four weeks`: 28 days, with the event on its day.
  - `a refused read shows that access has ended`.
  - `a grant that leaves the list ends access although the read still answers`.
  - `a failed read offers retry, and retry reads again`: `verify(exactly = 2) { observeFamilyEvents("u1__u2", any(), any()) }`.
  - `paging moves the window by four weeks`: the second call's `from` is `today.plusDays(28)`.

- [ ] **Step 2: Run them and see them fail.**
  `./gradlew testDebugUnitTest --tests '*FriendCalendarViewModelTest*'`.

- [ ] **Step 3: Implement** the ViewModel, the screen, `FriendEventDetails` and the route.

- [ ] **Step 4: Run the tests and see them pass.**

- [ ] **Step 5: Add the strings to `friend_strings.xml`** in all five locales.

  | Key | English |
  | --- | --- |
  | `friend_calendar_title` | Family calendar |
  | `friend_calendar_family` | %1$s and %2$s |
  | `friend_calendar_read_only` | View only. Private events are never shared. |
  | `friend_calendar_added_by` | Added by %1$s |
  | `friend_calendar_event_supporting` | %1$s · %2$s |
  | `friend_calendar_you_take_part` | You take part |
  | `friend_calendar_access_ended` | Your access to this calendar has ended |
  | `friend_calendar_failed` | The calendar couldn't be loaded |

- [ ] **Step 6: Add screenshot tests** in `FriendScreenshots`.
  - `friends_agenda_day`: a day with two events, one of them `friendParticipates = "f1"`, with `grant.friendUid = "f1"` and names.
  - `friends_event_details`.

- [ ] **Step 7: Commit.**
  `git commit -m "feat(friends): a family friend's read-only calendar"`

---

### Task 11: Family friends: the names, the table, the invite and the redeem

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/friends/FriendComparisonTable.kt`
- Create: `app/src/main/java/com/coparently/app/presentation/friends/GuestDurationText.kt`. It moves the private `GuestAccessDuration.labelRes()` out of `FriendInviteSheet.kt:143`.
- Modify: `app/src/main/java/com/coparently/app/presentation/friends/FriendsScreen.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/friends/FriendInviteSheet.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/friends/FriendViewModel.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/pairing/PairingLink.kt` (add `internal const val PLAY_STORE_URL`)
- Modify: `app/src/main/java/com/coparently/app/presentation/pairing/PairingScreen.kt:392` and `app/src/main/java/com/coparently/app/presentation/professionals/ProfessionalInviteSheet.kt:173`. Use the shared constant and drop the two private copies.
- Modify: `app/src/main/java/com/coparently/app/presentation/navigation/NavGraph.kt` (the Friends `pane` at :841)
- Modify these string files, five locales each: `friend_strings.xml`, `settings_account_strings.xml`, `pairing_strings.xml`.
- Test: `app/src/test/java/com/coparently/app/presentation/friends/FriendViewModelTest.kt`
- Test: `FriendScreenshots` (2 more snaps)

**Interfaces:**
- Consumes: `FamilyKey.orNull`, `ParentsSource.observe()`, `InviteCodeText`, `GuestAccessDuration.entries`, `FriendCalendar.createRoute` (Task 10).
- Produces:

```kotlin
fun FriendsScreen(onNavigateUp: () -> Unit, onOpenFriend: (String) -> Unit, onOpenMyProfile: () -> Unit,
                  onOpenFriendCalendar: (String) -> Unit, viewModel: FriendViewModel = hiltViewModel())
data class FriendRedeemState(val code: String = "", val isBusy: Boolean = false, val accepted: Boolean = false,
                             @StringRes val errorRes: Int? = null, val isExpanded: Boolean = false,
                             val openFamilyId: String? = null)
// FriendViewModel(friendRepository, parentsSource: ParentsSource)
val myName: StateFlow<String?>          // parents.me?.name
fun expandRedeem(); fun calendarOpened()
@Composable internal fun FriendComparisonTable(modifier: Modifier = Modifier)
@Composable internal fun FriendInviteContent(state: FriendInviteState, onChooseDuration: (GuestAccessDuration) -> Unit,
                                             onCreate: () -> Unit, onShare: (String) -> Unit, onDone: () -> Unit)
@StringRes internal fun GuestAccessDuration.labelRes(): Int
@Composable internal fun durationChoicesText(): String   // derived from GuestAccessDuration.entries
internal const val PLAY_STORE_URL: String
```

**The parent's view,** top to bottom:
1. `friend_intro`.
2. `FriendComparisonTable`. Its rows: Calendar, Custody schedule, Expenses, Chat, Children's records, How long.
   - Columns are Co-parent and Family friend. Text wraps and nothing is clipped.
   - Each row is one semantics node: `friend_compare_row_desc` with the label and both cells.
   - Yes and No cells pair a check or cross icon with the word.
   - The friend's "How long" cell is `durationChoicesText()`: the chip labels, the last two joined by `friend_compare_duration_choice` and any earlier ones by ", ".
   - Every cell must match the rules (spec §4.2). Cite `isCalendarFriendOf` and the collections a friend cannot read in the KDoc.
3. The friends list, or the existing `EmptyState`.
4. The invite row, whose title value becomes "Invite a family friend".
5. When `friends.isEmpty()`, the redeem row:
   - `SectionRow(friend_redeem_row_title, supporting = friend_redeem_row_supporting, onClick = viewModel::expandRedeem)`;
   - when `isExpanded`, the code field and a full-width `Button(friend_redeem_open_calendar)` in place of `pairing_link_accounts`.

**The friend's view** (`myGrants` is not empty)
- One `SectionRow` per grant, titled `friend_calendar_title`. Its supporting text is `friend_calendar_row_supporting` with both names and the expiry when the grant names both parents, else `friend_access_until`. A tap calls `onOpenFriendCalendar(grant.familyId)`.
- Then today's "My profile" row, unchanged.
- No redeem row (spec §4.2.4).

**After a redeem**
- `redeemCode()` success sets `openFamilyId = FamilyKey.orNull(parents[0], parents[1])`.
- The screen's `LaunchedEffect(redeem.openFamilyId)` calls `onOpenFriendCalendar(it)` and then `viewModel.calendarOpened()`.

**The invite sheet's body,** `FriendInviteContent`
- Above the chips, two lines with `Icons.Default.Check` and `Icons.Default.Close`. Each is one `AnnotatedString`: the lead in `MaterialTheme.typography.bodyMediumEmphasized.toSpanStyle()`, a space, then the body in `bodyMedium`. Emphasis is a type role, never a `fontWeight`. The lines are `friend_invite_will_see_lead` + `friend_invite_will_see_body`, then `friend_invite_wont_see_lead` + `friend_invite_wont_see_body`.
- The sheet no longer shows `friend_section_supporting`. That string is now the Settings row's line.
- After a code exists:
  - `InviteCodeText`;
  - `friend_invite_where_to_enter`;
  - `Button(friend_invite_share)`, which calls `onShare(code)`;
  - `TextButton(friend_invite_done)`, which calls `onDone`.
- The sheet's `onShare` starts `Intent.createChooser(Intent(ACTION_SEND).setType("text/plain").putExtra(EXTRA_TEXT, getString(friend_share_message, myName ?: "", code, PLAY_STORE_URL)), getString(friend_invite_share))`.
- `onDone` dismisses the sheet.

**Strings,** in all five locales. Each locale's path text must use that locale's own row titles.

Values that change:

| Key | New English |
| --- | --- |
| `settings_pairing_title` | Co-parent |
| `settings_pairing_description` | Your child's other parent: shared calendar, custody schedule, expenses and chat |
| `friend_section_title` | Family friends |
| `friend_section_supporting` | A grandparent, nanny or friend who only views the calendar, for as long as you choose |
| `friend_invite_action` | Invite a family friend |
| `pairing_error_friend_invitation` | This is a family friend invitation. Open Settings → Family friends → I have a code from the parents to use it. |

New keys:

| Key | English |
| --- | --- |
| `friend_intro` | A family friend is a grandparent, nanny or friend who needs to know where the children are. They only view the calendar and can't change anything. |
| `friend_compare_column_coparent` | Co-parent |
| `friend_compare_column_friend` | Family friend |
| `friend_compare_calendar` | Calendar |
| `friend_compare_custody` | Custody schedule |
| `friend_compare_expenses` | Expenses |
| `friend_compare_chat` | Chat |
| `friend_compare_children` | Children's records |
| `friend_compare_how_long` | How long |
| `friend_compare_views_and_changes` | Views and changes |
| `friend_compare_only_views` | Only views |
| `friend_compare_yes` | Yes |
| `friend_compare_no` | No |
| `friend_compare_while_linked` | While you are linked |
| `friend_compare_duration_choice` | %1$s or %2$s |
| `friend_compare_row_desc` | %1$s: co-parent %2$s, family friend %3$s |
| `friend_invite_will_see_lead` | Will see |
| `friend_invite_will_see_body` | the family's events in the calendar, except private ones. View only — they can't change anything. |
| `friend_invite_wont_see_lead` | Won't see |
| `friend_invite_wont_see_body` | the custody schedule, expenses, chat or children's records. |
| `friend_invite_where_to_enter` | They enter it in their own CoPlanly: Settings → Family friends → I have a code from the parents. |
| `friend_invite_share` | Share code |
| `friend_invite_done` | Done |
| `friend_share_message` | %1$s invited you to the family calendar in CoPlanly. Your code: %2$s. In CoPlanly, open Settings → Family friends → I have a code from the parents. Get the app: %3$s |
| `friend_redeem_row_title` | I have a code from the parents |
| `friend_redeem_row_supporting` | If you were invited as a family friend |
| `friend_redeem_open_calendar` | Open the family calendar |
| `friend_calendar_row_supporting` | %1$s and %2$s · access until %3$s |

- [ ] **Step 1: Write the failing tests** in `FriendViewModelTest`. Build with `FriendViewModel(friendRepository, testParentsSource())`.
  - `a redeemed code opens the calendar of the family it names`: the result `familyParents [u2, u1]` gives `openFamilyId == "u1__u2"`, and `calendarOpened()` clears it.
  - `the redeem field opens in place`: `expandRedeem()` sets `isExpanded`.
  - `a failed redeem opens no calendar`.

- [ ] **Step 2: Run them and see them fail.**
  `./gradlew testDebugUnitTest --tests '*FriendViewModelTest*'`.

- [ ] **Step 3: Implement** the screen, the sheet split, the table, the ViewModel changes, the strings and the `PLAY_STORE_URL` move.

- [ ] **Step 4: Run the tests and see them pass.** Then run `./gradlew detekt` and `node tools/check-invariants.js`.

- [ ] **Step 5: Add screenshot tests** in `FriendScreenshots`.
  - `friends_comparison`.
  - `friends_invite_sheet`: `FriendInviteContent` with a created code `"K7Q4-9P2M"`.
  - `DesignSystemScreenshots`' settings group changes its text. Its baseline is re-recorded in Task 14.

- [ ] **Step 6: Commit.**
  `git commit -m "feat(friends): say what a family friend sees, share the code, and open their calendar"`

---

### Task 12: The two-phone test of the friend's calendar

**Files:**
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/TwoParentAccessTest.kt`
- Modify: `tools/e2e/coverage.json`

**Interfaces:**
- Consumes: `newParent`, `pair`, `insertEvent(parent, title, isPrivate)`, `EmulatorParent.awaitFamilyWith`, `selectedFamilySource.select`, `friendRepository.observeFamilyEvents` (Task 9) and `CalendarFriendGrant.parentNames` (Task 8).

- [ ] **Step 1: Write the test** `aFamilyFriendSeesTheFamilysCalendarThroughTheApp`.
  1. `carol = newParent("Carol")`; `pair(inviter = alice, accepter = carol)`; `alice.awaitFamilyWith(carol.uid)`.
  2. With Bob's family selected (`alice.selectedFamilySource.select(FamilyKey.of(alice.uid, bob.uid))`), insert `shared` and a private event.
  3. Select Carol's family and insert `otherFamily`. Then select Bob's family again.
  4. Alice invites with `inviteFriend(now + MONTH_MS)`. `grandma` redeems, and `grant = grandma.friendRepository.myGrants().single()`.
  5. `assertEquals(mapOf(alice.uid to "Alice", bob.uid to "Bob"), grant.parentNames)`.
  6. `read = withTimeout(WAIT_MS) { grandma.friendRepository.observeFamilyEvents(grant.familyId, today, today.plusDays(28)).first { it is FriendCalendarRead.Events } }`. Its ids contain `shared.id` and neither the private event's nor `otherFamily.id`.
  7. Bob revokes with `bob.friendRepository.revokeFriend(grandma.uid)`. A fresh `observeFamilyEvents(...).first()` is `FriendCalendarRead.AccessEnded`.

- [ ] **Step 2: Register it.**
  - Add `"TwoParentAccessTest#aFamilyFriendSeesTheFamilysCalendarThroughTheApp"` to the `e2e` lists of `calendar_friends`, `events` and `acceptCalendarFriendInvitation` in `tools/e2e/coverage.json`.
  - Run `node tools/check-e2e-coverage.js`. Expected: it passes.

- [ ] **Step 3: Prove it.** This cannot run in a cloud session. The `e2e` CI job runs it, because the diff reaches `androidTest/` and `functions/`. Read its check run on the PR.

- [ ] **Step 4: Commit.**
  `git commit -m "test(e2e): a family friend reads the calendar through the app, and loses it on revoke"`

---

### Task 13: The UI tour and the device checklist

**Files:**
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/UiTourTest.kt`
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/UiTourSeed.kt` (if a helper is needed)
- Modify: `docs/DEVICE-CHECKLIST.md`: §5.6 and a new §5.8
- Modify: `tools/manual-test-plan.js` (`RULES`)

**Tour shots**
- In `homeSection`, after `change_requests_inbox`, add `change_requests_history`.
  - Through the inbox's own buttons, accept Bob's change request and decline Bob's swap. Both are seeded by `seedPending`.
  - Then `driver.scrollToEnd()` so History is in view.
  - The spec's "an answered request and an answered swap" are made on screen rather than seeded, so the tour exercises the answer path too.
- In `recordScreens`:
  - `fromSettings("friends", …)` keeps its name. It now shows the table.
  - Add `friends_invite_sheet`: tap `friend_invite_action`, then `friend_invite_action` again in the sheet, and wait for the code.
- Add a section `friend_view` last, after `switcher` and before `sign_out`.
  1. `dave` and `erin` (`newParent`) pair. Dave invites; Alice redeems through an injected `FriendRepository`; Dave inserts one event.
  2. Shoot `friend_calendar_list`: Settings → Family friends, which now shows the friend's branch.
  3. Shoot `friend_calendar`: tap the "Family calendar" row.
  - It is last because Alice holding a grant switches her Friends screen to the friend's branch.

**Device checklist**
- New `### 5.8 Requests on Home and the inbox's history · 2P, 1P fallback`. Its boxes:
  - the Requests row's line in each state: nothing; waiting on me; waiting on them; an answer yesterday;
  - History after the other phone accepts, declines and withdraws;
  - the awaiting card's description and mini month;
  - a two-family account showing only the family on screen;
  - the calendar banner's number equal to the row's.
- Extend §5.6:
  - step 1 reads "Settings → Family → **Family friends**";
  - add boxes for the table, the invite sheet's Will see and Won't see lines, Share code's text, the redeemed code opening the calendar, "You take part", and "Your access to this calendar has ended" after a revoke.
- In `RULES`, map `K/presentation/changerequests/**`, `K/domain/changerequests/**` and `K/presentation/home/Requests*`/`HomeFeed*` to `5.8`.
- Run `node --test tools/test/*.test.js`. Expected: it passes.

- [ ] **Step 1:** Make the tour and checklist edits above.
- [ ] **Step 2:** Run `node --test tools/test/*.test.js` and see it pass. The tour itself runs only through `.github/ui-tour-request`, after the PR is green.
- [ ] **Step 3: Commit.**
  `git commit -m "test(tour): the requests history, the friends screen and a friend's calendar; device checks for phase 2"`

---

### Task 14: Docs, the legal documents, and the screenshot baselines

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/ROADMAP.md`: the §11 entry, and the MON-18 note "a friend's app shows their grant and their profile, nothing else"
- Modify: `docs/legal/PRIVACY-POLICY.md` (:182, :333) and `docs/legal/RECORDS-OF-PROCESSING.md` (P8 at :120)
- Regenerate: `web/privacy/index.html`
- Touch: `.github/regenerate-request`

**`CLAUDE.md`**
- Add a new "Things that are easy to get wrong" item 40: **requests are derived for the family on screen, through one source**.
  - The inbox, Home's row and feed and the calendar banner read `RequestsSource`.
  - History is built from the stored records, never stored itself.
  - The flat `InboxItems` list is what the inbox draws and what it scrolls by.
  - There is no new Withdraw for events or custody (spec §6.2).
- Update the hard rule on calendar friends:
  - the friend's calendar reads `FriendRepository.observeFamilyEvents`, keeps no Room copy, and hides pending and declined events;
  - `parentNames` is on the grant.
- In design item 1, add `MiniMonth` (`presentation/calendar/components/`) as the one small calendar. Phase 3's day swaps reuse it.
- In UX item 5 and wherever "Awaiting your answer" or "Change requests" is named, use the new names.
- Update the screenshot count, "210 images", in Step 5, once the Regenerate run has recorded the baselines.

**Legal**
- A family friend now sees the two parents' first names on their own list and calendar.
- The data was already readable. A friend could read the events before this; the app now shows them.
- No new data type, so `DATA-SAFETY.md` and the DPIA register are unchanged. Say so in the commit body.
- Regenerate with `npx marked docs/legal/PRIVACY-POLICY.md | node tools/wrap-legal-page.js privacy > web/privacy/index.html`.

- [ ] **Step 1:** Make the doc edits above.
- [ ] **Step 2: Regenerate** the privacy page with the command above. Check that the HTML diff shows only the edited paragraphs.
- [ ] **Step 3: Commit the docs.**
  `git commit -m "docs: phase 2 in CLAUDE.md, the roadmap and the legal documents"`
- [ ] **Step 4: Request new screenshot baselines.**
  `date -u > .github/regenerate-request && git add .github/regenerate-request && git commit -m "chore: regenerate screenshot baselines for phase 2"`, then push.
  - The Regenerate workflow commits the baselines back to the branch.
  - Review its PNG diff: the new `requests_*` and `friends_*` images, the changed settings group, and **no change** to `calendar_month_grid` (Task 1 step 5).
- [ ] **Step 5: Record the new image count, and let CI verify.**
  - `git pull` the bot's commit.
  - Count the PNGs: `find app/src/test/screenshots -name '*.png' | wc -l`. Write that number in place of "210 images" in `CLAUDE.md`.
  - Commit (`docs: the screenshot count after phase 2`) and push. The bot's own commit starts no CI run, so this push is the one that verifies the baselines. Never push an empty commit to start CI.
