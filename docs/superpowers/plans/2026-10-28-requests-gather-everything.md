# Requests gather everything — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home's Requests row, the Requests inbox and the family switcher's dot also show three
more things that wait on both parents' word: a child support proposal, a professional's access,
and parenting plan answers.

**Architecture:** The pure domain decides what waits and on whom. That is
`ParentingPlanComparison.questionsAwaitingYou`, three new fields on `Waiting`, and
`RequestInbox.withAgreements`, which places a `FamilyAgreements` snapshot after `RequestInbox.build`.
A small `FamilyAgreementsSource` reads the three documents of the family on screen, by family id,
and `RequestsSource` joins it into `RequestsState.Ready`. The inbox draws each kind as a compact
row that opens its own screen. On the sender's side the row carries Withdraw.
`OtherFamiliesSignals` gains three per-family listeners and three `FamilySignal` kinds.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Firestore listeners, JUnit4 + MockK + Turbine, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-28-day-swap-ux-and-requests-design.md`, section "PR 2 — Requests gather everything (board SW-7)".

## Global Constraints

- Every new user-facing string goes into all five locales (`values`, `-cs`, `-de`, `-ru`, `-uk`) in the same commit. English is in sentence case. RU/UK never say "опека"/"опіка".
- No new push types, no new Room table, no schema bump.
- Every read is scoped to the family on screen, by its id. In `OtherFamiliesSignals`, every read is of one other family, never a query across families.
- `countWithoutProposal` (the calendar banner) does **not** include the three kinds. `Waiting.count` (Home's row) does.
- `HomeAskQueue` does not pop up for the three kinds. They wait on the row.
- History is unchanged. Nothing new enters `RequestHistory`.
- A plan question waits on this parent in two cases:
  - the co-parent answered, and this parent has neither an answer nor a tick of that answer as it reads now (`ONLY_THEIRS`);
  - both answered, and this parent's tick names an older wording of the co-parent's answer (`OPEN` with a stale tick).
- Composables stay stateless. Actions go through the ViewModel. Text from a ViewModel is a `UiText`.
- detekt: at most 5 parameters per function (6 is flagged), 60 lines per method, 20 functions per file, 3 `return`s, 120 columns.

## Review Focus

1. **The co-parent edits an answer this parent had ticked.** The question must come back as waiting (stale tick), and once this parent ticks again it must leave. Pinned in Task 1 (`staleTickWaitsAgain`).
2. **A grant of another family, or one this parent cannot act on, reaches the scope.** It must be ignored:
   - a grant whose `familyId` is not the scope's;
   - an expired grant;
   - a grant where only the professional is missing consent. This parent consented and the co-parent did not, so it waits on them, never on this parent.

   Pinned in Task 1 (`professionalOfAnotherFamilyIgnored`, `expiredGrantIgnored`, `myConsentGivenWaitsOnThem`).
3. **One of the three listeners fails or never answers.** The inbox must still load with the other requests. Neither `Ready` nor the existing kinds may be held back. Pinned in Task 2 (`failingSupportListenerStillReady`).
4. **The family switches while a Withdraw is in flight.** The withdraw must act on the agreement or grant the row showed, never on the new family's. Pinned in Task 3 (`withdrawSupportUsesShownAgreement`).
5. **A one-family account.** It attaches no new listener. `familiesToWatch` already returns none, and Task 4 adds no listener outside `watch`. Pinned in Task 4 (`oneFamilyWatchesNothing` stays green, with a verify that the new sources are never called).

---

### Task 1: What waits, decided in the domain

**Files:**
- Modify: `app/src/main/java/com/coparently/app/domain/parentingplan/ParentingPlan.kt` (`ParentingPlanComparison`)
- Create: `app/src/main/java/com/coparently/app/domain/changerequests/FamilyAgreements.kt`
- Modify: `app/src/main/java/com/coparently/app/domain/changerequests/RequestInbox.kt` (`Waiting`)
- Test: `app/src/test/java/com/coparently/app/domain/parentingplan/ParentingPlanComparisonTest.kt`, `app/src/test/java/com/coparently/app/domain/changerequests/RequestInboxAgreementsTest.kt` (new)

**Interfaces:**
- Produces:
  - `fun ParentingPlanComparison.questionsAwaitingYou(yours: ParentingPlanEntry, theirs: ParentingPlanEntry?): List<String>` returns question ids in catalogue order.
  - `data class FamilyAgreements(val support: SupportAgreement? = null, val professionals: List<ProfessionalGrant> = emptyList(), val plan: ParentingPlanPair? = null)`. `ParentingPlanPair` moves from `data/repository/ParentingPlanRepository.kt` to `domain/parentingplan/ParentingPlan.kt`, with the same shape `(yours, theirs)`. Fix its imports.
  - `Waiting` gains three fields. `count` adds `(support?1:0) + professionals.size + (if (planQuestions.isEmpty()) 0 else 1)`. `countWithoutProposal` is unchanged.
    - `support: SupportProposal? = null`
    - `professionals: List<ProfessionalGrant> = emptyList()`
    - `planQuestions: List<String> = emptyList()`
  - `fun RequestInbox.withAgreements(scope: RequestScope, agreements: FamilyAgreements, nowMillis: Long): RequestInbox` copies `waitingOnMe` and `waitingOnThem` with the three fields filled.

- [ ] **Step 1: Write the failing tests**

`ParentingPlanComparisonTest`, using real catalogue ids (`ParentingPlanCatalogue.questionIds[0]` as `q`, `[1]` as `q2`):
- `onlyTheirsWaits`: theirs answers `q`, yours empty → `listOf(q)`.
- `onlyTheirsTickedAsItReadsDoesNotWait`: yours `agreedTo = {q: their text}`, no own answer → empty.
- `staleTickWaitsAgain`: both answered, yours `agreedTo = {q: "old"}`, theirs answer `"new"` → `listOf(q)`. Ticking `"new"` → empty.
- `openNeverTickedDoesNotWait`: both answered, no ticks → empty.
- `noCoParentWaitsNothing`: `theirs = null` → empty.

`RequestInboxAgreementsTest`, with scope `RequestScope.of("me", "co", setOf("co"))!!` and `now = 1_000L`:
- `coParentsSupportProposalWaitsOnMe`: `SupportAgreement(familyId, participants, proposal = SupportProposal(terms, "co", 1))`. Expect `waitingOnMe.support` non-null, `waitingOnThem.support` null, and `waitingOnMe.count == 1`.
- `mySupportProposalWaitsOnThem`.
- `supportOfAnotherFamilyIgnored`: `familyId != scope.familyId` → both null.
- `grantAwaitingMyConsentWaitsOnMe`: `consents = {"co": 1}`, `expiresAtMillis = 10_000` → in `waitingOnMe.professionals`.
- `myConsentGivenWaitsOnThem`: `consents = {"me": 1}` → in `waitingOnThem.professionals`.
- `expiredGrantIgnored`: `expiresAtMillis = 500` → in neither.
- `professionalOfAnotherFamilyIgnored`.
- `activeGrantIgnored`: both consents.
- `planQuestionsCountAsOneRow`: two questions waiting → `planQuestions.size == 2`, `waitingOnMe.count == 1`.
- `agreementsNotInCalendarBanner`: all three on me → `waitingOnMe.countWithoutProposal == 0`.

- [ ] **Step 2: Run them; expect a compile failure (the functions and fields do not exist)**

Run: `scratchpad/ktest.sh "ParentingPlanComparisonTest RequestInboxAgreementsTest" <domain files>`. CI is the authority: `./gradlew testDebugUnitTest --tests '*ParentingPlanComparisonTest*' --tests '*RequestInboxAgreementsTest*'`.

- [ ] **Step 3: Implement**

Placement rules in `withAgreements`. A grant counts only when `familyId == scope.familyId`. `ProfessionalGrantPolicy.statusFor(grant, me, now)` decides where it goes:
- `WAITING_FOR_YOU` → `waitingOnMe`;
- `WAITING_FOR_CO_PARENT` with `me in consents` → `waitingOnThem`;
- anything else → neither.

Support goes by `proposal.proposedBy`: the co-parent → `waitingOnMe`, `me` → `waitingOnThem`. It counts only when `agreement.familyId == scope.familyId`.

The plan goes through `questionsAwaitingYou(plan.yours, plan.theirs)` → `waitingOnMe.planQuestions`.

- [ ] **Step 4: Run; expect PASS. Also run `RequestInboxTest` and the whole JVM suite in CI.**

- [ ] **Step 5: Commit**

Message: `feat(requests): decide when support, professional access and plan answers wait`.

### Task 2: Read the three documents for the family on screen

**Files:**
- Create: `app/src/main/java/com/coparently/app/data/requests/FamilyAgreementsSource.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/changerequests/RequestsSource.kt`
- Test: `app/src/test/java/com/coparently/app/presentation/changerequests/RequestsSourceTest.kt`

**Interfaces:**
- Consumes:
  - `SupportAgreementStore.observe(familyId): Flow<SupportRead>`;
  - `ProfessionalRepository.observeFamilyGrants(): Flow<List<ProfessionalGrant>>`;
  - `ParentingPlanRepository.observe(familyId, myUid): Flow<ParentingPlanPair>`;
  - `RequestInbox.withAgreements` (Task 1).
- Produces:
  - `class FamilyAgreementsSource @Inject constructor(supportStore: SupportAgreementStore, professionalRepository: ProfessionalRepository, planRepository: ParentingPlanRepository)` with `fun observe(scope: RequestScope): Flow<FamilyAgreements>`. Each of the three inputs is `.catch { emit(<empty>) }.onStart { emit(<empty>) }`, so the combined flow emits at once and never fails.
  - `RequestsState.Ready` gains `val agreements: FamilyAgreements = FamilyAgreements()`. Its `inbox` is `build(...).withAgreements(scope, agreements, System.currentTimeMillis())`.
  - `RequestsSource` takes `familyAgreementsSource: FamilyAgreementsSource` as an eighth constructor parameter (the suppression already exists).

- [ ] **Step 1: Write the failing tests in `RequestsSourceTest`**

Mock `FamilyAgreementsSource.observe(any())` (default `flowOf(FamilyAgreements())` in the fixture):
- `coParentsSupportProposalReachesTheInbox`: the source emits a co-parent proposal → `Ready.inbox.waitingOnMe.support` non-null and `Ready.agreements.support` is the same agreement.
- `failingSupportListenerStillReady`: this one tests `FamilyAgreementsSource` itself with a real instance and mocked inputs. `supportStore.observe` → `flow { throw IOException() }`, the others normal → the first emission is `FamilyAgreements()` and a later one carries the grants.
- `agreementsFollowTheScope`: switching the projected co-parent re-subscribes with the new scope's `familyId` (verify `observe(match { it.familyId == newFamily })`).

- [ ] **Step 2: Run; expect a compile failure (no such class or parameter)**
- [ ] **Step 3: Implement.** Map `SupportRead.Known(a)` to `a`, anything else to `null`. Keep only the grants with `it.familyId == scope.familyId`. Join through a nested `combine` (the outer one already holds five flows): `combine(<existing five>) {…}` then `.combine(familyAgreementsSource.observe(scope)) { ready, agreements -> … }`.
- [ ] **Step 4: Run `RequestsSourceTest`, `ChangeRequestViewModelTest` and `HomeViewModelTest`; expect PASS** (their fixtures construct `RequestsSource`, so add the new mock there).
- [ ] **Step 5: Commit**

Message: `feat(requests): read child support, professional grants and the plan for the family on screen`.

### Task 3: The inbox draws and withdraws them

**Files:**
- Modify: `presentation/changerequests/InboxItems.kt`, `ChangeRequestsScreen.kt`, `ChangeRequestViewModel.kt`
- Create: `presentation/changerequests/AgreementRows.kt` (the three rows), `data/requests/AgreementWithdrawals.kt`
- Modify: `presentation/navigation/NavGraph.kt` (the `ChangeRequests` route)
- Strings: `res/values*/change_requests_strings.xml` (five locales)
- Test: `InboxItemsTest.kt`, `ChangeRequestWithdrawTest.kt`, a new `app/src/test/java/com/coparently/app/screenshots/AgreementRowsScreenshotTest.kt`

**Interfaces:**
- Consumes: `Waiting.support/professionals/planQuestions` (Task 1) and `RequestsState.Ready.agreements` (Task 2).
- Produces:
  - Three new `InboxItem` kinds, appended at the end of each section's list, after change requests. The plan item is added only in `WAITING_ON_ME`.
    - `InboxItem.Support(proposal: SupportProposal, mine: Boolean)`, key `if (mine) "sup_mine" else "sup_theirs"`;
    - `InboxItem.Professional(grant: ProfessionalGrant, mine: Boolean)`, key `"pro_${grant.id}"`;
    - `InboxItem.Plan(questionCount: Int)`, key `"plan"`.
  - `enum class AgreementScreen { SUPPORT, PROFESSIONALS, PARENTING_PLAN }`.
  - `ChangeRequestsScreen(..., onOpenAgreement: (AgreementScreen) -> Unit = {})`. NavGraph maps the three values to `Screen.Support.route`, `Screen.Professionals.route` and `Screen.ParentingPlan.route`.
  - `class AgreementWithdrawals @Inject constructor(supportStore, professionalRepository)` with two functions:
    - `suspend fun withdrawSupport(agreement: SupportAgreement): Boolean` delegates to `clearProposal`;
    - `suspend fun withdrawProfessional(grantId: String): Boolean` delegates to `revoke(...).isSuccess`.
  - `ChangeRequestViewModel` takes `agreementWithdrawals` and gains `fun withdrawSupport()` and `fun withdrawProfessional(grantId: String)`. Each reads the `Ready` state at the moment of the call. On `false` it sets `errorMessage` to `UiText.Res(R.string.request_agreement_withdraw_failed)`.
  - `InboxAnswers` gains `withdrawSupport: () -> Unit`, `withdrawProfessional: (String) -> Unit` and `open: (AgreementScreen) -> Unit`. `InboxCards.clockFor` returns null for the three kinds.

Strings (English; translate into the other four):
- `request_support_theirs`: `%1$s proposes %2$s a month, on day %3$d, from %4$s`
- `request_support_mine`: `You proposed %1$s a month, on day %2$d, from %3$s`
- `request_professional_title`: `Professional access`
- `request_professional_theirs`: `%1$s %2$s — your consent is needed` (role, name)
- `request_professional_mine`: `%1$s %2$s — waiting for %3$s to consent`
- `request_plan_answered` (plurals): `%1$s answered %2$d question — compare and agree` / `%1$s answered %2$d questions — compare and agree`
- `request_agreement_withdraw_failed`: `That could not be withdrawn. Check your connection and try again.`

Each row's title reuses an existing string: `support_title`, `request_professional_title`, and the parenting plan screen's existing title. Withdraw reuses the label the split card's Withdraw uses.

- [ ] **Step 1: Write the failing tests**

`InboxItemsTest`:
- `agreementRowsCloseTheirSection`: `waitingOnMe` with a split, a change request, support, one grant and two plan questions. The items after the header end with `[…, Request, Support(mine=false), Professional(mine=false), Plan(2)]`.
- `minePlacesWithdrawRowsUnderThem`: support mine → `Support(mine=true)` under the `WAITING_ON_THEM` header.

`ChangeRequestWithdrawTest`:
- `withdrawSupportUsesShownAgreement`: `Ready.agreements.support = A` (proposal by me). Call `withdrawSupport()` → `coVerify { agreementWithdrawals.withdrawSupport(A) }`.
- `withdrawProfessionalRevokes`: `withdrawProfessional("g1")` → `coVerify { withdrawProfessional("g1") }`.
- `failedWithdrawShowsError`: returns `false` → `errorMessage` is `UiText.Res(R.string.request_agreement_withdraw_failed)`.
- `withdrawSupportIgnoresTheirs`: the proposal is the co-parent's → `withdrawSupport` is never called.

`AgreementRowsScreenshotTest`: the three rows, theirs and mine, through `ScreenshotMatrix` like the other request components. This one has no failing-first step: it records with Regenerate.

- [ ] **Step 2: Run the unit tests; expect a compile failure**
- [ ] **Step 3: Implement**

Each row is `SectionGroup { SectionRow(…) }`:
- the icon is `Payments` for support, `Gavel` for a professional (or whichever icon the Professionals screen uses), and `Description` for the plan;
- `onClick` opens its screen;
- a mine row carries one trailing `TextButton` Withdraw.

Amounts use `currencyFormat(terms.currency).format(terms.amount)` (make SupportScreen's `money` an `internal` helper in its file and reuse it). The month is `LLLL yyyy` in the default locale, as `monthName` (also made `internal`). The name comes from `parentNames.labelForUid(…)`, and the role from `ProfessionalRole.labelRes()`.
- [ ] **Step 4: Run the four test classes and `InboxItemsTest`; expect PASS. Run `node tools/check-invariants.js`; expect no missing locale or format errors.**
- [ ] **Step 5: Commit**

Message: `feat(requests): list child support, professional access and the plan in the inbox`.

### Task 4: The family switcher's dot

**Files:**
- Modify: `data/family/OtherFamiliesSignals.kt`, `presentation/common/FamilySwitcher.kt`, `domain/repository/ProfessionalRepository.kt`, `data/repository/ProfessionalRepositoryImpl.kt`
- Modify: `firestore.indexes.json` (`professional_grants`: `familyParents` CONTAINS + `familyId` ASC), `firestore-tests/rules/professional-access.test.js`
- Strings: `res/values*/settings_account_strings.xml`
- Test: `app/src/test/java/com/coparently/app/data/family/OtherFamiliesSignalsTest.kt`

**Interfaces:**
- Consumes:
  - `SupportAgreementStore.observe(familyId)`;
  - `FirestoreParentingPlanDataSource.observePlan(familyId)`;
  - `ParentingPlanComparison.questionsAwaitingYou` and `ProfessionalGrantPolicy.statusFor` (Task 1).
- Produces:
  - `FamilySignal` gains `SUPPORT`, `PROFESSIONAL` and `PLAN`.
  - `ProfessionalRepository.observeGrantsOf(familyId: String): Flow<List<ProfessionalGrant>>`: `whereArrayContains("familyParents", me).whereEqualTo("familyId", familyId)`, through the existing private `observeGrants` shape.
  - Three new companion functions:
    - `fun hasPendingSupport(read: SupportRead, myUid: String): Boolean`
    - `fun hasProfessionalWaiting(grants: List<ProfessionalGrant>, myUid: String, familyId: String, nowMillis: Long): Boolean`
    - `fun hasPlanWaiting(halves: Map<String, ParentingPlanEntry>, myUid: String, coParentUid: String): Boolean`
  - `watch` combines the seven listeners. `combine` takes at most five, so combine the three new ones into one `Set` flow first.

Strings:
- `family_switcher_support_other`: `A child support proposal is waiting in another family`
- `family_switcher_support_row`: `A child support proposal is waiting`
- `family_switcher_professional_other`: `A professional's access waits for your consent in another family`
- `family_switcher_professional_row`: `A professional's access waits for your consent`
- `family_switcher_plan_other`: `Parenting plan answers are waiting in another family`
- `family_switcher_plan_row`: `Parenting plan answers are waiting`

