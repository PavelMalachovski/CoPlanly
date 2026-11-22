# Home Requests, a chat of people, settle-up in Money, calendar K1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the owner-approved redesign: Home's one Requests block with action cards and Details,
the Money card that carries settle-up, a chat thread of people's messages, and calendar K1.

**Architecture:** UI-only change in `:shared-ui` (`commonMain`), plus one Room query in
`:shared-data` (unread counts ignore cards). No schema, wire or rules change: cards keep being
written to `messages` for the export; the thread just stops drawing them.

**Tech Stack:** Kotlin 2.4, Compose Multiplatform 1.9.3, Material 3 1.4, Koin, Room KMP, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-11-22-home-requests-calendar-concepts-design.md` and the canvas
https://claude.ai/artifact/CTpjVMU5nvM4vcNpXmXZwy (boards CalendarK1, CalendarK1iOS, HomeG1,
HomeSwapDetails, HomeScheduleDialog, MoneySettleUp, ChatAfter).

## Global Constraints

- Every new user-facing string goes into all five locales (`values`, `-cs`, `-de`, `-ru`, `-uk`) under
  `shared-ui/src/commonMain/composeResources/`, feature-named files; `'` for an apostrophe, no
  `%%` beside arguments, sentence case (CLAUDE.md "Localization"). `node tools/check-invariants.js` passes.
- Screens, components and ViewModels live in `:shared-ui`, never `app/src/main/java/` (ShellBoundaryTest).
- Colours from `MaterialTheme.colorScheme` and `ParentColors`; spacing `Spacing`, corners `MaterialTheme.shapes`/`CoPlanlyCorners`, icons through `AppIcon` with `IconSizes`; a new Material icon gets an `SF_SYMBOLS` row and a row in `docs/superpowers/plans/2026-11-16-iphone-phase-3d-sf-symbols.json` (`SfSymbolTableTest`).
- Status colour language (spec): waiting for you = `primary` filled chip; waiting for the co-parent = outlined (`outline`) chip; accepted/settled = `tertiaryContainer`/`onTertiaryContainer`; declined/no answer/withdrawn = `surfaceVariant`/`onSurfaceVariant`; new/info = `secondaryContainer`. Never a parent hue for a status. A word always carries the status; colour repeats it.
- Money is never cut off (design item 15); pairs stack from font scale 1.3.
- No score between parents: no counts of nights or days per parent.
- Android behaviour of every adaptive control is unchanged (`nativeControls()` null branch).
- Build: `./gradlew assembleDebug testDebugUnitTest :shared-ui:testAndroidHostTest :shared-data:testAndroidHostTest`, `./gradlew lint detekt` before each commit that touches Kotlin. Conventional Commits.

## Review Focus

- Two families: Requests, Money and Details read the family on screen only (items 40, 43, 46).
- A request already answered on the other phone disappears from Home without a reload (flows, not snapshots).
- Font scale 2.0 in German: action-card buttons stack (`AnswerButtons` does it from 1.3), amounts wrap.
- A chat push for a card that no longer shows in the thread must not open an empty-looking thread.
- iPhone: Calendar/Home keep `ScreenBar(hidden = true)`; the new header is drawn in content.

---

### Task 1: Settle-up leaves the chat for Money (Expenses header + Home Money card)

**Files:** `presentation/chat/SettleUpPayment.kt` (extract), new `presentation/payments/SettleUpPanel.kt`,
`presentation/expenses/ExpenseSummaryHeader.kt`, `ExpenseScreen.kt`, new `presentation/home/MoneyCard.kt`,
`home/HomeSection.kt`, `home/HomeScreen.kt`, `home/HomeViewModel.kt`, strings `expenses_strings.xml`/`home_strings.xml` ×5,
tests in `shared-ui/src/commonTest|androidHostTest`, screenshots `HomeScreenshots`/`FeatureScreenshots`.

