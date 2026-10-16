# Tester feedback, phase 3a: no request is left hanging — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four kinds of request wait on the other parent: a day swap, a change to an event, an event awaiting acceptance and a custody proposal. Each gets:
- a deadline the proposer chose;
- reminders on the answering phone, at sensible hours, with delivery and opening reported back;
- at the deadline, an explicit "No answer", with one optional 24-hour last chance;
- "No answer" as its own outcome in the inbox, History, the chat and the switcher's dot.

**Architecture:**
- **One clock document per request**, `request_deadlines/{id}`, stored beside the request's own record. No older build reads or writes it.
  - The phones derive every state from three things: the clock, the record and the time (`RequestClock`).
  - Only the end is stored: `outcome`.
- **The phones do all the work.** Nothing runs on a server schedule.
  - The proposer's phone writes the clock in the same action as the request. It also schedules its own deadline and end jobs, gives the last chance, and closes the request.
  - The answering phone records delivery, plans and posts the reminders in its own time zone, and records opening.
- **One derivation.**
  - `RequestsSource` reads the family's clocks, its other sources and a minute ticker.
  - `RequestInbox` and `RequestHistory` place each request by its state. The inbox, Home and the calendar therefore cannot disagree.
  - The switcher's dot applies the same rule to the other families.

**Tech Stack:**
- App: Kotlin 2.1, Compose with Material 3 1.4, Hilt.
- Backend: Firestore (listeners, one transaction, server timestamps), WorkManager (`@HiltWorker`), and FCM through `notification_queue`.
- Tests: JUnit, MockK, coroutines-test and Turbine; Roborazzi; the instrumented two-parent e2e on the Firebase emulators; Cloud Functions with mocha; the Firestore rules tests on the emulator.

**Spec:** `docs/superpowers/specs/2026-10-15-zuzana-feedback-phase-3a-requests-never-hang-design.md`, approved by the owner on 15 October 2026. Read it with this plan.
- The canvas is linked from the spec; its artboards 1–6 are spec §6.1–§6.6.
- The canvas's Russian copy is the owner's approved wording, so `values-ru` takes it wherever the canvas has a line.

## Global Constraints

**Strings and wording**
- Every new or changed user-facing string goes into all five locales in the same commit: `values`, `values-cs`, `values-de`, `values-ru` and `values-uk`.
- English is in sentence case.
- `node tools/check-invariants.js` must pass. It checks that every key is in every locale, that the format arguments agree, and the four-way agreement of push types.
- Text a ViewModel or a pure builder produces is a `UiText`, resolved in composition. Never inject a `Context` into a ViewModel.
- **Times**
  - Every date and time a reader sees goes through `answerByText` (Task 7) or `shortTime()`. Both follow the reader's 12/24-hour clock (R-9).
  - Times are shown in the device's zone.
  - No literal `"HH:mm"`.
- **Names**
  - Never show the words "Mom" or "Dad". Every parent label goes through `ParentNames`.
  - A notification that cannot resolve a name says `push_actor_fallback` ("Your co-parent").

**Design system**
- The last chance uses `InlineBanner(tone = BannerTone.ATTENTION)`.
- "No answer" is an outlined, neutral chip: `onSurfaceVariant` text and an `outline` border. Never the error colour, which belongs to "Declined".
- The deadline choices are `FilterChip`s in a `FlowRow`.
- Icons come from `IconSizes`, spacing from `Spacing`, corners from `MaterialTheme.shapes`.
- No new `RoundedCornerShape`, literal colour or literal dp size.

**Storage and backend**
- New:
  - the collection `request_deadlines`, with two composite indexes;
  - the client push type `request_last_chance`;
  - the push field `answerBy`;
  - the activity kinds `REQUEST_LAST_CHANCE` and `REQUEST_UNANSWERED`;
  - the WorkManager tag `request_clock`.
- Nothing is added to a `change_requests`, `custody_models` or `events` document.
  - The one exception: a one-day swap now fills `DayOverride.groupId`, a field that already exists.
- No Room schema change. Deadlines live in Firestore only.
- **Every background step takes the family from the clock's `familyId`**, never from the family on screen (spec §5.6).
  - A step never calls a function that goes through `CustodyModelRepository.currentPair()`.
  - A step never calls `ActivityAnnouncer.announce` without its new `partnerUid`.
- An older co-parent build must keep reading and answering every request as it does today.
  - No rule refuses an answer because a clock says "No answer" (spec §5.2, §5.5).

**Code rules**
- KDoc on every public class and function. Code and comments in English.
- minSdk 26: use `Instant.atZone(...)`, never `LocalDate.ofInstant`.
- detekt limits, read as where a finding starts:
  - a function with **6** parameters; a constructor with **7** (data classes are exempt);
  - `LongMethod` 60, `ReturnCount` 3, `LargeClass` 600;
  - `TooManyFunctions`: 11 in an interface or object, 20 in a class.
- **The baseline keys findings on a declaration's signature.**
  - Changing a baselined signature un-baselines its finding. This plan changes two:
    - `DaySwapSheet` (LongMethod): Task 12 splits it.
    - `CustodyModelRepository.applyDayOverridesForDates` (ReturnCount): Task 8 suppresses it with a reason.
  - It must **not** change the signatures of:
    - `AddEditEventScreen`;
    - `EventViewModel`'s and `HomeViewModel`'s constructors;
    - `DaySwapCard`;
    - `decideProposal` and `writeSwap`.
  - That is why the inbox cards read their clock from `LocalCardClock` (Task 13). `MessageItem` reads `LocalChatEventCard` for the same reason.
  - Never regenerate the detekt baseline for this work.
- Suppress a finding only where a task says so, always with a one-line reason.
- Conventional Commits. End every commit message with the session's `Co-Authored-By` and `Claude-Session` lines. Never put a model name in a commit, a PR, the code or the docs.

**Verifying**
- A cloud session has no Android SDK, so `./gradlew` does not run there.
  - A pure-JVM test class runs in the scratchpad harness once its sources are copied in.
  - Everything else is proved by CI.
- CI runs on a pull request only. Since PR #130 merged, this branch has none. One is opened only when the owner asks; the handoff asks.
- These run anywhere:
  - `cd functions && npm test && npm run lint`;
  - `node tools/check-invariants.js`;
  - `node --test tools/test/*.test.js`.
- `cd firestore-tests && npm test` needs a JDK 21 on `PATH`. Without one, CI's rules job runs it.
- Screenshot baselines are recorded only by the Regenerate workflow (Task 17). Until then the `screenshots` job lists the new images as having no baseline. That is expected.

## Rulings on what the spec leaves open (§11)

Each ruling is a decision the spec leaves to the builder. The owner may overrule any.

1. **Server times.** `createdAt`, `lastChanceAt` and `closedAt` are Firestore server timestamps, pinned by the rules to `request.time`, as `event_versions.recordedAt` is.
   - The spec's names end in `Millis`. A millis value a client chooses, bounded to minutes of `request.time`, would be refused when an offline phone's queue lands hours later.
   - The app reads all three as millis.
2. **The button and the end.**
   - `buttonUntil = min(answerBy + 24 h, cap − 2 h)`.
   - The button is offered while `answerBy ≤ now < buttonUntil`.
   - Pressing it gives `window = min(24 h, cap − 1 h − now)`, which is always at least 1 hour.
   - The end is `lastChanceUntil` when a last chance was given. Otherwise it is `max(answerBy, buttonUntil)`.
   - Together these meet every number in spec §4.2 and §4.4.
3. The button reads "Give a last chance — 24 hours" only when the window is 24 hours. Otherwise it reads "Give a last chance — until <time>". A label must not promise 24 hours it cannot give (design item 8).
4. **Pushes and notices name the person and the time, not the request.**
   - "<request>" in five languages would need a sentence per kind and per grammatical case.
   - A tap opens the card that names the request. The chat cards and the inbox name it.
   - So `request_last_chance` carries no field besides `answerBy`.
5. "Nothing has changed in the calendar." stands alone, without "<what stays whose>". The card above it already shows which days.
6. **Late reminders.**
   - A reminder whose job runs more than 30 minutes after its planned time posts nothing and is not counted. A phone that was off must not remind at night.
   - The answering phone plans only reminders still ahead when it receives the clock.
7. A delivered or opened time may be up to 5 minutes ahead of the server's clock, because device clocks drift.
8. **A refused clock.**
   - A clock whose write is refused leaves the request working as one without a clock (spec §5.1).
   - A change request that went to the Room outbox, and is sent later by `flushOutbox`, goes without a deadline. Deadlines are not stored in Room.
9. Deleting a seasonal layer is sent with the default deadline. Its confirmation dialog gets no deadline block.
10. **A proposal's cap counts every kind of change.** It takes the first day whose whole-day custody, contact windows or a child's own schedule changes, not only `movedDays`. Otherwise a proposal that changes tomorrow afternoon could get a 3-day deadline.
11. **A date outside the allowed range is clamped**, not refused:
    - too near: the "Tomorrow" time;
    - too far: the latest allowed time.

    `LocalDatePickerDialog` keeps its signature.
12. A swap's clock carries the offer's `firstDate` and `dayCount`. A "No answer" row then survives its record being replaced (spec §5.1, "an orphaned clock").
13. A "No answer" whose change request or event no longer exists is left out of History. The chat card still records it.
14. **Background writes wait at most 10 seconds** for the server.
    - A write still queued after that counts as written: Firestore delivers it later.
    - A refused write counts as not written.
    - Without the limit, a worker would hang offline until WorkManager stops it.
15. The presets add wall-clock days in the device's zone ("Tomorrow" is the same time tomorrow), then round up to the quarter hour. "A week" across a clock change therefore keeps its time of day.

## Review Focus

These five cases are implied by the spec but named by none of its tests. Each is pinned by a test in the task shown.

1. **A parent in two families.**
   - A clock from the family not on screen appears nowhere: not in the inbox, not on Home, not on the calendar.
   - A step for a family whose pairing has ended does nothing.
   - The end's chat card goes to the clock's family while the switcher shows the other.
   - Tests: Task 10 `RequestInboxTest`; Task 11 `RequestClockStepsTest` and `RequestClockKeeperTest`; Task 6 `ActivityAnnouncerTest`.
2. **Two time zones and a clock change.**
   - Quiet hours follow the answering parent's own clock, not the proposer's.
   - A reminder plan across 25 October 2026 (the end of summer time in the EU) puts nothing between 21:00 and 08:00.
   - Tests: Task 3 `ReminderPlanTest`; Task 2 `AnswerByTest`.
3. **A device clock ahead of the server, and an offline proposer.**
   - Delivery 4 minutes in the future is accepted; 10 minutes is refused.
   - A clock refused by the rules leaves the request sent, with no `answerBy` on its push.
   - Tests: Task 4 `request-deadlines.test.js`; Task 8 `CustodyModelRepositoryTest`.
4. **A swap offered again for the same day.** The new offer replaces the old override and orphans the old clock.
   - Without an outcome, the old clock shows nothing.
   - With one, History keeps the row, using the clock's first day and count.
   - Tests: Task 10 `RequestHistoryTest`.
5. **An answer racing the end.**
   - The end never closes an answered request.
   - The proposal transaction never withdraws a proposal that was decided meanwhile or replaced by a newer one.
   - Tests: Task 11 `RequestClockStepsTest`; Task 8 `CustodyModelRepositoryTest`.

## Order of work

| Task | What | Needs |
| --- | --- | --- |
| 1 | The clock and its states (domain) | — |
| 2 | Choosing a deadline, and the cap (domain) | — |
| 3 | The reminder plan (domain) | — |
| 4 | Rules, indexes and their tests | — |
| 5 | The clock in Firestore: wire form, data source, repository, jobs, time | 1 |
| 6 | Two chat cards, and the functions that must not push them | — |
| 7 | The last-chance push and the `answerBy` field | — |
| 8 | Swaps and proposals start their clocks | 2, 5, 7 |
| 9 | Change requests and events start their clocks | 2, 5, 7 |
| 10 | The inbox places each request by its clock | 1, 5 |
| 11 | The phones' background work | 3, 5, 6, 7, 8 |
| 12 | The deadline block in the four forms | 2, 8, 9 |
| 13 | The inbox cards, the seen mark and the last chance | 10, 11 |
| 14 | The switcher's dot reads the clocks | 5, 10 |
| 15 | Two phones on the emulators | 8, 11, 13 |
| 16 | The UI tour and the device checklist | 13, 15 |
| 17 | Legal documents, CLAUDE.md, ROADMAP, screenshot baselines | all |

---

### Task 1: The clock and its states

**Files:**
- Create: `app/src/main/java/com/coparently/app/domain/changerequests/RequestDeadline.kt`
- Create: `app/src/main/java/com/coparently/app/domain/changerequests/RequestClock.kt`
- Test: `app/src/test/java/com/coparently/app/domain/changerequests/RequestClockTest.kt`
- Test: `app/src/test/java/com/coparently/app/domain/changerequests/RequestDeadlineIdTest.kt`

**Interfaces:**
- Consumes:
  - `DaySwapGroup` (`swaps.first().override.groupId`, `.requestedBy`)
  - `ChangeRequest` (`id`, `requestedBy`)
  - `Event` (`id`, `createdByFirebaseUid`)
  - `CustodyProposal` (`proposedBy`, `proposedAt`)
- Produces (package `com.coparently.app.domain.changerequests`):