- [ ] **Step 1: Write the failing tests**

`OtherFamiliesSignalsTest`:
- `hasPendingSupport`: true for `SupportRead.Known` with the co-parent's proposal; false for my own proposal, `None` and `Unavailable`.
- `hasProfessionalWaiting`: true only for `WAITING_FOR_YOU` in that family; false when expired, for another family, or when only the co-parent's consent is missing.
- `hasPlanWaiting`: true for an `ONLY_THEIRS` question; false with no co-parent half.
- `supportProposalLightsTheDot`: with two families, the other family's support source emits a co-parent proposal → `signals` contains `FamilySignal.SUPPORT` for it.
- `oneFamilyWatchesNothing`: keep this test and add `verify(exactly = 0) { supportStore.observe(any()) }`.

In `firestore-tests/rules/professional-access.test.js`:
- a parent's query `familyParents array-contains me` + `familyId == F` succeeds;
- the same query by a stranger fails.

- [ ] **Step 2: Run; expect a compile failure / the new rules case to fail before the index and query exist** (`cd firestore-tests && npm test`; unset the proxy variables).
- [ ] **Step 3: Implement.** Each new listener goes through `bounded(kind)`. `observePlan` closes on error, which `bounded` already turns into `false`. The co-parent for `hasPlanWaiting` is `family.partnerUid`; when it is blank, use `flowOf(false)` as the requests listener does.
- [ ] **Step 4: Run `OtherFamiliesSignalsTest`, `FamilySwitcherTest` (if present) and the rules suite; expect PASS.**
- [ ] **Step 5: Commit**

Message: `feat(families): the switcher's dot names support, professional access and plan answers`.

### Task 5: Docs, baselines, PR

**Files:** `CLAUDE.md` (item 40 of "Things that are easy to get wrong", design refresh item 13's list of signals), `docs/DEVICE-CHECKLIST.md` (a two-phone check: support proposal and professional consent appear on the other phone's Requests row and inbox, the switcher dot from a second family), `tools/manual-test-plan.js` (map `AgreementRows.kt`, `FamilyAgreementsSource.kt` to it), `docs/legal/` only if a read changes (it does not: the same documents, the same readers), the spec's status line, `.github/regenerate-request`.

- [ ] **Step 1: Write the docs.** In item 40, the requests also include child support, professional access and the plan. Each row opens its own screen, they count on the row but not the banner, and they have no pop-up and no history.
- [ ] **Step 2: Run `node tools/check-invariants.js` and `node --test tools/test/*.test.js`; expect PASS.**
- [ ] **Step 3: Commit, push and open a draft PR. Touch `.github/regenerate-request` for the new screenshot baselines. After the bot commit, push a real commit so CI runs. Drive CI green, then do the final review, then merge.**