- [ ] Extract the payer's block (QR via `QrCodes`/`PaymentQr`, IBAN with `SensitiveClipboard` copy, Share QR, "I paid") and the payee's "Money arrived" step into `SettleUpPanel(state: SettleUpPanelState, actions)` with five states matching the canvas board MoneySettleUp: `AskToSettle`(payee, balance owed to me, not asked), `Pay`(payer, asked or owing), `WaitingForConfirm`(payer paid), `Confirm`(payee, payer said paid), `Settled`.
- [ ] Derive the state in a pure function `settleUpPanelState(balance, payment: PaymentState?, askedAt, iAmPayer)` in commonMain with a commonTest covering all five states and "no balance".
- [ ] Expenses: the summary header shows the panel under the balance row instead of only `PaymentStrip`'s two buttons (keep PaymentStrip's words).
- [ ] Home: `MoneyCard` = "Spent in {month}" + balance sentence (`balanceCaption`) + the panel; tapping the figures opens Expenses. Replaces the money half of `StatTiles`; the chat tile goes (chat has its own badge).
- [ ] Chat: `MessageItem` no longer invokes `LocalSettleUpPayment`; `SettleUpPaymentHost` removed from `ChatScreen`.
- [ ] Screenshot tests: `moneyCard` in each of the five states; update `statTiles`.
- [ ] Commit `feat(money): settle up from the Money card and Expenses, not the chat`.

### Task 2: Home G1 — one Requests block, action cards, Details, graphical schedule pop-up

**Files:** `home/HomeScreen.kt`, `home/HomeSection.kt`, new `home/RequestsBlock.kt`, `home/RequestActionCard.kt`,
`home/RequestDetailsSheet.kt`, `home/RequestStatusChip.kt`, `home/HomeViewModel.kt`, `home/AwaitingDialogs.kt`,
`calendar/components/CalendarHeader.kt` (HeadingLines moves), delete `home/UpdatesCard.kt`/`OpenRequestsCard.kt` uses,
strings ×5, tests, screenshots.

- [ ] Home order: brand row → "Your family calendar / Together, for …" (moved from `CalendarHeader.HeadingLines`, same strings) → Requests → Money → This week → handover (week and handover kept below, owner did not remove them).
- [ ] Requests heading: "Requests", a primary chip "{n} for you" when n > 0, and "See all" with a secondary chip "{n} new" when n > 0; See all opens the inbox (History when nothing waits).
- [ ] "{n} new" = `HomeFeed` items newer than a per-family seen mark in `SecurePreferences` (`home_requests_seen_<familyId>`, epoch millis), set when See all is opened. Pure `unseenCount(items, mark)` with commonTest.
- [ ] Action cards for `inbox.waitingOnMe` (swap groups, change requests, events awaiting, custody proposal, split, agreements rows): status chip "Waiting for you" + "Answer by …" (clock), icon disc by kind, title/subtitle reusing the inbox's wording, the note as a quote, and `Details` · `AnswerButtons(Decline, Accept)`. Answers call the same `ChangeRequestViewModel` actions the inbox and pop-ups call. Agreement rows (support, professional, plan) open their screens instead of answering.
- [ ] "Waiting for {co-parent}" rows for `inbox.waitingOnThem` with an outlined chip and Withdraw (same actions as the inbox).
- [ ] `RequestDetailsSheet` (AdaptiveSheet): swap → `swapMiniMonths` with `SwapMark`; schedule proposal → `ProposedSchedulePreview` with a Proposed | Now `AdaptiveSegmented` and changed days ringed in the receiver's colour; change request / awaiting event → `MiniMonth` of its day. Decline/Accept again at the bottom.
- [ ] `AwaitingDialogs.ProposalDialog` gets the same Proposed | Now preview with rings (one shared composable `SchedulePreviewWithChanges`). Pop-up order and once-per-visit stay.
- [ ] Remove `UpdatesCard` and `OpenRequestsCard` from Home (delete the files if unused; keep `UpdateMark` if History uses it).
- [ ] Two panes (≥ 840 dp): Requests | Money + week + handover.
- [ ] Tests: unseen count, section order (`HomeSectionTest`), details state mapping; screenshots `requestsBlock`, `requestActionCard`, `requestDetailsSheet` (swap, proposal), `proposalDialogPreview`.
- [ ] Commit `feat(home): one Requests block with answers and details`.

### Task 3: Chat shows people — cards leave the thread