```kotlin
enum class RequestKind(val wire: String) {
    SWAP("swap"), CHANGE("change"), EVENT("event"), PROPOSAL("proposal");
    companion object { fun fromWire(value: String?): RequestKind? }
}
enum class RequestClockOutcome(val wire: String) {
    NO_ANSWER("no_answer");
    companion object { fun fromWire(value: String?): RequestClockOutcome? }
}

/** One request's clock, `request_deadlines/{id}`, as this build reads it (spec §5.1). */
data class RequestDeadline(
    val id: String,
    val kind: RequestKind,
    val ref: String,                       // groupId | change request id | event id | proposal's proposedAt
    val familyId: String,
    val proposerUid: String,
    val responderUid: String,
    val answerByMillis: Long,
    val capMillis: Long? = null,
    val createdAtMillis: Long? = null,     // server time; null before the server stamped it
    val firstDate: LocalDate? = null,      // swaps: the offer's first day (ruling 12)
    val dayCount: Int? = null,             // swaps: how many days it covers
    val deliveredAtMillis: Long? = null,
    val remindersPlanned: Int? = null,
    val reminderTimesMillis: List<Long> = emptyList(),
    val seenAtMillis: Long? = null,
    val remindersShown: Int = 0,
    val lastChanceAtMillis: Long? = null,  // server time
    val lastChanceUntilMillis: Long? = null,
    val outcome: RequestClockOutcome? = null,
    val closedAtMillis: Long? = null       // server time
)

/** What a sender knows when it starts a clock. */
data class RequestStart(
    val kind: RequestKind, val ref: String, val familyId: String, val responderUid: String,
    val answerByMillis: Long, val capMillis: Long?, val firstDate: LocalDate? = null, val dayCount: Int? = null
)

object RequestDeadlineId {
    fun of(kind: RequestKind, proposerUid: String, ref: String, familyId: String): String
    fun forSwap(group: DaySwapGroup): String?            // null when the offer has no groupId (sent before 3a)
    fun forChange(request: ChangeRequest): String
    fun forEvent(event: Event): String?                  // null without a creator
    fun forProposal(proposal: CustodyProposal, familyId: String): String
}

enum class ClockState { OPEN, NO_ANSWER_LAST_CHANCE_POSSIBLE, LAST_CHANCE, NO_ANSWER }

object RequestClock {
    const val LAST_CHANCE_MILLIS: Long = 86_400_000      // 24 h
    const val BUTTON_MILLIS: Long = 86_400_000           // 24 h after the deadline
    const val CAP_MARGIN_MILLIS: Long = 3_600_000        // a last chance ends 1 h before the cap
    const val MIN_LAST_CHANCE_MILLIS: Long = 3_600_000   // and is not offered for less than 1 h
    fun stateOf(clock: RequestDeadline, nowMillis: Long): ClockState
    fun lastChanceWindowMillis(clock: RequestDeadline, nowMillis: Long): Long?   // what pressing now would give; null = not offered
    fun buttonUntilMillis(clock: RequestDeadline): Long
    fun endMillis(clock: RequestDeadline): Long
    fun governs(clock: RequestDeadline, authorUid: String, otherUid: String): Boolean
}

/** A clock seen at one moment: what every card and step reads. */
data class TimedRequest(val clock: RequestDeadline, val nowMillis: Long) {
    val state: ClockState                 // RequestClock.stateOf(clock, nowMillis)
    val lastChanceWindowMillis: Long?
    val nextReminderMillis: Long?         // the first planned time after nowMillis
    val waitsOnResponder: Boolean         // OPEN or LAST_CHANCE
}

/** The clocks of one family at one moment. */
data class ClocksAt(val byId: Map<String, RequestDeadline> = emptyMap(), val nowMillis: Long = 0L) {
    fun timed(id: String?): TimedRequest?
    companion object { fun of(clocks: List<RequestDeadline>, nowMillis: Long): ClocksAt }
}
```

- [ ] **Step 1: Write the failing tests.**

  `RequestDeadlineIdTest`:
  - ``fun `the id carries the kind and the proposer, and a proposal its family`()``:
    - `of(SWAP, "uA", "g-1", "uA__uB") == "swap_uA_g-1"`
    - `of(CHANGE, "uA", "cr-1", …) == "change_uA_cr-1"`
    - `of(EVENT, "uA", "ev-1", …) == "event_uA_ev-1"`
    - `of(PROPOSAL, "uA", "2026-10-15T10:00:00", "uA__uB") == "proposal_uA_uA__uB_2026-10-15T10:00:00"`
  - ``fun `an offer sent before groups had ids has no clock`()``: `forSwap` of a group whose first override has `groupId = null` is `null`.
  - ``fun `each record names its own author as the proposer`()``:
    - `forChange` uses `requestedBy`;
    - `forEvent` uses `createdByFirebaseUid`, and is `null` without one;
    - `forProposal` uses `proposedBy`.

  `RequestClockTest` uses `H = 3_600_000L`, `T0 = 1_791_000_000_000L` and a helper `clock(answerBy = T0, cap = null, …)`:
  - ``fun `before its deadline a request is open`()``: `stateOf(clock(), T0 - 1) == OPEN`.
  - ``fun `with no cap the button is offered for a day after the deadline`()``:
    - `stateOf(T0) == NO_ANSWER_LAST_CHANCE_POSSIBLE`
    - `lastChanceWindowMillis(T0) == 24 * H`
    - `stateOf(T0 + 24 * H) == NO_ANSWER`
    - `endMillis == T0 + 24 * H`
  - ``fun `a near cap shortens the last chance and ends the button two hours before it`()``, with `cap = T0 + 10 * H`:
    - `lastChanceWindowMillis(T0) == 9 * H`
    - `buttonUntilMillis == T0 + 8 * H`
    - `lastChanceWindowMillis(T0 + 7 * H + 30 * 60_000) == 90 * 60_000L`
    - `stateOf(T0 + 8 * H) == NO_ANSWER`
  - ``fun `a cap less than two hours after the deadline offers no button and ends at the deadline`()``, with `cap = T0 + 90 * 60_000`:
    - `stateOf(T0) == NO_ANSWER`
    - `endMillis == T0`
  - ``fun `a last chance runs to its end, and the button never comes back`()``, with `lastChanceAt = T0 + H` and `until = T0 + 25 * H`:
    - `stateOf(T0 + 2 * H) == LAST_CHANCE`
    - `lastChanceWindowMillis(T0 + 2 * H) == null`
    - `stateOf(T0 + 25 * H) == NO_ANSWER`
    - `endMillis == T0 + 25 * H`
  - ``fun `a stored outcome is final whatever the time`()``: `outcome = NO_ANSWER`, `stateOf(T0 - H) == NO_ANSWER`.
  - ``fun `a clock governs a record only when it names the record's author and the other parent`()``:
    - `governs(clock(proposer = "uA", responder = "uB"), "uA", "uB")` is true;
    - `("uB", "uA")` is false;
    - `("uA", "uC")` is false.
  - ``fun `a timed request knows its next reminder`()``: with times `[T0 - 2H, T0 - H]` at `T0 - 90 min`, the next is `T0 - H`; at `T0 - 30 min` it is `null`.
  - ``fun `the family's clocks are looked up by id at the moment they were read`()``:
    - `ClocksAt.of(listOf(c), T0).timed(c.id)?.nowMillis == T0`
    - `timed("x") == null`
    - `timed(null) == null`

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*RequestClockTest*' --tests '*RequestDeadlineIdTest*'`
  - Expected: compilation fails on the unresolved `RequestDeadline`.

- [ ] **Step 3: Implement the two files.**
  - `stateOf` decides in this order:
    1. `outcome != null` → `NO_ANSWER`;
    2. `now < answerBy` → `OPEN`;
    3. a last chance was given → `LAST_CHANCE` before its end, else `NO_ANSWER`;
    4. otherwise → `NO_ANSWER_LAST_CHANCE_POSSIBLE` while `lastChanceWindowMillis` is non-null, else `NO_ANSWER`.
  - Ruling 2 fixes `buttonUntilMillis`, `lastChanceWindowMillis` and `endMillis`.
  - `RequestDeadlineId.of` joins its parts with `_`. A Firebase uid has no `_` (`FamilyKey`'s KDoc), and the rules (Task 4) rebuild the id the same way.

- [ ] **Step 4: Run the tests and see them pass.**
  Same command. Expected: 12 tests pass.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(requests): a request's clock and the states it derives"`

---

### Task 2: Choosing a deadline, and the cap

**Files:**
- Create: `app/src/main/java/com/coparently/app/domain/changerequests/AnswerBy.kt`
- Create: `app/src/main/java/com/coparently/app/domain/changerequests/RequestCap.kt`
- Test: `app/src/test/java/com/coparently/app/domain/changerequests/AnswerByTest.kt`
- Test: `app/src/test/java/com/coparently/app/domain/changerequests/RequestCapTest.kt`

**Interfaces:**
- Consumes:
  - `CustodyModel.getCustodyFor(date)`, `contactWindowsOn(date)`, `CustodyModel.sameContactWindows(a, b)`, `childOverrides`
  - `ChildCustody.resolver(override, family)`
- Produces:

```kotlin
enum class AnswerByPreset { TOMORROW, THREE_DAYS, WEEK }
sealed interface AnswerByPick {
    data class Preset(val preset: AnswerByPreset) : AnswerByPick
    data class OnDate(val date: LocalDate) : AnswerByPick
    companion object {
        val DEFAULT: AnswerByPick                        // Preset(THREE_DAYS)
        fun encode(pick: AnswerByPick): String           // "TOMORROW" | "THREE_DAYS" | "WEEK" | "DATE:2026-10-20"
        fun decode(text: String?): AnswerByPick          // DEFAULT for anything unreadable
    }
}
enum class AnswerByBand { CHOICES, FIXED, TOO_SOON }
data class AnswerByChip(val pick: AnswerByPick, val answerByMillis: Long, val compressed: Boolean)
data class AnswerByOffer(
    val band: AnswerByBand,
    val nowMillis: Long,
    val chips: List<AnswerByChip> = emptyList(),   // CHOICES: the presets, merged where they meet
    val fixedMillis: Long? = null,                 // FIXED: the one deadline
    val latestMillis: Long? = null                 // CHOICES: the latest a picked date may read
)
object AnswerBy {
    const val PICKED_DATE_HOUR = 20
    const val QUARTER_HOUR_MILLIS = 900_000L
    const val NO_DEADLINE_UNDER_MILLIS = 7_200_000L     // 2 h
    const val FULL_CHOICE_FROM_MILLIS = 172_800_000L    // 48 h
    const val LAST_CHANCE_RESERVE_MILLIS = 86_400_000L  // 24 h
    const val MAX_AHEAD_MILLIS = 2_678_400_000L         // 31 days, the rules' bound
    fun offer(nowMillis: Long, capMillis: Long?, zone: ZoneId): AnswerByOffer
    fun resolve(pick: AnswerByPick, nowMillis: Long, capMillis: Long?, zone: ZoneId): Long?   // the deadline; null = none
}
object RequestCap {
    const val PROPOSAL_SCAN_DAYS = 366
    fun forSwap(dates: Collection<LocalDate>, zone: ZoneId): Long?
    fun forChange(currentStart: LocalDateTime, proposedStart: LocalDateTime, nowMillis: Long, zone: ZoneId): Long?
    fun forEvent(start: LocalDateTime, nowMillis: Long, zone: ZoneId): Long?
    fun forProposal(agreed: CustodyModel?, proposed: CustodyModel, today: LocalDate, zone: ZoneId): Long?
}
```

- [ ] **Step 1: Write the failing tests.**

  Both classes use `ZONE = ZoneId.of("Europe/Prague")` and `NOW` = 2026-10-10T10:07 in that zone. Assert instants through a helper `at("2026-10-13T10:15")`.

  `AnswerByTest`:
  - ``fun `with no cap the presets are tomorrow, three days and a week, rounded up to the quarter hour`()``:
    - band `CHOICES`;
    - chip times `at("2026-10-11T10:15")`, `at("2026-10-13T10:15")` and `at("2026-10-17T10:15")`, none compressed;
    - `resolve(DEFAULT) == at("2026-10-13T10:15")`.
  - ``fun `a picked date reads eight in the evening`()``: `resolve(OnDate(2026-10-14)) == at("2026-10-14T20:00")`.
  - ``fun `an option later than a day before the cap reads the latest time`()``, with cap `at("2026-10-15T00:00")`:
    - the week chip is `at("2026-10-14T00:00")` with `compressed = true`;
    - tomorrow and three days are unchanged;
    - `resolve(OnDate(2026-10-20)) == at("2026-10-14T00:00")`.
  - ``fun `options that land on the same time merge into one chip`()``, with cap `at("2026-10-13T08:00")`:
    - two chips, tomorrow and then three days at `at("2026-10-12T08:00")` with `compressed = true`;
    - `resolve(Preset(WEEK)) == at("2026-10-12T08:00")`.
  - ``fun `between two and forty-eight hours there is one deadline halfway, rounded down`()``, with cap `at("2026-10-11T08:00")`:
    - band `FIXED`;
    - `fixedMillis == at("2026-10-10T21:00")`;
    - `resolve` gives the same time for every pick.
  - ``fun `under two hours there is no deadline`()``: with cap `at("2026-10-10T11:30")` the band is `TOO_SOON` and `resolve(DEFAULT) == null`.
  - ``fun `a picked date too near falls back to tomorrow, and nothing is further than 31 days`()``:
    - with now at `2026-10-10T19:00`, `resolve(OnDate(2026-10-10)) == at("2026-10-11T19:00")`;
    - with no cap, `resolve(OnDate(2026-12-31))` is now + 31 days, rounded *down* to the quarter hour.
  - ``fun `a week across the clock change keeps its time of day`()``: with now at `2026-10-20T10:07`, `resolve(Preset(WEEK)) == at("2026-10-27T10:15")` (ruling 15).
  - ``fun `the pick survives its saved form`()``:
    - `decode(encode(p)) == p` for each preset and for `OnDate(2026-10-20)`;
    - `decode("garbage") == DEFAULT`.

  `RequestCapTest`:
  - ``fun `a swap's cap is the start of its earliest day`()``: `forSwap([10-21, 10-16, 10-17]) == at("2026-10-16T00:00")`.
  - ``fun `a change request's cap is the earlier start still ahead`()``:
    - current `10-15 17:30`, proposed `10-13 18:00` → `at("2026-10-13T18:00")`;
    - a series whose stored start is `2025-01-01 09:00`, proposed `10-20 09:00` → `at("2026-10-20T09:00")`;
    - both in the past → `null`.
  - ``fun `an event's cap is its start until it begins`()``.
  - ``fun `a proposal's cap is the first day anything changes, today or later`()``:
    - the agreed model is `weekOnWeekOff` started on `2026-10-05`;
    - the same pattern started on `2026-10-19` moves the earliest whole day the two disagree on, its 00:00;
    - an identical proposal → `null`;
    - no agreed model → `null`.
  - ``fun `a change to an afternoon or to a child's own days caps a proposal too`()``:
    - a contact window added on cycle day 4 → its first date ≥ today;
    - a child override that differs from `10-12` → `at("2026-10-12T00:00")` (ruling 10).

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*AnswerByTest*' --tests '*RequestCapTest*'`
  - Expected: compilation fails.

- [ ] **Step 3: Implement `AnswerBy.kt`.** Follow spec §4.2 and rulings 11 and 15.
  - **Rounding.** Round up to the next quarter hour from the zone's local time; a time already on a quarter is unchanged.
  - **The bands**, with `lead = cap − now`:
    - `lead < 2 h` → `TOO_SOON`;
    - `lead < 48 h` → `FIXED` at `now + lead / 2`, rounded *down*;
    - otherwise → `CHOICES`, with `L = cap − 24 h`.
  - **Choices.** Each chip is `min(rounded preset, L)`, and `compressed` is true when L won.
    - Merge chips with equal millis, keeping the first.
    - `latestMillis = L`. With no cap it is now + 31 days, rounded down.
  - **`resolve`** follows the band:
    - `FIXED` → `fixedMillis`, whatever the pick;
    - `TOO_SOON` → `null`;
    - otherwise the chip's time for a preset. A date is its 20:00, clamped as ruling 11 says, then `min(…, latestMillis)`.

- [ ] **Step 4: Implement `RequestCap.kt`.**
  - Every result is the epoch millis of a local date-time in `zone`.
  - **`forProposal`** scans `today` to `today + PROPOSAL_SCAN_DAYS` for the first day that differs in any of these:
    - `getCustodyFor`;
    - `sameContactWindows(contactWindowsOn)`;
    - for each child id in either model's `childOverrides`, `ChildCustody.resolver(override, model::getCustodyFor)`.

    The cap is that day's 00:00.

- [ ] **Step 5: Run the tests and see them pass.**
  Same command. Expected: 14 tests pass.

- [ ] **Step 6: Commit.**
  `git commit -m "feat(requests): the deadline a proposer can choose, capped by the first day it changes"`

---

### Task 3: The reminder plan

**Files:**
- Create: `app/src/main/java/com/coparently/app/domain/changerequests/ReminderPlan.kt`
- Test: `app/src/test/java/com/coparently/app/domain/changerequests/ReminderPlanTest.kt`

**Interfaces:**
- Produces:

```kotlin
object ReminderPlan {
    val QUIET_FROM: LocalTime          // 21:00
    val QUIET_UNTIL: LocalTime         // 08:00
    val EVENING_BEFORE: LocalTime      // 20:30
    fun countFor(windowMillis: Long): Int   // ≥ 24 h → 3; ≥ 6 h → 2; > 0 → 1; else 0
    fun plan(sentAtMillis: Long, answerByMillis: Long, notBeforeMillis: Long, zone: ZoneId): List<Long>
}
```

- [ ] **Step 1: Write the failing tests** in `ReminderPlanTest`. Use the Prague zone and `at(...)` as in Task 2.
  - ``fun `a day or more gets three, at a third, two thirds and two hours before`()``: sent `10-10T10:00`, deadline `10-13T10:00` → `10-11T10:00`, `10-12T10:00`, `10-13T08:00`.
  - ``fun `six to twenty-four hours gets two, at the midpoint and an hour before`()``: `10:00`→`20:00` gives `15:00` and `19:00`.
  - ``fun `under six hours gets one at the midpoint`()``: `10:00`→`14:00` gives `12:00`.
  - ``fun `a reminder at night moves to eight in the morning`()``: sent `10-10T18:00`, deadline `10-11T18:00` → `10-11T08:00`, `10-11T10:00`, `10-11T16:00`.
  - ``fun `when eight is too late it moves to half past eight the evening before, and equal times merge`()``: sent `10-10T14:00`, deadline `10-11T07:00` → only `10-10T20:30`.
  - ``fun `a time moved before the request was sent is dropped`()``: sent `10-10T21:30`, deadline `10-11T07:00` → empty.
  - ``fun `reminders already past when the phone receives it are not planned`()``: the first case with `notBefore = 10-12T12:00` → `10-13T08:00` only.
  - ``fun `night is the answering parent's own`()``:
    - sent `10-10T12:00Z`, deadline `10-11T12:00Z`, zone `America/New_York`;
    - every planned time is 08:00–21:00 in New York;
    - `10-10T20:00Z` is among them, though Prague calls it night (22:00).
  - ``fun `nothing lands at night across the October clock change`()``:
    - sent `2026-10-24T12:00`, deadline `2026-10-26T12:00`, Prague;
    - every time's local hour is in 08–20.
  - ``fun `the count follows the window`()``: 24 h → 3, 23 h 59 min → 2, 6 h → 2, 5 h 59 min → 1, 0 → 0.

- [ ] **Step 2: Run them and see them fail.**
  Run: `./gradlew testDebugUnitTest --tests '*ReminderPlanTest*'`. Expected: compilation fails.

- [ ] **Step 3: Implement `plan`** (spec §4.3, ruling 6). Run the steps in this order:
  1. Compute the raw times by `countFor`.
  2. Move every time in quiet hours to the next 08:00.
  3. If that is at or after the deadline, move it to 20:30 on the day before that 08:00.
  4. If that is at or before `sentAt`, drop it.
  5. Drop times before `notBefore`.
  6. Remove duplicates and sort.

  Compute with `Instant.atZone(zone)`.

- [ ] **Step 4: Run the tests and see them pass.**
  Same command. Expected: 10 tests pass.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(requests): up to three reminders, none at night on the answering parent's clock"`

---

### Task 4: Rules, indexes and their tests

**Files:**
- Modify: `firestore.rules`:
  - a new `match /request_deadlines/{clockId}` block with its helpers;
  - `isClientPushType` (+ `'request_last_chance'`);
  - the `notification_queue` create rule (bound `answerBy`).
- Modify: `firestore.indexes.json` (two composite indexes)
- Create: `firestore-tests/rules/request-deadlines.test.js`
- Modify: `firestore-tests/rules/notification-payload.test.js`

**Interfaces:**
- Consumes:
  - the stored shape of Task 1, as Task 5's `RequestDeadlineDocument` writes it: `kind`, `ref`, `familyId`, `proposerUid`, `responderUid`, `participants`, `createdAt`, `answerByMillis`, `capMillis`, `firstDate`, `dayCount`, then the later fields;
  - the rule helpers `isAuthenticated`, `isLiveFamilyMember`, `isValidLength`.
- Produces:
  - the rules the client's writes must pass;
  - the indexes on `(participants CONTAINS, familyId ASC)` and `(participants CONTAINS, answerByMillis ASC)`.

- [ ] **Step 1: Write the failing rules tests** in `request-deadlines.test.js`.
  - Use `harness.js`'s `testEnv`, `seed`, `assertSucceeds` and `assertFails`.
  - Seed paired users as `budgets-change-requests.test.js` does, plus `families/${FAMILY}`.
  - `clockDoc(over)` is a swap clock from ALICE to BOB:
    - `answerByMillis: Date.now() + 3 days`;
    - `createdAt: serverTimestamp()`;
    - id `swap_${ALICE}_g-1`.
  - A clock past its deadline is seeded with rules disabled.

  `describe('request_deadlines')`:
  - `it('lets the proposer start a clock for a request to their live co-parent')`
  - `it('refuses a clock under somebody else\'s uid, or one whose id does not match its fields')`:
    - BOB writes `swap_${ALICE}_g-1`;
    - ALICE writes `swap_${ALICE}_g-2` with `ref: 'g-1'`;
    - a proposal id without its family.
  - `it('refuses a responder outside the family, and a family that has ended')`: responder CAROL; `families/` deleted.
  - `it('refuses a deadline in the past or more than 31 days out, and a cap not after it')`
  - `it('refuses a createdAt the client chose, and any later field on create')`: `Timestamp.fromMillis(...)`; `deliveredAtMillis` present.
  - `it('lets the two parents read and list it by participants and family, and nobody else')`:
    - CAROL's `get` fails;
    - BOB's list without `where('participants','array-contains', BOB)` fails;
    - BOB's list with it and `where('familyId','==', FAMILY)` succeeds.
  - `it('lets the answering parent record delivery and its plan once, together')`:
    - three times inside `[createdAt, answerBy]` succeed;
    - each of these fails: a second write; delivery without the plan; four times; a time after the deadline;
    - delivery 10 minutes ahead of now fails; 4 minutes ahead succeeds (ruling 7).
  - `it('lets the answering parent record opening once')`
  - `it('lets the answering parent count shown reminders one at a time, up to the plan')`
  - `it('lets the proposer give one last chance after the deadline, within a day and the cap')`:
    - each of these fails: before the deadline; `until` more than 24 h after now; `until` after the cap; a client `lastChanceAt`; a second write.
  - `it('lets the proposer close it as no_answer once, only after the deadline')`:
    - each of these fails: `outcome: 'declined'`; before the deadline; a client `closedAt`.
  - `it('refuses each parent the other one\'s fields, a stranger everything, and every delete')`

  `notification-payload.test.js`:
  - add `'request_last_chance'` to the list in `'accepts each of the other client types'`;
  - add ``it('bounds the deadline a request push carries')``: `answerBy: 'x'.repeat(21)` fails; `answerBy: '1787000000000'` succeeds.

- [ ] **Step 2: Run them and see them fail.**
  - Run: `cd firestore-tests && npm test`
  - Expected: the new cases fail with `PERMISSION_DENIED` where they expect success: the collection has no rule and the type is not allowed.

- [ ] **Step 3: Write the rules.**
  - **Read**: `allow get, list: if isAuthenticated() && request.auth.uid in resource.data.participants`.
  - **Create**, by the proposer only:
    - The keys `hasOnly` the create set and `hasAll` the required set: `kind`, `ref`, `familyId`, `proposerUid`, `responderUid`, `participants`, `createdAt`, `answerByMillis`.
    - The id equals `kind + '_' + auth.uid + '_' + ref`, or `'proposal_' + auth.uid + '_' + familyId + '_' + ref`.
    - `proposerUid == auth.uid`.
    - `isLiveFamilyMember(familyId)`; the responder is in `familyId.split('__')` and is not the caller.
    - `participants` has size 2 and holds both parents.
    - `createdAt == request.time`.
    - `answerByMillis` is an int, after `request.time.toMillis()` and at most 31 days after it.
    - `capMillis` is absent or an int after `answerByMillis`.
    - `firstDate` is absent or a 10-character string; `dayCount` is absent or an int from 1 to 366.
    - `ref` is 1–100 characters.
  - **Update**: exactly one of five shapes, each checked with `diff().affectedKeys().hasOnly(...)` and `hasAll(...)`:
    1. **The responder's plan**, written once:
       - keys `deliveredAtMillis`, `remindersPlanned` and `reminderTimesMillis`;
       - `remindersPlanned` from 0 to 3, and `reminderTimesMillis` exactly that long;
       - each of up to three elements, unrolled, is an int between `createdAt.toMillis()` and `answerByMillis`;
       - `deliveredAtMillis` is at or after `createdAt` and at most `request.time` + 5 minutes.
    2. **The responder's opening**: `seenAtMillis` once, at or after `createdAt`, at most `request.time` + 5 minutes.
    3. **A shown reminder**: `remindersShown == before.get('remindersShown', 0) + 1` and `<= remindersPlanned`.
    4. **The proposer's last chance**, given once:
       - no `outcome` yet, and `request.time` is after `answerByMillis`;
       - `lastChanceAt == request.time`;
       - `lastChanceUntilMillis` is after `request.time.toMillis()`, at most 24 h after it, and at most `capMillis` when there is a cap.
    5. **The proposer's closure**, once, after `answerByMillis`: `outcome == 'no_answer'` and `closedAt == request.time`.
  - **Delete**: `allow delete: if false`.
  - **Pushes**: add `'request_last_chance'` to `isClientPushType`. Add `isValidLength(...get('answerBy', ''), 0, 20)` beside the `dayCount` bound.

- [ ] **Step 4: Add the two indexes** to `firestore.indexes.json`, in the shape of the existing `conversations` index.

- [ ] **Step 5: Run the tests and see them pass.**
  Same command. Expected: every suite passes.

- [ ] **Step 6: Commit.**
  `git commit -m "feat(rules): request clocks, the last-chance push and its deadline field"`

---

### Task 5: The clock in Firestore: wire form, data source, repository, jobs, time

**Files:**
- Create: `app/src/main/java/com/coparently/app/data/requests/RequestDeadlineDocument.kt`
- Create: `app/src/main/java/com/coparently/app/data/remote/firebase/FirestoreRequestDeadlineDataSource.kt`
- Create: `app/src/main/java/com/coparently/app/domain/repository/RequestDeadlineRepository.kt`
- Create: `app/src/main/java/com/coparently/app/data/repository/RequestDeadlineRepositoryImpl.kt`
- Create: `app/src/main/java/com/coparently/app/domain/notification/RequestClockScheduler.kt`
- Create: `app/src/main/java/com/coparently/app/data/notification/WorkRequestClockScheduler.kt`
- Create: `app/src/main/java/com/coparently/app/data/requests/RequestTime.kt`
- Create: `app/src/main/java/com/coparently/app/data/requests/RequestClocks.kt`
- Create: `app/src/main/java/com/coparently/app/di/RequestClockModule.kt`
- Create: `app/src/test/java/com/coparently/app/wire/RequestDeadlineWireContract.kt` and register it in `WireContracts.all`
- Create: `app/src/test/resources/wire/request_deadlines/minimal.json`, `newer-build.json`, `unknown-kind.json`
- Create: `app/src/test/resources/wire/current/request_deadlines/create.json`
- Test: `app/src/test/java/com/coparently/app/data/requests/RequestDeadlineDocumentTest.kt`
- Test: `app/src/test/java/com/coparently/app/data/repository/RequestDeadlineRepositoryImplTest.kt`
- Test: `app/src/test/java/com/coparently/app/data/requests/RequestClocksTest.kt`
- Test: `app/src/test/java/com/coparently/app/data/notification/WorkRequestClockSchedulerTest.kt`

**Interfaces:**
- Consumes:
  - Task 1's types;
  - `FirebaseAuthService.getCurrentUser()`;
  - `FieldValue.serverTimestamp()` and `FieldValue.increment(1)`;
  - `ServerTimestampBehavior.ESTIMATE`.
- Produces:

```kotlin
// data/requests/RequestDeadlineDocument.kt — pure, the one definition of the wire form
object RequestDeadlineDocument {
    const val COLLECTION = "request_deadlines"
    // One const per stored field, each named as stored: kind, ref, familyId, proposerUid, responderUid,
    // participants, createdAt, answerByMillis, capMillis, firstDate, dayCount, deliveredAtMillis,
    // remindersPlanned, reminderTimesMillis, seenAtMillis, remindersShown, lastChanceAt,
    // lastChanceUntilMillis, outcome, closedAt. createdAt, lastChanceAt and closedAt are server timestamps.
    data class ServerTimes(val createdAtMillis: Long? = null, val lastChanceAtMillis: Long? = null, val closedAtMillis: Long? = null)
    fun createFields(start: RequestStart, proposerUid: String): Map<String, Any>   // never createdAt; participants sorted
    fun deliveredFields(deliveredAtMillis: Long, reminderTimesMillis: List<Long>): Map<String, Any>
    fun parse(id: String, document: Map<String, Any?>, times: ServerTimes): RequestDeadline?
}

// domain/repository/RequestDeadlineRepository.kt
interface RequestDeadlineRepository {
    fun signedInUid(): String?
    fun observeFamily(myUid: String, familyId: String): Flow<List<RequestDeadline>>
    suspend fun openFor(myUid: String, sinceMillis: Long): List<RequestDeadline>
    suspend fun get(id: String): RequestDeadline?
    suspend fun start(start: RequestStart): RequestDeadline?        // null = not written
    suspend fun markDelivered(clock: RequestDeadline, deliveredAtMillis: Long, reminderTimesMillis: List<Long>): Boolean
    suspend fun markSeen(clock: RequestDeadline, seenAtMillis: Long): Boolean
    suspend fun reportReminderShown(clock: RequestDeadline): Boolean
    suspend fun giveLastChance(clock: RequestDeadline, untilMillis: Long): Boolean
    suspend fun close(clock: RequestDeadline): Boolean
}

// domain/notification/RequestClockScheduler.kt
interface RequestClockScheduler {
    fun scheduleReminder(clockId: String, atMillis: Long)
    fun scheduleDeadline(clockId: String, atMillis: Long)
    fun scheduleEnd(clockId: String, atMillis: Long)
    fun cancelAll()
}

// data/notification/WorkRequestClockScheduler.kt
object RequestClockWork {
    const val TAG = "request_clock"
    const val KEY_CLOCK_ID = "clock_id"; const val KEY_STEP = "step"; const val KEY_AT_MILLIS = "at_millis"
    const val STEP_REMINDER = "reminder"; const val STEP_DEADLINE = "deadline"; const val STEP_END = "end"
    fun uniqueName(step: String, clockId: String, atMillis: Long): String
    // "request_reminder_<id>_<at>", "request_deadline_<id>", "request_end_<id>_<at>"
}
class WorkRequestClockScheduler @Inject constructor(@ApplicationContext context: Context) : RequestClockScheduler

// data/requests/RequestTime.kt
@Singleton
class RequestTime internal constructor(private val now: () -> Long, private val zoneOf: () -> ZoneId, val ticks: Flow<Unit>) {
    @Inject constructor()                 // System.currentTimeMillis, ZoneId.systemDefault, minuteTicks()
    fun nowMillis(): Long
    fun zone(): ZoneId
}
internal fun minuteTicks(): Flow<Unit>    // emits at once, then at each minute boundary

// data/requests/RequestClocks.kt
@Singleton
class RequestClocks @Inject constructor(private val repository: RequestDeadlineRepository, private val time: RequestTime) {
    fun observeFamily(myUid: String, familyId: String): Flow<ClocksAt>   // the listener × the ticks
}
```

- `FirestoreRequestDeadlineDataSource` exposes:
  - `observeFamily`, the listener on `whereArrayContains("participants", myUid).whereEqualTo("familyId", familyId)`. It fails on a listener error, like `observeCustody`.
  - `openFor`: `whereArrayContains("participants", myUid).whereGreaterThanOrEqualTo("answerByMillis", since)`, read once.
  - `get(id)`.
  - `create(id, fields)`: `set()` plus `createdAt = serverTimestamp()`.
  - `update(id, fields)`.
  - `giveLastChance(id, untilMillis)`.
  - `close(id)`.
  - `reportShown(id)`: `remindersShown = increment(1)`.

  It turns server timestamps into `ServerTimes` with `getTimestamp(field, ESTIMATE)`.

- [ ] **Step 1: Write the failing tests.**

  `RequestDeadlineDocumentTest`:
  - ``fun `a new clock is written with only the fields its creator may write`()``:
    - the keys are exactly the create set, without `createdAt`;
    - `participants` is sorted;
    - `capMillis`, `firstDate` and `dayCount` are absent when null;
    - `firstDate` is ISO.
  - ``fun `every field reads back, whatever number type the store hands over`()``: Long, Int and Double all read as Long.
  - ``fun `a clock this build cannot read is skipped`()``: an unknown `kind`, a blank `ref`, or no `answerByMillis` or `responderUid` → `null`.
  - ``fun `an unknown field is ignored and an unknown outcome is not a closure`()``
  - ``fun `the plan is written as one write`()``: `deliveredFields` holds exactly the three keys, `remindersPlanned == times.size`.

  `RequestDeadlineRepositoryImplTest`, with a mocked data source, a recording `RequestClockScheduler` and the auth service:
  - ``fun `start writes the clock under its derived id, then schedules the deadline and the end`()``
  - ``fun `a refused or signed-out start writes nothing else and returns null`()``
  - ``fun `a write still unanswered after ten seconds counts as written`()``: the data source suspends forever; with virtual time the call returns `true` (ruling 14).
  - ``fun `a refused write returns false`()``
  - ``fun `a listener that keeps failing reads as no clocks`()``: after the bounded retries (8, as `OtherFamiliesSignals.bounded`) it emits `emptyList()`.

  `RequestClocksTest`: ``fun `a tick re-reads the same clocks at the new time`()``. Use Turbine, an internal `RequestTime` with a mutable now, and a `MutableSharedFlow<Unit>` for the ticks.

  `WorkRequestClockSchedulerTest`: ``fun `each job has its own unique name, so a moved end does not replace the old one`()``. It tests `RequestClockWork.uniqueName` alone.

- [ ] **Step 2: Write the wire contract and its fixtures.**
  - `RequestDeadlineWireContract`:
    - `collection = "request_deadlines"`;
    - `alwaysWrites` = the seven keys `createFields` always writes: the required set without `createdAt`, which the data source adds;
    - `read` converts `WireTimestamp` values into `ServerTimes`, as `EventVersionWireContract` does, then calls `parse`;
    - `roundTrip = null`: no build rewrites a whole clock; every later write touches named fields;
    - `currentWrites()` returns one case, `"create"`: `createFields` of a swap start from `"uidA"` to `"uidB"`, with `firstDate` `2026-05-16`, `dayCount` 2 and a cap.
  - The hand-written fixtures:
    - `minimal.json`: the required keys and a `createdAt` timestamp; reads every field;
    - `newer-build.json`: every optional field plus an unknown `escalation`; reads the known ones;
    - `unknown-kind.json`: `kind: "handover"`; `skipped`, with the reason "a kind this build does not know".
  - `current/request_deadlines/create.json`:
    - where Gradle runs: `UPDATE_WIRE_FIXTURES=1 ./gradlew testDebugUnitTest --tests '*CurrentWireFixturesTest*' --rerun`;
    - in a cloud session: write it by hand from `createFields`' output. CI's `CurrentWireFixturesTest` is the check.

- [ ] **Step 3: Run the tests and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*RequestDeadline*' --tests '*RequestClocksTest*' --tests '*WorkRequestClockScheduler*' --tests '*Wire*'`
  - Expected: compilation fails.

- [ ] **Step 4: Implement the files listed above.**
  - The repository's writes are `withTimeoutOrNull(10_000) { … }`:
    - a timeout → `true`;
    - an exception (cancellation rethrown) → `false`, with `Log.w`.
  - `observeFamily` retries with the same bounded backoff as `OtherFamiliesSignals.bounded`, then `catch { emit(emptyList()) }`.
  - `start` returns the clock it wrote, with `createdAtMillis = null`. It then calls `scheduler.scheduleDeadline(id, answerBy)` and `scheduler.scheduleEnd(id, RequestClock.endMillis(clock))`.
  - `WorkRequestClockScheduler`:
    - one `OneTimeWorkRequestBuilder<RequestClockWorker>` per job, enqueued with `ExistingWorkPolicy.KEEP`;
    - `setInitialDelay` to the target time, or none when it has passed;
    - tag `RequestClockWork.TAG`; input data `KEY_CLOCK_ID`, `KEY_STEP`, `KEY_AT_MILLIS`;
    - `cancelAll()` is `cancelAllWorkByTag(TAG)`.
  - `RequestClockWorker` is Task 11's. Until then, create it with a `doWork()` that returns `Result.success()`, so this task compiles.
  - `RequestClockModule` binds `RequestDeadlineRepositoryImpl` and `WorkRequestClockScheduler` in `SingletonComponent`.

- [ ] **Step 5: Run the tests and see them pass.**
  Same command. Expected: all pass, the wire suite included.

- [ ] **Step 6: Commit.**
  `git commit -m "feat(requests): request clocks in Firestore, their jobs and their wire contract"`

---

### Task 6: Two chat cards, and the functions that must not push them

**Files:**
- Modify: `app/src/main/java/com/coparently/app/domain/activity/ActivityAnnouncement.kt` (`ActivityKind` + `REQUEST_LAST_CHANCE`, `REQUEST_UNANSWERED`)
- Modify: `app/src/main/java/com/coparently/app/domain/activity/ActivityFallbackText.kt`
- Modify: `app/src/main/java/com/coparently/app/domain/activity/ActivityAnnouncer.kt` (optional `partnerUid`)
- Modify: `app/src/main/java/com/coparently/app/presentation/chat/ActivityCardText.kt` (resources, and a new `subjectOf`)
- Modify: `app/src/main/java/com/coparently/app/presentation/chat/MessagesList.kt` (reads `subjectOf`)
- Modify: every other exhaustive `when` over `ActivityKind`. The compiler lists them.
- Modify: `app/src/main/res/values*/activity_strings.xml` (5 files)
- Modify: `functions/index.js`:
  - `notifyOfChatMessage` skips the two kinds;
  - `deleteAccountDataImpl` deletes the clocks.
- Test: `app/src/test/java/com/coparently/app/domain/activity/ActivityFallbackTextTest.kt`
- Test: `app/src/test/java/com/coparently/app/presentation/chat/ActivityCardTextTest.kt`
- Test: `app/src/test/java/com/coparently/app/domain/activity/ActivityAnnouncerTest.kt` (new)
- Test: `functions/test/chat-notification.test.js`, `functions/test/account-deletion.test.js`

**Interfaces:**
- Consumes: `ConversationKey.of`, `MessageRepository.sendMessage`.
- Produces:

```kotlin
suspend fun announce(
    announcement: ActivityAnnouncement,
    senderName: String = "",
    suppress: Boolean = false,
    partnerUid: String? = null      // the thread of (me, partnerUid); null = the family on screen, as today
)

sealed interface CardSubjectText {             // presentation/chat/ActivityCardText.kt
    data class IsoDate(val iso: String) : CardSubjectText
    data class Res(@StringRes val id: Int) : CardSubjectText
    data class Raw(val text: String) : CardSubjectText
}
fun ActivityCardText.subjectOf(card: ActivityAnnouncement): CardSubjectText
```

- The cards' payloads:

  | Request | `entityType` | `entityId` | `title` |
  | --- | --- | --- | --- |
  | swap | `DAY_SWAP` | the first ISO date | the first ISO date |
  | change | `CHANGE_REQUEST` | the event id | the event title |
  | event | `EVENT` | the event id | the event title |
  | proposal | `CUSTODY_PROPOSAL` | the family id | `""` |

  The existing `ChatCardTarget` therefore routes each card where its request's own cards go.

- [ ] **Step 1: Write the failing tests.**
  - `ActivityFallbackTextTest`: ``fun `the two request cards read as plain text on an older build`()``:
    - `REQUEST_LAST_CHANCE` with title "Swimming" → `"Last chance to answer: Swimming"`;
    - `REQUEST_UNANSWERED` with title `""` → `"No answer"`.
  - `ActivityCardTextTest`:
    - ``fun `a custody card's subject is worded, a swap's is its date, anything else its title`()``: `subjectOf` gives `Res(R.string.activity_subject_custody_schedule)`, `IsoDate("2026-10-16")` and `Raw("Swimming")`.
    - The existing every-kind-has-a-resource test covers the two new kinds once they exist.
  - `ActivityAnnouncerTest`:
    - ``fun `a named co-parent's thread is used whatever family is on screen`()``:
      - the user row's `partnerId = "carol"`;
      - `announce(…, partnerUid = "bob")`;
      - the sent `Message.conversationId == ConversationKey.of(me, "bob")`.
    - ``fun `without a name it is the family on screen, as before`()``
  - `chat-notification.test.js`:
    - `it('pushes nothing for a last-chance or a no-answer card')`: `activity: {kind: 'REQUEST_LAST_CHANCE', …}` and `'REQUEST_UNANSWERED'`, `db._added.length === 0`.
    - `it('still pushes every other card')`: `kind: 'DAY_SWAP_OFFERED'` → one `chat_message`.
  - `account-deletion.test.js`: ``it('deletes every request clock naming the departing parent')``. Follow the file's pattern for `family_settings`.

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*Activity*'` and `cd functions && npm test`.
  - Expected: Kotlin fails to compile; the two mocha cases fail.

- [ ] **Step 3: Implement.**
  - The new kinds' resources:
    - `activity_request_last_chance` "Last chance to answer: %1$s";
    - `activity_request_unanswered` "No answer: %1$s";
    - `activity_subject_custody_schedule` "custody schedule".
  - The fallback prefixes are "Last chance to answer" and "No answer".
  - `MessagesList`'s bubble resolves `subjectOf`, and keeps the day-swap date formatting it already has.
  - `functions/index.js`:
    - add `const SILENT_CARD_KINDS = new Set(['REQUEST_LAST_CHANCE', 'REQUEST_UNANSWERED'])`;
    - `notifyOfChatMessage` returns at its first line when `message.activity && SILENT_CARD_KINDS.has(message.activity.kind)`, before any read;
    - in `deleteAccountDataImpl`: `removed.request_deadlines = await deleteQueryInBatches(db, db.collection('request_deadlines').where('participants', 'array-contains', uid))`.

- [ ] **Step 4: Run the tests and see them pass.**
  - Same commands, then `cd functions && npm run lint` and `node tools/check-invariants.js`.
  - Expected: all pass.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(chat): last-chance and no-answer cards, pushed to nobody"`

---

### Task 7: The last-chance push and the `answerBy` field

**Files:**
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/PushPayload.kt`
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/PushNotifier.kt`:
  - `PUSH_TEXT`;
  - `BodyArgs.ACTOR_AND_ANSWER_BY`;
  - `ANSWER_BY_TYPES`;
  - `compose`.
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/PushRouting.kt`
- Modify: `app/src/main/java/com/coparently/app/utils/LocalizedDates.kt` (`answerByText`)
- Modify: `app/src/main/res/values*/push_strings.xml` (5 files)
- Test: `app/src/test/java/com/coparently/app/data/remote/firebase/PushRoutingTest.kt`
- Test: `app/src/test/java/com/coparently/app/utils/AnswerByTextTest.kt` (new)
- Test: `app/src/androidTest/java/com/coparently/app/data/remote/firebase/PushNotificationTest.kt`

**Interfaces:**
- Produces:

```kotlin
// PushPayload
const val ANSWER_BY = "answerBy"                       // epoch millis as text
const val REQUEST_LAST_CHANCE = "request_last_chance"

// PushNotifier
internal enum class BodyArgs { …, ACTOR_AND_ANSWER_BY }
internal val ANSWER_BY_TYPES: Set<String>              // DAY_SWAP_OFFERED, DAY_SWAP_GROUP_OFFERED, CHANGE_REQUEST_CREATED, CUSTODY_PROPOSAL_PROPOSED

// utils/LocalizedDates.kt
fun answerByText(
    millis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    is24Hour: Boolean = ClockFormat.is24Hour
): String                                              // localizedDate("EEEMMMd") + ", " + shortTime(locale, is24Hour)
```

- Strings:

  | Key | English |
  | --- | --- |
  | `push_request_last_chance_title` | Last chance to answer |
  | `push_request_last_chance_body` | %1$s is waiting for your answer until %2$s. |
  | `push_answer_by` | Answer by %1$s. |

- [ ] **Step 1: Write the failing tests.**
  - `PushRoutingTest`: ``fun `a last chance opens the inbox, on the schedule channel`()``:
    - `destinationOf(REQUEST_LAST_CHANCE) == CHANGE_REQUESTS`;
    - `channelOf(...) == SCHEDULE`.
  - `AnswerByTextTest`:
    - ``fun `a deadline reads its weekday, day, month and the reader's time`()``: 2026-10-13T10:00 Prague, English, 24 h, contains `"13"` and `"10:00"`;
    - ``fun `the 12-hour clock says AM or PM`()``: contains `"10:00"` and `"AM"`.
  - `PushNotificationTest`:
    - `payload()` gains `PushPayload.ANSWER_BY to ANSWER_BY.toString()`;
    - `expectedBody` and `assertCarriesItsNames` get the `ACTOR_AND_ANSWER_BY` branch, which carries the actor and the day of `ANSWER_BY`;
    - `expectedBody` appends `push_answer_by` for the types in `ANSWER_BY_TYPES`.
    - New: ``fun anOfferWithADeadlineSaysWhenToAnswerBy()``: `day_swap_offered` with and without `ANSWER_BY`. The body ends with the sentence, or equals today's.
    - New: ``fun aLastChanceWithoutAReadableTimePostsNothing()``

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*PushRoutingTest*' --tests '*AnswerByTextTest*'`
  - Expected: compilation fails.

- [ ] **Step 3: Implement.**
  - `compose` appends `" " + getString(push_answer_by, answerByText(...))` for an `ANSWER_BY_TYPES` push whose `ANSWER_BY` parses. Without it, the body is unchanged.
  - `BodyArgs.ACTOR_AND_ANSWER_BY` composes `(actor, answerByText(answerBy))`. An unparseable value → `null`, so nothing posts.
  - The 12/24-hour flag in `PushNotifier` is `DateFormat.is24HourFormat(context)`, as `EventReminderScheduler` does.
  - The four-way agreement: `PushPayload`, `isClientPushType` (Task 4), `PUSH_TEXT` and the five `push_strings.xml`.

- [ ] **Step 4: Run the tests and see them pass.**
  - Same command, then `node tools/check-invariants.js`.
  - Expected: all pass; the four-way check names no type.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(push): the last-chance push, and the deadline on a request's first push"`

---

### Task 8: Swaps and proposals start their clocks

**Files:**
- Modify: `app/src/main/java/com/coparently/app/domain/custody/DayOverrideTransition.kt` (`offer` + `groupId: String? = null`, under its existing suppression)
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/FirestoreCustodyDataSource.kt` (`transact`)
- Modify: `app/src/main/java/com/coparently/app/data/repository/CustodyModelRepository.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/CalendarViewModel.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/custody/ChildScheduleForm.kt`:
  - `submitFor` + `answerBy`;
  - new `candidateModel`.
- Modify: `app/src/main/java/com/coparently/app/presentation/custody/CustodySetupViewModel.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/custody/SeasonalScheduleViewModel.kt`
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/EmulatorParent.kt` (construction only; Task 15 uses it)
- Test: `DayOverrideTransitionTest`, `CustodyModelRepositoryTest`, `CalendarViewModelTest`, `CustodySetupViewModelTest`, `SeasonalScheduleViewModelTest`
- Test: `app/src/test/java/com/coparently/app/presentation/custody/CandidateModelTest.kt` (new)

**Interfaces:**
- Consumes:
  - `AnswerBy.resolve`;
  - `RequestCap.forSwap` and `RequestCap.forProposal`;
  - `RequestDeadlineRepository.start` (Task 5);
  - `PushPayload.ANSWER_BY` (Task 7).
- Produces:

```kotlin
// CustodyModelRepository — the primary constructor gains `requestDeadlines: RequestDeadlineRepository`
// (@Suppress("LongParameterList") with the reason "the request clock joins the collaborators (phase 3a)");
// the @Inject constructor passes it on.
suspend fun applyDayOverrides(date: String, transform: …, answerBy: AnswerByPick? = null): Result<Map<String, DayOverride>>
@Suppress("ReturnCount") // one early refusal per reason; see the KDoc
suspend fun applyDayOverridesForDates(dates: List<String>, transform: …, answerBy: AnswerByPick? = null): Result<Map<String, DayOverride>>
suspend fun createWeekOnWeekOff(startDate, momFirst, contactWindows, planCitation, answerBy: AnswerByPick? = null): PatternSubmission
suspend fun createEveryOtherWeekend(…, planCitation, answerBy: AnswerByPick? = null): PatternSubmission   // @Suppress("LongParameterList") — the pattern's fields, then how it is sent
suspend fun createTwoTwoThree(…, answerBy: AnswerByPick? = null): PatternSubmission
suspend fun createThreeFourFourThree(…, answerBy: AnswerByPick? = null): PatternSubmission
suspend fun createCustom(…, planCitation, answerBy: AnswerByPick? = null): PatternSubmission           // @Suppress, same reason
suspend fun submitPattern(model, planCitation = null, answerBy: AnswerByPick? = null): PatternSubmission
suspend fun submitChildOverride(childId, override, answerBy: AnswerByPick? = null): PatternSubmission?
suspend fun submitSeasonalLayers(layers, planCitation = null, answerBy: AnswerByPick? = null): PatternSubmission?
/** Withdraws this parent's proposal in [familyId] when it is still the one made at [proposedAt]. */
suspend fun withdrawUnanswered(familyId: String, proposedAt: String): Boolean

// FirestoreCustodyDataSource
suspend fun transact(documentId: String, participants: List<String>, transform: (SharedCustody?) -> SharedCustody?): SharedCustody?

// CalendarViewModel
fun offerDaySwap(date: LocalDate, toParent: String, note: String? = null, answerBy: AnswerByPick? = null)
fun offerDaySwapForDates(dates: Collection<LocalDate>, toParentFor: (LocalDate) -> String?, note: String? = null, answerBy: AnswerByPick? = null)

// presentation/custody
internal suspend fun CustodyModelRepository.submitFor(state: CustodySetupUiState, citation: String?, followFamily: Boolean, answerBy: AnswerByPick?): PatternSubmission
internal fun CustodySetupUiState.candidateModel(active: CustodyModel?): CustodyModel?
// CustodySetupViewModel
val answerByCap: StateFlow<Long?>                 // RequestCap.forProposal(active, candidateModel(active), today, zone)
val proposes: StateFlow<Boolean>                  // a co-parent and an agreed schedule exist: a save will propose
fun save(followFamily: Boolean = false, answerBy: AnswerByPick? = null, onSuccess: () -> Unit = {})
// SeasonalScheduleViewModel
fun saveLayer(draft: SeasonalLayerDraft, editingId: String?, citePlan: Boolean = false, answerBy: AnswerByPick? = null)
fun answerByCap(draft: SeasonalLayerDraft, editingId: String?): Long?
val proposes: StateFlow<Boolean>
```

- [ ] **Step 1: Write the failing tests.**
  - `DayOverrideTransitionTest`: ``fun `a single-day offer carries its group id`()``.
  - `CalendarViewModelTest`: ``fun `a single-day offer is its own group and passes the deadline on`()``. Verify `applyDayOverrides(iso, any(), pick)`; the transform's output carries a non-null `groupId`.
  - `CustodyModelRepositoryTest` (a mocked `RequestDeadlineRepository`; `ZoneId.systemDefault()` read where the code reads it):
    - ``fun `an offer with a deadline starts its clock after the swap is written and before the push, which says when`()``:
      - `coVerifyOrder { setCustody; start(match { kind == SWAP && ref == groupId && dayCount == 1 && firstDate == date }); queueNotificationForUser(partner, match { it[ANSWER_BY] == clock.answerByMillis.toString() }) }`.
    - ``fun `a run starts one clock for the days that landed`()``: the 3-day run with the second write refused → one `start` with `dayCount == 1`.
    - ``fun `an offer for today goes out without a clock`()`` (`TOO_SOON`): `start` is never called; no `ANSWER_BY` on the push.
    - ``fun `answering a swap starts no clock`()``
    - ``fun `a refused clock still sends the offer, without answerBy`()`` (Review Focus 3).
    - ``fun `a proposal starts its clock under its proposedAt, capped at the first changed day`()``
    - ``fun `a pattern activated on an empty family starts no clock`()``
    - ``fun `withdrawUnanswered withdraws only the same pending proposal of this parent`()``. Apply the captured `transact` lambda to four documents; only the first is written (Review Focus 5):
      - the same `proposedAt` → withdrawn;
      - another `proposedAt` → `null`;
      - no proposal → `null`;
      - the co-parent's proposal → `null`.
    - ``fun `withdrawUnanswered acts on the clock's family while another is on screen, and not on an ended one`()``
  - `CustodySetupViewModelTest`:
    - ``fun `save passes the deadline on`()``
    - ``fun `the cap preview is the first day the edited pattern changes`()``
    - ``fun `a child's schedule's cap is the first day that child's days change`()``
    - ``fun `with no co-parent or no agreed schedule a save does not propose`()``
  - `SeasonalScheduleViewModelTest`:
    - ``fun `saving a layer passes the deadline, deleting one sends the default`()`` (ruling 9)
    - ``fun `a layer's cap is its first day when that day changes`()``
  - `CandidateModelTest`: ``fun `the candidate is the pattern a save would send`()``. For each `CustodyModelType`, compare `patternDays`, `momDayIndices`, `startDate` and `contactWindows` with the `CustodyModel` factory the matching `createXxx` calls, and check the active model's layers and overrides carry over.

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*DayOverrideTransitionTest*' --tests '*CustodyModelRepositoryTest*' --tests '*CalendarViewModelTest*' --tests '*CustodySetupViewModelTest*' --tests '*SeasonalScheduleViewModelTest*' --tests '*CandidateModelTest*'`
  - Expected: compilation fails.

- [ ] **Step 3: Implement.**
  - **Swaps.**
    - The single-day path generates a `UUID` group id, as the range path does.
    - After the last write, a run with `answerBy` resolves its deadline from `RequestCap.forSwap(changed dates)`. A non-null result → `start(RequestStart(SWAP, groupId, pair.documentId, partner, deadline, cap, firstDate, dayCount = changed.size))`.
    - `announceSwap` and `notifyPartnerOfSwap` take `answerByMillis: Long?`. The push gets `ANSWER_BY` only when a clock was written.
  - **Proposals.**
    - `submit` takes the `answerBy` that `submitNew` and the three public submits pass on.
    - After a successful `setCustody` of a proposal, the deadline comes from `RequestCap.forProposal(existing.model, model, today, zone)`. A non-null deadline → `start(PROPOSAL, proposed.proposal.proposedAt, pair.documentId, …)`.
    - Then `announceProposal`, then `notifyPartnerOfProposal(pair, "proposed", answerByMillis)`.
  - **`withdrawUnanswered(familyId, proposedAt)`.**
    - Read the families once (`currentFamilies()`). Return `false` unless the signed-in parent is in `familyId` and the other parent is still one of their co-parents.
    - Run `transact` with a transform that answers `null` unless `proposal?.proposedAt == proposedAt && proposal.proposedBy == myUid`. When they match, it returns `CustodyProposalTransition.withdraw(current, myUid).getOrNull()`.
    - Then `mirrorIntoRoom(documentId, written)`.
    - It never calls `currentPair()`.
  - **`transact`** runs `firestore.runTransaction`: `get`, parse, transform, and `set(toDocument(sorted participants))` when the transform answers non-null.
  - **`candidateModel`** builds with the same factories `createXxx` call. It canonicalises the windows as `withWindows` does and carries the active model's layers and overrides. For a child scope, it carries the active model with that child's override replaced.

- [ ] **Step 4: Run the tests and see them pass.**
  Same command. Expected: all pass, the existing ones in these classes included.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(custody): day swaps and proposals start their deadline clocks"`

---

### Task 9: Change requests and events start their clocks

**Files:**
- Modify: `app/src/main/java/com/coparently/app/domain/repository/ChangeRequestRepository.kt`
- Modify: `app/src/main/java/com/coparently/app/data/repository/ChangeRequestRepositoryImpl.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestChangeViewModel.kt`
- Modify: `app/src/main/java/com/coparently/app/domain/usecase/CreateEventUseCase.kt`
- Modify: `app/src/main/java/com/coparently/app/di/UseCaseModule.kt` (the provider: + parameter, `@Suppress("LongParameterList") // one binding per collaborator of the use case`)
- Modify: `app/src/main/java/com/coparently/app/data/school/SchoolEventWriter.kt` (constructor + `requestDeadlines`, passed to the use case it builds)
- Modify: `app/src/main/java/com/coparently/app/presentation/event/EventViewModel.kt` (`createEvent` only; the constructor is untouched)
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/EmulatorParent.kt` (construction)
- Test: `ChangeRequestRepositoryImplTest`, `RequestChangeViewModelTest`, `EventViewModelTest`
- Test: `app/src/test/java/com/coparently/app/domain/usecase/CreateEventUseCaseTest.kt` (new)

**Interfaces:**
- Consumes:
  - `AnswerBy.resolve`;
  - `RequestCap.forChange` and `RequestCap.forEvent`;
  - `RequestDeadlineRepository.start`;
  - `PushPayload.ANSWER_BY`;
  - `EventRepository.getEventById(id)`.
- Produces:

```kotlin
// ChangeRequestRepository
suspend fun createChangeRequest(request: ChangeRequest, answerBy: AnswerByPick? = null)
// RequestChangeViewModel
fun submit(event: Event, proposedStart: LocalDateTime, proposedEnd: LocalDateTime?, note: String?, answerBy: AnswerByPick? = null)
// CreateEventUseCase — constructor + requestDeadlines: RequestDeadlineRepository
suspend operator fun invoke(event: Event, announce: Boolean = true, answerBy: AnswerByPick? = null): Result<Event>
// EventViewModel
fun createEvent(event: Event, answerBy: AnswerByPick? = null)
```

- [ ] **Step 1: Write the failing tests.**
  - `ChangeRequestRepositoryImplTest`:
    - ``fun `a request with a deadline starts its clock after it is published and before the push, which says when`()``. The order is `setChangeRequest`, then `start(kind CHANGE, ref id, responder requestedTo)`, then the push with `ANSWER_BY`.
    - ``fun `a request flushed from the outbox later goes without a clock`()`` (ruling 8)
    - ``fun `an answer starts no clock`()``
  - `RequestChangeViewModelTest`: ``fun `submit passes the deadline on`()``.
  - `CreateEventUseCaseTest`, with a mocked repository and `RequestDeadlineRepository`:
    - ``fun `an event that waits for the co-parent gets a clock in its own family`()``:
      - `getEventById` returns it `PENDING` with `familyId "a__b"` and creator `"a"`;
      - `start(match { kind == EVENT && ref == id && responderUid == "b" && familyId == "a__b" })`.
    - ``fun `an event that does not wait, or no deadline chosen, gets no clock`()``
    - ``fun `a clock that cannot be written does not fail the save`()``
  - `EventViewModelTest`: ``fun `createEvent passes the deadline to the use case`()``.

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*ChangeRequestRepositoryImplTest*' --tests '*RequestChangeViewModelTest*' --tests '*CreateEventUseCaseTest*' --tests '*EventViewModelTest*'`
  - Expected: compilation fails.

- [ ] **Step 3: Implement.**
  - `publish` takes `answerBy` only from `createChangeRequest`. `flushOutbox` passes `null`.
  - The deadline comes from `RequestCap.forChange(currentStart, proposedStart, now, zone)`.
  - The clock is started after the row is marked synced and before `notifyCounterparty(..., answerByMillis)`. Its family is `request.familyId`.
  - The use case reads the stored event back. It starts a clock only when all of these hold:
    - `acceptance == PENDING`;
    - the event has a non-blank `familyId`;
    - the creator is one of the family's two uids.

    The responder is the family's other uid. A failure is logged, never returned.

- [ ] **Step 4: Run the tests and see them pass.**
  Same command. Expected: all pass.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(requests): change requests and events awaiting an answer start their clocks"`

---

### Task 10: The inbox places each request by its clock

**Files:**
- Modify: `app/src/main/java/com/coparently/app/domain/changerequests/RequestInbox.kt`
- Modify: `app/src/main/java/com/coparently/app/domain/changerequests/RequestHistory.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestsSource.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/InboxItems.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestHistoryGroup.kt` (the `NO_ANSWER` chip; Task 13 adds its meta line)
- Create: `app/src/main/res/values*/request_deadline_strings.xml` (5 files, starting with `request_clock_no_answer` "No answer"; Tasks 11–13 add to them)
- Test: `RequestInboxTest`, `RequestHistoryTest`, `RequestsSourceTest`, `InboxItemsTest`

**Interfaces:**
- Consumes:
  - `ClocksAt`, `TimedRequest`, `RequestDeadlineId`, `RequestClock` (Task 1);
  - `RequestClocks.observeFamily` (Task 5).
- Produces:

```kotlin
// RequestInbox — build gains a seventh parameter under its existing suppression
fun build(scope, changeRequests, events, overrides, shared, today, clocks: ClocksAt = ClocksAt()): RequestInbox
data class RequestInbox(
    val waitingOnMe: Waiting = Waiting(), val waitingOnThem: Waiting = Waiting(), val history: List<HistoryEntry> = emptyList(),
    val timed: Map<String, TimedRequest> = emptyMap(),        // by clock id, every request shown with a clock
    val unansweredSwapDates: Set<LocalDate> = emptySet()      // pending offers whose clock says no answer
) { fun timedFor(clockId: String?): TimedRequest? }

// RequestHistory
enum class RequestOutcome { ACCEPTED, DECLINED, WITHDRAWN, NO_ANSWER }
data class HistoryEntry(…, val clock: RequestDeadline? = null)   // the clock of a NO_ANSWER entry, for its meta line
@Suppress("LongParameterList") // the four sources, the scope, and the clocks that close them
fun build(scope, changeRequests, events, overrides, decision, clocks: ClocksAt = ClocksAt()): List<HistoryEntry>

// RequestsSource — constructor + requestClocks: RequestClocks; Ready gains:
data class Ready(…, val clocks: ClocksAt = ClocksAt()) : RequestsState

// InboxItems — each item kind gains `val timed: TimedRequest? = null`
```

- **Placement**, for a pending record whose clock governs it (Task 1):

  | State | Answering parent | Proposer |
  | --- | --- | --- |
  | `OPEN` | `waitingOnMe` | `waitingOnThem` |
  | `NO_ANSWER_LAST_CHANCE_POSSIBLE` | `waitingOnThem` | `waitingOnThem` |
  | `LAST_CHANCE` | `waitingOnMe` | `waitingOnThem` |
  | `NO_ANSWER` | History, `NO_ANSWER` | History, `NO_ANSWER` |

  A record with no clock, or whose clock does not govern it, is placed as today.
- **History rows for `NO_ANSWER`**:
  - Each takes the same key its record's own row would (`cr_`, `swap_`, `ev_`, `custody_`).
  - `askedBy` is the proposer and `decidedBy` the responder.
  - `decidedAt` is `closedAt`, else `endMillis`, in the device zone.
  - A proposal's or a replaced swap's row comes from the clock alone. A swap uses `firstDate` and `dayCount`; a proposal is `CustodySchedule`.
  - A change request or event with no record left is skipped (ruling 13).
  - An answered record's own row wins over its clock.

- [ ] **Step 1: Write the failing tests.**
  - `RequestInboxTest`:
    - ``fun `an open request waits where it always did`()``
    - ``fun `past the deadline with the button still offered, it waits on the proposer on both phones`()``
    - ``fun `under a last chance it waits on the answering parent again`()``
    - ``fun `after the end it is "No answer" in History on both phones`()``
    - ``fun `a request without a clock behaves as before`()``
    - ``fun `a clock that does not name the record's author is ignored`()``
    - ``fun `a clock of the family not on screen changes nothing here`()`` (Review Focus 1)
    - ``fun `the days of an unanswered offer are listed for the calendar`()``
    - ``fun `Home's count drops when a request stops waiting on this parent`()``: `waitingOnMe.count`.
  - `RequestHistoryTest`:
    - ``fun `"No answer" is its own outcome, dated by its closure`()``
    - ``fun `a late answer shows as the answer, not as "No answer"`()``
    - ``fun `a proposal withdrawn at its end stays in History from its clock`()``
    - ``fun `a swap offered again keeps its "No answer" row from the clock's first day and count`()`` (Review Focus 4)
    - ``fun `an orphaned clock without an outcome shows nothing`()``
    - ``fun `a "No answer" whose change request is gone is left out`()``
  - `RequestsSourceTest`:
    - ``fun `the family's clocks and the minute tick reach the inbox`()``
    - ``fun `a tick moves a request past its deadline without any document changing`()``
  - `InboxItemsTest`: ``fun `each item carries its clock`()``.

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*RequestInboxTest*' --tests '*RequestHistoryTest*' --tests '*RequestsSourceTest*' --tests '*InboxItemsTest*'`
  - Expected: compilation fails.

- [ ] **Step 3: Implement.**
  - `RequestsSource.readyFor` combines four flows: requests, events, `observeSharedOf(familyId)` and `requestClocks.observeFamily(scope.myUid, scope.familyId)`.
  - Every exhaustive `when` over `RequestOutcome` gets the `NO_ANSWER` branch. In `RequestHistoryGroup` that branch is an outlined neutral chip reading `request_clock_no_answer`: `onSurfaceVariant` text, an `outline` border, never the error colour.

- [ ] **Step 4: Run the tests and see them pass.**
  Same command. Expected: all pass.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(requests): the inbox places each request by its clock, and \"No answer\" is an outcome"`

---

### Task 11: The phones' background work

**Files:**
- Create: `app/src/main/java/com/coparently/app/data/requests/RequestRecords.kt`
- Create: `app/src/main/java/com/coparently/app/data/requests/RequestNotices.kt`
- Create: `app/src/main/java/com/coparently/app/data/requests/RequestTelling.kt`
- Create: `app/src/main/java/com/coparently/app/data/requests/RequestClockSteps.kt`
- Create: `app/src/main/java/com/coparently/app/data/requests/RequestClockKeeper.kt`
- Modify: `app/src/main/java/com/coparently/app/data/requests/RequestClockWorker.kt` (from Task 5's stub)
- Modify: `app/src/main/java/com/coparently/app/data/requests/RequestClocks.kt` (+ `keeper`). While the app runs, a clock the listener brings in is delivered at once (spec §5.3).
- Modify: `app/src/main/java/com/coparently/app/data/sync/SyncService.kt` (+ `requestClockKeeper`, under its existing suppression)
- Modify the three places that call `reminderScheduler.cancelAll()`. Each also cancels the request jobs:
  - `AuthStateViewModel`: its suppression comment counts eight collaborators;
  - `AccountSwitchGuard`;
  - `AccountDeletionService`.
- Modify: `app/src/main/res/values*/request_deadline_strings.xml` (5 files, from Task 10)
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/EmulatorParent.kt` (construction; a recording scheduler)
- Test: `app/src/test/java/com/coparently/app/data/requests/RequestRecordsTest.kt`
- Test: `app/src/test/java/com/coparently/app/data/requests/RequestClockStepsTest.kt`
- Test: `app/src/test/java/com/coparently/app/data/requests/RequestClockKeeperTest.kt`
- Test: `RequestClocksTest`, `SyncServiceTest`

**Interfaces:**
- Consumes:
  - `ReminderPlan` (Task 3);
  - the repository, scheduler and time (Task 5);
  - `ActivityAnnouncer.announce(…, partnerUid)` and the new kinds (Task 6);
  - `PushPayload.REQUEST_LAST_CHANCE` and `ANSWER_BY` (Task 7);
  - `CustodyModelRepository.withdrawUnanswered` (Task 8);
  - `PairingRepository.observeCoParentProfile`;
  - `PushChannel.SCHEDULE`, `PushDestination.CHANGE_REQUESTS`, `PushNotifier.createChannels`.
- Produces:

```kotlin
enum class RecordStatus { PENDING, ANSWERED, GONE, UNKNOWN }        // GONE also when the family has ended
data class CardSubject(val entityType: ActivityEntityType, val entityId: String, val title: String)
data class RecordLook(val status: RecordStatus, val card: CardSubject?)
@Singleton class RequestRecords @Inject constructor(changeRequestRepository, eventRepository, custodyDataSource: FirestoreCustodyDataSource,
                                                    custodyModelRepository, userRepository) {
    suspend fun look(clock: RequestDeadline): RecordLook
    suspend fun withdrawUnansweredProposal(clock: RequestDeadline): Boolean
}
@Singleton class RequestNotices @Inject constructor(@ApplicationContext context: Context, pairingRepository: PairingRepository) {
    suspend fun remind(clock: RequestDeadline)
    suspend fun deadlinePassed(clock: RequestDeadline)
    suspend fun noAnswer(clock: RequestDeadline)
}
@Singleton class RequestTelling @Inject constructor(activityAnnouncer: ActivityAnnouncer, fcmService: FcmService, authService: FirebaseAuthService) {
    suspend fun lastChance(clock: RequestDeadline, card: CardSubject, untilMillis: Long)   // the push, then the card
    suspend fun unanswered(clock: RequestDeadline, card: CardSubject)                      // the card only
}
enum class StepResult { DONE, RETRY }
@Singleton class RequestClockSteps @Inject constructor(repository: RequestDeadlineRepository, records: RequestRecords,
    notices: RequestNotices, telling: RequestTelling, scheduler: RequestClockScheduler, time: RequestTime) {
    suspend fun reminder(clockId: String, plannedAtMillis: Long): StepResult
    suspend fun deadline(clockId: String): StepResult
    suspend fun end(clockId: String): StepResult
}
@Singleton class RequestClockKeeper internal constructor(repository: RequestDeadlineRepository, steps: RequestClockSteps,
    scheduler: RequestClockScheduler, time: RequestTime, scope: CoroutineScope) {
    @Inject constructor(…)                                                  // the first four, with a process-lifetime scope
    suspend fun sweep(myUid: String, coParents: Set<String>)
    fun deliverSoon(myUid: String, clocks: Collection<RequestDeadline>)     // on `scope`, at most one attempt per clock id at a time
}
// RequestClocks — constructor + keeper; observeFamily hands each emission's clocks to keeper.deliverSoon
@HiltWorker class RequestClockWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters,
    private val steps: RequestClockSteps) : CoroutineWorker(context, params)   // RETRY → Result.retry() up to 3 attempts
```

- **Notices** (`request_deadline_strings.xml`):

  | Key | English |
  | --- | --- |
  | `request_notice_reminder_title` | Waiting for your answer |
  | `request_notice_reminder_body` | %1$s is waiting for your answer until %2$s. |
  | `request_notice_deadline_title` | %1$s didn't answer by the deadline |
  | `request_notice_deadline_body` | Nothing has changed in the calendar. You can give a last chance. |
  | `request_notice_no_answer_title` | No answer from %1$s |
  | `request_notice_no_answer_body` | The request is closed as “No answer”. Nothing has changed in the calendar. |

  - The notices post on `PushChannel.SCHEDULE.id`, calling `PushNotifier(context).createChannels()` first.
  - They check `POST_NOTIFICATIONS` as `ReminderWorker` does.
  - A tap opens the launcher intent with `PushDestination.EXTRA = CHANGE_REQUESTS.key` and `PushPayload.FAMILY_ID = clock.familyId`.
  - The id and the request code are hashes of the clock id and the step.
  - A name comes from `observeCoParentProfile(uid).first()` within 3 seconds, else `push_actor_fallback`.
- **The last-chance push** is addressed to the responder, with this payload:
  - `TYPE = request_last_chance`;
  - `ACTOR` = the signed-in parent's display name;
  - `ANSWER_BY = untilMillis`;
  - `FAMILY_ID = clock.familyId`.

- [ ] **Step 1: Write the failing tests.**

  `RequestRecordsTest`:
  - ``fun `each kind reads pending, answered or gone from its own record`()``:
    - a change request by id;
    - an event's acceptance;
    - a swap group by `groupId` in the family's custody document;
    - a proposal by `proposedAt`.
  - ``fun `a family the parent has left reads as gone`()``
  - ``fun `an unreadable custody document reads as unknown`()``
  - ``fun `the card names what the request's own cards name`()``: the table in Task 6.

  `RequestClockStepsTest`, with fakes for all six collaborators:
  - Reminders:
    - ``fun `a reminder posts and counts while the request is open`()``
    - ``fun `a reminder posts nothing for an answered, past-deadline or ended-family request, or another account's clock`()``
    - ``fun `a reminder more than half an hour late posts nothing and counts nothing`()`` (ruling 6)
  - The deadline:
    - ``fun `at the deadline the proposer is told once and the end is scheduled`()``
    - ``fun `at the deadline an answered request tells nobody`()``
    - ``fun `with no last chance possible the deadline goes straight to the end`()``
  - The end:
    - ``fun `the end closes, tells, posts its card to the clock's family and withdraws a proposal`()`` (Review Focus 1)
    - ``fun `the end leaves an answered request open`()`` (Review Focus 5)
    - ``fun `the end retries when the record cannot be read`()``
    - ``fun `a closed proposal still pending is withdrawn on the next try`()``
    - ``fun `an end that fires early reschedules itself`()``

  `RequestClockKeeperTest`:
  - ``fun `the answering phone plans its reminders in its own zone and records delivery once`()``
  - ``fun `a request received after its deadline is recorded as delivered with no reminders`()``
  - ``fun `deliverSoon tries each clock once while an attempt is running, and again after one failed`()``
  - ``fun `every planned reminder still ahead is scheduled`()``
  - ``fun `the proposer's deadline and end are scheduled, and an end already due runs now`()``
  - ``fun `a clock of a family this parent is no longer in is left alone`()``
  - ``fun `a clock whose id does not match its fields is left alone`()``

  `RequestClocksTest`: ``fun `an undelivered clock addressed to this parent is handed to the keeper`()``.

  `SyncServiceTest`:
  - ``fun `a full sync sweeps the request clocks with the account's co-parents`()``
  - ``fun `a failed sweep does not fail the sync`()``

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*RequestRecordsTest*' --tests '*RequestClockStepsTest*' --tests '*RequestClockKeeperTest*' --tests '*SyncServiceTest*'`
  - Expected: compilation fails.

- [ ] **Step 3: Implement.** Follow spec §4.3–§4.5 and §5.3, and rulings 6 and 14.
  - **`reminder`**: answering parent only, while `OPEN`, the record `PENDING`, and not late. Then `notices.remind`, then `reportReminderShown`.
  - **`deadline`**: proposer only.
    - In `NO_ANSWER_LAST_CHANCE_POSSIBLE` with the record pending: `notices.deadlinePassed`, then `scheduleEnd(endMillis)`.
    - In `NO_ANSWER`: run `end`.
    - In `OPEN` (it fired early): reschedule.
  - **`end`**: proposer only.
    - If the clock already has an outcome and it is a proposal still pending: retry `withdrawUnansweredProposal` and stop.
    - If the state is not yet `NO_ANSWER`: `scheduleEnd(endMillis)`.
    - If the record is `UNKNOWN`: `RETRY`. If it is not `PENDING`: `DONE`.
    - Otherwise, in the spec's order:
      1. `close` (`false` → `RETRY`);
      2. `notices.noAnswer`;
      3. `telling.unanswered`;
      4. for a proposal, `withdrawUnansweredProposal`.
  - **`sweep`** reads `openFor(myUid, now − 4 days)`. It keeps a clock only when its id equals `RequestDeadlineId.of(...)` of its own fields and its other uid is in `coParents`.
    - As answering parent: an undelivered clock with no outcome is delivered:
      - `ReminderPlan.plan(createdAt ?: now, answerBy, now, zone)`, which is empty once the deadline has passed;
      - then `markDelivered`;
      - then `scheduleReminder` for each time.

      A delivered clock re-schedules its future times.
    - `deliverSoon` runs the same delivery for the clocks it is handed. It keeps an in-memory set of the ids in flight, so the inbox, Home and the calendar collecting one listener start one write.
    - As proposer, while open: `scheduleDeadline` while `now < answerBy`; then the end is run now when it is due, else `scheduleEnd`. A closed proposal is handed to `steps.end`, so a failed withdrawal is retried.
  - **`SyncService.performFullSync`** calls `requestClockKeeper.sweep(uid, coParentsOf(uid, row))` after `changeRequestRepository.flushOutbox()`, inside `runCatching`.
  - **Sign-out, an account switch and deletion** call `requestClockScheduler.cancelAll()` beside `reminderScheduler.cancelAll()`.

- [ ] **Step 4: Run the tests and see them pass.**
  - Same command, then `node tools/check-invariants.js`.
  - Expected: all pass.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(requests): reminders, the deadline and the end, run by the two phones"`

---

### Task 12: The deadline block in the four forms

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/common/AnswerByField.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/components/DaySwapSheet.kt`:
  - the signature changes;
  - split the body until `DaySwapSheet` itself is under 60 lines, because its baselined LongMethod entry no longer matches.
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/CalendarScreen.kt` (`onOffer` passes the pick)
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestChangeScreen.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/event/AddEditEventScreen.kt`. The body changes, the signature does not.
- Modify: `app/src/main/java/com/coparently/app/presentation/custody/CustodySetupScreen.kt`
- Modify: the seasonal layer editor, the composable that calls `saveLayer`
- Modify: `app/src/main/res/values*/request_deadline_strings.xml` (5 files)
- Test: `app/src/test/java/com/coparently/app/screenshots/RequestClockScreenshots.kt` (new, `ScreenshotVariants.TEXT_HEAVY`)

**Interfaces:**
- Consumes:
  - `AnswerBy`, `AnswerByOffer`, `AnswerByPick` and `RequestCap` (Task 2);
  - `ReminderPlan.countFor` (Task 3);
  - `answerByText` (Task 7);
  - the ViewModel signatures of Tasks 8 and 9;
  - `descriptionReachesCoParent(...)`;
  - `LocalDatePickerDialog`.
- Produces:

```kotlin
@Composable fun rememberAnswerByOffer(capMillis: Long?): AnswerByOffer          // re-offered each minute
val AnswerByPickSaver: Saver<AnswerByPick, String>                              // through AnswerByPick.encode/decode
@Composable fun AnswerByField(
    offer: AnswerByOffer,
    selected: AnswerByPick,
    onSelect: (AnswerByPick) -> Unit,
    coParentName: String,
    modifier: Modifier = Modifier
)
// DaySwapSheet
onOffer: (dates: List<LocalDate>, note: String?, answerBy: AnswerByPick) -> Unit
```

- **Where the block appears:**

  | Form | When | Cap |
  | --- | --- | --- |
  | `DaySwapSheet` | always | `RequestCap.forSwap(offerable)` |
  | `RequestChangeScreen` | always | `RequestCap.forChange(event.startDateTime, proposedStart, now, zone)` |
  | `AddEditEventScreen` | while `descriptionReachesCoParent(...)` is true | `RequestCap.forEvent(start, now, zone)` |
  | `CustodySetupScreen`, the layer editor | while the ViewModel's `proposes` | the ViewModel's `answerByCap` |

  - Each form keeps `rememberSaveable(stateSaver = AnswerByPickSaver) { mutableStateOf(AnswerByPick.DEFAULT) }`.
  - The block sits between the form's own content and its send button.
  - A form passes its pick to its ViewModel on send.
  - A form that hides the block passes `null`.
- **The block** (spec §6.1):
  - **Choices**: the title, then a `FlowRow` of `FilterChip`s.
    - Presets read "Tomorrow", "3 days", "A week". A compressed one reads "By <time>".
    - A chip is selected when its millis equal `resolve(selected)`.
    - "Pick a date…" opens `LocalDatePickerDialog`. A picked date shows as a selected chip "By <time>".
  - Under the choices, two helper lines:
    - `answer_by_reminders` (plural on `ReminderPlan.countFor(selected − now)`);
    - `answer_by_outcome`.
  - **Fixed**: one line, `answer_by_fixed`, then `answer_by_reminders` and `answer_by_outcome_short`.
  - **Too soon**: `answer_by_too_soon` only.

  | Key | English |
  | --- | --- |
  | `answer_by_title` | Answer by |
  | `answer_by_tomorrow` | Tomorrow |
  | `answer_by_three_days` | 3 days |
  | `answer_by_week` | A week |
  | `answer_by_pick_date` | Pick a date… |
  | `answer_by_compressed` | By %1$s |
  | `answer_by_reminders` (plurals) | By %1$s. %2$s gets up to %3$d reminder, none at night. / …%3$d reminders, none at night. |
  | `answer_by_outcome` | No answer by then — you'll be told and can give a last chance of 24 hours. Nothing changes in the calendar without an answer. |
  | `answer_by_outcome_short` | No answer by then — you'll be told and can give a shorter last chance. Nothing changes in the calendar without an answer. |
  | `answer_by_fixed` | Answer by %1$s |
  | `answer_by_too_soon` | Too soon for a deadline — it goes out as a plain request. |

- [ ] **Step 1: Write the screenshot tests** in `RequestClockScreenshots`.
  - Pin `TimeZone.setDefault("Europe/Prague")` in a `@Before`; restore it in an `@After`.
  - Build the offers with `AnswerBy.offer(now = ScreenshotFixtures.TODAY at 10:07, …)`.
  - Four snaps:
    - `answerByChoices()` → `"requests_answer_by_choices"`, no cap;
    - `answerByCompressed()` → `"requests_answer_by_compressed"`, cap 3 days ahead;
    - `answerByFixed()` → `"requests_answer_by_fixed"`, cap 20 hours ahead;
    - `answerByTooSoon()` → `"requests_answer_by_too_soon"`, cap 1 hour ahead.

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew verifyRoborazziDebug --tests '*RequestClockScreenshots*'`
  - Expected: compilation fails.

- [ ] **Step 3: Implement `AnswerByField`, then each form.**
  - The block uses `FilterChip`, `FlowRow`, `Spacing` steps, `MaterialTheme.typography.bodySmall` and `onSurfaceVariant` for the helper lines.
  - The co-parent's name comes from `ParentNames.labelForUid(...)`.
  - Split `DaySwapSheet` into private composables: the day list, the note and the deadline block. `DaySwapSheet` itself is then under 60 lines.

- [ ] **Step 4: Run the build and the screenshots.**
  - Run: `./gradlew assembleDebug detekt recordRoborazziDebug --tests '*RequestClockScreenshots*'`
  - Expected:
    - the build passes;
    - detekt reports nothing new;
    - four images render, and none is committed (Task 17 records them).

- [ ] **Step 5: Commit.**
  `git commit -m "feat(requests): the proposer chooses when to be answered by, in all four forms"`

---

### Task 13: The inbox cards, the seen mark and the last chance

**Files:**
- Create: `app/src/main/java/com/coparently/app/presentation/changerequests/CardClock.kt`
- Create: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestClockText.kt`
- Create: `app/src/main/java/com/coparently/app/data/requests/RequestClockActions.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/ChangeRequestsScreen.kt`:
  - `InboxCard` provides `LocalCardClock`;
  - the three private cards read it.
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/AwaitingEventCard.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestHistoryGroup.kt` (the `NO_ANSWER` row)
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/ChangeRequestViewModel.kt` (+ `requestClockActions`, under its existing suppression)
- Modify: `app/src/main/java/com/coparently/app/presentation/home/AwaitingDialogs.kt` and `HomeScreen.kt` (the seen mark from the pop-ups)
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/CalendarScreen.kt` (`pendingSwapDates` drops `unansweredSwapDates`)
- Modify: `app/src/main/res/values*/request_deadline_strings.xml` (5 files)
- Test: `app/src/test/java/com/coparently/app/presentation/changerequests/RequestClockTextTest.kt` (new)
- Test: `app/src/test/java/com/coparently/app/data/requests/RequestClockActionsTest.kt` (new)
- Test: `ChangeRequestViewModelTest`, `RequestClockScreenshots`

**Interfaces:**
- Consumes:
  - `RequestInbox.timed`, `timedFor` and `unansweredSwapDates`, and `InboxItem.timed` (Task 10);
  - `RequestTelling` and `RequestRecords` (Task 11);
  - `answerByText` (Task 7).
- Produces:

```kotlin
enum class ClockRole { PROPOSER, RESPONDER }
@Immutable data class CardClock(
    val timed: TimedRequest,
    val role: ClockRole,
    val otherName: String,
    val onSeen: (() -> Unit)?,              // answering parent, not yet seen
    val onGiveLastChance: (() -> Unit)?     // proposer, while the button is offered
) { val hidesAnswers: Boolean }             // RESPONDER in NO_ANSWER_LAST_CHANCE_POSSIBLE
val LocalCardClock: ProvidableCompositionLocal<CardClock?>   // staticCompositionLocalOf { null }
@Composable internal fun CardClockBanner()                  // RESPONDER + LAST_CHANCE: InlineBanner(ATTENTION)
@Composable internal fun CardClockChip()                    // PROPOSER + NO_ANSWER_LAST_CHANCE_POSSIBLE: "No answer"
@Composable internal fun CardClockDetails()                 // the rows of spec §6.2–§6.4; calls onSeen once

object RequestClockText {
    fun left(millis: Long): UiText                          // ≥ 48 h → days; ≥ 1 h → hours; else minutes (≥ 1)
    fun historyMeta(clock: RequestDeadline): List<UiText>   // spec §6.6, in order, each present only when known
}

@Singleton class RequestClockActions @Inject constructor(repository: RequestDeadlineRepository, records: RequestRecords,
    telling: RequestTelling, scheduler: RequestClockScheduler, time: RequestTime) {
    suspend fun markSeen(clockId: String): Boolean          // answering parent, once
    suspend fun giveLastChance(clockId: String): Boolean    // proposer, while offered
}
// ChangeRequestViewModel
fun markSeen(clockId: String?)
fun markProposalSeen()
fun giveLastChance(clockId: String)
val pendingProposal: StateFlow<CustodyProposal?>            // now inbox.waitingOnMe.proposal of RequestsSource
// AwaitingActions (internal) + onSwapSeen: (DaySwapGroup) -> Unit, onProposalSeen: () -> Unit
```

- **Every card's three slots**:
  - `CardClockBanner()` is first in the card's `Column`.
  - `CardClockChip()` sits at the end of the title line, in a `Row` with the title `weight(1f)` (artboard 4).
  - `CardClockDetails()` comes after the card's content, before its buttons.
  - A card hides its answer buttons while `LocalCardClock.current?.hidesAnswers == true`.
- **What `CardClockDetails` draws** (artboards 2–5):
  - **Answering parent, `OPEN`**: `request_clock_answer_by` in `primary`, with a clock icon.
  - **Answering parent, `NO_ANSWER_LAST_CHANCE_POSSIBLE`**: `request_clock_responder_unanswered`, with the button's end time.
  - **Answering parent, `LAST_CHANCE`**: nothing; the banner says it.
  - **Proposer, `OPEN`**:
    - not delivered: `request_clock_sent` only;
    - delivered: these rows, each with its icon:
      - `request_clock_opened` (eye), or `request_clock_not_opened` (eye-off);
      - `request_clock_reminders_next` or `request_clock_reminders` (bell), left out when `remindersPlanned == 0`;
      - `request_clock_answer_by` (clock).
  - **Proposer, `LAST_CHANCE`**: the opened row, then `request_clock_last_chance_until`.
  - **Proposer, `NO_ANSWER_LAST_CHANCE_POSSIBLE`**:
    - `request_clock_deadline_passed`;
    - the opened and reminders rows;
    - `request_clock_calendar_unchanged`;
    - a filled button: `request_clock_give_last_chance` when the window is 24 h, else `request_clock_give_last_chance_until` (ruling 3);
    - `request_clock_last_chance_once`.

  | Key | English |
  | --- | --- |
  | `request_clock_answer_by` | Answer by %1$s · %2$s |
  | `request_left_days` (plurals) | %d day / %d days |
  | `request_left_hours` (plurals) | %d h |
  | `request_left_minutes` (plurals) | %d min |
  | `request_clock_sent` | Sent · Answer by %1$s |
  | `request_clock_opened` | %1$s opened it · %2$s |
  | `request_clock_not_opened` | %1$s hasn't opened it yet |
  | `request_clock_reminders` | Reminders: %1$d of %2$d |
  | `request_clock_reminders_next` | Reminders: %1$d of %2$d · next %3$s |
  | `request_clock_last_chance_until` | Last chance until %1$s · %2$s |
  | `request_clock_deadline_passed` | Deadline passed %1$s |
  | `request_clock_calendar_unchanged` | Nothing has changed in the calendar. |
  | `request_clock_give_last_chance` | Give a last chance — 24 hours |
  | `request_clock_give_last_chance_until` | Give a last chance — until %1$s |
  | `request_clock_last_chance_once` | Once. %1$s will have until %2$s. |
  | `request_clock_responder_unanswered` | You didn't answer by the deadline. %1$s can give you a last chance until %2$s. |
  | `request_last_chance_banner_title` | Last chance · %1$s left |
  | `request_last_chance_banner_text` | Answer by %1$s. Without an answer the request closes as “No answer”. |
  | `request_last_chance_failed` | The last chance couldn't be sent. Try again. |
  | `request_history_meta_deadline` | Deadline %1$s |
  | `request_history_meta_last_chance` | last chance until %1$s |
  | `request_history_meta_opened` | opened %1$s |
  | `request_history_meta_reminders` (plurals) | %d reminder / %d reminders |
  | `request_history_meta_unchanged` | calendar unchanged |

- **History's `NO_ANSWER` row** (artboard 6): Task 10's chip, then the meta parts joined with " · ".
- The proposer's "No answer" chip on a card is the same chip, with the same `request_clock_no_answer`.
- **`giveLastChance`**, in this order:
  1. Read the clock fresh (`repository.get`). Proceed only if the signed-in parent is its proposer, the window is non-null, and `records.look` reports it `PENDING`.
  2. `repository.giveLastChance(clock, now + window)`.
  3. `telling.lastChance(clock, look.card, until)`.
  4. `scheduler.scheduleEnd(id, until)`.
- **`markSeen`**: read the clock fresh. Write only if the signed-in parent is its responder and `seenAtMillis == null`.
- **The ViewModel never reads a `WhileSubscribed` value for these.** It passes ids (CLAUDE.md item 17).

- [ ] **Step 1: Write the failing tests.**
  - `RequestClockTextTest`:
    - ``fun `time left reads days, then hours, then minutes`()``: 72 h → 3 days; 47 h → 47 h; 59 min → 59 min; 20 s → 1 min.
    - ``fun `the history line names each part it knows, in order`()``:
      - a full clock: deadline, last chance, opened, reminders, unchanged;
      - a clock never delivered: deadline, unchanged.
  - `RequestClockActionsTest`:
    - ``fun `the answering parent marks a request seen once`()``
    - ``fun `the proposer gives one last chance: the clock, the push, the card, the end, in that order`()``
    - ``fun `no last chance once the button's time is over, for an answered request, or for the answering parent`()``
  - `ChangeRequestViewModelTest`:
    - ``fun `the proposal pop-up follows the inbox, so a proposal past its deadline does not pop up`()``
    - ``fun `a failed last chance says so`()``: `errorMessage == UiText.Res(R.string.request_last_chance_failed)`.
  - `RequestClockScreenshots`, each card part on its own:
    - `clockResponderLine` → `"requests_clock_responder"`;
    - `clockProposerSent` → `"requests_clock_sent"`;
    - `clockProposerDelivered` → `"requests_clock_delivered"`, delivered and not opened;
    - `clockProposerOpened` → `"requests_clock_opened"`, with the reminders row;
    - `clockNoAnswer` → `"requests_clock_no_answer"`, with the button;
    - `clockLastChanceBanner` → `"requests_clock_last_chance"`;
    - `historyNoAnswer` → `"requests_history_no_answer"`.

    Each provides `LocalCardClock` around the internal part. Fixtures: `ScreenshotFixtures.MY_UID`, `CO_PARENT_UID` and a clock pinned to the fixture month.

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*RequestClockTextTest*' --tests '*RequestClockActionsTest*' --tests '*ChangeRequestViewModelTest*'`
  - Expected: compilation fails.

- [ ] **Step 3: Implement.**
  - `InboxCard` wraps each item in `CompositionLocalProvider(LocalCardClock provides cards.clockFor(item, answers))`. `InboxAnswers` gains `markSeen` and `giveLastChance`.
  - `HomeScreen` wires the pop-ups' `onSwapSeen` to `markSeen(RequestDeadlineId.forSwap(group))` and `onProposalSeen` to `markProposalSeen()`. `AwaitingDialogs` calls each once when its dialog is shown, through a `LaunchedEffect` keyed on the dialog.
  - `CalendarScreen` takes `(inboxRequests as? Ready)?.inbox?.unansweredSwapDates` out of `pendingSwapDates`.

- [ ] **Step 4: Run the tests, the build and the screenshots.**
  - Run: `./gradlew testDebugUnitTest assembleDebug detekt recordRoborazziDebug --tests '*RequestClockScreenshots*'`
  - Expected: all pass, and detekt reports nothing new.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(requests): the deadline on every card, the seen mark and the last chance"`

---

### Task 14: The switcher's dot reads the clocks

**Files:**
- Modify: `app/src/main/java/com/coparently/app/data/family/OtherFamiliesSignals.kt` (constructor + `requestClocks`)
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/FirestoreChangeRequestDataSource.kt`. `observeHasPendingFrom` becomes `observePendingIdsFrom`: the same query without `limit(1)`, emitting the ids.
- Test: `OtherFamiliesSignalsTest`, `FirestoreChangeRequestDataSourceTest`

**Interfaces:**
- Consumes: `RequestClocks.observeFamily` (Task 5); `RequestDeadlineId`, `ClocksAt` and `TimedRequest.waitsOnResponder` (Task 1).
- Produces:

```kotlin
fun observePendingIdsFrom(myUid: String, fromUid: String): Flow<Set<String>>
// OtherFamiliesSignals companion
fun waitingRequests(pendingIds: Set<String>, fromUid: String, familyId: String, clocks: ClocksAt): Boolean
fun hasPendingSchedule(custody: SharedCustody?, myUid: String, today: LocalDate, clocks: ClocksAt = ClocksAt()): Boolean
```

- A request lights the dot only when it has no clock or its clock `waitsOnResponder`. This holds for a change request, for a swap offered to this parent and for the co-parent's proposal.

- [ ] **Step 1: Write the failing tests.**
  - `OtherFamiliesSignalsTest`:
    - ``fun `a request past its deadline does not light the dot`()``
    - ``fun `a request under a last chance lights it again`()``
    - ``fun `a request without a clock lights it as before`()``
    - ``fun `the dot goes out at the deadline without any document changing`()``: a tick only.
    - Existing cases: `observeHasPendingFrom` becomes `observePendingIdsFrom`.
  - `FirestoreChangeRequestDataSourceTest`: ``fun `the pending requests from one co-parent are listed by id`()``.

- [ ] **Step 2: Run them and see them fail.**
  - Run: `./gradlew testDebugUnitTest --tests '*OtherFamiliesSignalsTest*' --tests '*FirestoreChangeRequestDataSourceTest*'`
  - Expected: compilation fails.

- [ ] **Step 3: Implement.**
  - `watch` adds `requestClocks.observeFamily(myUid, family.familyId)` as a fourth input. It is bounded by the repository (Task 5).
  - Combine it into the change-request and schedule signals.
  - Keep one listener per other family per source (design item 13).

- [ ] **Step 4: Run the tests and see them pass.**
  Same command. Expected: all pass.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(family): the switcher's dot goes out once a request has no answer"`

---

### Task 15: Two phones on the emulators

**Files:**
- Create: `app/src/androidTest/java/com/coparently/app/e2e/TwoParentRequestDeadlinesTest.kt`
- Create: `app/src/androidTest/java/com/coparently/app/e2e/RecordingClockScheduler.kt`
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/EmulatorParent.kt`. Expose these properties:
  - `requestDeadlineRepository`
  - `requestClockSteps`
  - `requestClockKeeper`
  - `requestClockActions`
  - `requestScheduler: RecordingClockScheduler`
- Modify: `tools/e2e/coverage.json`

**Interfaces:**
- Consumes:
  - `TwoParentTest` (`alice`, `bob`, `pair`);
  - `EmulatorEnvironment.awaitQueuedPush`, `EmulatorEnvironment.step`, `queryAsAdmin`;
  - every class of Tasks 5, 8, 10, 11 and 13.
- Produces: `TwoParentRequestDeadlinesTest`, with the two tests below.

- [ ] **Step 1: Write the tests.** Every wait is bounded by `EmulatorParent.WAIT_MS`.
  - ``fun aSwapWithADeadlineIsDeliveredOpenedGivenALastChanceAndAnswered()``:
    1. Alice agrees a first pattern, as `TwoParentCustodyTest.agreeFirstPattern` does. She then offers a day 10 days ahead with `AnswerByPick.DEFAULT`.
       - `awaitQueuedPush(bob.uid, "day_swap_offered")["answerBy"]` equals the clock's `answerByMillis`.
    2. `bob.requestClockKeeper.sweep(bob.uid, setOf(alice.uid))`. Alice then reads the clock:
       - `deliveredAtMillis != null`;
       - `remindersPlanned == reminderTimesMillis.size`, in 1..3;
       - Bob's `requestScheduler` holds that many reminders.
    3. `bob.requestClockActions.markSeen(id)`. Alice reads `seenAtMillis != null`.
    4. Alice offers a second day with no pick. She then starts a clock for it directly: `answerByMillis = now + 45 s`, cap = that day's 00:00.
       - After the deadline, `alice.requestClockActions.giveLastChance(id2)` is true.
       - `awaitQueuedPush(bob.uid, "request_last_chance")` carries `answerBy == lastChanceUntilMillis.toString()` and `familyId`.
    5. Bob accepts the second day.
       - `alice.requestClockSteps.end(id2) == DONE`;
       - the clock has no `outcome`;
       - `RequestInbox.build(...)` over Alice's data puts it in History as `ACCEPTED`.
  - ``fun aProposalNobodyAnswersClosesAsNoAnswerAndIsWithdrawn()``:
    1. After the first pattern, Alice proposes another with no pick.
    2. She starts a clock directly, under its `proposedAt`: `answerBy = now + 45 s`, `cap = now + 60 s`. The button is never offered, so the end is the deadline (ruling 2).
    3. After the deadline, `alice.requestClockSteps.end(id) == DONE`. Then:
       - the clock has `outcome == "no_answer"` and `closedAt`;
       - the custody document has no `proposal`;
       - the thread holds a `REQUEST_UNANSWERED` card;
       - Bob's `RequestInbox` puts it in History as `NO_ANSWER`;
       - Bob can propose again.

- [ ] **Step 2: Map the surfaces** in `tools/e2e/coverage.json`:
  - `"request_deadlines"` → both tests, `"checklist": ["5.9"]`;
  - `"request_last_chance"` → the first test, `"checklist": ["5.9"]`.

- [ ] **Step 3: Run the coverage check.**
  - Run: `node tools/check-e2e-coverage.js`
  - Expected: it passes. It fails before Step 2, naming `request_deadlines` and `request_last_chance`.

- [ ] **Step 4: Run the e2e where an emulator exists.**
  - Run: `tools/e2e/run-two-parent-tests.sh`
  - Expected: the two new tests pass, with no skip. In a cloud session, CI's `e2e` job is the run.

- [ ] **Step 5: Commit.**
  `git commit -m "test(e2e): deadlines, the last chance and the end between two phones"`

---

### Task 16: The UI tour and the device checklist

**Files:**
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/UiTourSeed.kt`. In `seedPending`:
  - Bob's swap is offered with a group id, through `DayOverrideTransition.offer(..., groupId = …)`, and gets a clock 2 days out;
  - a new `attempt("alice's request")` sends Alice's change request to Bob with a clock. Bob's keeper sweeps it, and he marks it seen.
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/UiTourTest.kt`. After `"change_requests_inbox"`, add `camera.shot("requests_waiting_on_them")`: the section scrolled to Alice's card.
- Modify: `docs/DEVICE-CHECKLIST.md` (new `### 5.9 Deadlines, reminders and the last chance (phase 3a) · 2P, 1P + a second account fallback`)
- Modify: `tools/manual-test-plan.js` (`RULES` entry for 5.9) and `tools/test/manual-test-plan.test.js`

- [ ] **Step 1: Write the failing plan test.**
  - In `manual-test-plan.test.js`: `it('maps the request clocks to 5.9')`.
  - `planFor(['app/src/main/java/com/coparently/app/data/requests/RequestClockSteps.kt'])` includes `'5.9'`.

- [ ] **Step 2: Run it and see it fail.**
  - Run: `node --test tools/test/manual-test-plan.test.js`
  - Expected: it fails; no rule names 5.9.

- [ ] **Step 3: Add the rule and the section.**
  - The `RULES` entry:
    - `sections: ['5.9']`;
    - `why: 'request deadlines: the clock, reminders, the last chance and "No answer"'`;
    - `paths`:
      - `'K/data/requests/**'`
      - `'K/domain/changerequests/RequestClock*'`, `'K/domain/changerequests/RequestDeadline*'`, `'K/domain/changerequests/AnswerBy*'`, `'K/domain/changerequests/ReminderPlan*'`, `'K/domain/changerequests/RequestCap*'`
      - `'K/presentation/common/AnswerByField*'`
      - `'K/presentation/changerequests/CardClock*'`
      - `'app/src/main/res/values*/request_deadline_strings.xml'`
  - Section 5.9 covers what only phones show (spec §8):
    - the reminders at their planned times;
    - quiet hours on a phone set to another time zone;
    - both proposer notices;
    - the last-chance push;
    - the opened mark arriving;
    - an older build's co-parent answering a swap after "No answer";
    - the pop-up not returning after the deadline.

    Mark each step the e2e covers **[CI]**.

- [ ] **Step 4: Run the tests and see them pass.**
  - Run: `node --test tools/test/*.test.js`
  - Expected: all pass.

- [ ] **Step 5: Commit.**
  `git commit -m "test(requests): the UI tour shows a deadline, and the device checklist says what only phones show"`

---

### Task 17: Legal documents, CLAUDE.md, ROADMAP and the screenshot baselines

**Files:**
- Modify: `docs/legal/PRIVACY-POLICY.md`:
  - the requests' paragraph: the deadline, delivery, opening and reminder count, and that the other parent sees the opened time;
  - "Deleting your account" covers the clocks.
- Modify: `web/privacy/index.html`, regenerated as `web/README.md` says.
- Modify: `docs/legal/RECORDS-OF-PROCESSING.md` (P2: data and `request_deadlines` under "Where")
- Modify: `docs/legal/DPIA.md`:
  - new risk R22: "An opened mark used to pressure the other parent";
  - its mitigations: no push on opening, no reminders at night, none after the last chance, the mark written once.
- Modify: `web/README.md` (the collection list of "Keep three files in step") and `web/delete-account/index.html`, if its wording lists categories that miss the clocks
- Modify: `CLAUDE.md`:
  - "Things that are easy to get wrong" item 42, request clocks:
    - one document beside the record;
    - the states are derived and only the end is stored;
    - the proposer's uid is in the id;
    - every background step uses the clock's family;
    - older builds' late answers count;
    - no push for the two cards.
  - Design refresh item 1 lists `AnswerByField` and `LocalCardClock` beside the other primitives.
- Modify: `docs/ROADMAP.md`:
  - the phase 3a entry;
  - under "Yours only": `firebase deploy --only firestore:rules,firestore:indexes --project coparently-a39c9`, then `firebase deploy --only functions --project coparently-a39c9`. Until the functions are deployed, the two cards are pushed like any message (spec §7).
- Touch: `.github/regenerate-request`, to record the new screenshot baselines.

- [ ] **Step 1: Write the documents.** They are written from the code (Legal item 1). DATA-SAFETY needs no change (spec §7).
- [ ] **Step 2: Regenerate `web/privacy/`.**
  - Run the command in `web/README.md`.
  - Expected: `git diff web/privacy/` shows only the new paragraph.
- [ ] **Step 3: Run the checks.**
  - Run: `node tools/check-invariants.js && node --test tools/test/*.test.js && (cd web-tests && npm test)`
  - Expected: all pass.
- [ ] **Step 4: Commit and push, then touch the regenerate request.**
  - Commit the docs: `git commit -m "docs: request deadlines in the policy, the records, the DPIA, CLAUDE.md and the roadmap"`.
  - Touch `.github/regenerate-request` and push.
  - The workflow's bot commit carries the new baselines: Tasks 12 and 13's images.
  - Expected: CI's `screenshots` job then verifies with no image missing and none changed beyond these.