**Files:** `chat/MessagesList.kt` (`buildThread`), `chat/MessageItem` area, `shared-data/.../dao/MessageDao.kt` (unread query),
`shared/.../domain/chat/ChatReadState.kt`, `data/remote/firebase/PushRouting` (app) / `PushDestination`, tests.

- [ ] `buildThread` keeps a message when `activity == null` or `activity.kind == STICKER_SENT`; a pure `isShownInThread(message)` in commonMain with tests for every `ActivityKind`.
- [ ] Unread: `MessageDao` unread query and `ChatReadState.unreadCount` count only messages shown in the thread (add `AND (activityKind IS NULL OR activityKind = 'STICKER_SENT')` using the stored column the entity already has — check the entity; no schema change). Tests for both.
- [ ] A `chat_message` push that carries `activityKind` (not a sticker) opens Home, not the thread (`PushDestination`); `PushRoutingTest`.
- [ ] Chat search (`ChatSearchRepository`) skips cards too.
- [ ] Export unchanged (assert by an existing export test still passing).
- [ ] Screenshot `chatThread` fixture: replace the EVENT_DECLINED card with a sticker.
- [ ] Commit `feat(chat): the thread shows the parents' messages, files and stickers`.

### Task 4: Calendar K1

**Files:** `calendar/components/CalendarHeader.kt`, `CalendarMonthBar.kt`, `CalendarTopBar.kt`, `CustodyLegend.kt`,
`calendar/CalendarScreen.kt`, `MonthCellPills.kt`/`CellPills.kt`, `common/FamilyMemberChips.kt`, strings ×5, tests, screenshots.

- [ ] Header row: month title "November 2026 ▾" (always with the year; lighter weight `onSurfaceVariant` for the year; tap = jump to date) where the wordmark was, a "?" icon button opening the legend (sheet/dialog with `CustodyLegend`), then the children's avatars as the member filter (tap toggles that member; selected = ring; same filter state as `FamilyMemberFilterStrip`, which is removed from over the grid; at two members as before) and the gear.
- [ ] Card bar: `AdaptiveSegmented` M | W | D (letters, content descriptions Month/Week/Day) left; then a text "Today" outlined button, ⇄ swap days, "+". No ‹ › arrows (paging by swipe stays). The old view-mode row goes; **Filters is not dropped**: the "?" sheet ("What the colours mean") shows `CustodyLegend` and, under it, a "Filters" row that opens the existing filters sheet (`CalendarFilters`), whose state and behaviour do not change.
- [ ] Remove from the calendar: `HeadingLines`, the legend row, `TodayCard` bottom bar, the pending request/proposal `ChangeRequestBanner`s and the proposer-waiting line (they live on Home). Keep `CustodyChangedBanner`, `DaySwapSelectionBar`, `DaySwapStrip`.
- [ ] Month cell pills: up to two pills, up to two lines each, `Hyphens.Auto` + `LineBreak.Paragraph`, then "+n"; a pill only where it fits (existing rule) — update `pillsFor` and its tests.
- [ ] Font scale ≥ 1.3: title steps down, then avatars/gear wrap under it.
- [ ] iOS: `CalendarTopBar` keeps hidden bar; `CalendarBarsTest` updated.
- [ ] Tests: `calendarMonthTitle` always includes the year; pills fitting; screenshots `header`, `gridCard`, remove `todayCard`/`banners` cases that no longer render on the calendar (keep banner component screenshots if Home uses them).
- [ ] Commit `feat(calendar): give the month grid the screen (K1)`.

### Task 5: Docs, checks, PR

- [ ] CLAUDE.md: design refresh items 5 (calendar header, title with year, no Today card/banners), Home variant (item 5's Home paragraph), item 8/chat (cards not drawn), item 1 component list (SettleUpPanel, RequestDetailsSheet); "Things that are easy to get wrong" 40 (Requests on Home), 45 (settle-up place).
- [ ] `docs/DEVICE-CHECKLIST.md`: checks for Home Requests answers, Details, Money settle-up end to end, calendar hyphenation on iPhone; `tools/manual-test-plan.js` RULES if sections added.
- [ ] Push, open PR, touch `.github/regenerate-request` to record new screenshot baselines, wait for CI, fix, merge when green.
